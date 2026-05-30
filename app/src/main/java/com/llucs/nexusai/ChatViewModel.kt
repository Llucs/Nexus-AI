package com.llucs.nexusai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.llucs.nexusai.data.ChatStore
import com.llucs.nexusai.data.MemoryStore
import com.llucs.nexusai.data.StoredChat
import com.llucs.nexusai.data.StoredMessage
import com.llucs.nexusai.files.FileTransfer
import com.llucs.nexusai.net.ApiClient
import com.llucs.nexusai.planning.Plan
import com.llucs.nexusai.planning.PlanningStore
import com.llucs.nexusai.planning.Task
import com.llucs.nexusai.planning.TaskStatus
import com.llucs.nexusai.terminal.ProotDistro
import com.llucs.nexusai.terminal.TerminalSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.Locale
import java.util.UUID

class ChatViewModel(
    private val store: ChatStore,
    private val memoryStore: MemoryStore?,
    private val planningStore: PlanningStore?,
    private val fileTransfer: FileTransfer?,
    private val terminalSession: TerminalSession?,
    private val prootDistro: ProotDistro?,
    strings: ChatStrings
) : ViewModel() {

    private val client = ApiClient()
    private var strings: ChatStrings = strings
    private var memoriesEnabled: Boolean = true
    private var memoryAutoSaveEnabled: Boolean = true
    private var pendingMemorySavedNote: String? = null
    private val memorySaveRegex = Regex("(?m)^[\\t ]*<<\\s*MEMORY_SAVE\\s*:\\s*(.+?)\\s*>>\\s*$")
    private val memoryInlineRegex = Regex("<<\\s*MEMORY_SAVE\\s*:\\s*(.+?)\\s*>>")

    private var aiTerminalEnabled: Boolean = false
    private var aiFileAccessEnabled: Boolean = false
    private var activePlan: Plan? = null
    private val planParseRegex = Regex(
        "<<\\s*PLAN\\s*:\\s*title=(.+?);goal=(.*?);tasks=(.+?)\\s*>>",
        RegexOption.DOT_MATCHES_ALL
    )
    private val taskChecklistLine = Regex("^[-*]\\s*\\[([ xX])\\]\\s*(.+)")
    private val fileSendRequest = Regex(
        "<<\\s*FILE_SEND\\s*:\\s*name=(.+?);content=(.*?)\\s*>>",
        RegexOption.DOT_MATCHES_ALL
    )
    private val terminalExecRequest = Regex(
        "<<\\s*TERMINAL_EXEC\\s*:\\s*cmd=(.+?)(?:;timeout=(\\d+))?(?:;proot=(true|false))?\\s*>>",
        RegexOption.DOT_MATCHES_ALL
    )
    private val taskDoneRequest = Regex("<<\\s*TASK_DONE\\s*:\\s*(.+?)\\s*>>")
    private val planUpdateRegex = Regex(
        "<<\\s*PLAN_UPDATE\\s*:\\s*title=(.+?);goal=(.*?);tasks=(.+?)\\s*>>",
        RegexOption.DOT_MATCHES_ALL
    )

    fun updateMemorySettings(memoriesEnabled: Boolean, autoSaveEnabled: Boolean) {
        this.memoriesEnabled = memoriesEnabled
        this.memoryAutoSaveEnabled = autoSaveEnabled
    }

    fun updateTerminalSettings(enabled: Boolean) {
        aiTerminalEnabled = enabled
        _state.value = _state.value.copy(terminalEnabled = enabled)
    }

    fun updateFileAccessSettings(enabled: Boolean) {
        aiFileAccessEnabled = enabled
    }

    fun toggleTerminal() {
        _state.value = _state.value.copy(terminalOpen = !_state.value.terminalOpen)
    }

    fun togglePlanning() {
        _state.value = _state.value.copy(planningOpen = !_state.value.planningOpen)
    }

    fun setProotInstalling(installing: Boolean) {
        _state.value = _state.value.copy(prootInstalling = installing)
    }

    suspend fun executeAiCommand(command: String, useProot: Boolean = false): String {
        if (!aiTerminalEnabled) return "Terminal access disabled"
        if (terminalSession == null && !useProot) return "Terminal not available"
        if (!useProot && !terminalSession!!.isRunning) {
            val started = terminalSession!!.start()
            if (!started) return "Failed to start terminal"
        }
        return if (useProot) {
            if (prootDistro == null) {
                "ERROR: Ubuntu (proot) is not available in this app build"
            } else {
                val status = prootDistro.checkStatus()
                if (status != ProotStatus.READY) {
                    "ERROR: Ubuntu (proot) is not installed. Status: $status. The user needs to install it via the terminal panel (click the terminal icon and press Install Ubuntu) before running Ubuntu commands."
                } else {
                    prootDistro.executeCommand(command)
                }
            }
        } else {
            terminalSession!!.executeCommand(command)
        }
    }

    fun loadActivePlan() {
        viewModelScope.launch {
            val plan = planningStore?.getActivePlan()
            activePlan = plan
            _state.value = _state.value.copy(activePlan = plan)
        }
    }

    fun refreshPlans() {
        viewModelScope.launch {
            val plan = planningStore?.getActivePlan()
            activePlan = plan
            _state.value = _state.value.copy(activePlan = plan)
        }
    }

    fun toggleTask(planId: String, taskId: String, newStatus: TaskStatus) {
        viewModelScope.launch {
            planningStore?.updateTaskStatus(planId, taskId, newStatus)
            loadActivePlan()
        }
    }

    fun deletePlan(planId: String) {
        viewModelScope.launch {
            planningStore?.deletePlan(planId)
            loadActivePlan()
        }
    }

    fun deleteTask(planId: String, taskId: String) {
        viewModelScope.launch {
            planningStore?.let { store ->
                val plans = store.loadPlans()
                val plan = plans.firstOrNull { it.id == planId } ?: return@launch
                val filteredTasks = plan.tasks.filter { it.id != taskId }
                store.upsertPlan(plan.copy(tasks = filteredTasks))
                loadActivePlan()
            }
        }
    }

    fun createPlan(title: String, goal: String) {
        viewModelScope.launch {
            val plan = Plan(title = title, goal = goal)
            planningStore?.upsertPlan(plan)
            loadActivePlan()
        }
    }

    private fun containsSimulatedCommandExecution(text: String): Boolean {
        if (!aiTerminalEnabled) return false
        if (terminalExecRequest.containsMatchIn(text)) return false
        val patterns = listOf(
            Regex("(?i)(run(ning)?\\s+command|execute?\\s+command|ran\\s+[`'\"][\\w/.-]+)"),
            Regex("(?i)(the\\s+output\\s+(was|is|showed|returned))"),
            Regex("(?i)(i\\s+(ran|executed|ran\\s+the\\s+following|installed\\s+using\\s+terminal))"),
            Regex("(?i)(command\\s+output\\s*:?\\s*[\"'`]?\\w)"),
            Regex("(?ms)```(?:bash|sh|shell)\\s+.*?```"),
        )
        val cleaned = text.replace(terminalExecRequest, "")
        return patterns.any { it.containsMatchIn(cleaned) }
    }

    private suspend fun handleAiCommands(content: String): String {
        if (!aiFileAccessEnabled && !aiTerminalEnabled && planningStore == null) return content

        val replacements = mutableListOf<Pair<Pair<Int, Int>, String>>()

        if (aiFileAccessEnabled && fileTransfer != null) {
            for (m in fileSendRequest.findAll(content)) {
                val name = m.groupValues[1].trim()
                val fileContent = m.groupValues[2].trim()
                val result = try {
                    val file = fileTransfer.saveGeneratedFile(name, fileContent)
                    _state.value = _state.value.copy(generatedFilesCount = _state.value.generatedFilesCount + 1)
                    "\nFile saved: ${file.name}"
                } catch (e: Exception) {
                    "\nFile save error: ${e.message}"
                }
                replacements.add(m.range.first to (m.range.last - m.range.first + 1) to result)
            }

            val shareRegex = Regex("<<\\s*FILE_SHARE\\s*:\\s*name=(.+?)\\s*>>")
            for (m in shareRegex.findAll(content)) {
                val name = m.groupValues[1].trim()
                val files = fileTransfer.listGeneratedFiles()
                val file = files.firstOrNull { it.name == name }
                val result = if (file != null) {
                    fileTransfer.shareFile(file)
                    "\nFile shared: $name"
                } else {
                    "\nFile not found: $name"
                }
                replacements.add(m.range.first to (m.range.last - m.range.first + 1) to result)
            }
        }

        if (aiTerminalEnabled && terminalSession != null) {
            val terminalCache = mutableMapOf<String, String>()
            for (m in terminalExecRequest.findAll(content)) {
                val cmd = m.groupValues[1].trim().replace("\\n", "\n")
                val timeout = m.groupValues[2].toLongOrNull() ?: 30000
                val useProot = m.groupValues[3].toBoolean()
                val key = "$cmd|$timeout|$useProot"
                val result = terminalCache.getOrPut(key) {
                    try {
                        if (useProot) {
                            if (prootDistro == null) {
                                "ERROR: Ubuntu (proot) not available in this build"
                            } else {
                                val status = prootDistro.checkStatus()
                                if (status != com.llucs.nexusai.terminal.ProotStatus.READY) {
                                    "ERROR: Ubuntu (proot) not installed (status=$status). User must install it first via terminal panel."
                                } else {
                                    prootDistro.executeCommand(cmd)
                                }
                            }
                        } else {
                            terminalSession.executeCommand(cmd, timeout)
                        }
                    } catch (e: Exception) {
                        "Command error: ${e.message}"
                    }
                }
                replacements.add(m.range.first to (m.range.last - m.range.first + 1) to "\nCommand output:\n$result")
            }
        }

        if (planningStore != null) {
            for (m in planParseRegex.findAll(content)) {
                val title = m.groupValues[1].trim()
                val goal = m.groupValues[2].trim()
                val tasksRaw = m.groupValues[3].trim()
                val taskList = tasksRaw.split("|").map { it.trim() }.filter { it.isNotBlank() }
                val tasks = taskList.map { Task(description = it) }
                val plan = Plan(title = title, goal = goal, tasks = tasks)
                planningStore.upsertPlan(plan)
                replacements.add(m.range.first to (m.range.last - m.range.first + 1) to "\nPlan created: $title")
            }

            for (m in planUpdateRegex.findAll(content)) {
                val title = m.groupValues[1].trim()
                val goal = m.groupValues[2].trim()
                val tasksRaw = m.groupValues[3].trim()
                val taskList = tasksRaw.split("|").map { it.trim() }.filter { it.isNotBlank() }
                val existing = planningStore.getActivePlan()
                val tasks = taskList.map { Task(description = it) }
                val plan = (existing?.copy(title = title, goal = goal, tasks = tasks, updatedAt = System.currentTimeMillis())
                    ?: Plan(title = title, goal = goal, tasks = tasks))
                planningStore.upsertPlan(plan)
                replacements.add(m.range.first to (m.range.last - m.range.first + 1) to "\nPlan updated: $title")
            }

            for (m in taskDoneRequest.findAll(content)) {
                val desc = m.groupValues[1].trim()
                val existing = planningStore.getActivePlan()
                if (existing != null) {
                    val task = existing.tasks.firstOrNull {
                        it.description.contains(desc, ignoreCase = true)
                    }
                    if (task != null) {
                        planningStore.updateTaskStatus(existing.id, task.id, TaskStatus.COMPLETED)
                        replacements.add(m.range.first to (m.range.last - m.range.first + 1) to "\nTask completed: ${task.description}")
                    } else {
                        replacements.add(m.range.first to (m.range.last - m.range.first + 1) to "\nTask not found: $desc")
                    }
                }
            }

            loadActivePlan()
        }

        if (replacements.isEmpty()) return content

        replacements.sortByDescending { (range, _) -> range.first }
        val sb = StringBuilder(content)
        for ((range, replacement) in replacements) {
            val (start, len) = range
            sb.replace(start, start + len, replacement)
        }
        return sb.toString()
    }

    private fun cleanMemoryText(raw: String): String {
        var t = raw.trim()
        t = t.replace(Regex("^\\s*#+\\s*"), "")
        t = t.replace(Regex("^\\s*[-*•]+\\s*"), "")
        t = t.replace("`", "").replace("<", "").replace(">", "")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private fun extractPersonalMemoriesFromUser(userText: String): List<String> {
        val t = userText.trim()
        if (t.isBlank()) return emptyList()
        val out = mutableListOf<String>()

        Regex("\\b(eu\\s+tenho|tenho)\\s+(\\d{1,3})\\s+anos\\b", RegexOption.IGNORE_CASE)
            .find(t)?.let { m ->
                val age = m.groupValues.getOrNull(2).orEmpty()
                age.toIntOrNull()?.let { a ->
                    if (a in 3..120) out.add("O usuário tem $a anos.")
                }
            }

        Regex("\\bmeu\\s+nome\\s+(\u00e9|eh)\\s+([\\p{L}][\\p{L}\\s.\\'-]{1,40})", RegexOption.IGNORE_CASE)
            .find(t)?.let { m ->
                val name = m.groupValues.getOrNull(2).orEmpty().trim()
                if (name.isNotBlank()) out.add("O usu\u00e1rio se chama $name.")
            }

        Regex("\\b(eu\\s+)?moro\\s+em\\s+([^\\n,.]{2,60})", RegexOption.IGNORE_CASE)
            .find(t)?.let { m ->
                val loc = m.groupValues.getOrNull(2).orEmpty().trim()
                if (loc.isNotBlank()) out.add("O usu\u00e1rio mora em $loc.")
            }

        return out.map { cleanMemoryText(it) }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
    }

    private fun stripMemoryCommands(text: String): Pair<String, List<String>> {
        if (!text.contains("MEMORY_SAVE")) return text to emptyList()
        val mems = mutableListOf<String>()

        memoryInlineRegex.findAll(text).forEach { m ->
            val mem = cleanMemoryText(m.groupValues.getOrNull(1).orEmpty())
            if (mem.isNotBlank()) mems.add(mem)
        }

        for (line in text.lines()) {
            val m = memorySaveRegex.matchEntire(line)
            if (m != null) {
                val mem = cleanMemoryText(m.groupValues.getOrNull(1).orEmpty())
                if (mem.isNotBlank()) mems.add(mem)
            }
        }

        var cleaned = text.replace(memoryInlineRegex, "")
        cleaned = cleaned.lines().filter { memorySaveRegex.matchEntire(it) == null }.joinToString("\n")
        cleaned = cleaned.replace(Regex("[ \t]+\n"), "\n").replace(Regex("\n{3,}"), "\n\n").trimEnd()

        return cleaned to mems.distinctBy { it.lowercase() }
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

    fun setInput(v: String) { _state.value = _state.value.copy(input = v) }
    fun openHistory() { _state.value = _state.value.copy(historyOpen = true) }
    fun closeHistory() { _state.value = _state.value.copy(historyOpen = false) }

    fun newChat() {
        stop()
        val id = UUID.randomUUID().toString()
        _state.value = _state.value.copy(
            currentChatId = id, messages = listOf(greetingMessage()),
            input = "", sending = false, historyOpen = false,
            lastTokenUsage = null, lastModelName = null
        )
        viewModelScope.launch { store.upsertChat(toStoredChat(_state.value)); refreshChats() }
    }

    fun loadChat(id: String) {
        stop()
        val chat = _state.value.chats.firstOrNull { it.id == id } ?: return
        val ui = chat.messages.map { UiMessage(it.role, it.content) }
        _state.value = _state.value.copy(
            currentChatId = chat.id,
            messages = if (ui.isNotEmpty()) ui else listOf(greetingMessage()),
            historyOpen = false, input = "", sending = false
        )
    }

    fun deleteChat(id: String) {
        viewModelScope.launch {
            store.deleteChat(id)
            val isCurrent = id == _state.value.currentChatId
            refreshChats()
            if (isCurrent) newChat()
        }
    }

    fun clearAllHistory() {
        stop()
        viewModelScope.launch {
            store.clearAll()
            val id = UUID.randomUUID().toString()
            _state.value = _state.value.copy(
                chats = emptyList(), currentChatId = id, messages = listOf(greetingMessage()),
                input = "", sending = false, historyOpen = false,
                lastTokenUsage = null, lastModelName = null
            )
            store.upsertChat(toStoredChat(_state.value))
            refreshChats()
        }
    }

    fun stop() {
        client.cancelActive()
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

    fun showSnackbar(message: String) { _state.value = _state.value.copy(snackbar = SnackbarEvent(message)) }
    fun consumeSnackbar() { if (_state.value.snackbar != null) _state.value = _state.value.copy(snackbar = null) }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.sending) return

        lastUserMessageForRetry = text
        val currentMessages = _state.value.messages

        val baseMessages = buildList {
            add(UiMessage("system", strings.systemPrompt))
            addAll(currentMessages.filter { it.role != "system" })
            add(UiMessage("user", text))
        }

        val visible = currentMessages + UiMessage("user", text) + UiMessage("assistant", "", isThinking = true)

        _state.value = _state.value.copy(messages = visible, input = "", sending = true)

        if (memoriesEnabled && memoryAutoSaveEnabled && memoryStore != null) {
            val extractedFromUser = extractPersonalMemoriesFromUser(text)
            if (extractedFromUser.isNotEmpty()) {
                viewModelScope.launch { extractedFromUser.forEach { mem -> runCatching { memoryStore.addMemory(mem) } } }
                pendingMemorySavedNote = extractedFromUser.joinToString(" • ")
            }
        }

        val chatId = _state.value.currentChatId
        val assistantIndex = visible.lastIndex

        runningJob = viewModelScope.launch {
            try {
                val response = client.complete(baseMessages.map { UiMessage(it.role, it.content) })
                val cmdProcessed = handleAiCommands(response.content)
                val (cleaned, extracted) = stripMemoryCommands(cmdProcessed)
                var savedNote: String? = null
                if (extracted.isNotEmpty() && memoriesEnabled && memoryAutoSaveEnabled && memoryStore != null) {
                    extracted.forEach { mem -> runCatching { memoryStore.addMemory(mem) } }
                    savedNote = extracted.joinToString(" • ")
                }

                val pending = pendingMemorySavedNote
                pendingMemorySavedNote = null
                val combinedNote = listOfNotNull(savedNote, pending)
                    .flatMap { it.split(" • ").map(String::trim).filter(String::isNotBlank) }
                    .distinctBy { it.lowercase() }.takeIf { it.isNotEmpty() }?.joinToString(" • ")

                replaceAssistantAt(chatId, assistantIndex, cleaned, memorySaved = combinedNote, tokenUsage = response.usage, modelName = response.modelName)
                _state.value = _state.value.copy(sending = false, lastTokenUsage = response.usage, lastModelName = response.modelName)

                if (!combinedNote.isNullOrBlank()) scheduleClearMemorySaved(chatId, assistantIndex, combinedNote)
                persist()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                val msg = e.message ?: strings.genericError
                replaceAssistantAt(chatId, assistantIndex, String.format(Locale.getDefault(), strings.assistantErrorTemplate, msg))
                _state.value = _state.value.copy(sending = false, snackbar = SnackbarEvent(
                    message = String.format(Locale.getDefault(), strings.snackFailedTemplate, msg),
                    actionLabel = strings.retryActionLabel, onAction = { retry() }
                ))
                persist()
            } finally { runningJob = null }
        }
    }

    fun updateStrings(newStrings: ChatStrings) {
        strings = newStrings
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

    private fun scheduleClearMemorySaved(chatId: String, index: Int, note: String) {
        viewModelScope.launch {
            delay(2500)
            if (_state.value.currentChatId != chatId) return@launch
            val msgs = _state.value.messages.toMutableList()
            if (index < 0 || index >= msgs.size) return@launch
            val msg = msgs[index]
            if (msg.role != "assistant" || msg.memorySaved != note) return@launch
            msgs[index] = msg.copy(memorySaved = null)
            _state.value = _state.value.copy(messages = msgs)
            persist()
        }
    }

    private fun replaceAssistantAt(chatId: String, index: Int, content: String, memorySaved: String? = null, tokenUsage: TokenUsage? = null, modelName: String? = null, isThinking: Boolean = false) {
        if (_state.value.currentChatId != chatId) return
        val updated = _state.value.messages.toMutableList()
        if (index < 0 || index >= updated.size) return
        if (updated[index].role != "assistant") return
        updated[index] = UiMessage(role = "assistant", content = content, isThinking = isThinking, memorySaved = memorySaved, tokenUsage = tokenUsage, modelName = modelName)
        _state.value = _state.value.copy(messages = updated)
    }

    private suspend fun persist() { store.upsertChat(toStoredChat(_state.value)); refreshChats() }
    private suspend fun refreshChats() { _state.value = _state.value.copy(chats = store.loadChats().sortedByDescending { it.createdAt }) }

    private fun toStoredChat(state: ChatUiState): StoredChat {
        val now = System.currentTimeMillis()
        val msgs = state.messages.map { StoredMessage(role = it.role, content = it.content, ts = now) }
        val created = state.chats.firstOrNull { it.id == state.currentChatId }?.createdAt ?: now
        return StoredChat(id = state.currentChatId, createdAt = created, messages = msgs)
    }

    companion object {
        fun factory(
            store: ChatStore,
            memoryStore: MemoryStore?,
            planningStore: PlanningStore?,
            fileTransfer: FileTransfer?,
            terminalSession: TerminalSession?,
            prootDistro: ProotDistro?,
            strings: ChatStrings
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChatViewModel(store, memoryStore, planningStore, fileTransfer, terminalSession, prootDistro, strings) as T
            }
    }
}
