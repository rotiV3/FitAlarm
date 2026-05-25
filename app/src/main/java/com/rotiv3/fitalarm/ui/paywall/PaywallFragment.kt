package com.rotiv3.fitalarm.ui.paywall

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.rotiv3.fitalarm.R
import com.rotiv3.fitalarm.billing.SubscriptionManager
import com.rotiv3.fitalarm.databinding.FragmentPaywallBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PaywallFragment : BottomSheetDialogFragment() {

    @Inject lateinit var subscriptionManager: SubscriptionManager

    private var _binding: FragmentPaywallBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_FEATURE = "locked_feature"

        private val PRO_FEATURES = listOf(
            "Unlimited gym locations + custom names",
            "Full event details & training plans",
            "Share activities & route maps",
            "Sync your planned events to Google / Apple Calendar",
            "Priority support & new features first"
        )

        fun newInstance(lockedFeature: String = "This feature"): PaywallFragment {
            return PaywallFragment().apply {
                arguments = Bundle().apply { putString(ARG_FEATURE, lockedFeature) }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPaywallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val feature = arguments?.getString(ARG_FEATURE) ?: "This feature"
        binding.tvLockedFeature.text = "$feature is a Pro feature"

        // Populate feature rows dynamically
        val rows = listOf(binding.feat1, binding.feat2, binding.feat3, binding.feat4, binding.feat5)
        rows.forEachIndexed { i, row ->
            row.findViewById<TextView>(R.id.tvFeatureText).text = PRO_FEATURES.getOrElse(i) { "" }
        }

        binding.btnUpgrade.setOnClickListener {
            val launched = subscriptionManager.launchBillingFlow(requireActivity())
            if (!launched) {
                // Play Console not yet configured — show a friendly message in development
                Toast.makeText(requireContext(),
                    "Billing not configured yet — set up Google Play Console first.",
                    Toast.LENGTH_LONG).show()
            }
        }

        binding.btnRestore.setOnClickListener {
            Toast.makeText(requireContext(), "Checking purchases…", Toast.LENGTH_SHORT).show()
            // The SubscriptionManager.connect() already queries existing purchases on startup.
            // Nothing extra needed here — the tier updates automatically via the billing callback.
            dismiss()
        }

        binding.btnMaybeLater.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
