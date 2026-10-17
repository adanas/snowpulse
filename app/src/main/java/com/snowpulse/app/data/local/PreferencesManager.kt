package com.snowpulse.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.snowpulse.app.data.model.PhonePlacement
import com.snowpulse.app.data.model.Stance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_STANCE = stringPreferencesKey("user_stance")
        private val KEY_PHONE_PLACEMENT = stringPreferencesKey("phone_placement")
    }

    val stanceFlow: Flow<Stance> = context.dataStore.data.map { preferences ->
        val name = preferences[KEY_STANCE] ?: Stance.REGULAR.name
        runCatching { Stance.valueOf(name) }.getOrDefault(Stance.REGULAR)
    }

    val phonePlacementFlow: Flow<PhonePlacement> = context.dataStore.data.map { preferences ->
        val name = preferences[KEY_PHONE_PLACEMENT] ?: PhonePlacement.PANTS_FRONT.name
        runCatching { PhonePlacement.valueOf(name) }.getOrDefault(PhonePlacement.PANTS_FRONT)
    }

    suspend fun setStance(stance: Stance) {
        context.dataStore.edit { preferences ->
            preferences[KEY_STANCE] = stance.name
        }
    }

    suspend fun setPhonePlacement(placement: PhonePlacement) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PHONE_PLACEMENT] = placement.name
        }
    }
}
