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
import kotlinx.coroutines.delay
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
    private var pendingMemorySavedNote: String? = null
    // Marker that the assistant can output to save a memory (kept hidden from chat UI).
    // IMPORTANT: this must be on its OWN line, and ideally at the very end of the message.
    private val memorySaveRegex = Regex(
        """<<\s*MEMORY_SAVE\s*:\s*(.*?)\s*>>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val memorySaveStartRegex = Regex("""<<\s*MEMORY_SAVE\s*:""", RegexOption.IGNORE_CASE)

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

    private fun extractPersonalMemoriesFromUser(userText: String): List<String> {
        val t = userText.trim()
        if (t.isBlank()) return emptyList()

        val mems = mutableListOf<String>()

        // Age (Portuguese): "eu tenho 13 anos"
        Regex("""(?i)\b(eu\s+)?tenho\s+(\d{1,2})\s+anos\b""").find(t)?.let { m ->
            val age = m.groupValues.getOrNull(2)?.toIntOrNull()
            if (age != null && age in 5..120) mems.add("Usuário tem $age anos.")
        }

        // Birthday (Portuguese): "meu aniversário é dia 10 de agosto"
        Regex("""(?i)\b(meu\s+aniversa[rí]o|aniversa[rí]o)\s*(é|eh)?\s*(dia)?\s*(\d{1,2})\s*(de)?\s*([a-zçãáéíóú]+)\b""")
            .find(t)?.let { m ->
                val day = m.groupValues.getOrNull(4)?.toIntOrNull()
                val month = m.groupValues.getOrNull(6)?.trim().orEmpty()
                if (day != null && day in 1..31 && month.isNotBlank()) {
                    mems.add("Aniversário do usuário é $day de $month.")
                }
            }

        // Location hints (Portuguese): "eu moro em Natal, RN"
        Regex("""(?i)\b(eu\s+)?moro\s+em\s+([^\n\r]{3,80})""").find(t)?.let { m ->
            val loc = cleanMemoryText(m.groupValues.getOrNull(2).orEmpty())
            if (loc.isNotBlank()) mems.add("Usuário mora em $loc.")
        }

        return mems.distinct()
    }

    private fun stripMemoryCommands(text: String): Pair<String, List<String>> {
        if (!text.contains("MEMORY_SAVE", ignoreCase = true)) return text to emptyList()

        val mems = mutableListOf<String>()
        // Extract every complete marker anywhere in the text.
        val cleaned = memorySaveRegex.replace(text) { match ->
            val raw = match.groupValues.getOrNull(1).orEmpty()
            val mem = cleanMemoryText(raw)
            if (mem.isNotBlank()) mems.add(mem)
            "" // remove marker from visible text
        }

        // Tidy up whitespace/newlines after removing markers.
        val tidy = cleaned
            .replace(Regex("""[\t ]+"""), " ")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .replace(Regex("""[ ]+\n"""), "\n")
            .replace(Regex("""\n[ ]+"""), "\n")
            .trimEnd()

        return tidy to mems.distinct()
    }

    private fun stripMemoryCommandsStreaming(text: String): String {
        // Remove complete markers.
        var out = stripMemoryCommands(text).first

        // Also hide an *incomplete* marker tail while streaming (so it never flashes on screen).
        val start = memorySaveStartRegex.find(out)?.range?.first
        if (start != null) {
            val tail = out.substring(start)
            if (!tail.contains(">>")) {
                out = out.substring(0, start).trimEnd()
            }
        }
        return out
    }
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
                // Auto-save personal info from the USER message (more reliable than asking the model).
                if (memoriesEnabled && memoryAutoSaveEnabled && memoryStore != null) {
                    val userMems = extractPersonalMemoriesFromUser(text)
                    if (userMems.isNotEmpty()) {
                        userMems.forEach { mem -> runCatching { memoryStore.addMemory(mem) } }
                        pendingMemorySavedNote = userMems.joinToString(" • ")
                    }
                }

                val system = buildString {
                    append(strings.systemPrompt)
                    append("\n\nVocê está no app Nexus/Nexus AI, criado por Llucs (Leandro Lucas Mendes de Souza).")
                    if (memoriesEnabled && memoryStore != null) {
                        append("\n\nQuando o usuário disser uma informação pessoal estável (ex.: idade, preferências, aniversário, localização, nome), você PODE salvar isso como uma memória.")
                        append("\nPara salvar, inclua NO FINAL da sua resposta uma linha (somente essa linha) no formato: <<MEMORY_SAVE: ...>>")
                        append("\nNunca coloque o marcador no meio do texto. Nunca inclua markdown, números ou símbolos desnecessários dentro da memória.")
                    }
                }

                val memContext = if (memoriesEnabled && memoryStore != null) {
                    runCatching { memoryStore.loadMemories() }.getOrDefault(emptyList())
                } else emptyList()

                val requestMessages = buildList {
                    add(UiMessage("system", system))
                    if (memContext.isNotEmpty()) {
                        add(
                            UiMessage(
                                "system",
                                "Memórias salvas do usuário (use para personalizar, sem mencionar se não for relevante):\n- " +
                                    memContext.joinToString("\n- ")
                            )
                        )
                    }
                    addAll(_state.value.messages.filter { it.role != "system" })
                    add(UiMessage("user", text))
                }

                client.stream(requestMessages.map { UiMessage(it.role, it.content) }) { chunk ->
                    if (!isActive) return@stream
                    if (chunk.isBlank()) return@stream
                    acc += chunk
                    // Hide any full memory markers while streaming.
                    val display = stripMemoryCommandsStreaming(acc)
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

                val combinedNote = listOfNotNull(savedNote, pendingMemorySavedNote)
                    .joinToString(" • ")
                    .trim()
                    .ifBlank { null }
                pendingMemorySavedNote = null

                // Update last assistant with cleaned content and a temporary note (so UI can show “Memory saved”).
                replaceAssistantAt(chatId, assistantIndex, cleaned, memorySaved = combinedNote)
                if (combinedNote != null) scheduleClearMemorySaved(chatId, assistantIndex, combinedNote)

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

    
    private 
    private fun scheduleClearMemorySaved(chatId: String, index: Int, note: String) {
        viewModelScope.launch {
            delay(2500)
            if (_state.value.currentChatId != chatId) return@launch
            val updated = _state.value.messages.toMutableList()
            if (index < 0 || index >= updated.size) return@launch
            val msg = updated[index]
            if (msg.role != "assistant") return@launch
            if (msg.memorySaved != note) return@launch
            updated[index] = msg.copy(memorySaved = null)
            _state.value = _state.value.copy(messages = updated)
        }
    }

fun replaceAssistantAt(
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

        val current = updated[index]

        updated[index] = current.copy(
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
