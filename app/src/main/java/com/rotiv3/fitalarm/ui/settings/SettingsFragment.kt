package com.rotiv3.fitalarm.ui.settings

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes
import com.rotiv3.fitalarm.BuildConfig
import com.rotiv3.fitalarm.OnboardingActivity
import com.rotiv3.fitalarm.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populateAccountInfo()
        setupButtons()
    }

    private fun populateAccountInfo() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null) {
            binding.tvAccountName.text = account.displayName ?: "Unknown"
            binding.tvAccountEmail.text = account.email ?: ""
        }
    }

    private fun setupButtons() {
        binding.btnSignOut.setOnClickListener {
            signOut()
        }

        binding.btnNotificationSettings.setOnClickListener {
            openNotificationSettings()
        }

        binding.btnExactAlarmPermission.setOnClickListener {
            requestExactAlarmPermission()
        }

        binding.btnBackgroundLocationInfo.setOnClickListener {
            openAppSettings()
        }
    }

    private fun signOut() {
        val webClientId = BuildConfig.WEB_CLIENT_ID.ifEmpty { "" }
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CalendarScopes.CALENDAR_READONLY))
            .apply {
                if (webClientId.isNotEmpty()) requestIdToken(webClientId)
            }
            .build()

        val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn
            .getClient(requireContext(), gso)

        googleSignInClient.signOut().addOnCompleteListener {
            Toast.makeText(requireContext(), "Signed out", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireContext(), OnboardingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            requireActivity().finish()
        }
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
        }
        startActivity(intent)
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "Exact alarm permission already granted", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Exact alarms supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${requireContext().packageName}")
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
