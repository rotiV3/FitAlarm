package com.rotiv3.fitalarm.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wakeup_alarms")
data class WakeupAlarm(
    @PrimaryKey val dateEpochDay: Long,
    val wakeupTimeMillis: Long,
    val isSet: Boolean = false,
    val notes: String? = null,
    val eventTitle: String? = null,
    val leadTimeMinutes: Int = 30
)
