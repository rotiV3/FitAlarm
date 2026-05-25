package com.rotiv3.fitalarm.ui.gym

import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.rotiv3.fitalarm.R
import com.rotiv3.fitalarm.data.model.GymLocation
import com.rotiv3.fitalarm.databinding.FragmentGymSetupBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.rotiv3.fitalarm.ui.paywall.PaywallFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@AndroidEntryPoint
class GymSetupFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentGymSetupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GymViewModel by viewModels()
    private var googleMap: GoogleMap? = null
    private var selectedLatLng: LatLng? = null
    private var currentRadiusMeters: Float = 100f

    private lateinit var gymListAdapter: GymLocationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGymSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        setupRecyclerView()
        setupRadiusSeekBar()
        setupSaveButton()
        setupSearch()
        observeGymLocations()
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager
            .findFragmentById(R.id.mapFragmentGym) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    private fun setupRecyclerView() {
        gymListAdapter = GymLocationAdapter(
            onDelete = { gym -> viewModel.deleteGymLocation(gym) },
            onToggle = { gym -> viewModel.toggleGymActive(gym.id, !gym.isActive) },
            onRename = { gym, newName ->
                if (!viewModel.subscriptionManager.isPro) {
                    PaywallFragment.newInstance("Renaming gym locations")
                        .show(parentFragmentManager, "paywall")
                } else {
                    viewModel.updateGymLocation(gym.copy(name = newName))
                }
            }
        )
        binding.rvSavedGyms.adapter = gymListAdapter
        binding.rvSavedGyms.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupRadiusSeekBar() {
        // SeekBar range 0-450, mapped to 50-500m
        binding.seekBarRadius.max = 450
        binding.seekBarRadius.progress = 50 // default 100m (50 offset from 50)

        binding.seekBarRadius.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                currentRadiusMeters = (progress + 50).toFloat()
                binding.tvRadiusLabel.text = "Radius: ${currentRadiusMeters.toInt()}m"
                updateMapCircle()
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar) {}
        })
        binding.tvRadiusLabel.text = "Radius: 100m"
    }

    private fun setupSaveButton() {
        binding.btnSaveGym.setOnClickListener {
            val currentCount = viewModel.gymLocations.value.size
            if (!viewModel.canAddGym(currentCount)) {
                // Free users: show paywall when trying to add a 2nd gym
                PaywallFragment.newInstance("Multiple gym locations")
                    .show(parentFragmentManager, "paywall")
                return@setOnClickListener
            }
            val latLng = selectedLatLng
            if (latLng == null) {
                Toast.makeText(requireContext(), "Please select a location on the map", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val name = binding.etGymName.text?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: binding.searchViewGym.query?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: "My Gym"

            viewLifecycleOwner.lifecycleScope.launch {
                val address = resolveAddress(latLng)
                viewModel.addGymLocation(
                    name = name,
                    address = address,
                    latitude = latLng.latitude,
                    longitude = latLng.longitude,
                    radiusMeters = currentRadiusMeters
                )
                Toast.makeText(requireContext(), "Gym saved!", Toast.LENGTH_SHORT).show()
                selectedLatLng = null
                binding.etGymName.setText("")
                binding.searchViewGym.setQuery("", false)
                googleMap?.clear()
            }
        }
    }

    private fun setupSearch() {
        binding.searchViewGym.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { searchLocation(it) }
                return true
            }
            override fun onQueryTextChange(newText: String?) = false
        })
    }

    private fun searchLocation(query: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = withContext(Dispatchers.IO) {
                    geocoder.getFromLocationName(query, 1)
                }
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val latLng = LatLng(address.latitude, address.longitude)
                    selectLocation(latLng)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                } else {
                    Toast.makeText(requireContext(), "Location not found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Search failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun resolveAddress(latLng: LatLng): String {
        return try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = withContext(Dispatchers.IO) {
                geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            }
            addresses?.firstOrNull()?.getAddressLine(0) ?: "${latLng.latitude}, ${latLng.longitude}"
        } catch (e: Exception) {
            "${latLng.latitude}, ${latLng.longitude}"
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false

        // Default camera position
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(0.0, 0.0), 2f))

        map.setOnMapClickListener { latLng ->
            selectLocation(latLng)
        }
    }

    private fun selectLocation(latLng: LatLng) {
        selectedLatLng = latLng
        googleMap?.clear()

        googleMap?.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Gym Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )

        updateMapCircle()
    }

    private fun updateMapCircle() {
        val latLng = selectedLatLng ?: return
        val map = googleMap ?: return

        map.clear()
        map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Gym Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )
        map.addCircle(
            CircleOptions()
                .center(latLng)
                .radius(currentRadiusMeters.toDouble())
                .strokeColor(0xFF4CAF50.toInt())
                .fillColor(0x224CAF50)
                .strokeWidth(3f)
        )
    }

    private fun observeGymLocations() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.gymLocations.collect { gyms ->
                    gymListAdapter.submitList(gyms)
                    updateMapWithSavedGyms(gyms)
                    updateAddGymVisibility(gyms.size)
                }
            }
        }
    }

    private fun updateAddGymVisibility(count: Int) {
        val limitReached = !viewModel.canAddGym(count)
        binding.addGymControls.visibility = if (limitReached) View.GONE else View.VISIBLE
        if (limitReached) {
            binding.tvGymLimit.text = viewModel.gymLimitMessage
            binding.tvGymLimit.visibility = View.VISIBLE
            // Show paywall shortcut when free user hits limit
            if (!viewModel.subscriptionManager.isPro) {
                PaywallFragment.newInstance("Multiple gym locations")
                    .show(parentFragmentManager, "paywall")
            }
        } else {
            binding.tvGymLimit.visibility = View.GONE
        }

        // Hide the map entirely when max gyms reached so the bottom sheet fills the screen
        val mapView = requireView().findViewById<View>(R.id.mapFragmentGym)
        mapView?.visibility = if (limitReached) View.GONE else View.VISIBLE

        val behavior = BottomSheetBehavior.from(binding.bottomSheet)
        if (limitReached) {
            behavior.isDraggable = false
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        } else {
            behavior.isDraggable = true
            if (behavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    companion object {
        private const val MAX_GYM_LOCATIONS = 3
    }

    private fun updateMapWithSavedGyms(gyms: List<GymLocation>) {
        val map = googleMap ?: return
        if (selectedLatLng != null) return // Don't interfere with active selection

        map.clear()
        gyms.forEach { gym ->
            val latLng = LatLng(gym.latitude, gym.longitude)
            map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(gym.name)
                    .alpha(if (gym.isActive) 1f else 0.5f)
            )
            map.addCircle(
                CircleOptions()
                    .center(latLng)
                    .radius(gym.radiusMeters.toDouble())
                    .strokeColor(if (gym.isActive) 0xFF4CAF50.toInt() else 0xFF9E9E9E.toInt())
                    .fillColor(if (gym.isActive) 0x224CAF50 else 0x229E9E9E)
                    .strokeWidth(2f)
            )
        }

        if (gyms.isNotEmpty()) {
            val firstGym = gyms.first()
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(firstGym.latitude, firstGym.longitude), 15f
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
