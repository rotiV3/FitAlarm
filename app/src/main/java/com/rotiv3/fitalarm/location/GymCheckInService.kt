package com.rotiv3.fitalarm.location

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rotiv3.fitalarm.FitAlarmApp
import com.rotiv3.fitalarm.MainActivity
import com.rotiv3.fitalarm.R
import com.rotiv3.fitalarm.data.model.CalendarEvent
import com.rotiv3.fitalarm.data.repository.GymRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GymCheckInService : Service() {

    @Inject
    lateinit var gymRepository: GymRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var eventId: String = ""
    private var eventTitle: String = "Gym Session"
    private var eventEndTime: Long = 0L
    private var insideGymSince: Long? = null
    private var startTime: Long = 0L
    private var checkedIn = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            checkGymPresence(location)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startTime = System.currentTimeMillis()
        startForeground(NOTIFICATION_ID, buildNotification("Tracking gym location…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        eventId = intent?.getStringExtra(EXTRA_EVENT_ID) ?: "event_${System.currentTimeMillis()}"
        eventTitle = intent?.getStringExtra(EXTRA_EVENT_TITLE) ?: "Gym Session"
        eventEndTime = intent?.getLongExtra(EXTRA_EVENT_END_TIME, 0L) ?: 0L
        updateNotification("Watching for gym arrival: $eventTitle")

        // Persist tracking state so colour-coding updates immediately
        serviceScope.launch {
            gymRepository.upsertSession(
                com.rotiv3.fitalarm.data.model.GymSession(
                    eventId = eventId,
                    eventTitle = eventTitle,
                    startTime = startTime,
                    endTime = eventEndTime,
                    status = com.rotiv3.fitalarm.data.model.SessionStatus.UPCOMING
                )
            )
        }

        startLocationUpdates()
        scheduleLateNotifications()

        // Auto-stop after 2 hours to save battery
        serviceScope.launch {
            kotlinx.coroutines.delay(MAX_SERVICE_DURATION_MS)
            if (!checkedIn) Log.d(TAG, "Service timeout after 2 hours without check-in")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun scheduleLateNotifications() {
        serviceScope.launch {
            // After 5 min
            kotlinx.coroutines.delay(LATE_NOTIFY_5_MIN)
            if (!checkedIn && insideGymSince == null) sendLateNotification(5)

            // After 10 min total (5 more)
            kotlinx.coroutines.delay(LATE_NOTIFY_5_MIN)
            if (!checkedIn && insideGymSince == null) sendLateNotification(10)

            // After 30 min total (20 more)
            kotlinx.coroutines.delay(LATE_NOTIFY_20_MIN)
            if (!checkedIn && insideGymSince == null) sendLateNotification(30)
        }
    }

    private fun sendLateNotification(minutesLate: Int) {
        Log.d(TAG, "User late by $minutesLate min for: $eventTitle")
        val notification = NotificationCompat.Builder(this, FitAlarmApp.GYM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("You're $minutesLate min late for your session!")
            .setContentText("$eventTitle — are you on your way?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(LATE_NOTIFICATION_BASE_ID + minutesLate, notification)
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Location permission not granted")
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(LOCATION_INTERVAL_MS / 2)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    private fun checkGymPresence(location: Location) {
        if (checkedIn) return

        serviceScope.launch {
            val activeGyms = gymRepository.getActiveGymLocationsList()

            val isInsideGym = activeGyms.any { gym ->
                LocationUtils.isWithinRadius(
                    currentLat = location.latitude,
                    currentLon = location.longitude,
                    targetLat = gym.latitude,
                    targetLon = gym.longitude,
                    radiusMeters = gym.radiusMeters
                )
            }

            val now = System.currentTimeMillis()
            if (isInsideGym) {
                val since = insideGymSince
                if (since == null) {
                    insideGymSince = now
                    Log.d(TAG, "Entered gym zone at $now")
                    updateNotification("You're at the gym! Tracking session…")
                    gymRepository.updateSessionStatus(eventId, com.rotiv3.fitalarm.data.model.SessionStatus.AT_GYM)
                } else if (now - since >= CHECKIN_THRESHOLD_MS) {
                    performCheckIn()
                }
            } else {
                if (insideGymSince != null) {
                    Log.d(TAG, "Left gym zone")
                    insideGymSince = null
                    updateNotification("Left gym zone. Still watching…")
                    gymRepository.updateSessionStatus(eventId, com.rotiv3.fitalarm.data.model.SessionStatus.UPCOMING)
                }
            }
        }
    }

    private suspend fun performCheckIn() {
        if (checkedIn) return
        checkedIn = true

        Log.d(TAG, "Check-in confirmed for: $eventTitle")
        gymRepository.markSessionCompleted(eventId)

        val dummyEvent = CalendarEvent(
            id = "checkin_${System.currentTimeMillis()}",
            title = eventTitle,
            description = null,
            startTime = startTime,
            endTime = System.currentTimeMillis(),
            location = null,
            activityType = com.rotiv3.fitalarm.data.model.ActivityType.GYM,
            trainingPlan = null,
            colorId = null
        )

        val achievement = gymRepository.recordCheckin(dummyEvent)

        // Broadcast check-in complete
        val broadcastIntent = Intent(ACTION_CHECKIN_COMPLETE).apply {
            putExtra(EXTRA_EVENT_TITLE, eventTitle)
            putExtra(EXTRA_ACHIEVEMENT_TITLE, achievement.title)
        }
        sendBroadcast(broadcastIntent)

        // Show achievement notification
        showAchievementNotification(achievement.getTrophyEmoji(), achievement.title, achievement.description)

        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopSelf()
    }

    private fun showAchievementNotification(emoji: String, title: String, description: String) {
        val notification = NotificationCompat.Builder(this, FitAlarmApp.ACHIEVEMENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("$emoji $title")
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        notificationManager.notify(ACHIEVEMENT_NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FitAlarmApp.GYM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("FitAlarm - Gym Tracking")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "GymCheckInService"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_EVENT_TITLE = "extra_event_title"
        const val EXTRA_EVENT_END_TIME = "extra_event_end_time"
        const val EXTRA_ACHIEVEMENT_TITLE = "extra_achievement_title"
        const val ACTION_CHECKIN_COMPLETE = "com.rotiv3.fitalarm.ACTION_CHECKIN_COMPLETE"
        private const val NOTIFICATION_ID = 1001
        private const val ACHIEVEMENT_NOTIFICATION_ID = 1002
        private const val LATE_NOTIFICATION_BASE_ID = 2000
        private const val LOCATION_INTERVAL_MS = 30_000L
        private const val CHECKIN_THRESHOLD_MS = 5 * 60_000L
        private const val MAX_SERVICE_DURATION_MS = 2 * 60 * 60_000L
        private const val LATE_NOTIFY_5_MIN = 5 * 60_000L
        private const val LATE_NOTIFY_20_MIN = 20 * 60_000L
    }
}
