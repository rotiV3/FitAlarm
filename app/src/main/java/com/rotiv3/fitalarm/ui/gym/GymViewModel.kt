package com.rotiv3.fitalarm.ui.gym

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rotiv3.fitalarm.data.model.GymLocation
import com.rotiv3.fitalarm.data.repository.GymRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GymViewModel @Inject constructor(
    private val gymRepository: GymRepository
) : ViewModel() {

    val gymLocations: StateFlow<List<GymLocation>> = gymRepository.getAllGymLocations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addGymLocation(
        name: String,
        address: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float
    ) {
        viewModelScope.launch {
            val gym = GymLocation(
                name = name,
                address = address,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters,
                isActive = true
            )
            gymRepository.insertGymLocation(gym)
        }
    }

    fun updateGymLocation(gymLocation: GymLocation) {
        viewModelScope.launch {
            gymRepository.updateGymLocation(gymLocation)
        }
    }

    fun deleteGymLocation(gymLocation: GymLocation) {
        viewModelScope.launch {
            gymRepository.deleteGymLocation(gymLocation)
        }
    }

    fun toggleGymActive(id: Int, isActive: Boolean) {
        viewModelScope.launch {
            gymRepository.setGymLocationActive(id, isActive)
        }
    }
}
