package com.novacode.quicksharelite.data

import android.content.Context
import androidx.room.Room
import com.novacode.quicksharelite.transfer.FileStore
import com.novacode.quicksharelite.transfer.NearbyTransferManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val profileStore = ProfileStore(appContext)
    val settingsStore = SettingsStore(appContext)
    val billingManager = BillingManager(appContext, settingsStore)
    val fileStore = FileStore(appContext)
    val database = Room.databaseBuilder(appContext, AppDatabase::class.java, "quickshare.db").build()
    val transferManager = NearbyTransferManager(appContext, fileStore, database.historyDao())
}
