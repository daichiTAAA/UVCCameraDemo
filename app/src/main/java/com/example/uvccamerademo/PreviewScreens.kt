package com.example.uvccamerademo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
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
    val selectedResolution = ResolutionOption(
        width = BuildConfig.DEFAULT_PREVIEW_WIDTH,
        height = BuildConfig.DEFAULT_PREVIEW_HEIGHT
    )
    UVCCameraDemoTheme {
        val scrollState = rememberScrollState()
        UvcPreviewScreenContent(
            previewAspectRatio = selectedResolution.width.toFloat() / selectedResolution.height,
            statusMessage = stringResource(R.string.status_preview_mode),
            isCameraOpened = true,
            isRecordingSession = true,
            isSegmentRecording = true,
            isFinalizing = false,
            recordingElapsedMs = 65_000L,
            selectedResolution = selectedResolution,
            workState = WorkUiState.ACTIVE,
            currentWorkId = "work-preview",
            activeWorkInfo = WorkInfo(
                model = "MODEL-01",
                serial = "SN-0001",
                process = "組立"
            ),
            qrModel = "MODEL-01",
            qrSerial = "SN-0001",
            qrMessage = null,
            onScanQr = {},
            onClearQr = {},
            processSource = ProcessSource.LIVE,
            selectedProcess = ProcessItem(id = "P01", name = "組立"),
            processMessage = null,
            processApiUrl = "http://10.0.2.2:8080",
            onProcessApiUrlChange = {},
            onFetchProcesses = {},
            onSelectProcess = {},
            segmentIntervalMinutes = 5,
            onSegmentIntervalChange = {},
            onSegmentIntervalChangeFinished = {},
            selectedVideoCodec = VideoCodec.HEVC,
            onVideoCodecChange = {},
            onWorkStart = {},
            onWorkPause = {},
            onWorkResume = {},
            onWorkEnd = {},
            canStartWork = true,
            workMessage = null,
            onOpen = {},
            onClose = {},
            onToggleRecord = {},
            onOpenRecordings = {},
            previewContent = { contentModifier ->
                Box(
                    modifier = contentModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(R.string.label_preview))
                }
            },
            scrollState = scrollState,
            isScrollEnabled = true,
            showQrScanner = false
        )
    }
}
