package com.regiaobs.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.regiaobs.app.macro.DeckConfig
import com.regiaobs.app.macro.DeckPresets
import com.regiaobs.app.obs.ObsJson
import com.regiaobs.app.obs.ObsServerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

private val Context.regiaDataStore: DataStore<Preferences> by preferencesDataStore(name = "regia_obs_prefs")

@Serializable
data class AppSettings(
    val previewEnabled: Boolean = true,
    val previewFps: Int = 2,
    val previewQuality: Int = 65,
    val confirmBeforeStopLive: Boolean = true,
    val confirmBeforeStopRec: Boolean = true,
    val keepScreenOn: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val showActiveAudioOnly: Boolean = false,
    val verticalMixerFaders: Boolean = true,
    val foregroundServiceEnabled: Boolean = true
)

data class StoredAppState(
    val profiles: List<ObsServerProfile> = emptyList(),
    val lastProfileId: String? = null,
    val deckConfig: DeckConfig = DeckPresets.defaultDeckConfig(),
    val settings: AppSettings = AppSettings()
)

class AppPreferencesRepository(private val context: Context) {
    private object Keys {
        val PROFILES = stringPreferencesKey("obs_profiles_json")
        val LAST_PROFILE_ID = stringPreferencesKey("last_profile_id")
        val DECK_CONFIG = stringPreferencesKey("deck_config_json")
        val SETTINGS = stringPreferencesKey("app_settings_json")
    }

    val stateFlow: Flow<StoredAppState> = context.regiaDataStore.data.map { prefs ->
        val profiles = prefs[Keys.PROFILES]?.let { raw ->
            runCatching { ObsJson.codec.decodeFromString<List<ObsServerProfile>>(raw) }.getOrNull()
        } ?: emptyList()

        val deck = prefs[Keys.DECK_CONFIG]?.let { raw ->
            runCatching { ObsJson.codec.decodeFromString<DeckConfig>(raw) }.getOrNull()
        } ?: DeckPresets.defaultDeckConfig()

        val settings = prefs[Keys.SETTINGS]?.let { raw ->
            runCatching { ObsJson.codec.decodeFromString<AppSettings>(raw) }.getOrNull()
        } ?: AppSettings()

        StoredAppState(
            profiles = profiles,
            lastProfileId = prefs[Keys.LAST_PROFILE_ID],
            deckConfig = deck,
            settings = settings
        )
    }

    suspend fun saveProfiles(profiles: List<ObsServerProfile>, lastProfileId: String?) {
        context.regiaDataStore.edit { prefs ->
            prefs[Keys.PROFILES] = ObsJson.codec.encodeToString(profiles)
            if (lastProfileId != null) {
                prefs[Keys.LAST_PROFILE_ID] = lastProfileId
            } else {
                prefs.remove(Keys.LAST_PROFILE_ID)
            }
        }
    }

    suspend fun saveDeckConfig(config: DeckConfig) {
        context.regiaDataStore.edit { prefs ->
            prefs[Keys.DECK_CONFIG] = ObsJson.codec.encodeToString(config)
        }
    }

    suspend fun saveSettings(settings: AppSettings) {
        context.regiaDataStore.edit { prefs ->
            prefs[Keys.SETTINGS] = ObsJson.codec.encodeToString(settings)
        }
    }
}
