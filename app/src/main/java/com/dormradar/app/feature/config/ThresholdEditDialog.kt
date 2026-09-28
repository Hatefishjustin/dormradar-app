package com.dormradar.app.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 门限数值编辑对话框（用户需求 2026-09-28：点击数值直接修改，替代拖动）。
 * 数字输入框 + 步进按钮（±1 / ±10），取值 0~100。
 * 灵敏度页（改草稿）与首页能量曲线卡（直接写入设备）共用。
 *
 * @param subtitle 副标题（如该门实时能量对照），可为 null
 */
@Composable
fun ThresholdEditDialog(
    title: String,
    initial: Int,
    subtitle: String?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initial.coerceIn(0, 100).toString()) }
    val parsed = text.trim().toIntOrNull()
    val valid = parsed != null && parsed in 0..100
    // 步进基于：输入合法用输入值，否则回落初始值
    fun step(delta: Int) {
        val v = ((parsed ?: initial) + delta).coerceIn(0, 100)
        text = v.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() }.take(3) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !valid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-10, -1, 1, 10).forEach { delta ->
                        OutlinedButton(
                            onClick = { step(delta) },
                            modifier = Modifier.weight(1f),
                        ) { Text(if (delta > 0) "+$delta" else "$delta") }
                    }
                }
                Text(
                    "范围 0~100，数值越低越灵敏；100 = 屏蔽该距离门",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!valid) {
                    Text(
                        "请输入 0~100 的整数",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onConfirm(parsed!!) }, enabled = valid) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
