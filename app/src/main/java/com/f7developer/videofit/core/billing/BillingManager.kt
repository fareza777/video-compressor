package com.f7developer.videofit.core.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.f7developer.videofit.BuildConfig
import com.f7developer.videofit.core.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One non-consumable product: `remove_ads` ($4.99). The entitlement is stored
 * locally after Play acknowledges the purchase, and restored on any new install
 * through `queryPurchasesAsync`.
 */
class BillingManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) : PurchasesUpdatedListener {

    private var client: BillingClient? = null
    private var product: ProductDetails? = null
    private var connecting = false

    var ready by mutableStateOf(false)
        private set
    var priceLabel by mutableStateOf<String?>(null)
        private set
    var lastMessage by mutableStateOf<String?>(null)
        private set

    fun start() {
        if (client != null || connecting) return
        connecting = true
        client = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()
            .also { billing ->
                billing.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        connecting = false
                        ready = result.responseCode == BillingClient.BillingResponseCode.OK
                        if (ready) {
                            queryProduct()
                            restore(silent = true)
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        connecting = false
                        ready = false
                    }
                })
            }
    }

    private fun queryProduct() {
        val billing = client ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(BuildConfig.REMOVE_ADS_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        billing.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                product = details.firstOrNull()
                priceLabel = product?.oneTimePurchaseOfferDetails?.formattedPrice
            }
        }
    }

    fun purchase(activity: Activity) {
        val billing = client
        val details = product
        if (billing == null || details == null || !ready) {
            lastMessage = "Google Play is not reachable. Try again after installing from Play."
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        val result = billing.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            lastMessage = "Could not open the purchase sheet (${result.responseCode})."
        }
    }

    fun restore(silent: Boolean = false) {
        val billing = client ?: return
        billing.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                if (!silent) lastMessage = "Could not check previous purchases."
                return@queryPurchasesAsync
            }
            val owned = purchases.any { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    purchase.products.contains(BuildConfig.REMOVE_ADS_PRODUCT_ID)
            }
            if (owned) {
                scope.launch { settings.setAdsRemoved(true) }
                if (!silent) lastMessage = "Purchase restored. Ads are off."
            } else if (!silent) {
                lastMessage = "No previous purchase found on this account."
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return@forEach
                    if (!purchase.products.contains(BuildConfig.REMOVE_ADS_PRODUCT_ID)) return@forEach
                    if (!purchase.isAcknowledged) {
                        val params = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        client?.acknowledgePurchase(params) { ack ->
                            if (ack.responseCode == BillingClient.BillingResponseCode.OK) {
                                scope.launch { settings.setAdsRemoved(true) }
                                lastMessage = "Ads removed. Thank you!"
                            }
                        }
                    } else {
                        scope.launch { settings.setAdsRemoved(true) }
                        lastMessage = "Ads removed. Thank you!"
                    }
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> Unit

            else -> lastMessage = "Purchase failed (${result.responseCode})."
        }
    }
}
