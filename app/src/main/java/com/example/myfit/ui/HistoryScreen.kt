package com.example.myfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource // 关键引用
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfit.R // 关键引用
import com.example.myfit.model.*
import com.example.myfit.viewmodel.MainViewModel

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val tasks by viewModel.historyRecords.collectAsState(initial = emptyList())
    val weights by viewModel.weightHistory.collectAsState(initial = emptyList())

    val historyData = remember(tasks, weights) {
        val allDates = (tasks.map { it.date } + weights.map { it.date }).distinct().sortedDescending()
        allDates.map { date ->
            Triple(date, weights.find { it.date == date }, tasks.filter { it.date == date })
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)
    ) {
        // 标题也使用资源
        Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))

        if (historyData.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // 暂无记录使用资源
                Text(stringResource(R.string.no_history), color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                historyData.forEach { (date, weightRecord, dayTasks) ->
                    item {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = date, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                                if (weightRecord != null) {
                                    Surface(color = Color(0xFFFF9800).copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.MonitorWeight, null, modifier = Modifier.size(14.dp), tint = Color(0xFFFF9800))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "${weightRecord.weight} KG", color = Color(0xFFFF9800), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (dayTasks.isEmpty()) {
                        item {
                            // 当日无训练使用资源
                            Text(stringResource(R.string.history_no_train), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    } else {
                        items(dayTasks) { task ->
                            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text(task.name, style = MaterialTheme.typography.titleMedium)
                                        Text(if (task.actualWeight.isNotEmpty()) "${task.target} @ ${task.actualWeight}" else task.target, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                    // V4.4 修复：使用资源文件
                                    Text(stringResource(R.string.btn_done), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}