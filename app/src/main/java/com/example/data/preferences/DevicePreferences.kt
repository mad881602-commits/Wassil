package com.example.data.preferences

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wassilDataStore: DataStore<Preferences> by preferencesDataStore(name = "wassil_preferences")

class DevicePreferences(private val context: Context) {
    companion object {
        private val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
        private val KEY_LANGUAGE = stringPreferencesKey("app_language")

        fun defaultDeviceName(): String {
            val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
            return "هاتف $model"
        }
    }

    val deviceNameFlow: Flow<String> = context.wassilDataStore.data.map { prefs ->
        prefs[KEY_DEVICE_NAME]?.takeIf { it.isNotBlank() } ?: defaultDeviceName()
    }

    val languageFlow: Flow<AppLanguage> = context.wassilDataStore.data.map { prefs ->
        when (prefs[KEY_LANGUAGE]) {
            AppLanguage.ENGLISH.code -> AppLanguage.ENGLISH
            else -> AppLanguage.ARABIC
        }
    }

    suspend fun updateDeviceName(newName: String) {
        val sanitized = newName.trim().take(40)
        if (sanitized.isNotEmpty()) {
            context.wassilDataStore.edit { prefs ->
                prefs[KEY_DEVICE_NAME] = sanitized
            }
        }
    }

    suspend fun updateLanguage(language: AppLanguage) {
        context.wassilDataStore.edit { prefs ->
            prefs[KEY_LANGUAGE] = language.code
        }
    }
}
