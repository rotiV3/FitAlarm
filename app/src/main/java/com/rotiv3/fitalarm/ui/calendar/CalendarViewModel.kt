package com.rotiv3.fitalarm.ui.calendar

import android.accounts.Account
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.rotiv3.fitalarm.data.local.AppEventDao
import com.rotiv3.fitalarm.data.local.OutdoorSessionDao
import com.rotiv3.fitalarm.data.model.CalendarEvent
import com.rotiv3.fitalarm.data.model.SessionStatus
import com.rotiv3.fitalarm.data.repository.CalendarRepository
import com.rotiv3.fitalarm.data.repository.GymRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val gymRepository: GymRepository,
    private val outdoorSessionDao: OutdoorSessionDao,
    private val appEventDao: AppEventDao
) : ViewModel() {

    val achievedEventIds: StateFlow<Set<String>> = outdoorSessionDao.getAllSessionsFlow()
        .map { sessions -> sessions.map { it.eventId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _uiState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _dateLabel = MutableStateFlow("")
    val dateLabel: StateFlow<String> = _dateLabel.asStateFlow()

    private val _authEvent = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val authEvent: SharedFlow<Intent> = _authEvent.asSharedFlow()

    /** True when the user is signed in with Google (or Apple later). */
    private var currentAccount: Account? = null
    val isSignedIn: Boolean get() = currentAccount != null

    private var currentDate: LocalDate = LocalDate.now(ZoneId.systemDefault())
    private var showOnlyWorkouts = false

    fun setShowOnlyWorkouts(enabled: Boolean) {
        showOnlyWorkouts = enabled
        loadEventsForDate(currentDate)
    }

    fun setAccount(account: Account) {
        currentAccount = account
        loadEventsForDate(currentDate)
    }

    /** Called when user taps a day on the CalendarView. */
    fun selectDate(date: LocalDate) {
        currentDate = date
        updateDateLabel()
        loadEventsForDate(date)
    }

    fun goToToday() {
        currentDate = LocalDate.now(ZoneId.systemDefault())
        updateDateLabel()
        loadEventsForDate(currentDate)
    }

    fun refresh() {
        loadEventsForDate(currentDate)
    }

    fun initialLoad() {
        updateDateLabel()
        loadEventsForDate(currentDate)
    }

    private fun updateDateLabel() {
        val fmt = DateTimeFormatter.ofPattern("EEEE, MMMM d")
        _dateLabel.value = currentDate.format(fmt)
    }

    private fun loadEventsForDate(date: LocalDate) {
        _uiState.value = CalendarUiState.Loading

        viewModelScope.launch {
            try {
                val zone = ZoneId.systemDefault()
                val dayStart = date.atStartOfDay(zone).toEpochSecond() * 1000L
                val dayEnd   = date.plusDays(1).atStartOfDay(zone).toEpochSecond() * 1000L

                // Always load local app events
                val localEvents: List<CalendarEvent> = appEventDao
                    .getEventsForDay(dayStart, dayEnd)
                    .first()
                    .map { it.toCalendarEvent() }

                // Google Calendar events — only when signed in
                val remoteEvents: List<CalendarEvent> = if (currentAccount != null) {
                    try {
                        calendarRepository.getEventsForDay(currentAccount!!, date)
                    } catch (e: UserRecoverableAuthIOException) {
                        _authEvent.tryEmit(e.intent)
                        emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else emptyList()

                val merged = (localEvents + remoteEvents).sortedBy { it.startTime }
                val withStatus = attachSessionStatus(merged)
                val filtered = if (showOnlyWorkouts) withStatus.filter { it.isGymEvent } else withStatus

                _uiState.value = CalendarUiState.Success(
                    events = filtered,
                    isSignedIn = currentAccount != null
                )
            } catch (e: Exception) {
                _uiState.value = CalendarUiState.Error("Failed to load events: ${e.message}")
            }
        }
    }

    private suspend fun attachSessionStatus(
        events: List<CalendarEvent>
    ): List<CalendarEvent> {
        if (events.isEmpty()) return events
        val now = System.currentTimeMillis()
        val rangeStart = events.minOf { it.startTime }
        val rangeEnd = events.maxOf { it.endTime }
        val sessions = gymRepository.getSessionsForDay(rangeStart, rangeEnd).associateBy { it.eventId }

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

    // Kept for back-compat (CalendarFragment still calls setViewMode-style tabs)
    fun setViewMode(viewMode: ViewMode) { /* Day tab = default; month tab collapses the CalendarView */ }
}
