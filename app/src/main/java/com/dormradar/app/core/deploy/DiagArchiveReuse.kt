package com.dormradar.app.core.deploy

import com.dormradar.app.core.log.DiagPhase
import com.dormradar.app.core.log.DistanceDiagSample

/**
 * 采样档案复用（用户需求 2026-10-07「只测一次，处处可用」）：
 * 「距离诊断」两步引导完成后样本已自动存档（SettingsStore.deployArchiveCsv），
 * 这里把档案按引导阶段拆成各功能需要的输入形态——
 * - 无人段 → 无人基线定参（[perGateSeries]）/ 部署向导底噪样本（[toEnergySamples]）；
 * - 有人段 → 部署向导走动样本（[toEnergySamples]）。
 *
 * 纯 Kotlin 可单测；聚合口径与部署向导实时采样一致：每门 max(运动能量, 静止能量) 的 avg/max。
 * 所有函数要求传入**非空**且已过工程帧过滤的列表（[noPersonEngineering]/[personEngineering] 的产物）。
 */
object DiagArchiveReuse {

    /** 档案里的无人段工程帧（basic 帧无门能量列，不适用） */
    fun noPersonEngineering(samples: List<DistanceDiagSample>): List<DistanceDiagSample> =
        samples.filter { it.phase == DiagPhase.NO_PERSON && it.moveGateEnergies != null }

    /** 档案里的有人段工程帧 */
    fun personEngineering(samples: List<DistanceDiagSample>): List<DistanceDiagSample> =
        samples.filter { it.phase == DiagPhase.PERSON && it.moveGateEnergies != null }

    /** 门数取各帧并集最大（调用方需保证 frames 非空） */
    fun gateCountOf(frames: List<DistanceDiagSample>): Int =
        frames.maxOf { it.moveGateEnergies?.size ?: 0 }

    /** 每门运动/静止能量序列（外层下标=门，内层下标=帧）——供 NoiseBaselineAdvisor */
    fun perGateSeries(frames: List<DistanceDiagSample>): Pair<List<IntArray>, List<IntArray>> {
        val gateCount = gateCountOf(frames)
        val motion = List(gateCount) { g ->
            IntArray(frames.size) { i -> frames[i].moveGateEnergies!!.getOrElse(g) { 0 } }
        }
        val static = List(gateCount) { g ->
            IntArray(frames.size) { i -> frames[i].staticGateEnergies!!.getOrElse(g) { 0 } }
        }
        return motion to static
    }

    /** 每门 max(运动,静止) 的 avg/max——与部署向导实时采样同口径，供 DeployAdvisor */
    fun toEnergySamples(frames: List<DistanceDiagSample>, gateCount: Int): EnergySamples {
        val sums = IntArray(gateCount)
        val maxes = IntArray(gateCount)
        val counts = IntArray(gateCount)
        frames.forEach { f ->
            for (g in 0 until gateCount) {
                val e = maxOf(
                    f.moveGateEnergies?.getOrElse(g) { 0 } ?: 0,
                    f.staticGateEnergies?.getOrElse(g) { 0 } ?: 0,
                )
                sums[g] += e
                if (e > maxes[g]) maxes[g] = e
                counts[g]++
            }
        }
        return EnergySamples((0 until gateCount).map { g ->
            GateSample(
                avgEnergy = if (counts[g] > 0) sums[g] / counts[g] else 0,
                maxEnergy = maxes[g],
            )
        })
    }
}
