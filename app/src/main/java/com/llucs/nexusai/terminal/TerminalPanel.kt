package com.llucs.nexusai.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun TerminalPanel(
    session: TerminalSession,
    proot: ProotDistro?,
    modifier: Modifier = Modifier
) {
    val prootState by if (proot != null) proot.state.collectAsState(initial = ProotState()) else remember { mutableStateOf(ProotState()) }
    val listState = rememberLazyListState()
    val scrollState = rememberScrollState()

    val outputs = remember(session) { session.history }

    val terminalScope = rememberCoroutineScope()
    val terminalColor = Color(0xFF1E1E1E)
    val terminalTextColor = Color(0xFFD4D4D4)
    val terminalGreen = Color(0xFF6A9955)
    val terminalYellow = Color(0xFFDCDCAA)

    LaunchedEffect(outputs.size) {
        if (outputs.isNotEmpty()) {
            listState.animateScrollToItem(outputs.lastIndex.coerceAtLeast(0))
        }
    }

    Column(modifier = modifier.fillMaxSize().background(terminalColor).padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nexus Terminal",
                color = terminalTextColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            if (!session.isRunning) {
                IconButton(onClick = { terminalScope.launch { session.start() } }) {
                    Icon(Icons.Filled.PlayArrow, "Start", tint = terminalGreen, modifier = Modifier.size(18.dp))
                }
            } else {
                IconButton(onClick = { terminalScope.launch { session.stop() } }) {
                    Icon(Icons.Filled.Stop, "Stop", tint = terminalYellow, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = { session.clearHistory() }) {
                Icon(Icons.Filled.Delete, "Clear", tint = terminalTextColor, modifier = Modifier.size(18.dp))
            }
        }

        val prootNotInstalled = prootState.status == ProotStatus.NOT_INSTALLED
        if (prootNotInstalled && proot != null) {
            Button(
                onClick = { terminalScope.launch { proot.ensureInstalled() } },
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = terminalGreen)
            ) {
                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Install Ubuntu", fontSize = 12.sp)
            }
        }

        if (prootState.status == ProotStatus.DOWNLOADING || prootState.status == ProotStatus.INSTALLING) {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Text(prootState.currentStep, color = terminalYellow, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { prootState.progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = terminalGreen, trackColor = terminalColor)
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF252526), RoundedCornerShape(4.dp))) {
            if (!session.isRunning && outputs.isEmpty()) {
                Text(
                    text = "Terminal stopped. Press Play to start.",
                    color = terminalTextColor.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                ) {
                    items(outputs.takeLast(500)) { out ->
                        val color = when (out.stream) {
                            "stderr" -> terminalYellow
                            "stdin" -> terminalGreen
                            "error" -> Color(0xFFF44747)
                            else -> terminalTextColor
                        }
                        Text(
                            text = out.text,
                            color = color,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.horizontalScroll(scrollState)
                        )
                    }
                }
            }
        }

        if (prootState.status == ProotStatus.ERROR) {
            Text(
                text = "Error: ${prootState.error}",
                color = Color(0xFFF44747),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}
