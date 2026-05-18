package com.sovexis.mobile.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sovexis_settings")

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val ACTIVE_DID_KEY = stringPreferencesKey("active_did")
        private val THEME_KEY = stringPreferencesKey("app_theme")
        private val LANGUAGE_KEY = stringPreferencesKey("app_language")
    }

    val activeDid: Flow<String?> = context.dataStore.data.map { it[ACTIVE_DID_KEY] }
    val appTheme: Flow<String?> = context.dataStore.data.map { it[THEME_KEY] }
    val appLanguage: Flow<String?> = context.dataStore.data.map { it[LANGUAGE_KEY] }

    suspend fun setActiveDid(did: String) {
        context.dataStore.edit { it[ACTIVE_DID_KEY] = did }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[THEME_KEY] = theme }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { it[LANGUAGE_KEY] = language }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
