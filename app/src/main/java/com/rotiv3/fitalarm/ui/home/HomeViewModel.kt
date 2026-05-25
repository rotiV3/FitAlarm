package com.rotiv3.fitalarm.ui.home

import android.accounts.Account
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.rotiv3.fitalarm.data.local.AppEventDao
import com.rotiv3.fitalarm.data.model.CalendarEvent
import com.rotiv3.fitalarm.data.model.SessionStatus
import com.rotiv3.fitalarm.data.repository.CalendarRepository
import com.rotiv3.fitalarm.data.repository.GymRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val gymRepository: GymRepository,
    private val appEventDao: AppEventDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _authEvent = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val authEvent: SharedFlow<Intent> = _authEvent.asSharedFlow()

    private var showOnlyWorkouts = false

    fun setShowOnlyWorkouts(enabled: Boolean) {
        showOnlyWorkouts = enabled
        // Re-apply filter on current state without a network call
        val current = _uiState.value as? HomeUiState.Success ?: return
        val filtered = if (enabled) current.events.filter { it.isGymEvent } else current.events
        _uiState.value = current.copy(events = filtered)
    }

    /**
     * Load today's activities.
     * - [account] may be null for guests — local AppEvents are always shown.
     * - Google Calendar events are merged on top when signed in.
     */
    fun loadTodayData(account: Account?, userName: String) {
        _uiState.value = HomeUiState.Loading

        viewModelScope.launch {
            try {
                val today = LocalDate.now(ZoneId.systemDefault())
                val zone = ZoneId.systemDefault()
                val dayStart = today.atStartOfDay(zone).toEpochSecond() * 1000L
                val dayEnd = today.plusDays(1).atStartOfDay(zone).toEpochSecond() * 1000L

                // Always load local app events
                val localEvents: List<CalendarEvent> = appEventDao
                    .getEventsForDay(dayStart, dayEnd)
                    .first()
                    .map { it.toCalendarEvent() }

                // Optionally merge Google Calendar events
                val remoteEvents: List<CalendarEvent> = if (account != null) {
                    try {
                        calendarRepository.getEventsForDay(account, today)
                    } catch (e: UserRecoverableAuthIOException) {
                        _authEvent.tryEmit(e.intent)
                        emptyList()
                    }
                } else emptyList()

                val merged = (localEvents + remoteEvents)
                    .sortedBy { it.startTime }
                val withStatus = attachSessionStatus(merged)
                val allEvents = if (showOnlyWorkouts) withStatus.filter { it.isGymEvent } else withStatus
                val gymEvents = withStatus.filter { it.isGymEvent }

                _uiState.value = HomeUiState.Success(
                    events = allEvents,
                    gymEvents = gymEvents,
                    userName = userName,
                    isSignedIn = account != null
                )
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error("Failed to load activities: ${e.message}")
            }
        }
    }

    private suspend fun attachSessionStatus(
        events: List<CalendarEvent>
    ): List<CalendarEvent> {
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
                db?.status == SessionStatus.AT_GYM    -> SessionStatus.AT_GYM
                event.endTime < now && db?.status != SessionStatus.COMPLETED -> SessionStatus.MISSED
                else -> SessionStatus.UPCOMING
            }
            event.copy(sessionStatus = status)
        }
    }
}
