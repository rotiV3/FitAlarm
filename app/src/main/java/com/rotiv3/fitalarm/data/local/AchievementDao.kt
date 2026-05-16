package com.rotiv3.fitalarm.data.local

import androidx.room.*
import com.rotiv3.fitalarm.data.model.Achievement
import com.rotiv3.fitalarm.data.model.AchievementType
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(achievement: Achievement): Long

    @Query("SELECT * FROM achievements ORDER BY earnedAt DESC")
    fun getAll(): Flow<List<Achievement>>

    @Query("SELECT COUNT(*) FROM achievements")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM achievements WHERE type = :type")
    suspend fun getCountByType(type: AchievementType): Int

    @Query("SELECT MAX(streakCount) FROM achievements")
    suspend fun getMaxStreak(): Int?

    @Query("SELECT * FROM achievements ORDER BY earnedAt DESC LIMIT 1")
    suspend fun getLatest(): Achievement?

    @Query("SELECT * FROM achievements WHERE type = :type ORDER BY earnedAt DESC LIMIT 1")
    suspend fun getLatestByType(type: AchievementType): Achievement?

    @Query("UPDATE achievements SET isShared = 1 WHERE id = :id")
    suspend fun markAsShared(id: Int)

    /**
     * Returns the current consecutive streak count.
     * Each achievement with streakCount represents a check-in.
     * We look at the most recent check-in achievements.
     */
    @Query("""
        SELECT streakCount FROM achievements
        WHERE type = :firstType OR type = :streak3 OR type = :streak7 OR type = :streak30
        ORDER BY earnedAt DESC
        LIMIT 1
    """)
    suspend fun getLatestStreakCount(
        firstType: AchievementType = AchievementType.FIRST_CHECKIN,
        streak3: AchievementType = AchievementType.STREAK_3,
        streak7: AchievementType = AchievementType.STREAK_7,
        streak30: AchievementType = AchievementType.STREAK_30
    ): Int?
}
