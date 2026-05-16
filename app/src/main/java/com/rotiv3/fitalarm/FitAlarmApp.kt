package com.rotiv3.fitalarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FitAlarmApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Alarm Channel — high importance, full-screen
        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Wake-Up Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Wake-up alarm notifications"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        // Gym Channel — default importance
        val gymChannel = NotificationChannel(
            GYM_CHANNEL_ID,
            "Gym Check-In",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Gym check-in and session tracking notifications"
            enableVibration(true)
            setShowBadge(true)
        }

        // Outdoor Activity Channel — high importance so action buttons show on prompts
        val outdoorChannel = NotificationChannel(
            OUTDOOR_CHANNEL_ID,
            "Outdoor Activities",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Confirmation prompts for walks, runs, hikes, and bike rides"
            enableVibration(true)
            setShowBadge(true)
        }

        // Tracking Channel — silent, no banner; required by Android for GPS foreground service
        val trackingChannel = NotificationChannel(
            TRACKING_CHANNEL_ID,
            "GPS Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background GPS tracking — shown only in notification drawer"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }

        // Achievement Channel — default importance
        val achievementChannel = NotificationChannel(
            ACHIEVEMENT_CHANNEL_ID,
            "Achievements",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Fitness achievement notifications"
            setShowBadge(true)
        }

        notificationManager.createNotificationChannels(
            listOf(alarmChannel, gymChannel, outdoorChannel, trackingChannel, achievementChannel)
        )
    }

    companion object {
        const val ALARM_CHANNEL_ID = "alarm_channel"
        const val GYM_CHANNEL_ID = "gym_channel"
        const val OUTDOOR_CHANNEL_ID = "outdoor_channel"
        const val TRACKING_CHANNEL_ID = "tracking_channel"
        const val ACHIEVEMENT_CHANNEL_ID = "achievement_channel"
    }
}
