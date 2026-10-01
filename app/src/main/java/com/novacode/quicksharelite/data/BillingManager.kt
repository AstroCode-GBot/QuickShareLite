package com.novacode.quicksharelite.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BillingManager(private val context: Context, private val settingsStore: SettingsStore) {
    companion object { const val REMOVE_ADS_PRODUCT_ID = "remove_ads" }
    private val _available = MutableStateFlow<ProductDetails?>(null)
    val available: StateFlow<ProductDetails?> = _available
    private val _owned = MutableStateFlow(false)
    val owned: StateFlow<Boolean> = _owned

    private var client: BillingClient? = null

    fun start() {
        client = BillingClient.newBuilder(context)
            .setListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) processPurchases(purchases.orEmpty())
            }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        client?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) { if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh() }
            override fun onBillingServiceDisconnected() { }
        })
    }

    fun refreshPurchases() { refresh() }

    private fun refresh() {
        val c = client ?: return
        c.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(
                listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(REMOVE_ADS_PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build())
            ).build()
        ) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) _available.value = details.productDetailsList.firstOrNull()
        }
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) processPurchases(purchases)
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val purchase = purchases.firstOrNull { REMOVE_ADS_PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (purchase == null) return
        _owned.value = true
        // Client-side entitlement is re-read from Play on startup; the purchase is acknowledged below.
        settingsStoreSetAdFree()
        if (!purchase.isAcknowledged) client?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { }
    }

    private fun settingsStoreSetAdFree() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { settingsStore.setAdFree(true) }
    }

    fun purchase(activity: Activity): BillingResult? {
        val details = _available.value ?: return null
        val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: return null
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(offer.offerToken).build()))
            .build()
        return client?.launchBillingFlow(activity, params)
    }
}
