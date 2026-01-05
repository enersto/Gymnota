package com.example.myfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource // 关键引用
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myfit.R // 关键引用
import com.example.myfit.model.ExerciseTemplate
import com.example.myfit.viewmodel.MainViewModel

@Composable
fun ExerciseManagerScreen(navController: NavController, viewModel: MainViewModel) {
    val templates by viewModel.allTemplates.collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<ExerciseTemplate?>(null) }

    Scaffold(
        topBar = {
            // 顶部导航栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.title_manage_exercises), // 国际化标题
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
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
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(templates) { template ->
                ExerciseItemCard(
                    template = template,
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
        ExerciseEditDialog(
            template = editingTemplate,
            onDismiss = { showDialog = false },
            onSave = { temp ->
                viewModel.saveTemplate(temp)
                showDialog = false
            }
        )
    }
}

@Composable
fun ExerciseItemCard(
    template: ExerciseTemplate,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // 将数据库的英文类别转换为多语言显示
    val categoryLabel = when (template.category) {
        "STRENGTH" -> stringResource(R.string.category_strength)
        "CARDIO" -> stringResource(R.string.category_cardio)
        else -> template.category
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    // 类别标签
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = categoryLabel,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = template.defaultTarget,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun ExerciseEditDialog(
    template: ExerciseTemplate?,
    onDismiss: () -> Unit,
    onSave: (ExerciseTemplate) -> Unit
) {
    var name by remember { mutableStateOf(template?.name ?: "") }
    var target by remember { mutableStateOf(template?.defaultTarget ?: "") }
    var category by remember { mutableStateOf(template?.category ?: "STRENGTH") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (template == null) stringResource(R.string.title_new_exercise)
                else stringResource(R.string.title_edit_exercise)
            )
        },
        text = {
            Column {
                // 名称输入
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 目标输入
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text(stringResource(R.string.label_target)) },
                    placeholder = { Text(stringResource(R.string.hint_target)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 类别选择
                Text(stringResource(R.string.label_category), style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = category == "STRENGTH", onClick = { category = "STRENGTH" })
                    Text(stringResource(R.string.category_strength), modifier = Modifier.clickable { category = "STRENGTH" })

                    Spacer(modifier = Modifier.width(16.dp))

                    RadioButton(selected = category == "CARDIO", onClick = { category = "CARDIO" })
                    Text(stringResource(R.string.category_cardio), modifier = Modifier.clickable { category = "CARDIO" })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(ExerciseTemplate(
                            id = template?.id ?: 0,
                            name = name,
                            defaultTarget = target,
                            category = category
                        ))
                    }
                }
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}