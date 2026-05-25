package com.rotiv3.fitalarm.ui.createevent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rotiv3.fitalarm.billing.SubscriptionManager
import com.rotiv3.fitalarm.data.local.AppEventDao
import com.rotiv3.fitalarm.data.model.ActivityType
import com.rotiv3.fitalarm.data.model.AppEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateEventViewModel @Inject constructor(
    private val appEventDao: AppEventDao,
    val subscriptionManager: SubscriptionManager
) : ViewModel() {

    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()

    sealed class SaveResult {
        object Success : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    val isPro: Boolean get() = subscriptionManager.isPro

    fun saveEvent(
        title: String,
        activityType: ActivityType,
        startTime: Long,
        endTime: Long,
        location: String?,
        notes: String?,
        addToCalendar: Boolean
    ) {
        when {
            title.isBlank() -> {
                _saveResult.value = SaveResult.Error("Please enter a title")
                return
            }
            startTime == 0L -> {
                _saveResult.value = SaveResult.Error("Please select a date and start time")
                return
            }
            endTime <= startTime -> {
                _saveResult.value = SaveResult.Error("End time must be after start time")
                return
            }
        }

        viewModelScope.launch {
            val event = AppEvent(
                title = title.trim(),
                activityType = activityType,
                startTime = startTime,
                endTime = endTime,
                location = location?.trim()?.takeIf { it.isNotBlank() },
                notes = notes?.trim()?.takeIf { it.isNotBlank() },
                isSyncedToCalendar = false // Pro calendar sync handled separately
            )
            appEventDao.upsert(event)
            _saveResult.value = SaveResult.Success
        }
    }

    fun clearResult() {
        _saveResult.value = null
    }
}
