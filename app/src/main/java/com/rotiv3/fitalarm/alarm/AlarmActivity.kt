package com.rotiv3.fitalarm.alarm

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.rotiv3.fitalarm.MainActivity
import com.rotiv3.fitalarm.data.repository.AlarmRepository
import com.rotiv3.fitalarm.data.repository.CalendarRepository
import com.rotiv3.fitalarm.databinding.ActivityAlarmBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding

    @Inject
    lateinit var alarmRepository: AlarmRepository

    @Inject
    lateinit var calendarRepository: CalendarRepository

    private var alarmId: Int = 0
    private var alarmLabel: String = "Wake Up"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alarmId = intent.getIntExtra(EXTRA_ALARM_ID, 0)
        alarmLabel = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "Wake Up"

        setupUi()
        setupButtons()
    }

    private fun setupUi() {
        val now = System.currentTimeMillis()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

        binding.tvAlarmTime.text = timeFormat.format(Date(now))
        binding.tvAlarmDate.text = dateFormat.format(Date(now))
        binding.tvAlarmLabel.text = alarmLabel
    }

    private fun setupButtons() {
        binding.btnDismiss.setOnClickListener {
            dismissAlarm()
        }

        binding.btnSnooze.setOnClickListener {
            snoozeAlarm()
        }
    }

    private fun dismissAlarm() {
        // Cancel the alarm notification
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(alarmId)

        // Schedule next day alarm
        lifecycleScope.launch {
            val account = GoogleSignIn.getLastSignedInAccount(this@AlarmActivity)
            if (account?.account != null) {
                alarmRepository.scheduleNextDayAlarm(account.account!!, calendarRepository)
            }
        }

        navigateToMain()
    }

    private fun snoozeAlarm() {
        val snoozeTime = System.currentTimeMillis() + SNOOZE_DURATION_MS

        // Cancel existing notification
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(alarmId)

        // Schedule snooze alarm with offset ID
        val appAlarmScheduler = AppAlarmScheduler(this)
        appAlarmScheduler.scheduleWakeupAlarm(
            timeMillis = snoozeTime,
            label = "$alarmLabel (Snoozed)",
            alarmId = alarmId + SNOOZE_ID_OFFSET
        )

        navigateToMain()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        // Prevent back button from dismissing the alarm without user action
        // User must explicitly dismiss or snooze
    }

    companion object {
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        private const val SNOOZE_DURATION_MS = 10 * 60 * 1000L // 10 minutes
        private const val SNOOZE_ID_OFFSET = 50000
    }
}
