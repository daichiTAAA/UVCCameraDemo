package com.example.uvccamerademo

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.jiangdg.ausbc.callback.ICaptureCallBack
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("DEPRECATION")
class HevcRecorder(
    context: Context,
    private val width: Int,
    private val height: Int,
    private val outputFile: File,
    private val callback: ICaptureCallBack
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recording = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val errorReported = AtomicBoolean(false)
    private val videoQueue = ArrayBlockingQueue<ByteArray>(3)

    private var videoCodec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private var muxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false
    private var videoColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
    private var lastVideoPtsUs = 0L
    private var lastAudioPtsUs = 0L
    private var audioPtsUs = 0L
    private var videoThread: Thread? = null
    private var audioThread: Thread? = null

    private val audioSampleRate = 44100
    private val audioChannelCount = 1
    private val audioBitrate = 64000

    @Volatile
    var isAudioEnabled: Boolean = true
        private set

    fun start(): Boolean {
        if (recording.getAndSet(true)) {
            return false
        }
        try {
            prepareOutputFile()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            videoCodec = createVideoEncoder()
            if (videoCodec == null) {
                reportError("HEVC encoder not available")
                return false
            }
            isAudioEnabled = initAudioEncoder()
            stopRequested.set(false)
            videoThread = Thread(::runVideoLoop, "hevc-video")
            videoThread?.start()
            if (isAudioEnabled) {
                audioThread = Thread(::runAudioLoop, "hevc-audio")
                audioThread?.start()
            }
            postOnMain { callback.onBegin() }
            return true
        } catch (e: Exception) {
            reportError("Failed to start recording: ${e.localizedMessage}")
            return false
        }
    }

    fun stop() {
        if (!recording.getAndSet(false)) {
            return
        }
        if (stopping.getAndSet(true)) {
            return
        }
        stopRequested.set(true)
        Thread {
            try {
                videoThread?.join(2000)
                audioThread?.join(2000)
            } catch (_: InterruptedException) {
            } finally {
                releaseResources()
                postOnMain { callback.onComplete(outputFile.absolutePath) }
            }
        }.start()
    }

    fun onPreviewFrame(data: ByteArray) {
        if (!recording.get()) {
            return
        }
        val expectedSize = width * height * 3 / 2
        if (data.size < expectedSize) {
            return
        }
        val copy = ByteArray(expectedSize)
        System.arraycopy(data, 0, copy, 0, expectedSize)
        if (!videoQueue.offer(copy)) {
            videoQueue.poll()
            videoQueue.offer(copy)
        }
    }

    private fun prepareOutputFile() {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }
    }

    private fun createVideoEncoder(): MediaCodec? {
        val mime = MediaFormat.MIMETYPE_VIDEO_HEVC
        val codecInfo = selectCodec(mime) ?: return null
        val format = MediaFormat.createVideoFormat(mime, width, height)
        videoColorFormat = selectColorFormat(codecInfo, mime)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, videoColorFormat)
        format.setInteger(MediaFormat.KEY_BIT_RATE, estimateBitrate(width, height))
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        val codec = MediaCodec.createByCodecName(codecInfo.name)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        return codec
    }

    private fun initAudioEncoder(): Boolean {
        return try {
            val bufferSize = AudioRecord.getMinBufferSize(
                audioSampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (bufferSize <= 0) {
                false
            } else {
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    audioSampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize * 2
                )
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    false
                } else {
                    val format = MediaFormat.createAudioFormat(
                        MediaFormat.MIMETYPE_AUDIO_AAC,
                        audioSampleRate,
                        audioChannelCount
                    )
                    format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    format.setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate)
                    format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
                    val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                    codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    codec.start()
                    audioRecord = record
                    audioCodec = codec
                    true
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun runVideoLoop() {
        val codec = videoCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        val startNs = System.nanoTime()
        var inputEosQueued = false
        while (true) {
            val frame = videoQueue.poll(10, TimeUnit.MILLISECONDS)
            if (frame != null) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        writeNv21ToBuffer(inputBuffer, frame)
                        val ptsUs = (System.nanoTime() - startNs) / 1000
                        codec.queueInputBuffer(inputIndex, 0, inputBuffer.position(), ptsUs, 0)
                    }
                }
            } else if (stopRequested.get() && !inputEosQueued && videoQueue.isEmpty()) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    codec.queueInputBuffer(
                        inputIndex,
                        0,
                        0,
                        (System.nanoTime() - startNs) / 1000,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    inputEosQueued = true
                }
            }

            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            while (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }
                if (bufferInfo.size > 0 && muxerStarted) {
                    bufferInfo.presentationTimeUs = ensureMonotonic(bufferInfo.presentationTimeUs, true)
                    outputBuffer?.position(bufferInfo.offset)
                    outputBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                    muxer?.writeSampleData(videoTrackIndex, outputBuffer ?: ByteBuffer.allocate(0), bufferInfo)
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    return
                }
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = codec.outputFormat
                synchronized(this) {
                    if (videoTrackIndex == -1) {
                        videoTrackIndex = muxer?.addTrack(newFormat) ?: -1
                        startMuxerIfReady()
                    }
                }
            }
        }
    }

    private fun runAudioLoop() {
        val codec = audioCodec ?: return
        val record = audioRecord ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        val audioBuffer = ByteArray(2048)
        var inputEosQueued = false
        record.startRecording()
        while (true) {
            if (!stopRequested.get()) {
                val read = record.read(audioBuffer, 0, audioBuffer.size)
                if (read > 0) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            inputBuffer.put(audioBuffer, 0, read)
                            val ptsUs = audioPtsUs
                            val samples = read / 2
                            audioPtsUs += samples * 1_000_000L / audioSampleRate
                            codec.queueInputBuffer(inputIndex, 0, read, ptsUs, 0)
                        }
                    }
                }
            } else if (!inputEosQueued) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    codec.queueInputBuffer(
                        inputIndex,
                        0,
                        0,
                        audioPtsUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    inputEosQueued = true
                }
            }

            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            while (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }
                if (bufferInfo.size > 0 && muxerStarted) {
                    bufferInfo.presentationTimeUs = ensureMonotonic(bufferInfo.presentationTimeUs, false)
                    outputBuffer?.position(bufferInfo.offset)
                    outputBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                    muxer?.writeSampleData(audioTrackIndex, outputBuffer ?: ByteBuffer.allocate(0), bufferInfo)
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    return
                }
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = codec.outputFormat
                synchronized(this) {
                    if (audioTrackIndex == -1) {
                        audioTrackIndex = muxer?.addTrack(newFormat) ?: -1
                        startMuxerIfReady()
                    }
                }
            }
        }
    }

    private fun startMuxerIfReady() {
        if (muxerStarted) {
            return
        }
        if (videoTrackIndex == -1) {
            return
        }
        if (isAudioEnabled && audioTrackIndex == -1) {
            return
        }
        muxer?.start()
        muxerStarted = true
    }

    private fun writeNv21ToBuffer(buffer: ByteBuffer, data: ByteArray) {
        buffer.clear()
        val frameSize = width * height
        buffer.put(data, 0, frameSize)
        when (videoColorFormat) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar -> {
                for (i in 0 until frameSize / 2 step 2) {
                    buffer.put(data[frameSize + i + 1])
                }
                for (i in 0 until frameSize / 2 step 2) {
                    buffer.put(data[frameSize + i])
                }
            }
            else -> {
                for (i in 0 until frameSize / 2 step 2) {
                    buffer.put(data[frameSize + i + 1])
                    buffer.put(data[frameSize + i])
                }
            }
        }
    }

    private fun ensureMonotonic(ptsUs: Long, isVideo: Boolean): Long {
        return if (isVideo) {
            if (ptsUs <= lastVideoPtsUs) {
                lastVideoPtsUs += 1
                lastVideoPtsUs
            } else {
                lastVideoPtsUs = ptsUs
                ptsUs
            }
        } else {
            if (ptsUs <= lastAudioPtsUs) {
                lastAudioPtsUs += 1
                lastAudioPtsUs
            } else {
                lastAudioPtsUs = ptsUs
                ptsUs
            }
        }
    }

    private fun selectCodec(mimeType: String): MediaCodecInfo? {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        return codecList.firstOrNull { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
        }
    }

    private fun selectColorFormat(codecInfo: MediaCodecInfo, mimeType: String): Int {
        val caps = codecInfo.getCapabilitiesForType(mimeType)
        val preferred = listOf(
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        )
        for (format in preferred) {
            if (caps.colorFormats.contains(format)) {
                return format
            }
        }
        return caps.colorFormats.firstOrNull()
            ?: MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
    }

    private fun estimateBitrate(width: Int, height: Int): Int {
        return width * height * 4
    }

    private fun reportError(message: String) {
        if (errorReported.getAndSet(true)) {
            return
        }
        releaseResources()
        postOnMain { callback.onError(message) }
    }

    private fun releaseResources() {
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
        }
        audioRecord?.release()
        audioRecord = null
        try {
            audioCodec?.stop()
        } catch (_: IllegalStateException) {
        }
        audioCodec?.release()
        audioCodec = null
        try {
            videoCodec?.stop()
        } catch (_: IllegalStateException) {
        }
        videoCodec?.release()
        videoCodec = null
        try {
            if (muxerStarted) {
                muxer?.stop()
            }
        } catch (_: IllegalStateException) {
        }
        muxer?.release()
        muxer = null
        muxerStarted = false
        videoTrackIndex = -1
        audioTrackIndex = -1
    }

    private fun postOnMain(block: () -> Unit) {
        mainHandler.post(block)
    }
}
