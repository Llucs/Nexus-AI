package com.llucs.nexusai.terminal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
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
        private val PROOT_DEB_URLS = mapOf(
            "aarch64" to listOf(
                "https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107.74_aarch64.deb",
                "https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107.73_aarch64.deb"
            ),
            "arm" to listOf(
                "https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107.74_arm.deb",
                "https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107.73_arm.deb"
            ),
            "x86_64" to listOf(
                "https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107.74_x86_64.deb",
                "https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107.73_x86_64.deb"
            ),
            "i686" to listOf(
                "https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107.74_i686.deb",
                "https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107.73_i686.deb"
            )
        )

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

    private fun getProotArch(): String {
        val arch = System.getProperty("os.arch") ?: "aarch64"
        return when {
            arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
            arch.contains("arm") -> "arm"
            arch.contains("x86_64") || arch.contains("amd64") -> "x86_64"
            arch.contains("x86") || arch.contains("i686") || arch.contains("i386") -> "i686"
            else -> "aarch64"
        }
    }

    private fun getUbuntuArch(prootArch: String): String = when (prootArch) {
        "i686" -> "x86_64"
        "arm" -> "armhf"
        else -> prootArch
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

            val prootArch = getProotArch()

            _state.value = ProotState(ProotStatus.DOWNLOADING, 0.1f, "Downloading proot binary...")
            onOutput?.invoke("Downloading proot binary...")

            val debUrls = PROOT_DEB_URLS[prootArch] ?: PROOT_DEB_URLS["aarch64"]!!
            val workingDebUrl = findWorkingUrl(debUrls)
                ?: throw Exception("Nenhuma URL de proot respondeu. Verifique sua internet e tente novamente.")
            val debFile = File(baseDir, "proot.deb")

            downloadFile(workingDebUrl, debFile)
            extractProotFromDeb(debFile, prootBin)
            debFile.delete()
            prootBin.setExecutable(true)

            if (!prootBin.exists()) {
                throw Exception("Failed to extract proot binary from any source")
            }

            _state.value = ProotState(ProotStatus.INSTALLING, 0.3f, "Downloading Ubuntu rootfs...")
            onOutput?.invoke("Downloading Ubuntu rootfs...")

            val ubuntuArch = getUbuntuArch(prootArch)
            val rootfsCandidates = ROOTFS_URLS[ubuntuArch] ?: ROOTFS_URLS["aarch64"]!!
            val workingRootfsUrl = findWorkingUrl(rootfsCandidates)
                ?: throw Exception("Nenhuma URL de rootfs Ubuntu respondeu. Verifique sua internet.")
            val rootfsArchive = File(baseDir, "ubuntu-rootfs.tar.xz")

            downloadFile(workingRootfsUrl, rootfsArchive, onProgress = { p ->
                _state.value = ProotState(ProotStatus.INSTALLING, 0.3f + p * 0.5f, "Downloading Ubuntu rootfs...")
            })

            _state.value = ProotState(ProotStatus.INSTALLING, 0.8f, "Extracting rootfs...")
            onOutput?.invoke("Extracting rootfs...")
            extractAnyTar(rootfsArchive, rootfsDir, stripComponents = 1)
            rootfsArchive.delete()

            val resolvConf = File(rootfsDir, "etc/resolv.conf")
            resolvConf.parentFile?.mkdirs()
            resolvConf.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
            File(rootfsDir, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")

            val fstab = File(rootfsDir, "etc/fstab")
            if (!fstab.exists()) fstab.writeText("# Android fstab - not used\n")

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

    suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        if (_state.value.status != ProotStatus.READY) {
            return@withContext "Ubuntu not installed yet"
        }
        try {
            val pb = ProcessBuilder(
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
            pb.environment()["HOME"] = "/root"
            pb.environment()["TERM"] = "xterm-256color"
            pb.environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
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

    private fun extractProotFromDeb(debFile: File, outputFile: File) {
        val tmpDir = File(baseDir, "deb_extract")
        tmpDir.mkdirs()

        try {
            val raf = RandomAccessFile(debFile, "r")
            val magic = ByteArray(8)
            raf.readFully(magic)
            val magicStr = String(magic, Charsets.US_ASCII)
            if (magicStr != "!<arch>\n") throw Exception("Not a valid deb archive")

            while (raf.filePointer < raf.length()) {
                val hdr = ByteArray(60)
                raf.readFully(hdr)
                val name = String(hdr, 0, 16, Charsets.US_ASCII).trim()
                val sizeStr = String(hdr, 48, 10, Charsets.US_ASCII).trim()
                val size = sizeStr.toLongOrNull() ?: 0L
                val padded = size + (size % 2L)

                if (name.startsWith("data.tar")) {
                    val dataBytes = ByteArray(size.toInt())
                    raf.readFully(dataBytes)
                    val tarFile = File(tmpDir, name)
                    FileOutputStream(tarFile).use { it.write(dataBytes) }
                    extractAnyTar(tarFile, tmpDir)
                    tarFile.delete()

                    val found = findProotBinary(tmpDir)
                    if (found != null) {
                        found.copyTo(outputFile, overwrite = true)
                        raf.close()
                        return
                    }
                }

                raf.skipBytes(padded.toInt())
            }
            raf.close()
            throw Exception("data.tar not found in deb")
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    private fun findProotBinary(dir: File): File? {
        val files = dir.walkTopDown().filter { it.name == "proot" && it.isFile }.toList()
        if (files.isNotEmpty()) return files.first()
        val altFiles = dir.walkTopDown().filter { it.name.contains("proot") && it.isFile }.toList()
        if (altFiles.isNotEmpty()) return altFiles.first()
        return null
    }

    private fun extractAnyTar(archive: File, dest: File, stripComponents: Int = 0) {
        val name = archive.name
        val cmd = mutableListOf<String>()
        when {
            name.endsWith(".xz") -> { cmd.addAll(listOf("tar", "-xJf")) }
            name.endsWith(".gz") || name.endsWith(".gzip") -> { cmd.addAll(listOf("tar", "-xzf")) }
            name.endsWith(".zst") || name.endsWith(".zstd") -> { cmd.addAll(listOf("tar", "--zstd", "-xf")) }
            else -> { cmd.addAll(listOf("tar", "-xf")) }
        }
        cmd.add(archive.absolutePath)
        cmd.add("-C")
        cmd.add(dest.absolutePath)
        if (stripComponents > 0) {
            cmd.add("--strip-components=$stripComponents")
        }
        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        val p = pb.start()
        p.inputStream.bufferedReader().readText()
        p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
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
