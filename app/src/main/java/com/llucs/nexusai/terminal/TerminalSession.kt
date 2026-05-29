package com.llucs.nexusai.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

data class TerminalOutput(
    val text: String,
    val stream: String,
    val timestamp: Long = System.currentTimeMillis()
)

class TerminalSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: Process? = null
    private var stdin: OutputStream? = null
    private var readerJob: Job? = null
    private var processThread: Thread? = null

    private val _output = MutableSharedFlow<TerminalOutput>(
        replay = 256,
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val output: Flow<TerminalOutput> = _output.asSharedFlow()

    private val _history = mutableListOf<TerminalOutput>()
    val history: List<TerminalOutput> get() = _history.toList()

    private val _running = AtomicBoolean(false)
    val isRunning: Boolean get() = _running.get()

    private val commandQueue = ConcurrentLinkedQueue<String>()
    private val processing = AtomicBoolean(false)

    suspend fun start(shell: String = "/system/bin/sh"): Boolean = withContext(Dispatchers.IO) {
        if (_running.get()) return@withContext false
        try {
            val pb = ProcessBuilder(shell)
            pb.redirectErrorStream(false)
            pb.environment()["TERM"] = "xterm-256color"
            pb.environment()["HOME"] = System.getProperty("user.home") ?: "/data/data/com.llucs.nexusai/files"
            val p = pb.start()
            process = p
            stdin = p.outputStream
            _running.set(true)

            processThread = Thread {
                val buf = ByteArray(4096)
                try {
                    while (_running.get() && p.isAlive) {
                        val read = p.inputStream.read(buf)
                        if (read <= 0) break
                        val text = String(buf, 0, read, Charsets.UTF_8)
                        val line = TerminalOutput(text, "stdout")
                        _history.add(line)
                        _output.tryEmit(line)
                    }
                } catch (_: Exception) {}
            }.apply {
                isDaemon = true
                name = "terminal-stdout"
                start()
            }

            Thread {
                val buf = ByteArray(4096)
                try {
                    while (_running.get() && p.isAlive) {
                        val read = p.errorStream.read(buf)
                        if (read <= 0) break
                        val text = String(buf, 0, read, Charsets.UTF_8)
                        val line = TerminalOutput(text, "stderr")
                        _history.add(line)
                        _output.tryEmit(line)
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
            _output.tryEmit(TerminalOutput("$ $command\n", "stdin"))
        } catch (e: Exception) {
            _output.tryEmit(TerminalOutput("Write error: ${e.message}", "error"))
        }
    }

    suspend fun executeCommand(command: String, timeoutMs: Long = 30000): String = withContext(Dispatchers.IO) {
        if (!_running.get()) return@withContext "Terminal not running"
        val marker = "CMD_DONE_${System.nanoTime()}"
        val sb = StringBuilder()
        val job = scope.launch {
            _output.collect { out ->
                sb.append(out.text)
                if (sb.contains(marker)) cancel()
            }
        }
        try {
            write(command)
            write("echo $marker")
            delay(timeoutMs)
        } catch (_: kotlinx.coroutines.CancellationException) {}
        finally { job.cancel() }
        sb.toString()
    }

    fun clearHistory() {
        _history.clear()
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
        scope.cancel()
    }
}
