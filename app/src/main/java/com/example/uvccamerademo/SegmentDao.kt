package com.example.uvccamerademo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SegmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(segment: SegmentEntity)

    @Query("SELECT MAX(segmentIndex) FROM segments WHERE workId = :workId")
    suspend fun maxSegmentIndex(workId: String): Int?

    @Query(
        """
        UPDATE segments
        SET durationMs = :durationMs,
            sizeBytes = :sizeBytes,
            uploadState = CASE
                WHEN workId IS NOT NULL AND uploadState = :pendingState THEN :pendingState
                WHEN workId IS NOT NULL AND uploadState = :noneState THEN :pendingState
                ELSE uploadState
            END
        WHERE segmentUuid = :segmentUuid
        """
    )
    suspend fun updateFinalized(
        segmentUuid: String,
        durationMs: Long,
        sizeBytes: Long,
        pendingState: UploadState,
        noneState: UploadState
    )

    @Query("DELETE FROM segments WHERE segmentUuid = :segmentUuid")
    suspend fun deleteById(segmentUuid: String)

    @Query("SELECT * FROM segments WHERE segmentUuid = :segmentUuid")
    suspend fun findById(segmentUuid: String): SegmentEntity?

    @Query("SELECT * FROM segments WHERE path = :path LIMIT 1")
    suspend fun findByPath(path: String): SegmentEntity?

    @Query(
        """
        SELECT segments.segmentUuid AS segmentUuid,
               segments.recordedAt AS recordedAt,
               segments.workId AS workId,
               segments.segmentIndex AS segmentIndex,
               segments.uploadState AS uploadState,
               works.model AS model,
               works.serial AS serial,
               works.process AS process
        FROM segments
        LEFT JOIN works ON segments.workId = works.workId
        WHERE segments.path = :path
        LIMIT 1
        """
    )
    suspend fun findMetadataByPath(path: String): SegmentMetadata?

    @Query("SELECT * FROM segments WHERE workId = :workId ORDER BY recordedAt ASC")
    suspend fun listByWork(workId: String): List<SegmentEntity>

    @Query(
        """
        UPDATE segments
        SET workId = :workId,
            uploadState = :uploadState
        WHERE segmentUuid = :segmentUuid
        """
    )
    suspend fun assignWork(
        segmentUuid: String,
        workId: String,
        uploadState: UploadState
    )

    @Query("UPDATE segments SET segmentIndex = :segmentIndex WHERE segmentUuid = :segmentUuid")
    suspend fun updateSegmentIndex(segmentUuid: String, segmentIndex: Int)

    @Query(
        """
        SELECT * FROM segments
        WHERE workId IS NOT NULL
            AND durationMs IS NOT NULL
            AND sizeBytes IS NOT NULL
            AND uploadState IN (:states)
            AND (uploadState != :failedState OR uploadRetryCount < :maxRetryCount)
        ORDER BY recordedAt ASC
        LIMIT 1
        """
    )
    suspend fun findNextUploadCandidate(
        states: List<UploadState>,
        failedState: UploadState,
        maxRetryCount: Int
    ): SegmentEntity?

    @Query(
        """
        UPDATE segments
        SET uploadState = :state,
            uploadRemoteId = :remoteId,
            uploadBytesSent = :bytesSent,
            uploadRetryCount = :retryCount
        WHERE segmentUuid = :segmentUuid
        """
    )
    suspend fun updateUploadProgress(
        segmentUuid: String,
        state: UploadState,
        remoteId: String?,
        bytesSent: Long,
        retryCount: Int
    )

    @Query(
        """
        UPDATE segments
        SET uploadState = :state,
            uploadRetryCount = :retryCount
        WHERE segmentUuid = :segmentUuid
        """
    )
    suspend fun updateUploadFailure(
        segmentUuid: String,
        state: UploadState,
        retryCount: Int
    )

    @Query(
        """
        UPDATE segments
        SET uploadState = :state,
            uploadCompletedAt = :completedAt
        WHERE segmentUuid = :segmentUuid
        """
    )
    suspend fun markUploadCompleted(
        segmentUuid: String,
        state: UploadState,
        completedAt: Long
    )
}
