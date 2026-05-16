package com.rotiv3.fitalarm.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rotiv3.fitalarm.data.model.OutdoorAchievement
import kotlinx.coroutines.flow.Flow

@Dao
interface OutdoorAchievementDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedIfAbsent(achievements: List<OutdoorAchievement>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(achievement: OutdoorAchievement)

    @Query("SELECT * FROM outdoor_achievements WHERE id = :id")
    suspend fun getById(id: String): OutdoorAchievement?

    @Query("SELECT * FROM outdoor_achievements WHERE activityType = :activityType AND isUnlocked = 1 ORDER BY unlockedAt ASC")
    suspend fun getUnlockedByType(activityType: String): List<OutdoorAchievement>

    @Query("SELECT * FROM outdoor_achievements ORDER BY activityType, category, id")
    fun getAllFlow(): Flow<List<OutdoorAchievement>>

    @Query("SELECT COUNT(*) FROM outdoor_achievements WHERE id = :id")
    suspend fun exists(id: String): Int
}
