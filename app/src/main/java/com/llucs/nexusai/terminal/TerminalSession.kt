package com.llucs.nexusai.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

data class TerminalOutput(
    val text: String,
    val stream: String,
    val timestamp: Long = System.currentTimeMillis()
)

class TerminalSession(
    private val prootBin: String? = null,
    private val rootfsDir: String? = null
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: Process? = null
    private var stdin: OutputStream? = null
    private var readerJob: Job? = null
    private var processThread: Thread? = null

    private val _output = MutableSharedFlow<TerminalOutput>(
        replay = 512,
        extraBufferCapacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val output: Flow<TerminalOutput> = _output.asSharedFlow()

    private val _history = mutableListOf<TerminalOutput>()
    val history: List<TerminalOutput> get() = _history.toList()

    private val _running = AtomicBoolean(false)
    val isRunning: Boolean get() = _running.get()

    private val commandQueue = ConcurrentLinkedQueue<String>()
    private val processing = AtomicBoolean(false)

    private fun fixExecPerms(file: File) {
        if (!file.exists()) return
        file.setExecutable(true, false)
        file.setReadable(true, false)
        if (!file.canExecute()) {
            try { Runtime.getRuntime().exec("chmod 755 " + file.absolutePath).waitFor() } catch (_: Exception) {}
            try { Runtime.getRuntime().exec(arrayOf("/system/bin/chmod", "755", file.absolutePath)).waitFor() } catch (_: Exception) {}
        }
    }

    suspend fun start(shell: String = "/system/bin/sh"): Boolean = withContext(Dispatchers.IO) {
        if (_running.get()) return@withContext false
        try {
            val cmd: List<String>
            val env: MutableMap<String, String> = HashMap()

            if (prootBin != null && rootfsDir != null && File(prootBin).exists() && File(rootfsDir).exists()) {
                fixExecPerms(File(prootBin))
                fixExecPerms(File(prootBin).parentFile?.let { File(it, "loader") })
                fixExecPerms(File(prootBin).parentFile?.let { File(it, "loader32") })
                cmd = listOf(
                    prootBin,
                    "--link2symlink",
                    "-0",
                    "-r", rootfsDir,
                    "-b", "/dev",
                    "-b", "/proc",
                    "-b", "/sys",
                    "-b", "/sdcard",
                    "/usr/bin/env",
                    "-i",
                    "HOME=/root",
                    "TERM=xterm-256color",
                    "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                    "LD_LIBRARY_PATH=/usr/local/lib:/usr/lib:/lib",
                    "/bin/bash", "--login"
                )
                env["HOME"] = "/root"
                env["TERM"] = "xterm-256color"
                env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            } else {
                cmd = listOf(shell)
                env["TERM"] = "xterm-256color"
                env["HOME"] = System.getProperty("user.home") ?: "/data/data/com.llucs.nexusai/files"
            }

            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(false)
            pb.environment().putAll(env)
            val p = pb.start()
            process = p
            stdin = p.outputStream
            _running.set(true)

            val buf = ByteArray(8192)
            processThread = Thread {
                try {
                    while (_running.get() && p.isAlive) {
                        val read = p.inputStream.read(buf)
                        if (read <= 0) break
                        val text = String(buf, 0, read, Charsets.UTF_8)
                        if (text.isNotBlank()) {
                            val line = TerminalOutput(text, "stdout")
                            synchronized(_history) { _history.add(line) }
                            _output.tryEmit(line)
                        }
                    }
                } catch (_: Exception) {}
            }.apply {
                isDaemon = true
                name = "terminal-stdout"
                start()
            }

            Thread {
                try {
                    while (_running.get() && p.isAlive) {
                        val read = p.errorStream.read(buf)
                        if (read <= 0) break
                        val text = String(buf, 0, read, Charsets.UTF_8)
                        if (text.isNotBlank()) {
                            val line = TerminalOutput(text, "stderr")
                            synchronized(_history) { _history.add(line) }
                            _output.tryEmit(line)
                        }
                    }
                } catch (_: Exception) {}
            }.apply {
                isDaemon = true
                name = "terminal-stderr"
                start()
            }

            Thread {
                while (_running.get() && p.isAlive) {
                    try {
                        p.waitFor()
                    } catch (_: InterruptedException) { break }
                    _running.set(false)
                }
            }.apply {
                isDaemon = true
                name = "terminal-watcher"
                start()
            }

            true
        } catch (e: Exception) {
            _running.set(false)
            _output.tryEmit(TerminalOutput("Failed to start terminal: ${e.message}", "error"))
            false
        }
    }

    suspend fun write(command: String) = withContext(Dispatchers.IO) {
        try {
            stdin?.write((command + "\n").toByteArray(Charsets.UTF_8))
            stdin?.flush()
        } catch (e: Exception) {
            _output.tryEmit(TerminalOutput("Write error: ${e.message}", "error"))
        }
    }

    suspend fun executeCommand(command: String, timeoutMs: Long = 60000): String = withContext(Dispatchers.IO) {
        if (!_running.get()) return@withContext "Terminal not running"
        val marker = "NEXUS_CMD_DONE_${System.nanoTime()}"
        val fullCommand = "echo '---NEXUS-CMD-START---'; $command; echo '$marker'"

        val sb = StringBuilder()
        val job = scope.launch {
            _output.collect { out ->
                sb.append(out.text)
                if (out.text.contains(marker)) {
                    cancel()
                }
            }
        }
        try {
            stdin?.write((fullCommand + "\n").toByteArray(Charsets.UTF_8))
            stdin?.flush()
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (sb.contains(marker)) break
                kotlinx.coroutines.delay(50)
            }
        } catch (_: kotlinx.coroutines.CancellationException) {}
        finally { job.cancel() }

        val raw = sb.toString()
        val startIdx = raw.indexOf("---NEXUS-CMD-START---")
        if (startIdx < 0) return@withContext raw.trim()
        val afterStart = raw.substring(startIdx + "---NEXUS-CMD-START---".length)
        val endIdx = afterStart.lastIndexOf(marker)
        if (endIdx < 0) return@withContext afterStart.trim()
        val result = afterStart.substring(0, endIdx).trim()
        result
    }

    fun clearHistory() {
        synchronized(_history) { _history.clear() }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        _running.set(false)
        try { stdin?.close() } catch (_: Exception) {}
        process?.let { p ->
            try {
                p.destroyForcibly()
                p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            } catch (_: Exception) {}
        }
        process = null
        stdin = null
    }
}
