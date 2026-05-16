package com.rotiv3.fitalarm.data.model

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String?,
    val startTime: Long,
    val endTime: Long,
    val location: String?,
    val activityType: ActivityType = ActivityType.NONE,
    val trainingPlan: String?,
    val colorId: String?,
    val sessionStatus: SessionStatus = SessionStatus.UPCOMING
) {
    val isGymEvent: Boolean get() = activityType != ActivityType.NONE

    companion object {
        // Fixed-location training events — GPS check-in at a saved gym
        private val GYM_KEYWORDS = setOf(
            "gym", "workout", "training", "exercise", "lift", "crossfit",
            "weightlifting", "powerlifting", "strength", "bootcamp",
            "yoga", "pilates", "hiit", "cardio", "aerobics", "zumba",
            "martial arts", "boxing", "kickboxing",
            "swim", "swimming", "rowing",
            "fitness", "sport", "sports", "session", "practice", "class"
        )

        // Outdoor / mobile activities — confirmation notification only, no GPS geofence
        private val OUTDOOR_KEYWORDS = setOf(
            "run", "running", "jog", "jogging",
            "walk", "walking", "dog walk", "nature walk",
            "cycling", "bike", "biking", "bicycle", "ride", "bike ride",
            "hike", "hiking", "trail", "trek", "trekking", "climbing",
            "outdoor", "outdoors"
        )

        private val TRAINING_PLAN_MARKERS = listOf(
            "plan:", "workout:", "exercises:", "training plan:", "session:",
            "sets:", "reps:", "training:", "routine:"
        )

        fun classifyActivity(title: String, description: String?): ActivityType {
            val combined = title.lowercase()
            return when {
                OUTDOOR_KEYWORDS.any { combined.contains(it) } -> ActivityType.OUTDOOR
                GYM_KEYWORDS.any { combined.contains(it) } -> ActivityType.GYM
                else -> ActivityType.NONE
            }
        }

        fun extractTrainingPlan(description: String?): String? {
            if (description.isNullOrBlank()) return null

            val lowerDesc = description.lowercase()
            val markerIndex = TRAINING_PLAN_MARKERS
                .mapNotNull { marker ->
                    val idx = lowerDesc.indexOf(marker)
                    if (idx >= 0) idx else null
                }
                .minOrNull()

            return if (markerIndex != null) {
                description.substring(markerIndex).trim()
            } else {
                description.trim()
            }
        }

        fun fromApiEvent(
            id: String,
            title: String,
            description: String?,
            startTime: Long,
            endTime: Long,
            location: String?,
            colorId: String?
        ): CalendarEvent {
            val activityType = classifyActivity(title, description)
            val trainingPlan = if (activityType == ActivityType.GYM) extractTrainingPlan(description) else null

            return CalendarEvent(
                id = id,
                title = title,
                description = description,
                startTime = startTime,
                endTime = endTime,
                location = location,
                activityType = activityType,
                trainingPlan = trainingPlan,
                colorId = colorId
            )
        }
    }
}
