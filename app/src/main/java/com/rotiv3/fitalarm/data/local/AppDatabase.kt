package com.rotiv3.fitalarm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.rotiv3.fitalarm.data.model.Achievement
import com.rotiv3.fitalarm.data.model.AchievementType
import com.rotiv3.fitalarm.data.model.GymLocation
import com.rotiv3.fitalarm.data.model.GymSession
import com.rotiv3.fitalarm.data.model.OutdoorAchievement
import com.rotiv3.fitalarm.data.model.OutdoorSession
import com.rotiv3.fitalarm.data.model.RoutePoint
import com.rotiv3.fitalarm.data.model.SessionStatus
import com.rotiv3.fitalarm.data.model.WakeupAlarm
import org.json.JSONArray
import org.json.JSONObject

class Converters {
    @TypeConverter
    fun fromAchievementType(value: AchievementType): String = value.name

    @TypeConverter
    fun toAchievementType(value: String): AchievementType = AchievementType.valueOf(value)

    @TypeConverter
    fun fromSessionStatus(value: SessionStatus): String = value.name

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus = SessionStatus.valueOf(value)

    @TypeConverter
    fun routeToJson(points: List<RoutePoint>): String {
        val arr = JSONArray()
        points.forEach { p ->
            arr.put(JSONObject().put("lat", p.lat).put("lng", p.lng).put("t", p.timestampMs))
        }
        return arr.toString()
    }

    @TypeConverter
    fun routeFromJson(json: String): List<RoutePoint> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                RoutePoint(obj.getDouble("lat"), obj.getDouble("lng"), obj.getLong("t"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

@Database(
    entities = [
        GymLocation::class,
        Achievement::class,
        WakeupAlarm::class,
        GymSession::class,
        OutdoorSession::class,
        OutdoorAchievement::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun gymLocationDao(): GymLocationDao
    abstract fun achievementDao(): AchievementDao
    abstract fun wakeupAlarmDao(): WakeupAlarmDao
    abstract fun gymSessionDao(): GymSessionDao
    abstract fun outdoorSessionDao(): OutdoorSessionDao
    abstract fun outdoorAchievementDao(): OutdoorAchievementDao

    companion object {
        private const val DATABASE_NAME = "fitalarm_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
