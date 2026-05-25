package com.rotiv3.fitalarm

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.rotiv3.fitalarm.alarm.CalendarSyncService
import com.rotiv3.fitalarm.billing.SubscriptionManager
import com.rotiv3.fitalarm.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var subscriptionManager: SubscriptionManager

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Connect billing client and verify existing subscriptions
        subscriptionManager.connect()

        setupNavigation()

        // Schedule activity alarms for today + tomorrow whenever the app opens
        try {
            startForegroundService(android.content.Intent(this, CalendarSyncService::class.java))
        } catch (e: Exception) {
            // Safe to ignore on some devices
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.calendarFragment,
                R.id.gymSetupFragment,
                R.id.achievementFragment
            )
        )

        binding.bottomNavigationView.setupWithNavController(navController)

        // Hide bottom nav on detail/create screens
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.settingsFragment,
                R.id.eventDetailFragment,
                R.id.createEventFragment -> {
                    binding.bottomNavigationView.visibility = View.GONE
                }
                else -> {
                    binding.bottomNavigationView.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
