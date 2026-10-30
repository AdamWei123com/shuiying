package com.shiftcam.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shiftcam.app.AppGraph
import kotlinx.coroutines.launch
import java.util.Locale

/** 设置页：业务日切点与引导重置（MVP 不做天气/备份入口）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(graph: AppGraph, onBack: () -> Unit) {
    val setting by graph.settingRepository.observe().collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("业务日切点", style = MaterialTheme.typography.titleMedium)
            Text(
                "凌晨 ${String.format(Locale.US, "%02d:%02d", (setting?.businessDayBoundaryMinute ?: 240) / 60, (setting?.businessDayBoundaryMinute ?: 240) % 60)} 之前的作业算前一天（R21 跨零点）。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 240, 300, 360).forEach { minute ->
                    OutlinedButton(onClick = { scope.launch { graph.settingRepository.setBusinessDayBoundary(minute) } }) {
                        Text(String.format(Locale.US, "%02d:00", minute / 60))
                    }
                }
            }
            HorizontalDivider()
            Text("数据与隐私", style = MaterialTheme.typography.titleMedium)
            Text(
                "作业数据只存本机；成片写入相册；卸载即丢失，P11 会补备份/恢复。",
                style = MaterialTheme.typography.bodyMedium,
            )
            HorizontalDivider()
            Text("版本", style = MaterialTheme.typography.titleMedium)
            Text("0.1.0（P1~P7 最小闭环）", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { scope.launch { graph.settingRepository.setOnboardingDone(false) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("重新显示首次引导")
            }
        }
    }
}
