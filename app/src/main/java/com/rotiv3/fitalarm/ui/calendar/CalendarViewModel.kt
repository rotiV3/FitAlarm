package com.rotiv3.fitalarm.ui.calendar

import android.accounts.Account
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.rotiv3.fitalarm.data.local.OutdoorSessionDao
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val gymRepository: GymRepository,
    private val outdoorSessionDao: OutdoorSessionDao
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

    private var currentViewMode = ViewMode.DAY
    private var currentAccount: Account? = null
    private var currentDate: LocalDate = LocalDate.now(ZoneId.systemDefault())
    private var showOnlyWorkouts = false

    fun setShowOnlyWorkouts(enabled: Boolean) {
        showOnlyWorkouts = enabled
        loadEvents()
    }

    fun setAccount(account: Account) {
        currentAccount = account
        updateDateLabel()
        loadEvents()
    }

    fun setViewMode(viewMode: ViewMode) {
        currentViewMode = viewMode
        updateDateLabel()
        loadEvents()
    }

    fun navigatePrevious() {
        currentDate = when (currentViewMode) {
            ViewMode.DAY -> currentDate.minusDays(1)
            ViewMode.WEEK -> currentDate.minusWeeks(1)
            ViewMode.MONTH -> currentDate.minusMonths(1)
        }
        updateDateLabel()
        loadEvents()
    }

    fun navigateNext() {
        currentDate = when (currentViewMode) {
            ViewMode.DAY -> currentDate.plusDays(1)
            ViewMode.WEEK -> currentDate.plusWeeks(1)
            ViewMode.MONTH -> currentDate.plusMonths(1)
        }
        updateDateLabel()
        loadEvents()
    }

    fun goToToday() {
        currentDate = LocalDate.now(ZoneId.systemDefault())
        updateDateLabel()
        loadEvents()
    }

    fun refresh() {
        loadEvents()
    }

    private suspend fun attachSessionStatus(
        events: List<com.rotiv3.fitalarm.data.model.CalendarEvent>
    ): List<com.rotiv3.fitalarm.data.model.CalendarEvent> {
        val now = System.currentTimeMillis()
        val rangeStart = events.minOfOrNull { it.startTime } ?: return events
        val rangeEnd = events.maxOfOrNull { it.endTime } ?: return events
        val sessions = gymRepository.getSessionsForDay(rangeStart, rangeEnd)
            .associateBy { it.eventId }

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

    private fun updateDateLabel() {
        val fmt = DateTimeFormatter.ofPattern("MMM d")
        val fmtFull = DateTimeFormatter.ofPattern("MMMM d, yyyy")
        val fmtMonth = DateTimeFormatter.ofPattern("MMMM yyyy")
        _dateLabel.value = when (currentViewMode) {
            ViewMode.DAY -> currentDate.format(fmtFull)
            ViewMode.WEEK -> {
                val monday = currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val sunday = monday.plusDays(6)
                "${monday.format(fmt)} – ${sunday.format(fmt)}"
            }
            ViewMode.MONTH -> currentDate.format(fmtMonth)
        }
    }

    private fun loadEvents() {
        val account = currentAccount ?: return
        _uiState.value = CalendarUiState.Loading

        viewModelScope.launch {
            try {
                val events = when (currentViewMode) {
                    ViewMode.DAY -> calendarRepository.getEventsForDay(account, currentDate)
                    ViewMode.WEEK -> {
                        val monday = currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        calendarRepository.getEventsForWeek(account, monday)
                    }
                    ViewMode.MONTH -> calendarRepository.getEventsForMonth(
                        account,
                        YearMonth.from(currentDate)
                    )
                }
                val eventsWithStatus = attachSessionStatus(events)
                val filtered = if (showOnlyWorkouts) eventsWithStatus.filter { it.isGymEvent } else eventsWithStatus
                _uiState.value = CalendarUiState.Success(filtered, currentViewMode)
            } catch (e: UserRecoverableAuthIOException) {
                _authEvent.tryEmit(e.intent)
                _uiState.value = CalendarUiState.Error("Calendar permission needed. Tap sync after granting access.")
            } catch (e: Exception) {
                _uiState.value = CalendarUiState.Error("Failed to load calendar: ${e.message}")
            }
        }
    }
}
