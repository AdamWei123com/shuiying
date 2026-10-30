package com.shiftcam.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shiftcam.app.AppGraph
import com.shiftcam.app.data.db.PhotoEntity
import com.shiftcam.app.platform.CameraPlatform
import com.shiftcam.app.platform.PhotoStorage
import com.shiftcam.app.watermark.WatermarkRenderer
import com.shiftcam.domain.reading.ReadingFormatter
import com.shiftcam.domain.session.AddressSource
import com.shiftcam.domain.session.RemarkOwnerType
import com.shiftcam.domain.watermark.WatermarkInput
import com.shiftcam.domain.watermark.WatermarkLayoutSpec
import com.shiftcam.domain.weather.WeatherCachePolicy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** S14 相机 + 水印：预览与成片共用 WatermarkRenderer.draw（INV-06）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(graph: AppGraph, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 定位失败不影响拍照（INV-09） */ }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("需要相机权限才能拍照。", style = MaterialTheme.typography.titleMedium)
            Text("没有相机权限也可以正常打卡、计数。", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("授予权限")
            }
            TextButton(onClick = onBack) { Text("返回") }
        }
        return
    }

    val session by graph.sessionRepository.observeRunning().collectAsState(initial = null)
    val setting by graph.settingRepository.observe().collectAsState(initial = null)
    val totalFlow = remember(session?.id) {
        session?.let { graph.countRepository.observeTotal(it.id) } ?: flowOf(0)
    }
    val total by totalFlow.collectAsState(initial = 0)

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var spec by remember { mutableStateOf(WatermarkLayoutSpec()) }
    var input by remember { mutableStateOf(WatermarkInput()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            val provider = CameraPlatform.provider(context)
            val rotation = previewView.display?.rotation ?: 0
            val preview = CameraPreview.Builder().setTargetRotation(rotation).build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(rotation)
                .build()
            imageCapture = capture
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
        }.onFailure { message = "相机启动失败：${it.message}" }
    }

    LaunchedEffect(Unit) {
        spec = graph.templateRepository.currentSpec()
    }

    LaunchedEffect(session?.id, spec, setting, total) {
        input = buildWatermarkInput(graph, spec, setting, session, total)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拍照") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
        floatingActionButton = {
            Button(
                enabled = !busy,
                onClick = {
                    val capture = imageCapture ?: return@Button
                    busy = true
                    scope.launch {
                        try {
                            val rawFile = PhotoStorage.originalFile(context)
                            CameraPlatform.takePicture(context, capture, rawFile)
                            val bitmap = CameraPlatform.decodeRotated(rawFile)
                            val rendered = WatermarkRenderer.render(bitmap, spec, input)
                            val uri = PhotoStorage.saveWatermarked(context, rendered)
                            val point = graph.locationProvider.lastKnown()
                            val address = graph.locationProvider.addressOf(point)
                            val accurate = point != null && point.accuracy <= 100f
                            val now = System.currentTimeMillis()
                            graph.photoRepository.insert(
                                PhotoEntity(
                                    sessionId = session?.id,
                                    fileUri = uri.toString(),
                                    originalPath = rawFile.absolutePath,
                                    takenAt = now,
                                    lat = if (accurate) point?.lat else null,
                                    lng = if (accurate) point?.lng else null,
                                    locationAccuracy = point?.accuracy,
                                    address = address,
                                    addressSource = if (address != null) AddressSource.ONLINE else null,
                                    width = rendered.width,
                                    height = rendered.height,
                                    createdAt = now,
                                ),
                            )
                            message = "已保存到相册"
                        } catch (t: Throwable) {
                            message = "拍照失败：${t.message}"
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) { Text(if (busy) "处理中…" else "拍照") }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                WatermarkRenderer.draw(
                    drawContext.canvas.nativeCanvas,
                    size.width.toInt(),
                    size.height.toInt(),
                    spec,
                    input,
                )
            }
            message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                )
            }
            if (session == null) {
                Text(
                    "当前没有进行中的班次，照片不关联班次",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private suspend fun buildWatermarkInput(
    graph: AppGraph,
    spec: WatermarkLayoutSpec,
    setting: com.shiftcam.app.data.db.AppSettingEntity?,
    session: com.shiftcam.app.data.db.WorkSessionEntity?,
    total: Int,
): WatermarkInput {
    val s = session ?: return WatermarkInput(
        dateTime = formatDate(System.currentTimeMillis()),
        weather = weatherText(setting),
    )
    val machine = graph.baseDataRepository.getMachine(s.machineId)
    val segments = graph.sessionRepository.listSegments(s.id)
    val workType = segments.lastOrNull { !it.isVoided }?.workType
    val remark = graph.remarkRepository.get(RemarkOwnerType.SESSION, s.id)?.content.orEmpty()
    val point = graph.locationProvider.lastKnown()
    val address = graph.locationProvider.addressOf(point)
    val unit = machine?.readingUnit ?: com.shiftcam.domain.reading.ReadingUnit.FIELD
    return WatermarkInput(
        machineName = s.machineNameSnapshot,
        machineCode = machine?.code.orEmpty(),
        siteName = s.siteNameSnapshot,
        workType = workType?.label.orEmpty(),
        shiftNo = "第${s.shiftNo}班",
        clockIn = clockText(s.startTime),
        clockOut = s.endTime?.let { clockText(it) }.orEmpty(),
        workHours = s.meterHours?.let {
            ReadingFormatter.format(it, unit, machine?.readingUnitCustom, machine?.readingDecimals ?: 1)
        }.orEmpty(),
        count = if (total > 0) "$total 车" else "",
        remark = remark,
        weather = weatherText(setting),
        location = graph.locationProvider.watermarkText(point, address),
        dateTime = formatDate(System.currentTimeMillis()),
    )
}

private fun weatherText(setting: com.shiftcam.app.data.db.AppSettingEntity?): String {
    val shown = WeatherCachePolicy.decide(
        setting?.lastWeatherText,
        setting?.lastWeatherAt,
        System.currentTimeMillis(),
    )
    return (shown as? WeatherCachePolicy.Display.Shown)?.text.orEmpty()
}

private val watermarkClockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun clockText(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(watermarkClockFormatter)
