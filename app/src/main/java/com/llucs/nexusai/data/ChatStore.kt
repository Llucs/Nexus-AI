package com.llucs.nexusai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "nexusai")

@Serializable
data class StoredMessage(
    val role: String,
    val content: String,
    val ts: Long
)

@Serializable
data class StoredChat(
    val id: String,
    val createdAt: Long,
    val messages: List<StoredMessage>
)

class ChatStore(private val context: Context) {

    private val keyChats = stringPreferencesKey("chats_json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun loadChats(): List<StoredChat> {
        val prefs = context.dataStore.data.first()
        val raw = prefs[keyChats] ?: return emptyList()
        return try {
            json.decodeFromString<List<StoredChat>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveChats(chats: List<StoredChat>) {
        val raw = json.encodeToString(chats)
        context.dataStore.edit { p -> p[keyChats] = raw }
    }

    suspend fun upsertChat(chat: StoredChat) {
        val hasRealMessage = chat.messages.any { it.content.trim().isNotEmpty() }

        if (!hasRealMessage) {
            deleteChat(chat.id)
            return
        }

        val chats = loadChats().toMutableList()
        val idx = chats.indexOfFirst { it.id == chat.id }
        if (idx >= 0) chats[idx] = chat else chats.add(0, chat)
        saveChats(chats)
    }

    suspend fun deleteChat(id: String) {
        val chats = loadChats().filterNot { it.id == id }
        saveChats(chats)
    }

    suspend fun clearAll() {
        context.dataStore.edit { p -> p.remove(keyChats) }
    }
}