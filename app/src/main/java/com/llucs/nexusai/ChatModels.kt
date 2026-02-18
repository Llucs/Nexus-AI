package com.llucs.nexusai

data class UiMessage(
    val role: String,
    val content: String,
    val isThinking: Boolean = false,
    // When the assistant saves a memory, we show a small note under the message.
    val memorySaved: String? = null
)

data class SnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

data class ChatUiState(
    val chats: List<com.llucs.nexusai.data.StoredChat> = emptyList(),
    val currentChatId: String = "",
    val messages: List<UiMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val historyOpen: Boolean = false,
    val snackbar: SnackbarEvent? = null
)

/**
 * Strings that the ViewModel uses (so we can localize without holding an Android Context).
 */
data class ChatStrings(
    val systemPrompt: String,
    val greeting: String,
    val interrupted: String,
    val genericError: String,
    val assistantErrorTemplate: String, // e.g. "Erro: %s"
    val snackFailedTemplate: String,    // e.g. "Falhou: %s"
    val retryActionLabel: String
)
