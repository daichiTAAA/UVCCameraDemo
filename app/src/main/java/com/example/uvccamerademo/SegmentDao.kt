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
            sizeBytes = :sizeBytes
        WHERE segmentUuid = :segmentUuid
        """
    )
    suspend fun updateFinalized(segmentUuid: String, durationMs: Long, sizeBytes: Long)

    @Query("DELETE FROM segments WHERE segmentUuid = :segmentUuid")
    suspend fun deleteById(segmentUuid: String)
}
