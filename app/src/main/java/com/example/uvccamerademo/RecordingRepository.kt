package com.example.uvccamerademo

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecordingItem(
    val path: String,
    val name: String,
    val createdAt: Long,
    val sizeBytes: Long,
    val durationMs: Long
)

class RecordingRepository(context: Context) {
    private val appContext = context.applicationContext

    fun createRecordingFile(): File? {
        val dir = getRecordingDir() ?: return null
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val name = "uvc_${formatter.format(Date())}.mp4"
        return File(dir, name)
    }

    suspend fun loadRecordings(): List<RecordingItem> = withContext(Dispatchers.IO) {
        val dir = getRecordingDir() ?: return@withContext emptyList()
        val files = dir.listFiles { file ->
            file.isFile && file.extension.equals("mp4", ignoreCase = true)
        } ?: return@withContext emptyList()

        files
            .map { file ->
                RecordingItem(
                    path = file.absolutePath,
                    name = file.name,
                    createdAt = file.lastModified(),
                    sizeBytes = file.length(),
                    durationMs = readDurationMs(file)
                )
            }
            .sortedByDescending { it.createdAt }
    }

    fun deleteRecording(item: RecordingItem): Boolean {
        return File(item.path).delete()
    }

    private fun getRecordingDir(): File? {
        val baseDir = appContext.getExternalFilesDir(null) ?: return null
        val dir = File(baseDir, "Movies/UVC")
        if (!dir.exists() && !dir.mkdirs()) {
            return null
        }
        return dir
    }

    private fun readDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )
            duration?.toLongOrNull() ?: 0L
        } catch (e: RuntimeException) {
            0L
        } finally {
            retriever.release()
        }
    }
}
