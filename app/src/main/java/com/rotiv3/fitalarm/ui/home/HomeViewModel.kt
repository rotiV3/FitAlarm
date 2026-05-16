package com.rotiv3.fitalarm.ui.home

import android.accounts.Account
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.rotiv3.fitalarm.data.model.SessionStatus
import com.rotiv3.fitalarm.data.repository.AlarmRepository
import com.rotiv3.fitalarm.data.repository.CalendarRepository
import com.rotiv3.fitalarm.data.repository.GymRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val alarmRepository: AlarmRepository,
    private val gymRepository: GymRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _authEvent = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val authEvent: SharedFlow<Intent> = _authEvent.asSharedFlow()

    private var showOnlyWorkouts = false
    private var lastAccount: Account? = null
    private var lastUserName: String = ""

    fun setShowOnlyWorkouts(enabled: Boolean) {
        showOnlyWorkouts = enabled
        lastAccount?.let { loadTodayData(it, lastUserName) }
    }

    fun loadTodayData(account: Account, userName: String, leadTimeMinutes: Int = 30) {
        lastAccount = account
        lastUserName = userName
        _uiState.value = HomeUiState.Loading

        viewModelScope.launch {
            try {
                val today = LocalDate.now(ZoneId.systemDefault())
                val rawEvents = calendarRepository.getEventsForDay(account, today)
                val allEvents = attachSessionStatus(rawEvents)
                val events = if (showOnlyWorkouts) allEvents.filter { it.isGymEvent } else allEvents
                val gymEvents = allEvents.filter { it.isGymEvent }
                val nextAlarm = alarmRepository.getNextAlarm()

                val firstEventStart = allEvents.minByOrNull { it.startTime }?.startTime
                val suggestedWakeup = firstEventStart?.let { it - (leadTimeMinutes * 60_000L) }
                    ?.takeIf { it > System.currentTimeMillis() }

                _uiState.value = HomeUiState.Success(
                    events = events,
                    gymEvents = gymEvents,
                    nextAlarm = nextAlarm,
                    userName = userName,
                    suggestedWakeupTime = suggestedWakeup
                )
            } catch (e: UserRecoverableAuthIOException) {
                _authEvent.tryEmit(e.intent)
                _uiState.value = HomeUiState.Error("Calendar permission needed. Tap sync after granting access.")
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error("Failed to load events: ${e.message}")
            }
        }
    }

    private suspend fun attachSessionStatus(
        events: List<com.rotiv3.fitalarm.data.model.CalendarEvent>
    ): List<com.rotiv3.fitalarm.data.model.CalendarEvent> {
        if (events.isEmpty()) return events
        val now = System.currentTimeMillis()
        val dayStart = events.minOf { it.startTime }
        val dayEnd = events.maxOf { it.endTime }
        val sessions = gymRepository.getSessionsForDay(dayStart, dayEnd).associateBy { it.eventId }
        return events.map { event ->
            if (!event.isGymEvent) return@map event
            val db = sessions[event.id]
            val status = when {
                db?.status == SessionStatus.COMPLETED -> SessionStatus.COMPLETED
                db?.status == SessionStatus.AT_GYM -> SessionStatus.AT_GYM
                event.endTime < now && db?.status != SessionStatus.COMPLETED -> SessionStatus.MISSED
                else -> SessionStatus.UPCOMING
            }
            event.copy(sessionStatus = status)
        }
    }

    fun scheduleWakeupAlarm(account: Account, wakeupTimeMillis: Long, label: String, leadTimeMinutes: Int = 30) {
        viewModelScope.launch {
            val today = LocalDate.now(ZoneId.systemDefault())
            alarmRepository.scheduleWakeupAlarm(
                date = today,
                wakeupTimeMillis = wakeupTimeMillis,
                label = label,
                leadTimeMinutes = leadTimeMinutes
            )
            // Refresh to show updated alarm
            loadTodayData(account, ((_uiState.value as? HomeUiState.Success)?.userName ?: ""), leadTimeMinutes)
        }
    }
}
