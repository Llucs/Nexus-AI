package com.llucs.nexusai.terminal

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class ProotStatus {
    NOT_INSTALLED, ERROR, READY
}

data class ProotState(
    val status: ProotStatus = ProotStatus.NOT_INSTALLED,
    val error: String? = null
)

class ProotDistro(context: Context) {

    private val _state = MutableStateFlow(ProotState())
    val state: Flow<ProotState> = _state.asStateFlow()

    private val baseDir: File = File(context.filesDir, "nexus-proot")
    private val rootfsDir: File = File(baseDir, "rootfs")
    private val markerFile: File = File(baseDir, ".installed")

    suspend fun ensureInstalled(): ProotStatus {
        return ProotStatus.NOT_INSTALLED
    }

    suspend fun executeCommand(command: String): String {
        return "Ubuntu (proot) not available"
    }

    fun getRootfsDirectory(): File = rootfsDir
}
