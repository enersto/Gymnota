package com.example.myfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.myfit.model.ExerciseTemplate
import com.example.myfit.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseManagerScreen(navController: NavController, viewModel: MainViewModel) {
    val templates by viewModel.allTemplates.collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<ExerciseTemplate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("动作库管理") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTemplate = null // 新建模式
                    showDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(templates) { template ->
                TemplateItem(template,
                    onEdit = {
                        editingTemplate = template
                        showDialog = true
                    },
                    onDelete = { viewModel.deleteTemplate(template.id) }
                )
            }
        }
    }

    if (showDialog) {
        EditTemplateDialog(
            template = editingTemplate,
            onDismiss = { showDialog = false },
            onSave = {
                viewModel.saveTemplate(it)
                showDialog = false
            }
        )
    }
}

@Composable
fun TemplateItem(template: ExerciseTemplate, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "${if (template.category == "STRENGTH") "力量" else "有氧"} | 默认: ${template.defaultTarget}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.Gray) }
            }
        }
    }
}

@Composable
fun EditTemplateDialog(
    template: ExerciseTemplate?,
    onDismiss: () -> Unit,
    onSave: (ExerciseTemplate) -> Unit
) {
    var name by remember { mutableStateOf(template?.name ?: "") }
    var target by remember { mutableStateOf(template?.defaultTarget ?: "") }
    var category by remember { mutableStateOf(template?.category ?: "STRENGTH") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (template == null) "新建动作" else "编辑动作") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("动作名称") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))

                // 类别选择
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("类别:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = category == "STRENGTH",
                        onClick = { category = "STRENGTH" },
                        label = { Text("力量") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = category == "CARDIO",
                        onClick = { category = "CARDIO" },
                        label = { Text("有氧") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text(if (category == "STRENGTH") "默认目标 (如: 3组x12次)" else "默认目标 (如: 30分钟)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotEmpty()) {
                        onSave(ExerciseTemplate(
                            id = template?.id ?: 0,
                            name = name,
                            defaultTarget = target,
                            category = category
                        ))
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        textContentColor = MaterialTheme.colorScheme.onBackground
    )
}

