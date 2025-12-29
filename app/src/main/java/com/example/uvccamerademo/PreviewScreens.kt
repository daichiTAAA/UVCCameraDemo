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
    val selectedResolution = ResolutionOption(
        width = BuildConfig.DEFAULT_PREVIEW_WIDTH,
        height = BuildConfig.DEFAULT_PREVIEW_HEIGHT
    )
    UVCCameraDemoTheme {
        UvcPreviewScreenContent(
            previewAspectRatio = selectedResolution.width.toFloat() / selectedResolution.height,
            statusMessage = stringResource(R.string.status_preview_mode),
            isCameraOpened = true,
            isRecording = true,
            isFinalizing = false,
            recordingElapsedMs = 65_000L,
            selectedResolution = selectedResolution,
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
            }
        )
    }
}
