package com.example.uvccamerademo

import android.content.Context
import android.media.MediaFormat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

enum class VideoCodec(val mimeType: String, val labelRes: Int) {
    HEVC(MediaFormat.MIMETYPE_VIDEO_HEVC, R.string.label_codec_hevc),
    AVC(MediaFormat.MIMETYPE_VIDEO_AVC, R.string.label_codec_avc);

    companion object {
        fun fromPersisted(value: String?): VideoCodec {
            return values().firstOrNull { it.name == value } ?: HEVC
        }
    }
}

class SettingsRepository(private val context: Context) {
    private val dataStore = context.appDataStore

    suspend fun loadSegmentIntervalMinutes(): Int {
        val prefs = dataStore.data.first()
        val stored = prefs[KEY_SEGMENT_INTERVAL_MINUTES] ?: DEFAULT_SEGMENT_INTERVAL_MINUTES
        return stored.coerceIn(MIN_SEGMENT_INTERVAL_MINUTES, MAX_SEGMENT_INTERVAL_MINUTES)
    }

    suspend fun saveSegmentIntervalMinutes(minutes: Int) {
        val normalized = minutes.coerceIn(MIN_SEGMENT_INTERVAL_MINUTES, MAX_SEGMENT_INTERVAL_MINUTES)
        dataStore.edit { prefs ->
            prefs[KEY_SEGMENT_INTERVAL_MINUTES] = normalized
        }
    }

    suspend fun loadVideoCodec(): VideoCodec {
        val prefs = dataStore.data.first()
        return VideoCodec.fromPersisted(prefs[KEY_VIDEO_CODEC])
    }

    suspend fun saveVideoCodec(codec: VideoCodec) {
        dataStore.edit { prefs ->
            prefs[KEY_VIDEO_CODEC] = codec.name
        }
    }

    companion object {
        const val DEFAULT_SEGMENT_INTERVAL_MINUTES = 5
        const val MIN_SEGMENT_INTERVAL_MINUTES = 1
        const val MAX_SEGMENT_INTERVAL_MINUTES = 10

        private val KEY_SEGMENT_INTERVAL_MINUTES = intPreferencesKey("segment_interval_minutes")
        private val KEY_VIDEO_CODEC = stringPreferencesKey("video_codec")
    }
}
