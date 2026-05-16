package com.rotiv3.fitalarm.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AchievementType {
    FIRST_CHECKIN,
    STREAK_3,
    STREAK_7,
    STREAK_30,
    EARLY_BIRD,
    CONSISTENT
}

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: AchievementType,
    val title: String,
    val description: String,
    val earnedAt: Long,
    val gymEventTitle: String,
    val streakCount: Int = 1,
    val isShared: Boolean = false
) {
    fun getTrophyEmoji(): String = when (type) {
        AchievementType.FIRST_CHECKIN -> "🥇"
        AchievementType.STREAK_3 -> "🏆"
        AchievementType.STREAK_7 -> "🌟"
        AchievementType.STREAK_30 -> "💎"
        AchievementType.EARLY_BIRD -> "🌅"
        AchievementType.CONSISTENT -> "💪"
    }

    companion object {
        fun createForStreak(streak: Int, gymEventTitle: String): Achievement? {
            val now = System.currentTimeMillis()
            return when (streak) {
                1 -> Achievement(
                    type = AchievementType.FIRST_CHECKIN,
                    title = "First Check-In!",
                    description = "You completed your first gym session. The journey begins!",
                    earnedAt = now,
                    gymEventTitle = gymEventTitle,
                    streakCount = 1
                )
                3 -> Achievement(
                    type = AchievementType.STREAK_3,
                    title = "3-Day Streak!",
                    description = "You've worked out 3 days in a row. Building the habit!",
                    earnedAt = now,
                    gymEventTitle = gymEventTitle,
                    streakCount = 3
                )
                7 -> Achievement(
                    type = AchievementType.STREAK_7,
                    title = "Week Warrior!",
                    description = "7-day workout streak! You're on fire!",
                    earnedAt = now,
                    gymEventTitle = gymEventTitle,
                    streakCount = 7
                )
                30 -> Achievement(
                    type = AchievementType.STREAK_30,
                    title = "Monthly Champion!",
                    description = "30-day workout streak! Incredible dedication!",
                    earnedAt = now,
                    gymEventTitle = gymEventTitle,
                    streakCount = 30
                )
                else -> null
            }
        }
    }
}
