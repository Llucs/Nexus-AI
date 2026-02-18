package com.llucs.nexusai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.userDataStore by preferencesDataStore(name = "nexusai_user")

class UserPrefs(private val context: Context) {

    private val keyUserName = stringPreferencesKey("user_name")
    private val keyLanguage = stringPreferencesKey("language")

    private val keyMemoriesEnabled = booleanPreferencesKey("memories_enabled")
    private val keyMemoryAutoSave = booleanPreferencesKey("memories_auto_save")

    suspend fun getUserName(): String? {
        val prefs = context.userDataStore.data.first()
        return prefs[keyUserName]?.trim()?.ifBlank { null }
    }

    suspend fun setUserName(name: String) {
        val clean = name.trim()
        context.userDataStore.edit { it[keyUserName] = clean }
    }

    suspend fun getLanguage(): String? {
        val prefs = context.userDataStore.data.first()
        return prefs[keyLanguage]?.trim()?.ifBlank { null }
    }

    suspend fun setLanguage(lang: String?) {
        context.userDataStore.edit { p ->
            if (lang.isNullOrBlank()) p.remove(keyLanguage) else p[keyLanguage] = lang
        }
    }

    suspend fun getMemoriesEnabled(defaultValue: Boolean = true): Boolean {
        val prefs = context.userDataStore.data.first()
        return prefs[keyMemoriesEnabled] ?: defaultValue
    }

    suspend fun setMemoriesEnabled(enabled: Boolean) {
        context.userDataStore.edit { it[keyMemoriesEnabled] = enabled }
    }

    suspend fun getMemoryAutoSaveEnabled(defaultValue: Boolean = true): Boolean {
        val prefs = context.userDataStore.data.first()
        return prefs[keyMemoryAutoSave] ?: defaultValue
    }

    suspend fun setMemoryAutoSaveEnabled(enabled: Boolean) {
        context.userDataStore.edit { it[keyMemoryAutoSave] = enabled }
    }
}
