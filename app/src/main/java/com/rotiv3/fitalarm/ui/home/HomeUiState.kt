package com.rotiv3.fitalarm.ui.home

import com.rotiv3.fitalarm.data.model.CalendarEvent
import com.rotiv3.fitalarm.data.model.WakeupAlarm

sealed class HomeUiState {
    object Loading : HomeUiState()

    data class Success(
        val events: List<CalendarEvent>,
        val gymEvents: List<CalendarEvent>,
        val nextAlarm: WakeupAlarm?,
        val userName: String,
        val suggestedWakeupTime: Long? = null
    ) : HomeUiState()

    data class Error(val message: String) : HomeUiState()
}
