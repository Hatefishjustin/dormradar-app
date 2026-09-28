package com.dormradar.app.core.device

/**
 * 一键设置推荐参数包（docs/06 阶段 2 任务 3，简单层入口）。
 *
 * 灵敏度语义（docs/08 §6）：能量 > 灵敏度才判定有人 →
 * **数值越低越灵敏**（穿门场景），越高越抗干扰（防误报），100 = 屏蔽该门。
 * 表按 9 距离门（0.75m 分辨率出厂布局）定义；0.2m 分辨率门数不同时按比例映射。
 */
data class PresetPack(
    val id: String,
    val name: String,
    val description: String,
    val moveMaxGate: Int,
    val staticMaxGate: Int,
    val unmannedSeconds: Int,
    val motion: IntArray,
    val static: IntArray,
)

object PresetPacks {

    /** 标准（出厂值）：通用场景，远近距离均衡 */
    val STANDARD = PresetPack(
        id = "standard",
        name = "标准模式",
        description = "出厂默认参数，远近距离均衡，适合先跑起来再观察",
        moveMaxGate = 8, staticMaxGate = 8, unmannedSeconds = 5,
        motion = intArrayOf(50, 50, 40, 30, 20, 15, 15, 15, 15),
        static = intArrayOf(40, 40, 40, 40, 30, 30, 20, 20, 20),
    )

    /** 灵敏穿门：门板衰减大、怕漏报 → 灵敏度整体下调（更容易触发） */
    val SENSITIVE = PresetPack(
        id = "sensitive",
        name = "灵敏穿门",
        description = "阈值整体调低（更灵敏），适合开阔空间远距探测。" +
            "⚠ 贴门板慎用：门板反射会抬高底噪，阈值低于底噪会\"没人也报有人\"——那时请改用「部署向导」采样",
        moveMaxGate = 8, staticMaxGate = 8, unmannedSeconds = 5,
        motion = intArrayOf(35, 35, 25, 15, 10, 5, 5, 5, 5),
        static = intArrayOf(25, 25, 25, 25, 15, 15, 10, 10, 10),
    )

    /** 强抗干扰：怕误报 → 缩短探测距离 + 灵敏度上调 + 快速回落 */
    val ANTI_NOISE = PresetPack(
        id = "anti_noise",
        name = "强抗干扰",
        description = "探测距离收窄到 4.5m、阈值调高、无人 2 秒快速回落，走廊嘈杂环境首选",
        moveMaxGate = 6, staticMaxGate = 6, unmannedSeconds = 2,
        motion = intArrayOf(65, 65, 55, 45, 35, 30, 30, 30, 30),
        static = intArrayOf(55, 55, 55, 55, 45, 45, 40, 40, 40),
    )

    val ALL = listOf(STANDARD, SENSITIVE, ANTI_NOISE)

    /**
     * 按目标门数映射参数包灵敏度表（0.2m 分辨率门数 ≠ 9 时按比例取值）。
     * 目标门数多于 9 时，重复末段值（远处保持同一策略）。
     */
    fun mapTable(table: IntArray, gateCount: Int): IntArray {
        require(gateCount >= 1)
        if (gateCount == table.size) return table.copyOf()
        return IntArray(gateCount) { i ->
            val idx = if (table.size == 1) 0 else i * (table.size - 1) / (gateCount - 1).coerceAtLeast(1)
            table[idx.coerceIn(0, table.size - 1)]
        }
    }
}

/**
 * 应用参数包：先设距离门 + 无人时长，再按设备实际门数写入灵敏度表（各自带回读校验）。
 * 换预设不会丢失用户已设置的近距屏蔽（最小距离门）——灵敏度表中重套用原屏蔽范围。
 */
suspend fun applyPresetPack(connection: RadarConnection, pack: PresetPack): OpResult {
    val currentMinGate = connection.deviceInfo.value?.params?.let { p ->
        p.motionSensitivities.indexOfFirst { it < 100 }.coerceAtLeast(0)
    } ?: 0
    val r1 = connection.applyParams(pack.moveMaxGate, pack.staticMaxGate, pack.unmannedSeconds)
    if (r1 != OpResult.Ok) return r1
    val params = connection.deviceInfo.value?.params
        ?: return OpResult.Fail("设备参数未就绪")
    val gateCount = params.motionSensitivities.size
    val motion = PresetPacks.mapTable(pack.motion, gateCount)
    val static = PresetPacks.mapTable(pack.static, gateCount)
    // 重套近距屏蔽（截到本预设最远门之内，避免全屏蔽）
    val minGate = currentMinGate.coerceIn(0, minOf(pack.moveMaxGate, pack.staticMaxGate) - 1)
    for (g in 0 until minGate) {
        motion[g] = 100
        static[g] = 100
    }
    return connection.applySensitivityTable(motion, static)
}
