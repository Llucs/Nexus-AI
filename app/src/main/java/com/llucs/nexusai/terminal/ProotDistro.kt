package com.llucs.nexusai.terminal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
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

    val baseDir: File get() = File(context.filesDir, "nexus-proot")
    val prootBin: File get() = File(baseDir, "proot")
    val rootfsDir: File get() = File(baseDir, "rootfs")
    private val markerFile: File get() = File(baseDir, ".installed")

    companion object {
        private val ROOTFS_URLS = mapOf(
            "aarch64" to listOf(
                "https://github.com/termux/proot-distro/releases/download/v4.29.0/ubuntu-plucky-aarch64-pd-v4.29.0.tar.xz",
                "https://github.com/termux/proot-distro/releases/download/v4.28.0/ubuntu-plucky-aarch64-pd-v4.28.0.tar.xz"
            ),
            "x86_64" to listOf(
                "https://github.com/termux/proot-distro/releases/download/v4.29.0/ubuntu-plucky-x86_64-pd-v4.29.0.tar.xz",
                "https://github.com/termux/proot-distro/releases/download/v4.28.0/ubuntu-plucky-x86_64-pd-v4.28.0.tar.xz"
            ),
            "armhf" to listOf(
                "https://github.com/termux/proot-distro/releases/download/v4.29.0/ubuntu-plucky-armhf-pd-v4.29.0.tar.xz"
            )
        )
    }

    private fun getArch(): String {
        val arch = System.getProperty("os.arch") ?: "aarch64"
        return when {
            arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
            arch.contains("arm") -> "armhf"
            arch.contains("x86_64") || arch.contains("amd64") -> "x86_64"
            else -> "aarch64"
        }
    }

    private fun urlExists(urlStr: String): Boolean {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 10000
            conn.readTimeout = 5000
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399
        } catch (_: Exception) {
            false
        }
    }

    private fun findWorkingUrl(urls: List<String>): String? {
        for (url in urls) {
            if (urlExists(url)) return url
        }
        return null
    }

    suspend fun checkStatus(): ProotStatus = withContext(Dispatchers.IO) {
        if (markerFile.exists() && rootfsDir.isDirectory && rootfsDir.list()?.isNotEmpty() == true && prootBin.exists()) {
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

            _state.value = ProotState(ProotStatus.DOWNLOADING, 0.1f, "Extracting proot binary...")
            onOutput?.invoke("Extracting proot binary...")
            extractProotFromAssets()

            if (!prootBin.exists()) {
                throw Exception("Failed to extract proot binary from assets")
            }

            val arch = getArch()
            val rootfsCandidates = ROOTFS_URLS[arch] ?: ROOTFS_URLS["aarch64"]!!

            _state.value = ProotState(ProotStatus.INSTALLING, 0.3f, "Verifying Ubuntu rootfs URL...")
            onOutput?.invoke("Verifying Ubuntu rootfs URL...")

            val workingRootfsUrl = findWorkingUrl(rootfsCandidates)
            if (workingRootfsUrl == null) {
                throw Exception(
                    "Nenhum servidor de rootfs respondeu. URLs testadas:\n" +
                    rootfsCandidates.joinToString("\n") + "\n" +
                    "Verifique sua conexao com internet."
                )
            }

            _state.value = ProotState(ProotStatus.INSTALLING, 0.35f, "Downloading Ubuntu rootfs...")
            onOutput?.invoke("Downloading Ubuntu rootfs...")

            val rootfsArchive = File(baseDir, "ubuntu-rootfs.tar.xz")
            downloadFile(workingRootfsUrl, rootfsArchive, onProgress = { p ->
                _state.value = ProotState(ProotStatus.INSTALLING, 0.35f + p * 0.45f, "Downloading Ubuntu rootfs...")
            })

            if (!rootfsArchive.exists() || rootfsArchive.length() < 1024 * 1024) {
                throw Exception("Rootfs download failed or file too small (${rootfsArchive.length()} bytes)")
            }

            _state.value = ProotState(ProotStatus.INSTALLING, 0.8f, "Extracting rootfs...")
            onOutput?.invoke("Extracting rootfs...")
            extractRootfs(rootfsArchive, rootfsDir)
            rootfsArchive.delete()

            val resolvConf = File(rootfsDir, "etc/resolv.conf")
            resolvConf.parentFile?.mkdirs()
            resolvConf.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
            File(rootfsDir, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")

            markerFile.createNewFile()
            _state.value = ProotState(ProotStatus.READY, 1f, "Ubuntu ready")
            onOutput?.invoke("Ubuntu ready")
            true
        } catch (e: Exception) {
            _state.value = ProotState(ProotStatus.ERROR, 0f, "Installation failed", e.message)
            onOutput?.invoke("Error: ${e.message}")
            false
        }
    }

    private fun extractProotFromAssets() {
        val assetManager = context.assets
        val files = listOf("proot/proot", "proot/loader", "proot/loader32")
        for (assetPath in files) {
            val fileName = assetPath.substringAfterLast("/")
            val outFile = File(baseDir, fileName)
            try {
                assetManager.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                outFile.setExecutable(true)
            } catch (e: Exception) {
                throw Exception("Failed to extract $assetPath: ${e.message}")
            }
        }
    }

    suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        if (_state.value.status != ProotStatus.READY) {
            return@withContext "Ubuntu not installed yet"
        }
        try {
            val loaderDir = baseDir.absolutePath
            val env = mapOf(
                "HOME" to "/root",
                "TERM" to "xterm-256color",
                "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "PROOT_LOADER_DIR" to loaderDir
            )
            val cmd = listOf(
                prootBin.absolutePath,
                "--link2symlink",
                "-0",
                "-r", rootfsDir.absolutePath,
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-w", "/root",
                "/usr/bin/env",
                "-i",
                "HOME=/root",
                "TERM=xterm-256color",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "/bin/bash", "-c", command
            )
            val pb = ProcessBuilder(cmd)
            pb.environment().putAll(env)
            val p = pb.start()
            val stdout = p.inputStream.bufferedReader().readText()
            val stderr = p.errorStream.bufferedReader().readText()
            p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
            val exitCode = p.exitValue()
            buildString {
                if (stdout.isNotBlank()) append(stdout)
                if (stderr.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(stderr)
                }
                if (exitCode != 0) {
                    if (isNotEmpty()) append("\n")
                    append("(exit code: $exitCode)")
                }
            }.ifBlank { if (exitCode == 0) "" else "(exit code: $exitCode)" }
        } catch (e: Exception) {
            "Command error: ${e.message}"
        }
    }

    suspend fun ensureInstalled(onOutput: ((String) -> Unit)? = null): ProotStatus = withContext(Dispatchers.IO) {
        val status = checkStatus()
        if (status == ProotStatus.READY) return@withContext ProotStatus.READY
        val ok = install(onOutput)
        if (ok) ProotStatus.READY else _state.value.status
    }

    private fun extractRootfs(archive: File, dest: File) {
        val name = archive.name
        val cmd = mutableListOf<String>()
        when {
            name.endsWith(".xz") -> cmd.addAll(listOf("tar", "-xJf"))
            name.endsWith(".gz") -> cmd.addAll(listOf("tar", "-xzf"))
            else -> cmd.addAll(listOf("tar", "-xf"))
        }
        cmd.add(archive.absolutePath)
        cmd.add("-C")
        cmd.add(dest.absolutePath)
        cmd.add("--strip-components=1")
        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        val p = pb.start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
        if (p.exitValue() != 0) {
            throw Exception("tar extraction failed: $out")
        }
    }

    private fun downloadFile(urlStr: String, target: File, onProgress: ((Float) -> Unit)? = null) {
        val url = URL(urlStr)
        val conn = url.openConnection()
        conn.connectTimeout = 15000
        conn.readTimeout = 120000
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

    fun getRootfsDirectory(): File = rootfsDir

    suspend fun uninstall() = withContext(Dispatchers.IO) {
        baseDir.deleteRecursively()
        _state.value = ProotState(ProotStatus.NOT_INSTALLED)
    }
}
