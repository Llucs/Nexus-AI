package com.llucs.nexusai.planning

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class Task(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val subtasks: List<Task> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class TaskStatus {
    PENDING, IN_PROGRESS, COMPLETED, CANCELLED, BLOCKED
}

@Serializable
data class Plan(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val goal: String = "",
    val tasks: List<Task> = emptyList(),
    val status: TaskStatus = TaskStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

private val Context.planStore by preferencesDataStore(name = "nexusai_plans")

class PlanningStore(private val context: Context) {

    private val keyPlans = stringPreferencesKey("plans_json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun loadPlans(): List<Plan> {
        val prefs = context.planStore.data.first()
        val raw = prefs[keyPlans] ?: return emptyList()
        return try {
            json.decodeFromString<List<Plan>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun savePlans(plans: List<Plan>) {
        val raw = json.encodeToString(plans)
        context.planStore.edit { p -> p[keyPlans] = raw }
    }

    suspend fun upsertPlan(plan: Plan) {
        val plans = loadPlans().toMutableList()
        val idx = plans.indexOfFirst { it.id == plan.id }
        val updated = plan.copy(updatedAt = System.currentTimeMillis())
        if (idx >= 0) plans[idx] = updated else plans.add(0, updated)
        savePlans(plans)
    }

    suspend fun deletePlan(id: String) {
        val plans = loadPlans().filterNot { it.id == id }
        savePlans(plans)
    }

    suspend fun updateTaskStatus(planId: String, taskId: String, status: TaskStatus) {
        val plans = loadPlans().toMutableList()
        val planIdx = plans.indexOfFirst { it.id == planId }
        if (planIdx < 0) return
        val plan = plans[planIdx]
        val updatedTasks = updateTaskInList(plan.tasks, taskId, status)
        plans[planIdx] = plan.copy(tasks = updatedTasks, updatedAt = System.currentTimeMillis())

        val allDone = updatedTasks.all { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.CANCELLED }
        if (allDone && updatedTasks.isNotEmpty()) {
            plans[planIdx] = plans[planIdx].copy(status = TaskStatus.COMPLETED)
        }

        savePlans(plans)
    }

    private fun updateTaskInList(tasks: List<Task>, taskId: String, status: TaskStatus): List<Task> {
        return tasks.map { task ->
            if (task.id == taskId) {
                task.copy(status = status)
            } else if (task.subtasks.isNotEmpty()) {
                task.copy(subtasks = updateTaskInList(task.subtasks, taskId, status))
            } else task
        }
    }

    suspend fun getActivePlan(): Plan? {
        return loadPlans().firstOrNull { it.status == TaskStatus.PENDING || it.status == TaskStatus.IN_PROGRESS }
    }

    suspend fun clearAll() {
        context.planStore.edit { p -> p.remove(keyPlans) }
    }

    companion object {
        fun parsePlanFromResponse(aiResponse: String): Pair<String, List<Task>> {
            val lines = aiResponse.lines()
            val tasks = mutableListOf<Task>()
            var title = "Plan"
            var inTaskList = false

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("# Plan:") || trimmed.startsWith("## Plan") || trimmed.startsWith("Plano:")) {
                    title = trimmed.substringAfter(":").trim().ifBlank { "Plan" }
                    inTaskList = false
                    continue
                }

                val checklistMatch = Regex("^[-*]\\s*\\[([ xX])\\]\\s*(.+)").find(trimmed)
                if (checklistMatch != null) {
                    inTaskList = true
                    val checked = checklistMatch.groupValues[1]
                    val desc = checklistMatch.groupValues[2].trim()
                    val status = when {
                        checked.equals("x", ignoreCase = true) -> TaskStatus.COMPLETED
                        else -> TaskStatus.PENDING
                    }
                    tasks.add(Task(description = desc, status = status))
                    continue
                }

                val numberMatch = Regex("^\\d+\\.\\s*(.+)").find(trimmed)
                if (numberMatch != null && inTaskList) {
                    tasks.add(Task(description = numberMatch.groupValues[1].trim()))
                    continue
                }

                if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                    val desc = trimmed.drop(2).trim()
                    if (desc.isNotBlank() && !desc.startsWith("[")) {
                        inTaskList = false
                    }
                }
            }

            return title to tasks
        }
    }
}
