package com.example.uvccamerademo

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.uvccamerademo.ui.theme.UVCCameraDemoTheme
import com.jiangdg.ausbc.CameraClient
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.ausbc.camera.CameraUvcStrategy
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.serenegiant.usb.USBMonitor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

private sealed interface Screen {
    object Preview : Screen
    object Recordings : Screen
    data class Playback(val item: RecordingItem) : Screen
}

private data class ResolutionOption(val width: Int, val height: Int) {
    val label: String = "${width}x${height}"
}

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
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val cameraView = remember { AspectRatioTextureView(context) }
    val cameraRequest = remember {
        CameraRequest.Builder()
            .setPreviewWidth(640)
            .setPreviewHeight(480)
            .create()
    }
    val cameraStrategy = remember { CameraUvcStrategy(context) }
    val cameraClient = remember {
        CameraClient.newBuilder(context)
            .setCameraStrategy(cameraStrategy)
            .setCameraRequest(cameraRequest)
            .setEnableGLES(false)
            .build()
    }

    val deviceList = remember { mutableStateListOf<UsbDevice>() }
    var selectedDeviceId by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("Idle") }
    var isCameraOpened by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingStartAt by remember { mutableStateOf<Long?>(null) }
    var recordingElapsedMs by remember { mutableStateOf(0L) }
    var pendingOpen by remember { mutableStateOf(false) }
    var pendingRecord by remember { mutableStateOf(false) }

    val resolutionOptions = remember {
        listOf(
            ResolutionOption(640, 480),
            ResolutionOption(1280, 720),
            ResolutionOption(1920, 1080)
        )
    }
    var selectedResolution by remember { mutableStateOf(resolutionOptions.first()) }

    val refreshDevices = {
        deviceList.clear()
        val devices = cameraStrategy.getUsbDeviceList()
        if (devices.isNullOrEmpty()) {
            selectedDeviceId = null
        } else {
            deviceList.addAll(devices)
            if (selectedDeviceId == null || devices.none { it.deviceId.toString() == selectedDeviceId }) {
                selectedDeviceId = devices.first().deviceId.toString()
            }
        }
    }

    val applyResolution = { option: ResolutionOption ->
        val updated = if (isCameraOpened) {
            cameraClient.updateResolution(option.width, option.height)
        } else {
            true
        }
        if (!updated) {
            statusMessage = "Resolution not supported: ${option.label}"
        } else {
            cameraRequest.previewWidth = option.width
            cameraRequest.previewHeight = option.height
            selectedResolution = option
            statusMessage = "Resolution set: ${option.label}"
        }
    }

    val openCamera = {
        if (selectedDeviceId == null) {
            statusMessage = "No UVC device selected"
        } else {
            cameraClient.openCamera(cameraView, false)
            cameraClient.switchCamera(selectedDeviceId!!)
            statusMessage = "Open requested"
        }
    }

    val stopRecording = {
        if (isRecording) {
            cameraClient.captureVideoStop()
            isRecording = false
            recordingStartAt = null
            statusMessage = "Stopping recording..."
        }
    }

    val startRecording = startRecording@{
        if (!isCameraOpened) {
            statusMessage = "Open the camera first"
            return@startRecording
        }
        val file = recordingRepository.createRecordingFile()
        if (file == null) {
            statusMessage = "Storage not available"
            return@startRecording
        }
        recordingStartAt = System.currentTimeMillis()
        recordingElapsedMs = 0L
        isRecording = true
        statusMessage = "Recording..."
        cameraClient.captureVideoStart(
            object : ICaptureCallBack {
                override fun onBegin() {
                    mainHandler.post {
                        recordingStartAt = System.currentTimeMillis()
                        recordingElapsedMs = 0L
                        isRecording = true
                        statusMessage = "Recording..."
                    }
                }

                override fun onError(error: String?) {
                    mainHandler.post {
                        isRecording = false
                        recordingStartAt = null
                        statusMessage = "Recording failed: ${error ?: "Unknown error"}"
                    }
                }

                override fun onComplete(path: String?) {
                    mainHandler.post {
                        isRecording = false
                        recordingStartAt = null
                        statusMessage = "Saved: ${path ?: "Unknown path"}"
                    }
                }
            },
            file.absolutePath,
            0L
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingOpen) {
            pendingOpen = false
            openCamera()
        } else if (!granted) {
            pendingOpen = false
            statusMessage = "Camera permission required"
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingRecord) {
            pendingRecord = false
            startRecording()
        } else if (!granted) {
            pendingRecord = false
            statusMessage = "Audio permission required"
        }
    }

    DisposableEffect(lifecycleOwner, cameraClient, cameraStrategy) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> cameraStrategy.register()
                Lifecycle.Event.ON_STOP -> {
                    stopRecording()
                    cameraClient.closeCamera()
                    isCameraOpened = false
                }
                Lifecycle.Event.ON_DESTROY -> cameraStrategy.unRegister()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
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
                    statusMessage = "Device attached: ${device.deviceName}"
                    refreshDevices()
                }
            }

            override fun onDetachDec(device: UsbDevice?) {
                if (device == null) return
                mainHandler.post {
                    statusMessage = "Device detached: ${device.deviceName}"
                    if (selectedDeviceId == device.deviceId.toString()) {
                        selectedDeviceId = null
                    }
                    stopRecording()
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
                    statusMessage = "In use: ${device.deviceName}"
                    isCameraOpened = cameraClient.isCameraOpened() == true
                }
            }

            override fun onDisConnectDec(
                device: UsbDevice?,
                ctrlBlock: USBMonitor.UsbControlBlock?
            ) {
                if (device == null) return
                mainHandler.post {
                    statusMessage = "Disconnected: ${device.deviceName}"
                    stopRecording()
                    isCameraOpened = false
                }
            }

            override fun onCancelDev(device: UsbDevice?) {
                if (device == null) return
                mainHandler.post {
                    statusMessage = "USB permission canceled"
                    isCameraOpened = false
                }
            }
        }
        cameraStrategy.setDeviceConnectStatusListener(callback)
        onDispose { }
    }

    LaunchedEffect(Unit) {
        refreshDevices()
    }

    LaunchedEffect(selectedDeviceId, isCameraOpened) {
        if (isCameraOpened && selectedDeviceId != null) {
            cameraClient.switchCamera(selectedDeviceId!!)
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "UVC Camera Preview", style = MaterialTheme.typography.titleLarge)
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(selectedResolution.width.toFloat() / selectedResolution.height.toFloat()),
            factory = { cameraView }
        )
        Text(text = "Status: $statusMessage")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isRecording) {
                Text(
                    text = "REC ${formatDuration(recordingElapsedMs)}",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            if (selectedDeviceId != null) {
                Text(text = "Selected: $selectedDeviceId")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val hasCameraPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasCameraPermission) {
                        openCamera()
                    } else {
                        pendingOpen = true
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                enabled = !isCameraOpened
            ) {
                Text("Open")
            }
            OutlinedButton(
                onClick = {
                    stopRecording()
                    cameraClient.closeCamera()
                    isCameraOpened = false
                    statusMessage = "Closed"
                },
                enabled = isCameraOpened
            ) {
                Text("Close")
            }
            OutlinedButton(
                onClick = {
                    if (isRecording) {
                        stopRecording()
                        return@OutlinedButton
                    }
                    val hasRecordPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasRecordPermission) {
                        startRecording()
                    } else {
                        pendingRecord = true
                        recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = isCameraOpened
            ) {
                Text(if (isRecording) "Stop" else "Record")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            resolutionOptions.forEach { option ->
                val selected = option == selectedResolution
                val action = { applyResolution(option) }
                if (selected) {
                    Button(onClick = action, enabled = !isRecording) {
                        Text(option.label)
                    }
                } else {
                    OutlinedButton(onClick = action, enabled = !isRecording) {
                        Text(option.label)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refreshDevices() }) {
                Text("Refresh Devices")
            }
            OutlinedButton(onClick = onOpenRecordings, enabled = !isRecording) {
                Text("Recordings")
            }
            if (deviceList.isEmpty()) {
                Text(text = "No UVC devices", modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        HorizontalDivider()
        Text(text = "Devices", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
        ) {
            items(deviceList, key = { it.deviceId }) { device ->
                val deviceId = device.deviceId.toString()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedDeviceId = deviceId }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = deviceId == selectedDeviceId,
                        onClick = { selectedDeviceId = deviceId }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = device.productName ?: device.deviceName)
                        Text(
                            text = "id=$deviceId vid=${device.vendorId} pid=${device.productId}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "USB permission appears after Open; preview starts when granted.",
            style = MaterialTheme.typography.bodySmall
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
            title = { Text("Delete recording?") },
            text = { Text(target.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleted = repository.deleteRecording(target)
                        message = if (deleted) "Deleted: ${target.name}" else "Delete failed"
                        if (deleted) {
                            recordings = recordings.filterNot { it.path == target.path }
                        }
                        deleteTarget = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Recordings", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }
            OutlinedButton(
                onClick = {
                    message = null
                    scope.launch {
                        recordings = repository.loadRecordings()
                    }
                }
            ) {
                Text("Refresh")
            }
        }
        if (message != null) {
            Text(text = message!!, style = MaterialTheme.typography.bodySmall)
        }
        if (recordings.isEmpty()) {
            Text(text = "No recordings yet")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recordings, key = { it.path }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = item.name, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${formatDateTime(item.createdAt)} · ${formatFileSize(item.sizeBytes)} · ${formatDuration(item.durationMs)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onPlay(item) }) {
                                    Text("Play")
                                }
                                OutlinedButton(onClick = { deleteTarget = item }) {
                                    Text("Delete")
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
    var deleteConfirm by remember { mutableStateOf(false) }
    val exoPlayer = remember(item.path) {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(item.path.toUri()))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Delete recording?") },
            text = { Text(item.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        exoPlayer.stop()
                        repository.deleteRecording(item)
                        deleteConfirm = false
                        onBack()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Playback", style = MaterialTheme.typography.titleLarge)
        Text(text = item.name, style = MaterialTheme.typography.bodySmall)
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = true
                }
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }
            OutlinedButton(onClick = { deleteConfirm = true }) {
                Text("Delete")
            }
        }
    }
}

private fun formatDateTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
    return formatter.format(Date(timestamp))
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) {
        return "${bytes} B"
    }
    val kb = bytes / 1024.0
    if (kb < 1024.0) {
        return String.format(Locale.US, "%.1f KB", kb)
    }
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) {
        return "--:--"
    }
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
