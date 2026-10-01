package com.novacode.quicksharelite.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore("profile")

class ProfileStore(private val context: Context) {
    private object Keys {
        val Name = stringPreferencesKey("name")
        val Avatar = intPreferencesKey("avatar")
    }

    val profile: Flow<Profile?> = context.profileDataStore.data.map { prefs: Preferences ->
        val name = prefs[Keys.Name]?.trim().orEmpty()
        if (name.isBlank()) null else Profile(name, prefs[Keys.Avatar] ?: 0)
    }

    suspend fun save(profile: Profile) {
        context.profileDataStore.edit { prefs ->
            prefs[Keys.Name] = profile.name.trim()
            prefs[Keys.Avatar] = profile.avatar
        }
    }
}
