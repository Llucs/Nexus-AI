package com.llucs.nexusai.terminal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

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
            _state.value = ProotState(ProotStatus.INSTALLING, 0f, "Creating directories...")
            baseDir.mkdirs()
            rootfsDir.mkdirs()

            _state.value = ProotState(ProotStatus.INSTALLING, 0.1f, "Extracting proot binaries...")
            onOutput?.invoke("Extracting proot binaries...")
            extractProotFromAssets()

            if (!prootBin.exists()) {
                throw Exception("Failed to extract proot binary from assets")
            }

            _state.value = ProotState(ProotStatus.INSTALLING, 0.2f, "Extracting Ubuntu rootfs...")
            onOutput?.invoke("Extracting Ubuntu rootfs (~84MB)...")
            extractRootfsFromAssets()

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
                if (!outFile.canExecute()) {
                    try {
                        Runtime.getRuntime().exec("chmod 755 ${outFile.absolutePath}").waitFor()
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                throw Exception("Failed to extract $assetPath: ${e.message}")
            }
        }
    }

    private fun extractRootfsFromAssets() {
        val apkPath = context.applicationInfo.sourceDir
        val entryPath = "assets/rootfs/ubuntu-rootfs.tgz"
        try {
            ZipFile(apkPath).use { zip ->
                val entry = zip.getEntry(entryPath)
                    ?: throw Exception("Rootfs entry not found in APK: $entryPath")
                zip.getInputStream(entry).use { input ->
                    extractTarGz(input, rootfsDir)
                }
            }
        } catch (e: Exception) {
            throw Exception("Failed to extract rootfs from APK: ${e.message}")
        }
        rootfsDir.setReadable(true, false)
    }

    private fun extractTarGz(input: InputStream, dest: File) {
        GZIPInputStream(input).use { gz ->
            val buffer = ByteArray(8192)
            var inTar = false
            var currentFile: File? = null
            var currentOut: FileOutputStream? = null
            var remaining = 0L

            while (true) {
                if (!inTar) {
                    val header = ByteArray(512)
                    val bytesRead = gz.read(header, 0, 512)
                    if (bytesRead < 512) break

                    if (header.all { it == 0.toByte() }) break

                    val name = extractTarString(header, 0, 100) ?: break
                    if (name.isEmpty()) continue

                    val size = extractTarOctal(header, 124, 12)
                    val type = header[156].toInt()

                    val entryName = if (name.endsWith("/")) name.dropLast(1) else name
                    val targetFile = File(dest, entryName)

                    if (type == 53) {
                        targetFile.mkdirs()
                        remaining = 0
                    } else {
                        targetFile.parentFile?.mkdirs()
                        currentFile = targetFile
                        currentOut = FileOutputStream(targetFile)
                        remaining = size
                        inTar = true
                    }

                    val padded = (size + 511) / 512 * 512
                    val skip = padded - size
                    if (skip > 0) {
                        var skipped = 0L
                        while (skipped < skip) {
                            val s = gz.skip(skip - skipped)
                            if (s <= 0) break
                            skipped += s
                        }
                    }
                } else {
                    val toRead = minOf(remaining.toInt(), buffer.size)
                    val bytesRead = gz.read(buffer, 0, toRead)
                    if (bytesRead <= 0) {
                        inTar = false
                        currentOut?.close()
                        currentFile?.setExecutable(currentFile!!.name == "proot")
                        currentOut = null
                        currentFile = null
                        continue
                    }
                    currentOut?.write(buffer, 0, bytesRead)
                    remaining -= bytesRead
                    if (remaining <= 0) {
                        inTar = false
                        currentOut?.close()
                        currentFile?.setExecutable(currentFile!!.name == "proot")
                        currentOut = null
                        currentFile = null
                    }
                }
            }
            currentOut?.close()
        }
    }

    private fun extractTarString(data: ByteArray, offset: Int, maxLen: Int): String? {
        val end = (offset until offset + maxLen).firstOrNull { data[it] == 0.toByte() } ?: (offset + maxLen)
        return data.sliceArray(offset until end).decodeToString()
    }

    private fun extractTarOctal(data: ByteArray, offset: Int, maxLen: Int): Long {
        val end = (offset until offset + maxLen).firstOrNull { data[it] == 0.toByte() } ?: (offset + maxLen)
        val str = data.sliceArray(offset until end).decodeToString()
        return str.trim().toLongOrNull(8) ?: 0L
    }

    suspend fun ensureProotExecutable(): Boolean = withContext(Dispatchers.IO) {
        if (prootBin.exists() && !prootBin.canExecute()) {
            prootBin.setExecutable(true)
            if (!prootBin.canExecute()) {
                try {
                    val p = Runtime.getRuntime().exec("chmod 755 ${prootBin.absolutePath}")
                    p.waitFor()
                } catch (_: Exception) {}
            }
        }
        prootBin.canExecute()
    }

    suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        if (_state.value.status != ProotStatus.READY) {
            return@withContext "Ubuntu not installed yet"
        }
        if (!ensureProotExecutable()) {
            return@withContext "Command error: Cannot execute proot binary - permission denied"
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

    fun getRootfsDirectory(): File = rootfsDir

    suspend fun uninstall() = withContext(Dispatchers.IO) {
        baseDir.deleteRecursively()
        _state.value = ProotState(ProotStatus.NOT_INSTALLED)
    }
}
