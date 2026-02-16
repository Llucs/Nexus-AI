package com.llucs.nexusai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import com.llucs.nexusai.data.ChatStore
import com.llucs.nexusai.data.UserPrefs
import com.llucs.nexusai.ui.chat.ChatScreen
import com.llucs.nexusai.ui.NexusTheme
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NexusTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                val chatStore = remember { ChatStore(context.applicationContext) }
                val prefs = remember { UserPrefs(context.applicationContext) }

                var userName by rememberSaveable { mutableStateOf<String?>(null) }
                var languageCode by rememberSaveable { mutableStateOf<String?>(null) }

                var showNameDialog by rememberSaveable { mutableStateOf(false) }
                var nameInput by rememberSaveable { mutableStateOf("") }

                fun normalizeLanguage(code: String?): String {
                    val c = (code ?: "").lowercase(Locale.ROOT)
                    return when {
                        c.startsWith("pt") -> "pt"
                        c.startsWith("en") -> "en"
                        else -> if (Locale.getDefault().language.lowercase(Locale.ROOT).startsWith("pt")) "pt" else "en"
                    }
                }

                fun applyLanguage(code: String) {
                    val tags = when (code) {
                        "pt" -> "pt-BR"
                        "en" -> "en"
                        else -> ""
                    }
                    val list = if (tags.isBlank()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tags)
                    AppCompatDelegate.setApplicationLocales(list)
                }

                LaunchedEffect(Unit) {
                    val savedName = prefs.getUserName()
                    val savedLang = normalizeLanguage(prefs.getLanguage())

                    userName = savedName
                    languageCode = savedLang
                    nameInput = savedName.orEmpty()

                    applyLanguage(savedLang)
                    if (savedName.isNullOrBlank()) showNameDialog = true
                }

                if (showNameDialog) {
                    val lang = normalizeLanguage(languageCode)
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text(if (lang == "pt") "Seu nome" else "Your name") },
                        text = {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it.take(24) },
                                singleLine = true,
                                label = { Text(if (lang == "pt") "Como você quer ser chamado?" else "What should I call you?") }
                            )
                        },
                        confirmButton = {
                            val ok = nameInput.trim().isNotEmpty()
                            TextButton(
                                enabled = ok,
                                onClick = {
                                    val fixed = nameInput.trim()
                                    scope.launch {
                                        prefs.setUserName(fixed)
                                        userName = fixed
                                        showNameDialog = false
                                    }
                                }
                            ) { Text(if (lang == "pt") "Salvar" else "Save") }
                        }
                    )
                }

                Surface(color = MaterialTheme.colorScheme.background) {
                    val lang = normalizeLanguage(languageCode)
                    ChatScreen(
                        store = chatStore,
                        userName = userName.orEmpty(),
                        languageCode = lang,
                        onEditName = {
                            nameInput = userName.orEmpty()
                            showNameDialog = true
                        },
                        onChangeLanguage = { newCode ->
                            val fixed = normalizeLanguage(newCode)
                            if (fixed != lang) {
                                scope.launch { prefs.setLanguage(fixed) }
                                languageCode = fixed
                                applyLanguage(fixed)
                                this@MainActivity.recreate()
                            }
                        }
                    )
                }
            }
        }
    }
}
