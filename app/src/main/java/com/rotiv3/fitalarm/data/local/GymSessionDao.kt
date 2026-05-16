package com.rotiv3.fitalarm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rotiv3.fitalarm.data.model.GymSession
import com.rotiv3.fitalarm.data.model.SessionStatus

@Dao
interface GymSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: GymSession)

    @Query("SELECT * FROM gym_sessions WHERE eventId = :eventId")
    suspend fun getSession(eventId: String): GymSession?

    @Query("SELECT * FROM gym_sessions WHERE startTime >= :dayStart AND startTime < :dayEnd")
    suspend fun getSessionsForDay(dayStart: Long, dayEnd: Long): List<GymSession>

    @Query("UPDATE gym_sessions SET status = :status WHERE eventId = :eventId")
    suspend fun updateStatus(eventId: String, status: SessionStatus)

    @Query("UPDATE gym_sessions SET status = :status, completedAt = :completedAt WHERE eventId = :eventId")
    suspend fun markCompleted(eventId: String, status: SessionStatus, completedAt: Long)
}
