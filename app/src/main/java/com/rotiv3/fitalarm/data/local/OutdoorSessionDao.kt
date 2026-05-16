package com.rotiv3.fitalarm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rotiv3.fitalarm.data.model.OutdoorSession
import kotlinx.coroutines.flow.Flow

@Dao
interface OutdoorSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: OutdoorSession)

    @Query("SELECT * FROM outdoor_sessions WHERE eventId = :eventId")
    suspend fun getSession(eventId: String): OutdoorSession?

    @Query("SELECT * FROM outdoor_sessions WHERE subType = :subType")
    suspend fun getSessionsBySubType(subType: String): List<OutdoorSession>

    @Query("SELECT * FROM outdoor_sessions ORDER BY startTime DESC")
    suspend fun getAllSessions(): List<OutdoorSession>

    @Query("SELECT * FROM outdoor_sessions ORDER BY startTime DESC")
    fun getAllSessionsFlow(): Flow<List<OutdoorSession>>
}
