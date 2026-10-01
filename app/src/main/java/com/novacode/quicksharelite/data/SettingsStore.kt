package com.novacode.quicksharelite.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val Theme = stringPreferencesKey("theme")
        val AdFree = booleanPreferencesKey("ad_free")
    }
    val theme: Flow<String> = context.settingsDataStore.data.map { it[Keys.Theme] ?: "system" }
    val adFree: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.AdFree] ?: false }
    suspend fun setTheme(value: String) = context.settingsDataStore.edit { it[Keys.Theme] = value }
    suspend fun setAdFree(value: Boolean) = context.settingsDataStore.edit { it[Keys.AdFree] = value }
}
