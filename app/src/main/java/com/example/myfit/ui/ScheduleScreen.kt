package com.example.myfit.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myfit.model.AppTheme
import com.example.myfit.model.DayType
import com.example.myfit.model.ScheduleConfig
import com.example.myfit.viewmodel.MainViewModel

@Composable
fun ScheduleScreen(navController: NavController, viewModel: MainViewModel) {
    val currentTheme by viewModel.currentTheme.collectAsState()

    val context = LocalContext.current
    val dao = remember { com.example.myfit.data.AppDatabase.getDatabase(context).workoutDao() }
    val scheduleList by dao.getAllSchedules().collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // --- 顶部：设置区 ---
        Text("个性化设置", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))

        // 1. 动作库入口
        Button(
            onClick = { navController.navigate("exercise_manager") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("管理动作库 (新建/编辑)", color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.weight(1f))
            Text(">", color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. 主题切换
        Text("主题风格", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppTheme.values().forEach { theme ->
                ThemeCircle(theme, currentTheme == theme) { viewModel.switchTheme(theme) }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)

        // --- 底部：日程区 ---
        Text("周计划安排", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text("点击切换类型", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(scheduleList) { config ->
                ScheduleItem(config) { newType ->
                    viewModel.updateScheduleConfig(config.dayOfWeek, newType)
                }
            }

            // --- 新增：关于模块 (这里是调用) ---
            item {
                Spacer(modifier = Modifier.height(24.dp))
                AboutSection()
            }
        }
    }
}

// --- 组件定义区 ---

@Composable
fun ThemeCircle(theme: AppTheme, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(theme.primary))
            .border(3.dp, if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent, CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
fun ScheduleItem(config: ScheduleConfig, onTypeChange: (DayType) -> Unit) {
    val dayName = when(config.dayOfWeek) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"; 5 -> "周五"; 6 -> "周六"; 7 -> "周日"
        else -> ""
    }
    fun nextType() = DayType.values()[(config.dayType.ordinal + 1) % DayType.values().size]

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onTypeChange(nextType()) },
        colors = CardDefaults.cardColors(containerColor = Color(config.dayType.colorHex).copy(alpha = 0.9f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(dayName, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(config.dayType.label, color = Color.White)
        }
    }
}

// --- 关于模块定义 (这里只定义一次) ---
@Composable
fun AboutSection() {
    val context = LocalContext.current
    val versionName = try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName
    } catch (e: Exception) {
        "1.0"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "About",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "myFit $versionName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Designed & Built by enersto",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            fontSize = 10.sp
        )
    }
}