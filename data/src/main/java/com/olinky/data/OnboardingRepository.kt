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
        val CachedDetectedProfiles = stringPreferencesKey("cached_detected_profiles")
        val CachedRecommendedProfile = stringPreferencesKey("cached_recommended_profile")
        val ProfileDetectionTimestamp = longPreferencesKey("profile_detection_timestamp")
        val WritableImageIds = stringPreferencesKey("writable_image_ids")
        val AutoMountEnabled = booleanPreferencesKey("auto_mount_enabled")
        val DarkModeEnabled = booleanPreferencesKey("dark_mode_enabled")
    }

    val preferencesFlow: Flow<OnboardingPreferences> =
        context.onboardingDataStore.data.map { prefs ->
            OnboardingPreferences(
                rootGranted = prefs[Keys.RootGranted] ?: false,
                imageDirectory = prefs[Keys.ImageDirectory],
                usbProfileId = prefs[Keys.UsbProfileId],
                completed = prefs[Keys.Completed] ?: false,
                updatedAtEpochSeconds = prefs[Keys.LastUpdated],
                cachedDetectedProfiles = prefs[Keys.CachedDetectedProfiles]?.split(",")?.toSet() ?: emptySet(),
                cachedRecommendedProfile = prefs[Keys.CachedRecommendedProfile],
                profileDetectionTimestamp = prefs[Keys.ProfileDetectionTimestamp],
                writableImageIds = prefs[Keys.WritableImageIds]?.split(",")?.toSet() ?: emptySet(),
                autoMountEnabled = prefs[Keys.AutoMountEnabled] ?: false,
                darkModeEnabled = prefs[Keys.DarkModeEnabled] ?: false
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

    suspend fun cacheProfileDetection(detectedProfiles: Set<String>, recommendedProfile: String?) {
        updatePreferences { mutablePrefs ->
            mutablePrefs[Keys.CachedDetectedProfiles] = detectedProfiles.joinToString(",")
            if (recommendedProfile != null) {
                mutablePrefs[Keys.CachedRecommendedProfile] = recommendedProfile
            }
            mutablePrefs[Keys.ProfileDetectionTimestamp] = Instant.now().epochSecond
        }
    }

    suspend fun setWritableImages(writableIds: Set<String>) {
        updatePreferences { mutablePrefs ->
            mutablePrefs[Keys.WritableImageIds] = writableIds.joinToString(",")
        }
    }

    suspend fun setAutoMountEnabled(enabled: Boolean) {
        updatePreferences { mutablePrefs ->
            mutablePrefs[Keys.AutoMountEnabled] = enabled
        }
    }

    suspend fun setDarkModeEnabled(enabled: Boolean) {
        updatePreferences { mutablePrefs ->
            mutablePrefs[Keys.DarkModeEnabled] = enabled
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
    val updatedAtEpochSeconds: Long?,
    val cachedDetectedProfiles: Set<String> = emptySet(),
    val cachedRecommendedProfile: String? = null,
    val profileDetectionTimestamp: Long? = null,
    val writableImageIds: Set<String> = emptySet(),
    val autoMountEnabled: Boolean = false,
    val darkModeEnabled: Boolean = false
) {
    val isDetectionCacheValid: Boolean
        get() {
            val timestamp = profileDetectionTimestamp ?: return false
            val ageSeconds = Instant.now().epochSecond - timestamp
            return ageSeconds < 86400 // Cache valid for 24 hours
        }
}
