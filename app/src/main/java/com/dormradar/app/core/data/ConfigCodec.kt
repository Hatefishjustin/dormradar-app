package com.dormradar.app.core.data

import com.dormradar.app.core.device.DeviceInfo

/** 一份可应用/对比的设备配置（与 DeviceParams 解耦，分辨率单列——不支持的固件为 null） */
data class ConfigSnapshotData(
    val maxMoveGate: Int,
    val maxStaticGate: Int,
    val unmannedSeconds: Int,
    val resolution02m: Boolean?,
    val motionSensitivities: IntArray,
    val staticSensitivities: IntArray,
) {
    override fun equals(other: Any?): Boolean = other is ConfigSnapshotData &&
        maxMoveGate == other.maxMoveGate && maxStaticGate == other.maxStaticGate &&
        unmannedSeconds == other.unmannedSeconds && resolution02m == other.resolution02m &&
        motionSensitivities.contentEquals(other.motionSensitivities) &&
        staticSensitivities.contentEquals(other.staticSensitivities)

    override fun hashCode(): Int = listOf(
        maxMoveGate, maxStaticGate, unmannedSeconds, resolution02m,
        motionSensitivities.contentHashCode(), staticSensitivities.contentHashCode(),
    ).hashCode()
}

/**
 * 配置快照编解码（纯 Kotlin，行式 k=v 格式，稳定可单测；不用 org.json——JVM 单测无实现）。
 * v1 格式：
 * ```
 * v=1
 * maxMove=8
 * maxStatic=6
 * unmanned=5
 * res=0|1|u          （0=0.75m / 1=0.2m / u=未知或不支持）
 * m=50,50,40,...     （运动灵敏度，逗号分隔）
 * s=40,40,40,...
 * ```
 */
object ConfigCodec {

    fun encode(data: ConfigSnapshotData): String = buildString {
        appendLine("v=1")
        appendLine("maxMove=${data.maxMoveGate}")
        appendLine("maxStatic=${data.maxStaticGate}")
        appendLine("unmanned=${data.unmannedSeconds}")
        appendLine("res=" + when (data.resolution02m) {
            true -> "1"
            false -> "0"
            null -> "u"
        })
        appendLine("m=" + data.motionSensitivities.joinToString(","))
        append("s=" + data.staticSensitivities.joinToString(","))
    }

    fun decode(text: String): ConfigSnapshotData? {
        val map = mutableMapOf<String, String>()
        text.lineSequence().forEach { line ->
            val idx = line.indexOf('=')
            if (idx > 0) map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
        }
        if (map["v"] != "1") return null
        val maxMove = map["maxMove"]?.toIntOrNull() ?: return null
        val maxStatic = map["maxStatic"]?.toIntOrNull() ?: return null
        val unmanned = map["unmanned"]?.toIntOrNull() ?: return null
        val resolution = when (map["res"]) {
            "1" -> true
            "0" -> false
            "u", null -> null
            else -> null
        }
        val motion = map["m"]?.split(',')?.map { it.trim().toIntOrNull() ?: return null } ?: return null
        val static = map["s"]?.split(',')?.map { it.trim().toIntOrNull() ?: return null } ?: return null
        if (motion.isEmpty() || motion.size != static.size) return null
        return ConfigSnapshotData(maxMove, maxStatic, unmanned, resolution, motion.toIntArray(), static.toIntArray())
    }

    /** 从当前设备信息取快照（未连接/参数未就绪 = null） */
    fun fromDeviceInfo(info: DeviceInfo?): ConfigSnapshotData? {
        val params = info?.params ?: return null
        return ConfigSnapshotData(
            maxMoveGate = params.maxMoveGate,
            maxStaticGate = params.maxStaticGate,
            unmannedSeconds = params.unmannedSeconds,
            resolution02m = info.resolution02m,
            motionSensitivities = params.motionSensitivities.copyOf(),
            staticSensitivities = params.staticSensitivities.copyOf(),
        )
    }
}
