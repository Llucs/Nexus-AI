package com.llucs.nexusai
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.llucs.nexusai.data.ChatStore
import com.llucs.nexusai.data.StoredChat
import com.llucs.nexusai.data.StoredMessage
import com.llucs.nexusai.net.PollinationsClient
import com.llucs.nexusai.ui.NexusTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = ChatStore(applicationContext)

        setContent {
            NexusTheme {
                androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize()) {
                    ChatScreen(store = store)
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(store: ChatStore) {
    val vm: ChatViewModel = viewModel(factory = ChatViewModel.factory(store))
    val uiState by vm.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    LaunchedEffect(uiState.snackbar) {
        val s = uiState.snackbar ?: return@LaunchedEffect
        val res = snackbarHostState.showSnackbar(
            message = s.message,
            actionLabel = s.actionLabel,
            withDismissAction = true
        )
        if (res == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            s.onAction?.invoke()
        }
        vm.consumeSnackbar()
    }

    androidx.compose.material3.Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            HeaderBar(
                streaming = uiState.streaming,
                onToggleStreaming = vm::setStreaming,
                onNewChat = vm::newChat,
                onOpenHistory = vm::openHistory,
                onStop = vm::stop,
                canStop = uiState.sending
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(uiState.messages) { msg ->
                        MessageBubble(
                            role = msg.role,
                            content = msg.content,
                            isThinking = msg.isThinking,
                            onCopy = if (msg.role == "assistant" && msg.content.isNotBlank()) {
                                {
                                    clipboard.setText(AnnotatedString(msg.content))
                                    vm.showSnackbar("Copiado")
                                }
                            } else null
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = uiState.input,
                        onValueChange = vm::setInput,
                        modifier = Modifier.weight(1f),
                        label = { androidx.compose.material3.Text("Mensagem") },
                        enabled = !uiState.sending,
                        singleLine = true
                    )

                    Spacer(Modifier.width(8.dp))

                    androidx.compose.material3.Button(
                        onClick = vm::send,
                        enabled = !uiState.sending && uiState.input.trim().isNotEmpty()
                    ) {
                        androidx.compose.material3.Text("Enviar")
                    }
                }
            }

            if (uiState.historyOpen) {
                HistorySheet(
                    chats = uiState.chats,
                    currentChatId = uiState.currentChatId,
                    onClose = vm::closeHistory,
                    onPick = vm::loadChat,
                    onDelete = vm::deleteChat
                )
            }
        }
    }
}

@Composable
private fun HeaderBar(
    streaming: Boolean,
    onToggleStreaming: (Boolean) -> Unit,
    onNewChat: () -> Unit,
    onOpenHistory: () -> Unit,
    onStop: () -> Unit,
    canStop: Boolean
) {
    androidx.compose.material3.Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Text("Nexus AI", fontWeight = FontWeight.Bold)
                androidx.compose.material3.Text("Llucs", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text("Live", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(6.dp))
                androidx.compose.material3.Switch(checked = streaming, onCheckedChange = onToggleStreaming)
                Spacer(Modifier.width(6.dp))

                androidx.compose.material3.IconButton(onClick = onOpenHistory) {
                    androidx.compose.material3.Icon(Icons.Filled.History, contentDescription = "Histórico")
                }
                androidx.compose.material3.IconButton(onClick = onNewChat) {
                    androidx.compose.material3.Icon(Icons.Filled.Add, contentDescription = "Novo chat")
                }
                androidx.compose.material3.IconButton(onClick = onStop, enabled = canStop) {
                    androidx.compose.material3.Icon(Icons.Filled.Close, contentDescription = "Parar")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    role: String,
    content: String,
    isThinking: Boolean,
    onCopy: (() -> Unit)?
) {
    val isUser = role == "user"
    val align = if (isUser) Alignment.End else Alignment.Start
    val title = if (isUser) "Você" else "IA"

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.material3.MaterialTheme.shapes.large,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(12.dp).widthIn(max = 340.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Text(
                        title,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (onCopy != null) {
                        androidx.compose.material3.IconButton(onClick = onCopy) {
                            androidx.compose.material3.Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar")
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))

                if (isThinking) {
                    ThinkingDots()
                } else {
                    androidx.compose.material3.Text(content, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ThinkingDots() {
    var dots by remember { mutableStateOf(".") }
    LaunchedEffect(Unit) {
        while (true) {
            dots = when (dots) {
                "." -> ".."
                ".." -> "..."
                else -> "."
            }
            delay(350)
        }
    }
    androidx.compose.material3.Text("Digitando$dots")
}

@Composable
private fun HistorySheet(
    chats: List<StoredChat>,
    currentChatId: String,
    onClose: () -> Unit,
    onPick: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onClose) {
                androidx.compose.material3.Text("Fechar")
            }
        },
        title = { androidx.compose.material3.Text("Histórico") },
        text = {
            if (chats.isEmpty()) {
                androidx.compose.material3.Text("Nenhum chat salvo ainda.")
            } else {
                Column {
                    chats.take(30).forEach { c ->
                        val title = c.messages.firstOrNull { it.role == "user" }?.content
                            ?.take(38)
                            ?.ifBlank { "(sem título)" }
                            ?: "(novo chat)"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { onPick(c.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                androidx.compose.material3.Text(
                                    if (c.id == currentChatId) "• $title" else title,
                                    fontWeight = if (c.id == currentChatId) FontWeight.Bold else FontWeight.Normal
                                )
                                androidx.compose.material3.Text(
                                    "${c.messages.size} mensagens",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                                )
                            }
                            androidx.compose.material3.IconButton(onClick = { onDelete(c.id) }) {
                                androidx.compose.material3.Icon(Icons.Filled.Delete, contentDescription = "Apagar")
                            }
                        }
                    }
                }
            }
        }
    )
}

data class UiMessage(val role: String, val content: String, val isThinking: Boolean = false)

data class SnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

data class ChatUiState(
    val chats: List<StoredChat> = emptyList(),
    val currentChatId: String = "",
    val messages: List<UiMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val streaming: Boolean = true,
    val historyOpen: Boolean = false,
    val snackbar: SnackbarEvent? = null
)

class ChatViewModel(private val store: ChatStore) : ViewModel() {

    private val client = PollinationsClient()

    private val systemPrompt =
        "Responda sempre em português, de forma simples, como se estivesse explicando para uma criança de 12 anos. Seja direto."

    private val greeting = UiMessage("assistant", "Oi! Eu sou o Nexus AI. Pode perguntar.")

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

    fun setStreaming(v: Boolean) {
        _state.value = _state.value.copy(streaming = v)
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
            refreshChats()
            if (_state.value.currentChatId == id) {
                newChat()
            }
        }
    }

    fun stop() {
        runningJob?.cancel()
        runningJob = null
        if (_state.value.sending) {
            val msgs = _state.value.messages.toMutableList()
            if (msgs.isNotEmpty() && msgs.last().role == "assistant" && msgs.last().isThinking) {
                msgs[msgs.lastIndex] = UiMessage("assistant", "Interrompido.")
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
            add(UiMessage("system", systemPrompt))
            addAll(_state.value.messages.filter { it.role != "system" })
            add(UiMessage("user", text))
        }

        val visible = _state.value.messages + UiMessage("user", text) + UiMessage("assistant", "", isThinking = true)
        _state.value = _state.value.copy(
            messages = visible,
            input = "",
            sending = true
        )

        runningJob = viewModelScope.launch {
            try {
                if (!_state.value.streaming) {
                    val answer = client.complete(baseMessages.map { UiMessage(it.role, it.content) })
                    replaceLastAssistant(answer)
                    return@launch
                }

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
                val msg = e.message ?: "Erro"
                replaceLastAssistant("Erro: $msg")
                _state.value = _state.value.copy(
                    sending = false,
                    snackbar = SnackbarEvent(
                        message = "Falhou: $msg",
                        actionLabel = "Tentar de novo",
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
        fun factory(store: ChatStore): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(store) as T
            }
        }
    }
}
