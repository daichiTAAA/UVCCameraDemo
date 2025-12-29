package com.example.uvccamerademo

import android.content.Context
import android.util.Base64
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    private val database = AppDatabase.getInstance(applicationContext)
    private val segmentDao = database.segmentDao()
    private val workDao = database.workDao()
    private val processRepository = ProcessRepository(applicationContext)

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val baseUrl = processRepository.loadApiUrl().trim()
            val endpoint = buildTusEndpoint(baseUrl)
            if (endpoint.isNullOrBlank()) {
                return@withContext Result.retry()
            }
            while (true) {
                if (isStopped) {
                    return@withContext Result.retry()
                }
                val candidate = segmentDao.findNextUploadCandidate(
                    states = listOf(UploadState.PENDING, UploadState.FAILED, UploadState.UPLOADING),
                    failedState = UploadState.FAILED,
                    maxRetryCount = MAX_RETRY_COUNT
                ) ?: return@withContext Result.success()
                val workId = candidate.workId
                if (workId.isNullOrBlank()) {
                    segmentDao.updateUploadFailure(
                        candidate.segmentUuid,
                        UploadState.FAILED,
                        MAX_RETRY_COUNT
                    )
                    continue
                }
                val work = workDao.findById(workId)
                if (work == null) {
                    segmentDao.updateUploadFailure(
                        candidate.segmentUuid,
                        UploadState.FAILED,
                        MAX_RETRY_COUNT
                    )
                    continue
                }
                val file = File(candidate.path)
                if (!file.exists()) {
                    segmentDao.updateUploadFailure(
                        candidate.segmentUuid,
                        UploadState.FAILED,
                        MAX_RETRY_COUNT
                    )
                    continue
                }

                segmentDao.updateUploadProgress(
                    candidate.segmentUuid,
                    UploadState.UPLOADING,
                    candidate.uploadRemoteId,
                    candidate.uploadBytesSent,
                    candidate.uploadRetryCount
                )

                val outcome = runCatching {
                    uploadSegment(
                        segment = candidate,
                        work = work,
                        file = file,
                        endpoint = endpoint
                    )
                }.getOrElse { error ->
                    UploadOutcome.RetryableFailure(error.localizedMessage)
                }
                when (outcome) {
                    is UploadOutcome.Completed -> {
                        segmentDao.markUploadCompleted(
                            candidate.segmentUuid,
                            UploadState.COMPLETED,
                            outcome.completedAt
                        )
                    }
                    is UploadOutcome.RetryableFailure -> {
                        val nextRetry = candidate.uploadRetryCount + 1
                        segmentDao.updateUploadFailure(
                            candidate.segmentUuid,
                            UploadState.FAILED,
                            nextRetry
                        )
                        if (nextRetry < MAX_RETRY_COUNT) {
                            return@withContext Result.retry()
                        }
                    }
                    is UploadOutcome.Stopped -> {
                        segmentDao.updateUploadProgress(
                            candidate.segmentUuid,
                            UploadState.PENDING,
                            outcome.remoteId,
                            outcome.bytesSent,
                            candidate.uploadRetryCount
                        )
                        return@withContext Result.retry()
                    }
                }
            }
            Result.success()
        }
    }

    private suspend fun uploadSegment(
        segment: SegmentEntity,
        work: WorkEntity,
        file: File,
        endpoint: String
    ): UploadOutcome {
        val metadata = buildMetadata(segment, work)
        val totalBytes = file.length()
        var uploadUrl = segment.uploadRemoteId
        var offset = segment.uploadBytesSent.coerceAtLeast(0L)

        if (uploadUrl != null) {
            when (val offsetResult = requestUploadOffset(uploadUrl)) {
                is OffsetResult.Found -> {
                    offset = offsetResult.offset.coerceAtMost(totalBytes)
                    segmentDao.updateUploadProgress(
                        segment.segmentUuid,
                        UploadState.UPLOADING,
                        uploadUrl,
                        offset,
                        segment.uploadRetryCount
                    )
                }
                OffsetResult.NotFound -> {
                    uploadUrl = null
                    offset = 0L
                }
                OffsetResult.Failed -> return UploadOutcome.RetryableFailure()
            }
        }

        if (uploadUrl == null) {
            val created = createUpload(endpoint, totalBytes, metadata) ?: return UploadOutcome.RetryableFailure()
            uploadUrl = created
            offset = 0L
            segmentDao.updateUploadProgress(
                segment.segmentUuid,
                UploadState.UPLOADING,
                uploadUrl,
                offset,
                segment.uploadRetryCount
            )
        }

        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            while (offset < totalBytes) {
                if (isStopped) {
                    return UploadOutcome.Stopped(uploadUrl, offset)
                }
                val remaining = totalBytes - offset
                val chunkSize = minOf(CHUNK_SIZE_BYTES, remaining).toInt()
                val buffer = ByteArray(chunkSize)
                val read = raf.read(buffer)
                if (read <= 0) {
                    break
                }
                val actualChunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                val newOffset = sendChunk(uploadUrl, offset, actualChunk) ?: return UploadOutcome.RetryableFailure()
                if (newOffset <= offset) {
                    return UploadOutcome.RetryableFailure("Offset did not advance")
                }
                offset = newOffset.coerceAtMost(totalBytes)
                segmentDao.updateUploadProgress(
                    segment.segmentUuid,
                    UploadState.UPLOADING,
                    uploadUrl,
                    offset,
                    segment.uploadRetryCount
                )
            }
        }
        return if (offset >= totalBytes) {
            UploadOutcome.Completed(System.currentTimeMillis())
        } else {
            UploadOutcome.RetryableFailure()
        }
    }

    private fun createUpload(
        endpoint: String,
        totalBytes: Long,
        metadata: Map<String, String>
    ): String? {
        val connection = openConnection(endpoint, "POST").apply {
            setRequestProperty(HEADER_TUS_RESUMABLE, TUS_VERSION)
            setRequestProperty(HEADER_UPLOAD_LENGTH, totalBytes.toString())
            setRequestProperty(HEADER_UPLOAD_METADATA, encodeMetadata(metadata))
            doOutput = true
            setFixedLengthStreamingMode(0)
        }
        connection.outputStream.use { }
        val code = connection.responseCode
        if (code !in 200..299 && code != 201) {
            connection.disconnect()
            return null
        }
        val location = connection.getHeaderField("Location") ?: run {
            connection.disconnect()
            return null
        }
        connection.disconnect()
        return resolveLocation(endpoint, location)
    }

    private fun requestUploadOffset(uploadUrl: String): OffsetResult {
        val connection = openConnection(uploadUrl, "HEAD").apply {
            setRequestProperty(HEADER_TUS_RESUMABLE, TUS_VERSION)
        }
        val code = connection.responseCode
        if (code == 404) {
            connection.disconnect()
            return OffsetResult.NotFound
        }
        if (code !in 200..299) {
            connection.disconnect()
            return OffsetResult.Failed
        }
        val offsetHeader = connection.getHeaderField(HEADER_UPLOAD_OFFSET)
        connection.disconnect()
        val offset = offsetHeader?.toLongOrNull() ?: return OffsetResult.Failed
        return OffsetResult.Found(offset)
    }

    private fun sendChunk(uploadUrl: String, offset: Long, data: ByteArray): Long? {
        val connection = openConnection(uploadUrl, "PATCH").apply {
            setRequestProperty(HEADER_TUS_RESUMABLE, TUS_VERSION)
            setRequestProperty(HEADER_UPLOAD_OFFSET, offset.toString())
            setRequestProperty("Content-Type", "application/offset+octet-stream")
            doOutput = true
            setFixedLengthStreamingMode(data.size)
        }
        connection.outputStream.use { stream ->
            stream.write(data)
        }
        val code = connection.responseCode
        if (code !in 200..299 && code != 204) {
            val recovered = connection.getHeaderField(HEADER_UPLOAD_OFFSET)?.toLongOrNull()
            connection.disconnect()
            return recovered
        }
        val newOffset = connection.getHeaderField(HEADER_UPLOAD_OFFSET)?.toLongOrNull()
        connection.disconnect()
        return newOffset ?: (offset + data.size)
    }

    private fun buildMetadata(segment: SegmentEntity, work: WorkEntity): Map<String, String> {
        val metadata = linkedMapOf<String, String>()
        metadata["segmentUuid"] = segment.segmentUuid
        metadata["workId"] = work.workId
        metadata["model"] = work.model
        metadata["serial"] = work.serial
        metadata["process"] = work.process
        segment.segmentIndex?.let { metadata["segmentIndex"] = it.toString() }
        metadata["recordedAt"] = formatRecordedAt(segment.recordedAt)
        segment.durationMs?.let { metadata["durationSec"] = "%.1f".format(Locale.US, it / 1000.0) }
        metadata["appVersion"] = BuildConfig.VERSION_NAME
        return metadata
    }

    private fun formatRecordedAt(recordedAt: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(recordedAt)
    }

    private fun encodeMetadata(metadata: Map<String, String>): String {
        return metadata.entries.joinToString(",") { entry ->
            val encoded = Base64.encodeToString(
                entry.value.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            "${entry.key} $encoded"
        }
    }

    private fun buildTusEndpoint(baseUrl: String): String? {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) {
            return null
        }
        return try {
            val base = URL(trimmed)
            val path = base.path ?: ""
            val apiIndex = path.indexOf("/api")
            val basePath = if (apiIndex >= 0) path.substring(0, apiIndex) else path
            val normalizedBase = if (basePath.endsWith("/")) {
                basePath.dropLast(1)
            } else {
                basePath
            }
            val uploadPath = if (normalizedBase.isBlank()) {
                TUSD_PATH
            } else {
                normalizedBase + TUSD_PATH
            }
            URL(base.protocol, base.host, base.port, uploadPath).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveLocation(endpoint: String, location: String): String {
        return try {
            val base = URL(endpoint)
            URL(base, location).toString()
        } catch (_: Exception) {
            location
        }
    }

    private fun openConnection(url: String, method: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
        } catch (e: ProtocolException) {
            if (method == "PATCH") {
                connection.requestMethod = "POST"
                connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                throw e
            }
        }
        if (BuildConfig.TUSD_UPLOAD_API_KEY.isNotBlank()) {
            connection.setRequestProperty("X-Api-Key", BuildConfig.TUSD_UPLOAD_API_KEY)
        }
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.useCaches = false
        connection.doInput = true
        return connection
    }

    private sealed interface UploadOutcome {
        data class Completed(val completedAt: Long) : UploadOutcome
        data class RetryableFailure(val reason: String? = null) : UploadOutcome
        data class Stopped(val remoteId: String?, val bytesSent: Long) : UploadOutcome
    }

    private sealed interface OffsetResult {
        data class Found(val offset: Long) : OffsetResult
        object NotFound : OffsetResult
        object Failed : OffsetResult
    }

    companion object {
        private const val TUS_VERSION = "1.0.0"
        private const val HEADER_TUS_RESUMABLE = "Tus-Resumable"
        private const val HEADER_UPLOAD_LENGTH = "Upload-Length"
        private const val HEADER_UPLOAD_METADATA = "Upload-Metadata"
        private const val HEADER_UPLOAD_OFFSET = "Upload-Offset"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val CHUNK_SIZE_BYTES = 1 * 1024 * 1024L
        private const val MAX_RETRY_COUNT = 5
        private const val TUSD_PATH = "/files/"
    }
}
