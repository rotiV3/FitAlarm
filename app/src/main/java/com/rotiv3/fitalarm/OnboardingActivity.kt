package com.rotiv3.fitalarm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes
import com.rotiv3.fitalarm.databinding.ActivityOnboardingBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var googleSignInClient: GoogleSignInClient

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            handleSignInSuccess(account)
        } catch (e: ApiException) {
            Log.e(TAG, "Google sign-in failed: ${e.statusCode}", e)
            showError("Sign-in failed. Please try again.")
            binding.progressBar.visibility = View.GONE
            binding.btnSignIn.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGoogleSignIn()
        setupClickListeners()

        // Already signed in → skip onboarding
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null && account.grantedScopes.contains(Scope(CalendarScopes.CALENDAR_READONLY))) {
            navigateToMain()
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.WEB_CLIENT_ID)   // required for server-side auth & Calendar API
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope(CalendarScopes.CALENDAR_READONLY))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupClickListeners() {
        // ── Google Sign-In ────────────────────────────────────────────────────
        binding.btnSignIn.setOnClickListener {
            binding.btnSignIn.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE
            signInLauncher.launch(googleSignInClient.signInIntent)
        }

        // ── Apple Sign-In (OAuth2 via Custom Tab) ─────────────────────────────
        binding.btnSignInApple.setOnClickListener {
            launchAppleSignIn()
        }

        // ── Guest (no account) ────────────────────────────────────────────────
        binding.btnContinueAsGuest?.setOnClickListener {
            navigateToMain()
        }
    }

    private fun handleSignInSuccess(account: GoogleSignInAccount) {
        Log.d(TAG, "Sign-in successful: ${account.email}")
        binding.progressBar.visibility = View.GONE
        navigateToMain()
    }

    private fun navigateToMain() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // ── Apple Sign-In via OAuth2 ──────────────────────────────────────────────
    // Requires setup in Apple Developer Console:
    //   1. Create a Services ID at developer.apple.com (e.g. com.rotiv3.fitalarm)
    //   2. Enable "Sign in with Apple" for that Services ID
    //   3. Add a Return URL: https://your-domain.com/apple-callback
    //   4. Handle the server-side callback (see APPLE_SIGNIN_SETUP.md)
    private fun launchAppleSignIn() {
        val clientId    = "com.rotiv3.fitalarm"              // ← your Apple Services ID
        val redirectUri = "https://rotiv3.com/apple-callback" // ← your server redirect URL
        val state       = java.util.UUID.randomUUID().toString()

        val appleAuthUrl = buildString {
            append("https://appleid.apple.com/auth/authorize")
            append("?response_type=code")
            append("&client_id=").append(Uri.encode(clientId))
            append("&redirect_uri=").append(Uri.encode(redirectUri))
            append("&scope=").append(Uri.encode("name email"))
            append("&response_mode=form_post")
            append("&state=").append(state)
        }

        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(this, Uri.parse(appleAuthUrl))
        } catch (e: Exception) {
            // Fallback to browser if Custom Tabs unavailable
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(appleAuthUrl)))
        }
    }

    companion object {
        private const val TAG = "OnboardingActivity"
    }
}
