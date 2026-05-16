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
import com.rotiv3.fitalarm.R
import com.rotiv3.fitalarm.data.local.GymSessionDao
import com.rotiv3.fitalarm.data.local.OutdoorSessionDao
import com.rotiv3.fitalarm.data.model.GymSession
import com.rotiv3.fitalarm.data.model.OutdoorSession
import com.rotiv3.fitalarm.data.model.RoutePoint
import com.rotiv3.fitalarm.data.model.SessionStatus
import com.rotiv3.fitalarm.ui.outdoor.OutdoorTrackingActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackingState(
    val routePoints: List<RoutePoint> = emptyList(),
    val totalDistanceMeters: Float = 0f,
    val elapsedSeconds: Long = 0L,
    val isTracking: Boolean = true
)

@AndroidEntryPoint
class OutdoorTrackingService : Service() {

    @Inject lateinit var outdoorSessionDao: OutdoorSessionDao
    @Inject lateinit var gymSessionDao: GymSessionDao
    @Inject lateinit var achievementChecker: OutdoorAchievementChecker

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var eventId = ""
    private var eventTitle = ""
    private var subType = "OUTDOOR"
    private var eventEndTime = 0L
    private var trackingStartTime = System.currentTimeMillis()
    private val routePoints = mutableListOf<RoutePoint>()
    private var lastLocation: Location? = null
    private var totalDistance = 0f

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            onNewLocation(loc)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startForeground(NOTIFICATION_ID, buildSilentNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }

        eventId = intent?.getStringExtra(EXTRA_EVENT_ID) ?: ""
        eventTitle = intent?.getStringExtra(EXTRA_EVENT_TITLE) ?: "Activity"
        eventEndTime = intent?.getLongExtra(EXTRA_EVENT_END_TIME, 0L) ?: 0L
        subType = OutdoorSession.classifySubType(eventTitle)
        trackingStartTime = System.currentTimeMillis()

        _state.value = TrackingState()
        routePoints.clear()
        lastLocation = null
        totalDistance = 0f

        startLocationUpdates()
        startTimer()
        return START_NOT_STICKY
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Location permission missing, stopping service")
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_INTERVAL_MS / 2)
            .build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun startTimer() {
        serviceScope.launch {
            while (_state.value.isTracking) {
                delay(1000L)
                _state.value = _state.value.copy(
                    elapsedSeconds = _state.value.elapsedSeconds + 1
                )
            }
        }
    }

    private fun onNewLocation(location: Location) {
        val point = RoutePoint(location.latitude, location.longitude, System.currentTimeMillis())
        routePoints.add(point)

        lastLocation?.let { prev ->
            val segment = FloatArray(1)
            Location.distanceBetween(prev.latitude, prev.longitude, location.latitude, location.longitude, segment)
            if (segment[0] > 3f) totalDistance += segment[0]
        }
        lastLocation = location

        _state.value = _state.value.copy(
            routePoints = routePoints.toList(),
            totalDistanceMeters = totalDistance
        )
    }

    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _state.value = _state.value.copy(isTracking = false)

        val endTime = System.currentTimeMillis()
        val durationSeconds = (endTime - trackingStartTime) / 1000L
        val snapshot = routePoints.toList()
        val distanceSnapshot = totalDistance
        val savedEventId = eventId
        val savedTitle = eventTitle
        val savedSubType = subType
        val savedEventEndTime = if (eventEndTime > 0L) eventEndTime else endTime

        serviceScope.launch {
            try {
                val session = OutdoorSession(
                    eventId = savedEventId,
                    eventTitle = savedTitle,
                    startTime = trackingStartTime,
                    endTime = endTime,
                    routeJson = routePointsToJson(snapshot),
                    totalDistanceMeters = distanceSnapshot,
                    durationSeconds = durationSeconds,
                    subType = savedSubType
                )
                outdoorSessionDao.upsert(session)

                // Upsert (not just update) so we always have a GymSession record,
                // even if the alarm never fired and none was created beforehand.
                gymSessionDao.upsert(
                    GymSession(
                        eventId = savedEventId,
                        eventTitle = savedTitle,
                        startTime = trackingStartTime,
                        endTime = savedEventEndTime,
                        status = SessionStatus.COMPLETED,
                        completedAt = endTime
                    )
                )

                achievementChecker.checkAndNotify(session)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun routePointsToJson(points: List<RoutePoint>): String {
        val arr = org.json.JSONArray()
        points.forEach { p ->
            arr.put(org.json.JSONObject().put("lat", p.lat).put("lng", p.lng).put("t", p.timestampMs))
        }
        return arr.toString()
    }

    private fun buildSilentNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, OutdoorTrackingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(OutdoorTrackingActivity.EXTRA_EVENT_ID, eventId)
                putExtra(OutdoorTrackingActivity.EXTRA_EVENT_TITLE, eventTitle)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, FitAlarmApp.TRACKING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(eventTitle)
            .setContentText("GPS tracking active — tap to open")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "OutdoorTrackingService"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_EVENT_TITLE = "extra_event_title"
        const val EXTRA_EVENT_END_TIME = "extra_event_end_time"
        const val ACTION_STOP = "com.rotiv3.fitalarm.STOP_OUTDOOR_TRACKING"
        private const val NOTIFICATION_ID = 1003
        private const val LOCATION_INTERVAL_MS = 5_000L

        private val _state = MutableStateFlow(TrackingState())
        val state: StateFlow<TrackingState> = _state.asStateFlow()

        fun formatDistance(meters: Float): String =
            if (meters < 1000) "${meters.toInt()} m"
            else "${"%.2f".format(meters / 1000)} km"

        fun formatTime(seconds: Long): String {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
            else "$m:${s.toString().padStart(2, '0')}"
        }

        fun calculatePace(distanceMeters: Float, seconds: Long): String {
            if (distanceMeters < 50f) return "--:--"
            val secPerKm = (seconds * 1000f / distanceMeters).toLong()
            val min = secPerKm / 60
            val sec = secPerKm % 60
            return "$min:${sec.toString().padStart(2, '0')}"
        }
    }
}
