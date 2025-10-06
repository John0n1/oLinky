package com.olinky.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "onboarding_preferences"

private val Context.onboardingDataStore by preferencesDataStore(name = DATASTORE_NAME)

class OnboardingRepository(private val context: Context) {

    private object Keys {
        val RootGranted = booleanPreferencesKey("root_granted")
        val ImageDirectory = stringPreferencesKey("image_directory")
        val UsbProfileId = stringPreferencesKey("usb_profile_id")
        val Completed = booleanPreferencesKey("completed")
        val LastUpdated = longPreferencesKey("last_updated")
    }

    val preferencesFlow: Flow<OnboardingPreferences> =
        context.onboardingDataStore.data.map { prefs ->
            OnboardingPreferences(
                rootGranted = prefs[Keys.RootGranted] ?: false,
                imageDirectory = prefs[Keys.ImageDirectory],
                usbProfileId = prefs[Keys.UsbProfileId],
                completed = prefs[Keys.Completed] ?: false,
                updatedAtEpochSeconds = prefs[Keys.LastUpdated]
            )
        }

    suspend fun setRootGranted(granted: Boolean) {
        updatePreferences { mutablePrefs ->
            mutablePrefs[Keys.RootGranted] = granted
        }
    }

    suspend fun setImageDirectory(path: String) {
        updatePreferences { mutablePrefs ->
            mutablePrefs[Keys.ImageDirectory] = path
        }
    }

    suspend fun setUsbProfile(id: String) {
        updatePreferences { mutablePrefs ->
            mutablePrefs[Keys.UsbProfileId] = id
        }
    }

    suspend fun setCompleted(completed: Boolean) {
        updatePreferences { mutablePrefs ->
            mutablePrefs[Keys.Completed] = completed
        }
    }

    suspend fun clear() {
        context.onboardingDataStore.edit { it.clear() }
    }

    private suspend fun updatePreferences(block: (MutablePreferences) -> Unit) {
        context.onboardingDataStore.edit { prefs ->
            block(prefs)
            prefs[Keys.LastUpdated] = Instant.now().epochSecond
        }
    }
}

data class OnboardingPreferences(
    val rootGranted: Boolean,
    val imageDirectory: String?,
    val usbProfileId: String?,
    val completed: Boolean,
    val updatedAtEpochSeconds: Long?
)
