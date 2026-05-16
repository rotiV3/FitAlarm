package com.rotiv3.fitalarm.di

import android.content.Context
import com.rotiv3.fitalarm.data.local.AppDatabase
import com.rotiv3.fitalarm.data.local.AchievementDao
import com.rotiv3.fitalarm.data.local.GymLocationDao
import com.rotiv3.fitalarm.data.local.GymSessionDao
import com.rotiv3.fitalarm.data.local.OutdoorAchievementDao
import com.rotiv3.fitalarm.data.local.OutdoorSessionDao
import com.rotiv3.fitalarm.data.local.WakeupAlarmDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideGymLocationDao(db: AppDatabase): GymLocationDao = db.gymLocationDao()

    @Provides
    @Singleton
    fun provideAchievementDao(db: AppDatabase): AchievementDao = db.achievementDao()

    @Provides
    @Singleton
    fun provideWakeupAlarmDao(db: AppDatabase): WakeupAlarmDao = db.wakeupAlarmDao()

    @Provides
    @Singleton
    fun provideGymSessionDao(db: AppDatabase): GymSessionDao = db.gymSessionDao()

    @Provides
    @Singleton
    fun provideOutdoorSessionDao(db: AppDatabase): OutdoorSessionDao = db.outdoorSessionDao()

    @Provides
    @Singleton
    fun provideOutdoorAchievementDao(db: AppDatabase): OutdoorAchievementDao = db.outdoorAchievementDao()
}
