package com.dormradar.app.feature.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dormradar.app.core.device.DeviceInfo
import com.dormradar.app.core.device.RadarState
import com.dormradar.app.core.protocol.DeviceParams
import com.dormradar.app.core.protocol.ProtocolConst
import kotlin.math.cos
import kotlin.math.sin

/**
 * 首页雷达视图（用户需求 2026-09-27，二版）：半圆扇形「波束」视图，直观展示目标距离。
 *
 * 设计取舍（大白话）：LD2410C 只测距离、不辨方位——它知道人离它多远，不知道人在哪边。
 * 所以本图不画完整圆（那会暗示 360° 方位感知），改为雷达在底部中心、向上展开的探测扇面：
 * - 弧线 = 距离刻度（每弧一个距离门），目标点沿竖直中线按真实距离定位；
 * - 整个探测区一种颜色，随目标状态切换：运动=红 / 静止=紫 / 运动+静止=橙 / 无人=淡绿；
 * - 灰色弧段 = 已屏蔽的距离段（近距屏蔽）；
 * - 扫描线像倒车雷达一样左右摆动（纯氛围）。
 */
@Composable
internal fun RadarScopeCard(
    radarState: RadarState?,
    deviceInfo: DeviceInfo?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("雷达视图", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScopeLegend("运动", Color(0xFFDC2626))
                    ScopeLegend("静止", Color(0xFF9333EA))
                    ScopeLegend("屏蔽段", MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.height(8.dp))
            RadarScopeCanvas(
                radarState = radarState,
                params = deviceInfo?.params,
                resolution02m = deviceInfo?.resolution02m,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "点 = 目标位置（旁标距离）；灰色弧段 = 已屏蔽的距离段（近处晃动不触发）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScopeLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RadarScopeCanvas(
    radarState: RadarState?,
    params: DeviceParams?,
    resolution02m: Boolean?,
) {
    val textMeasurer = rememberTextMeasurer()
    val moveColor = Color(0xFFDC2626)
    val staticColor = Color(0xFF9333EA)
    val bothColor = Color(0xFFEA580C)
    val scanColor = Color(0xFF22C55E)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val blockedColor = MaterialTheme.colorScheme.outline
    val labelStyle = TextStyle(
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // 扫描线钟摆（像倒车雷达）+ 目标脉冲
    val transition = rememberInfiniteTransition(label = "radarScope")
    val sweepAngle by transition.animateFloat(
        initialValue = 197f,
        targetValue = 343f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "sweepAngle",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "targetPulse",
    )

    val maxGate = params?.maxGate ?: 8
    val resolutionCm = if (resolution02m == true) 20 else 75
    val maxDistanceCm = (maxGate + 1) * resolutionCm

    // 探测区颜色：单区域随当前目标状态切换（用户需求：不要运动/静止分区）
    val zoneColor = when (radarState?.targetState) {
        ProtocolConst.TARGET_MOVING -> moveColor
        ProtocolConst.TARGET_STATIC -> staticColor
        ProtocolConst.TARGET_BOTH -> bothColor
        else -> scanColor // 无人 / 无数据：淡绿
    }

    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.8f)) {
        val pad = 12.dp.toPx()
        val R = size.width / 2 - pad
        val c = Offset(size.width / 2, size.height - 8.dp.toPx())
        val ringW = R / (maxGate + 1)

        // ---- 1. 距离弧刻度（每门边界一条弧线） ----
        for (g in 1..maxGate + 1) {
            drawArc(
                color = gridColor.copy(alpha = 0.6f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(c.x - g * ringW, c.y - g * ringW),
                size = Size(g * ringW * 2, g * ringW * 2),
                style = Stroke(1f),
            )
        }

        // ---- 2. 探测区弧带（单色随状态；屏蔽门灰色；超出最大门的段不画） ----
        val maxActiveGate = params?.let { maxOf(it.maxMoveGate, it.maxStaticGate) } ?: maxGate
        val bandStroke = (ringW * 0.82f).coerceAtLeast(2f)
        for (g in 0..maxGate) {
            if (g > maxActiveGate) continue
            val rCenter = (g + 0.5f) * ringW
            val blocked = params != null &&
                params.motionSensitivities.getOrNull(g)?.let { it >= 100 } == true &&
                params.staticSensitivities.getOrNull(g)?.let { it >= 100 } == true
            drawArc(
                color = if (blocked) blockedColor.copy(alpha = 0.30f) else zoneColor.copy(alpha = 0.13f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(c.x - rCenter, c.y - rCenter),
                size = Size(rCenter * 2, rCenter * 2),
                style = Stroke(bandStroke),
            )
        }

        // ---- 3. 钟摆扫描线 + 拖尾 ----
        for (i in 3 downTo 1) {
            val a = sweepAngle + i * 5f
            val rad = Math.toRadians(a.toDouble())
            drawLine(
                color = scanColor.copy(alpha = 0.18f * (1f - i / 4f)),
                start = c,
                end = Offset(c.x + cos(rad).toFloat() * R, c.y + sin(rad).toFloat() * R),
                strokeWidth = 2f,
            )
        }
        val sweepRad = Math.toRadians(sweepAngle.toDouble())
        drawLine(
            color = scanColor,
            start = c,
            end = Offset(c.x + cos(sweepRad).toFloat() * R, c.y + sin(sweepRad).toFloat() * R),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round,
        )

        // ---- 4. 中心设备点（雷达位置） ----
        drawCircle(scanColor.copy(alpha = 0.3f), radius = 6.dp.toPx(), center = c)
        drawCircle(scanColor, radius = 3.dp.toPx(), center = c)

        // ---- 5. 距离刻度数字（沿竖直中线，门多时隔 2 门标） ----
        val step = if (maxGate + 1 > 6) 2 else 1
        for (g in step..maxGate + 1 step step) {
            val distM = g * resolutionCm / 100f
            val label = if (resolutionCm >= 100) "%.1f".format(distM) else "%.2f".format(distM)
            val m = textMeasurer.measure(label, labelStyle)
            drawText(
                textLayoutResult = m,
                topLeft = Offset(c.x + 3.dp.toPx(), c.y - g * ringW - m.size.height / 2f),
            )
        }

        // ---- 6. 目标点（单点沿竖直中线，径向距离 = 真实位置） ----
        val state = radarState
        if (state != null && state.hasTarget) {
            val useMove = state.moveDistanceCm > 0
            val distanceCm = if (useMove) state.moveDistanceCm else state.staticDistanceCm
            val energy = if (useMove) state.moveEnergy else state.staticEnergy
            if (distanceCm > 0) {
                drawTargetDot(
                    distanceCm = distanceCm,
                    energy = energy,
                    color = zoneColor,
                    center = c,
                    maxRadius = R,
                    maxDistanceCm = maxDistanceCm,
                    pulse = pulse,
                    textMeasurer = textMeasurer,
                    labelStyle = labelStyle,
                )
            }
        }
    }
}

/** 单个目标点：脉冲光圈 + 实心点 + 白描边 + 距离标签（点正上方） */
private fun DrawScope.drawTargetDot(
    distanceCm: Int,
    energy: Int,
    color: Color,
    center: Offset,
    maxRadius: Float,
    maxDistanceCm: Int,
    pulse: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    labelStyle: TextStyle,
) {
    val r = (distanceCm.toFloat() / maxDistanceCm).coerceIn(0.05f, 0.95f) * maxRadius
    val p = Offset(center.x, center.y - r)
    val dotR = (8 + (energy.coerceIn(0, 100) / 100f) * 6).dp.toPx()

    // 脉冲光圈（向外扩散渐隐）
    drawCircle(
        color = color.copy(alpha = 0.35f * (1f - pulse)),
        radius = dotR + 4.dp.toPx() + 10.dp.toPx() * pulse,
        center = p,
    )
    drawCircle(color = color, radius = dotR, center = p)
    drawCircle(color = Color.White, radius = dotR, center = p, style = Stroke(2f))

    // 距离标签：点正上方
    val label = if (distanceCm >= 100) "%.2fm".format(distanceCm / 100.0) else "${distanceCm}cm"
    val m = textMeasurer.measure(label, labelStyle.copy(color = color, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
    drawText(
        textLayoutResult = m,
        topLeft = Offset(p.x - m.size.width / 2f, p.y - dotR - m.size.height - 4.dp.toPx()),
    )
}
