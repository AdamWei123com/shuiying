package com.shiftcam.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shiftcam.app.AppGraph
import com.shiftcam.domain.session.WorkType
import kotlinx.coroutines.launch

/** S06 计数页 + S07 车牌键盘。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountScreen(graph: AppGraph, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val session by graph.sessionRepository.observeRunning().collectAsState(initial = null)
    val sessionState = session
    var plate by remember(sessionState?.id) { mutableStateOf("") }
    var showPlateKeyboard by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("计数") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        if (sessionState == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("当前没有进行中的班次，请先上班打卡。")
            }
            return@Scaffold
        }
        val total by graph.countRepository.observeTotal(sessionState.id).collectAsState(initial = 0)
        val records by graph.countRepository.observeRecords(sessionState.id).collectAsState(initial = emptyList())
        val segments by graph.sessionRepository.observeSegments(sessionState.id).collectAsState(initial = emptyList())
        val activeType = segments.lastOrNull()?.workType ?: WorkType.DIGGING

        var message by remember { mutableStateOf<String?>(null) }

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("计数合计", style = MaterialTheme.typography.titleMedium)
            Text("$total", style = MaterialTheme.typography.displayLarge)
            Text("当前施工方式：${activeType.label}", style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = plate,
                    onValueChange = { if (it.length <= 8) plate = it },
                    label = { Text("车牌（可选）") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { showPlateKeyboard = true }) { Text("车牌键盘") }
            }

            Button(
                onClick = {
                    scope.launch {
                        try {
                            graph.countRepository.add(
                                sessionId = sessionState.id,
                                workType = activeType,
                                plateNo = plate.trim().ifEmpty { null },
                            )
                            message = null
                        } catch (t: Exception) {
                            message = t.message
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) {
                Text("＋1 计数", style = MaterialTheme.typography.headlineSmall)
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        val undone = graph.countRepository.undoLatest(sessionState.id)
                        message = if (undone) "已撤销上一笔计数" else "没有可撤销的计数"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("撤销上一笔") }

            Text("最近记录", style = MaterialTheme.typography.titleMedium)
            if (records.isEmpty()) {
                Text("还没有计数记录。", style = MaterialTheme.typography.bodyMedium)
            }
            records.take(30).forEach { record ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        buildString {
                            append(formatClock(record.countedAt))
                            append("  ")
                            append(record.workType.label)
                            record.plateNo?.let { append("  $it") }
                            append("  ×${record.countValue}")
                            if (record.isVoided) append("  （已撤销）")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { graph.countRepository.softDelete(record.id) }
                                .onFailure { message = it.message }
                        }
                    }) { Text("删除") }
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showPlateKeyboard) {
        PlateKeyboardDialog(
            initial = plate,
            onDismiss = { showPlateKeyboard = false },
            onConfirm = {
                plate = it
                showPlateKeyboard = false
            },
        )
    }
}

private val plateProvinces = listOf(
    "京", "沪", "粤", "苏", "浙", "鲁", "冀", "豫", "川", "渝", "辽", "皖",
    "鄂", "湘", "赣", "闽", "桂", "琼", "晋", "陕", "甘", "云", "贵", "新",
    "蒙", "宁", "青", "藏", "黑", "吉",
)

/** 车牌键盘：省份简称 + 字母数字 + 退格，避免司机在工地戴手套打字。 */
@Composable
private fun PlateKeyboardDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val keys = remember {
        plateProvinces + ('A'..'Z').map { it.toString() } + ('0'..'9').map { it.toString() } + "⌫"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("车牌输入") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(value.ifBlank { "请输入车牌" }, style = MaterialTheme.typography.titleMedium)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.height(320.dp),
                ) {
                    items(keys) { key ->
                        TextButton(
                            onClick = {
                                when {
                                    key == "⌫" -> value = value.dropLast(1)
                                    value.length < 8 -> value += key
                                }
                            },
                        ) { Text(key) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim()) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
