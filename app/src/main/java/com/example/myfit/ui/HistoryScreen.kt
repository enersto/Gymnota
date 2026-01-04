package com.example.myfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // 注意：虽然引入了Color，但主要用主题色
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myfit.model.WorkoutTask
import com.example.myfit.viewmodel.MainViewModel

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val history by viewModel.historyRecords.collectAsState(initial = emptyList())
    // 按日期分组
    val grouped = history.groupBy { it.date }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // 关键修复：跟随主题背景
            .padding(16.dp)
    ) {
        Text(
            text = "历史记录",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground // 关键修复：跟随主题文字色
        )

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp) // 防止底部被导航栏遮挡
        ) {
            grouped.forEach { (date, tasks) ->
                item {
                    Text(
                        text = date,
                        color = MaterialTheme.colorScheme.primary, // 日期使用主题主色
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                }

                items(tasks) { task ->
                    HistoryItemCard(task)
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(task: WorkoutTask) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface // 关键修复：卡片背景色
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface // 卡片上的文字色
                )
                // 显示具体的完成情况 (如果有重量显示重量，没有则显示单纯的Target)
                val detailText = if (task.actualWeight.isNotEmpty()) {
                    "${task.target} @ ${task.actualWeight}${if(task.type == "CARDIO") "" else "kg"}"
                } else {
                    task.target
                }

                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // 完成标记
            Text(
                text = "完成",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}