package com.shiftcam.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.shiftcam.app.AppGraph
import com.shiftcam.domain.session.RemarkOwnerType
import kotlinx.coroutines.launch

/** S15 通用备注组件：4 类 owner 共用（R14 / P5）。 */
@Composable
fun RemarkDialog(
    graph: AppGraph,
    ownerType: RemarkOwnerType,
    ownerId: Long,
    sessionId: Long?,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ownerType, ownerId) {
        text = graph.remarkRepository.get(ownerType, ownerId)?.content.orEmpty()
        loaded = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("备注") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("备注信息") },
                    minLines = 3,
                )
                Text("清空内容保存即删除该备注。")
            }
        },
        confirmButton = {
            TextButton(
                enabled = loaded,
                onClick = {
                    scope.launch {
                        graph.remarkRepository.save(ownerType, ownerId, sessionId, text)
                        onDismiss()
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
