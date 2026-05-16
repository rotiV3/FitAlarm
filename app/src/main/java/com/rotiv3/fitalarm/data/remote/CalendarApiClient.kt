package com.rotiv3.fitalarm.data.remote

import android.accounts.Account
import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.rotiv3.fitalarm.data.model.CalendarEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import com.google.api.client.util.DateTime as GoogleDateTime

@Singleton
class CalendarApiClient @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private fun buildCalendarService(account: Account): Calendar {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(CalendarScopes.CALENDAR_READONLY)
        ).apply {
            selectedAccount = account
        }

        return Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("FitAlarm")
            .build()
    }

    suspend fun getEventsForDay(account: Account, date: LocalDate): List<CalendarEvent> {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault())
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault())
        return getEventsForDateRange(account, startOfDay, endOfDay)
    }

    suspend fun getEventsForWeek(account: Account, startDate: LocalDate): List<CalendarEvent> {
        val startOfWeek = startDate.atStartOfDay(ZoneId.systemDefault())
        val endOfWeek = startDate.plusDays(7).atStartOfDay(ZoneId.systemDefault())
        return getEventsForDateRange(account, startOfWeek, endOfWeek)
    }

    suspend fun getEventsForMonth(account: Account, yearMonth: YearMonth): List<CalendarEvent> {
        val startOfMonth = yearMonth.atDay(1).atStartOfDay(ZoneId.systemDefault())
        val endOfMonth = yearMonth.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault())
        return getEventsForDateRange(account, startOfMonth, endOfMonth)
    }

    suspend fun getEventsForDateRange(account: Account, start: LocalDate, end: LocalDate): List<CalendarEvent> {
        val startZdt = start.atStartOfDay(ZoneId.systemDefault())
        val endZdt = end.atStartOfDay(ZoneId.systemDefault())
        return getEventsForDateRange(account, startZdt, endZdt)
    }

    private suspend fun getEventsForDateRange(
        account: Account,
        start: ZonedDateTime,
        end: ZonedDateTime
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val service = buildCalendarService(account)
        val timeMin = GoogleDateTime(start.toInstant().toEpochMilli())
        val timeMax = GoogleDateTime(end.toInstant().toEpochMilli())

        val events = service.events().list("primary")
            .setTimeMin(timeMin)
            .setTimeMax(timeMax)
            .setOrderBy("startTime")
            .setSingleEvents(true)
            .setMaxResults(250)
            .execute()

        events.items?.mapNotNull { event ->
            try {
                val startMillis = event.start?.dateTime?.value
                    ?: event.start?.date?.value
                    ?: return@mapNotNull null
                val endMillis = event.end?.dateTime?.value
                    ?: event.end?.date?.value
                    ?: (startMillis + 3600_000L)

                CalendarEvent.fromApiEvent(
                    id = event.id ?: "",
                    title = event.summary ?: "Untitled",
                    description = event.description,
                    startTime = startMillis,
                    endTime = endMillis,
                    location = event.location,
                    colorId = event.colorId
                )
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
    }
}
