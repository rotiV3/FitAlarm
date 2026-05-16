package com.rotiv3.fitalarm.ui.outdoor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.rotiv3.fitalarm.R
import com.rotiv3.fitalarm.databinding.ActivityOutdoorTrackingBinding
import com.rotiv3.fitalarm.location.OutdoorTrackingService
import com.rotiv3.fitalarm.location.TrackingState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OutdoorTrackingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityOutdoorTrackingBinding
    private var googleMap: GoogleMap? = null
    private var routePolyline: Polyline? = null
    private var cameraFollowing = true

    private var eventId = ""
    private var eventTitle = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOutdoorTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: ""
        eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Activity"
        binding.tvActivityTitle.text = "🌿 $eventTitle"

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.btnFinish.setOnClickListener { confirmFinish() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish() // minimise — tracking continues in background
            }
        })

        observeTrackingState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: eventId
        eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: eventTitle
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false
        map.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                cameraFollowing = false
            }
        }
        val currentState = OutdoorTrackingService.state.value
        if (currentState.routePoints.isNotEmpty()) updateMap(currentState)
    }

    private fun observeTrackingState() {
        lifecycleScope.launch {
            OutdoorTrackingService.state.collectLatest { state ->
                updateStats(state)
                if (googleMap != null && state.routePoints.isNotEmpty()) updateMap(state)
            }
        }
    }

    private fun updateStats(state: TrackingState) {
        binding.tvDistance.text = OutdoorTrackingService.formatDistance(state.totalDistanceMeters)
        binding.tvTime.text = OutdoorTrackingService.formatTime(state.elapsedSeconds)
        binding.tvPace.text = OutdoorTrackingService.calculatePace(state.totalDistanceMeters, state.elapsedSeconds)
    }

    private fun updateMap(state: TrackingState) {
        val latLngs = state.routePoints.map { LatLng(it.lat, it.lng) }
        if (latLngs.isEmpty()) return

        if (routePolyline == null) {
            routePolyline = googleMap?.addPolyline(
                PolylineOptions()
                    .addAll(latLngs)
                    .width(10f)
                    .color(Color.parseColor("#4CAF50"))
                    .geodesic(true)
            )
        } else {
            routePolyline?.points = latLngs
        }

        if (cameraFollowing) {
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLngs.last(), 17f))
        }
    }

    private fun confirmFinish() {
        AlertDialog.Builder(this)
            .setTitle("Finish Activity?")
            .setMessage("This will save your route and mark the activity as completed.")
            .setPositiveButton("Finish") { _, _ ->
                startService(Intent(this, OutdoorTrackingService::class.java).apply {
                    action = OutdoorTrackingService.ACTION_STOP
                })
                // Open the detail screen — it retries loading the session from DB
                startActivity(
                    Intent(this, OutdoorSessionDetailActivity::class.java).apply {
                        putExtra(OutdoorSessionDetailActivity.EXTRA_EVENT_ID, eventId)
                    }
                )
                finish()
            }
            .setNegativeButton("Continue", null)
            .show()
    }

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_EVENT_TITLE = "extra_event_title"
    }
}
