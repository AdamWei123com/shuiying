package com.shiftcam.app.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.shiftcam.app.AppGraph
import com.shiftcam.app.AppScreen
import com.shiftcam.app.data.db.MachineEntity
import com.shiftcam.app.data.db.WorkSegmentEntity
import com.shiftcam.app.data.db.WorkSessionEntity
import com.shiftcam.app.data.db.WorkSiteEntity
import com.shiftcam.app.data.repo.DomainException
import com.shiftcam.app.data.repo.StartShiftInput
import com.shiftcam.domain.reading.ReadingFormatter
import com.shiftcam.domain.reading.ReadingUnit
import com.shiftcam.domain.session.RemarkOwnerType
import com.shiftcam.domain.session.WorkType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** S03 上下班打卡 + S05 当前班次。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftScreen(graph: AppGraph, navigate: (AppScreen) -> Unit) {
    val scope = rememberCoroutineScope()
    val running by graph.sessionRepository.observeRunning().collectAsState(initial = null)
    val machines by graph.baseDataRepository.observeMachines().collectAsState(initial = emptyList())
    val sites by graph.baseDataRepository.observeSites().collectAsState(initial = emptyList())
    val history by graph.sessionRepository.observeActiveSessions(10).collectAsState(initial = emptyList())
    val voided by graph.sessionRepository.observeVoidedSessions(10).collectAsState(initial = emptyList())

    var error by remember { mutableStateOf<String?>(null) }
    var showFinish by remember { mutableStateOf(false) }
    var showVoid by remember { mutableStateOf(false) }
    var showRemark by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf<StartShiftInput?>(null) }
    var pendingFinishReading by remember { mutableStateOf<Double?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("班次") },
                actions = {
                    TextButton(onClick = { navigate(AppScreen.BaseData) }) { Text("基础资料") }
                    TextButton(onClick = { navigate(AppScreen.Settings) }) { Text("设置") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val session = running
            if (session == null) {
                StartPanel(
                    machines = machines,
                    sites = sites,
                    onManage = { navigate(AppScreen.BaseData) },
                    onStart = { input ->
                        scope.launch {
                            try {
                                graph.sessionRepository.start(input)
                                error = null
                            } catch (e: DomainException.ConfirmRequired) {
                                pendingStart = input
                                error = e.message
                            } catch (t: Exception) {
                                error = t.message
                            }
                        }
                    },
                    onError = { error = it },
                )
            } else {
                RunningPanel(
                    graph = graph,
                    session = session,
                    machines = machines,
                    navigate = navigate,
                    onFinish = { showFinish = true },
                    onVoid = { showVoid = true },
                    onRemark = { showRemark = true },
                    onError = { error = it },
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SessionHistoryCard(
                graph = graph,
                active = history,
                voided = voided,
                machines = machines,
                onError = { error = it },
            )
        }
    }

    pendingStart?.let { input ->
        AlertDialog(
            onDismissRequest = { pendingStart = null },
            title = { Text("读数倒挂确认") },
            text = { Text("上班读数小于上次结班读数，确认按此读数开始班次？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            graph.sessionRepository.start(input.copy(anomalyConfirmed = true))
                            error = null
                        } catch (t: Exception) {
                            error = t.message
                        }
                        pendingStart = null
                    }
                }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { pendingStart = null }) { Text("取消") } },
        )
    }

    pendingFinishReading?.let { reading ->
        AlertDialog(
            onDismissRequest = { pendingFinishReading = null },
            title = { Text("读数倒挂确认") },
            text = { Text("下班读数小于上班读数，确认以该读数结班？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            graph.sessionRepository.finish(reading, anomalyConfirmed = true)
                            error = null
                        } catch (t: Exception) {
                            error = t.message
                        }
                        pendingFinishReading = null
                    }
                }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { pendingFinishReading = null }) { Text("取消") } },
        )
    }

    if (showFinish) {
        FinishDialog(
            onDismiss = { showFinish = false },
            onConfirm = { reading ->
                scope.launch {
                    try {
                        graph.sessionRepository.finish(reading, anomalyConfirmed = false)
                        showFinish = false
                        error = null
                    } catch (e: DomainException.ConfirmRequired) {
                        pendingFinishReading = reading
                        showFinish = false
                        error = e.message
                    } catch (t: Exception) {
                        error = t.message
                    }
                }
            },
        )
    }

    if (showVoid) {
        AlertDialog(
            onDismissRequest = { showVoid = false },
            title = { Text("作废班次") },
            text = { Text("作废后该班次不计入报表，可在班次列表恢复（仅已结班）。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        running?.let {
                            runCatching { graph.sessionRepository.voidSession(it.id, "用户作废") }
                                .onFailure { e -> error = e.message }
                        }
                        showVoid = false
                    }
                }) { Text("作废") }
            },
            dismissButton = { TextButton(onClick = { showVoid = false }) { Text("取消") } },
        )
    }

    if (showRemark) {
        val session = running
        if (session != null) {
            RemarkDialog(
                graph = graph,
                ownerType = RemarkOwnerType.SESSION,
                ownerId = session.id,
                sessionId = session.id,
                onDismiss = { showRemark = false },
            )
        }
    }
}

@Composable
private fun StartPanel(
    machines: List<MachineEntity>,
    sites: List<WorkSiteEntity>,
    onManage: () -> Unit,
    onStart: (StartShiftInput) -> Unit,
    onError: (String?) -> Unit,
) {
    var machine by remember { mutableStateOf<MachineEntity?>(null) }
    var site by remember { mutableStateOf<WorkSiteEntity?>(null) }
    var reading by remember { mutableStateOf("") }

    Text("上班打卡", style = MaterialTheme.typography.titleMedium)
    if (machines.isEmpty()) {
        Text("还没有设备，先到基础资料添加一台。", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onManage) { Text("去添加设备") }
        return
    }

    DropdownSelector(
        label = "选择设备",
        items = machines,
        itemLabel = { "${it.name}${if (it.code.isBlank()) "" else " · ${it.code}"}" },
        selected = machine ?: machines.first(),
        onSelect = { machine = it },
    )
    val currentMachine = machine ?: machines.first()
    DropdownSelector(
        label = "选择工地（可选）",
        items = sites,
        itemLabel = { it.name },
        selected = site,
        onSelect = { site = it },
    )
    OutlinedTextField(
        value = reading,
        onValueChange = { reading = it },
        label = { Text("上班读数（${ReadingFormatter.unitLabel(currentMachine.readingUnit, currentMachine.readingUnitCustom)}）") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
    Button(
        onClick = {
            val value = reading.trim().toDoubleOrNull()
            if (value == null) {
                onError("请填写有效的上班读数")
                return@Button
            }
            onError(null)
            onStart(
                StartShiftInput(
                    machineId = currentMachine.id,
                    siteId = site?.id,
                    startReading = value,
                    workType = WorkType.DIGGING,
                ),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("上班打卡")
    }
}

@Composable
private fun RunningPanel(
    graph: AppGraph,
    session: WorkSessionEntity,
    machines: List<MachineEntity>,
    navigate: (AppScreen) -> Unit,
    onFinish: () -> Unit,
    onVoid: () -> Unit,
    onRemark: () -> Unit,
    onError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val segments by graph.sessionRepository.observeSegments(session.id).collectAsState(initial = emptyList())
    val total by graph.countRepository.observeTotal(session.id).collectAsState(initial = 0)
    val active: WorkSegmentEntity? = segments.lastOrNull()
    val machine = machines.firstOrNull { it.id == session.machineId }
    val unit: ReadingUnit = machine?.readingUnit ?: ReadingUnit.FIELD

    var undoVisible by remember { mutableStateOf(false) }
    var undoToken by remember { mutableStateOf(0) }

    LaunchedEffect(undoToken) {
        if (undoVisible) {
            delay(SessionUndoWindowMillis)
            undoVisible = false
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("进行中 · 第 ${session.shiftNo} 班", style = MaterialTheme.typography.titleMedium)
            Text("设备：${session.machineNameSnapshot}")
            Text("工地：${session.siteNameSnapshot.ifBlank { "未选" }}")
            Text(
                "上班：${formatClock(session.startTime)} · 读数 " +
                    ReadingFormatter.format(
                        session.startReading,
                        unit,
                        machine?.readingUnitCustom,
                        machine?.readingDecimals ?: 1,
                    ),
            )
            Text("当前施工方式：${active?.workType?.label ?: "-"}", style = MaterialTheme.typography.bodyLarge)
            Text("计数合计：$total")
        }
    }

    Text("切换施工方式", style = MaterialTheme.typography.labelLarge)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WorkType.entries.forEach { type ->
            FilterChip(
                selected = active?.workType == type,
                onClick = {
                    if (active?.workType == type) return@FilterChip
                    scope.launch {
                        try {
                            graph.sessionRepository.switchWorkType(type)
                            undoToken += 1
                            undoVisible = true
                        } catch (t: Exception) {
                            onError(t.message ?: "切换失败")
                        }
                    }
                },
                label = { Text(type.label) },
            )
        }
    }

    if (undoVisible) {
        Surface(tonalElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("已切换为 ${active?.workType?.label}（3 秒内可撤销）")
                TextButton(onClick = {
                    scope.launch {
                        try {
                            graph.sessionRepository.undoLastSwitch()
                            undoVisible = false
                        } catch (t: Exception) {
                            onError(t.message ?: "撤销失败")
                        }
                    }
                }) { Text("撤销") }
            }
        }
    }

    HorizontalDivider()

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { navigate(AppScreen.Count) }, modifier = Modifier.weight(1f)) { Text("计数") }
        OutlinedButton(onClick = { navigate(AppScreen.Camera) }, modifier = Modifier.weight(1f)) { Text("拍照") }
        OutlinedButton(onClick = onRemark, modifier = Modifier.weight(1f)) { Text("备注") }
    }

    Spacer(Modifier.height(4.dp))
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("下班打卡") }
    TextButton(onClick = onVoid, modifier = Modifier.fillMaxWidth()) { Text("作废班次") }
}

private const val SessionUndoWindowMillis: Long = 3_000L

/** 班次记录 + 作废/恢复入口（P3）。作废不物理删，恢复仅对已结班班次开放。 */
@Composable
private fun SessionHistoryCard(
    graph: AppGraph,
    active: List<WorkSessionEntity>,
    voided: List<WorkSessionEntity>,
    machines: List<MachineEntity>,
    onError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val all = active + voided
    if (all.isEmpty()) return

    Text("班次记录", style = MaterialTheme.typography.titleMedium)
    all.take(12).forEach { session ->
        val machine = machines.firstOrNull { it.id == session.machineId }
        val unit = machine?.readingUnit ?: ReadingUnit.FIELD
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${session.machineNameSnapshot} · ${formatClock(session.startTime)}")
                    val state = when (session.status) {
                        com.shiftcam.domain.session.SessionStatus.RUNNING -> "进行中"
                        com.shiftcam.domain.session.SessionStatus.FINISHED -> "已结班"
                        com.shiftcam.domain.session.SessionStatus.VOIDED -> "已作废"
                    }
                    val hours = session.meterHours?.let {
                        ReadingFormatter.format(
                            it,
                            unit,
                            machine?.readingUnitCustom,
                            machine?.readingDecimals ?: 1,
                        )
                    } ?: ReadingFormatter.MISSING
                    Text("$state · 工时 $hours", style = MaterialTheme.typography.bodySmall)
                }
                when (session.status) {
                    com.shiftcam.domain.session.SessionStatus.VOIDED -> TextButton(onClick = {
                        scope.launch {
                            runCatching { graph.sessionRepository.restoreSession(session.id) }
                                .onFailure { onError(it.message ?: "恢复失败") }
                        }
                    }) { Text("恢复") }

                    com.shiftcam.domain.session.SessionStatus.FINISHED -> TextButton(onClick = {
                        scope.launch {
                            runCatching { graph.sessionRepository.voidSession(session.id, "历史作废") }
                                .onFailure { onError(it.message ?: "作废失败") }
                        }
                    }) { Text("作废") }

                    com.shiftcam.domain.session.SessionStatus.RUNNING -> Unit
                }
            }
        }
    }
}

@Composable
private fun FinishDialog(onDismiss: () -> Unit, onConfirm: (Double?) -> Unit) {
    var text by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    val parsed = text.trim().toDoubleOrNull()
    val valid = missing || parsed != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("下班打卡") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    enabled = !missing,
                    label = { Text("下班读数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = missing, onCheckedChange = { missing = it })
                    Text("读数不可用（记为缺读数班次）")
                }
                Text(
                    "工时 = 下班读数 − 上班读数，空白读数不计算工时。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(if (missing) null else parsed) }) {
                Text("结班")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun <T> DropdownSelector(
    label: String,
    items: List<T>,
    itemLabel: (T) -> String,
    selected: T?,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.let(itemLabel) ?: label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                )
            }
        }
    }
}
