package com.example.uvccamerademo

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceHolder
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
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
import com.jiangdg.ausbc.render.env.RotateType
import com.jiangdg.ausbc.widget.AspectRatioSurfaceView
import com.serenegiant.usb.USBMonitor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UVCCameraDemoTheme {
                MainContent()
            }
        }
    }
}

private const val TAG = "UvcDemo"
private const val RESOLUTION_SWITCH_IGNORE_MS = 2000L

private sealed interface Screen {
    object Preview : Screen
    object Recordings : Screen
    data class Playback(val item: RecordingItem) : Screen
}

internal data class ResolutionOption(val width: Int, val height: Int)

internal data class UvcDeviceInfo(
    val id: String,
    val name: String,
    val vendorId: Int,
    val productId: Int
)

@Composable
private fun MainContent() {
    val context = LocalContext.current
    val repository = remember { RecordingRepository(context) }
    var screen by remember { mutableStateOf<Screen>(Screen.Preview) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        when (val current = screen) {
            Screen.Preview -> UvcPreviewScreen(
                modifier = Modifier.padding(innerPadding),
                recordingRepository = repository,
                onOpenRecordings = { screen = Screen.Recordings }
            )
            Screen.Recordings -> RecordingListScreen(
                modifier = Modifier.padding(innerPadding),
                repository = repository,
                onBack = { screen = Screen.Preview },
                onPlay = { item -> screen = Screen.Playback(item) }
            )
            is Screen.Playback -> PlaybackScreen(
                modifier = Modifier.padding(innerPadding),
                repository = repository,
                item = current.item,
                onBack = { screen = Screen.Recordings }
            )
        }
    }
}

@Composable
private fun UvcPreviewScreen(
    modifier: Modifier = Modifier,
    recordingRepository: RecordingRepository,
    onOpenRecordings: () -> Unit
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val configuration = LocalConfiguration.current
    val usbManager = remember(context) {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    val cameraView = remember {
        if (isPreview) {
            null
        } else {
            AspectRatioSurfaceView(context)
        }
    }
    val cameraRequest = remember {
        CameraRequest.Builder()
            .setPreviewWidth(1920)
            .setPreviewHeight(1080)
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
                .setDefaultRotateType(RotateType.ANGLE_0)
                .openDebug(true)
                .build()
        }
    }

    val deviceList = remember { mutableStateListOf<UvcDeviceInfo>() }
    val deviceById = remember { mutableMapOf<String, UsbDevice>() }
    var selectedDeviceId by remember { mutableStateOf<String?>(null) }
    val previewStatus = stringResource(R.string.status_preview_mode)
    val idleStatus = stringResource(R.string.status_idle)
    var statusMessage by remember {
        mutableStateOf(if (isPreview) previewStatus else idleStatus)
    }
    var isCameraOpened by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isFinalizing by remember { mutableStateOf(false) }
    var recordingStartAt by remember { mutableStateOf<Long?>(null) }
    var recordingElapsedMs by remember { mutableStateOf(0L) }
    var pendingOpen by remember { mutableStateOf(false) }
    var pendingRecord by remember { mutableStateOf(false) }
    var isStrategyActive by remember(lifecycleOwner) {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
    }
    var isOpening by remember { mutableStateOf(false) }
    val openRequestIssued = remember { AtomicBoolean(false) }
    var hasIssuedSwitchRequest by remember { mutableStateOf(false) }
    var isPreviewViewReady by remember { mutableStateOf(false) }
    var hasRequestedUsbPermission by remember { mutableStateOf(false) }
    var usbPermissionDenied by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<HevcRecorder?>(null) }
    val previewFrameLogged = remember { AtomicBoolean(false) }
    val pendingReopenRef = remember { arrayOfNulls<Runnable>(1) }
    var isResolutionSwitching by remember { mutableStateOf(false) }
    var lastResolutionSwitchAt by remember { mutableStateOf(0L) }
    val resolutionRecoveryRef = remember { arrayOfNulls<Runnable>(1) }
    var lastOpenAttemptAt by remember { mutableStateOf(0L) }

    val resolutionOptions = remember {
        listOf(
            ResolutionOption(640, 480),
            ResolutionOption(1280, 720),
            ResolutionOption(1920, 1080)
        )
    }
    val defaultResolution =
        resolutionOptions.firstOrNull { it.width == 1920 && it.height == 1080 }
            ?: resolutionOptions.first()
    var selectedResolution by remember { mutableStateOf(defaultResolution) }
    val previewRotation = 0f
    val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    val previewAspectRatio = if (isPortrait) {
        selectedResolution.height.toFloat() / selectedResolution.width.toFloat()
    } else {
        selectedResolution.width.toFloat() / selectedResolution.height.toFloat()
    }

    fun cancelResolutionRecovery() {
        resolutionRecoveryRef[0]?.let { mainHandler.removeCallbacks(it) }
        resolutionRecoveryRef[0] = null
    }

    fun isResolutionSwitchWindowActive(): Boolean {
        if (!isResolutionSwitching) {
            return false
        }
        val lastSwitch = lastResolutionSwitchAt
        if (lastSwitch == 0L) {
            return true
        }
        return SystemClock.elapsedRealtime() - lastSwitch < RESOLUTION_SWITCH_IGNORE_MS
    }

    val previewDataCallBack = remember {
        object : IPreviewDataCallBack {
            override fun onPreviewData(
                data: ByteArray?,
                format: IPreviewDataCallBack.DataFormat
            ) {
                if (!previewFrameLogged.get() && data != null) {
                    if (previewFrameLogged.compareAndSet(false, true)) {
                        Log.d(
                            TAG,
                            "preview frame received size=${data.size} format=$format"
                        )
                    }
                }
                if (data != null) {
                    mainHandler.post {
                        if (!isCameraOpened) {
                            isCameraOpened = true
                            isOpening = false
                        }
                        if (isResolutionSwitching) {
                            isResolutionSwitching = false
                            cancelResolutionRecovery()
                        }
                    }
                }
                if (data != null && format == IPreviewDataCallBack.DataFormat.NV21) {
                    recorder?.onPreviewFrame(data)
                }
            }
        }
    }

    fun refreshDevices() {
        deviceList.clear()
        deviceById.clear()
        if (isPreview || cameraStrategy == null) {
            selectedDeviceId = null
            return
        }
        val devices = cameraStrategy.getUsbDeviceList()
        if (devices.isNullOrEmpty()) {
            selectedDeviceId = null
        } else {
            val mapped = devices.map { device ->
                deviceById[device.deviceId.toString()] = device
                UvcDeviceInfo(
                    id = device.deviceId.toString(),
                    name = device.productName ?: device.deviceName,
                    vendorId = device.vendorId,
                    productId = device.productId
                )
            }
            deviceList.addAll(mapped)
            if (selectedDeviceId == null || mapped.none { it.id == selectedDeviceId }) {
                selectedDeviceId = mapped.first().id
            }
        }
    }

    fun requestUsbPermissionIfNeeded(): Boolean {
        val targetId = selectedDeviceId ?: return false
        val device = deviceById[targetId] ?: return false
        if (isResolutionSwitchWindowActive()) {
            return false
        }
        if (usbManager.hasPermission(device)) {
            hasRequestedUsbPermission = false
            usbPermissionDenied = false
            return true
        }
        if (usbPermissionDenied) {
            statusMessage = context.getString(R.string.status_usb_permission_denied)
            return false
        }
        if (hasRequestedUsbPermission) {
            return false
        }
        if (!hasIssuedSwitchRequest) {
            cameraClient?.switchCamera(targetId)
            hasIssuedSwitchRequest = true
        }
        hasRequestedUsbPermission = true
        usbPermissionDenied = false
        statusMessage = context.getString(R.string.status_usb_permission_requesting)
        Log.d(TAG, "requesting USB permission: id=$targetId")
        return false
    }

    fun attemptOpenIfReady() {
        if (isResolutionSwitchWindowActive() || isPreview || !isStrategyActive || !isPreviewViewReady || !isOpening) {
            return
        }
        if (isCameraOpened || cameraClient?.isCameraOpened() == true) {
            isCameraOpened = true
            isOpening = false
            return
        }
        if (!requestUsbPermissionIfNeeded()) {
            return
        }
        val targetId = selectedDeviceId ?: return
        val view = cameraView ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastOpenAttemptAt < 800L) {
            return
        }
        if (!openRequestIssued.compareAndSet(false, true)) {
            return
        }
        lastOpenAttemptAt = now
        cameraRequest.cameraId = targetId
        cameraClient?.openCamera(view, false)
    }

    fun cancelPendingReopen() {
        pendingReopenRef[0]?.let { mainHandler.removeCallbacks(it) }
        pendingReopenRef[0] = null
    }

    fun scheduleReopen(delayMs: Long) {
        cancelPendingReopen()
        val runnable = Runnable {
            if (isPreview || !isStrategyActive || !isPreviewViewReady) {
                return@Runnable
            }
            isOpening = true
            openRequestIssued.set(false)
            attemptOpenIfReady()
        }
        pendingReopenRef[0] = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    fun openCamera() {
        if (isCameraOpened) {
            return
        }
        if (isPreview) {
            statusMessage = context.getString(R.string.status_preview_camera_disabled)
            return
        }
        if (!isPreviewViewReady) {
            return
        }
        val targetId = selectedDeviceId
        if (targetId == null) {
            statusMessage = context.getString(R.string.status_uvc_device_not_selected)
        } else {
            isOpening = true
            openRequestIssued.set(false)
            hasIssuedSwitchRequest = false
            cameraRequest.cameraId = targetId
            attemptOpenIfReady()
            statusMessage = context.getString(R.string.status_camera_opening)
            Log.d(TAG, "openCamera requested: id=$targetId viewReady=$isPreviewViewReady")
        }
    }

    fun stopRecording() {
        if (isRecording) {
            recorder?.stop()
            isFinalizing = true
            statusMessage = context.getString(R.string.status_record_stopping)
        }
    }

    val startRecording = startRecording@{
        if (isPreview) {
            statusMessage = context.getString(R.string.status_preview_record_disabled)
            return@startRecording
        }
        if (!isCameraOpened) {
            statusMessage = context.getString(R.string.status_open_camera_first)
            return@startRecording
        }
        val file = recordingRepository.createRecordingFile()
        if (file == null) {
            statusMessage = context.getString(R.string.status_storage_unavailable)
            return@startRecording
        }
        isFinalizing = false
        var createdRecorder: HevcRecorder? = null
        val callback = object : ICaptureCallBack {
            override fun onBegin() {
                mainHandler.post {
                    recordingStartAt = System.currentTimeMillis()
                    recordingElapsedMs = 0L
                    isRecording = true
                    statusMessage = if (createdRecorder?.isAudioEnabled == true) {
                        context.getString(R.string.status_recording)
                    } else {
                        context.getString(R.string.status_recording_no_audio)
                    }
                }
            }

            override fun onError(error: String?) {
                mainHandler.post {
                    isRecording = false
                    recordingStartAt = null
                    isFinalizing = false
                    recorder = null
                    statusMessage = context.getString(
                        R.string.status_recording_failed,
                        error ?: context.getString(R.string.error_unknown)
                    )
                }
            }

            override fun onComplete(path: String?) {
                mainHandler.post {
                    isRecording = false
                    recordingStartAt = null
                    isFinalizing = false
                    recorder = null
                    statusMessage = context.getString(
                        R.string.status_saved,
                        path ?: context.getString(R.string.label_unknown_path)
                    )
                }
            }
        }
        createdRecorder = HevcRecorder(
            context = context,
            width = selectedResolution.width,
            height = selectedResolution.height,
            outputFile = file,
            callback = callback
        )
        if (!createdRecorder.start()) {
            statusMessage = context.getString(R.string.status_record_start_failed)
            return@startRecording
        }
        recorder = createdRecorder
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
            startRecording()
        } else if (!granted) {
            pendingRecord = false
            statusMessage = context.getString(R.string.status_audio_permission_required)
        }
    }
    }

    fun handleOpen() {
        if (isPreview) {
            statusMessage = context.getString(R.string.status_preview_camera_disabled)
            return
        }
        if (pendingOpen || isOpening || isCameraOpened) {
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

    fun handleAutoOpen() {
        if (isResolutionSwitchWindowActive() || isPreview || !isStrategyActive || !isPreviewViewReady) {
            return
        }
        val alreadyOpened = cameraClient?.isCameraOpened() == true
        if (alreadyOpened) {
            isCameraOpened = true
            return
        }
        if (pendingOpen || isOpening || selectedDeviceId == null || isCameraOpened) {
            return
        }
        handleOpen()
    }

    fun scheduleResolutionRecovery() {
        cancelResolutionRecovery()
        val runnable = Runnable {
            if (!isResolutionSwitching) {
                resolutionRecoveryRef[0] = null
                return@Runnable
            }
            isResolutionSwitching = false
            isOpening = true
            openRequestIssued.set(false)
            handleAutoOpen()
            attemptOpenIfReady()
            resolutionRecoveryRef[0] = null
        }
        resolutionRecoveryRef[0] = runnable
        mainHandler.postDelayed(runnable, RESOLUTION_SWITCH_IGNORE_MS)
    }

    fun isUsbDeviceStillPresent(device: UsbDevice?): Boolean {
        val deviceId = device?.deviceId ?: return false
        return usbManager.deviceList.values.any { it.deviceId == deviceId }
    }

    fun applyResolution(option: ResolutionOption) {
        val resolutionLabel = context.getString(
            R.string.format_resolution,
            option.width,
            option.height
        )
        Log.d(TAG, "applyResolution: ${option.width}x${option.height} opened=$isCameraOpened")
        val targetId = selectedDeviceId
        if (targetId == null) {
            statusMessage = context.getString(R.string.status_uvc_device_not_selected)
            return
        }
        cameraRequest.cameraId = targetId
        val supportedSizes = cameraClient?.getAllPreviewSizes(null).orEmpty()
        val isSupported = supportedSizes.isEmpty() || supportedSizes.any {
            it.width == option.width && it.height == option.height
        }
        if (!isSupported) {
            statusMessage = context.getString(
                R.string.status_resolution_not_supported,
                resolutionLabel
            )
            return
        }
        cameraRequest.previewWidth = option.width
        cameraRequest.previewHeight = option.height
        selectedResolution = option
        if (isPreviewViewReady) {
            cameraView?.holder?.setFixedSize(option.width, option.height)
            cameraView?.setAspectRatio(option.width, option.height)
        }
        val clientOpened = cameraClient?.isCameraOpened() == true
        if (clientOpened) {
            isResolutionSwitching = true
            lastResolutionSwitchAt = SystemClock.elapsedRealtime()
            val updated = run {
                val strategy = cameraStrategy ?: return@run false
                try {
                    val method = strategy.javaClass.getDeclaredMethod(
                        "updateResolutionInternal",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )
                    method.isAccessible = true
                    method.invoke(strategy, option.width, option.height)
                    true
                } catch (error: Exception) {
                    Log.e(TAG, "updateResolutionInternal failed", error)
                    false
                }
            }
            if (!updated) {
                statusMessage = context.getString(
                    R.string.status_resolution_not_supported,
                    resolutionLabel
                )
                isResolutionSwitching = false
                return
            }
            statusMessage = context.getString(R.string.status_camera_opening)
            hasIssuedSwitchRequest = false
            openRequestIssued.set(false)
            isCameraOpened = false
            isOpening = false
            previewFrameLogged.set(false)
            scheduleResolutionRecovery()
        } else {
            statusMessage = context.getString(
                R.string.status_resolution_set,
                resolutionLabel
            )
            if (!isOpening) {
                handleAutoOpen()
            }
        }
    }

    if (!isPreview && cameraClient != null && cameraStrategy != null) {
        DisposableEffect(cameraView) {
            val previewView = cameraView ?: return@DisposableEffect onDispose { }
            val holder = previewView.holder
            val callback = object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    isPreviewViewReady = true
                    holder.setFixedSize(
                        cameraRequest.previewWidth,
                        cameraRequest.previewHeight
                    )
                    Log.d(TAG, "surfaceCreated")
                    handleAutoOpen()
                    attemptOpenIfReady()
                }

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                    Log.d(TAG, "surfaceChanged w=$width h=$height")
                    cameraClient?.setRenderSize(width, height)
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    Log.d(TAG, "surfaceDestroyed")
                    isPreviewViewReady = false
                    isOpening = false
                    isCameraOpened = false
                    openRequestIssued.set(false)
                    hasIssuedSwitchRequest = false
                    cancelPendingReopen()
                    cancelResolutionRecovery()
                    isResolutionSwitching = false
                    stopRecording()
                    cameraClient?.closeCamera()
                }
            }
            holder.addCallback(callback)
            isPreviewViewReady = holder.surface.isValid
            if (isPreviewViewReady) {
                handleAutoOpen()
                attemptOpenIfReady()
            }
            onDispose {
                holder.removeCallback(callback)
                isPreviewViewReady = false
                cancelPendingReopen()
                cancelResolutionRecovery()
                isResolutionSwitching = false
            }
        }

        DisposableEffect(lifecycleOwner, cameraClient, cameraStrategy) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        if (!isStrategyActive) {
                            cameraStrategy.register()
                            isStrategyActive = true
                        }
                    }
                    Lifecycle.Event.ON_STOP -> {
                        isStrategyActive = false
                        isOpening = false
                        openRequestIssued.set(false)
                        hasIssuedSwitchRequest = false
                        cancelPendingReopen()
                        cancelResolutionRecovery()
                        isResolutionSwitching = false
                        stopRecording()
                        cameraClient.closeCamera()
                        isCameraOpened = false
                    }
                    Lifecycle.Event.ON_DESTROY -> {
                        isStrategyActive = false
                        isOpening = false
                        openRequestIssued.set(false)
                        hasIssuedSwitchRequest = false
                        cancelPendingReopen()
                        cancelResolutionRecovery()
                        isResolutionSwitching = false
                        cameraStrategy.unRegister()
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (!isStrategyActive &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                cameraStrategy.register()
                isStrategyActive = true
            }
            onDispose {
                isStrategyActive = false
                isOpening = false
                openRequestIssued.set(false)
                hasIssuedSwitchRequest = false
                cancelPendingReopen()
                cancelResolutionRecovery()
                isResolutionSwitching = false
                lifecycleOwner.lifecycle.removeObserver(observer)
                stopRecording()
                cameraClient.closeCamera()
                cameraStrategy.unRegister()
            }
        }

        DisposableEffect(cameraStrategy) {
            val callback = object : IDeviceConnectCallBack {
            override fun onAttachDev(device: UsbDevice?) {
                if (device == null) return
                mainHandler.post {
                    Log.d(TAG, "onAttachDev: ${device.deviceName}")
                    statusMessage = context.getString(
                        R.string.status_device_attached,
                        device.deviceName
                    )
                    hasRequestedUsbPermission = false
                    usbPermissionDenied = false
                    hasIssuedSwitchRequest = false
                    openRequestIssued.set(false)
                    refreshDevices()
                    handleAutoOpen()
                    attemptOpenIfReady()
                }
            }

            override fun onDetachDec(device: UsbDevice?) {
                if (device == null) return
                mainHandler.post {
                    statusMessage = context.getString(
                        R.string.status_device_detached,
                        device.deviceName
                    )
                    if (isResolutionSwitchWindowActive()) {
                        return@post
                    }
                    if (isUsbDeviceStillPresent(device)) {
                        Log.d(TAG, "onDetachDev ignored; device still present")
                        openRequestIssued.set(false)
                        hasIssuedSwitchRequest = false
                        scheduleReopen(300L)
                        return@post
                    }
                    isResolutionSwitching = false
                    cancelResolutionRecovery()
                    if (selectedDeviceId == device.deviceId.toString()) {
                        selectedDeviceId = null
                    }
                    usbPermissionDenied = false
                    stopRecording()
                    cameraClient.closeCamera()
                    isOpening = false
                    isCameraOpened = false
                    openRequestIssued.set(false)
                    hasIssuedSwitchRequest = false
                    hasRequestedUsbPermission = false
                    cancelPendingReopen()
                    refreshDevices()
                }
                }

            override fun onConnectDev(
                device: UsbDevice?,
                ctrlBlock: USBMonitor.UsbControlBlock?
            ) {
                if (device == null) return
                mainHandler.post {
                    Log.d(TAG, "onConnectDev: ${device.deviceName}")
                    statusMessage = context.getString(
                        R.string.status_device_in_use,
                        device.deviceName
                    )
                    usbPermissionDenied = false
                    if (!isOpening) {
                        isOpening = true
                        openRequestIssued.set(false)
                    }
                    attemptOpenIfReady()
                    if (isPreviewViewReady && cameraClient.isCameraOpened() == true) {
                        isCameraOpened = true
                        isOpening = false
                    }
                }
            }

            override fun onDisConnectDec(
                device: UsbDevice?,
                ctrlBlock: USBMonitor.UsbControlBlock?
            ) {
                if (device == null) return
                mainHandler.post {
                    Log.d(TAG, "onDisConnectDev: ${device.deviceName}")
                    if (isResolutionSwitchWindowActive()) {
                        return@post
                    }
                    if (isUsbDeviceStillPresent(device)) {
                        Log.d(TAG, "onDisConnectDev ignored; device still present")
                        openRequestIssued.set(false)
                        hasIssuedSwitchRequest = false
                        scheduleReopen(300L)
                        return@post
                    }
                    isResolutionSwitching = false
                    cancelResolutionRecovery()
                    statusMessage = context.getString(
                        R.string.status_device_disconnected,
                        device.deviceName
                    )
                    usbPermissionDenied = false
                    stopRecording()
                    isOpening = false
                    isCameraOpened = false
                    openRequestIssued.set(false)
                    hasIssuedSwitchRequest = false
                    hasRequestedUsbPermission = false
                    cancelPendingReopen()
                }
            }

            override fun onCancelDev(device: UsbDevice?) {
                mainHandler.post {
                    Log.d(TAG, "onCancelDev: ${device?.deviceName}")
                    if (isResolutionSwitchWindowActive()) {
                        return@post
                    }
                    isResolutionSwitching = false
                    cancelResolutionRecovery()
                    statusMessage = context.getString(R.string.status_usb_permission_denied)
                    usbPermissionDenied = true
                    isOpening = false
                    isCameraOpened = false
                    openRequestIssued.set(false)
                    hasIssuedSwitchRequest = false
                    hasRequestedUsbPermission = false
                    cancelPendingReopen()
                }
            }
            }
            cameraStrategy.setDeviceConnectStatusListener(callback)
            onDispose { }
        }

        LaunchedEffect(Unit) {
            cameraClient.addPreviewDataCallBack(previewDataCallBack)
        }

        LaunchedEffect(isStrategyActive) {
            if (!isStrategyActive) {
                return@LaunchedEffect
            }
            repeat(10) {
                refreshDevices()
                handleAutoOpen()
                if (selectedDeviceId != null) {
                    return@LaunchedEffect
                }
                delay(400L)
            }
        }

        LaunchedEffect(isOpening, cameraClient, isResolutionSwitching) {
            if (!isOpening || isResolutionSwitchWindowActive()) {
                return@LaunchedEffect
            }
            repeat(12) {
                if (cameraClient.isCameraOpened() == true) {
                    isCameraOpened = true
                    isOpening = false
                    isResolutionSwitching = false
                    cancelResolutionRecovery()
                    return@LaunchedEffect
                }
                attemptOpenIfReady()
                delay(300L)
            }
            isOpening = false
            openRequestIssued.set(false)
            isResolutionSwitching = false
            cancelResolutionRecovery()
        }
    }

    LaunchedEffect(isRecording, recordingStartAt) {
        if (isRecording && recordingStartAt != null) {
            while (isRecording) {
                recordingElapsedMs = System.currentTimeMillis() - (recordingStartAt ?: 0L)
                delay(1000L)
            }
        } else {
            recordingElapsedMs = 0L
        }
    }

    fun handleToggleRecord() {
        if (isPreview) {
            statusMessage = context.getString(R.string.status_preview_record_disabled)
            return
        }
        if (isRecording) {
            stopRecording()
            return
        }
        val hasRecordPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasRecordPermission) {
            startRecording()
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

    LaunchedEffect(selectedDeviceId, selectedResolution) {
        val targetId = selectedDeviceId
        if (targetId != null) {
            cameraRequest.cameraId = targetId
        }
        if (!isResolutionSwitchWindowActive()) {
            handleAutoOpen()
        }
    }

    UvcPreviewScreenContent(
        modifier = modifier,
        previewAspectRatio = previewAspectRatio,
        statusMessage = statusMessage,
        isCameraOpened = isCameraOpened,
        isRecording = isRecording,
        isFinalizing = isFinalizing,
        recordingElapsedMs = recordingElapsedMs,
        selectedDeviceId = selectedDeviceId,
        resolutionOptions = resolutionOptions,
        selectedResolution = selectedResolution,
        deviceList = deviceList,
        onToggleRecord = { handleToggleRecord() },
        onApplyResolution = { option -> applyResolution(option) },
        onRefreshDevices = { refreshDevices() },
        onOpenRecordings = onOpenRecordings,
        onSelectDevice = { id ->
            val wasSelected = selectedDeviceId == id
            selectedDeviceId = id
            if (!wasSelected) {
                hasRequestedUsbPermission = false
                hasIssuedSwitchRequest = false
                cameraClient?.switchCamera(id)
                hasIssuedSwitchRequest = true
            } else {
                handleAutoOpen()
            }
        },
        previewContent = previewContent
    )
}

@Composable
internal fun UvcPreviewScreenContent(
    modifier: Modifier = Modifier,
    previewAspectRatio: Float,
    statusMessage: String,
    isCameraOpened: Boolean,
    isRecording: Boolean,
    isFinalizing: Boolean,
    recordingElapsedMs: Long,
    selectedDeviceId: String?,
    resolutionOptions: List<ResolutionOption>,
    selectedResolution: ResolutionOption,
    deviceList: List<UvcDeviceInfo>,
    onToggleRecord: () -> Unit,
    onApplyResolution: (ResolutionOption) -> Unit,
    onRefreshDevices: () -> Unit,
    onOpenRecordings: () -> Unit,
    onSelectDevice: (String) -> Unit,
    previewContent: @Composable (Modifier) -> Unit
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
        isRecording -> stringResource(
            R.string.pill_recording_with_time,
            formatDuration(recordingElapsedMs)
        )
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
    val recordContainer = if (isRecording || isFinalizing) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val recordContent = if (isRecording || isFinalizing) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val recordButtonColors = if (isRecording) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    } else {
        ButtonDefaults.buttonColors()
    }

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
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(2.dp),
                ) {
                    Button(
                        modifier = Modifier
                            .weight(1f),
                        onClick = onToggleRecord,
                        enabled = isCameraOpened && !isFinalizing,
                        colors = recordButtonColors
                    ) {
                        val recordText = if (isRecording) {
                            stringResource(R.string.action_stop)
                        } else {
                            stringResource(R.string.action_record)
                        }
                        Text(recordText)
                    }
                    FilledTonalButton(
                        modifier = Modifier
                            .weight(1f),
                        onClick = onOpenRecordings
                    ) {
                        Text(stringResource(R.string.label_recordings))
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_resolution),
                        style = MaterialTheme.typography.titleMedium
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(resolutionOptions, key = { it.width to it.height }) { option ->
                            val optionLabel = stringResource(
                                R.string.format_resolution,
                                option.width,
                                option.height
                            )
                            FilterChip(
                                selected = option == selectedResolution,
                                onClick = { onApplyResolution(option) },
                                label = { Text(optionLabel) },
                                enabled = !isRecording && !isFinalizing,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.label_devices),
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedButton(onClick = onRefreshDevices) {
                            Text(stringResource(R.string.action_refresh))
                        }
                    }
                    if (deviceList.isEmpty()) {
                        Text(
                            text = stringResource(R.string.label_no_devices),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(deviceList, key = { it.id }) { device ->
                                val isSelected = device.id == selectedDeviceId
                                val containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                                val contentColor = if (isSelected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                                val detailColor = if (isSelected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectDevice(device.id) },
                                    color = containerColor,
                                    contentColor = contentColor,
                                    tonalElevation = if (isSelected) 2.dp else 0.dp,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { onSelectDevice(device.id) }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = device.name)
                                            Text(
                                                text = stringResource(
                                                    R.string.label_device_details,
                                                    device.id,
                                                    device.vendorId,
                                                    device.productId
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = detailColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
private fun RecordingListScreen(
    modifier: Modifier = Modifier,
    repository: RecordingRepository,
    onBack: () -> Unit,
    onPlay: (RecordingItem) -> Unit
) {
    val context = LocalContext.current
    var recordings by remember { mutableStateOf(emptyList<RecordingItem>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<RecordingItem?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        recordings = repository.loadRecordings()
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
                            recordings = recordings.filterNot { it.path == target.path }
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
                                recordings = repository.loadRecordings()
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
            if (recordings.isEmpty()) {
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
                    items(recordings, key = { it.path }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlay(item) },
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
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onPlay(item) }) {
                                        Text(stringResource(R.string.action_play))
                                    }
                                    OutlinedButton(onClick = { deleteTarget = item }) {
                                        Text(stringResource(R.string.action_delete))
                                    }
                                }
                            }
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
    onBack: () -> Unit
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

    LaunchedEffect(item.path) {
        isRepairing = true
        errorMessage = null
        attemptedRepair = false
        usePlatformPlayer = false
        val repaired = repository.prepareForPlayback(item)
        playableItem = repaired
        playbackToken += 1
        isRepairing = false
    }

    val exoPlayer = remember(playableItem.path, playbackToken, isRepairing, usePlatformPlayer) {
        if (isRepairing || usePlatformPlayer) {
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
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
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
                    PlayerView(it).apply {
                        player = exoPlayer
                        useController = true
                    }
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
