package com.rotiv3.fitalarm.data.repository

import com.rotiv3.fitalarm.data.local.AchievementDao
import com.rotiv3.fitalarm.data.local.GymLocationDao
import com.rotiv3.fitalarm.data.local.GymSessionDao
import com.rotiv3.fitalarm.data.model.Achievement
import com.rotiv3.fitalarm.data.model.AchievementType
import com.rotiv3.fitalarm.data.model.CalendarEvent
import com.rotiv3.fitalarm.data.model.GymLocation
import com.rotiv3.fitalarm.data.model.GymSession
import com.rotiv3.fitalarm.data.model.SessionStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GymRepository @Inject constructor(
    private val gymLocationDao: GymLocationDao,
    private val achievementDao: AchievementDao,
    private val gymSessionDao: GymSessionDao
) {

    // ---- GymLocation CRUD ----

    suspend fun insertGymLocation(gymLocation: GymLocation): Long =
        gymLocationDao.insert(gymLocation)

    suspend fun updateGymLocation(gymLocation: GymLocation) =
        gymLocationDao.update(gymLocation)

    suspend fun deleteGymLocation(gymLocation: GymLocation) =
        gymLocationDao.delete(gymLocation)

    suspend fun deleteGymLocationById(id: Int) =
        gymLocationDao.deleteById(id)

    fun getAllGymLocations(): Flow<List<GymLocation>> = gymLocationDao.getAll()

    fun getActiveGymLocations(): Flow<List<GymLocation>> = gymLocationDao.getActiveLocations()

    suspend fun getActiveGymLocationsList(): List<GymLocation> =
        gymLocationDao.getActiveLocationsList()

    suspend fun setGymLocationActive(id: Int, isActive: Boolean) =
        gymLocationDao.setActive(id, isActive)

    // ---- GymSession status ----

    suspend fun upsertSession(session: GymSession) = gymSessionDao.upsert(session)

    suspend fun getSession(eventId: String): GymSession? = gymSessionDao.getSession(eventId)

    suspend fun updateSessionStatus(eventId: String, status: SessionStatus) =
        gymSessionDao.updateStatus(eventId, status)

    suspend fun markSessionCompleted(eventId: String) =
        gymSessionDao.markCompleted(eventId, SessionStatus.COMPLETED, System.currentTimeMillis())

    suspend fun getSessionsForDay(dayStart: Long, dayEnd: Long): List<GymSession> =
        gymSessionDao.getSessionsForDay(dayStart, dayEnd)

    // ---- Achievements ----

    fun getAchievementsFlow(): Flow<List<Achievement>> = achievementDao.getAll()

    suspend fun getAchievementCount(): Int = achievementDao.getCount()

    suspend fun markAchievementShared(id: Int) = achievementDao.markAsShared(id)

    suspend fun getCurrentStreak(): Int {
        return achievementDao.getLatestStreakCount() ?: 0
    }

    suspend fun recordCheckin(gymEvent: CalendarEvent): Achievement {
        val currentStreak = getCurrentStreak() + 1

        // Create streak-based achievement
        val achievement = Achievement.createForStreak(currentStreak, gymEvent.title)
            ?: run {
                // For streaks without milestones, check for early bird or consistent
                val hourOfDay = java.util.Calendar.getInstance().apply {
                    timeInMillis = gymEvent.startTime
                }.get(java.util.Calendar.HOUR_OF_DAY)

                if (hourOfDay < 7) {
                    Achievement(
                        type = AchievementType.EARLY_BIRD,
                        title = "Early Bird!",
                        description = "You completed a workout before 7 AM. Rise and shine!",
                        earnedAt = System.currentTimeMillis(),
                        gymEventTitle = gymEvent.title,
                        streakCount = currentStreak
                    )
                } else {
                    Achievement(
                        type = AchievementType.CONSISTENT,
                        title = "Staying Consistent!",
                        description = "Another great workout completed. Keep it up!",
                        earnedAt = System.currentTimeMillis(),
                        gymEventTitle = gymEvent.title,
                        streakCount = currentStreak
                    )
                }
            }

        achievementDao.insert(achievement)
        return achievement
    }
}
