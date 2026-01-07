package com.example.myfit.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfit.R
import com.example.myfit.data.AppDatabase
import com.example.myfit.model.*
import com.example.myfit.ui.ChartDataPoint
import com.example.myfit.ui.ChartGranularity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).workoutDao()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate = _selectedDate.asStateFlow()

    // 主题与语言设置
    val currentTheme = dao.getAppSettings()
        .map { it?.themeId ?: 0 }
        .map { AppTheme.fromId(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, AppTheme.DARK)

    val currentLanguage = dao.getAppSettings()
        .map { it?.languageCode ?: "zh" }
        .stateIn(viewModelScope, SharingStarted.Lazily, "zh")

    // 日程类型逻辑
    val todayScheduleType = combine(_selectedDate, dao.getAllSchedules()) { date, schedules ->
        val dayOfWeek = date.dayOfWeek.value
        schedules.find { it.dayOfWeek == dayOfWeek }?.dayType ?: DayType.REST
    }.stateIn(viewModelScope, SharingStarted.Lazily, DayType.REST)

    // 核心数据流
    val todayTasks = _selectedDate.flatMapLatest { dao.getTasksForDate(it.toString()) }
    val historyRecords = dao.getHistoryRecords()
    val weightHistory = dao.getAllWeights()
    val allTemplates = dao.getAllTemplates()

    val showWeightAlert = dao.getLatestWeight().map { record ->
        if (record == null) true else ChronoUnit.DAYS.between(LocalDate.parse(record.date), LocalDate.now()) > 7
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    // ================== 设置与基础操作 ==================

    fun switchLanguage(code: String) {
        viewModelScope.launch {
            val currentSetting = dao.getAppSettings().firstOrNull()
            val themeId = currentSetting?.themeId ?: 0
            dao.saveAppSettings(AppSetting(id = 0, themeId = themeId, languageCode = code))
        }
    }

    fun switchTheme(theme: AppTheme) = viewModelScope.launch {
        val currentSetting = dao.getAppSettings().firstOrNull()
        val lang = currentSetting?.languageCode ?: "zh"
        dao.saveAppSettings(AppSetting(id = 0, themeId = theme.id, languageCode = lang))
    }

    // ================== 周计划与任务操作 ==================

    fun addRoutineItem(dayOfWeek: Int, template: ExerciseTemplate) {
        viewModelScope.launch {
            dao.insertRoutineItem(WeeklyRoutineItem(
                dayOfWeek = dayOfWeek,
                templateId = template.id,
                name = template.name,
                target = template.defaultTarget,
                category = template.category,
                bodyPart = template.bodyPart,
                equipment = template.equipment
            ))
        }
    }

    fun removeRoutineItem(item: WeeklyRoutineItem) = viewModelScope.launch { dao.deleteRoutineItem(item) }

    fun applyWeeklyRoutineToToday() {
        viewModelScope.launch {
            val date = _selectedDate.value
            val routineItems = dao.getRoutineForDay(date.dayOfWeek.value)
            if (routineItems.isEmpty()) {
                Toast.makeText(getApplication(), "No Routine Found", Toast.LENGTH_SHORT).show()
                return@launch
            }
            routineItems.forEach { item ->
                // 尝试获取最新模板信息，若无则使用 Routine 中的快照
                val template = dao.getTemplateById(item.templateId)
                dao.insertTask(WorkoutTask(
                    date = date.toString(),
                    templateId = item.templateId,
                    name = item.name,
                    category = item.category,
                    bodyPart = template?.bodyPart ?: item.bodyPart, // 优先用模板最新，其次用Routine快照
                    equipment = template?.equipment ?: item.equipment,
                    sets = listOf(WorkoutSet(1, "", "")),
                    target = item.target
                ))
            }
            Toast.makeText(getApplication(), "Routine Applied", Toast.LENGTH_SHORT).show()
        }
    }

    // ================== 导入导出与备份 ==================

    // V5.2 升级：支持 6 列 CSV 导入
    fun importWeeklyRoutine(csvContent: String) {
        viewModelScope.launch {
            try {
                dao.clearWeeklyRoutine()
                val lines = csvContent.lines()
                var count = 0
                lines.forEach { line ->
                    // 兼容中文逗号，并去除空白
                    val parts = line.replace("，", ",").split(",").map { it.trim() }

                    // 检查是否为有效数据行 (第一列转数字成功)
                    if (parts.size >= 4 && parts[0].toIntOrNull() != null) {
                        val day = parts[0].toInt()
                        val name = parts[1]
                        val catStr = parts[2].uppercase()
                        val target = parts[3]

                        // 兼容旧版 CSV (如果没有第5、6列，使用默认值)
                        val bodyPart = if (parts.size > 4) parts[4] else ""
                        val equipment = if (parts.size > 5) parts[5] else ""

                        val category = when {
                            catStr.contains("有氧") || catStr.contains("CARDIO") -> "CARDIO"
                            catStr.contains("核心") || catStr.contains("CORE") -> "CORE"
                            else -> "STRENGTH"
                        }

                        dao.insertRoutineItem(WeeklyRoutineItem(
                            dayOfWeek = day,
                            templateId = 0,
                            name = name,
                            category = category,
                            target = target,
                            bodyPart = bodyPart,
                            equipment = equipment
                        ))
                        count++
                    }
                }
                Toast.makeText(getApplication(), "Imported $count items", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportHistoryToCsv(context: Context) {
        viewModelScope.launch {
            val records = dao.getHistoryRecordsSync()
            // 升级：导出包含 BodyPart 和 Equipment
            val sb = StringBuilder().append("Date,Name,Category,BodyPart,Equipment,Sets\n")
            records.forEach {
                val setsStr = it.sets.joinToString(" | ") { s -> "${s.weightOrDuration}x${s.reps}" }
                sb.append("${it.date},${it.name},${it.category},${it.bodyPart},${it.equipment},\"$setsStr\"\n")
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, sb.toString())
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "history.csv")
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_csv_btn)))
        }
    }

    // 数据库备份 (.db 文件)
    fun backupDatabase(uri: android.net.Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbName = "myfit_v7.db"
                val dbPath = context.getDatabasePath(dbName)

                context.contentResolver.openOutputStream(uri)?.use { output ->
                    dbPath.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.msg_backup_success), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.msg_backup_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 数据库恢复 (.db 文件)
    fun restoreDatabase(uri: android.net.Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbName = "myfit_v7.db"
                val dbPath = context.getDatabasePath(dbName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    dbPath.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.msg_restore_success), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.msg_restore_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ================== 动作与记录 CRUD ==================

    fun saveTemplate(t: ExerciseTemplate) = viewModelScope.launch {
        if (t.id == 0L) dao.insertTemplate(t) else dao.updateTemplate(t)
    }

    fun deleteTemplate(id: Long) = viewModelScope.launch { dao.softDeleteTemplate(id) }

    fun addTaskFromTemplate(t: ExerciseTemplate) = viewModelScope.launch {
        dao.insertTask(WorkoutTask(
            date = _selectedDate.value.toString(),
            templateId = t.id,
            name = t.name,
            category = t.category,
            bodyPart = t.bodyPart,
            equipment = t.equipment,
            target = t.defaultTarget,
            sets = listOf(WorkoutSet(1, "", ""))
        ))
    }

    fun updateTask(t: WorkoutTask) = viewModelScope.launch { dao.updateTask(t) }
    fun removeTask(t: WorkoutTask) = viewModelScope.launch { dao.deleteTask(t) }

    fun updateScheduleConfig(day: Int, type: DayType) = viewModelScope.launch {
        dao.insertSchedule(ScheduleConfig(day, type))
    }

    fun logWeight(w: Float) = viewModelScope.launch {
        dao.insertWeight(WeightRecord(date = LocalDate.now().toString(), weight = w))
    }

    suspend fun getRoutineForDay(day: Int) = dao.getRoutineForDay(day)

    // ================== 图表统计数据逻辑 (V5.3) ==================

    // 解析数字 (提取字符串中的浮点数)
    private fun parseValue(input: String): Float {
        val regex = Regex("[0-9]+(\\.[0-9]+)?")
        return regex.find(input)?.value?.toFloatOrNull() ?: 0f
    }

    // 解析时长 (支持 "30min", "1h", "90")，统一返回分钟
    private fun parseDuration(input: String): Float {
        val lower = input.lowercase()
        val num = parseValue(lower)
        return when {
            lower.contains("h") -> num * 60
            lower.contains("s") && !lower.contains("m") -> num / 60
            else -> num // 默认分钟
        }
    }

    // 通用聚合函数：将原始数据转换为图表点
    private fun <T> aggregateData(
        data: List<T>,
        dateSelector: (T) -> LocalDate,
        valueSelector: (T) -> Float,
        granularity: ChartGranularity
    ): List<ChartDataPoint> {
        val grouped = when (granularity) {
            ChartGranularity.DAILY -> data.groupBy { dateSelector(it) }
            ChartGranularity.MONTHLY -> data.groupBy { dateSelector(it).withDayOfMonth(1) }
        }

        return grouped.map { (date, items) ->
            // 这里我们采用平均值作为聚合策略 (例如月度平均体重，月度平均单次训练时长)
            // 如果需要总量(如月总容量)，需要在传入 data 之前处理，或者修改此处逻辑。
            // 简单起见，统一用 Average 表现趋势。
            val value = items.map { valueSelector(it) }.average().toFloat()

            val label = if (granularity == ChartGranularity.DAILY)
                date.format(DateTimeFormatter.ofPattern("MM/dd"))
            else
                date.format(DateTimeFormatter.ofPattern("yy/MM"))

            ChartDataPoint(date, value, label)
        }.sortedBy { it.date }
    }

    // 模块 1: 体重数据
    fun getWeightChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> {
        return weightHistory.map { records ->
            val raw = records.map { Pair(LocalDate.parse(it.date), it.weight) }
            // 每日可能有多次记录，先按日取最新
            val dailyMap = raw.groupBy { it.first }.mapValues { it.value.last().second }
            val dailyList = dailyMap.map { ChartDataPoint(it.key, it.value, "") }

            aggregateData(dailyList, { it.date }, { it.value }, granularity)
        }
    }

    // 模块 2: 有氧总时长
    fun getCardioTotalChartData(granularity: ChartGranularity): Flow<List<ChartDataPoint>> {
        return dao.getHistoryRecords().map { tasks ->
            val cardioTasks = tasks.filter { it.category == "CARDIO" }
            val dailySums = cardioTasks.groupBy { LocalDate.parse(it.date) }
                .mapValues { (_, dayTasks) ->
                    dayTasks.sumOf { task ->
                        if (task.sets.isNotEmpty()) {
                            task.sets.sumOf { parseDuration(it.weightOrDuration).toDouble() }
                        } else {
                            parseDuration(task.target).toDouble()
                        }
                    }.toFloat()
                }

            val dailyData = dailySums.map { ChartDataPoint(it.key, it.value, "") }
            aggregateData(dailyData, { it.date }, { it.value }, granularity)
        }
    }

    // 获取某类别的所有动作名称
    fun getExerciseNamesByCategory(category: String): Flow<List<String>> {
        return dao.getHistoryRecords().map { tasks ->
            tasks.filter { it.category == category }
                .map { it.name }
                .distinct()
                .sorted()
        }
    }

    // 模块 2b, 3, 4: 单动作趋势 (mode: 0=时长, 1=重量, 2=次数)
    fun getSingleExerciseChartData(name: String, mode: Int, granularity: ChartGranularity): Flow<List<ChartDataPoint>> {
        return dao.getHistoryRecords().map { tasks ->
            val targetTasks = tasks.filter { it.name == name }

            val dailyValues = targetTasks.groupBy { LocalDate.parse(it.date) }
                .mapValues { (_, dayTasks) ->
                    val values = dayTasks.flatMap { task ->
                        if (task.sets.isNotEmpty()) task.sets else listOf(WorkoutSet(1, task.actualWeight.ifEmpty { task.target }, task.target))
                    }

                    when (mode) {
                        0 -> values.sumOf { parseDuration(it.weightOrDuration).toDouble() }.toFloat() // 有氧：总时长
                        1 -> values.maxOfOrNull { parseValue(it.weightOrDuration) } ?: 0f // 力量：最大重量
                        2 -> values.sumOf { parseValue(it.reps).toDouble() }.toFloat() // 核心：总次数
                        else -> 0f
                    }
                }

            val dailyData = dailyValues.map { ChartDataPoint(it.key, it.value, "") }
            aggregateData(dailyData, { it.date }, { it.value }, granularity)
        }
    }
}