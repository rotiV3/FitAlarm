package com.rotiv3.fitalarm.ui

import androidx.lifecycle.ViewModel
import com.rotiv3.fitalarm.data.model.CalendarEvent

class SharedEventViewModel : ViewModel() {
    var selectedEvent: CalendarEvent? = null
}
