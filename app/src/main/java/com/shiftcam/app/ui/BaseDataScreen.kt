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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.shiftcam.app.data.db.MachineEntity
import com.shiftcam.app.data.db.WorkSiteEntity
import com.shiftcam.domain.reading.ReadingFormatter
import com.shiftcam.domain.reading.ReadingUnit
import kotlinx.coroutines.launch

/** S13/S16 基础资料：设备（含读数单位）与工地。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseDataScreen(graph: AppGraph, onBack: () -> Unit) {
    val machines by graph.baseDataRepository.observeMachines().collectAsState(initial = emptyList())
    val sites by graph.baseDataRepository.observeSites().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAddMachine by remember { mutableStateOf(false) }
    var showAddSite by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("基础资料") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("设备", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showAddMachine = true }) { Text("添加设备") }
            }
            if (machines.isEmpty()) {
                Text("还没有设备，先添加一台才能上班打卡。", style = MaterialTheme.typography.bodyMedium)
            }
            machines.forEach { machine ->
                MachineCard(machine) {
                    scope.launch {
                        runCatching { graph.baseDataRepository.softDeleteMachine(machine.id) }
                            .onFailure { error = it.message }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("工地", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showAddSite = true }) { Text("添加工地") }
            }
            if (sites.isEmpty()) {
                Text("未添加工地时，水印工地一栏留空。", style = MaterialTheme.typography.bodyMedium)
            }
            sites.forEach { site ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(site.name, style = MaterialTheme.typography.bodyLarge)
                            site.note?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { graph.baseDataRepository.softDeleteSite(site.id) }
                                    .onFailure { error = it.message }
                            }
                        }) { Text("归档") }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showAddMachine) {
        AddMachineDialog(
            onDismiss = { showAddMachine = false },
            onSave = { name, code, unit, custom, decimals ->
                scope.launch {
                    runCatching {
                        graph.baseDataRepository.addMachine(name, code, unit, custom, decimals)
                        showAddMachine = false
                    }.onFailure { error = it.message }
                }
            },
        )
    }
    if (showAddSite) {
        AddSiteDialog(
            onDismiss = { showAddSite = false },
            onSave = { name, note ->
                scope.launch {
                    runCatching {
                        graph.baseDataRepository.addSite(name, note)
                        showAddSite = false
                    }.onFailure { error = it.message }
                }
            },
        )
    }
}

@Composable
private fun MachineCard(machine: MachineEntity, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(machine.name, style = MaterialTheme.typography.bodyLarge)
                val unit = ReadingFormatter.unitLabel(machine.readingUnit, machine.readingUnitCustom)
                Text(
                    "编号 ${machine.code.ifBlank { "-" }} · 读数单位 $unit · ${machine.readingDecimals} 位小数",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}

@Composable
private fun AddMachineDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, ReadingUnit, String?, Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(ReadingUnit.FIELD) }
    var custom by remember { mutableStateOf("") }
    var decimals by remember { mutableStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加设备") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("设备名称") })
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("设备编号（可选）") })
                Text("读数单位", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReadingUnit.entries.forEach { item ->
                        FilterChip(
                            selected = unit == item,
                            onClick = { unit = item },
                            label = { Text(ReadingFormatter.unitLabel(item)) },
                        )
                    }
                }
                if (unit != ReadingUnit.FIELD) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        label = { Text("自定义单位（可选，如 亩/方）") },
                    )
                }
                Text("小数位", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 1, 2).forEach { item ->
                        FilterChip(
                            selected = decimals == item,
                            onClick = { decimals = item },
                            label = { Text("$item") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, code, unit, custom.ifBlank { null }, decimals) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AddSiteDialog(onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加工地") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("工地名称") })
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注（可选）") })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, note.ifBlank { null }) }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
