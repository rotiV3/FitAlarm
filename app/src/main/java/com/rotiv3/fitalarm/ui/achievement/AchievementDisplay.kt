package com.rotiv3.fitalarm.ui.achievement

import com.rotiv3.fitalarm.data.model.Achievement
import com.rotiv3.fitalarm.data.model.OutdoorAchievement

data class AchievementDisplay(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val contextText: String,
    val earnedAt: Long,
    val streakCount: Int = 0,
    val gymAchievementId: Int? = null
) {
    companion object {
        fun from(a: Achievement) = AchievementDisplay(
            id = "gym_${a.id}",
            emoji = a.getTrophyEmoji(),
            title = a.title,
            description = a.description,
            contextText = a.gymEventTitle,
            earnedAt = a.earnedAt,
            streakCount = a.streakCount,
            gymAchievementId = a.id
        )

        fun from(a: OutdoorAchievement) = AchievementDisplay(
            id = "outdoor_${a.id}",
            emoji = a.emoji,
            title = a.title,
            description = a.description,
            contextText = "${a.activityType.lowercase().replaceFirstChar { it.uppercase() }} achievement",
            earnedAt = a.unlockedAt
        )
    }
}
