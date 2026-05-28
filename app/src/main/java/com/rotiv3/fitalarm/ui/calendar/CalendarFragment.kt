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
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes
import com.google.android.material.tabs.TabLayout
import com.rotiv3.fitalarm.R
import com.rotiv3.fitalarm.databinding.FragmentCalendarBinding
import com.rotiv3.fitalarm.ui.paywall.PaywallFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@AndroidEntryPoint
class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CalendarViewModel by viewModels()
    private lateinit var eventAdapter: CalendarEventAdapter

    // Show/hide the CalendarView based on selected tab
    private var calendarExpanded = true

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Re-check sign-in state after returning from auth
            tryLoadSignedInAccount()
        }
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) tryLoadSignedInAccount()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabs()
        setupCalendarView()
        setupRecyclerView()
        setupButtons()
        setupFilter()
        observeState()

        tryLoadSignedInAccount()
        viewModel.initialLoad()
    }

    // ── Tabs: Day (CalendarView visible) / Month (CalendarView collapsed) ───

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Day"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Month"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                calendarExpanded = tab.position == 1 // Month tab shows full CalendarView
                binding.calendarView.visibility = if (calendarExpanded) View.VISIBLE else View.GONE
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    // ── CalendarView: tapping a day fires the ViewModel ──────────────────────

    private fun setupCalendarView() {
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selected = LocalDate.of(year, month + 1, dayOfMonth)
            viewModel.selectDate(selected)
        }
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        eventAdapter = CalendarEventAdapter { event ->
            findNavController().navigate(
                R.id.action_calendarFragment_to_eventDetailFragment,
                bundleOf(
                    "eventId"          to event.id,
                    "eventTitle"       to event.title,
                    "eventStart"       to event.startTime,
                    "eventEnd"         to event.endTime,
                    "eventLocation"    to event.location,
                    "eventDescription" to event.description,
                    "isGymEvent"       to event.isGymEvent,
                    "activityType"     to event.activityType.name,
                    "sessionStatus"    to event.sessionStatus.name,
                    "trainingPlan"     to event.trainingPlan
                )
            )
        }
        binding.rvEvents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEvents.adapter = eventAdapter
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnToday.setOnClickListener {
            val today = LocalDate.now(ZoneId.systemDefault())
            binding.calendarView.date = today
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            viewModel.goToToday()
        }
        binding.btnSync.setOnClickListener { viewModel.refresh() }

        // Guest sync buttons
        binding.btnSyncGoogle.setOnClickListener { launchGoogleSignIn() }
        binding.btnSyncApple.setOnClickListener  { launchAppleSignIn() }

        // Pro teaser upgrade button
        binding.btnUpgradePro.setOnClickListener {
            PaywallFragment.newInstance("Full calendar sync")
                .show(parentFragmentManager, "paywall")
        }
    }

    private fun setupFilter() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            viewModel.setShowOnlyWorkouts(checkedIds.contains(R.id.chipWorkouts))
        }
    }

    // ── State observation ─────────────────────────────────────────────────────

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
                        is CalendarUiState.Loading -> showLoading()
                        is CalendarUiState.Success -> showSuccess(state)
                        is CalendarUiState.Error   -> showError(state.message)
                    }
                }
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvEvents.visibility    = View.GONE
        binding.tvEmpty.visibility     = View.GONE
    }

    private fun showSuccess(state: CalendarUiState.Success) {
        binding.progressBar.visibility = View.GONE

        // Guest-only UI
        val isGuest = !state.isSignedIn
        binding.cardSyncPrompt.visibility = if (isGuest) View.VISIBLE else View.GONE
        binding.cardProTeaser.visibility  = if (isGuest) View.VISIBLE else View.GONE

        if (state.events.isEmpty()) {
            binding.tvEmpty.visibility  = View.VISIBLE
            binding.rvEvents.visibility = View.GONE
            if (isGuest) {
                binding.tvEmpty.text = "No planned activities for this day.\nCreate one with the ＋ button on the Home screen."
            } else {
                binding.tvEmpty.text = getString(R.string.no_events)
            }
        } else {
            binding.tvEmpty.visibility  = View.GONE
            binding.rvEvents.visibility = View.VISIBLE
            eventAdapter.submitList(state.events)
        }
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.tvEmpty.visibility     = View.VISIBLE
        binding.tvEmpty.text           = message
        binding.rvEvents.visibility    = View.GONE
    }

    // ── Sign-in helpers ───────────────────────────────────────────────────────

    private fun tryLoadSignedInAccount() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account?.account != null) {
            viewModel.setAccount(account.account!!)
        } else {
            // Guest: show sync prompt, load local events only
            viewModel.initialLoad()
        }
    }

    private fun launchGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.WEB_CLIENT_ID)   // required for Calendar API auth
            .requestEmail()
            .requestScopes(Scope(CalendarScopes.CALENDAR_READONLY))
            .build()
        val client = GoogleSignIn.getClient(requireActivity(), gso)
        signInLauncher.launch(client.signInIntent)
    }

    /** Apple Sign-In via OAuth2 Custom Tab — requires Apple developer setup. */
    private fun launchAppleSignIn() {
        val clientId     = "com.rotiv3.fitalarm"   // Service ID registered in Apple Developer
        val redirectUri  = "https://rotiv3.com/apple-callback" // Must be registered in Apple
        val state        = java.util.UUID.randomUUID().toString()
        val scope        = "name%20email"
        val appleAuthUrl = "https://appleid.apple.com/auth/authorize" +
                "?response_type=code" +
                "&client_id=$clientId" +
                "&redirect_uri=${android.net.Uri.encode(redirectUri)}" +
                "&scope=$scope" +
                "&response_mode=form_post" +
                "&state=$state"

        val intent = androidx.browser.customtabs.CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        try {
            intent.launchUrl(requireContext(), android.net.Uri.parse(appleAuthUrl))
        } catch (e: Exception) {
            // Custom Tabs not available — open in browser
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(appleAuthUrl)))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
