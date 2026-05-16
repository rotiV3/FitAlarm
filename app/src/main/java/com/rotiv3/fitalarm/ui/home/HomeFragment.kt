package com.rotiv3.fitalarm.ui.home

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        setupFilter()
        observeState()
        loadData()
    }

    private fun setupRecyclerView() {
        eventAdapter = CalendarEventAdapter { event ->
            findNavController().navigate(
                R.id.action_homeFragment_to_eventDetailFragment,
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
        binding.rvTodayEvents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTodayEvents.adapter = eventAdapter
    }

    private fun setupFab() {
        binding.fabSetAlarm.setOnClickListener { showTimePickerDialog() }
        binding.btnSync.setOnClickListener { loadData() }
        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_settingsFragment)
        }
    }

    private fun setupFilter() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            viewModel.setShowOnlyWorkouts(checkedIds.contains(R.id.chipWorkouts))
        }
    }

    private fun showTimePickerDialog() {
        val calendar = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
            setNativeAlarm(selectedHour, selectedMinute, "Wake Up")

            // Also persist locally so the app can show the alarm card
            val alarmCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, selectedHour)
                set(Calendar.MINUTE, selectedMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
            }
            val account = GoogleSignIn.getLastSignedInAccount(requireContext())
            account?.account?.let {
                viewModel.scheduleWakeupAlarm(it, alarmCalendar.timeInMillis, "Wake Up")
            }
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun setNativeAlarm(hour: Int, minute: Int, label: String) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
            Toast.makeText(requireContext(), "Alarm set for $hour:${minute.toString().padStart(2, '0')}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "No clock app found on this device", Toast.LENGTH_SHORT).show()
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
                        is HomeUiState.Loading -> showLoading()
                        is HomeUiState.Success -> showSuccess(state)
                        is HomeUiState.Error -> showError(state.message)
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
        val firstName = state.userName.substringBefore(" ")
        binding.tvGreeting.text = "$greeting, $firstName!"

        // Date
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        binding.tvDate.text = dateFormat.format(Date())

        // Alarm card
        if (state.nextAlarm != null) {
            binding.cardAlarm.visibility = View.VISIBLE
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            binding.tvAlarmTime.text = timeFormat.format(Date(state.nextAlarm.wakeupTimeMillis))
            binding.tvAlarmLabel.text = state.nextAlarm.notes ?: "Wake Up"
        } else if (state.suggestedWakeupTime != null) {
            binding.cardAlarm.visibility = View.VISIBLE
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            binding.tvAlarmTime.text = "Suggested: ${timeFormat.format(Date(state.suggestedWakeupTime))}"
            binding.tvAlarmLabel.text = "Tap + to set alarm"
        } else {
            binding.cardAlarm.visibility = View.GONE
        }

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
        if (account?.account != null) {
            val userName = account.displayName ?: account.email ?: "Athlete"
            viewModel.loadTodayData(account.account!!, userName)
        }
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
