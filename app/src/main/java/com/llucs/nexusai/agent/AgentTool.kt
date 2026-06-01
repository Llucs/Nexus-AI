package com.llucs.nexusai.agent

import com.llucs.nexusai.terminal.ProotDistro
import com.llucs.nexusai.terminal.ProotStatus
import com.llucs.nexusai.terminal.TerminalSession
import java.io.File

sealed class ToolResult {
    data class Success(val output: String) : ToolResult()
    data class Error(val message: String) : ToolResult()
}

interface AgentTool {
    val name: String
    val description: String
    suspend fun execute(args: Map<String, String>): ToolResult
}

class ExecuteCommandTool(
    private val terminalSession: TerminalSession?,
    private val prootDistro: ProotDistro?
) : AgentTool {
    override val name = "execute_command"
    override val description = "Execute a command in the Linux terminal. Args: cmd (the command to run), timeout_ms (optional, default 60000)"

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val cmd = args["cmd"] ?: return ToolResult.Error("Missing required argument: cmd")
        val timeoutMs = args["timeout_ms"]?.toLongOrNull() ?: 60000L

        if (terminalSession == null) return ToolResult.Error("Terminal not available")

        try {
            if (prootDistro != null) {
                val status = prootDistro.ensureInstalled()
                if (status == ProotStatus.READY) {
                    val result = prootDistro.executeCommand(cmd)
                    return ToolResult.Success(result)
                }
            }
            if (terminalSession.isRunning) {
                val result = terminalSession.executeCommand(cmd, timeoutMs)
                return ToolResult.Success(result)
            }
            val started = terminalSession.start()
            if (!started) return ToolResult.Error("Failed to start terminal")
            val result = terminalSession.executeCommand(cmd, timeoutMs)
            return ToolResult.Success(result)
        } catch (e: Exception) {
            return ToolResult.Error("Command execution failed: ${e.message}")
        }
    }
}

class ReadFileTool(
    private val prootDistro: ProotDistro?,
    private val terminalSession: TerminalSession?
) : AgentTool {
    override val name = "read_file"
    override val description = "Read contents of a file. Args: path (absolute path in the Linux filesystem)"

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult.Error("Missing required argument: path")
        return runInProot("cat \"$path\" 2>&1; echo; wc -l \"$path\" 2>/dev/null | cut -d' ' -f1")
    }

    private suspend fun runInProot(cmd: String): ToolResult {
        try {
            if (prootDistro != null && prootDistro.checkStatus() == ProotStatus.READY) {
                val result = prootDistro.executeCommand(cmd)
                return ToolResult.Success(result)
            }
            if (terminalSession != null && terminalSession.isRunning) {
                val result = terminalSession.executeCommand(cmd)
                return ToolResult.Success(result)
            }
            return ToolResult.Error("No active terminal session")
        } catch (e: Exception) {
            return ToolResult.Error("Read failed: ${e.message}")
        }
    }
}

class WriteFileTool(
    private val prootDistro: ProotDistro?,
    private val terminalSession: TerminalSession?
) : AgentTool {
    override val name = "write_file"
    override val description = "Write content to a file. Args: path (absolute path), content (file contents)"

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: return ToolResult.Error("Missing required argument: path")
        val content = args["content"] ?: return ToolResult.Error("Missing required argument: content")

        val escaped = content
            .replace("\\", "\\\\")
            .replace("'", "'\\''")
            .replace("\n", "\\n")

        val cmd = buildString {
            append("mkdir -p \"$(dirname \"$path\")\" 2>/dev/null; ")
            append("printf '%s\\n' '$escaped' > \"$path\"; ")
            append("echo 'Wrote '$(wc -c < \"$path\")' bytes to $path'")
        }

        try {
            if (prootDistro != null && prootDistro.checkStatus() == ProotStatus.READY) {
                val result = prootDistro.executeCommand(cmd)
                return ToolResult.Success(result)
            }
            if (terminalSession != null && terminalSession.isRunning) {
                val result = terminalSession.executeCommand(cmd)
                return ToolResult.Success(result)
            }
            return ToolResult.Error("No active terminal session")
        } catch (e: Exception) {
            return ToolResult.Error("Write failed: ${e.message}")
        }
    }
}

class ListFilesTool(
    private val prootDistro: ProotDistro?,
    private val terminalSession: TerminalSession?
) : AgentTool {
    override val name = "list_files"
    override val description = "List files in a directory. Args: path (directory path), pattern (optional glob pattern)"

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val path = args["path"] ?: "."
        val pattern = args["pattern"] ?: "*"

        val cmd = buildString {
            append("ls -la ")
            append(if (pattern != "*") "\"$path\"/$pattern 2>/dev/null || " else "")
            append("\"$path\" 2>&1; ")
            append("echo; echo 'TOTAL: '; ls -1 \"$path\" 2>/dev/null | wc -l")
        }

        try {
            if (prootDistro != null && prootDistro.checkStatus() == ProotStatus.READY) {
                val result = prootDistro.executeCommand(cmd)
                return ToolResult.Success(result)
            }
            if (terminalSession != null && terminalSession.isRunning) {
                val result = terminalSession.executeCommand(cmd)
                return ToolResult.Success(result)
            }
            return ToolResult.Error("No active terminal session")
        } catch (e: Exception) {
            return ToolResult.Error("List failed: ${e.message}")
        }
    }
}

class SearchFilesTool(
    private val prootDistro: ProotDistro?,
    private val terminalSession: TerminalSession?
) : AgentTool {
    override val name = "search_files"
    override val description = "Search for text in files. Args: pattern (regex pattern), path (optional, default /root)"

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val pattern = args["pattern"] ?: return ToolResult.Error("Missing required argument: pattern")
        val path = args["path"] ?: "/root"

        val escapedPattern = pattern.replace("'", "'\\''")
        val cmd = "grep -rn --include='*.kt' --include='*.java' --include='*.py' --include='*.js' --include='*.ts' --include='*.json' --include='*.xml' --include='*.sh' --include='*.txt' --include='*.md' '$escapedPattern' \"$path\" 2>&1 | head -100"

        try {
            if (prootDistro != null && prootDistro.checkStatus() == ProotStatus.READY) {
                val result = prootDistro.executeCommand(cmd)
                return ToolResult.Success(result)
            }
            if (terminalSession != null && terminalSession.isRunning) {
                val result = terminalSession.executeCommand(cmd)
                return ToolResult.Success(result)
            }
            return ToolResult.Error("No active terminal session")
        } catch (e: Exception) {
            return ToolResult.Error("Search failed: ${e.message}")
        }
    }
}

class InstallPackageTool(
    private val prootDistro: ProotDistro?
) : AgentTool {
    override val name = "install_package"
    override val description = "Install a Linux package via apt. Args: package (package name), update (optional, 'true' to apt update first)"

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val pkg = args["package"] ?: return ToolResult.Error("Missing required argument: package")
        if (prootDistro == null) return ToolResult.Error("PRoot distro not available")
        if (prootDistro.checkStatus() != ProotStatus.READY) return ToolResult.Error("Ubuntu not installed")

        val update = if (args["update"] == "true") "apt-get update -qq 2>&1 && " else ""
        val cmd = "${update}DEBIAN_FRONTEND=noninteractive apt-get install -y -qq $pkg 2>&1"
        return try {
            val result = prootDistro.executeCommand(cmd)
            ToolResult.Success(result)
        } catch (e: Exception) {
            ToolResult.Error("Install failed: ${e.message}")
        }
    }
}

class ToolRegistry(
    terminalSession: TerminalSession?,
    prootDistro: ProotDistro?
) {
    val tools: Map<String, AgentTool> = listOf(
        ExecuteCommandTool(terminalSession, prootDistro),
        ReadFileTool(prootDistro, terminalSession),
        WriteFileTool(prootDistro, terminalSession),
        ListFilesTool(prootDistro, terminalSession),
        SearchFilesTool(prootDistro, terminalSession),
        InstallPackageTool(prootDistro)
    ).associateBy { it.name }

    fun getToolNames(): String = tools.keys.joinToString(", ")
    fun getToolDescriptions(): String = tools.map { "${it.key}: ${it.value.description}" }.joinToString("\n")
}
