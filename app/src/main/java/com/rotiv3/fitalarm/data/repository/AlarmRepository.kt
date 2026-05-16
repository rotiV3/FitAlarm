package com.rotiv3.fitalarm.data.repository

import android.accounts.Account
import com.rotiv3.fitalarm.alarm.AppAlarmScheduler
import com.rotiv3.fitalarm.data.local.WakeupAlarmDao
import com.rotiv3.fitalarm.data.model.ActivityType
import com.rotiv3.fitalarm.data.model.WakeupAlarm
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepository @Inject constructor(
    private val wakeupAlarmDao: WakeupAlarmDao,
    private val alarmScheduler: AppAlarmScheduler
) {

    suspend fun scheduleWakeupAlarm(
        date: LocalDate,
        wakeupTimeMillis: Long,
        label: String = "Wake Up",
        leadTimeMinutes: Int = 30
    ) {
        val dateEpochDay = date.toEpochDay()
        val alarm = WakeupAlarm(
            dateEpochDay = dateEpochDay,
            wakeupTimeMillis = wakeupTimeMillis,
            isSet = true,
            notes = label,
            leadTimeMinutes = leadTimeMinutes
        )
        wakeupAlarmDao.upsert(alarm)
        alarmScheduler.scheduleWakeupAlarm(
            timeMillis = wakeupTimeMillis,
            label = label,
            alarmId = dateEpochDay.toInt()
        )
    }

    fun getAlarmsFlow(): Flow<List<WakeupAlarm>> = wakeupAlarmDao.getAll()

    suspend fun cancelAlarm(date: LocalDate) {
        val dateEpochDay = date.toEpochDay()
        wakeupAlarmDao.setAlarmStatus(dateEpochDay, false)
        alarmScheduler.cancelAlarm(dateEpochDay.toInt())
    }

    suspend fun getNextAlarm(): WakeupAlarm? {
        return wakeupAlarmDao.getNextAlarm(System.currentTimeMillis())
    }

    suspend fun scheduleNextDayAlarm(
        account: Account,
        calendarRepository: CalendarRepository,
        leadTimeMinutes: Int = 30
    ) {
        val today = LocalDate.now(ZoneId.systemDefault())
        val tomorrow = today.plusDays(1)
        val now = System.currentTimeMillis()

        try {
            // Schedule wakeup alarm for tomorrow's first event
            val tomorrowEvents = calendarRepository.getEventsForDay(account, tomorrow)
            val firstTomorrow = tomorrowEvents.minByOrNull { it.startTime }
            if (firstTomorrow != null) {
                val wakeupTime = firstTomorrow.startTime - (leadTimeMinutes * 60_000L)
                if (wakeupTime > now) {
                    scheduleWakeupAlarm(
                        date = tomorrow,
                        wakeupTimeMillis = wakeupTime,
                        label = firstTomorrow.title,
                        leadTimeMinutes = leadTimeMinutes
                    )
                }
            }

            // Schedule activity alarms for TODAY (still-upcoming) and TOMORROW
            val allEvents = calendarRepository.getEventsForDay(account, today) +
                            calendarRepository.getEventsForDay(account, tomorrow)

            for (event in allEvents) {
                if (event.startTime <= now) continue   // already started — skip
                val alarmId = event.id.hashCode().and(0x7FFFFFFF)
                when (event.activityType) {
                    ActivityType.GYM -> alarmScheduler.scheduleGymAlarm(
                        timeMillis = event.startTime,
                        eventTitle = event.title,
                        eventId = event.id,
                        eventEndTime = event.endTime,
                        alarmId = alarmId
                    )
                    ActivityType.OUTDOOR -> alarmScheduler.scheduleOutdoorAlarm(
                        timeMillis = event.startTime,
                        eventTitle = event.title,
                        eventId = event.id,
                        eventEndTime = event.endTime,
                        alarmId = alarmId
                    )
                    ActivityType.NONE -> { /* no activity alarm */ }
                }
            }
        } catch (e: Exception) {
            // Silently fail — will retry on next sync
        }
    }

    suspend fun rescheduleAllAlarms() {
        val activeAlarms = wakeupAlarmDao.getActiveAlarms()
        val now = System.currentTimeMillis()
        for (alarm in activeAlarms) {
            if (alarm.wakeupTimeMillis > now) {
                alarmScheduler.scheduleWakeupAlarm(
                    timeMillis = alarm.wakeupTimeMillis,
                    label = alarm.notes ?: "Wake Up",
                    alarmId = alarm.dateEpochDay.toInt()
                )
            } else {
                wakeupAlarmDao.setAlarmStatus(alarm.dateEpochDay, false)
            }
        }
    }
}
