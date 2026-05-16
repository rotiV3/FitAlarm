package com.rotiv3.fitalarm.data.local

import androidx.room.*
import com.rotiv3.fitalarm.data.model.WakeupAlarm
import kotlinx.coroutines.flow.Flow

@Dao
interface WakeupAlarmDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: WakeupAlarm): Long

    @Query("SELECT * FROM wakeup_alarms WHERE dateEpochDay = :dateEpochDay")
    suspend fun getByDate(dateEpochDay: Long): WakeupAlarm?

    @Query("SELECT * FROM wakeup_alarms ORDER BY dateEpochDay ASC")
    fun getAll(): Flow<List<WakeupAlarm>>

    @Query("SELECT * FROM wakeup_alarms WHERE isSet = 1 ORDER BY wakeupTimeMillis ASC")
    suspend fun getActiveAlarms(): List<WakeupAlarm>

    @Query("DELETE FROM wakeup_alarms WHERE dateEpochDay = :dateEpochDay")
    suspend fun deleteByDate(dateEpochDay: Long)

    @Query("UPDATE wakeup_alarms SET isSet = :isSet WHERE dateEpochDay = :dateEpochDay")
    suspend fun setAlarmStatus(dateEpochDay: Long, isSet: Boolean)

    @Query("SELECT * FROM wakeup_alarms WHERE wakeupTimeMillis > :afterMillis ORDER BY wakeupTimeMillis ASC LIMIT 1")
    suspend fun getNextAlarm(afterMillis: Long): WakeupAlarm?
}
