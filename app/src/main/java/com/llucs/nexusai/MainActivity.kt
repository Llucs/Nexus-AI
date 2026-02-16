package com.llucs.nexusai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.llucs.nexusai.data.ChatStore
import com.llucs.nexusai.ui.NexusTheme
import com.llucs.nexusai.ui.chat.ChatScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val store = ChatStore(applicationContext)

        setContent {
            NexusTheme {
                Surface(
                    modifier = Modifier,
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen(store = store)
                }
            }
        }
    }
}
