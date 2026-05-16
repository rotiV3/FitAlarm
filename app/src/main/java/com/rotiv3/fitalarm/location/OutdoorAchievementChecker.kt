package com.rotiv3.fitalarm.location

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rotiv3.fitalarm.FitAlarmApp
import com.rotiv3.fitalarm.MainActivity
import com.rotiv3.fitalarm.R
import com.rotiv3.fitalarm.data.local.OutdoorAchievementDao
import com.rotiv3.fitalarm.data.local.OutdoorSessionDao
import com.rotiv3.fitalarm.data.model.OutdoorAchievement
import com.rotiv3.fitalarm.data.model.OutdoorAchievements
import com.rotiv3.fitalarm.data.model.OutdoorSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutdoorAchievementChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val achievementDao: OutdoorAchievementDao,
    private val sessionDao: OutdoorSessionDao
) {

    suspend fun checkAndNotify(session: OutdoorSession) {
        // Seed all achievement definitions on first use
        achievementDao.seedIfAbsent(OutdoorAchievements.buildAll())

        val subType = session.subType
        val sessions = sessionDao.getSessionsBySubType(subType)
        val count = sessions.size
        val totalMeters = sessions.sumOf { it.totalDistanceMeters.toDouble() }.toFloat()
        val maxSingle = sessions.maxOfOrNull { it.totalDistanceMeters } ?: 0f
        val now = System.currentTimeMillis()

        val newlyUnlocked = mutableListOf<OutdoorAchievement>()

        suspend fun tryUnlock(id: String) {
            val ach = achievementDao.getById(id) ?: return
            if (!ach.isUnlocked) {
                achievementDao.upsert(ach.copy(isUnlocked = true, unlockedAt = now))
                newlyUnlocked.add(ach)
            }
        }

        // Count milestones
        if (count >= 1)  tryUnlock("${subType}_COUNT_1")
        if (count >= 3)  tryUnlock("${subType}_COUNT_3")
        if (count >= 5)  tryUnlock("${subType}_COUNT_5")
        if (count >= 10) tryUnlock("${subType}_COUNT_10")

        // Single-session distance
        if (maxSingle >= 1_000f)  tryUnlock("${subType}_SINGLE_1000")
        if (maxSingle >= 2_500f)  tryUnlock("${subType}_SINGLE_2500")
        if (maxSingle >= 5_000f)  tryUnlock("${subType}_SINGLE_5000")

        // Total distance
        if (totalMeters >= 5_000f)  tryUnlock("${subType}_TOTAL_5000")
        if (totalMeters >= 10_000f) tryUnlock("${subType}_TOTAL_10000")
        if (totalMeters >= 50_000f) tryUnlock("${subType}_TOTAL_50000")

        newlyUnlocked.forEach { notify(it) }
    }

    private fun notify(ach: OutdoorAchievement) {
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, FitAlarmApp.ACHIEVEMENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("${ach.emoji} Achievement Unlocked!")
            .setContentText(ach.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${ach.title}\n${ach.description}"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(ACHIEVEMENT_NOTIF_BASE + ach.id.hashCode().and(0x7FFF), notification)
    }

    companion object {
        private const val ACHIEVEMENT_NOTIF_BASE = 9000
    }
}
