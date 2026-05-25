package com.rotiv3.fitalarm.billing

import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.rotiv3.fitalarm.data.model.UserTier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "SubscriptionManager"
        const val PRO_PRODUCT_ID = "fitalarm_pro_monthly"
        private const val PREF_NAME = "fitalarm_subscription"
        private const val KEY_IS_PRO = "is_pro"
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _tier = MutableStateFlow(loadPersistedTier())
    val tier: StateFlow<UserTier> = _tier.asStateFlow()

    val isPro: Boolean get() = _tier.value == UserTier.PRO

    // ─── Google Play Billing client ─────────────────────────────────────────

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private var proProductDetails: ProductDetails? = null

    /** Call from MainActivity.onCreate() to connect and verify existing purchases. */
    fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected")
                    queryExistingPurchases()
                    queryProductDetails()
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
            }
        })
    }

    private fun queryExistingPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasActiveSub = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                persistTier(hasActiveSub)
            }
        }
    }

    private fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRO_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            ).build()

        billingClient.queryProductDetailsAsync(params) { _, productDetailsList ->
            proProductDetails = productDetailsList.firstOrNull()
            Log.d(TAG, "Product details loaded: ${proProductDetails?.name}")
        }
    }

    /**
     * Launch the Play Store subscription flow from an Activity.
     * Returns false if billing is not ready yet.
     */
    fun launchBillingFlow(activity: android.app.Activity): Boolean {
        val details = proProductDetails ?: run {
            Log.w(TAG, "Product details not loaded yet")
            return false
        }
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return false

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            ).build()

        billingClient.launchBillingFlow(activity, flowParams)
        return true
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (!purchase.isAcknowledged) acknowledgePurchase(purchase)
                    persistTier(true)
                }
            }
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User cancelled billing flow")
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            Log.d(TAG, "Acknowledge result: ${result.responseCode}")
        }
    }

    // ─── Tier state ─────────────────────────────────────────────────────────

    private fun persistTier(isPro: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PRO, isPro).apply()
        _tier.value = if (isPro) UserTier.PRO else UserTier.FREE
        Log.d(TAG, "Tier set to: ${_tier.value}")
    }

    private fun loadPersistedTier(): UserTier =
        if (prefs.getBoolean(KEY_IS_PRO, false)) UserTier.PRO else UserTier.FREE

    /** For testing/development: toggle Pro without Play Store. */
    fun devSetPro(isPro: Boolean) {
        persistTier(isPro)
    }
}
