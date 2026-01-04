package com.example.uvccamerademo

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.uvccamerademo.ui.theme.UVCCameraDemoTheme
import com.jiangdg.ausbc.CameraClient
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.camera.CameraUvcStrategy
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.serenegiant.usb.USBMonitor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UploadScheduler.ensureScheduled(applicationContext)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        setContent {
            UVCCameraDemoTheme {
                MainContent()
            }
        }
    }
}

private sealed interface Screen {
    object Preview : Screen
    object Recordings : Screen
    data class Playback(val item: RecordingItem) : Screen
}

internal data class ResolutionOption(val width: Int, val height: Int)

enum class WorkUiState {
    NONE,
    ACTIVE,
    PAUSED
}

data class WorkInfo(
    val model: String,
    val serial: String,
    val process: String
)

@Composable
private fun MainContent() {
    val context = LocalContext.current
    val view = LocalView.current
    val window = (context as? Activity)?.window
    val repository = remember { RecordingRepository(context) }
    var screen by remember { mutableStateOf<Screen>(Screen.Preview) }
    val isRecordingSessionState = remember { mutableStateOf(false) }
    val isSegmentRecordingState = remember { mutableStateOf(false) }
    val isFinalizingState = remember { mutableStateOf(false) }
    val recordingPathState = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(screen) {
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            UvcPreviewScreen(
                modifier = Modifier.fillMaxSize(),
                recordingRepository = repository,
                onOpenRecordings = { screen = Screen.Recordings },
                isRecordingSessionState = isRecordingSessionState,
                isSegmentRecordingState = isSegmentRecordingState,
                isFinalizingState = isFinalizingState,
                recordingPathState = recordingPathState
            )
            when (val current = screen) {
                Screen.Preview -> Unit
                Screen.Recordings -> RecordingListScreen(
                    modifier = Modifier.fillMaxSize(),
                    repository = repository,
                    onBack = { screen = Screen.Preview },
                    onPlay = { item -> screen = Screen.Playback(item) },
                    recordingPath = recordingPathState.value,
                    isSegmentRecording = isSegmentRecordingState.value,
                    isFinalizing = isFinalizingState.value
                )
                is Screen.Playback -> PlaybackScreen(
                    modifier = Modifier.fillMaxSize(),
                    repository = repository,
                    item = current.item,
                    onBack = { screen = Screen.Recordings },
                    recordingPath = recordingPathState.value,
                    isSegmentRecording = isSegmentRecordingState.value,
                    isFinalizing = isFinalizingState.value
                )
            }
        }
    }
}

@Composable
private fun UvcPreviewScreen(
    modifier: Modifier = Modifier,
    recordingRepository: RecordingRepository,
    onOpenRecordings: () -> Unit,
    isRecordingSessionState: MutableState<Boolean>,
    isSegmentRecordingState: MutableState<Boolean>,
    isFinalizingState: MutableState<Boolean>,
    recordingPathState: MutableState<String?>
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val scope = rememberCoroutineScope()

    val cameraView = remember {
        if (isPreview) {
            null
        } else {
            AspectRatioTextureView(context)
        }
    }
    val selectedResolution = remember {
        ResolutionOption(
            width = BuildConfig.DEFAULT_PREVIEW_WIDTH,
            height = BuildConfig.DEFAULT_PREVIEW_HEIGHT
        )
    }
    val cameraRequest = remember {
        CameraRequest.Builder()
            .setPreviewWidth(selectedResolution.width)
            .setPreviewHeight(selectedResolution.height)
            .create()
    }
    val cameraStrategy = remember {
        if (isPreview) {
            null
        } else {
            CameraUvcStrategy(context)
        }
    }
    val cameraClient = remember {
        if (isPreview || cameraStrategy == null) {
            null
        } else {
            CameraClient.newBuilder(context)
                .setCameraStrategy(cameraStrategy)
                .setCameraRequest(cameraRequest)
                .setEnableGLES(false)
                .build()
        }
    }

    var selectedDeviceId by remember { mutableStateOf<String?>(null) }
    val previewStatus = stringResource(R.string.status_preview_mode)
    val idleStatus = stringResource(R.string.status_idle)
    var statusMessage by remember {
        mutableStateOf(if (isPreview) previewStatus else idleStatus)
    }
    var isCameraOpened by remember { mutableStateOf(false) }
    var isRecordingSession by isRecordingSessionState
    var isSegmentRecording by isSegmentRecordingState
    var isFinalizing by isFinalizingState
    var currentRecordingPath by recordingPathState
    var recordingStartAt by remember { mutableStateOf<Long?>(null) }
    var recordingElapsedMs by remember { mutableStateOf(0L) }
    var pendingOpen by remember { mutableStateOf(false) }
    var pendingRecord by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<HevcRecorder?>(null) }
    var currentSegmentUuid by remember { mutableStateOf<String?>(null) }
    var activeSegmentIntervalMs by remember { mutableStateOf<Long?>(null) }
    var pendingSegmentWorkId by remember { mutableStateOf<String?>(null) }
    var workState by remember { mutableStateOf(WorkUiState.NONE) }
    var currentWorkId by remember { mutableStateOf<String?>(null) }
    var activeWorkInfo by remember { mutableStateOf<WorkInfo?>(null) }
    var workMessage by remember { mutableStateOf<String?>(null) }
    var segmentIntervalMinutes by remember {
        mutableStateOf(SettingsRepository.DEFAULT_SEGMENT_INTERVAL_MINUTES)
    }
    var selectedVideoCodec by remember { mutableStateOf(VideoCodec.HEVC) }
    var qrModel by rememberSaveable { mutableStateOf<String?>(null) }
    var qrSerial by rememberSaveable { mutableStateOf<String?>(null) }
    var qrMessage by remember { mutableStateOf<String?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }
    val contentScrollState = rememberScrollState()
    val qrScanner = remember {
        if (isPreview) {
            null
        } else {
            UvcQrScanner { raw ->
                mainHandler.post {
                    val parsed = parseQrPayload(raw)
                    if (parsed == null) {
                        qrMessage = context.getString(R.string.message_qr_parse_failed)
                    } else {
                        qrModel = parsed.model
                        qrSerial = parsed.serial
                        qrMessage = context.getString(R.string.message_qr_scanned)
                    }
                    showQrScanner = false
                }
            }
        }
    }

    val processRepository = remember { ProcessRepository(context) }
    val settingsRepository = remember { SettingsRepository(context) }
    var processItems by remember { mutableStateOf<List<ProcessItem>>(emptyList()) }
    var processSource by remember { mutableStateOf(ProcessSource.NONE) }
    var selectedProcess by remember { mutableStateOf<ProcessItem?>(null) }
    var processMessage by remember { mutableStateOf<String?>(null) }
    var processApiUrl by rememberSaveable { mutableStateOf("") }
    var showProcessDialog by remember { mutableStateOf(false) }
    val unknownProcess = remember { ProcessItem(id = "UNKNOWN", name = "UNKNOWN") }

    val previewRotation = 0f
    val previewAspectRatio =
        selectedResolution.width.toFloat() / selectedResolution.height.toFloat()

    val previewDataCallBack = remember {
        object : IPreviewDataCallBack {
            override fun onPreviewData(
                data: ByteArray?,
                format: IPreviewDataCallBack.DataFormat
            ) {
                if (data != null && format == IPreviewDataCallBack.DataFormat.NV21) {
                    recorder?.onPreviewFrame(data)
                    if (showQrScanner) {
                        qrScanner?.onFrame(
                            data = data,
                            width = selectedResolution.width,
                            height = selectedResolution.height,
                            rotationDegrees = 0
                        )
                    }
                }
            }
        }
    }

    fun refreshDevices() {
        if (isPreview || cameraStrategy == null) {
            selectedDeviceId = null
            return
        }
        val devices = cameraStrategy.getUsbDeviceList()
        if (devices.isNullOrEmpty()) {
            selectedDeviceId = null
            return
        }
        val currentId = selectedDeviceId
        if (currentId == null || devices.none { it.deviceId.toString() == currentId }) {
            selectedDeviceId = devices.first().deviceId.toString()
        }
    }

    fun openCamera() {
        if (isPreview) {
            statusMessage = context.getString(R.string.status_preview_camera_disabled)
            return
        }
        if (selectedDeviceId == null) {
            statusMessage = context.getString(R.string.status_uvc_device_not_selected)
        } else {
            val view = cameraView ?: return
            val client = cameraClient ?: return
            client.openCamera(view, false)
            client.switchCamera(selectedDeviceId!!)
            statusMessage = context.getString(R.string.status_camera_opening)
        }
    }

    fun stopSegment() {
        if (isSegmentRecording) {
            isSegmentRecording = false
            recorder?.stop()
            isFinalizing = true
            activeSegmentIntervalMs = null
            statusMessage = context.getString(R.string.status_record_stopping)
        }
    }

    fun startSegment(workId: String?) {
        if (isPreview) {
            statusMessage = context.getString(R.string.status_preview_record_disabled)
            isRecordingSession = false
            pendingSegmentWorkId = null
            return
        }
        if (!isCameraOpened) {
            statusMessage = context.getString(R.string.status_open_camera_first)
            isRecordingSession = false
            pendingSegmentWorkId = null
            return
        }
        val file = recordingRepository.createRecordingFile()
        if (file == null) {
            statusMessage = context.getString(R.string.status_storage_unavailable)
            isRecordingSession = false
            pendingSegmentWorkId = null
            return
        }
        val segmentUuid = UUID.randomUUID().toString()
        val recordedAt = System.currentTimeMillis()
        val normalizedInterval = segmentIntervalMinutes.coerceIn(
            SettingsRepository.MIN_SEGMENT_INTERVAL_MINUTES,
            SettingsRepository.MAX_SEGMENT_INTERVAL_MINUTES
        )
        activeSegmentIntervalMs = normalizedInterval * 60_000L
        isFinalizing = false
        currentRecordingPath = file.absolutePath
        currentSegmentUuid = segmentUuid
        var createdRecorder: HevcRecorder? = null
        val callback = object : ICaptureCallBack {
            override fun onBegin() {
                mainHandler.post {
                    recordingStartAt = recordedAt
                    recordingElapsedMs = 0L
                    isSegmentRecording = true
                    statusMessage = if (createdRecorder?.isAudioEnabled == true) {
                        context.getString(R.string.status_recording)
                    } else {
                        context.getString(R.string.status_recording_no_audio)
                    }
                }
            }

            override fun onError(error: String?) {
                val failedUuid = currentSegmentUuid
                val failedPath = currentRecordingPath
                mainHandler.post {
                    isRecordingSession = false
                    isSegmentRecording = false
                    recordingStartAt = null
                    isFinalizing = false
                    recorder = null
                    currentRecordingPath = null
                    currentSegmentUuid = null
                    activeSegmentIntervalMs = null
                    pendingSegmentWorkId = null
                    statusMessage = context.getString(
                        R.string.status_recording_failed,
                        error ?: context.getString(R.string.error_unknown)
                    )
                }
                if (failedUuid != null && failedPath != null) {
                    scope.launch {
                        recordingRepository.finalizeSegment(failedUuid, failedPath)
                    }
                }
            }

            override fun onComplete(path: String?) {
                val finishedUuid = currentSegmentUuid
                val finishedPath = path ?: currentRecordingPath
                mainHandler.post {
                    isSegmentRecording = false
                    recordingStartAt = null
                    isFinalizing = false
                    recorder = null
                    currentRecordingPath = null
                    currentSegmentUuid = null
                    activeSegmentIntervalMs = null
                    statusMessage = context.getString(
                        R.string.status_saved,
                        finishedPath ?: context.getString(R.string.label_unknown_path)
                    )
                    if (isRecordingSession) {
                        val nextWorkId = pendingSegmentWorkId
                            ?: if (workState == WorkUiState.ACTIVE) currentWorkId else null
                        pendingSegmentWorkId = null
                        startSegment(nextWorkId)
                    } else {
                        pendingSegmentWorkId = null
                    }
                }
                if (finishedUuid != null && finishedPath != null) {
                    scope.launch {
                        recordingRepository.finalizeSegment(finishedUuid, finishedPath)
                    }
                }
            }
        }
        createdRecorder = HevcRecorder(
            context = context,
            width = selectedResolution.width,
            height = selectedResolution.height,
            outputFile = file,
            videoMimeType = selectedVideoCodec.mimeType,
            callback = callback
        )
        if (!createdRecorder.start()) {
            statusMessage = context.getString(R.string.status_record_start_failed)
            currentRecordingPath = null
            currentSegmentUuid = null
            activeSegmentIntervalMs = null
            isRecordingSession = false
            pendingSegmentWorkId = null
            return
        }
        isSegmentRecording = true
        recordingElapsedMs = 0L
        recorder = createdRecorder
        scope.launch {
            recordingRepository.insertSegment(segmentUuid, file.absolutePath, recordedAt, workId)
        }
    }

    fun startRecordingSession() {
        if (isRecordingSession) {
            return
        }
        isRecordingSession = true
        pendingSegmentWorkId = if (workState == WorkUiState.ACTIVE) currentWorkId else null
        if (!isSegmentRecording && !isFinalizing) {
            startSegment(pendingSegmentWorkId)
        }
    }

    fun stopRecordingSession() {
        if (!isRecordingSession) {
            return
        }
        isRecordingSession = false
        pendingSegmentWorkId = null
        if (isSegmentRecording) {
            stopSegment()
        }
    }

    fun requestSegmentBoundary(nextWorkId: String?) {
        pendingSegmentWorkId = nextWorkId
        if (!isRecordingSession) {
            return
        }
        if (isSegmentRecording) {
            stopSegment()
        } else if (!isFinalizing) {
            startSegment(nextWorkId)
        }
    }

    fun applyProcessItems(items: List<ProcessItem>, source: ProcessSource, message: String?) {
        processItems = items
        processSource = source
        processMessage = message
        val selected = selectedProcess
        if (selected != null && items.none { it.id == selected.id }) {
            selectedProcess = null
            scope.launch {
                processRepository.saveSelectedProcess(null)
            }
            processMessage = context.getString(R.string.message_process_selection_invalid)
        }
    }

    fun refreshProcesses() {
        scope.launch {
            val url = processApiUrl.trim()
            if (url.isBlank()) {
                processMessage = context.getString(R.string.message_process_api_url_required)
                return@launch
            }
            processRepository.saveApiUrl(url)
            val result = runCatching { processRepository.fetchProcesses(url) }
            val items = result.getOrNull().orEmpty()
            if (result.isSuccess && items.isNotEmpty()) {
                processRepository.saveCachedProcesses(items)
                applyProcessItems(
                    items,
                    ProcessSource.LIVE,
                    context.getString(R.string.message_process_fetch_success)
                )
            } else {
                val cache = processRepository.loadCachedProcesses()
                if (cache != null && processRepository.isCacheValid(cache.fetchedAt) && cache.items.isNotEmpty()) {
                    applyProcessItems(
                        cache.items,
                        ProcessSource.TEMPORARY,
                        context.getString(R.string.message_process_fetch_failed_cache)
                    )
                } else {
                    applyProcessItems(
                        listOf(unknownProcess),
                        ProcessSource.UNKNOWN_ONLY,
                        context.getString(R.string.message_process_fetch_failed_unknown)
                    )
                }
            }
        }
    }

    fun handleStartWork() {
        val model = qrModel?.trim().orEmpty()
        val serial = qrSerial?.trim().orEmpty()
        val process = selectedProcess?.name?.trim().orEmpty()
        if (model.isBlank() || serial.isBlank()) {
            workMessage = context.getString(R.string.message_work_qr_required)
            return
        }
        if (selectedProcess == null || process.isBlank()) {
            workMessage = context.getString(R.string.message_work_process_required)
            return
        }
        workMessage = null
        val startedAt = System.currentTimeMillis()
        val previousWorkId = currentWorkId
        val wasActive = workState != WorkUiState.NONE && previousWorkId != null
        scope.launch {
            if (wasActive) {
                recordingRepository.updateWorkState(previousWorkId!!, WorkState.ENDED, startedAt)
            }
            val newWork = recordingRepository.createWork(model, serial, process, startedAt)
            currentWorkId = newWork.workId
            activeWorkInfo = WorkInfo(model = model, serial = serial, process = process)
            workState = WorkUiState.ACTIVE
            workMessage = context.getString(R.string.message_work_started)
            requestSegmentBoundary(newWork.workId)
        }
    }

    fun handlePauseWork() {
        val workId = currentWorkId ?: return
        if (workState != WorkUiState.ACTIVE) {
            return
        }
        scope.launch {
            recordingRepository.updateWorkState(workId, WorkState.PAUSED, null)
            workState = WorkUiState.PAUSED
            workMessage = context.getString(R.string.message_work_paused)
            requestSegmentBoundary(null)
        }
    }

    fun handleResumeWork() {
        val workId = currentWorkId ?: return
        if (workState != WorkUiState.PAUSED) {
            return
        }
        scope.launch {
            recordingRepository.updateWorkState(workId, WorkState.ACTIVE, null)
            workState = WorkUiState.ACTIVE
            workMessage = context.getString(R.string.message_work_resumed)
            requestSegmentBoundary(workId)
        }
    }

    fun handleEndWork() {
        val workId = currentWorkId ?: return
        if (workState == WorkUiState.NONE) {
            return
        }
        val endedAt = System.currentTimeMillis()
        scope.launch {
            recordingRepository.updateWorkState(workId, WorkState.ENDED, endedAt)
            workState = WorkUiState.NONE
            currentWorkId = null
            activeWorkInfo = null
            workMessage = context.getString(R.string.message_work_ended)
            requestSegmentBoundary(null)
        }
    }

    val cameraPermissionLauncher = if (isPreview) {
        null
    } else {
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted && pendingOpen) {
                pendingOpen = false
                openCamera()
            } else if (!granted) {
                pendingOpen = false
                statusMessage = context.getString(R.string.status_camera_permission_required)
            }
        }
    }

    val recordPermissionLauncher = if (isPreview) {
        null
    } else {
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted && pendingRecord) {
                pendingRecord = false
                startRecordingSession()
            } else if (!granted) {
                pendingRecord = false
                statusMessage = context.getString(R.string.status_audio_permission_required)
            }
        }
    }

    if (!isPreview && cameraClient != null && cameraStrategy != null) {
        DisposableEffect(lifecycleOwner, cameraClient, cameraStrategy) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> cameraStrategy.register()
                    Lifecycle.Event.ON_STOP -> {
                        if (!isRecordingSession && !isFinalizing) {
                            cameraClient.closeCamera()
                            isCameraOpened = false
                        }
                    }
                    Lifecycle.Event.ON_DESTROY -> {
                        if (!isRecordingSession && !isFinalizing) {
                            cameraStrategy.unRegister()
                        }
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                if (!isRecordingSession && !isFinalizing) {
                    cameraClient.closeCamera()
                    isCameraOpened = false
                    cameraStrategy.unRegister()
                }
            }
        }

        DisposableEffect(cameraStrategy) {
            val callback = object : IDeviceConnectCallBack {
                override fun onAttachDev(device: UsbDevice?) {
                    if (device == null) return
                    mainHandler.post {
                        statusMessage = context.getString(
                            R.string.status_device_attached,
                            device.deviceName
                        )
                        refreshDevices()
                    }
                }

                override fun onDetachDec(device: UsbDevice?) {
                    if (device == null) return
                    mainHandler.post {
                        statusMessage = context.getString(
                            R.string.status_device_detached,
                            device.deviceName
                        )
                        if (selectedDeviceId == device.deviceId.toString()) {
                            selectedDeviceId = null
                        }
                        stopRecordingSession()
                        cameraClient.closeCamera()
                        isCameraOpened = false
                        refreshDevices()
                    }
                }

                override fun onConnectDev(
                    device: UsbDevice?,
                    ctrlBlock: USBMonitor.UsbControlBlock?
                ) {
                    if (device == null) return
                    mainHandler.post {
                        statusMessage = context.getString(
                            R.string.status_device_in_use,
                            device.deviceName
                        )
                        isCameraOpened = cameraClient.isCameraOpened() == true
                    }
                }

                override fun onDisConnectDec(
                    device: UsbDevice?,
                    ctrlBlock: USBMonitor.UsbControlBlock?
                ) {
                    if (device == null) return
                    mainHandler.post {
                        statusMessage = context.getString(
                            R.string.status_device_disconnected,
                            device.deviceName
                        )
                        stopRecordingSession()
                        isCameraOpened = false
                    }
                }

                override fun onCancelDev(device: UsbDevice?) {
                    if (device == null) return
                    mainHandler.post {
                        statusMessage = context.getString(R.string.status_usb_permission_denied)
                        isCameraOpened = false
                    }
                }
            }
            cameraStrategy.setDeviceConnectStatusListener(callback)
            onDispose { }
        }

        LaunchedEffect(Unit) {
            cameraClient.addPreviewDataCallBack(previewDataCallBack)
            refreshDevices()
        }

        LaunchedEffect(selectedDeviceId, isCameraOpened) {
            if (isCameraOpened && selectedDeviceId != null) {
                cameraClient.switchCamera(selectedDeviceId!!)
            }
        }
    }

    LaunchedEffect(Unit) {
        processApiUrl = processRepository.loadApiUrl()
        selectedProcess = processRepository.loadSelectedProcess()
        val cache = processRepository.loadCachedProcesses()
        if (cache != null && processRepository.isCacheValid(cache.fetchedAt) && cache.items.isNotEmpty()) {
            processItems = cache.items
            processSource = ProcessSource.CACHE
        }
        segmentIntervalMinutes = settingsRepository.loadSegmentIntervalMinutes()
        selectedVideoCodec = settingsRepository.loadVideoCodec()
        val selected = selectedProcess
        if (selected != null && processItems.isNotEmpty() && processItems.none { it.id == selected.id }) {
            selectedProcess = null
            processRepository.saveSelectedProcess(null)
            processMessage = context.getString(R.string.message_process_selection_invalid)
        }
    }

    DisposableEffect(qrScanner) {
        onDispose { qrScanner?.release() }
    }

    LaunchedEffect(showQrScanner) {
        qrScanner?.setActive(showQrScanner)
        if (showQrScanner) {
            contentScrollState.animateScrollTo(0)
        }
    }

    LaunchedEffect(isCameraOpened) {
        if (!isCameraOpened && showQrScanner) {
            showQrScanner = false
        }
    }

    LaunchedEffect(isSegmentRecording, recordingStartAt) {
        if (isSegmentRecording && recordingStartAt != null) {
            while (isSegmentRecording) {
                recordingElapsedMs = System.currentTimeMillis() - (recordingStartAt ?: 0L)
                delay(1000L)
            }
        } else {
            recordingElapsedMs = 0L
        }
    }

    LaunchedEffect(
        isSegmentRecording,
        currentSegmentUuid,
        activeSegmentIntervalMs,
        isRecordingSession,
        recordingStartAt
    ) {
        val intervalMs = activeSegmentIntervalMs ?: return@LaunchedEffect
        val segmentId = currentSegmentUuid ?: return@LaunchedEffect
        val startedAt = recordingStartAt ?: return@LaunchedEffect
        if (!isSegmentRecording || !isRecordingSession) {
            return@LaunchedEffect
        }
        val elapsed = System.currentTimeMillis() - startedAt
        val remaining = intervalMs - elapsed
        if (remaining > 0L) {
            delay(remaining)
        }
        if (isRecordingSession && isSegmentRecording && currentSegmentUuid == segmentId) {
            val nextWorkId = if (workState == WorkUiState.ACTIVE) currentWorkId else null
            requestSegmentBoundary(nextWorkId)
        }
    }

    fun handleOpen() {
        if (isPreview) {
            statusMessage = context.getString(R.string.status_preview_camera_disabled)
            return
        }
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCameraPermission) {
            openCamera()
        } else {
            pendingOpen = true
            cameraPermissionLauncher?.launch(Manifest.permission.CAMERA)
        }
    }

    fun handleClose() {
        stopRecordingSession()
        cameraClient?.closeCamera()
        isCameraOpened = false
        statusMessage = context.getString(R.string.status_closed)
    }

    fun handleToggleRecord() {
        if (isPreview) {
            statusMessage = context.getString(R.string.status_preview_record_disabled)
            return
        }
        if (isRecordingSession) {
            stopRecordingSession()
            return
        }
        val hasRecordPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasRecordPermission) {
            startRecordingSession()
        } else {
            pendingRecord = true
            recordPermissionLauncher?.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val previewContent: @Composable (Modifier) -> Unit = { contentModifier ->
        if (isPreview || cameraView == null) {
            Box(
                modifier = contentModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(text = stringResource(R.string.label_preview))
            }
        } else {
            val previewView = requireNotNull(cameraView)
            AndroidView(
                modifier = contentModifier,
                factory = { previewView },
                update = { view ->
                    view.rotation = previewRotation
                }
            )
        }
    }

    val canStartWork =
        !qrModel.isNullOrBlank() && !qrSerial.isNullOrBlank() && selectedProcess != null

    if (showProcessDialog) {
        AlertDialog(
            onDismissRequest = { showProcessDialog = false },
            title = { Text(stringResource(R.string.dialog_process_select_title)) },
            text = {
                if (processItems.isEmpty()) {
                    Text(stringResource(R.string.label_process_empty))
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        processItems.forEach { item ->
                            TextButton(
                                onClick = {
                                    selectedProcess = item
                                    scope.launch {
                                        processRepository.saveSelectedProcess(item)
                                    }
                                    processMessage = null
                                    showProcessDialog = false
                                }
                            ) {
                                Text("${item.id} ${item.name}")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProcessDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        UvcPreviewScreenContent(
            modifier = Modifier.fillMaxSize(),
            previewAspectRatio = previewAspectRatio,
            statusMessage = statusMessage,
            isCameraOpened = isCameraOpened,
            isRecordingSession = isRecordingSession,
            isSegmentRecording = isSegmentRecording,
            isFinalizing = isFinalizing,
            recordingElapsedMs = recordingElapsedMs,
            selectedResolution = selectedResolution,
            workState = workState,
            currentWorkId = currentWorkId,
            activeWorkInfo = activeWorkInfo,
            qrModel = qrModel,
            qrSerial = qrSerial,
            qrMessage = qrMessage,
            onScanQr = {
                if (showQrScanner) {
                    showQrScanner = false
                } else if (!isCameraOpened) {
                    qrMessage = context.getString(R.string.message_qr_requires_uvc)
                } else {
                    qrMessage = null
                    showQrScanner = true
                }
            },
            onClearQr = {
                qrModel = null
                qrSerial = null
                qrMessage = context.getString(R.string.message_qr_cleared)
            },
            processSource = processSource,
            selectedProcess = selectedProcess,
            processMessage = processMessage,
            processApiUrl = processApiUrl,
            onProcessApiUrlChange = { processApiUrl = it },
            onFetchProcesses = { refreshProcesses() },
            onSelectProcess = { showProcessDialog = true },
            segmentIntervalMinutes = segmentIntervalMinutes,
            onSegmentIntervalChange = { minutes ->
                segmentIntervalMinutes = minutes
            },
            onSegmentIntervalChangeFinished = {
                scope.launch {
                    settingsRepository.saveSegmentIntervalMinutes(segmentIntervalMinutes)
                }
            },
            selectedVideoCodec = selectedVideoCodec,
            onVideoCodecChange = { codec ->
                selectedVideoCodec = codec
                scope.launch {
                    settingsRepository.saveVideoCodec(codec)
                }
            },
            onWorkStart = { handleStartWork() },
            onWorkPause = { handlePauseWork() },
            onWorkResume = { handleResumeWork() },
            onWorkEnd = { handleEndWork() },
            canStartWork = canStartWork,
            workMessage = workMessage,
            onOpen = { handleOpen() },
            onClose = { handleClose() },
            onToggleRecord = { handleToggleRecord() },
            onOpenRecordings = onOpenRecordings,
            previewContent = previewContent,
            scrollState = contentScrollState,
            isScrollEnabled = !showQrScanner,
            showQrScanner = showQrScanner
        )
    }
}

@Composable
internal fun UvcPreviewScreenContent(
    modifier: Modifier = Modifier,
    previewAspectRatio: Float,
    statusMessage: String,
    isCameraOpened: Boolean,
    isRecordingSession: Boolean,
    isSegmentRecording: Boolean,
    isFinalizing: Boolean,
    recordingElapsedMs: Long,
    selectedResolution: ResolutionOption,
    workState: WorkUiState,
    currentWorkId: String?,
    activeWorkInfo: WorkInfo?,
    qrModel: String?,
    qrSerial: String?,
    qrMessage: String?,
    onScanQr: () -> Unit,
    onClearQr: () -> Unit,
    processSource: ProcessSource,
    selectedProcess: ProcessItem?,
    processMessage: String?,
    processApiUrl: String,
    onProcessApiUrlChange: (String) -> Unit,
    onFetchProcesses: () -> Unit,
    onSelectProcess: () -> Unit,
    segmentIntervalMinutes: Int,
    onSegmentIntervalChange: (Int) -> Unit,
    onSegmentIntervalChangeFinished: () -> Unit,
    selectedVideoCodec: VideoCodec,
    onVideoCodecChange: (VideoCodec) -> Unit,
    onWorkStart: () -> Unit,
    onWorkPause: () -> Unit,
    onWorkResume: () -> Unit,
    onWorkEnd: () -> Unit,
    canStartWork: Boolean,
    workMessage: String?,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onToggleRecord: () -> Unit,
    onOpenRecordings: () -> Unit,
    previewContent: @Composable (Modifier) -> Unit,
    scrollState: ScrollState,
    isScrollEnabled: Boolean,
    showQrScanner: Boolean
) {
    val backgroundBrush = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    )
    val cameraStateLabel = stringResource(
        if (isCameraOpened) R.string.pill_camera_open else R.string.pill_camera_closed
    )
    val recordLabel = when {
        isFinalizing -> stringResource(R.string.pill_record_finalizing)
        isSegmentRecording -> stringResource(
            R.string.pill_recording_with_time,
            formatDuration(recordingElapsedMs)
        )
        isRecordingSession -> stringResource(R.string.status_recording)
        else -> stringResource(R.string.status_idle)
    }
    val selectedResolutionLabel = stringResource(
        R.string.format_resolution,
        selectedResolution.width,
        selectedResolution.height
    )
    val cameraContainer = if (isCameraOpened) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val cameraContent = if (isCameraOpened) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val recordContainer = if (isRecordingSession || isFinalizing) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val recordContent = if (isRecordingSession || isFinalizing) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val recordButtonColors = if (isRecordingSession) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    } else {
        ButtonDefaults.buttonColors()
    }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState, enabled = isScrollEnabled)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = stringResource(R.string.label_camera_status, cameraStateLabel),
                            containerColor = cameraContainer,
                            contentColor = cameraContent
                        )
                        StatusPill(
                            text = stringResource(R.string.label_record_status, recordLabel),
                            containerColor = recordContainer,
                            contentColor = recordContent
                        )
                    }
                    if (statusMessage.isNotBlank()) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = selectedResolutionLabel,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(previewAspectRatio)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        previewContent(Modifier.fillMaxSize())
                        if (showQrScanner) {
                            androidx.compose.foundation.layout.BoxWithConstraints(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val frameSize = minOf(maxWidth, maxHeight) * 0.65f
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(frameSize)
                                        .border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            val workStateLabel = when (workState) {
                WorkUiState.NONE -> stringResource(R.string.pill_work_none)
                WorkUiState.ACTIVE -> stringResource(R.string.pill_work_active)
                WorkUiState.PAUSED -> stringResource(R.string.pill_work_paused)
            }
            val workContainer = when (workState) {
                WorkUiState.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                WorkUiState.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
                WorkUiState.NONE -> MaterialTheme.colorScheme.surfaceVariant
            }
            val workContent = when (workState) {
                WorkUiState.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
                WorkUiState.PAUSED -> MaterialTheme.colorScheme.onTertiaryContainer
                WorkUiState.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_work),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = stringResource(R.string.label_work_status, workStateLabel),
                            containerColor = workContainer,
                            contentColor = workContent
                        )
                    }
                    if (currentWorkId != null) {
                        Text(
                            text = stringResource(R.string.label_work_id, currentWorkId),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (activeWorkInfo != null) {
                        Text(
                            text = stringResource(
                                R.string.label_work_info,
                                activeWorkInfo.model,
                                activeWorkInfo.serial,
                                activeWorkInfo.process
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val qrStateLabel = if (!qrModel.isNullOrBlank() && !qrSerial.isNullOrBlank()) {
                        stringResource(R.string.pill_qr_ready)
                    } else {
                        stringResource(R.string.pill_qr_missing)
                    }
                    StatusPill(
                        text = stringResource(R.string.label_qr_status, qrStateLabel),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onScanQr
                        ) {
                            Text(stringResource(R.string.action_scan_qr))
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onClearQr
                        ) {
                            Text(stringResource(R.string.action_clear_qr))
                        }
                    }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = qrModel.orEmpty(),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.hint_model)) },
                        placeholder = { Text(stringResource(R.string.label_qr_unscanned)) },
                        readOnly = true,
                        singleLine = true
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = qrSerial.orEmpty(),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.hint_serial)) },
                        placeholder = { Text(stringResource(R.string.label_qr_unscanned)) },
                        readOnly = true,
                        singleLine = true
                    )
                    if (qrMessage != null) {
                        Text(
                            text = qrMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val processStateLabel = when (processSource) {
                        ProcessSource.LIVE -> stringResource(R.string.pill_process_live)
                        ProcessSource.CACHE -> stringResource(R.string.pill_process_cached)
                        ProcessSource.TEMPORARY -> stringResource(R.string.pill_process_temporary)
                        ProcessSource.UNKNOWN_ONLY -> stringResource(R.string.pill_process_unknown)
                        ProcessSource.NONE -> stringResource(R.string.pill_process_missing)
                    }
                    StatusPill(
                        text = stringResource(R.string.label_process_status, processStateLabel),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    val selectedProcessLabel = selectedProcess?.let {
                        "${it.id} ${it.name}"
                    } ?: stringResource(R.string.label_process_unselected)
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = selectedProcessLabel,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.label_process)) },
                        readOnly = true,
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onFetchProcesses
                        ) {
                            Text(stringResource(R.string.action_process_fetch))
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onSelectProcess
                        ) {
                            Text(stringResource(R.string.action_process_select))
                        }
                    }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = processApiUrl,
                        onValueChange = onProcessApiUrlChange,
                        label = { Text(stringResource(R.string.label_process_api_url)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        )
                    )
                    if (processMessage != null) {
                        Text(
                            text = processMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onWorkStart,
                            enabled = canStartWork
                        ) {
                            Text(stringResource(R.string.action_work_start))
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onWorkPause,
                            enabled = workState == WorkUiState.ACTIVE
                        ) {
                            Text(stringResource(R.string.action_work_pause))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onWorkResume,
                            enabled = workState == WorkUiState.PAUSED
                        ) {
                            Text(stringResource(R.string.action_work_resume))
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onWorkEnd,
                            enabled = workState != WorkUiState.NONE
                        ) {
                            Text(stringResource(R.string.action_work_end))
                        }
                    }
                    if (workMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = workMessage,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                val minInterval = SettingsRepository.MIN_SEGMENT_INTERVAL_MINUTES
                val maxInterval = SettingsRepository.MAX_SEGMENT_INTERVAL_MINUTES
                val coercedInterval = segmentIntervalMinutes.coerceIn(minInterval, maxInterval)
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_settings),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.label_segment_interval),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.label_segment_interval_value, coercedInterval),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = coercedInterval.toFloat(),
                        onValueChange = { raw ->
                            val rounded = raw.roundToInt().coerceIn(minInterval, maxInterval)
                            onSegmentIntervalChange(rounded)
                        },
                        onValueChangeFinished = onSegmentIntervalChangeFinished,
                        valueRange = minInterval.toFloat()..maxInterval.toFloat(),
                        steps = maxInterval - minInterval - 1
                    )
                    Text(
                        text = stringResource(R.string.label_codec),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isHevc = selectedVideoCodec == VideoCodec.HEVC
                        val isAvc = selectedVideoCodec == VideoCodec.AVC
                        if (isHevc) {
                            Button(onClick = { onVideoCodecChange(VideoCodec.HEVC) }) {
                                Text(stringResource(VideoCodec.HEVC.labelRes))
                            }
                        } else {
                            OutlinedButton(onClick = { onVideoCodecChange(VideoCodec.HEVC) }) {
                                Text(stringResource(VideoCodec.HEVC.labelRes))
                            }
                        }
                        if (isAvc) {
                            Button(onClick = { onVideoCodecChange(VideoCodec.AVC) }) {
                                Text(stringResource(VideoCodec.AVC.labelRes))
                            }
                        } else {
                            OutlinedButton(onClick = { onVideoCodecChange(VideoCodec.AVC) }) {
                                Text(stringResource(VideoCodec.AVC.labelRes))
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.message_setting_next_segment),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onOpen,
                            enabled = !isCameraOpened
                        ) {
                            Text(stringResource(R.string.action_open))
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onClose,
                            enabled = isCameraOpened && !isFinalizing
                        ) {
                            Text(stringResource(R.string.action_close))
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onToggleRecord,
                            enabled = isCameraOpened && !isFinalizing,
                            colors = recordButtonColors
                        ) {
                            val recordText = if (isRecordingSession) {
                                stringResource(R.string.action_stop)
                            } else {
                                stringResource(R.string.action_record)
                            }
                            Text(recordText)
                        }
                    }
                    FilledTonalButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenRecordings
                    ) {
                        Text(stringResource(R.string.label_recordings))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun RecordingGroupHeader(title: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
private fun WorkHeaderCard(work: WorkEntity, segmentCount: Int) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.label_work_info,
                    work.model,
                    work.serial,
                    work.process
                ),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.label_work_id, work.workId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.label_work_period,
                    formatDateTime(work.startedAt),
                    work.endedAt?.let { formatDateTime(it) }
                        ?: stringResource(R.string.label_work_in_progress)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.label_work_segment_count, segmentCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecordingItemCard(
    item: RecordingItem,
    isLocked: Boolean,
    onPlay: (RecordingItem) -> Unit,
    onDelete: (RecordingItem) -> Unit,
    onAssign: (() -> Unit)?
) {
    val isPlayable = !isLocked
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isPlayable) { onPlay(item) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = item.name, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.format_recording_meta,
                    formatDateTime(item.createdAt),
                    formatFileSize(item.sizeBytes),
                    formatDuration(item.durationMs)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val workLabel = if (item.workId.isNullOrBlank()) {
                stringResource(R.string.label_work_unassigned)
            } else {
                stringResource(R.string.label_work_id, item.workId)
            }
            Text(
                text = workLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.segmentIndex != null) {
                Text(
                    text = stringResource(R.string.label_segment_index, item.segmentIndex),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!item.model.isNullOrBlank() &&
                !item.serial.isNullOrBlank() &&
                !item.process.isNullOrBlank()
            ) {
                Text(
                    text = stringResource(
                        R.string.label_work_info,
                        item.model,
                        item.serial,
                        item.process
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPlay(item) }, enabled = isPlayable) {
                    Text(stringResource(R.string.action_play))
                }
                if (onAssign != null) {
                    OutlinedButton(
                        onClick = onAssign,
                        enabled = item.segmentUuid != null
                    ) {
                        Text(stringResource(R.string.action_assign))
                    }
                }
                OutlinedButton(onClick = { onDelete(item) }) {
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }
}


@Composable
private fun RecordingListScreen(
    modifier: Modifier = Modifier,
    repository: RecordingRepository,
    onBack: () -> Unit,
    onPlay: (RecordingItem) -> Unit,
    recordingPath: String?,
    isSegmentRecording: Boolean,
    isFinalizing: Boolean
) {
    val context = LocalContext.current
    var recordingGroups by remember { mutableStateOf(RecordingGroups(emptyList(), emptyList())) }
    var message by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<RecordingItem?>(null) }
    var assignTarget by remember { mutableStateOf<RecordingItem?>(null) }
    var availableWorks by remember { mutableStateOf<List<WorkEntity>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        recordingGroups = repository.loadRecordingGroups()
    }

    LaunchedEffect(assignTarget) {
        if (assignTarget != null) {
            availableWorks = repository.loadWorks()
        }
    }

    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(target.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleted = repository.deleteRecording(target)
                        message = if (deleted) {
                            context.getString(R.string.message_deleted, target.name)
                        } else {
                            context.getString(R.string.message_delete_failed)
                        }
                        if (deleted) {
                            scope.launch {
                                recordingGroups = repository.loadRecordingGroups()
                            }
                        }
                        deleteTarget = null
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (assignTarget != null) {
        val target = assignTarget!!
        AlertDialog(
            onDismissRequest = { assignTarget = null },
            title = { Text(stringResource(R.string.dialog_assign_title)) },
            text = {
                if (availableWorks.isEmpty()) {
                    Text(stringResource(R.string.label_no_work_available))
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        availableWorks.forEach { work ->
                            TextButton(
                                onClick = {
                                    val segmentUuid = target.segmentUuid
                                    if (segmentUuid == null) {
                                        message = context.getString(R.string.message_assign_failed)
                                        assignTarget = null
                                        return@TextButton
                                    }
                                    scope.launch {
                                        val success = repository.assignSegmentToWork(segmentUuid, work.workId)
                                        message = if (success) {
                                            context.getString(R.string.message_assign_success)
                                        } else {
                                            context.getString(R.string.message_assign_failed)
                                        }
                                        if (success) {
                                            recordingGroups = repository.loadRecordingGroups()
                                        }
                                        assignTarget = null
                                    }
                                }
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(
                                            R.string.label_work_info,
                                            work.model,
                                            work.serial,
                                            work.process
                                        )
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.label_work_period,
                                            formatDateTime(work.startedAt),
                                            work.endedAt?.let { formatDateTime(it) }
                                                ?: stringResource(R.string.label_work_in_progress)
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { assignTarget = null }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    val backgroundBrush = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.label_recordings),
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = stringResource(R.string.label_recordings_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = onBack
                    ) {
                        Text(stringResource(R.string.action_back))
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            message = null
                            scope.launch {
                                recordingGroups = repository.loadRecordingGroups()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_refresh))
                    }
                }
            }
            if (message != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = message!!,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            val hasRecordings =
                recordingGroups.unassigned.isNotEmpty() || recordingGroups.workGroups.isNotEmpty()
            if (!hasRecordings) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_no_recordings),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (recordingGroups.unassigned.isNotEmpty()) {
                        item {
                            RecordingGroupHeader(
                                title = stringResource(R.string.label_work_group_unassigned)
                            )
                        }
                        items(recordingGroups.unassigned, key = { it.path }) { item ->
                            val isLocked =
                                recordingPath != null &&
                                    recordingPath == item.path &&
                                    (isSegmentRecording || isFinalizing)
                            RecordingItemCard(
                                item = item,
                                isLocked = isLocked,
                                onPlay = onPlay,
                                onDelete = { deleteTarget = it },
                                onAssign = { assignTarget = item }
                            )
                        }
                    }
                    recordingGroups.workGroups.forEach { group ->
                        item(key = "work-${group.work.workId}") {
                            WorkHeaderCard(work = group.work, segmentCount = group.segments.size)
                        }
                        items(group.segments, key = { it.path }) { item ->
                            val isLocked =
                                recordingPath != null &&
                                    recordingPath == item.path &&
                                    (isSegmentRecording || isFinalizing)
                            RecordingItemCard(
                                item = item,
                                isLocked = isLocked,
                                onPlay = onPlay,
                                onDelete = { deleteTarget = it },
                                onAssign = null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackScreen(
    modifier: Modifier = Modifier,
    repository: RecordingRepository,
    item: RecordingItem,
    onBack: () -> Unit,
    recordingPath: String?,
    isSegmentRecording: Boolean,
    isFinalizing: Boolean
) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = (context as? Activity)?.window
    val scope = rememberCoroutineScope()
    var deleteConfirm by remember { mutableStateOf(false) }
    var playableItem by remember { mutableStateOf(item) }
    var isRepairing by remember { mutableStateOf(true) }
    var playbackToken by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attemptedRepair by remember { mutableStateOf(false) }
    var usePlatformPlayer by remember { mutableStateOf(false) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    val isRecordingSegment =
        recordingPath != null &&
            recordingPath == item.path &&
            (isSegmentRecording || isFinalizing)

    LaunchedEffect(item.path, isRecordingSegment) {
        if (isRecordingSegment) {
            playableItem = item
            isRepairing = false
            errorMessage = context.getString(R.string.error_playback_recording_in_progress)
            usePlatformPlayer = false
            return@LaunchedEffect
        }
        isRepairing = true
        errorMessage = null
        attemptedRepair = false
        usePlatformPlayer = false
        val repaired = repository.prepareForPlayback(item)
        playableItem = repaired
        playbackToken += 1
        isRepairing = false
    }

    val exoPlayer = remember(
        playableItem.path,
        playbackToken,
        isRepairing,
        usePlatformPlayer,
        isRecordingSegment
    ) {
        if (isRepairing || usePlatformPlayer || isRecordingSegment) {
            null
        } else {
            ExoPlayer.Builder(context)
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(File(playableItem.path))))
                    prepare()
                    playWhenReady = true
                }
        }
    }

    if (exoPlayer != null) {
        DisposableEffect(exoPlayer) {
            val listener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (attemptedRepair) {
                        errorMessage = context.getString(
                            R.string.error_exoplayer,
                            error.errorCodeName
                        )
                        usePlatformPlayer = true
                        return
                    }
                    attemptedRepair = true
                    errorMessage = context.getString(R.string.error_repairing)
                    exoPlayer.stop()
                    scope.launch {
                        isRepairing = true
                        val repaired = repository.forceVideoOnlyRepair(playableItem)
                        if (repaired != null) {
                            playableItem = repaired
                            playbackToken += 1
                            errorMessage = null
                        } else {
                            errorMessage = context.getString(
                                R.string.error_playback_failed,
                                error.errorCodeName
                            )
                            usePlatformPlayer = true
                        }
                        isRepairing = false
                    }
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        }
    }

    DisposableEffect(usePlatformPlayer) {
        onDispose {
            videoViewRef?.stopPlayback()
            videoViewRef = null
        }
    }

    DisposableEffect(isFullscreen, window) {
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, !isFullscreen)
            val controller = WindowInsetsControllerCompat(window, view)
            if (isFullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = WindowInsetsControllerCompat(window, view)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    @Composable
    fun PlayerSurface(surfaceModifier: Modifier) {
        if (usePlatformPlayer) {
            AndroidView(
                modifier = surfaceModifier,
                factory = { viewContext ->
                    VideoView(viewContext).apply {
                        val controller = MediaController(viewContext)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setOnPreparedListener { player ->
                            player.isLooping = false
                            start()
                        }
                        setOnErrorListener { _, what, extra ->
                            errorMessage = context.getString(
                                R.string.error_playback_failed_media,
                                what,
                                extra
                            )
                            true
                        }
                        setVideoURI(Uri.fromFile(File(playableItem.path)))
                        tag = playableItem.path
                    }.also { videoViewRef = it }
                },
                update = { view ->
                    if (view.tag != playableItem.path) {
                        view.setVideoURI(Uri.fromFile(File(playableItem.path)))
                        view.tag = playableItem.path
                    }
                    videoViewRef = view
                    if (!view.isPlaying && !isRepairing) {
                        view.start()
                    }
                }
            )
        } else if (exoPlayer != null) {
            AndroidView(
                modifier = surfaceModifier,
                factory = {
                    LayoutInflater.from(it)
                        .inflate(R.layout.view_player, null, false) as PlayerView
                },
                update = { view ->
                    if (view.player !== exoPlayer) {
                        view.player = exoPlayer
                    }
                    view.useController = true
                }
            )
        }
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(item.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        exoPlayer?.stop()
                        videoViewRef?.stopPlayback()
                        repository.deleteRecording(item)
                        deleteConfirm = false
                        onBack()
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (isFullscreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            PlayerSurface(surfaceModifier = Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
                if (isRepairing) {
                    Text(
                        text = stringResource(R.string.label_preparing_video),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            OutlinedButton(
                onClick = { isFullscreen = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.action_exit))
            }
        }
    } else {
        val backgroundBrush = Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        )
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.label_playback),
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = playableItem.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isRepairing) {
                        StatusPill(
                            text = stringResource(R.string.pill_preparing),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (usePlatformPlayer) {
                        StatusPill(
                            text = stringResource(R.string.pill_system_player),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        PlayerSurface(surfaceModifier = Modifier.fillMaxSize())
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = onBack
                    ) {
                        Text(stringResource(R.string.action_back))
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { deleteConfirm = true }
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { isFullscreen = true }
                    ) {
                        Text(stringResource(R.string.action_fullscreen))
                    }
                }
            }
        }
    }
}

@Composable
private fun formatDateTime(timestamp: Long): String {
    val pattern = stringResource(R.string.format_datetime)
    val formatter = SimpleDateFormat(pattern, Locale.getDefault())
    return formatter.format(Date(timestamp))
}

@Composable
private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) {
        return stringResource(R.string.format_file_size_bytes, bytes)
    }
    val kb = bytes / 1024.0
    if (kb < 1024.0) {
        return stringResource(R.string.format_file_size_kb, kb)
    }
    val mb = kb / 1024.0
    return stringResource(R.string.format_file_size_mb, mb)
}

@Composable
private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) {
        return stringResource(R.string.format_duration_unknown)
    }
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        stringResource(R.string.format_duration_hms, hours, minutes, seconds)
    } else {
        stringResource(R.string.format_duration_mmss, minutes, seconds)
    }
}

@Preview(showBackground = true)
@Composable
fun MainContentPreview() {
    UvcPreviewScreenPreview()
}
