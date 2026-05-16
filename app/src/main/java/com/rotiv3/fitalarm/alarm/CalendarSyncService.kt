package com.rotiv3.fitalarm.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.rotiv3.fitalarm.FitAlarmApp
import com.rotiv3.fitalarm.MainActivity
import com.rotiv3.fitalarm.R
import com.rotiv3.fitalarm.data.repository.AlarmRepository
import com.rotiv3.fitalarm.data.repository.CalendarRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CalendarSyncService : Service() {

    @Inject
    lateinit var calendarRepository: CalendarRepository

    @Inject
    lateinit var alarmRepository: AlarmRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(this@CalendarSyncService)
                if (account?.account != null) {
                    alarmRepository.scheduleNextDayAlarm(account.account!!, calendarRepository)
                    Log.d(TAG, "Calendar sync completed successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Calendar sync failed", e)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FitAlarmApp.GYM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("FitAlarm")
            .setContentText("Syncing calendar events…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "CalendarSyncService"
        private const val NOTIFICATION_ID = 2001
    }
}
