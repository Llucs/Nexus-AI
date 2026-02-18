package com.llucs.nexusai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Context.memoryDataStore by preferencesDataStore(name = "nexusai_memory")

class MemoryStore(private val context: Context) {

    private val keyMemories = stringPreferencesKey("memories_json")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun loadMemories(): List<String> {
        val prefs = context.memoryDataStore.data.first()
        val raw = prefs[keyMemories] ?: return emptyList()
        return try {
            json.decodeFromString<List<String>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveMemories(memories: List<String>) {
        val cleaned = memories
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        val raw = json.encodeToString(cleaned)
        context.memoryDataStore.edit { p -> p[keyMemories] = raw }
    }

    suspend fun addMemory(text: String, maxItems: Int = 60, maxLen: Int = 240) {
        val clean = text.trim().replace("\n", " ")
        if (clean.isBlank()) return
        val clipped = if (clean.length > maxLen) clean.take(maxLen).trimEnd() else clean

        val current = loadMemories().toMutableList()
        // de-dup (case-insensitive)
        current.removeAll { it.equals(clipped, ignoreCase = true) }
        current.add(0, clipped)

        // limit total items
        val limited = current.take(maxItems)
        saveMemories(limited)
    }

    suspend fun removeAt(index: Int) {
        val current = loadMemories().toMutableList()
        if (index < 0 || index >= current.size) return
        current.removeAt(index)
        saveMemories(current)
    }

    suspend fun clearAll() {
        context.memoryDataStore.edit { p -> p.remove(keyMemories) }
    }
}
