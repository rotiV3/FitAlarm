package com.rotiv3.fitalarm.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rotiv3.fitalarm.data.model.AppEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface AppEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: AppEvent)

    @Update
    suspend fun update(event: AppEvent)

    @Delete
    suspend fun delete(event: AppEvent)

    @Query("SELECT * FROM app_events WHERE id = :id")
    suspend fun getById(id: String): AppEvent?

    /** All events for today (ordered by start time). */
    @Query("SELECT * FROM app_events WHERE startTime >= :dayStart AND startTime < :dayEnd ORDER BY startTime ASC")
    fun getEventsForDay(dayStart: Long, dayEnd: Long): Flow<List<AppEvent>>

    /** All events in a time window — used by Calendar screen. */
    @Query("SELECT * FROM app_events WHERE startTime >= :from AND endTime <= :to ORDER BY startTime ASC")
    fun getEventsInRange(from: Long, to: Long): Flow<List<AppEvent>>

    /** All events, latest first — used to merge with calendar on home screen. */
    @Query("SELECT * FROM app_events ORDER BY startTime ASC")
    fun getAllFlow(): Flow<List<AppEvent>>
}
