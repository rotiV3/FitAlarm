package com.rotiv3.fitalarm.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gym_sessions")
data class GymSession(
    @PrimaryKey val eventId: String,
    val eventTitle: String,
    val startTime: Long,
    val endTime: Long,
    val status: SessionStatus,
    val completedAt: Long? = null
)
