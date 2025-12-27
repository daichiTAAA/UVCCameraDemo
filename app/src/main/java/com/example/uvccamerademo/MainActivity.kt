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
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.uvccamerademo.ui.theme.UVCCameraDemoTheme
import com.jiangdg.ausbc.CameraClient
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.ausbc.camera.CameraUvcStrategy
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.serenegiant.usb.USBMonitor
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UVCCameraDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    UvcScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun UvcScreen(modifier: Modifier = Modifier) {
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
    var pendingOpen by remember { mutableStateOf(false) }
    var pendingCapture by remember { mutableStateOf(false) }

    val refreshDevices = {
        deviceList.clear()
        val devices = cameraStrategy.getUsbDeviceList()
        if (devices.isNullOrEmpty()) {
            selectedDeviceId = null
        } else {
            deviceList.addAll(devices)
            if (selectedDeviceId == null) {
                selectedDeviceId = devices.first().deviceId.toString()
            }
        }
    }

    val openCamera = {
        cameraClient.openCamera(cameraView, false)
        selectedDeviceId?.let { cameraClient.switchCamera(it) }
        isCameraOpened = cameraClient.isCameraOpened() == true
        statusMessage = "Open requested"
    }

    val captureImage = captureImage@{
        val outputDir = context.getExternalFilesDir(null)
        if (outputDir == null) {
            statusMessage = "No output directory"
            return@captureImage
        }
        val filePath = File(outputDir, "uvc_${System.currentTimeMillis()}.jpg").absolutePath
        cameraClient.captureImage(
            object : ICaptureCallBack {
                override fun onBegin() {
                    mainHandler.post { statusMessage = "Capturing..." }
                }

                override fun onError(error: String?) {
                    val message = error ?: "Unknown error"
                    mainHandler.post { statusMessage = "Capture failed: $message" }
                }

                override fun onComplete(path: String?) {
                    val message = path ?: "No output path"
                    mainHandler.post { statusMessage = "Saved: $message" }
                }
            },
            filePath
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

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingCapture) {
            pendingCapture = false
            captureImage()
        } else if (!granted) {
            pendingCapture = false
            statusMessage = "Storage permission required"
        }
    }

    DisposableEffect(lifecycleOwner, cameraClient, cameraStrategy) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> cameraStrategy.register()
                Lifecycle.Event.ON_STOP -> {
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
                    refreshDevices()
                    if (selectedDeviceId == device.deviceId.toString()) {
                        selectedDeviceId = null
                    }
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
                    isCameraOpened = false
                }
            }

            override fun onCancelDev(device: UsbDevice?) {
                if (device == null) return
                mainHandler.post { statusMessage = "Permission canceled" }
            }
        }
        cameraStrategy.setDeviceConnectStatusListener(callback)
        onDispose { }
    }

    LaunchedEffect(cameraStrategy) {
        refreshDevices()
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
                .aspectRatio(4f / 3f),
            factory = { cameraView }
        )
        Text(text = "Status: $statusMessage")
        if (selectedDeviceId != null) {
            Text(text = "Selected: $selectedDeviceId")
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
                    if (!isCameraOpened) {
                        statusMessage = "Camera not open"
                        return@OutlinedButton
                    }
                    val hasStoragePermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasStoragePermission) {
                        captureImage()
                    } else {
                        pendingCapture = true
                        storagePermissionLauncher.launch(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    }
                }
            ) {
                Text("Capture")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refreshDevices() }) {
                Text("Refresh Devices")
            }
            if (deviceList.isEmpty()) {
                Text(text = "No UVC devices", modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        Divider()
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
