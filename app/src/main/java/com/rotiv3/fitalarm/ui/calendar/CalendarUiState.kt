package com.rotiv3.fitalarm.ui.calendar

import com.rotiv3.fitalarm.data.model.CalendarEvent

sealed class CalendarUiState {
    object Loading : CalendarUiState()

    data class Success(
        val events: List<CalendarEvent>,
        val isSignedIn: Boolean = false
    ) : CalendarUiState()

    data class Error(val message: String) : CalendarUiState()
}

enum class ViewMode { DAY, WEEK, MONTH }
