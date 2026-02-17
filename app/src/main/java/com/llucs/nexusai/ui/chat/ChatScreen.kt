package com.llucs.nexusai.ui.chat
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.llucs.nexusai.ChatStrings
import com.llucs.nexusai.ChatViewModel
import com.llucs.nexusai.R
import com.llucs.nexusai.UiMessage
import com.llucs.nexusai.data.ChatStore
import com.llucs.nexusai.data.StoredChat
import kotlinx.coroutines.launch
import com.llucs.nexusai.MarkdownTextBlock
import com.llucs.nexusai.splitMarkdown

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    store: ChatStore,
    userName: String,
    languageCode: String,
    onEditName: () -> Unit,
    onChangeLanguage: (String) -> Unit,
    sourceUrl: String = "https://github.com/Llucs/Nexus-AI"
) {
    val context = LocalContext.current

    val locale = languageCode.lowercase()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    val navLetter = userName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "N"
val displayName = userName.trim().ifBlank { if (locale == "pt") "você" else "there" }

val systemPrompt = if (locale == "pt") {
    """
    Oi! Eu sou o Nexus, seu parceiro de aventuras com IA.

    Nome do usuário: ${displayName}. Sempre chame o usuário de "${displayName}".

    Regras do Nexus:
    - Fale claro e simples.
    - Vá direto ao ponto.
    - Use Markdown bem formatado quando ajudar:
      - Use linhas em branco entre parágrafos.
      - Em listas, coloque cada item em uma linha.
      - Para separador, use uma linha só com: ---
      - Para código, use blocos com ``` e linguagem (se souber).
    - Se eu não souber algo, eu vou falar e a gente encontra uma alternativa.

    Chame o usuário de: ${displayName}.
    """.trimIndent()
} else {
    """
    Hi! I'm Nexus, your AI adventure buddy.

    User name: ${displayName}. Always address the user as "${displayName}".

    Nexus rules:
    - Speak clearly and keep things simple.
    - Go straight to the point.
    - Use well-formatted Markdown when helpful:
      - Leave blank lines between paragraphs.
      - In lists, keep one item per line.
      - For a divider, use a line containing only: ---
      - For code, use fenced blocks with ``` and language (if known).
    - If I don't know something, I'll say so and we’ll find an alternative.

    Call the user: ${displayName}.
    """.trimIndent()
}

val greeting = if (locale == "pt") {
    if (userName.isBlank()) "Oi! Eu sou o Nexus AI. Pode perguntar qualquer coisa." else "Oi, ${userName.trim()}! Eu sou o Nexus AI. Pode perguntar qualquer coisa."
} else {
    if (userName.isBlank()) "Hi! I'm Nexus AI. Ask me anything." else "Hi, ${userName.trim()}! I'm Nexus AI. Ask me anything."
}

    val interrupted = stringResource(R.string.msg_interrupted)
    val genericError = stringResource(R.string.generic_error)
    val assistantErrTemplate = stringResource(R.string.assistant_error_template)
    val snackFailedTemplate = stringResource(R.string.snack_failed_template)
    val retryAction = stringResource(R.string.snack_retry)

    val vm: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(
            store = store,
            strings = ChatStrings(
                systemPrompt = systemPrompt,
                greeting = greeting,
                interrupted = interrupted,
                genericError = genericError,
                assistantErrorTemplate = assistantErrTemplate,
                snackFailedTemplate = snackFailedTemplate,
                retryActionLabel = retryAction
            )
        )
    )

    val uiState by vm.state.collectAsState()

    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val copiedText = stringResource(R.string.snack_copied)

    // Snackbar events (from ViewModel)
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

    // Auto scroll only when user is already near the bottom
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isEmpty()) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = listState.layoutInfo.totalItemsCount
        val nearBottom = total == 0 || lastVisible >= total - 3
        if (nearBottom) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total <= 0) return@derivedStateOf false
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible < total - 3
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            NexusTopBar(
                sending = uiState.sending,
                navLetter = navLetter,
                onOpenSettings = { showSettings = true },
                onHistory = vm::openHistory,
                onNewChat = vm::newChat,
                onStop = vm::stop,
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NexusInputBar(
                input = uiState.input,
                enabled = !uiState.sending,
                onInputChange = vm::setInput,
                onSend = vm::send
            )
        }
    ) { padding ->
        val bg = Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.background
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(uiState.messages) { index, msg ->
                    val prevRole = uiState.messages.getOrNull(index - 1)?.role
                    val showMeta = prevRole != msg.role

                    MessageBubble(
                            userLetter = navLetter,
                            userName = displayName,
                            message = msg,
                        showMeta = showMeta,
                        onCopy = if (msg.role == "assistant" && msg.content.isNotBlank() && !msg.isThinking) {
                            {
                                clipboard.setText(AnnotatedString(msg.content))
                                vm.showSnackbar(copiedText)
                            }
                        } else null,
                        onLongCopy = if (msg.role == "assistant" && msg.content.isNotBlank() && !msg.isThinking) {
                            {
                                clipboard.setText(AnnotatedString(msg.content))
                                vm.showSnackbar(copiedText)
                            }
                        } else null
                    )
                }
            }

            AnimatedVisibility(
                visible = showScrollToBottom,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 96.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        scope.launch { listState.animateScrollToItem(uiState.messages.lastIndex) }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = null)
                }
            }

            if (uiState.historyOpen) {
                HistoryBottomSheet(
                    chats = uiState.chats,
                    currentChatId = uiState.currentChatId,
                    onClose = vm::closeHistory,
                    onPick = vm::loadChat,
                    onDelete = vm::deleteChat,
                    onClearAll = vm::clearAllHistory
                )
            }
        }
    }
if (showSettings) {
    SettingsBottomSheet(
        userName = userName,
        languageCode = locale,
        onEditName = {
            showSettings = false
            onEditName()
        },
        onChangeLanguage = { code ->
            showSettings = false
            onChangeLanguage(code)
        },
        sourceUrl = sourceUrl,
        onDismiss = { showSettings = false }
    )
}

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NexusTopBar(
    sending: Boolean,
    navLetter: String,
    onOpenSettings: () -> Unit,
    onHistory: () -> Unit,
    onNewChat: () -> Unit,
    onStop: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior
) {
    CenterAlignedTopAppBar(
        modifier = Modifier.statusBarsPadding(),
        scrollBehavior = scrollBehavior,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.topbar_title),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.topbar_subtitle),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = { BrandDot(letter = navLetter, onClick = onOpenSettings) },
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onHistory) {
                    Icon(Icons.Filled.History, contentDescription = stringResource(R.string.action_history))
                }
                FilledTonalIconButton(onClick = onNewChat) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_new_chat))
                }
                AnimatedVisibility(visible = sending) {
                    FilledTonalIconButton(
                        onClick = onStop,
                        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_stop))
                    }
                }
                Spacer(Modifier.width(4.dp))
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun BrandDot(letter: String, onClick: () -> Unit) {
    val shape = CircleShape
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary
        )
    )
    Box(
        modifier = Modifier
            .padding(start = 16.dp)
            .size(36.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

private fun cleanAssistantText(s: String): String {
    return s
        .replace("\\n", "\n")
        .replace("\r\n", "\n")
        .replace(Regex("""\*\*(.*?)\*\*"""), "$1")
        .replace(Regex("""`{1,3}"""), "")
        .replace(Regex("""^#{1,6}\s*""", RegexOption.MULTILINE), "")
        .replace(Regex("""\\\(|\\\)"""), "")
        .replace(Regex("""\\\[|\\\]"""), "")
        .trim()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    userLetter: String,
    userName: String,
    message: UiMessage,
    showMeta: Boolean,
    onCopy: (() -> Unit)?,
    onLongCopy: (() -> Unit)?
) {
    val isUser = message.role == "user"
    val align = if (isUser) Alignment.End else Alignment.Start

    val bubbleColor = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val bubbleContent = when {
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val avatarBg = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val avatarFg = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
        if (showMeta) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!isUser) {
                    AvatarDot(bg = avatarBg, fg = avatarFg, letter = "N")
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (isUser) userName else stringResource(R.string.label_assistant),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isUser) {
                    Spacer(Modifier.width(8.dp))
                    AvatarDot(bg = avatarBg, fg = avatarFg, letter = userLetter)
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Surface(
            color = bubbleColor,
            contentColor = bubbleContent,
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 1.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onLongCopy?.invoke() }
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {

                if (!isUser && onCopy != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.label_assistant),
                            style = MaterialTheme.typography.labelSmall,
                            color = bubbleContent.copy(alpha = 0.75f)
                        )
                        IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.action_copy),
                                tint = bubbleContent.copy(alpha = 0.75f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                if (message.isThinking) {
                    TypingIndicator(
                        labelColor = bubbleContent.copy(alpha = 0.8f),
                        dotColor = bubbleContent.copy(alpha = 0.8f)
                    )
                } else {
                    val content = message.content.replace("\\n", "\n")
                    val blocks = remember(content) { splitMarkdown(content) }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        blocks.forEach { b ->
                            MarkdownTextBlock(
                                block = b,
                                modifier = Modifier.fillMaxWidth(),
                                contentColor = bubbleContent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarDot(bg: Color, fg: Color, letter: String) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(text = letter, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
private fun TypingIndicator(labelColor: Color, dotColor: Color) {
    val infinite = rememberInfiniteTransition(label = "typing")
    val a1 by infinite.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "a1"
    )
    val a2 by infinite.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, delayMillis = 140), RepeatMode.Reverse),
        label = "a2"
    )
    val a3 by infinite.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, delayMillis = 280), RepeatMode.Reverse),
        label = "a3"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.label_typing),
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor
        )
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Dot(alpha = a1, color = dotColor)
            Dot(alpha = a2, color = dotColor)
            Dot(alpha = a3, color = dotColor)
        }
    }
}

@Composable
private fun Dot(alpha: Float, color: Color) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun NexusInputBar(
    input: String,
    enabled: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val canSend = enabled && input.trim().isNotEmpty()

    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                placeholder = { Text(stringResource(R.string.input_placeholder)) },                shape = RoundedCornerShape(26.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                maxLines = 6,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
            )

            Spacer(Modifier.width(10.dp))

            FloatingActionButton(
                onClick = { if (canSend) onSend() },
                containerColor = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.input_send))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryBottomSheet(
    chats: List<StoredChat>,
    currentChatId: String,
    onClose: () -> Unit,
    onPick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    val filtered = remember(chats, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) chats
        else chats.filter { c ->
            c.messages.any { it.content.lowercase().contains(q) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {

            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.history_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            Spacer(Modifier.height(10.dp))

            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(filtered.take(60), key = { _, c -> c.id }) { _, c ->
                        val title = c.messages.firstOrNull { it.role == "user" }?.content
                            ?.trim()
                            ?.take(50)
                            ?.ifBlank { stringResource(R.string.history_untitled) }
                            ?: stringResource(R.string.history_new_chat)

                        val isCurrent = c.id == currentChatId

                        Surface(
                            onClick = { onPick(c.id) },
                            shape = RoundedCornerShape(18.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = stringResource(R.string.history_messages_count, c.messages.size),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    IconButton(onClick = { onDelete(c.id) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.history_delete_chat),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.history_close))
                }
                TextButton(
                    onClick = { confirmClear = true },
                    enabled = chats.isNotEmpty()
                ) {
                    Text(stringResource(R.string.history_clear_all))
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.confirm_clear_title)) },
            text = { Text(stringResource(R.string.confirm_clear_text)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearAll() }) {
                    Text(stringResource(R.string.confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.confirm_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsBottomSheet(
    userName: String,
    languageCode: String,
    onEditName: () -> Unit,
    onChangeLanguage: (String) -> Unit,
    sourceUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) {
        sheetState.show()
    }
    val scope = rememberCoroutineScope()

    fun closeThen(action: () -> Unit = {}) {
        scope.launch {
            runCatching { sheetState.hide() }
            onDismiss()
            action()
        }
    }
    val versionName = remember { getAppVersionName(context) }
    val displayName = userName.trim().ifBlank { "Nexus" }

    ModalBottomSheet(
        onDismissRequest = { closeThen() },
        sheetState = sheetState,
) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Nexus",
                    modifier = Modifier.size(56.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Nexus",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "feito por Llucs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(onClick = { closeThen() }) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Fechar")
                }
            }

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Nome") },
                supportingContent = { Text(displayName) },
                modifier = Modifier.clickable { closeThen(onEditName) }
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Idioma",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))

                LanguageOption(
                    title = "Português",
                    selected = languageCode.lowercase() == "pt",
                    onClick = { closeThen { onChangeLanguage("pt") } }
                )
                LanguageOption(
                    title = "English",
                    selected = languageCode.lowercase() == "en",
                    onClick = { closeThen { onChangeLanguage("en") } }
                )
            }

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Versão") },
                supportingContent = { Text(versionName) }
            )

            TextButton(
                onClick = {
                    closeThen { openUrlSafely(context, sourceUrl) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Código fonte")
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun LanguageOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Suppress("DEPRECATION")
private fun getAppVersionName(context: Context): String {
    return try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }
}

private fun openUrlSafely(context: Context, url: String) {
    val u = url.trim()
    if (u.isBlank()) return

    val uri = runCatching { Uri.parse(u) }.getOrNull() ?: return

    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching {
        context.startActivity(intent)
    }
}