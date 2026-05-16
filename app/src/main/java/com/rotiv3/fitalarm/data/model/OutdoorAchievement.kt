package com.rotiv3.fitalarm.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outdoor_achievements")
data class OutdoorAchievement(
    @PrimaryKey val id: String,
    val activityType: String,  // WALK, RUN, HIKE, BIKE
    val category: String,      // COUNT, SINGLE, TOTAL
    val title: String,
    val description: String,
    val emoji: String,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long = 0L
)

object OutdoorAchievements {

    fun buildAll(): List<OutdoorAchievement> {
        val types = listOf(
            Triple("WALK", "Dog Walk", "🐾"),
            Triple("RUN",  "Run",      "🏃"),
            Triple("HIKE", "Hike",     "🥾"),
            Triple("BIKE", "Bike Ride","🚴")
        )
        return types.flatMap { (type, label, _) ->
            listOf(
                // Count milestones
                OutdoorAchievement("${type}_COUNT_1",  type, "COUNT", "First $label!", "You completed your first $label. The adventure begins!", "🥇"),
                OutdoorAchievement("${type}_COUNT_3",  type, "COUNT", "$label Regular", "3 ${label}s done — making it a habit!", "🏅"),
                OutdoorAchievement("${type}_COUNT_5",  type, "COUNT", "$label Enthusiast", "5 ${label}s! You're getting serious.", "🏆"),
                OutdoorAchievement("${type}_COUNT_10", type, "COUNT", "$label Veteran", "10 ${label}s completed. Incredible!", "💎"),
                // Single-session distance
                OutdoorAchievement("${type}_SINGLE_1000",  type, "SINGLE", "1 km $label",   "Covered 1 km in a single $label.", "🌟"),
                OutdoorAchievement("${type}_SINGLE_2500",  type, "SINGLE", "2.5 km $label", "Pushed through 2.5 km in one go!", "⭐"),
                OutdoorAchievement("${type}_SINGLE_5000",  type, "SINGLE", "5 km $label",   "5 km in a single $label — phenomenal!", "🔥"),
                // Total distance
                OutdoorAchievement("${type}_TOTAL_5000",  type, "TOTAL", "5 km Club",  "Covered 5 km total across all ${label}s.", "🌱"),
                OutdoorAchievement("${type}_TOTAL_10000", type, "TOTAL", "10 km Club", "10 km total distance — keep going!", "🌍"),
                OutdoorAchievement("${type}_TOTAL_50000", type, "TOTAL", "50 km Club", "50 km total — you are an absolute machine!", "🚀")
            )
        }
    }
}
