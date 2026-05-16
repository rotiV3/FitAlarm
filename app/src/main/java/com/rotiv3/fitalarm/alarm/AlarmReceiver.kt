package com.rotiv3.fitalarm.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rotiv3.fitalarm.FitAlarmApp
import com.rotiv3.fitalarm.R
import com.rotiv3.fitalarm.data.local.AppDatabase
import com.rotiv3.fitalarm.data.model.GymSession
import com.rotiv3.fitalarm.data.model.SessionStatus
import com.rotiv3.fitalarm.location.GymCheckInService
import com.rotiv3.fitalarm.location.OutdoorTrackingService
import com.rotiv3.fitalarm.ui.outdoor.OutdoorTrackingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: action=${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.LOCKED_BOOT_COMPLETED" -> handleBootCompleted(context)

            ACTION_FIRE_ALARM -> {
                val label = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "Wake Up"
                val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, 0)
                handleFireAlarm(context, label, alarmId)
            }

            ACTION_GYM_ALARM -> {
                val eventTitle = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "Gym Session"
                val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: ""
                val eventEndTime = intent.getLongExtra(EXTRA_EVENT_END_TIME, 0L)
                val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, 0)
                handleGymAlarm(context, eventTitle, eventId, eventEndTime, alarmId)
            }

            // Outdoor: fires at event start — schedules prompt and followup
            ACTION_OUTDOOR_ALARM -> {
                val eventTitle = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "Activity"
                val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: ""
                val eventEndTime = intent.getLongExtra(EXTRA_EVENT_END_TIME, 0L)
                val startTime = intent.getLongExtra(EXTRA_EVENT_START_TIME, System.currentTimeMillis())
                handleOutdoorAlarm(context, eventTitle, eventId, startTime, eventEndTime)
            }

            // Outdoor: fires end+5min — shows followup if still unconfirmed
            ACTION_OUTDOOR_FOLLOWUP -> {
                val eventTitle = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "Activity"
                val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: ""
                handleOutdoorFollowup(context, eventTitle, eventId)
            }

            // User tapped "Yes!" on either notification
            ACTION_OUTDOOR_CONFIRM -> {
                val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: ""
                val eventTitle = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: ""
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, outdoorNotifId(eventId))
                val eventEndTime = intent.getLongExtra(EXTRA_EVENT_END_TIME, 0L)
                handleOutdoorConfirm(context, eventId, eventTitle, eventEndTime, notifId)
            }

            // User tapped "Not today" on either notification
            ACTION_OUTDOOR_SKIP -> {
                val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: ""
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, outdoorNotifId(eventId))
                handleOutdoorSkip(context, eventId, notifId)
            }
        }
    }

    // ─── Wakeup alarm ───────────────────────────────────────────────────────

    private fun handleFireAlarm(context: Context, label: String, alarmId: Int) {
        Log.d(TAG, "Firing wake-up alarm: $label")
        val alarmActivityIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_NO_HISTORY
            putExtra(AlarmActivity.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmActivity.EXTRA_ALARM_ID, alarmId)
        }
        context.startActivity(alarmActivityIntent)
        showAlarmNotification(context, label, alarmId, alarmActivityIntent)
    }

    // ─── Gym GPS alarm ──────────────────────────────────────────────────────

    private fun handleGymAlarm(
        context: Context,
        eventTitle: String,
        eventId: String,
        eventEndTime: Long,
        alarmId: Int
    ) {
        Log.d(TAG, "Gym alarm: $eventTitle")
        val serviceIntent = Intent(context, GymCheckInService::class.java).apply {
            putExtra(GymCheckInService.EXTRA_EVENT_ID, eventId)
            putExtra(GymCheckInService.EXTRA_EVENT_TITLE, eventTitle)
            putExtra(GymCheckInService.EXTRA_EVENT_END_TIME, eventEndTime)
        }
        context.startForegroundService(serviceIntent)
        showGymNotification(context, eventTitle)
    }

    // ─── Outdoor alarm ──────────────────────────────────────────────────────

    private fun handleOutdoorAlarm(
        context: Context,
        eventTitle: String,
        eventId: String,
        startTime: Long,
        eventEndTime: Long
    ) {
        Log.d(TAG, "Outdoor alarm: $eventTitle — showing start notification")

        val notifId = outdoorNotifId(eventId)

        // "Start Tracking" action — fires confirm which starts the service + opens the map screen
        val startIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_OUTDOOR_CONFIRM
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_ALARM_LABEL, eventTitle)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
            putExtra(EXTRA_EVENT_END_TIME, eventEndTime)
        }
        val startPi = PendingIntent.getBroadcast(
            context, outdoorConfirmCode(eventId), startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, FitAlarmApp.OUTDOOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("🌿 $eventTitle")
            .setContentText("Your activity is starting! Tap to begin tracking your route.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Start Tracking ▶", startPi)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(notifId, notification)

        // Fallback followup at end + 5 min — asks if they completed it without tracking
        scheduleExactBroadcast(
            context, ACTION_OUTDOOR_FOLLOWUP,
            eventEndTime + 5 * 60_000L,
            outdoorFollowupCode(eventId),
            eventTitle, eventId, eventEndTime
        )
    }

    private fun handleOutdoorFollowup(context: Context, eventTitle: String, eventId: String) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val session = db.gymSessionDao().getSession(eventId)
                if (session == null || session.status == SessionStatus.UPCOMING) {
                    val notifId = outdoorNotifId(eventId) + 1
                    showOutdoorConfirmNotification(
                        context, eventTitle, eventId,
                        notifId = notifId,
                        confirmAction = ACTION_OUTDOOR_CONFIRM,
                        confirmRequestCode = outdoorFollowupConfirmCode(eventId),
                        skipAction = ACTION_OUTDOOR_SKIP,
                        skipRequestCode = outdoorFollowupSkipCode(eventId),
                        title = "Did you complete $eventTitle?",
                        body = "Your activity ended a few minutes ago. Did you do it?"
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleOutdoorConfirm(context: Context, eventId: String, eventTitle: String, eventEndTime: Long, notifId: Int) {
        Log.d(TAG, "Outdoor confirmed: $eventId — starting GPS route tracking")
        cancelOutdoorFollowup(context, eventId)
        context.getSystemService(NotificationManager::class.java).cancel(notifId)

        // Upsert session as AT_GYM — creates it if the alarm never fired (e.g. manual start)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getInstance(context).gymSessionDao().upsert(
                    GymSession(
                        eventId = eventId,
                        eventTitle = eventTitle,
                        startTime = System.currentTimeMillis(),
                        endTime = eventEndTime,
                        status = SessionStatus.AT_GYM
                    )
                )
            } finally {
                pendingResult.finish()
            }
        }

        // Start GPS route recording service
        context.startForegroundService(
            Intent(context, OutdoorTrackingService::class.java).apply {
                putExtra(OutdoorTrackingService.EXTRA_EVENT_ID, eventId)
                putExtra(OutdoorTrackingService.EXTRA_EVENT_TITLE, eventTitle)
                putExtra(OutdoorTrackingService.EXTRA_EVENT_END_TIME, eventEndTime)
            }
        )

        // Open tracking screen
        context.startActivity(
            Intent(context, OutdoorTrackingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(OutdoorTrackingActivity.EXTRA_EVENT_ID, eventId)
                putExtra(OutdoorTrackingActivity.EXTRA_EVENT_TITLE, eventTitle)
            }
        )
    }

    private fun handleOutdoorSkip(context: Context, eventId: String, notifId: Int) {
        Log.d(TAG, "Outdoor skipped: $eventId")
        cancelOutdoorFollowup(context, eventId)
        context.getSystemService(NotificationManager::class.java).cancel(notifId)

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            db.gymSessionDao().updateStatus(eventId, SessionStatus.MISSED)
        }
    }

    private fun cancelOutdoorFollowup(context: Context, eventId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        PendingIntent.getBroadcast(
            context, outdoorFollowupCode(eventId), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.let { am.cancel(it) }
    }

    private fun showOutdoorConfirmNotification(
        context: Context,
        eventTitle: String,
        eventId: String,
        notifId: Int,
        confirmAction: String,
        confirmRequestCode: Int,
        skipAction: String,
        skipRequestCode: Int,
        title: String,
        body: String
    ) {
        val confirmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = confirmAction
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_ALARM_LABEL, eventTitle)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val confirmPi = PendingIntent.getBroadcast(
            context, confirmRequestCode, confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val skipIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = skipAction
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_ALARM_LABEL, eventTitle)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val skipPi = PendingIntent.getBroadcast(
            context, skipRequestCode, skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, FitAlarmApp.OUTDOOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Yes, I'm doing it! ✓", confirmPi)
            .addAction(0, "Not today ✗", skipPi)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(notifId, notification)
    }

    // ─── Scheduling helper ──────────────────────────────────────────────────

    private fun scheduleExactBroadcast(
        context: Context,
        action: String,
        timeMillis: Long,
        requestCode: Int,
        eventTitle: String,
        eventId: String,
        eventEndTime: Long
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_ALARM_LABEL, eventTitle)
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_EVENT_END_TIME, eventEndTime)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pi)
        }
    }

    // ─── Notifications ──────────────────────────────────────────────────────

    private fun showAlarmNotification(
        context: Context,
        label: String,
        alarmId: Int,
        fullScreenIntent: Intent
    ) {
        val fullScreenPi = PendingIntent.getActivity(
            context, alarmId, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_DISMISS_ALARM
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val dismissPi = PendingIntent.getBroadcast(
            context, alarmId + 20000, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, FitAlarmApp.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("FitAlarm")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPi, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .addAction(R.drawable.ic_alarm, "Dismiss", dismissPi)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(alarmId, notification)
    }

    private fun showGymNotification(context: Context, eventTitle: String) {
        val notification = NotificationCompat.Builder(context, FitAlarmApp.GYM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Time for your workout!")
            .setContentText(eventTitle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(GYM_NOTIFICATION_ID, notification)
    }

    // ─── Boot completed ─────────────────────────────────────────────────────

    private fun handleBootCompleted(context: Context) {
        Log.d(TAG, "Device rebooted, rescheduling alarms")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val now = System.currentTimeMillis()
                val activeAlarms = db.wakeupAlarmDao().getActiveAlarms()
                val scheduler = AppAlarmScheduler(context)

                for (alarm in activeAlarms) {
                    if (alarm.wakeupTimeMillis > now) {
                        scheduler.scheduleWakeupAlarm(
                            timeMillis = alarm.wakeupTimeMillis,
                            label = alarm.notes ?: "Wake Up",
                            alarmId = alarm.dateEpochDay.toInt()
                        )
                    } else {
                        db.wakeupAlarmDao().setAlarmStatus(alarm.dateEpochDay, false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling alarms after boot", e)
            }
        }
    }

    // ─── Request code helpers (per-event, collision-resistant) ───────────────

    private fun eventBase(eventId: String) = eventId.hashCode().and(0x7FFFFFFF) % 100000

    private fun outdoorPromptCode(eventId: String)         = eventBase(eventId) + 300000
    private fun outdoorFollowupCode(eventId: String)       = eventBase(eventId) + 400000
    private fun outdoorConfirmCode(eventId: String)        = eventBase(eventId) + 500000
    private fun outdoorSkipCode(eventId: String)           = eventBase(eventId) + 600000
    private fun outdoorFollowupConfirmCode(eventId: String)= eventBase(eventId) + 700000
    private fun outdoorFollowupSkipCode(eventId: String)   = eventBase(eventId) + 800000
    private fun outdoorNotifId(eventId: String)            = eventBase(eventId) + 9000

    companion object {
        private const val TAG = "AlarmReceiver"
        const val ACTION_FIRE_ALARM      = "com.rotiv3.fitalarm.ACTION_FIRE_ALARM"
        const val ACTION_GYM_ALARM       = "com.rotiv3.fitalarm.ACTION_GYM_ALARM"
        const val ACTION_DISMISS_ALARM   = "com.rotiv3.fitalarm.ACTION_DISMISS_ALARM"
        const val ACTION_OUTDOOR_ALARM   = "com.rotiv3.fitalarm.ACTION_OUTDOOR_ALARM"
        const val ACTION_OUTDOOR_PROMPT  = "com.rotiv3.fitalarm.ACTION_OUTDOOR_PROMPT"
        const val ACTION_OUTDOOR_FOLLOWUP= "com.rotiv3.fitalarm.ACTION_OUTDOOR_FOLLOWUP"
        const val ACTION_OUTDOOR_CONFIRM = "com.rotiv3.fitalarm.ACTION_OUTDOOR_CONFIRM"
        const val ACTION_OUTDOOR_SKIP    = "com.rotiv3.fitalarm.ACTION_OUTDOOR_SKIP"

        const val EXTRA_ALARM_LABEL      = "extra_alarm_label"
        const val EXTRA_ALARM_ID         = "extra_alarm_id"
        const val EXTRA_ALARM_TYPE       = "extra_alarm_type"
        const val EXTRA_EVENT_ID         = "extra_event_id"
        const val EXTRA_EVENT_START_TIME = "extra_event_start_time"
        const val EXTRA_EVENT_END_TIME   = "extra_event_end_time"
        const val EXTRA_NOTIFICATION_ID  = "extra_notification_id"

        const val TYPE_WAKEUP = "wakeup"
        const val TYPE_GYM    = "gym"
        const val TYPE_OUTDOOR = "outdoor"

        private const val GYM_NOTIFICATION_ID = 9999
    }
}
