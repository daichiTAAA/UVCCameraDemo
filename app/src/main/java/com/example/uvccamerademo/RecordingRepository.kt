package com.example.uvccamerademo

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaMetadataRetriever
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.ByteBuffer
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private val database = AppDatabase.getInstance(appContext)
    private val workDao = database.workDao()
    private val segmentDao = database.segmentDao()

    suspend fun createWork(
        model: String,
        serial: String,
        process: String,
        startedAt: Long
    ): WorkEntity = withContext(Dispatchers.IO) {
        val work = WorkEntity(
            workId = UUID.randomUUID().toString(),
            model = model,
            serial = serial,
            process = process,
            state = WorkState.ACTIVE,
            startedAt = startedAt,
            endedAt = null
        )
        workDao.insert(work)
        work
    }

    suspend fun updateWorkState(workId: String, state: WorkState, endedAt: Long?) {
        withContext(Dispatchers.IO) {
            workDao.updateState(workId, state, endedAt)
        }
    }

    suspend fun insertSegment(
        segmentUuid: String,
        path: String,
        recordedAt: Long,
        workId: String?
    ): SegmentEntity = withContext(Dispatchers.IO) {
        val segmentIndex = if (workId != null) {
            (segmentDao.maxSegmentIndex(workId) ?: 0) + 1
        } else {
            null
        }
        val segment = SegmentEntity(
            segmentUuid = segmentUuid,
            path = path,
            recordedAt = recordedAt,
            durationMs = null,
            sizeBytes = null,
            workId = workId,
            segmentIndex = segmentIndex,
            uploadState = UploadState.NONE,
            uploadRemoteId = null,
            uploadBytesSent = 0L,
            uploadCompletedAt = null
        )
        segmentDao.insert(segment)
        segment
    }

    suspend fun deleteSegment(segmentUuid: String) {
        withContext(Dispatchers.IO) {
            segmentDao.deleteById(segmentUuid)
        }
    }

    suspend fun finalizeSegment(segmentUuid: String, path: String) {
        withContext(Dispatchers.IO) {
            val file = File(path)
            val durationMs = readDurationMs(file)
            val sizeBytes = file.length()
            segmentDao.updateFinalized(segmentUuid, durationMs, sizeBytes)
        }
    }

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
                buildItem(normalizeExtension(file))
            }
            .sortedByDescending { it.createdAt }
    }

    suspend fun prepareForPlayback(item: RecordingItem): RecordingItem = withContext(Dispatchers.IO) {
        val normalized = normalizeExtension(File(item.path))
        if (shouldWaitForFinalize(normalized)) {
            waitForStableFile(normalized)
        }
        buildItem(preparePlaybackFile(normalized))
    }

    suspend fun forceVideoOnlyRepair(item: RecordingItem): RecordingItem? = withContext(Dispatchers.IO) {
        val normalized = normalizeExtension(File(item.path))
        val playbackCopy = playbackCopyFor(normalized) ?: return@withContext null
        if (playbackCopy.exists() && playbackCopy.lastModified() >= normalized.lastModified()) {
            return@withContext buildItem(playbackCopy)
        }
        if (remuxVideoOnly(normalized, playbackCopy)) {
            return@withContext buildItem(playbackCopy)
        }
        playbackCopy.delete()
        null
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

    private fun buildItem(file: File): RecordingItem {
        return RecordingItem(
            path = file.absolutePath,
            name = file.name,
            createdAt = file.lastModified(),
            sizeBytes = file.length(),
            durationMs = readDurationMs(file)
        )
    }

    private fun preparePlaybackFile(file: File): File {
        val playbackCopy = playbackCopyFor(file) ?: return file
        if (playbackCopy.exists() && playbackCopy.lastModified() >= file.lastModified()) {
            return playbackCopy
        }
        if (needsVideoOnlyRemux(file) && remuxVideoOnly(file, playbackCopy)) {
            return playbackCopy
        }
        playbackCopy.delete()
        return file
    }

    private fun normalizeExtension(file: File): File {
        if (!file.name.endsWith(".mp4.mp4")) {
            return file
        }
        val normalizedName = file.name.removeSuffix(".mp4.mp4") + ".mp4"
        val normalizedFile = File(file.parentFile, normalizedName)
        if (normalizedFile.exists()) {
            return file
        }
        return if (file.renameTo(normalizedFile)) normalizedFile else file
    }

    private fun playbackCopyFor(file: File): File? {
        val dir = File(appContext.cacheDir, "playback")
        if (!dir.exists() && !dir.mkdirs()) {
            return null
        }
        return File(dir, file.nameWithoutExtension + "_playback.mp4")
    }

    private fun shouldWaitForFinalize(file: File): Boolean {
        val ageMs = System.currentTimeMillis() - file.lastModified()
        return ageMs in 0..1500
    }

    private suspend fun waitForStableFile(file: File) {
        var lastSize = file.length()
        var waitedMs = 0L
        while (waitedMs < 2000L) {
            delay(200L)
            val newSize = file.length()
            if (newSize == lastSize) {
                return
            }
            lastSize = newSize
            waitedMs += 200L
        }
    }

    private fun needsVideoOnlyRemux(file: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var hasVideo = false
            var hasAudio = false
            var audioInvalid = false
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    hasVideo = true
                    val csd = format.getByteBuffer("csd-0")
                    if (csd == null || csd.remaining() == 0) {
                        return false
                    }
                } else if (mime.startsWith("audio/")) {
                    hasAudio = true
                    val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    } else {
                        0
                    }
                    val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    } else {
                        0
                    }
                    val csd = format.getByteBuffer("csd-0")
                    if (sampleRate <= 0 || channels <= 0) {
                        audioInvalid = true
                    } else if (csd == null || csd.remaining() == 0) {
                        audioInvalid = true
                    }
                }
            }
            hasVideo && hasAudio && audioInvalid
        } catch (e: RuntimeException) {
            false
        } finally {
            extractor.release()
        }
    }

    private fun remuxVideoOnly(source: File, target: File): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(source.absolutePath)
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    videoTrackIndex = index
                    videoFormat = format
                    break
                }
            }
            if (videoTrackIndex == -1 || videoFormat == null) {
                return false
            }
            extractor.selectTrack(videoTrackIndex)
            muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(videoFormat)
            muxer.start()

            val bufferSize = if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                1024 * 256
            }
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    bufferInfo.size = 0
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }
            return true
        } catch (e: RuntimeException) {
            return false
        } finally {
            try {
                muxer?.stop()
            } catch (_: RuntimeException) {
            }
            try {
                muxer?.release()
            } catch (_: RuntimeException) {
            }
            extractor.release()
        }
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
