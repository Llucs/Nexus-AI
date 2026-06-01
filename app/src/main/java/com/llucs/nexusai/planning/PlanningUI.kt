package com.llucs.nexusai.planning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PlanCard(
    plan: Plan,
    onToggleTask: (planId: String, taskId: String, newStatus: TaskStatus) -> Unit,
    onDeletePlan: (String) -> Unit,
    onDeleteTask: (planId: String, taskId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val completedCount = plan.tasks.count { it.status == TaskStatus.COMPLETED }
    val totalCount = plan.tasks.size

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.TaskAlt,
                    contentDescription = null,
                    tint = if (plan.status == TaskStatus.COMPLETED) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (totalCount > 0) {
                        Text(
                            text = "$completedCount/$totalCount completed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { onDeletePlan(plan.id) }) {
                    Icon(Icons.Filled.Delete, "Delete plan", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (plan.goal.isNotBlank()) {
                Text(
                    text = plan.goal,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            plan.tasks.forEach { task ->
                TaskRow(
                    task = task,
                    planId = plan.id,
                    onToggle = onToggleTask,
                    onDelete = onDeleteTask,
                    indentLevel = 0
                )
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    planId: String,
    onToggle: (String, String, TaskStatus) -> Unit,
    onDelete: (String, String) -> Unit,
    indentLevel: Int
) {
    val isCompleted = task.status == TaskStatus.COMPLETED
    val isInProgress = task.status == TaskStatus.IN_PROGRESS
    val isCancelled = task.status == TaskStatus.CANCELLED

    val icon = when {
        isCompleted -> Icons.Filled.CheckCircle
        isInProgress -> Icons.Filled.TaskAlt
        else -> Icons.Filled.RadioButtonUnchecked
    }
    val iconTint = when {
        isCompleted -> MaterialTheme.colorScheme.primary
        isCancelled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val nextStatus = when (task.status) {
        TaskStatus.PENDING -> TaskStatus.IN_PROGRESS
        TaskStatus.IN_PROGRESS -> TaskStatus.COMPLETED
        TaskStatus.COMPLETED -> TaskStatus.PENDING
        else -> TaskStatus.PENDING
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indentLevel * 20).dp, top = 4.dp, bottom = 4.dp)
            .clickable { onToggle(planId, task.id, nextStatus) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (isCompleted) "Completed" else "Pending",
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = task.description,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCancelled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (task.subtasks.isNotEmpty()) {
            IconButton(onClick = { onDelete(planId, task.id) }) {
                Icon(Icons.Filled.Delete, "Delete task", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }

    if (task.subtasks.isNotEmpty()) {
        task.subtasks.forEach { sub ->
            TaskRow(
                task = sub,
                planId = planId,
                onToggle = onToggle,
                onDelete = onDelete,
                indentLevel = indentLevel + 1
            )
        }
    }
}

@Composable
fun CreatePlanDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, goal: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Plan") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text("Plan title") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it.take(200) },
                    label = { Text("Goal (optional)") },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title.trim(), goal.trim()) },
                enabled = title.trim().isNotEmpty()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PlanningPanel(
    activePlan: Plan?,
    plans: List<Plan>,
    onToggleTask: (planId: String, taskId: String, newStatus: TaskStatus) -> Unit,
    onDeletePlan: (String) -> Unit,
    onDeleteTask: (planId: String, taskId: String) -> Unit,
    onCreatePlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        if (activePlan != null) {
            PlanCard(
                plan = activePlan,
                onToggleTask = onToggleTask,
                onDeletePlan = onDeletePlan,
                onDeleteTask = onDeleteTask
            )
        }

        val otherPlans = plans.filter { it.id != activePlan?.id }.take(3)
        if (otherPlans.isNotEmpty()) {
            otherPlans.forEach { plan ->
                Spacer(Modifier.height(8.dp))
                PlanCard(
                    plan = plan,
                    onToggleTask = onToggleTask,
                    onDeletePlan = onDeletePlan,
                    onDeleteTask = onDeleteTask
                )
            }
        }

        if (plans.isEmpty()) {
            Text(
                text = "No active plans. Tell the AI to create a step-by-step plan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }
}
