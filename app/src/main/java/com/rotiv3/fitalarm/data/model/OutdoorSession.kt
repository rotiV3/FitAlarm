package com.rotiv3.fitalarm.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outdoor_sessions")
data class OutdoorSession(
    @PrimaryKey val eventId: String,
    val eventTitle: String,
    val startTime: Long,
    val endTime: Long = 0L,
    val routeJson: String = "[]",
    val totalDistanceMeters: Float = 0f,
    val durationSeconds: Long = 0L,
    val subType: String = "OUTDOOR"  // WALK, RUN, HIKE, BIKE, OUTDOOR
) {
    companion object {
        fun classifySubType(title: String): String {
            val t = title.lowercase()
            return when {
                listOf("run", "running", "jog", "jogging").any { t.contains(it) } -> "RUN"
                listOf("walk", "walking", "dog walk", "stroll").any { t.contains(it) } -> "WALK"
                listOf("hike", "hiking", "trail").any { t.contains(it) } -> "HIKE"
                listOf("bike", "biking", "cycling", "cycle", "bicycle", "ride").any { t.contains(it) } -> "BIKE"
                else -> "OUTDOOR"
            }
        }

        fun subTypeEmoji(subType: String) = when (subType) {
            "RUN" -> "🏃"
            "WALK" -> "🐾"
            "HIKE" -> "🥾"
            "BIKE" -> "🚴"
            else -> "🌿"
        }

        fun subTypeLabel(subType: String) = when (subType) {
            "RUN" -> "Run"
            "WALK" -> "Dog Walk"
            "HIKE" -> "Hike"
            "BIKE" -> "Bike Ride"
            else -> "Outdoor Activity"
        }
    }
}
