package com.llucs.nexusai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.llucs.nexusai.data.ChatStore
import com.llucs.nexusai.data.StoredChat
import com.llucs.nexusai.data.StoredMessage
import com.llucs.nexusai.net.PollinationsClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class ChatViewModel(
    private val store: ChatStore,
    private val strings: ChatStrings
) : ViewModel() {

    private val client = PollinationsClient()

    private val greeting = UiMessage("assistant", strings.greeting)

    private val _state = MutableStateFlow(
        ChatUiState(
            currentChatId = UUID.randomUUID().toString(),
            messages = listOf(greeting)
        )
    )
    val state: StateFlow<ChatUiState> = _state

    private var runningJob: Job? = null
    private var lastUserMessageForRetry: String? = null

    init {
        viewModelScope.launch {
            val chats = store.loadChats().sortedByDescending { it.createdAt }
            _state.value = _state.value.copy(chats = chats)
            store.upsertChat(toStoredChat(_state.value))
        }
    }

    fun setInput(v: String) {
        _state.value = _state.value.copy(input = v)
    }

    fun openHistory() {
        _state.value = _state.value.copy(historyOpen = true)
    }

    fun closeHistory() {
        _state.value = _state.value.copy(historyOpen = false)
    }

    fun newChat() {
        stop()
        val id = UUID.randomUUID().toString()
        _state.value = _state.value.copy(
            currentChatId = id,
            messages = listOf(greeting),
            input = "",
            sending = false,
            historyOpen = false
        )
        viewModelScope.launch {
            store.upsertChat(toStoredChat(_state.value))
            refreshChats()
        }
    }

    fun loadChat(id: String) {
        stop()
        val chat = _state.value.chats.firstOrNull { it.id == id } ?: return
        val ui = chat.messages.map { UiMessage(it.role, it.content) }
        _state.value = _state.value.copy(
            currentChatId = chat.id,
            messages = if (ui.isNotEmpty()) ui else listOf(greeting),
            historyOpen = false,
            input = "",
            sending = false
        )
    }

    fun deleteChat(id: String) {
        viewModelScope.launch {
            store.deleteChat(id)
            val isCurrent = id == _state.value.currentChatId
            refreshChats()
            if (isCurrent) {
                newChat()
            }
        }
    }

    fun clearAllHistory() {
        stop()
        viewModelScope.launch {
            store.clearAll()
            val id = UUID.randomUUID().toString()
            _state.value = _state.value.copy(
                chats = emptyList(),
                currentChatId = id,
                messages = listOf(greeting),
                input = "",
                sending = false,
                historyOpen = false
            )
            store.upsertChat(toStoredChat(_state.value))
            refreshChats()
        }
    }

    fun stop() {
        runningJob?.cancel()
        runningJob = null
        if (_state.value.sending) {
            val msgs = _state.value.messages.toMutableList()
            if (msgs.isNotEmpty() && msgs.last().role == "assistant" && msgs.last().isThinking) {
                msgs[msgs.lastIndex] = UiMessage("assistant", strings.interrupted)
            }
            _state.value = _state.value.copy(messages = msgs, sending = false)
            viewModelScope.launch { persist() }
        }
    }

    fun showSnackbar(message: String) {
        _state.value = _state.value.copy(snackbar = SnackbarEvent(message))
    }

    fun consumeSnackbar() {
        if (_state.value.snackbar != null) {
            _state.value = _state.value.copy(snackbar = null)
        }
    }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.sending) return

        lastUserMessageForRetry = text

        val baseMessages = buildList {
            add(UiMessage("system", strings.systemPrompt))
            addAll(_state.value.messages.filter { it.role != "system" })
            add(UiMessage("user", text))
        }

        val visible = _state.value.messages +
            UiMessage("user", text) +
            UiMessage("assistant", "", isThinking = true)

        _state.value = _state.value.copy(
            messages = visible,
            input = "",
            sending = true
        )

        runningJob = viewModelScope.launch {
            try {
                var acc = ""
                client.stream(baseMessages.map { UiMessage(it.role, it.content) }) { chunk ->
                    if (!isActive) return@stream
                    if (chunk.isBlank()) return@stream
                    acc += chunk
                    replaceLastAssistant(acc)
                }

                _state.value = _state.value.copy(sending = false)
                persist()
            } catch (e: Exception) {
                val msg = e.message ?: strings.genericError
                replaceLastAssistant(String.format(Locale.getDefault(), strings.assistantErrorTemplate, msg))
                _state.value = _state.value.copy(
                    sending = false,
                    snackbar = SnackbarEvent(
                        message = String.format(Locale.getDefault(), strings.snackFailedTemplate, msg),
                        actionLabel = strings.retryActionLabel,
                        onAction = { retry() }
                    )
                )
                persist()
            } finally {
                runningJob = null
            }
        }
    }

    private fun retry() {
        if (_state.value.sending) return
        val last = lastUserMessageForRetry ?: return
        _state.value = _state.value.copy(input = last)
        send()
    }

    private fun replaceLastAssistant(content: String) {
        val updated = _state.value.messages.toMutableList()
        if (updated.isEmpty()) return
        val lastIdx = updated.lastIndex
        if (updated[lastIdx].role != "assistant") return
        updated[lastIdx] = UiMessage("assistant", content, isThinking = false)
        _state.value = _state.value.copy(messages = updated)
    }

    private suspend fun persist() {
        store.upsertChat(toStoredChat(_state.value))
        refreshChats()
    }

    private suspend fun refreshChats() {
        val chats = store.loadChats().sortedByDescending { it.createdAt }
        _state.value = _state.value.copy(chats = chats)
    }

    private fun toStoredChat(state: ChatUiState): StoredChat {
        val now = System.currentTimeMillis()
        val msgs = state.messages.map {
            StoredMessage(
                role = it.role,
                content = it.content,
                ts = now
            )
        }
        val created = state.chats.firstOrNull { it.id == state.currentChatId }?.createdAt ?: now
        return StoredChat(id = state.currentChatId, createdAt = created, messages = msgs)
    }

    companion object {
        fun factory(store: ChatStore, strings: ChatStrings): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(store, strings) as T
                }
            }
    }
}
