package com.rotiv3.fitalarm.ui.achievement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rotiv3.fitalarm.data.local.OutdoorAchievementDao
import com.rotiv3.fitalarm.data.repository.GymRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AchievementViewModel @Inject constructor(
    private val gymRepository: GymRepository,
    private val outdoorAchievementDao: OutdoorAchievementDao
) : ViewModel() {

    val achievements: StateFlow<List<AchievementDisplay>> =
        combine(
            gymRepository.getAchievementsFlow(),
            outdoorAchievementDao.getAllFlow()
        ) { gymAchievements, outdoorAchievements ->
            val gym = gymAchievements.map { AchievementDisplay.from(it) }
            val outdoor = outdoorAchievements
                .filter { it.isUnlocked }
                .map { AchievementDisplay.from(it) }
            (gym + outdoor).sortedByDescending { it.earnedAt }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun markShared(gymAchievementId: Int) {
        viewModelScope.launch {
            gymRepository.markAchievementShared(gymAchievementId)
        }
    }
}
