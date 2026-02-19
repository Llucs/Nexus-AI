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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.onDispose
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
import com.llucs.nexusai.data.UserPrefs
import com.llucs.nexusai.data.MemoryStore
import com.llucs.nexusai.data.StoredChat
import kotlinx.coroutines.launch
import com.llucs.nexusai.MarkdownTextBlock
import com.llucs.nexusai.splitMarkdown
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.core.content.ContextCompat

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    store: ChatStore,
    prefs: UserPrefs,
    memoryStore: MemoryStore,
    userName: String,
    languageCode: String,
    onEditName: () -> Unit,
    onChangeLanguage: (String) -> Unit,
    sourceUrl: String = "https://github.com/Llucs/Nexus-AI"
) {
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    var voiceMode by rememberSaveable { mutableStateOf(false) }
    var voiceState by remember { mutableStateOf(VoiceCaptureState()) }

    val voiceController = remember(context, scope) {
        VoiceInputController(context, scope) { voiceState = it }
    }

    val requestMic = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceMode = true
            voiceController.resetText()
            voiceController.start()
        }
    }

    fun startVoice() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            voiceMode = true
            voiceController.resetText()
            voiceController.start()
        } else {
            requestMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(voiceMode) {
        if (!voiceMode) voiceController.stop()
    }

    DisposableEffect(Unit) {
        onDispose { voiceController.stop() }
    }

    val locale = languageCode.lowercase()

    var memoriesEnabled by rememberSaveable { mutableStateOf(true) }
    var memoryAutoSaveEnabled by rememberSaveable { mutableStateOf(true) }
    var memories by remember { mutableStateOf<List<String>>(emptyList()) }
    var showMemoriesManager by rememberSaveable { mutableStateOf(false) }


    var showSettings by rememberSaveable { mutableStateOf(false) }
    val navLetter = userName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "N"
    val trimmedName = userName.trim()
    val hasName = trimmedName.isNotEmpty()
    val displayName = trimmedName
        val nameHint = when (locale) {
        "pt" -> if (hasName) {
            "Nome do usuário: $displayName. Você pode usar esse nome, mas só quando for natural (não em toda mensagem)."
        } else {
            "O nome do usuário ainda não foi informado. Se precisar, pergunte o nome. Não use \"você\" como nome."
        }
        "es" -> if (hasName) {
            "Nombre del usuario: $displayName. Puedes usar ese nombre, pero solo cuando sea natural (no en cada mensaje)."
        } else {
            "El nombre del usuario aún no fue informado. Si hace falta, pregunta el nombre. No uses \"tú\" como nombre."
        }
        "ru" -> if (hasName) {
            "Имя пользователя: $displayName. Можешь использовать имя, но только когда это уместно (не в каждом сообщении)."
        } else {
            "Имя пользователя ещё не указано. Если нужно, спроси имя. Не используй «вы» как имя."
        }
        else -> if (hasName) {
            "User name: $displayName. You may use it, but only when it feels natural (not in every message)."
        } else {
            "The user's name hasn't been provided yet. If needed, ask for their name. Do not use \"you\" as a name."
        }
    }


    val appIdentity = when (locale) {
        "pt" -> "Você está no app Nexus / Nexus AI. Ele foi criado por Llucs (Leandro Lucas Mendes de Souza)."
        "es" -> "Estás en la app Nexus / Nexus AI. Fue creada por Llucs (Leandro Lucas Mendes de Souza)."
        "ru" -> "Вы находитесь в приложении Nexus / Nexus AI. Оно создано Llucs (Leandro Lucas Mendes de Souza)."
        else -> "You are in the Nexus / Nexus AI app. It was created by Llucs (Leandro Lucas Mendes de Souza)."
    }

    val memoriesBlock = if (memoriesEnabled && memories.isNotEmpty()) {
        val list = memories.take(30).joinToString("\n") { "- $it" }
        when (locale) {
            "pt" -> "Memórias salvas do usuário (use só quando ajudar):\n$list"
            "es" -> "Memorias guardadas del usuario (úsalas solo cuando ayuden):\n$list"
            "ru" -> "Сохранённые воспоминания пользователя (используй только когда полезно):\n$list"
            else -> "Saved user memories (use only when helpful):\n$list"
        }
    } else if (!memoriesEnabled) {
        when (locale) {
            "pt" -> "Memórias estão DESATIVADAS: não salve memórias e não use memórias antigas."
            "es" -> "Las memorias están DESACTIVADAS: no guardes memorias ni uses memorias anteriores."
            "ru" -> "Память ОТКЛЮЧЕНА: не сохраняй и не используй старые воспоминания."
            else -> "Memories are DISABLED: don't save memories and don't use old memories."
        }
    } else ""

    val memorySaveRules = when {
        !memoriesEnabled -> ""
        memoriesEnabled && memoryAutoSaveEnabled -> when (locale) {
            "pt" -> "Quando o usuário disser algo pessoal e estável (ex.: nome, idade, cidade/país, preferências, hobbies, dispositivos, projetos, metas), você DEVE salvar como memória. No FIM da sua resposta, em uma linha separada e apenas com isso, escreva: <<MEMORY_SAVE: ...>>. Use uma frase curta (sem Markdown, sem #, sem listas). Se houver mais de 1 memória, use 1 linha por memória (máx. 2). Nunca escreva nada depois do(s) marcador(es)."
            "es" -> "Cuando el usuario diga algo personal y estable (p. ej. nombre, edad, ciudad/país, preferencias, hobbies, dispositivos, proyectos, metas), DEBES guardarlo como memoria. AL FINAL de tu respuesta, en una línea separada y solo con eso, escribe: <<MEMORY_SAVE: ...>>. Usa una frase corta (sin Markdown, sin #, sin listas). Si hay más de 1 memoria, usa 1 línea por memoria (máx. 2). No escribas nada después del/los marcador(es)."
            "ru" -> "Если пользователь говорит что-то личное и стабильное (например: имя, возраст, город/страна, предпочтения, хобби, устройства, проекты, цели), ТЫ ДОЛЖЕН сохранить это как память. В КОНЦЕ ответа, отдельной строкой и только это, напиши: <<MEMORY_SAVE: ...>>. Коротко (без Markdown, без #, без списков). Если памяти больше одной — 1 строка на память (макс. 2). После маркера(ов) ничего не добавляй."
            else -> "When the user says stable personal info (e.g., name, age, city/country, preferences, hobbies, devices, projects, goals), you MUST save it as a memory. At the END of your reply, on its own line and with nothing else, write: <<MEMORY_SAVE: ...>>. Keep it short (no Markdown, no #, no lists). If there is more than one memory, use one line per memory (max 2). Do not write anything after the marker line(s)."
        }
        else -> when (locale) {
            "pt" -> "Não use marcadores de memória (auto-salvar está desligado)."
            "es" -> "No uses marcadores de memoria (auto-guardar está desactivado)."
            "ru" -> "Не используй маркеры памяти (автосохранение выключено)."
            else -> "Don't use memory markers (auto-save is off)."
        }
    }

    val systemPrompt = when (locale) {
        "pt" -> """
            Oi! Eu sou o Nexus AI.

            $nameHint

            $appIdentity

            ${if (memoriesBlock.isNotBlank()) memoriesBlock + "\n\n" else ""}$memorySaveRules

            Regras do Nexus:
            - Fale claro e simples.
            - Vá direto ao ponto.
            - Não junte palavras, letras e números; mantenha espaçamento normal.
            - Use Markdown bem formatado quando ajudar:
              - Deixe uma linha em branco entre parágrafos.
              - Em listas, um item por linha.
              - Para separador, use uma linha só com: ---
              - Para código, use blocos com ``` e linguagem (se souber).
            - Se eu não souber algo, eu vou falar e sugerir alternativas.
        """.trimIndent()

        "es" -> """
            ¡Hola! Soy Nexus AI.

            $nameHint

            $appIdentity

            ${if (memoriesBlock.isNotBlank()) memoriesBlock + "\n\n" else ""}$memorySaveRules

            Reglas:
            - Habla claro y simple.
            - Ve directo al punto.
            - No juntes palabras, letras y números; mantén el espaciado normal.
            - Usa Markdown bien formateado cuando ayude:
              - Deja una línea en blanco entre párrafos.
              - En listas, un ítem por línea.
              - Para separar, usa una línea que tenga solo: ---
              - Para código, usa bloques con ``` y el lenguaje (si lo sabes).
            - Si no sé algo, lo diré y sugeriré alternativas.
        """.trimIndent()

        "ru" -> """
            Привет! Я Nexus AI.

            $nameHint

            $appIdentity

            ${if (memoriesBlock.isNotBlank()) memoriesBlock + "\n\n" else ""}$memorySaveRules

            Правила:
            - Пиши ясно и просто.
            - Сразу к делу.
            - Не склеивай слова, буквы и числа; сохраняй обычные пробелы.
            - Используй аккуратный Markdown, когда это помогает:
              - Оставляй пустую строку между абзацами.
              - В списках — один пункт на строку.
              - Для разделителя — строка только из: ---
              - Для кода — блоки с ``` и языком (если знаешь).
            - Если я чего-то не знаю, я скажу и предложу варианты.
        """.trimIndent()

        else -> """
            Hi! I'm Nexus AI.

            $nameHint

            $appIdentity

            ${if (memoriesBlock.isNotBlank()) memoriesBlock + "\n\n" else ""}$memorySaveRules

            Rules:
            - Speak clearly and keep it simple.
            - Go straight to the point.
            - Don't glue words, letters, and numbers together; keep normal spacing.
            - Use well-formatted Markdown when helpful:
              - Leave a blank line between paragraphs.
              - In lists, keep one item per line.
              - For a divider, use a line containing only: ---
              - For code, use fenced blocks with ``` and language (if known).
            - If I don't know something, I'll say so and suggest alternatives.
        """.trimIndent()
    }

    val finalSystemPrompt = if (hasName) {
        systemPrompt + "\n\n" + when (locale) {
            "pt" -> "Nome preferido do usuário: $displayName. Use o nome só quando for natural; não repita em toda resposta."
            "es" -> "Nombre preferido del usuario: $displayName. Usa el nombre solo cuando sea natural; no lo repitas en cada respuesta."
            "ru" -> "Предпочтительное имя пользователя: $displayName. Используй имя только когда это уместно; не повторяй в каждом ответе."
            else -> "User preferred name: $displayName. Use the name only when it feels natural; don't repeat it in every reply."
        }
    } else {
        systemPrompt
    }


val greeting = when (locale) {
        "pt" -> if (hasName) "Oi, ${displayName}! Eu sou o Nexus AI. Pode perguntar qualquer coisa." else "Oi! Eu sou o Nexus AI. Pode perguntar qualquer coisa."
        "es" -> if (hasName) "¡Hola, ${displayName}! Soy Nexus AI. Pregunta lo que quieras." else "¡Hola! Soy Nexus AI. Pregunta lo que quieras."
        "ru" -> if (hasName) "Привет, ${displayName}! Я Nexus AI. Спрашивай что угодно." else "Привет! Я Nexus AI. Спрашивай что угодно."
        else -> if (hasName) "Hi, ${displayName}! I'm Nexus AI. Ask me anything." else "Hi! I'm Nexus AI. Ask me anything."
    }

    val interrupted = stringResource(R.string.msg_interrupted)
    val genericError = stringResource(R.string.generic_error)
    val assistantErrTemplate = stringResource(R.string.assistant_error_template)
    val snackFailedTemplate = stringResource(R.string.snack_failed_template)
    val retryAction = stringResource(R.string.snack_retry)

    val vm: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(
            store = store,
            memoryStore = memoryStore,
            strings = ChatStrings(
                systemPrompt = finalSystemPrompt,
                greeting = greeting,
                interrupted = interrupted,
                genericError = genericError,
                assistantErrorTemplate = assistantErrTemplate,
                snackFailedTemplate = snackFailedTemplate,
                retryActionLabel = retryAction
            )
        )
    )

    LaunchedEffect(finalSystemPrompt, greeting, interrupted, genericError, assistantErrTemplate, snackFailedTemplate, retryAction) {
        vm.updateStrings(
            ChatStrings(
                systemPrompt = finalSystemPrompt,
                greeting = greeting,
                interrupted = interrupted,
                genericError = genericError,
                assistantErrorTemplate = assistantErrTemplate,
                snackFailedTemplate = snackFailedTemplate,
                retryActionLabel = retryAction
            )
        )
    }

    val uiState by vm.state.collectAsState()

    LaunchedEffect(memoriesEnabled, memoryAutoSaveEnabled) {
        vm.updateMemorySettings(memoriesEnabled, memoryAutoSaveEnabled)
    }

    // When the assistant saves a memory, reload the list so the settings screen stays updated.
    LaunchedEffect(uiState.messages.lastOrNull()?.memorySaved) {
        if (!uiState.messages.lastOrNull()?.memorySaved.isNullOrBlank()) {
            runCatching { memories = memoryStore.loadMemories() }
        }
    }


    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val uiScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { memoriesEnabled = prefs.getMemoriesEnabled(true) }
        runCatching { memoryAutoSaveEnabled = prefs.getMemoryAutoSaveEnabled(true) }
        runCatching { memories = memoryStore.loadMemories() }
    }



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
            if (voiceMode) {
                VoiceInputBar(
                    state = voiceState,
                    onToggleShowText = voiceController::toggleShowText,
                    onCancel = { voiceMode = false },
                    onSendText = { t ->
                        val normalized = normalizePtDictation(t)
                        if (normalized.isNotBlank()) {
                            vm.setInput(normalized)
                            voiceMode = false
                            vm.send()
                        } else {
                            voiceMode = false
                        }
                    }
                )
            } else {
                NexusInputBar(
                    input = uiState.input,
                    enabled = !uiState.sending,
                    onInputChange = vm::setInput,
                    onSend = vm::send,
                    onMic = { startVoice() }
                )
            }
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
            AnimatedVisibility(visible = uiState.messages.isEmpty()) {
                EmptySuggestions(
                    onPick = { suggestion ->
                        vm.setInput(suggestion)
                    }
                )
            }
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
                        uiScope.launch { listState.animateScrollToItem(uiState.messages.lastIndex) }
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
        memoriesEnabled = memoriesEnabled,
        memoryAutoSaveEnabled = memoryAutoSaveEnabled,
        memoriesCount = memories.size,
        onToggleMemoriesEnabled = { enabled ->
            memoriesEnabled = enabled
            uiScope.launch { prefs.setMemoriesEnabled(enabled) }
        },
        onToggleMemoryAutoSave = { enabled ->
            memoryAutoSaveEnabled = enabled
            uiScope.launch { prefs.setMemoryAutoSaveEnabled(enabled) }
        },
        onOpenMemoriesManager = {
            showMemoriesManager = true
        },
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

    if (showMemoriesManager) {
        MemoriesManagerBottomSheet(
            memories = memories,
            onAdd = { mem ->
                uiScope.launch {
                    memoryStore.addMemory(mem)
                    memories = memoryStore.loadMemories()
                }
            },
            onDeleteAt = { idx ->
                uiScope.launch {
                    memoryStore.removeAt(idx)
                    memories = memoryStore.loadMemories()
                }
            },
            onClearAll = {
                uiScope.launch {
                    memoryStore.clearAll()
                    memories = emptyList()
                }
            },
            onDismiss = { showMemoriesManager = false }
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

    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 10.dp, bottomStart = 22.dp)
    } else {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 10.dp)
    }

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
            shape = bubbleShape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), bubbleShape)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onLongCopy?.invoke() }
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {

                if (!isUser && onCopy != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.action_copy),
                                tint = bubbleContent.copy(alpha = 0.75f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
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

        if (!isUser && !message.isThinking && !message.memorySaved.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.memory_saved_template, message.memorySaved!!),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
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
    onSend: () -> Unit,
    onMic: () -> Unit
) {
    val canSend = enabled && input.trim().isNotEmpty()

    Surface(
        tonalElevation = 2.dp,
        color = Color.Transparent
    ) {
        val top = Color.Transparent
        val mid = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
        val bottom = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(top, mid, bottom)
                    )
                )
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    enabled = enabled,
                    placeholder = { Text(stringResource(R.string.input_placeholder)) },
                    shape = RoundedCornerShape(999.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    maxLines = 6,
                    leadingIcon = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(40.dp)
                        ) {
                            IconButton(
                                onClick = { if (enabled) onMic() },
                                enabled = enabled,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = stringResource(R.string.voice_start),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.9f else 0.45f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        Surface(
                            shape = CircleShape,
                            color = if (canSend) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            IconButton(
                                onClick = { if (canSend) onSend() },
                                enabled = canSend,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Send,
                                    contentDescription = stringResource(R.string.input_send),
                                    tint = if (canSend) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
    
        }
    }
}



@Composable
private fun EmptySuggestions(
    onPick: (String) -> Unit
) {
    val promptCreateImage = stringResource(R.string.prompt_create_image)
    val promptSummarize = stringResource(R.string.prompt_summarize)
    val promptSurprise = stringResource(R.string.prompt_surprise)
    val promptHelpWrite = stringResource(R.string.prompt_help_write)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 42.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.suggestions_title),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Filled.Image,
                    text = stringResource(R.string.suggestions_create_image),
                    onClick = { onPick(promptCreateImage) }
                )
                QuickActionCard(
                    icon = Icons.Filled.Summarize,
                    text = stringResource(R.string.suggestions_summarize),
                    onClick = { onPick(promptSummarize) }
                )
                QuickActionCard(
                    icon = Icons.Filled.AutoAwesome,
                    text = stringResource(R.string.suggestions_surprise),
                    onClick = { onPick(promptSurprise) }
                )
                QuickActionCard(
                    icon = Icons.Filled.Edit,
                    text = stringResource(R.string.suggestions_help_write),
                    onClick = { onPick(promptHelpWrite) }
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun VoiceInputBar(
    state: VoiceCaptureState,
    onToggleShowText: () -> Unit,
    onCancel: () -> Unit,
    onSendText: (String) -> Unit
) {
    val text = (state.partialText.ifBlank { state.finalText }).trim()

    val top = Color.Transparent
    val mid = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    val bottom = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)

    Surface(
        tonalElevation = 2.dp,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(colors = listOf(top, mid, bottom)))
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        RoundedCornerShape(22.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.showText) {
                        Text(
                            text = if (text.isBlank()) stringResource(R.string.voice_listening_hint) else text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.voice_cancel),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        AudioWaveform(
                            levels = state.levels,
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        OutlinedButton(
                            onClick = onToggleShowText,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.10f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(text = stringResource(R.string.voice_show_text))
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(46.dp)
                        ) {
                            IconButton(
                                onClick = { onSendText(text) },
                                enabled = text.isNotBlank(),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Send,
                                    contentDescription = stringResource(R.string.input_send),
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioWaveform(
    levels: List<Float>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val bars = if (levels.isEmpty()) List(24) { 0f } else levels
        for (v in bars) {
            val h = (6f + 20f * v).dp
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            )
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
    memoriesEnabled: Boolean,
    memoryAutoSaveEnabled: Boolean,
    memoriesCount: Int,
    onToggleMemoriesEnabled: (Boolean) -> Unit,
    onToggleMemoryAutoSave: (Boolean) -> Unit,
    onOpenMemoriesManager: () -> Unit,
    onEditName: () -> Unit,
    onChangeLanguage: (String) -> Unit,
    sourceUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { sheetState.show() }
    val scope = rememberCoroutineScope()


    var showLanguagePicker by remember { mutableStateOf(false) }

    fun closeSettingsThen(action: () -> Unit = {}) {
        scope.launch {
            runCatching { sheetState.hide() }
            onDismiss()
            action()
        }
    }

    val versionName = remember { getAppVersionName(context) }
    val displayName = userName.trim().ifBlank { stringResource(R.string.settings_not_set) }

    val langCode = languageCode.trim().lowercase()
    val languageLabel = when (langCode) {
        "pt" -> stringResource(R.string.language_pt)
        "en" -> stringResource(R.string.language_en)
        "es" -> stringResource(R.string.language_es)
        "ru" -> stringResource(R.string.language_ru)
        else -> stringResource(R.string.language_en)
    }

    ModalBottomSheet(
        onDismissRequest = { closeSettingsThen() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
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
                    contentDescription = null,
                    modifier = Modifier.size(56.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.topbar_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_made_by, stringResource(R.string.developer_name)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(onClick = { closeSettingsThen() }) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            HorizontalDivider()

            PillListItem(
                headline = stringResource(R.string.settings_name),
                supporting = displayName,
                onClick = { closeSettingsThen(onEditName) }
            )

            PillListItem(
                headline = stringResource(R.string.settings_language),
                supporting = languageLabel,
                trailing = {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { showLanguagePicker = true }
            )

            HorizontalDivider()

            PillListItem(
                headline = stringResource(R.string.settings_memories_title),
                supporting = if (memoriesCount > 0)
                    stringResource(R.string.memories_count_template, memoriesCount)
                else
                    stringResource(R.string.memories_empty_short),
                trailing = {
                    Switch(
                        checked = memoriesEnabled,
                        onCheckedChange = onToggleMemoriesEnabled
                    )
                },
                onClick = { onToggleMemoriesEnabled(!memoriesEnabled) }
            )

            PillListItem(
                headline = stringResource(R.string.settings_memories_auto_save),
                supporting = stringResource(R.string.settings_memories_auto_save_desc),
                trailing = {
                    Switch(
                        checked = memoryAutoSaveEnabled,
                        onCheckedChange = { onToggleMemoryAutoSave(it) },
                        enabled = memoriesEnabled
                    )
                },
                onClick = {
                    if (memoriesEnabled) onToggleMemoryAutoSave(!memoryAutoSaveEnabled)
                }
            )

            PillListItem(
                headline = stringResource(R.string.settings_memories_manage),
                supporting = stringResource(R.string.settings_memories_manage_desc),
                onClick = { closeSettingsThen(onOpenMemoriesManager) }
            )

            HorizontalDivider()

            PillListItem(
                headline = stringResource(R.string.settings_version),
                supporting = versionName
            )

            PillButton(
                text = stringResource(R.string.settings_source_code),
                onClick = { closeSettingsThen { openUrlSafely(context, sourceUrl) } }
            )

            Spacer(Modifier.height(6.dp))
        }
    }

    if (showLanguagePicker) {
        LanguagePickerBottomSheet(
            currentCode = langCode,
            onPick = { picked ->
                showLanguagePicker = false
                closeSettingsThen { onChangeLanguage(picked) }
            },
            onDismiss = { showLanguagePicker = false }
        )
    }
}

@Composable
private fun PillListItem(
    headline: String,
    supporting: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(22.dp)
    val clickable = if (onClick != null) Modifier.clickable { onClick() } else Modifier

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier
            .fillMaxWidth()
            .then(clickable)
    ) {
        ListItem(
            headlineContent = { Text(headline) },
            supportingContent = { Text(supporting, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingContent = trailing
        )
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoriesManagerBottomSheet(
    memories: List<String>,
    onAdd: (String) -> Unit,
    onDeleteAt: (Int) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { sheetState.show() }
    val scope = rememberCoroutineScope()

    var newMemory by rememberSaveable { mutableStateOf("") }

    fun closeThen(action: () -> Unit = {}) {
        scope.launch {
            runCatching { sheetState.hide() }
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { closeThen() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.memories_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { closeThen() }) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            Text(
                text = stringResource(R.string.memories_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = newMemory,
                onValueChange = { newMemory = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.memories_add_hint)) },
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = {
                        val txt = newMemory.trim()
                        if (txt.isNotBlank()) {
                            onAdd(txt)
                            newMemory = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.memories_save_manual))
                }

                if (memories.isNotEmpty()) {
                    TextButton(onClick = { onClearAll() }) {
                        Text(stringResource(R.string.memories_clear_all))
                    }
                }
            }

            if (memories.isEmpty()) {
                Text(
                    text = stringResource(R.string.memories_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    itemsIndexed(memories) { idx, mem ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = mem,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = { onDeleteAt(idx) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerBottomSheet(
    currentCode: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { sheetState.show() }
    val scope = rememberCoroutineScope()


    fun closeThen(action: () -> Unit = {}) {
        scope.launch {
            runCatching { sheetState.hide() }
            onDismiss()
            action()
        }
    }

    fun pick(code: String) {
        closeThen { onPick(code) }
    }

    val items = listOf(
        "pt" to stringResource(R.string.language_pt),
        "en" to stringResource(R.string.language_en),
        "es" to stringResource(R.string.language_es),
        "ru" to stringResource(R.string.language_ru)
    )

    ModalBottomSheet(
        onDismissRequest = { closeThen() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_language_select),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalIconButton(onClick = { closeThen() }) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            items.forEach { (code, label) ->
                LanguagePillOption(
                    title = label,
                    selected = currentCode == code,
                    onClick = { pick(code) }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LanguagePillOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = shape,
        color = bg,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), shape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                tint = fg
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = fg,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = fg
                )
            }
        }
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