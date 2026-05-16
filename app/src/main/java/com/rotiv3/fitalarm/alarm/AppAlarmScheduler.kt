package com.rotiv3.fitalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleWakeupAlarm(timeMillis: Long, label: String, alarmId: Int) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_ALARM_TYPE, AlarmReceiver.TYPE_WAKEUP)
        }
        scheduleExact(
            intent,
            requestCode = alarmId,
            timeMillis = timeMillis
        )
    }

    fun scheduleGymAlarm(
        timeMillis: Long,
        eventTitle: String,
        eventId: String,
        eventEndTime: Long,
        alarmId: Int
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_GYM_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, eventTitle)
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_ALARM_TYPE, AlarmReceiver.TYPE_GYM)
            putExtra(AlarmReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(AlarmReceiver.EXTRA_EVENT_END_TIME, eventEndTime)
        }
        scheduleExact(
            intent,
            requestCode = alarmId + 10000,
            timeMillis = timeMillis
        )
    }

    fun scheduleOutdoorAlarm(
        timeMillis: Long,
        eventTitle: String,
        eventId: String,
        eventEndTime: Long,
        alarmId: Int
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_OUTDOOR_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, eventTitle)
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(AlarmReceiver.EXTRA_EVENT_END_TIME, eventEndTime)
        }
        scheduleExact(
            intent,
            requestCode = alarmId + 20000,
            timeMillis = timeMillis
        )
    }

    fun cancelAlarm(alarmId: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)

        listOf(alarmId, alarmId + 10000, alarmId + 20000).forEach { code ->
            PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let { alarmManager.cancel(it) }
        }
    }

    private fun scheduleExact(intent: Intent, requestCode: Int, timeMillis: Long) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
        }
    }
}
