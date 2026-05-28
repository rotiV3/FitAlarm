package com.rotiv3.fitalarm.ui.home

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.rotiv3.fitalarm.BuildConfig
import com.rotiv3.fitalarm.R
import com.rotiv3.fitalarm.databinding.FragmentHomeBinding
import com.rotiv3.fitalarm.ui.calendar.CalendarEventAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var eventAdapter: CalendarEventAdapter

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) loadData()
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) loadData()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        setupFilter()
        setupTopBar()
        observeState()
        loadData()
    }

    private fun setupRecyclerView() {
        eventAdapter = CalendarEventAdapter { event ->
            findNavController().navigate(
                R.id.action_homeFragment_to_eventDetailFragment,
                bundleOf(
                    "eventId"       to event.id,
                    "eventTitle"    to event.title,
                    "eventStart"    to event.startTime,
                    "eventEnd"      to event.endTime,
                    "eventLocation" to event.location,
                    "eventDescription" to event.description,
                    "isGymEvent"    to event.isGymEvent,
                    "activityType"  to event.activityType.name,
                    "sessionStatus" to event.sessionStatus.name,
                    "trainingPlan"  to event.trainingPlan
                )
            )
        }
        binding.rvTodayEvents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTodayEvents.adapter = eventAdapter
    }

    private fun setupFab() {
        binding.fabCreateActivity.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_createEventFragment)
        }
    }

    private fun setupTopBar() {
        binding.btnSync.setOnClickListener { loadData() }
        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_settingsFragment)
        }
        binding.btnSignIn.setOnClickListener { launchGoogleSignIn() }
    }

    private fun setupFilter() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            viewModel.setShowOnlyWorkouts(checkedIds.contains(R.id.chipWorkouts))
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authEvent.collect { intent -> authLauncher.launch(intent) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is HomeUiState.Loading  -> showLoading()
                        is HomeUiState.Success  -> showSuccess(state)
                        is HomeUiState.Error    -> showError(state.message)
                    }
                }
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.contentGroup.visibility = View.GONE
    }

    private fun showSuccess(state: HomeUiState.Success) {
        binding.progressBar.visibility = View.GONE
        binding.contentGroup.visibility = View.VISIBLE

        // Greeting
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        val firstName = state.userName.substringBefore(" ").ifBlank { "Athlete" }
        binding.tvGreeting.text = "$greeting, $firstName!"

        // Date
        binding.tvDate.text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())

        // Alarm card — hidden (removed from state)
        binding.cardAlarm.visibility = View.GONE

        // Sign-in nudge banner for guests
        binding.cardSignInBanner.visibility = if (state.isSignedIn) View.GONE else View.VISIBLE

        // Events list
        if (state.events.isEmpty()) {
            binding.tvNoEvents.visibility = View.VISIBLE
            binding.rvTodayEvents.visibility = View.GONE
        } else {
            binding.tvNoEvents.visibility = View.GONE
            binding.rvTodayEvents.visibility = View.VISIBLE
            eventAdapter.submitList(state.events)
        }
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.contentGroup.visibility = View.VISIBLE
        binding.tvNoEvents.visibility = View.VISIBLE
        binding.tvNoEvents.text = message
        binding.rvTodayEvents.visibility = View.GONE
    }

    private fun loadData() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        val userName = account?.displayName ?: account?.email ?: ""
        viewModel.loadTodayData(account?.account, userName)
    }

    private fun launchGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.WEB_CLIENT_ID)   // required for Calendar API auth
            .requestEmail()
            .requestScopes(Scope(com.google.api.services.calendar.CalendarScopes.CALENDAR_READONLY))
            .build()
        val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(requireActivity(), gso)
        signInLauncher.launch(client.signInIntent)
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
