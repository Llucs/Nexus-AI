package com.llucs.nexusai
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Send


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
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
    val listState = rememberLazyListState()

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

    // Auto scroll quando novas mensagens aparecem
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    androidx.compose.material3.Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            ModernHeaderBar(
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
            ) {
                // Chat messages
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    items(uiState.messages) { msg ->
                        ModernMessageBubble(
                            role = msg.role,
                            content = msg.content,
                            isThinking = msg.isThinking,
                            onCopy = if (msg.role == "assistant" && msg.content.isNotBlank()) {
                                {
                                    clipboard.setText(AnnotatedString(msg.content))
                                    vm.showSnackbar("Copiado ✓")
                                }
                            } else null
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                // Input area moderna
                ModernInputArea(
                    input = uiState.input,
                    onInputChange = vm::setInput,
                    onSend = vm::send,
                    enabled = !uiState.sending
                )
            }

            if (uiState.historyOpen) {
                ModernHistorySheet(
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
private fun ModernHeaderBar(
    onNewChat: () -> Unit,
    onOpenHistory: () -> Unit,
    onStop: () -> Unit,
    canStop: Boolean
) {
    androidx.compose.material3.Surface(
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo/Menu button (circular)
            androidx.compose.material3.Surface(
                shape = CircleShape,
                color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Title badge (pill shape)
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(20.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                androidx.compose.material3.Text(
                    "Nexus AI",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.weight(1f))

            // Action buttons (circular)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularIconButton(
                    icon = Icons.Filled.History,
                    contentDescription = "Histórico",
                    onClick = onOpenHistory
                )
                
                CircularIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Novo chat",
                    onClick = onNewChat
                )
                
                if (canStop) {
                    CircularIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Parar",
                        onClick = onStop,
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer,
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun CircularIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    containerColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = contentColor
            )
        }
    }
}

@Composable
private fun ModernMessageBubble(
    role: String,
    content: String,
    isThinking: Boolean,
    onCopy: (() -> Unit)?
) {
    val isUser = role == "user"
    val align = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isUser) {
                androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
            } else {
                androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
            ) {
                // Header com nome e botão copiar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.material3.Text(
                        text = if (isUser) "Você" else "Nexus",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isUser) {
                            androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (onCopy != null) {
                        androidx.compose.material3.IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(32.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Copiar",
                                modifier = Modifier.size(16.dp),
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Conteúdo
                if (isThinking) {
                    ModernThinkingDots()
                } else {
                    val blocks = remember(content) { splitMarkdown(content) }
                    val textColor = if (isUser) {
                        androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    blocks.forEach { b ->
        MarkdownTextBlock(
            block = b,
            modifier = Modifier.fillMaxWidth(),
            contentColor = textColor
        )
    }
}
                }
            }
        }
    }
}

@Composable
private fun ModernThinkingDots() {
    var dots by remember { mutableStateOf(".") }
    LaunchedEffect(Unit) {
        while (true) {
            dots = when (dots) {
                "." -> ".."
                ".." -> "..."
                else -> "."
            }
            delay(400)
        }
    }
    androidx.compose.material3.Text(
        "Digitando$dots",
        fontSize = 14.sp,
        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    )
}

@Composable
private fun ModernInputArea(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    androidx.compose.material3.Surface(
        tonalElevation = 3.dp,
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Campo de texto moderno com bordas arredondadas
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(24.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f)
            ) {
                androidx.compose.material3.TextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { 
                        androidx.compose.material3.Text(
                            "Pergunte ao Nexus AI",
                            fontSize = 15.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        ) 
                    },
                    enabled = enabled,
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    maxLines = 5
                )
            }

            Spacer(Modifier.width(8.dp))

            // Botão circular de enviar
            val canSend = enabled && input.trim().isNotEmpty()
            androidx.compose.material3.Surface(
                onClick = if (canSend) onSend else { {} },
                shape = CircleShape,
                color = if (canSend) {
                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                } else {
                    androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Rounded.Send,
                        contentDescription = "Enviar",
                        modifier = Modifier.size(22.dp),
                        tint = if (canSend) {
                            androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernHistorySheet(
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
        title = { 
            androidx.compose.material3.Text(
                "Histórico de Conversas",
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            if (chats.isEmpty()) {
                androidx.compose.material3.Text(
                    "Nenhum chat salvo ainda.",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn {
                    items(chats.take(30)) { c ->
                        val title = c.messages.firstOrNull { it.role == "user" }?.content
                            ?.take(45)
                            ?.ifBlank { "(sem título)" }
                            ?: "(novo chat)"

                        androidx.compose.material3.Surface(
                            onClick = { onPick(c.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (c.id == currentChatId) {
                                androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    androidx.compose.material3.Text(
                                        title,
                                        fontWeight = if (c.id == currentChatId) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp,
                                        maxLines = 1
                                    )
                                    androidx.compose.material3.Text(
                                        "${c.messages.size} mensagens",
                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                                
                                androidx.compose.material3.IconButton(
                                    onClick = { onDelete(c.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    androidx.compose.material3.Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Apagar",
                                        modifier = Modifier.size(18.dp),
                                        tint = androidx.compose.material3.MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(24.dp)
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
    val historyOpen: Boolean = false,
    val snackbar: SnackbarEvent? = null
)

class ChatViewModel(private val store: ChatStore) : ViewModel() {

    private val client = PollinationsClient()

    private val systemPrompt = """
Oi! Eu sou o Nexus, sua IA parceira de aventuras! 🚀

Regras do Nexus:
- Falo só em português brasileiro, bem tranquilo e fácil.
- Vou direto ao ponto, nada de texto gigante chato.
- Adoro ajudar com ideias, perguntas, histórias e principalmente criar imagens incríveis!
- Quando for gerar imagem, capricho na descrição pra sair perfeita.
- Se eu não souber algo, falo na boa e já penso em outra coisa legal pra gente fazer juntos.

Bora lá? 😎
""".trimIndent()

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
                // SEMPRE usar streaming (removido a opção de desativar)
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
