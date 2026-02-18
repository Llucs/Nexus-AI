package com.llucs.nexusai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.llucs.nexusai.data.ChatStore
import com.llucs.nexusai.data.MemoryStore
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
    private val memoryStore: MemoryStore?,
    strings: ChatStrings
) : ViewModel() {

    private val client = PollinationsClient()

    private var strings: ChatStrings = strings

    private var memoriesEnabled: Boolean = true
    private var memoryAutoSaveEnabled: Boolean = true
    // Marker that the assistant can output to save a memory (kept hidden from chat UI).
    // IMPORTANT: this must be on its OWN line, and ideally at the very end of the message.
    private val memorySaveRegex = Regex("""(?m)^[\t ]*<<\s*MEMORY_SAVE\s*:\s*(.+?)\s*>>\s*$""")

    fun updateMemorySettings(memoriesEnabled: Boolean, autoSaveEnabled: Boolean) {
        this.memoriesEnabled = memoriesEnabled
        this.memoryAutoSaveEnabled = autoSaveEnabled
    }

    
    private fun cleanMemoryText(raw: String): String {
        var t = raw.trim()

        // Remove common markdown prefixes that can leak into memories.
        t = t.replace(Regex("""^\s*#+\s*"""), "")
        t = t.replace(Regex("""^\s*[-*•]+\s*"""), "")

        // Prevent control / marker characters from leaking into stored memories.
        t = t.replace("`", "")
            .replace("<", "")
            .replace(">", "")

        // Normalize whitespace.
        t = t.replace(Regex("""\s+"""), " ").trim()
        return t
    }

    private fun stripMemoryCommands(text: String): Pair<String, List<String>> {
        if (!text.contains("MEMORY_SAVE")) return text to emptyList()

        val mems = mutableListOf<String>()
        val cleanedLines = mutableListOf<String>()

        for (line in text.lines()) {
            val m = memorySaveRegex.matchEntire(line)
            if (m != null) {
                val mem = cleanMemoryText(m.groupValues.getOrNull(1).orEmpty())
                if (mem.isNotBlank()) mems.add(mem)
            } else {
                cleanedLines.add(line)
            }
        }

        // NOTE: We only remove lines that are EXACTLY the marker line.
        // This prevents nuking real content if the model formats the line wrong.
        val cleaned = cleanedLines.joinToString("\n").trimEnd()
        return cleaned to mems.distinct()
    }


    private fun greetingMessage(): UiMessage = UiMessage("assistant", strings.greeting)

    private val _state = MutableStateFlow(
        ChatUiState(
            currentChatId = UUID.randomUUID().toString(),
            messages = listOf(greetingMessage())
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
            messages = listOf(greetingMessage()),
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
            messages = if (ui.isNotEmpty()) ui else listOf(greetingMessage()),
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
                messages = listOf(greetingMessage()),
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

        // Capture where the placeholder assistant message lives (so we update the right bubble, even if chats change).
        val chatId = _state.value.currentChatId
        val assistantIndex = _state.value.messages.lastIndex

        runningJob = viewModelScope.launch {
            try {
                var acc = ""
                client.stream(baseMessages.map { UiMessage(it.role, it.content) }) { chunk ->
                    if (!isActive) return@stream
                    if (chunk.isBlank()) return@stream
                    acc += chunk
                    // Hide any full memory markers while streaming.
                    val display = stripMemoryCommands(acc).first
                    replaceAssistantAt(chatId, assistantIndex, display)
                }

                // Final pass: remove memory marker(s) and (optionally) save them.
                val (cleaned, extracted) = stripMemoryCommands(acc)
                var savedNote: String? = null
                if (extracted.isNotEmpty() && memoriesEnabled && memoryAutoSaveEnabled && memoryStore != null) {
                    extracted.forEach { mem ->
                        runCatching { memoryStore.addMemory(mem) }
                    }
                    savedNote = extracted.joinToString(" • ")
                }
                // Update last assistant with cleaned content and a note (so UI can show “Memory saved”).
                replaceAssistantAt(chatId, assistantIndex, cleaned, memorySaved = savedNote)

                _state.value = _state.value.copy(sending = false)
                persist()
            } catch (e: Exception) {
                val msg = e.message ?: strings.genericError
                replaceAssistantAt(chatId, assistantIndex, String.format(Locale.getDefault(), strings.assistantErrorTemplate, msg))
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

    /**
     * Updates localized strings / user name hints without recreating the ViewModel.
     * This keeps chat history but ensures the system prompt and greeting are correct.
     */
    fun updateStrings(newStrings: ChatStrings) {
        strings = newStrings

        // If the chat only has the initial greeting, update it (so it can include the user's name).
        val msgs = _state.value.messages
        if (msgs.size == 1 && msgs.firstOrNull()?.role == "assistant") {
            _state.value = _state.value.copy(messages = listOf(greetingMessage()))
            viewModelScope.launch { persist() }
        }
    }

    private fun retry() {
        if (_state.value.sending) return
        val last = lastUserMessageForRetry ?: return
        _state.value = _state.value.copy(input = last)
        send()
    }

    
    private fun replaceAssistantAt(
        chatId: String,
        index: Int,
        content: String,
        memorySaved: String? = null,
        isThinking: Boolean = false
    ) {
        // Guard against race conditions when the user switches chats mid-stream.
        if (_state.value.currentChatId != chatId) return

        val updated = _state.value.messages.toMutableList()
        if (index < 0 || index >= updated.size) return
        if (updated[index].role != "assistant") return

        updated[index] = UiMessage(
            role = "assistant",
            content = content,
            isThinking = isThinking,
            memorySaved = memorySaved
        )
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
        fun factory(store: ChatStore, memoryStore: MemoryStore?, strings: ChatStrings): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(store, memoryStore, strings) as T
                }
            }
    }
}
