package com.rotiv3.fitalarm.ui.eventdetail

import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.rotiv3.fitalarm.R
import com.rotiv3.fitalarm.data.local.GymSessionDao
import com.rotiv3.fitalarm.data.local.OutdoorSessionDao
import com.rotiv3.fitalarm.data.model.ActivityType
import com.rotiv3.fitalarm.data.model.GymSession
import com.rotiv3.fitalarm.data.model.SessionStatus
import com.rotiv3.fitalarm.databinding.FragmentEventDetailBinding
import com.rotiv3.fitalarm.location.OutdoorTrackingService
import com.rotiv3.fitalarm.ui.outdoor.OutdoorSessionDetailActivity
import com.rotiv3.fitalarm.ui.outdoor.OutdoorTrackingActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class EventDetailFragment : Fragment(), OnMapReadyCallback {

    @Inject lateinit var gymSessionDao: GymSessionDao
    @Inject lateinit var outdoorSessionDao: OutdoorSessionDao

    private var _binding: FragmentEventDetailBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null
    private var eventId: String = ""
    private var eventLocation: String? = null
    private var eventTitle: String = ""
    private var eventEndTime: Long = 0L
    private var activityType: ActivityType = ActivityType.NONE

    // Live status loaded from DB — overrides the stale bundle value
    private var liveStatus: SessionStatus = SessionStatus.UPCOMING

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        eventId = arguments?.getString("eventId") ?: ""
        eventTitle = arguments?.getString("eventTitle") ?: ""
        val startTime = arguments?.getLong("eventStart") ?: 0L
        eventEndTime = arguments?.getLong("eventEnd") ?: 0L
        eventLocation = arguments?.getString("eventLocation")
        val isGymEvent = arguments?.getBoolean("isGymEvent") ?: false
        val trainingPlan = arguments?.getString("trainingPlan")

        activityType = try {
            ActivityType.valueOf(arguments?.getString("activityType") ?: "NONE")
        } catch (e: IllegalArgumentException) {
            if (isGymEvent) ActivityType.GYM else ActivityType.NONE
        }

        populateUi(eventTitle, startTime, eventEndTime, eventLocation, activityType, trainingPlan)
        setupMap()

        // Load live session status from DB, then wire the action button
        viewLifecycleOwner.lifecycleScope.launch {
            liveStatus = loadLiveStatus()
            setupStartButton()
        }
    }

    private suspend fun loadLiveStatus(): SessionStatus {
        if (activityType == ActivityType.NONE) return SessionStatus.UPCOMING
        return try {
            // Primary: check GymSession (created by alarm or by startOutdoorTracking)
            val gymSession = gymSessionDao.getSession(eventId)
            if (gymSession != null) return gymSession.status

            // Fallback: if an OutdoorSession exists, the activity was completed
            if (activityType == ActivityType.OUTDOOR) {
                val outdoorSession = outdoorSessionDao.getSession(eventId)
                if (outdoorSession != null) return SessionStatus.COMPLETED
            }

            SessionStatus.UPCOMING
        } catch (e: Exception) {
            SessionStatus.UPCOMING
        }
    }

    private fun populateUi(
        title: String,
        startTime: Long,
        endTime: Long,
        location: String?,
        activityType: ActivityType,
        trainingPlan: String?
    ) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())

        binding.tvEventTitle.text = title
        binding.tvEventDate.text = dateFormat.format(Date(startTime))
        binding.tvEventTime.text =
            "${timeFormat.format(Date(startTime))} – ${timeFormat.format(Date(endTime))}"

        if (!location.isNullOrBlank()) {
            binding.tvEventLocation.visibility = View.VISIBLE
            binding.tvEventLocation.text = location
            binding.ivLocationIcon.visibility = View.VISIBLE
        } else {
            binding.tvEventLocation.visibility = View.GONE
            binding.ivLocationIcon.visibility = View.GONE
        }

        val showTrainingPlan = activityType == ActivityType.GYM && !trainingPlan.isNullOrBlank()
        if (showTrainingPlan) {
            binding.cardTrainingPlan.visibility = View.VISIBLE
            binding.tvTrainingPlan.text = trainingPlan
        } else {
            binding.cardTrainingPlan.visibility = View.GONE
        }
    }

    private fun setupStartButton() {
        when (activityType) {
            ActivityType.NONE -> {
                binding.btnStartActivity.visibility = View.GONE
            }
            ActivityType.GYM -> {
                binding.btnStartActivity.visibility = View.VISIBLE
                binding.btnStartActivity.isEnabled = false
                binding.btnStartActivity.alpha = 0.75f
                binding.btnStartActivity.text = when (liveStatus) {
                    SessionStatus.UPCOMING  -> "⏰ GPS tracking starts at event time"
                    SessionStatus.AT_GYM    -> "📍 GPS Tracking Active"
                    SessionStatus.COMPLETED -> "✅ Gym Session Completed"
                    SessionStatus.MISSED    -> "❌ Session Missed"
                }
            }
            ActivityType.OUTDOOR -> {
                binding.btnStartActivity.visibility = View.VISIBLE
                when (liveStatus) {
                    SessionStatus.COMPLETED -> {
                        binding.btnStartActivity.text = "📊 View Activity Summary"
                        binding.btnStartActivity.isEnabled = true
                        binding.btnStartActivity.alpha = 1f
                        binding.btnStartActivity.setOnClickListener {
                            startActivity(
                                Intent(requireContext(), OutdoorSessionDetailActivity::class.java).apply {
                                    putExtra(OutdoorSessionDetailActivity.EXTRA_EVENT_ID, eventId)
                                }
                            )
                        }
                    }
                    SessionStatus.AT_GYM -> {
                        binding.btnStartActivity.text = "📍 Open Tracking Screen"
                        binding.btnStartActivity.isEnabled = true
                        binding.btnStartActivity.alpha = 1f
                        binding.btnStartActivity.setOnClickListener {
                            startActivity(
                                Intent(requireContext(), OutdoorTrackingActivity::class.java).apply {
                                    putExtra(OutdoorTrackingActivity.EXTRA_EVENT_ID, eventId)
                                    putExtra(OutdoorTrackingActivity.EXTRA_EVENT_TITLE, eventTitle)
                                }
                            )
                        }
                    }
                    SessionStatus.UPCOMING, SessionStatus.MISSED -> {
                        binding.btnStartActivity.text = "🌿 Start Tracking"
                        binding.btnStartActivity.isEnabled = true
                        binding.btnStartActivity.alpha = 1f
                        binding.btnStartActivity.setOnClickListener { startOutdoorTracking() }
                    }
                }
            }
        }
    }

    private fun startOutdoorTracking() {
        val now = System.currentTimeMillis()

        // Create/update GymSession so loadLiveStatus() can detect AT_GYM / COMPLETED
        viewLifecycleOwner.lifecycleScope.launch {
            gymSessionDao.upsert(
                GymSession(
                    eventId = eventId,
                    eventTitle = eventTitle,
                    startTime = now,
                    endTime = eventEndTime,
                    status = SessionStatus.AT_GYM
                )
            )
        }

        requireContext().startForegroundService(
            Intent(requireContext(), OutdoorTrackingService::class.java).apply {
                putExtra(OutdoorTrackingService.EXTRA_EVENT_ID, eventId)
                putExtra(OutdoorTrackingService.EXTRA_EVENT_TITLE, eventTitle)
                putExtra(OutdoorTrackingService.EXTRA_EVENT_END_TIME, eventEndTime)
            }
        )
        startActivity(
            Intent(requireContext(), OutdoorTrackingActivity::class.java).apply {
                putExtra(OutdoorTrackingActivity.EXTRA_EVENT_ID, eventId)
                putExtra(OutdoorTrackingActivity.EXTRA_EVENT_TITLE, eventTitle)
            }
        )
    }

    private fun setupMap() {
        if (!eventLocation.isNullOrBlank()) {
            binding.mapContainer.visibility = View.VISIBLE
            val mapFragment = childFragmentManager
                .findFragmentById(R.id.mapFragment) as? SupportMapFragment
            mapFragment?.getMapAsync(this)
        } else {
            binding.mapContainer.visibility = View.GONE
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        val location = eventLocation ?: return

        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())

            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(location, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val latLng = LatLng(address.latitude, address.longitude)
                map.addMarker(MarkerOptions().position(latLng).title(eventTitle))
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            }
        } catch (e: Exception) {
            binding.mapContainer.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
