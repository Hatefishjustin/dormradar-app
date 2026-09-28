package com.dormradar.app.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.dormradar.app.core.log.LogStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/** 扫描结果条目 */
data class BleScanDevice(
    val mac: String,
    val name: String?,
    val rssi: Int,
    val advertisesRadarService: Boolean,
)

/** BLE 层事件（不含协议语义） */
sealed interface BleEvent {
    /** 连接就绪：服务发现 + 订阅 + MTU 全部完成，可以开始收发 */
    data class Ready(val mac: String) : BleEvent

    /** 收到设备通知数据（原始字节，协议层负责解码） */
    data class Data(val bytes: ByteArray) : BleEvent

    /** 连接断开 */
    data class Disconnected(val mac: String) : BleEvent
}

/**
 * Android GATT 薄封装（docs/04 §3.1）。
 *
 * 职责：扫描、连接时序编排（连接 → 服务发现 → 订阅 Notify → 请求 MTU）、
 * 写操作串行队列、原始字节流输出。不认识协议内容。
 *
 * 连接时序依据官方 Demo（docs/02 §2 连接时序）：
 * connectGatt(false) → discoverServices → 找 fff0 下 fff1/fff2（缺一即断）
 * → setCharacteristicNotification + writeDescriptor → WRITE_TYPE_NO_RESPONSE → requestMtu(247)。
 */
@Singleton
class BleClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: LogStore,
) {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
        private val CCC_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val TARGET_MTU = 247
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val WRITE_TIMEOUT_MS = 3_000L
        private const val TAG = "BleClient"
    }

    private val bluetoothManager: BluetoothManager?
        get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val _events = MutableSharedFlow<BleEvent>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<BleEvent> = _events

    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    /** GATT 操作串行锁（连接与写互斥；Android BLE 同一时刻只允许一个操作在途） */
    private val gattMutex = Mutex()

    // 每次连接的挂起信号（在 connect() 中重建）
    private var connectedSignal = CompletableDeferred<Unit>()
    private var servicesResult = CompletableDeferred<Int>()
    private var descriptorResult = CompletableDeferred<Boolean>()
    private var mtuResult = CompletableDeferred<Int>()
    private var writeResult = CompletableDeferred<Int>()
    private var rssiResult = CompletableDeferred<Int>()

    /** 是否为用户主动断开（供上层区分意外断连；由 disconnect() 置位） */
    @Volatile
    var isManualDisconnect: Boolean = false
        private set

    /** 蓝牙是否可用（已开启） */
    fun isBluetoothReady(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        return adapter.isEnabled
    }

    // ---- 扫描 ----

    /**
     * 扫描附近 BLE 设备。不做硬件过滤（广播名格式待实机确认，docs/02 §2），
     * 由上层按「广播 fff0 服务 / 名称含 LD2410」标注雷达设备。
     */
    fun scan(): Flow<BleScanDevice> = callbackFlow {
        val adapter = bluetoothAdapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || scanner == null || !adapter.isEnabled) {
            close(IllegalStateException("蓝牙不可用或未开启"))
            return@callbackFlow
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val advertised = result.scanRecord?.serviceUuids.orEmpty()
                trySend(
                    BleScanDevice(
                        mac = result.device.address,
                        name = result.scanRecord?.deviceName ?: result.device.name,
                        rssi = result.rssi,
                        advertisesRadarService = advertised.any { it.uuid == SERVICE_UUID },
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                log.w(TAG, "BLE 扫描失败，错误码 $errorCode")
                close(IllegalStateException("BLE 扫描失败，错误码 $errorCode"))
            }
        }
        try {
            scanner.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(),
                callback,
            )
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }
        awaitClose {
            runCatching { scanner.stopScan(callback) }
        }
    }

    // ---- 连接 ----

    /**
     * 连接设备并完成全部时序，成功后发出 [BleEvent.Ready]。
     * @throws IOException 连接/发现/订阅/MTU 任一环节失败
     * @throws SecurityException 缺少 BLUETOOTH_CONNECT 权限（API 31+）
     */
    suspend fun connect(mac: String) = gattMutex.withLock {
        closeInternal()
        val device = bluetoothAdapter?.getRemoteDevice(mac)
            ?: throw IOException("蓝牙适配器不可用，无法连接 $mac")

        connectedSignal = CompletableDeferred()
        servicesResult = CompletableDeferred()
        descriptorResult = CompletableDeferred()
        mtuResult = CompletableDeferred()
        rssiResult = CompletableDeferred()

        val g: BluetoothGatt = device.connectGatt(
            context, false, gattCallback, BluetoothDevice.TRANSPORT_LE,
        )
        gatt = g
        try {
            withTimeout(CONNECT_TIMEOUT_MS) {
                connectedSignal.await() // onConnectionStateChange 已触发 discoverServices
                val status = servicesResult.await()
                if (status != BluetoothGatt.GATT_SUCCESS) throw IOException("服务发现失败（status=$status）")

                val service = g.getService(SERVICE_UUID)
                    ?: throw IOException("未找到透传服务 fff0（设备可能已连接其他手机）")
                val notify = service.getCharacteristic(NOTIFY_UUID)
                    ?: throw IOException("未找到 Notify 特征 fff1")
                val write = service.getCharacteristic(WRITE_UUID)
                    ?: throw IOException("未找到 Write 特征 fff2")

                // 订阅 Notify：本地开关 + 写 CCC 描述符，两者都成功才算订阅
                if (!g.setCharacteristicNotification(notify, true)) {
                    throw IOException("订阅 Notify 失败（本地开关）")
                }
                notify.getDescriptor(CCC_UUID)?.let { desc ->
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    if (!g.writeDescriptor(desc)) throw IOException("写 CCC 描述符失败")
                    if (!descriptorResult.await()) throw IOException("订阅 Notify 失败（描述符写入失败）")
                }
                writeCharacteristic = write

                // 请求大 MTU；失败退回默认 23，不影响功能（docs/02 §2）
                if (g.requestMtu(TARGET_MTU)) {
                    val negotiated = mtuResult.await()
                    log.i(TAG, "MTU 协商完成（$negotiated）")
                }
            }
        } catch (e: TimeoutCancellationException) {
            closeInternal()
            throw IOException("连接超时（设备不在范围或已被占用）", e)
        } catch (e: Exception) {
            closeInternal()
            throw e
        }
        _events.tryEmit(BleEvent.Ready(mac))
        log.i(TAG, "连接就绪：$mac")
    }

    // ---- 写 ----

    /**
     * 写入命令字节（WRITE_TYPE_NO_RESPONSE，等回调确认底层成功）。
     * 通过 [gattMutex] 保证与连接操作互斥；命令级串行由上层 CommandSession 负责。
     */
    suspend fun write(bytes: ByteArray) = gattMutex.withLock {
        val g = gatt ?: throw IOException("未连接")
        val c = writeCharacteristic ?: throw IOException("Write 特征不可用")
        writeResult = CompletableDeferred()
        @Suppress("DEPRECATION")
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        @Suppress("DEPRECATION")
        c.value = bytes
        val accepted = try {
            g.writeCharacteristic(c)
        } catch (e: SecurityException) {
            throw IOException("缺少蓝牙连接权限", e)
        }
        if (!accepted) throw IOException("writeCharacteristic 被拒绝（连接可能已断开）")
        val status = withTimeoutOrNull(WRITE_TIMEOUT_MS) { writeResult.await() }
            ?: throw TimeoutException("写入回调超时")
        if (status != BluetoothGatt.GATT_SUCCESS) {
            throw IOException("写入失败（status=$status）")
        }
    }

    /** 读取当前 RSSI（连接质量页用）。失败/超时返回 null，不影响连接 */
    suspend fun readRssi(): Int? {
        val g = gatt ?: return null
        rssiResult = CompletableDeferred()
        val accepted = try {
            g.readRemoteRssi()
        } catch (e: SecurityException) {
            return null
        }
        if (!accepted) return null
        return withTimeoutOrNull(2_000L) { rssiResult.await() }
    }

    /** 主动断开（用户操作；不会触发上层自动重连语义） */
    suspend fun disconnect() = gattMutex.withLock {
        isManualDisconnect = true
        closeInternal()
    }

    private fun closeInternal() {
        writeCharacteristic = null
        gatt?.let { g ->
            runCatching {
                g.disconnect()
                g.close()
            }
        }
        gatt = null
    }

    // ---- GATT 回调 ----

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {

        /** 防止上一条连接的迟到回调污染当前连接的挂起信号 */
        private fun BluetoothGatt.isCurrent() = this === gatt

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            log.i(TAG, "onConnectionStateChange status=$status newState=$newState")
            if (!g.isCurrent()) return
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedSignal.complete(Unit)
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    // 连接失败/中断时把挂起的信号全部放行，避免上层卡死
                    connectedSignal.complete(Unit)
                    servicesResult.complete(-1)
                    descriptorResult.complete(false)
                    mtuResult.complete(-1)
                    closeInternal()
                    _events.tryEmit(BleEvent.Disconnected(g.device.address))
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g.isCurrent()) servicesResult.complete(status)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (g.isCurrent()) descriptorResult.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (g.isCurrent()) mtuResult.complete(if (status == BluetoothGatt.GATT_SUCCESS) mtu else -1)
        }

        override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) {
            if (g.isCurrent()) rssiResult.complete(if (status == BluetoothGatt.GATT_SUCCESS) rssi else -1)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (g.isCurrent()) writeResult.complete(status)
        }

        @Deprecated("API 33 起用新签名，但旧回调仍会被调用（兼容 minSdk 26）")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid != NOTIFY_UUID) return
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            if (value.isNotEmpty()) _events.tryEmit(BleEvent.Data(value))
        }
    }
}
