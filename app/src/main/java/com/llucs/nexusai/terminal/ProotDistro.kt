package com.llucs.nexusai.terminal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

enum class ProotStatus {
    NOT_INSTALLED, DOWNLOADING, INSTALLING, READY, ERROR
}

data class ProotState(
    val status: ProotStatus = ProotStatus.NOT_INSTALLED,
    val progress: Float = 0f,
    val currentStep: String = "",
    val error: String? = null
)

class ProotDistro(private val context: Context) {

    private val _state = MutableStateFlow(ProotState())
    val state: Flow<ProotState> = _state.asStateFlow()

    private val baseDir: File get() = File(context.filesDir, "nexus-proot")
    private val prootBin: File get() = File(baseDir, "proot")
    private val rootfsDir: File get() = File(baseDir, "rootfs")
    private val markerFile: File get() = File(baseDir, ".installed")

    suspend fun checkStatus(): ProotStatus = withContext(Dispatchers.IO) {
        if (markerFile.exists() && rootfsDir.isDirectory && rootfsDir.list()?.isNotEmpty() == true) {
            _state.value = ProotState(status = ProotStatus.READY)
            ProotStatus.READY
        } else {
            _state.value = ProotState(status = ProotStatus.NOT_INSTALLED)
            ProotStatus.NOT_INSTALLED
        }
    }

    suspend fun install(onOutput: ((String) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            _state.value = ProotState(ProotStatus.DOWNLOADING, 0f, "Creating directories...")

            baseDir.mkdirs()
            rootfsDir.mkdirs()

            val arch = System.getProperty("os.arch") ?: "aarch64"
            val prootArch = when {
                arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
                arch.contains("arm") -> "arm"
                arch.contains("x86_64") || arch.contains("amd64") -> "x86_64"
                arch.contains("x86") || arch.contains("i686") || arch.contains("i386") -> "x86"
                else -> "aarch64"
            }

            _state.value = ProotState(ProotStatus.DOWNLOADING, 0.15f, "Downloading proot binary...")
            onOutput?.invoke("Downloading proot for $prootArch...")
            val prootUrl = "https://github.com/termux/proot/releases/download/v0.1.0/proot-$prootArch"

            downloadFile(prootUrl, prootBin)
            prootBin.setExecutable(true)

            _state.value = ProotState(ProotStatus.INSTALLING, 0.3f, "Downloading Ubuntu rootfs...")
            onOutput?.invoke("Downloading Ubuntu rootfs (minimal)...")

            val rootfsUrl = when (prootArch) {
                "aarch64" -> "https://github.com/termux/proot-distro/releases/download/v4.0.0/rootfs-ubuntu-arm64.tar.xz"
                "arm" -> "https://github.com/termux/proot-distro/releases/download/v4.0.0/rootfs-ubuntu-arm.tar.xz"
                "x86_64" -> "https://github.com/termux/proot-distro/releases/download/v4.0.0/rootfs-ubuntu-i686.tar.xz"
                else -> "https://github.com/termux/proot-distro/releases/download/v4.0.0/rootfs-ubuntu-arm64.tar.xz"
            }

            val rootfsArchive = File(baseDir, "ubuntu-rootfs.tar.xz")
            downloadFile(rootfsUrl, rootfsArchive, onProgress = { p ->
                _state.value = ProotState(ProotStatus.INSTALLING, 0.3f + p * 0.5f, "Downloading Ubuntu rootfs...")
            })

            _state.value = ProotState(ProotStatus.INSTALLING, 0.8f, "Extracting rootfs...")
            onOutput?.invoke("Extracting rootfs (this may take a moment)...")
            extractTarXz(rootfsArchive, rootfsDir)

            rootfsArchive.delete()

            val resolvConf = File(rootfsDir, "etc/resolv.conf")
            resolvConf.parentFile?.mkdirs()
            resolvConf.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")

            markerFile.createNewFile()
            _state.value = ProotState(ProotStatus.READY, 1f, "Ubuntu ready")
            onOutput?.invoke("Ubuntu proot-distro installed successfully")
            true
        } catch (e: Exception) {
            _state.value = ProotState(ProotStatus.ERROR, 0f, "Installation failed", e.message)
            onOutput?.invoke("Error: ${e.message}")
            false
        }
    }

    suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        if (_state.value.status != ProotStatus.READY) {
            return@withContext "proot-distro not installed. Run install first."
        }
        try {
            val pb = ProcessBuilder(
                prootBin.absolutePath,
                "--rootfs=$rootfsDir",
                "--link2symlink",
                "-w", "/root",
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "/bin/bash", "-c", command
            )
            pb.environment()["HOME"] = "/root"
            pb.environment()["TERM"] = "xterm-256color"
            pb.environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            val p = pb.start()
            val stdout = p.inputStream.bufferedReader().readText()
            val stderr = p.errorStream.bufferedReader().readText()
            p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            val combined = buildString {
                if (stdout.isNotBlank()) append(stdout)
                if (stderr.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(stderr)
                }
            }
            combined.ifBlank { "(no output)" }
        } catch (e: Exception) {
            "Command error: ${e.message}"
        }
    }

    private fun downloadFile(urlStr: String, target: File, onProgress: ((Float) -> Unit)? = null) {
        val url = URL(urlStr)
        val conn = url.openConnection()
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        val totalBytes = conn.contentLengthLong
        val input = conn.getInputStream()
        val output = FileOutputStream(target)
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalRead = 0L
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            if (totalBytes > 0) {
                onProgress?.invoke(totalRead.toFloat() / totalBytes)
            }
        }
        output.close()
        input.close()
    }

    private fun extractTarXz(archive: File, dest: File) {
        val pb = ProcessBuilder(
            "tar", "-xJf", archive.absolutePath,
            "-C", dest.absolutePath,
            "--strip-components=1"
        )
        pb.redirectErrorStream(true)
        val p = pb.start()
        p.inputStream.bufferedReader().readText()
        p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
    }

    fun getRootfsDirectory(): File = rootfsDir

    suspend fun uninstall() = withContext(Dispatchers.IO) {
        baseDir.deleteRecursively()
        _state.value = ProotState(ProotStatus.NOT_INSTALLED)
    }
}
