package com.hlk.hlkradartool.data

import android.bluetooth.BluetoothGatt
import android.content.Context
import android.util.Log
import com.hlk.hlkradartool.R
import com.hlk.hlkradartool.activity.DemoApplication
import java.util.*

class SendDataHelper{

    lateinit var bluetoothLeClass : BluetoothLeClass
    internal var strSendData = ""
    internal var strBLEDevMac = ""
    internal var context:Context? = null

    constructor(context: Context,bluetoothLeClass: BluetoothLeClass) {
        this.context = context
        this.bluetoothLeClass = bluetoothLeClass
        this.strBLEDevMac = bluetoothLeClass.strBleDevMac
    }

    /** 开始发送数据 是否超时重发 */
    fun StartSendData(strData : String,isTimeOut : Boolean) {
        StopSend()
        this.strSendData = strData
        this.iSendCount = 0
        if (isTimeOut) {
            startSendDataByTimer(true)
        }
        else {
            bluetoothLeClass.startSendData(strSendData)
        }

    }

    /** 停止发送 */
    fun StopSend() {
        startSendDataByTimer(false)
    }


    internal var iSendCount = 0
    internal var iMaxSendCount = 3
    internal var sendTimerInfo = Timer()
    internal fun startSendDataByTimer(isSendTimer: Boolean) {
        sendTimerInfo.cancel()
        if (!isSendTimer) {
            return
        }
        iSendCount = 0
        iMaxSendCount = 3
        sendTimerInfo = Timer()
        sendTimerInfo.schedule(object : TimerTask(){
            override fun run() {
                if (bluetoothLeClass == null) {
                    Log.e("SendDataHelper", "${strBLEDevMac}，数据发送失败！")
                    bluetoothLeClass.returnSendErrorResult(BluetoothGatt.GATT_FAILURE,
                        DemoApplication.getInstance().getString(R.string.fason_shibai))
                }
                else {
                    if (iSendCount >= iMaxSendCount) {
                        Log.e("SendDataHelper", "${strBLEDevMac}，数据发送超时！")
                        bluetoothLeClass.returnSendErrorResult(BluetoothGatt.GATT_FAILURE,DemoApplication.getInstance().getString(R.string.fasong_chaoshi))
                        startSendDataByTimer(false)
                    }
                    else {
                        ++iSendCount
                        bluetoothLeClass.startSendData(strSendData)
                    }
                }

            }
        },0,1000*5) // 延时0后执行第一次，之后每隔5s重复执行

    }

}