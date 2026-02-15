@file:OptIn(ExperimentalMaterial3Api::class)

package com.llucs.nexusai
import androidx.compose.material3.ExperimentalMaterial3Api

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.llucs.nexusai.net.PollinationsClient
import com.llucs.nexusai.ui.NexusTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NexusTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChatScreen()
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val uiState by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Nexus AI", fontWeight = FontWeight.Bold)
                        Text("Llucs", style = MaterialTheme.typography.labelMedium)
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Live", style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = uiState.streaming,
                            onCheckedChange = { vm.setStreaming(it) }
                        )
                        TextButton(onClick = { vm.clear() }) {
                            Text("Limpar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(uiState.messages) { msg ->
                    MessageBubble(role = msg.role, content = msg.content)
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.input,
                    onValueChange = { vm.setInput(it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Mensagem") },
                    enabled = !uiState.sending,
                    singleLine = true
                )

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = { vm.send() },
                    enabled = !uiState.sending && uiState.input.trim().isNotEmpty()
                ) {
                    Text(if (uiState.sending) "..." else "Enviar")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(role: String, content: String) {
    val isUser = role == "user"
    val align = if (isUser) Alignment.End else Alignment.Start
    val title = if (isUser) "Você" else "IA"

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 3.dp
        ) {
            Column(modifier = Modifier.padding(12.dp).widthIn(max = 320.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

data class UiMessage(val role: String, val content: String)

data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val streaming: Boolean = true,
    val error: String? = null
)

class ChatViewModel : ViewModel() {
    private val client = PollinationsClient()
    private val systemPrompt =
        "Responda sempre em português, de forma simples, como se estivesse explicando para uma criança de 12 anos. Seja direto."

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(
        ChatUiState(
            messages = listOf(UiMessage("assistant", "Oi! Eu sou o Nexus AI. Pode perguntar."))
        )
    )
    val state: kotlinx.coroutines.flow.StateFlow<ChatUiState> = _state

    fun setInput(v: String) {
        _state.value = _state.value.copy(input = v, error = null)
    }

    fun setStreaming(v: Boolean) {
        _state.value = _state.value.copy(streaming = v, error = null)
    }

    fun clear() {
        _state.value = ChatUiState(
            messages = listOf(UiMessage("assistant", "Chat limpo. Pode perguntar de novo.")),
            streaming = _state.value.streaming
        )
    }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.sending) return

        val streaming = _state.value.streaming
        val baseMessages = buildList {
            add(UiMessage("system", systemPrompt))
            addAll(_state.value.messages.filter { it.role != "system" })
            add(UiMessage("user", text))
        }

        val visibleBefore = _state.value.messages + UiMessage("user", text) + UiMessage("assistant", "")
        _state.value = _state.value.copy(
            messages = visibleBefore,
            input = "",
            sending = true,
            error = null
        )

        viewModelScope.launch {
            try {
                if (!streaming) {
                    val answer = client.complete(baseMessages)
                    val updated = _state.value.messages.toMutableList()
                    updated[updated.lastIndex] = UiMessage("assistant", answer)
                    _state.value = _state.value.copy(messages = updated, sending = false)
                    return@launch
                }

                val updated = _state.value.messages.toMutableList()
                updated[updated.lastIndex] = UiMessage("assistant", "")
                _state.value = _state.value.copy(messages = updated)

                var acc = ""
                client.stream(baseMessages) { chunk ->
                    if (chunk.isEmpty()) return@stream
                    acc += chunk
                    val cur = _state.value.messages.toMutableList()
                    cur[cur.lastIndex] = UiMessage("assistant", acc)
                    _state.value = _state.value.copy(messages = cur)
                }

                _state.value = _state.value.copy(sending = false)
            } catch (e: Exception) {
                val cur = _state.value.messages.toMutableList()
                if (cur.isNotEmpty() && cur.last().role == "assistant" && cur.last().content.isEmpty()) {
                    cur[cur.lastIndex] = UiMessage("assistant", "Erro ao responder.")
                }
                _state.value = _state.value.copy(messages = cur, sending = false, error = (e.message ?: "Erro"))
            }
        }
    }
}
