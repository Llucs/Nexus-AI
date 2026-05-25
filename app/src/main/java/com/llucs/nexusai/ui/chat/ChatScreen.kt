package com.llucs.nexusai.ui.chat
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.llucs.nexusai.ChatStrings
import com.llucs.nexusai.ChatViewModel
import com.llucs.nexusai.MarkdownTextBlock
import com.llucs.nexusai.R
import com.llucs.nexusai.TokenUsage
import com.llucs.nexusai.UiMessage
import com.llucs.nexusai.data.ChatStore
import com.llucs.nexusai.data.MemoryStore
import com.llucs.nexusai.data.StoredChat
import com.llucs.nexusai.data.UserPrefs
import com.llucs.nexusai.splitMarkdown
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    store: ChatStore, prefs: UserPrefs, memoryStore: MemoryStore,
    userName: String, languageCode: String,
    onEditName: () -> Unit, onChangeLanguage: (String) -> Unit,
    sourceUrl: String = "https://github.com/Llucs/Nexus-AI"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var voiceMode by rememberSaveable { mutableStateOf(false) }
    var voiceState by remember { mutableStateOf(VoiceCaptureState()) }
    val voiceController = remember(context, scope) { VoiceInputController(context, scope) { voiceState = it } }

    val requestMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { voiceMode = true; voiceController.resetText(); voiceController.start() }
    }

    fun startVoice() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceMode = true; voiceController.resetText(); voiceController.start()
        } else { requestMic.launch(Manifest.permission.RECORD_AUDIO) }
    }

    LaunchedEffect(voiceMode) { if (!voiceMode) voiceController.stop() }
    DisposableEffect(Unit) { onDispose { voiceController.stop() } }

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
        "pt" -> if (hasName) "Nome do usu\u00e1rio: $displayName. Voc\u00ea pode usar esse nome, mas s\u00f3 quando for natural (n\u00e3o em toda mensagem)." else "O nome do usu\u00e1rio ainda n\u00e3o foi informado. Se precisar, pergunte o nome. N\u00e3o use \"voc\u00ea\" como nome."
        "es" -> if (hasName) "Nombre del usuario: $displayName. Puedes usar ese nombre, pero solo cuando sea natural (no en cada mensaje)." else "El nombre del usuario a\u00fan no fue informado. Si hace falta, pregunta el nombre. No uses \"t\u00fa\" como nombre."
        "ru" -> if (hasName) "\u0418\u043c\u044f \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044f: $displayName. \u041c\u043e\u0436\u0435\u0448\u044c \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u044c \u0438\u043c\u044f, \u043d\u043e \u0442\u043e\u043b\u044c\u043a\u043e \u043a\u043e\u0433\u0434\u0430 \u044d\u0442\u043e \u0443\u043c\u0435\u0441\u0442\u043d\u043e (\u043d\u0435 \u0432 \u043a\u0430\u0436\u0434\u043e\u043c \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0438)." else "\u0418\u043c\u044f \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044f \u0435\u0449\u0451 \u043d\u0435 \u0443\u043a\u0430\u0437\u0430\u043d\u043e. \u0415\u0441\u043b\u0438 \u043d\u0443\u0436\u043d\u043e, \u0441\u043f\u0440\u043e\u0441\u0438 \u0438\u043c\u044f. \u041d\u0435 \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0439 \u00ab\u0432\u044b\u00bb \u043a\u0430\u043a \u0438\u043c\u044f."
        else -> if (hasName) "User name: $displayName. You may use it, but only when it feels natural (not in every message)." else "The user's name hasn't been provided yet. If needed, ask for their name. Do not use \"you\" as a name."
    }

    val appIdentity = when (locale) {
        "pt" -> "Voc\u00ea est\u00e1 no app Nexus / Nexus AI. Ele foi criado por Llucs (Leandro Lucas Mendes de Souza)."
        "es" -> "Est\u00e1s en la app Nexus / Nexus AI. Fue creada por Llucs (Leandro Lucas Mendes de Souza)."
        "ru" -> "\u0412\u044b \u043d\u0430\u0445\u043e\u0434\u0438\u0442\u0435\u0441\u044c \u0432 \u043f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u0438 Nexus / Nexus AI. \u041e\u043d\u043e \u0441\u043e\u0437\u0434\u0430\u043d\u043e Llucs (Leandro Lucas Mendes de Souza)."
        else -> "You are in the Nexus / Nexus AI app. It was created by Llucs (Leandro Lucas Mendes de Souza)."
    }

    val memoriesBlock = if (memoriesEnabled && memories.isNotEmpty()) {
        val list = memories.take(30).joinToString("\n") { "- $it" }
        when (locale) {
            "pt" -> "Mem\u00f3rias salvas do usu\u00e1rio (use s\u00f3 quando ajudar):\n$list"
            "es" -> "Memorias guardadas del usuario (\u00fasalas solo cuando ayuden):\n$list"
            "ru" -> "\u0421\u043e\u0445\u0440\u0430\u043d\u0451\u043d\u043d\u044b\u0435 \u0432\u043e\u0441\u043f\u043e\u043c\u0438\u043d\u0430\u043d\u0438\u044f \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044f (\u0438\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0439 \u0442\u043e\u043b\u044c\u043a\u043e \u043a\u043e\u0433\u0434\u0430 \u043f\u043e\u043b\u0435\u0437\u043d\u043e):\n$list"
            else -> "Saved user memories (use only when helpful):\n$list"
        }
    } else if (!memoriesEnabled) {
        when (locale) {
            "pt" -> "Mem\u00f3rias est\u00e3o DESATIVADAS: n\u00e3o salve mem\u00f3rias e n\u00e3o use mem\u00f3rias antigas."
            "es" -> "Las memorias est\u00e1n DESACTIVADAS: no guardes memorias ni uses memorias anteriores."
            "ru" -> "\u041f\u0430\u043c\u044f\u0442\u044c \u041e\u0422\u041a\u041b\u042e\u0427\u0415\u041d\u0410: \u043d\u0435 \u0441\u043e\u0445\u0440\u0430\u043d\u044f\u0439 \u0438 \u043d\u0435 \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0439 \u0441\u0442\u0430\u0440\u044b\u0435 \u0432\u043e\u0441\u043f\u043e\u043c\u0438\u043d\u0430\u043d\u0438\u044f."
            else -> "Memories are DISABLED: don't save memories and don't use old memories."
        }
    } else ""

    val memorySaveRules = when {
        !memoriesEnabled -> ""
        memoriesEnabled && memoryAutoSaveEnabled -> when (locale) {
            "pt" -> "Quando o usu\u00e1rio disser algo pessoal e est\u00e1vel (ex.: nome, idade, cidade/pa\u00eds, prefer\u00eancias, hobbies, dispositivos, projetos, metas), voc\u00ea DEVE salvar como mem\u00f3ria. No FIM da sua resposta, em uma linha separada e apenas com isso, escreva: <<MEMORY_SAVE: ...>>. Use uma frase curta (sem Markdown, sem #, sem listas). Se houver mais de 1 mem\u00f3ria, use 1 linha por mem\u00f3ria (m\u00e1x. 2)."
            "es" -> "Cuando el usuario diga algo personal y estable (p. ej. nombre, edad, ciudad/pa\u00eds, preferencias, hobbies, dispositivos, proyectos, metas), DEBES guardarlo como memoria. AL FINAL de tu respuesta, en una l\u00ednea separada y solo con eso, escribe: <<MEMORY_SAVE: ...>>."
            "ru" -> "\u0415\u0441\u043b\u0438 \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044c \u0433\u043e\u0432\u043e\u0440\u0438\u0442 \u0447\u0442\u043e-\u0442\u043e \u043b\u0438\u0447\u043d\u043e\u0435 \u0438 \u0441\u0442\u0430\u0431\u0438\u043b\u044c\u043d\u043e\u0435, \u0422\u042b \u0414\u041e\u041b\u0416\u0415\u041d \u0441\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c \u044d\u0442\u043e \u043a\u0430\u043a \u043f\u0430\u043c\u044f\u0442\u044c. \u0412 \u041a\u041e\u041d\u0426\u0415 \u043e\u0442\u0432\u0435\u0442\u0430 \u043d\u0430\u043f\u0438\u0448\u0438: <<MEMORY_SAVE: ...>>."
            else -> "When the user says stable personal info, you MUST save it as a memory. At the END of your reply write: <<MEMORY_SAVE: ...>>."
        }
        else -> when (locale) {
            "pt" -> "N\u00e3o use marcadores de mem\u00f3ria (auto-salvar est\u00e1 desligado)."
            "es" -> "No uses marcadores de memoria (auto-guardar est\u00e1 desactivado)."
            "ru" -> "\u041d\u0435 \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0439 \u043c\u0430\u0440\u043a\u0435\u0440\u044b \u043f\u0430\u043c\u044f\u0442\u0438 (\u0430\u0432\u0442\u043e\u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0438\u0435 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d\u043e)."
            else -> "Don't use memory markers (auto-save is off)."
        }
    }

    val systemPrompt = when (locale) {
        "pt" -> "Oi! Eu sou o Nexus AI.\n\n$nameHint\n\n$appIdentity\n\n${if (memoriesBlock.isNotBlank()) memoriesBlock + "\n\n" else ""}$memorySaveRules\n\nRegras do Nexus:\n- Fale claro e simples.\n- V\u00e1 direto ao ponto.\n- N\u00e3o junte palavras, letras e n\u00fameros; mantenha espa\u00e7amento normal.\n- Use Markdown bem formatado quando ajudar.\n- Se eu n\u00e3o souber algo, eu vou falar e sugerir alternativas."
        "es" -> "\u00a1Hola! Soy Nexus AI.\n\n$nameHint\n\n$appIdentity\n\n${if (memoriesBlock.isNotBlank()) memoriesBlock + "\n\n" else ""}$memorySaveRules\n\nReglas:\n- Habla claro y simple.\n- Ve directo al punto.\n- No juntes palabras, letras y n\u00fameros; mant\u00e9n el espaciado normal.\n- Usa Markdown bien formateado cuando ayude.\n- Si no s\u00e9 algo, lo dir\u00e9 y sugerir\u00e9 alternativas."
        "ru" -> "\u041f\u0440\u0438\u0432\u0435\u0442! \u042f Nexus AI.\n\n$nameHint\n\n$appIdentity\n\n${if (memoriesBlock.isNotBlank()) memoriesBlock + "\n\n" else ""}$memorySaveRules\n\n\u041f\u0440\u0430\u0432\u0438\u043b\u0430:\n- \u041f\u0438\u0448\u0438 \u044f\u0441\u043d\u043e \u0438 \u043f\u0440\u043e\u0441\u0442\u043e.\n- \u0421\u0440\u0430\u0437\u0443 \u043a \u0434\u0435\u043b\u0443.\n- \u0418\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0439 \u0430\u043a\u043a\u0443\u0440\u0430\u0442\u043d\u044b\u0439 Markdown, \u043a\u043e\u0433\u0434\u0430 \u044d\u0442\u043e \u043f\u043e\u043c\u043e\u0433\u0430\u0435\u0442.\n- \u0415\u0441\u043b\u0438 \u044f \u0447\u0435\u0433\u043e-\u0442\u043e \u043d\u0435 \u0437\u043d\u0430\u044e, \u044f \u0441\u043a\u0430\u0436\u0443 \u0438 \u043f\u0440\u0435\u0434\u043b\u043e\u0436\u0443 \u0432\u0430\u0440\u0438\u0430\u043d\u0442\u044b."
        else -> "Hi! I'm Nexus AI.\n\n$nameHint\n\n$appIdentity\n\n${if (memoriesBlock.isNotBlank()) memoriesBlock + "\n\n" else ""}$memorySaveRules\n\nRules:\n- Speak clearly and keep it simple.\n- Go straight to the point.\n- Don't glue words, letters, and numbers together.\n- Use well-formatted Markdown when helpful.\n- If I don't know something, I'll say so and suggest alternatives."
    }

    val finalSystemPrompt = if (hasName) systemPrompt + "\n\n" + when (locale) {
        "pt" -> "Nome preferido do usu\u00e1rio: $displayName. Use o nome s\u00f3 quando for natural; n\u00e3o repita em toda resposta."
        "es" -> "Nombre preferido del usuario: $displayName. Usa el nombre solo cuando sea natural; no lo repitas en cada respuesta."
        "ru" -> "\u041f\u0440\u0435\u0434\u043f\u043e\u0447\u0442\u0438\u0442\u0435\u043b\u044c\u043d\u043e\u0435 \u0438\u043c\u044f \u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u0435\u043b\u044f: $displayName. \u0418\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0439 \u0438\u043c\u044f \u0442\u043e\u043b\u044c\u043a\u043e \u043a\u043e\u0433\u0434\u0430 \u044d\u0442\u043e \u0443\u043c\u0435\u0441\u0442\u043d\u043e."
        else -> "User preferred name: $displayName. Use the name only when it feels natural; don't repeat it in every reply."
    } else systemPrompt

    val greeting = when (locale) {
        "pt" -> if (hasName) "Oi, ${displayName}! Eu sou o Nexus AI. Pode perguntar qualquer coisa." else "Oi! Eu sou o Nexus AI. Pode perguntar qualquer coisa."
        "es" -> if (hasName) "\u00a1Hola, ${displayName}! Soy Nexus AI. Pregunta lo que quieras." else "\u00a1Hola! Soy Nexus AI. Pregunta lo que quieras."
        "ru" -> if (hasName) "\u041f\u0440\u0438\u0432\u0435\u0442, ${displayName}! \u042f Nexus AI. \u0421\u043f\u0440\u0430\u0448\u0438\u0432\u0430\u0439 \u0447\u0442\u043e \u0443\u0433\u043e\u0434\u043d\u043e." else "\u041f\u0440\u0438\u0432\u0435\u0442! \u042f Nexus AI. \u0421\u043f\u0440\u0430\u0448\u0438\u0432\u0430\u0439 \u0447\u0442\u043e \u0443\u0433\u043e\u0434\u043d\u043e."
        else -> if (hasName) "Hi, ${displayName}! I'm Nexus AI. Ask me anything." else "Hi! I'm Nexus AI. Ask me anything."
    }

    val interrupted = stringResource(R.string.msg_interrupted)
    val genericError = stringResource(R.string.generic_error)
    val assistantErrTemplate = stringResource(R.string.assistant_error_template)
    val snackFailedTemplate = stringResource(R.string.snack_failed_template)
    val retryAction = stringResource(R.string.snack_retry)

    val vm: ChatViewModel = viewModel(factory = ChatViewModel.factory(store = store, memoryStore = memoryStore, strings = ChatStrings(
        systemPrompt = finalSystemPrompt, greeting = greeting, interrupted = interrupted,
        genericError = genericError, assistantErrorTemplate = assistantErrTemplate,
        snackFailedTemplate = snackFailedTemplate, retryActionLabel = retryAction
    )))

    LaunchedEffect(finalSystemPrompt, greeting, interrupted, genericError, assistantErrTemplate, snackFailedTemplate, retryAction) {
        vm.updateStrings(ChatStrings(systemPrompt = finalSystemPrompt, greeting = greeting, interrupted = interrupted,
            genericError = genericError, assistantErrorTemplate = assistantErrTemplate,
            snackFailedTemplate = snackFailedTemplate, retryActionLabel = retryAction))
    }

    val uiState by vm.state.collectAsState()

    LaunchedEffect(memoriesEnabled, memoryAutoSaveEnabled) { vm.updateMemorySettings(memoriesEnabled, memoryAutoSaveEnabled) }
    LaunchedEffect(uiState.messages.lastOrNull()?.memorySaved) { if (!uiState.messages.lastOrNull()?.memorySaved.isNullOrBlank()) runCatching { memories = memoryStore.loadMemories() } }

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

    LaunchedEffect(uiState.snackbar) {
        val s = uiState.snackbar ?: return@LaunchedEffect
        val res = snackbarHostState.showSnackbar(message = s.message, actionLabel = s.actionLabel, withDismissAction = true)
        if (res == androidx.compose.material3.SnackbarResult.ActionPerformed) s.onAction?.invoke()
        vm.consumeSnackbar()
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isEmpty()) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = listState.layoutInfo.totalItemsCount
        if (total == 0 || lastVisible >= total - 3) listState.animateScrollToItem(uiState.messages.lastIndex)
    }

    val showScrollToBottom by remember { derivedStateOf {
        val total = listState.layoutInfo.totalItemsCount
        if (total <= 0) false else (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) < total - 3
    } }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { NexusTopBar(sending = uiState.sending, navLetter = navLetter, onOpenSettings = { showSettings = true }, onHistory = vm::openHistory, onNewChat = vm::newChat, onStop = vm::stop, scrollBehavior = scrollBehavior, lastTokenUsage = uiState.lastTokenUsage) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (voiceMode) VoiceInputBar(state = voiceState, onToggleShowText = voiceController::toggleShowText, onCancel = { voiceMode = false }, onSendText = { t ->
                val normalized = normalizePtDictation(t)
                if (normalized.isNotBlank()) { vm.setInput(normalized); voiceMode = false; vm.send() } else voiceMode = false
            }) else NexusInputBar(input = uiState.input, enabled = !uiState.sending, onInputChange = vm::setInput, onSend = vm::send, onMic = { startVoice() })
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding)) {
            AnimatedVisibility(visible = uiState.messages.isEmpty()) { EmptySuggestions(onPick = { vm.setInput(it) }) }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(uiState.messages, key = { index, msg -> "${uiState.currentChatId}_${index}_${msg.role}" }) { index, msg ->
                    val prevRole = uiState.messages.getOrNull(index - 1)?.role
                    MessageBubble(userLetter = navLetter, userName = displayName, message = msg, showMeta = prevRole != msg.role,
                        onCopy = if (msg.role == "assistant" && msg.content.isNotBlank() && !msg.isThinking) {{ clipboard.setText(AnnotatedString(msg.content)); vm.showSnackbar(copiedText) }} else null,
                        onLongCopy = if (msg.role == "assistant" && msg.content.isNotBlank() && !msg.isThinking) {{ clipboard.setText(AnnotatedString(msg.content)); vm.showSnackbar(copiedText) }} else null)
                }
            }

            AnimatedVisibility(visible = showScrollToBottom, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 96.dp)) {
                FloatingActionButton(onClick = { uiScope.launch { listState.animateScrollToItem(uiState.messages.lastIndex) } }, containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = null)
                }
            }

            if (uiState.historyOpen) HistoryBottomSheet(chats = uiState.chats, currentChatId = uiState.currentChatId, onClose = vm::closeHistory, onPick = vm::loadChat, onDelete = vm::deleteChat, onClearAll = vm::clearAllHistory)
        }
    }

    if (showSettings) SettingsBottomSheet(userName = userName, languageCode = locale, memoriesEnabled = memoriesEnabled, memoryAutoSaveEnabled = memoryAutoSaveEnabled, memoriesCount = memories.size, lastTokenUsage = uiState.lastTokenUsage, lastModelName = uiState.lastModelName,
        onToggleMemoriesEnabled = { memoriesEnabled = it; uiScope.launch { prefs.setMemoriesEnabled(it) } },
        onToggleMemoryAutoSave = { memoryAutoSaveEnabled = it; uiScope.launch { prefs.setMemoryAutoSaveEnabled(it) } },
        onOpenMemoriesManager = { showMemoriesManager = true }, onEditName = { showSettings = false; onEditName() },
        onChangeLanguage = { showSettings = false; onChangeLanguage(it) }, sourceUrl = sourceUrl, onDismiss = { showSettings = false })

    if (showMemoriesManager) MemoriesManagerBottomSheet(memories = memories,
        onAdd = { uiScope.launch { memoryStore.addMemory(it); memories = memoryStore.loadMemories() } },
        onDeleteAt = { uiScope.launch { memoryStore.removeAt(it); memories = memoryStore.loadMemories() } },
        onClearAll = { uiScope.launch { memoryStore.clearAll(); memories = emptyList() } }, onDismiss = { showMemoriesManager = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NexusTopBar(sending: Boolean, navLetter: String, onOpenSettings: () -> Unit, onHistory: () -> Unit, onNewChat: () -> Unit, onStop: () -> Unit, scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior, lastTokenUsage: TokenUsage? = null) {
    CenterAlignedTopAppBar(modifier = Modifier.statusBarsPadding(), scrollBehavior = scrollBehavior, title = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.topbar_title), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (lastTokenUsage != null) { Spacer(Modifier.width(8.dp)); Text(text = lastTokenUsage.formatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace) }
            }
            Text(text = stringResource(R.string.topbar_subtitle), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }, navigationIcon = { Box(modifier = Modifier.padding(start = 8.dp)) { BrandDot(letter = navLetter, onClick = onOpenSettings) } },
        actions = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onHistory) { Icon(Icons.Filled.History, contentDescription = stringResource(R.string.action_history)) }
            FilledTonalIconButton(onClick = onNewChat) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_new_chat)) }
            AnimatedVisibility(visible = sending) { FilledTonalIconButton(onClick = onStop, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_stop)) } }
            Spacer(Modifier.width(4.dp))
        } },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), titleContentColor = MaterialTheme.colorScheme.onSurface))
}

@Composable
private fun BrandDot(letter: String, onClick: () -> Unit) {
    Box(modifier = Modifier.padding(start = 4.dp).size(38.dp).clip(CircleShape).clickable(onClick = onClick).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Text(text = letter, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(userLetter: String, userName: String, message: UiMessage, showMeta: Boolean, onCopy: (() -> Unit)?, onLongCopy: (() -> Unit)?) {
    val isUser = message.role == "user"
    val align = if (isUser) Alignment.End else Alignment.Start
    val bubbleShape = if (isUser) RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 4.dp, bottomStart = 20.dp) else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 4.dp)

    AnimatedVisibility(visible = true, enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300), initialOffsetY = { it / 4 })) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
            if (showMeta) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                    if (!isUser) { NexusAvatar(size = 24.dp); Spacer(Modifier.width(8.dp)) }
                    Text(text = if (isUser) userName else "Nexus", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isUser) { Spacer(Modifier.width(8.dp)); AvatarDot(bg = MaterialTheme.colorScheme.primary, fg = MaterialTheme.colorScheme.onPrimary, letter = if (userName.isNotBlank()) userName.first().uppercaseChar().toString() else "U") }
                }
                Spacer(Modifier.height(4.dp))
            }

            Surface(color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = bubbleShape, tonalElevation = 0.dp, shadowElevation = 0.dp,
                modifier = Modifier.widthIn(max = 520.dp).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), bubbleShape).combinedClickable(onClick = {}, onLongClick = { onLongCopy?.invoke() })) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (message.isThinking) { ThinkingIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant) } else {
                        val content = message.content.replace("\\n", "\n")
                        val blocks = remember(content) { splitMarkdown(content) }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { blocks.forEach { b -> MarkdownTextBlock(block = b, modifier = Modifier.fillMaxWidth(), contentColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) } }

                        if (!isUser) {
                            Spacer(Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                if (message.tokenUsage != null) { Text(text = message.tokenUsage.formatted, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)); Spacer(Modifier.width(8.dp)) }
                                if (onCopy != null) { IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) { Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.action_copy), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp)) } }
                            }
                        }
                    }
                }
            }

            if (!isUser && !message.isThinking && !message.memorySaved.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 520.dp).padding(horizontal = 8.dp)) {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(text = stringResource(R.string.memory_saved_template, message.memorySaved!!), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun NexusAvatar(size: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun AvatarDot(bg: Color, fg: Color, letter: String) {
    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(text = letter, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun ThinkingIndicator(color: Color) {
    val infinite = rememberInfiniteTransition(label = "thinking")
    val a1 by infinite.animateFloat(initialValue = 0.3f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "a1")
    val a2 by infinite.animateFloat(initialValue = 0.3f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(500, delayMillis = 120), RepeatMode.Reverse), label = "a2")
    val a3 by infinite.animateFloat(initialValue = 0.3f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(500, delayMillis = 240), RepeatMode.Reverse), label = "a3")

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = stringResource(R.string.label_typing), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = color)
        Spacer(Modifier.width(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf(a1, a2, a3).forEach { alpha -> Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color.copy(alpha = alpha))) } }
    }
}

@Composable
private fun NexusInputBar(input: String, enabled: Boolean, onInputChange: (String) -> Unit, onSend: () -> Unit, onMic: () -> Unit) {
    val canSend = enabled && input.trim().isNotEmpty()

    Surface(tonalElevation = 0.dp, color = Color.Transparent) {
        Box(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), MaterialTheme.colorScheme.surface)))) {
            Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                TextField(value = input, onValueChange = onInputChange, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled,
                    placeholder = { Text(stringResource(R.string.input_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    shape = RoundedCornerShape(28.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }), maxLines = 6,
                    leadingIcon = { IconButton(onClick = { if (enabled) onMic() }, enabled = enabled, modifier = Modifier.size(40.dp)) { Icon(imageVector = Icons.Filled.Mic, contentDescription = stringResource(R.string.voice_start), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.7f else 0.35f), modifier = Modifier.size(20.dp)) } },
                    trailingIcon = {
                        Box(modifier = Modifier.size(44.dp).clip(CircleShape).then(if (canSend) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))), contentAlignment = Alignment.Center) {
                            IconButton(onClick = { if (canSend) onSend() }, enabled = canSend, modifier = Modifier.size(44.dp)) { Icon(imageVector = Icons.Filled.Send, contentDescription = stringResource(R.string.input_send), tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp)) }
                        }
                    },
                    colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent, cursorColor = MaterialTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
private fun EmptySuggestions(onPick: (String) -> Unit) {
    val promptCreateImage = stringResource(R.string.prompt_create_image)
    val promptSummarize = stringResource(R.string.prompt_summarize)
    val promptSurprise = stringResource(R.string.prompt_surprise)
    val promptHelpWrite = stringResource(R.string.prompt_help_write)

    Box(modifier = Modifier.fillMaxSize().padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.suggestions_title), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = stringResource(R.string.suggestions_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
            Spacer(modifier = Modifier.height(28.dp))
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard(icon = Icons.Filled.Image, text = stringResource(R.string.suggestions_create_image), onClick = { onPick(promptCreateImage) })
                QuickActionCard(icon = Icons.Filled.Summarize, text = stringResource(R.string.suggestions_summarize), onClick = { onPick(promptSummarize) })
                QuickActionCard(icon = Icons.Filled.AutoAwesome, text = stringResource(R.string.suggestions_surprise), onClick = { onPick(promptSurprise) })
                QuickActionCard(icon = Icons.Filled.Edit, text = stringResource(R.string.suggestions_help_write), onClick = { onPick(promptHelpWrite) })
            }
        }
    }
}

@Composable
private fun QuickActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Surface(tonalElevation = 0.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp)) }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(text = text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun VoiceInputBar(state: VoiceCaptureState, onToggleShowText: () -> Unit, onCancel: () -> Unit, onSendText: (String) -> Unit) {
    val text = (state.partialText.ifBlank { state.finalText }).trim()
    Surface(tonalElevation = 0.dp, color = Color.Transparent) {
        Box(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), MaterialTheme.colorScheme.surface))).navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), RoundedCornerShape(20.dp))) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.showText) Text(text = if (text.isBlank()) stringResource(R.string.voice_listening_hint) else text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f), modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), modifier = Modifier.size(40.dp)) {
                            IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) { Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.voice_cancel), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)) }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        AudioWaveform(levels = state.levels, modifier = Modifier.weight(1f).height(26.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedButton(onClick = onToggleShowText, shape = RoundedCornerShape(999.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.10f), contentColor = MaterialTheme.colorScheme.onSurface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text(text = stringResource(R.string.voice_show_text)) }
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.size(46.dp).clip(CircleShape).then(if (text.isNotBlank()) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))), contentAlignment = Alignment.Center) {
                            IconButton(onClick = { onSendText(text) }, enabled = text.isNotBlank(), modifier = Modifier.size(46.dp)) { Icon(imageVector = Icons.Filled.Send, contentDescription = stringResource(R.string.input_send), tint = if (text.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioWaveform(levels: List<Float>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        val bars = if (levels.isEmpty()) List(24) { 0f } else levels
        for (v in bars) Box(modifier = Modifier.width(3.dp).height((5f + 22f * v).dp).clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f + 0.5f * v)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryBottomSheet(chats: List<StoredChat>, currentChatId: String, onClose: () -> Unit, onPick: (String) -> Unit, onDelete: (String) -> Unit, onClearAll: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val filtered = remember(chats, query) { val q = query.trim().lowercase(); if (q.isEmpty()) chats else chats.filter { c -> c.messages.any { it.content.lowercase().contains(q) } } }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(text = stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.history_search)) }, leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.height(12.dp))
            if (filtered.isEmpty()) Text(text = stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp)) else LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(filtered.take(60), key = { _, c -> c.id }) { _, c ->
                    val title = c.messages.firstOrNull { it.role == "user" }?.content?.trim()?.take(50)?.ifBlank { stringResource(R.string.history_untitled) } ?: stringResource(R.string.history_new_chat)
                    val isCurrent = c.id == currentChatId
                    Surface(onClick = { onPick(c.id) }, shape = RoundedCornerShape(16.dp), color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
                        ListItem(headlineContent = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal) },
                            supportingContent = { Text(text = stringResource(R.string.history_messages_count, c.messages.size), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            trailingContent = { IconButton(onClick = { onDelete(c.id) }) { Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.history_delete_chat), tint = MaterialTheme.colorScheme.error) } })
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.history_close)) }
                TextButton(onClick = { confirmClear = true }, enabled = chats.isNotEmpty()) { Text(stringResource(R.string.history_clear_all)) }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text(stringResource(R.string.confirm_clear_title)) }, text = { Text(stringResource(R.string.confirm_clear_text)) }, confirmButton = { TextButton(onClick = { confirmClear = false; onClearAll() }) { Text(stringResource(R.string.confirm_delete)) } }, dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.confirm_cancel)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsBottomSheet(userName: String, languageCode: String, memoriesEnabled: Boolean, memoryAutoSaveEnabled: Boolean, memoriesCount: Int, lastTokenUsage: TokenUsage?, lastModelName: String?, onToggleMemoriesEnabled: (Boolean) -> Unit, onToggleMemoryAutoSave: (Boolean) -> Unit, onOpenMemoriesManager: () -> Unit, onEditName: () -> Unit, onChangeLanguage: (String) -> Unit, sourceUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { sheetState.show() }
    val scope = rememberCoroutineScope()
    var showLanguagePicker by remember { mutableStateOf(false) }
    fun closeSettingsThen(action: () -> Unit = {}) { scope.launch { runCatching { sheetState.hide() }; onDismiss(); action() } }
    val versionName = remember { getAppVersionName(context) }
    val displayName = userName.trim().ifBlank { stringResource(R.string.settings_not_set) }
    val langCode = languageCode.trim().lowercase()
    val languageLabel = when (langCode) { "pt" -> stringResource(R.string.language_pt); "en" -> stringResource(R.string.language_en); "es" -> stringResource(R.string.language_es); "ru" -> stringResource(R.string.language_ru); else -> stringResource(R.string.language_en) }

    ModalBottomSheet(onDismissRequest = { closeSettingsThen() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(painter = painterResource(id = R.drawable.logo), contentDescription = null, modifier = Modifier.size(52.dp))
                Column(modifier = Modifier.weight(1f)) { Text(text = stringResource(R.string.topbar_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface); Text(text = stringResource(R.string.settings_made_by, stringResource(R.string.developer_name)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                FilledTonalIconButton(onClick = { closeSettingsThen() }) { Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.action_close)) }
            }
            HorizontalDivider()
            PillListItem(headline = stringResource(R.string.settings_name), supporting = displayName, onClick = { closeSettingsThen(onEditName) })
            PillListItem(headline = stringResource(R.string.settings_language), supporting = languageLabel, trailing = { Icon(imageVector = Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = { showLanguagePicker = true })
            HorizontalDivider()
            PillListItem(headline = stringResource(R.string.settings_memories_title), supporting = if (memoriesCount > 0) stringResource(R.string.memories_count_template, memoriesCount) else stringResource(R.string.memories_empty_short), trailing = { Switch(checked = memoriesEnabled, onCheckedChange = onToggleMemoriesEnabled) }, onClick = { onToggleMemoriesEnabled(!memoriesEnabled) })
            PillListItem(headline = stringResource(R.string.settings_memories_auto_save), supporting = stringResource(R.string.settings_memories_auto_save_desc), trailing = { Switch(checked = memoryAutoSaveEnabled, onCheckedChange = onToggleMemoryAutoSave, enabled = memoriesEnabled) }, onClick = { if (memoriesEnabled) onToggleMemoryAutoSave(!memoryAutoSaveEnabled) })
            PillListItem(headline = stringResource(R.string.settings_memories_manage), supporting = stringResource(R.string.settings_memories_manage_desc), onClick = { closeSettingsThen(onOpenMemoriesManager) })
            if (lastTokenUsage != null || lastModelName != null) {
                HorizontalDivider()
                if (lastModelName != null) PillListItem(headline = "Model", supporting = lastModelName)
                if (lastTokenUsage != null) PillListItem(headline = stringResource(R.string.settings_token_usage), supporting = stringResource(R.string.settings_token_detail, lastTokenUsage.promptTokens, lastTokenUsage.completionTokens, lastTokenUsage.totalTokens))
            }
            HorizontalDivider()
            PillListItem(headline = stringResource(R.string.settings_version), supporting = versionName)
            PillButton(text = stringResource(R.string.settings_source_code), onClick = { closeSettingsThen { openUrlSafely(context, sourceUrl) } })
            Spacer(Modifier.height(6.dp))
        }
    }
    if (showLanguagePicker) LanguagePickerBottomSheet(currentCode = langCode, onPick = { showLanguagePicker = false; closeSettingsThen { onChangeLanguage(it) } }, onDismiss = { showLanguagePicker = false })
}

@Composable
private fun PillListItem(headline: String, supporting: String, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(18.dp)
    val clickable = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), modifier = Modifier.fillMaxWidth().then(clickable)) {
        ListItem(headlineContent = { Text(headline) }, supportingContent = { Text(supporting, color = MaterialTheme.colorScheme.onSurfaceVariant) }, trailingContent = trailing)
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoriesManagerBottomSheet(memories: List<String>, onAdd: (String) -> Unit, onDeleteAt: (Int) -> Unit, onClearAll: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { sheetState.show() }
    val scope = rememberCoroutineScope()
    var newMemory by rememberSaveable { mutableStateOf("") }
    fun closeThen(action: () -> Unit = {}) { scope.launch { runCatching { sheetState.hide() }; onDismiss(); action() } }

    ModalBottomSheet(onDismissRequest = { closeThen() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = stringResource(R.string.memories_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { closeThen() }) { Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.action_close)) }
            }
            Text(text = stringResource(R.string.memories_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = newMemory, onValueChange = { newMemory = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.memories_add_hint)) }, singleLine = true, shape = RoundedCornerShape(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = { val txt = newMemory.trim(); if (txt.isNotBlank()) { onAdd(txt); newMemory = "" } }) { Text(stringResource(R.string.memories_save_manual)) }
                if (memories.isNotEmpty()) TextButton(onClick = { onClearAll() }) { Text(stringResource(R.string.memories_clear_all)) }
            }
            if (memories.isEmpty()) Text(text = stringResource(R.string.memories_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) else LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                itemsIndexed(memories) { idx, mem ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(text = mem, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { onDeleteAt(idx) }) { Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerBottomSheet(currentCode: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { sheetState.show() }
    val scope = rememberCoroutineScope()
    fun closeThen(action: () -> Unit = {}) { scope.launch { runCatching { sheetState.hide() }; onDismiss(); action() } }
    fun pick(code: String) { closeThen { onPick(code) } }
    val items = listOf("pt" to stringResource(R.string.language_pt), "en" to stringResource(R.string.language_en), "es" to stringResource(R.string.language_es), "ru" to stringResource(R.string.language_ru))

    ModalBottomSheet(onDismissRequest = { closeThen() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.settings_language_select), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = { closeThen() }) { Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.action_close)) }
            }
            items.forEach { (code, label) -> LanguagePillOption(title = label, selected = currentCode == code, onClick = { pick(code) }) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LanguagePillOption(title: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(shape = shape, color = bg, modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), shape).clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(imageVector = Icons.Filled.Language, contentDescription = null, tint = fg)
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = fg, modifier = Modifier.weight(1f))
            if (selected) Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = fg)
        }
    }
}

@Suppress("DEPRECATION")
private fun getAppVersionName(context: Context): String = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?" } catch (_: Exception) { "?" }

private fun openUrlSafely(context: Context, url: String) {
    val u = url.trim(); if (u.isBlank()) return
    val uri = runCatching { Uri.parse(u) }.getOrNull() ?: return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
}
