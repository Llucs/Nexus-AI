package com.llucs.nexusai

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import com.llucs.nexusai.ui.NexusTheme
import com.llucs.nexusai.ui.chat.ChatScreen
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private fun defaultLanguageCode(): String {
        val sys = Locale.getDefault().language.lowercase(Locale.ROOT)
        return if (sys.startsWith("pt")) "pt" else "en"
    }

    private fun normalizeLanguage(code: String?): String {
        val c = (code ?: "").trim().lowercase(Locale.ROOT)
        return when {
            c == "pt" || c.startsWith("pt") -> "pt"
            c == "en" || c.startsWith("en") -> "en"
            c == "system" || c.isBlank() -> defaultLanguageCode()
            else -> defaultLanguageCode()
        }
    }

    private fun applyLanguage(code: String) {
        val tags = when (code) {
            "pt" -> "pt"
            "en" -> "en"
            else -> ""
        }
        val desired = LocaleListCompat.forLanguageTags(tags)
        val current = AppCompatDelegate.getApplicationLocales()
        if (current.toLanguageTags() != desired.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(desired)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
        NexusTheme {
            val context = LocalContext.current
            val activity = context as? ComponentActivity
            val scope = rememberCoroutineScope()

            val chatStore = remember { ChatStore(context.applicationContext) }
            val prefs = remember { UserPrefs(context.applicationContext) }

            var prefsLoaded by remember { mutableStateOf(false) }
            var userName by rememberSaveable { mutableStateOf("") }
            var nameInput by rememberSaveable { mutableStateOf("") }
            var languageCode by rememberSaveable { mutableStateOf(defaultLanguageCode()) }

            LaunchedEffect(Unit) {
                val savedName = prefs.getUserName().orEmpty().trim()
                val savedLang = normalizeLanguage(prefs.getLanguage())

                languageCode = savedLang
                applyLanguage(savedLang)

                userName = savedName
                nameInput = savedName

                prefsLoaded = true
            }

                val lang = normalizeLanguage(languageCode)

                Surface(color = MaterialTheme.colorScheme.background) {
                    ChatScreen(
                        store = chatStore,
                        userName = userName,
                        languageCode = lang,
                        onEditName = {
                            nameInput = userName
                            scope.launch {
                                prefs.setUserName("")
                                userName = ""
                            }
                        },
                        onChangeLanguage = { newCode: String ->
                            val fixed = normalizeLanguage(newCode)
                            if (fixed != lang) {
                                scope.launch { prefs.setLanguage(fixed) }
                                languageCode = fixed
                                applyLanguage(fixed)
                                activity?.recreate()
                            }
                        }
                    )
                }

                if (prefsLoaded && userName.isBlank()) {
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text(if (lang == "pt") "Seu nome" else "Your name") },
                        text = {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it.take(24) },
                                singleLine = true,
                                label = {
                                    Text(
                                        if (lang == "pt") "Como você quer ser chamado?" else "What should I call you?"
                                    )
                                }
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
                                        nameInput = fixed
                                    }
                                }
                            ) { Text(if (lang == "pt") "Salvar" else "Save") }
                        }
                    )
                }
            }
        }
    }
}
