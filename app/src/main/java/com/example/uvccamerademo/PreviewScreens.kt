package com.example.uvccamerademo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.uvccamerademo.ui.theme.UVCCameraDemoTheme

@Preview(showBackground = true)
@Composable
fun UvcPreviewScreenPreview() {
    val resolutionOptions = listOf(
        ResolutionOption(640, 480),
        ResolutionOption(1280, 720),
        ResolutionOption(1920, 1080)
    )
    val selectedResolution =
        resolutionOptions.firstOrNull { it.width == 1920 && it.height == 1080 }
            ?: resolutionOptions.first()
    val devices = listOf(
        UvcDeviceInfo(
            id = "1001",
            name = stringResource(R.string.preview_device_uvc),
            vendorId = 4660,
            productId = 22136
        ),
        UvcDeviceInfo(
            id = "1002",
            name = stringResource(R.string.preview_device_usb_capture),
            vendorId = 11325,
            productId = 8
        )
    )
    UVCCameraDemoTheme {
        UvcPreviewScreenContent(
            previewAspectRatio = selectedResolution.width.toFloat() / selectedResolution.height,
            statusMessage = stringResource(R.string.status_preview_mode),
            isCameraOpened = true,
            isRecording = true,
            isFinalizing = false,
            recordingElapsedMs = 65_000L,
            selectedDeviceId = devices.first().id,
            resolutionOptions = resolutionOptions,
            selectedResolution = selectedResolution,
            deviceList = devices,
            onToggleRecord = {},
            onApplyResolution = {},
            onRefreshDevices = {},
            onOpenRecordings = {},
            onSelectDevice = {},
            previewContent = { contentModifier ->
                Box(
                    modifier = contentModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(R.string.label_preview))
                }
            }
        )
    }
}
