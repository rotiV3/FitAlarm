package com.rotiv3.fitalarm.ui.home

import com.rotiv3.fitalarm.data.model.CalendarEvent

sealed class HomeUiState {
    object Loading : HomeUiState()

    data class Success(
        val events: List<CalendarEvent>,
        val gymEvents: List<CalendarEvent>,
        val userName: String,
        /** True when the user is authenticated with Google (calendar sync available). */
        val isSignedIn: Boolean = false
    ) : HomeUiState()

    data class Error(val message: String) : HomeUiState()
}
