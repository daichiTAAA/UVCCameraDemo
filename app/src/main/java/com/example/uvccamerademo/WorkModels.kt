package com.example.uvccamerademo

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class WorkState {
    ACTIVE,
    PAUSED,
    ENDED
}

enum class UploadState {
    NONE,
    PENDING,
    UPLOADING,
    FAILED,
    COMPLETED
}

@Entity(tableName = "works")
data class WorkEntity(
    @PrimaryKey val workId: String,
    val model: String,
    val serial: String,
    val process: String,
    val state: WorkState,
    val startedAt: Long,
    val endedAt: Long?
)

@Entity(
    tableName = "segments",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["workId"])
    ]
)
data class SegmentEntity(
    @PrimaryKey val segmentUuid: String,
    val path: String,
    val recordedAt: Long,
    val durationMs: Long?,
    val sizeBytes: Long?,
    val workId: String?,
    val segmentIndex: Int?,
    val uploadState: UploadState,
    val uploadRemoteId: String?,
    val uploadBytesSent: Long,
    val uploadCompletedAt: Long?
)
