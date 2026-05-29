package com.llucs.nexusai

data class UiMessage(
    val role: String,
    val content: String,
    val isThinking: Boolean = false,
    val memorySaved: String? = null,
    val tokenUsage: TokenUsage? = null,
    val modelName: String? = null
)

data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
) {
    val formatted: String
        get() = "\u2191$totalTokens"
}

data class ApiResponse(
    val content: String,
    val modelName: String?,
    val usage: TokenUsage?
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
    val snackbar: SnackbarEvent? = null,
    val lastTokenUsage: TokenUsage? = null,
    val lastModelName: String? = null,
    val terminalOpen: Boolean = false,
    val terminalEnabled: Boolean = false,
    val prootInstalling: Boolean = false,
    val planningOpen: Boolean = false,
    val activePlan: com.llucs.nexusai.planning.Plan? = null,
    val generatedFilesCount: Int = 0
)

data class ChatStrings(
    val systemPrompt: String,
    val greeting: String,
    val interrupted: String,
    val genericError: String,
    val assistantErrorTemplate: String,
    val snackFailedTemplate: String,
    val retryActionLabel: String
)

data class AiFileRequest(
    val action: String,
    val filename: String,
    val content: String,
    val mimeType: String = "text/plain"
)

data class AiTerminalCommand(
    val command: String,
    val timeoutMs: Long = 30000,
    val useProot: Boolean = false
)

data class AiPlanRequest(
    val title: String,
    val goal: String,
    val tasks: List<String> = emptyList()
)
