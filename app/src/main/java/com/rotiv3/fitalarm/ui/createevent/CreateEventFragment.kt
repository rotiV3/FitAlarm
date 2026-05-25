package com.rotiv3.fitalarm.ui.createevent

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
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.rotiv3.fitalarm.R
import com.rotiv3.fitalarm.data.model.ActivityType
import com.rotiv3.fitalarm.databinding.FragmentCreateEventBinding
import com.rotiv3.fitalarm.ui.paywall.PaywallFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@AndroidEntryPoint
class CreateEventFragment : Fragment() {

    private var _binding: FragmentCreateEventBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateEventViewModel by viewModels()

    // Form state
    private var selectedDateMillis: Long = 0L
    private var startHour: Int = -1
    private var startMinute: Int = -1
    private var endHour: Int = -1
    private var endMinute: Int = -1

    private val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationIcon(R.drawable.ic_alarm)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        setupDatePicker()
        setupTimePickers()
        setupCalendarSwitch()
        setupSaveButton()
        observeSaveResult()

        // Pre-fill with sensible defaults so Save works without forcing pickers
        selectedDateMillis = stripTimeFromMillis(System.currentTimeMillis())
        binding.etDate.setText(dateFormat.format(Date(selectedDateMillis)))

        val nowCal = Calendar.getInstance()
        startHour = nowCal.get(Calendar.HOUR_OF_DAY) + 1
        startMinute = 0
        endHour = startHour + 1
        endMinute = 0
        binding.etStartTime.setText(formatTime(startHour, startMinute))
        binding.etEndTime.setText(formatTime(endHour, endMinute))
    }

    // ─── Date picker ────────────────────────────────────────────────────────

    private fun setupDatePicker() {
        binding.etDate.setOnClickListener { showDatePicker() }
    }

    private fun showDatePicker() {
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.now())
            .build()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select activity date")
            .setSelection(
                if (selectedDateMillis > 0) selectedDateMillis
                else MaterialDatePicker.todayInUtcMilliseconds()
            )
            .setCalendarConstraints(constraints)
            .build()

        picker.addOnPositiveButtonClickListener { millis ->
            selectedDateMillis = millis
            binding.etDate.setText(dateFormat.format(Date(millis)))
        }
        picker.show(childFragmentManager, "date_picker")
    }

    // ─── Time pickers ────────────────────────────────────────────────────────

    private fun setupTimePickers() {
        binding.etStartTime.setOnClickListener { showTimePicker(isStart = true) }
        binding.etEndTime.setOnClickListener { showTimePicker(isStart = false) }
    }

    private fun showTimePicker(isStart: Boolean) {
        val currentHour = if (isStart) {
            if (startHour >= 0) startHour else Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        } else {
            if (endHour >= 0) endHour else (startHour + 1).coerceAtMost(23)
        }
        val currentMinute = if (isStart) {
            if (startMinute >= 0) startMinute else 0
        } else {
            if (endMinute >= 0) endMinute else 0
        }

        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(currentHour)
            .setMinute(currentMinute)
            .setTitleText(if (isStart) "Start time" else "End time")
            .build()

        picker.addOnPositiveButtonClickListener {
            if (isStart) {
                startHour = picker.hour
                startMinute = picker.minute
                binding.etStartTime.setText(formatTime(startHour, startMinute))
                // Auto-set end time +1h if not yet set
                if (endHour < 0) {
                    endHour = (startHour + 1).coerceAtMost(23)
                    endMinute = startMinute
                    binding.etEndTime.setText(formatTime(endHour, endMinute))
                }
            } else {
                endHour = picker.hour
                endMinute = picker.minute
                binding.etEndTime.setText(formatTime(endHour, endMinute))
            }
        }
        picker.show(childFragmentManager, "time_picker_${if (isStart) "start" else "end"}")
    }

    // ─── Calendar switch (Pro gate) ──────────────────────────────────────────

    private fun setupCalendarSwitch() {
        binding.switchCalendar.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !viewModel.isPro) {
                // Snap back immediately and show paywall
                binding.switchCalendar.isChecked = false
                PaywallFragment.newInstance("Add to calendar")
                    .show(parentFragmentManager, "paywall")
            }
        }
        // Disable switch visual affordance for free users
        binding.llCalendarSync.alpha = if (viewModel.isPro) 1f else 0.5f
    }

    // ─── Save ────────────────────────────────────────────────────────────────

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val title = binding.etTitle.text?.toString() ?: ""
            val location = binding.etLocation.text?.toString()
            val notes = binding.etNotes.text?.toString()
            val addToCalendar = binding.switchCalendar.isChecked && viewModel.isPro

            val activityType = selectedActivityType()
            val startTime = buildTimestampMillis(selectedDateMillis, startHour, startMinute)
            val endTime = buildTimestampMillis(selectedDateMillis, endHour, endMinute)

            viewModel.saveEvent(
                title = title,
                activityType = activityType,
                startTime = startTime,
                endTime = endTime,
                location = location,
                notes = notes,
                addToCalendar = addToCalendar
            )
        }
    }

    // ─── Observe result ──────────────────────────────────────────────────────

    private fun observeSaveResult() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveResult.collect { result ->
                    when (result) {
                        is CreateEventViewModel.SaveResult.Success -> {
                            Toast.makeText(requireContext(), "Activity saved! 🎯", Toast.LENGTH_SHORT).show()
                            viewModel.clearResult()
                            findNavController().popBackStack()
                        }
                        is CreateEventViewModel.SaveResult.Error -> {
                            Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                            viewModel.clearResult()
                        }
                        null -> { /* idle */ }
                    }
                }
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun selectedActivityType(): ActivityType {
        return when (binding.chipGroupType.checkedChipId) {
            R.id.chipGym -> ActivityType.GYM
            else -> ActivityType.OUTDOOR // Run, Walk, Hike, Bike all map to OUTDOOR
        }
    }

    /**
     * Combines a UTC-midnight date (from MaterialDatePicker) with local hour/minute
     * to produce an absolute epoch millisecond.
     */
    private fun buildTimestampMillis(dateMillis: Long, hour: Int, minute: Int): Long {
        if (dateMillis == 0L || hour < 0) return 0L
        // MaterialDatePicker returns UTC midnight; convert to local date
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = dateMillis
        }
        val localCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, cal.get(Calendar.YEAR))
            set(Calendar.MONTH, cal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return localCal.timeInMillis
    }

    private fun stripTimeFromMillis(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun formatTime(hour: Int, minute: Int) =
        String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
