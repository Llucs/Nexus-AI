package com.llucs.nexusai.agent

data class AgentAction(
    val type: String,
    val tool: String,
    val args: Map<String, String>,
    val timestamp: Long = System.currentTimeMillis()
)

data class AgentStep(
    val stepNumber: Int,
    val thought: String,
    val action: AgentAction,
    val result: ToolResult,
    val completed: Boolean = false
)

data class AgentPlan(
    val goal: String,
    val steps: List<AgentAction>,
    val completedSteps: Int = 0,
    val totalSteps: Int = 0
)

class AgentSession(
    private val registry: ToolRegistry
) {
    private val steps = mutableListOf<AgentStep>()
    private var currentPlan: AgentPlan? = null

    fun getSteps(): List<AgentStep> = steps.toList()

    fun getLastResult(): String? = steps.lastOrNull()?.let { step ->
        when (val result = step.result) {
            is ToolResult.Success -> result.output
            is ToolResult.Error -> "ERROR: ${result.message}"
        }
    }

    fun getContext(): String {
        val sb = StringBuilder()
        sb.appendLine("## Agent Session Context")
        sb.appendLine()

        if (currentPlan != null) {
            sb.appendLine("### Current Plan")
            sb.appendLine("Goal: ${currentPlan!!.goal}")
            sb.appendLine("Progress: ${currentPlan!!.completedSteps}/${currentPlan!!.totalSteps}")
            sb.appendLine()
        }

        sb.appendLine("### Available Tools")
        sb.appendLine(registry.getToolDescriptions())
        sb.appendLine()

        if (steps.isNotEmpty()) {
            sb.appendLine("### Recent Steps (last ${steps.size})")
            for (step in steps.takeLast(10)) {
                val status = if (step.completed) "DONE" else "FAILED"
                sb.appendLine("Step ${step.stepNumber}: ${step.thought}")
                sb.appendLine("  Action: ${step.action.type} ${step.action.tool}(${step.action.args})")
                sb.appendLine("  Status: $status")
                when (val r = step.result) {
                    is ToolResult.Success -> {
                        val preview = r.output.take(300)
                        sb.appendLine("  Output: $preview")
                    }
                    is ToolResult.Error -> sb.appendLine("  Error: ${r.message}")
                }
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    suspend fun executeStep(thought: String, action: AgentAction): AgentStep {
        val tool = registry.tools[action.tool]
        if (tool == null) {
            val errorStep = AgentStep(
                stepNumber = steps.size + 1,
                thought = thought,
                action = action,
                result = ToolResult.Error("Unknown tool: ${action.tool}. Available: ${registry.getToolNames()}"),
                completed = false
            )
            steps.add(errorStep)
            return errorStep
        }

        val result = tool.execute(action.args)
        val completed = result is ToolResult.Success
        val step = AgentStep(
            stepNumber = steps.size + 1,
            thought = thought,
            action = action,
            result = result,
            completed = completed
        )
        steps.add(step)

        currentPlan?.let { plan ->
            if (completed) {
                currentPlan = plan.copy(completedSteps = plan.completedSteps + 1)
            }
        }

        return step
    }

    fun setPlan(plan: AgentPlan) {
        currentPlan = plan
    }

    fun clear() {
        steps.clear()
        currentPlan = null
    }

    suspend fun parseAndExecutePlan(llmPlan: String): List<String> {
        val results = mutableListOf<String>()
        val lines = llmPlan.lines()
        var currentThought = ""
        var parsingThought = false

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.startsWith("THOUGHT:")) {
                currentThought = trimmed.removePrefix("THOUGHT:").trim()
                parsingThought = true
                continue
            }

            if (trimmed.startsWith("TOOL:")) {
                val toolLine = trimmed.removePrefix("TOOL:").trim()
                val toolName = toolLine.substringBefore("(").trim()
                val argsStr = toolLine.substringAfter("(").substringBeforeLast(")").trim()

                val args = mutableMapOf<String, String>()
                if (argsStr.isNotBlank()) {
                    val pairs = argsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    for (pair in pairs) {
                        val eqIdx = pair.indexOf("=")
                        if (eqIdx > 0) {
                            val key = pair.substring(0, eqIdx).trim()
                            val value = pair.substring(eqIdx + 1).trim()
                                .removeSurrounding("\"").removeSurrounding("'")
                            args[key] = value
                        }
                    }
                }

                val action = AgentAction(
                    type = "tool_call",
                    tool = toolName,
                    args = args
                )

                val stepResult = executeStep(currentThought, action)
                results.add(
                    when (val r = stepResult.result) {
                        is ToolResult.Success -> r.output
                        is ToolResult.Error -> "ERROR: ${r.message}"
                    }
                )
                currentThought = ""
                parsingThought = false
                continue
            }

            if (parsingThought && trimmed.isNotBlank()) {
                currentThought += " $trimmed"
            }
        }

        return results
    }
}
