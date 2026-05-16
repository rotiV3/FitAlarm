package com.rotiv3.fitalarm.data.repository

import android.accounts.Account
import com.rotiv3.fitalarm.data.model.CalendarEvent
import com.rotiv3.fitalarm.data.remote.CalendarApiClient
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepository @Inject constructor(
    private val calendarApiClient: CalendarApiClient
) {

    suspend fun getEventsForDay(account: Account, date: LocalDate): List<CalendarEvent> {
        return calendarApiClient.getEventsForDay(account, date)
    }

    suspend fun getEventsForWeek(account: Account, startDate: LocalDate): List<CalendarEvent> {
        return calendarApiClient.getEventsForWeek(account, startDate)
    }

    suspend fun getEventsForMonth(account: Account, yearMonth: YearMonth): List<CalendarEvent> {
        return calendarApiClient.getEventsForMonth(account, yearMonth)
    }

    suspend fun getEventsForDateRange(
        account: Account,
        start: LocalDate,
        end: LocalDate
    ): List<CalendarEvent> {
        return calendarApiClient.getEventsForDateRange(account, start, end)
    }

    fun getGymKeywords(): List<String> = listOf(
        "gym", "workout", "training", "exercise", "lift", "crossfit",
        "yoga", "pilates", "run", "running", "cycling", "swim", "swimming",
        "hiit", "cardio", "fitness", "sport", "sports", "weightlifting",
        "powerlifting", "strength", "aerobics", "zumba", "bootcamp",
        "martial arts", "boxing", "kickboxing", "climbing", "rowing"
    )
}
