package com.rotiv3.fitalarm.ui.calendar

import android.app.Activity
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
import com.google.android.material.tabs.TabLayout
import com.rotiv3.fitalarm.R
import com.rotiv3.fitalarm.databinding.FragmentCalendarBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CalendarViewModel by viewModels()
    private lateinit var eventAdapter: CalendarEventAdapter

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.refresh()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        setupRecyclerView()
        setupNavigation()
        setupFilter()
        observeState()

        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account?.account != null) {
            viewModel.setAccount(account.account!!)
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Day"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Week"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Month"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val viewMode = when (tab.position) {
                    0 -> ViewMode.DAY
                    1 -> ViewMode.WEEK
                    2 -> ViewMode.MONTH
                    else -> ViewMode.DAY
                }
                viewModel.setViewMode(viewMode)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupRecyclerView() {
        eventAdapter = CalendarEventAdapter { event ->
            findNavController().navigate(
                R.id.action_calendarFragment_to_eventDetailFragment,
                bundleOf(
                    "eventId" to event.id,
                    "eventTitle" to event.title,
                    "eventStart" to event.startTime,
                    "eventEnd" to event.endTime,
                    "eventLocation" to event.location,
                    "eventDescription" to event.description,
                    "isGymEvent" to event.isGymEvent,
                    "activityType" to event.activityType.name,
                    "sessionStatus" to event.sessionStatus.name,
                    "trainingPlan" to event.trainingPlan
                )
            )
        }
        binding.rvEvents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEvents.adapter = eventAdapter
    }

    private fun setupNavigation() {
        binding.btnPrevious.setOnClickListener { viewModel.navigatePrevious() }
        binding.btnNext.setOnClickListener { viewModel.navigateNext() }
        binding.btnToday.setOnClickListener { viewModel.goToToday() }
        binding.btnSync.setOnClickListener { viewModel.refresh() }
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
                viewModel.dateLabel.collect { label ->
                    binding.tvCurrentDate.text = label
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.achievedEventIds.collect { ids ->
                    eventAdapter.setAchievedEventIds(ids)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is CalendarUiState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.rvEvents.visibility = View.GONE
                            binding.tvEmpty.visibility = View.GONE
                        }
                        is CalendarUiState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            if (state.events.isEmpty()) {
                                binding.tvEmpty.visibility = View.VISIBLE
                                binding.rvEvents.visibility = View.GONE
                            } else {
                                binding.tvEmpty.visibility = View.GONE
                                binding.rvEvents.visibility = View.VISIBLE
                                eventAdapter.submitList(state.events)
                            }
                        }
                        is CalendarUiState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.tvEmpty.visibility = View.VISIBLE
                            binding.tvEmpty.text = state.message
                            binding.rvEvents.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
