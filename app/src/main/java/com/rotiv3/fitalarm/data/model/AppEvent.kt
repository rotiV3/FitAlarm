package com.rotiv3.fitalarm.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "app_events")
data class AppEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val activityType: ActivityType = ActivityType.NONE,
    val startTime: Long,
    val endTime: Long,
    val location: String? = null,
    val notes: String? = null,
    val isSyncedToCalendar: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Maps to the shared CalendarEvent display model used across Calendar/Home screens. */
    fun toCalendarEvent() = CalendarEvent(
        id = id,
        title = title,
        description = notes,
        startTime = startTime,
        endTime = endTime,
        location = location,
        activityType = activityType,
        trainingPlan = null,
        colorId = null,
        isLocalEvent = true
    )
}
