package com.example.uvccamerademo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WorkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(work: WorkEntity)

    @Query("SELECT * FROM works WHERE workId = :workId")
    suspend fun findById(workId: String): WorkEntity?

    @Query("SELECT * FROM works ORDER BY startedAt DESC")
    suspend fun listAll(): List<WorkEntity>

    @Query("UPDATE works SET state = :state, endedAt = :endedAt WHERE workId = :workId")
    suspend fun updateState(workId: String, state: WorkState, endedAt: Long?)
}
