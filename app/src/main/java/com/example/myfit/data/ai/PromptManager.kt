package com.example.myfit.data.ai

import android.content.Context
import com.example.myfit.R
import com.example.myfit.data.api.ChatMessage
import com.example.myfit.model.AppSetting
import com.example.myfit.model.ContentPart // ✅ 确保引用了这个
import com.example.myfit.model.DayType
import com.example.myfit.model.ImageUrl   // ✅ 确保引用了这个
import com.example.myfit.model.ScheduleConfig
import com.example.myfit.model.WorkoutTask

object PromptManager {


    fun buildWeeklyPlanPrompt(
        context: Context,
        profile: AppSetting,
        schedules: List<ScheduleConfig>,
        history: List<WorkoutTask>,
        goal: String,
        weeks: Int,
        focusAreas: Set<String>,
        equipmentScene: Set<String>,
        injuredAreas: Set<String>
    ): List<ChatMessage> {
        val resources = context.resources

        // 1. 构建 System Prompt
        val systemContent = resources.getString(R.string.prompt_system_role)

        // 2. 构建 User Content
        val sb = StringBuilder()

        // (A) 用户档案
        val genderStr = if (profile.gender == 0) resources.getString(R.string.gender_male) else resources.getString(R.string.gender_female)
        sb.append(resources.getString(R.string.prompt_user_profile, genderStr, profile.age, profile.height)).append("\n\n")

        // (B) 训练目标
        // [新增] (B-1) 结构化训练约束
        sb.append("【训练偏好与限制】\n")

        // 1. 训练重心
        val focusText = if (focusAreas.contains("COMPREHENSIVE") || focusAreas.isEmpty()) {
            resources.getString(R.string.focus_comprehensive)
        } else {
            // 将 STRENGTH, CARDIO 等转换为自然语言
            val labels = focusAreas.joinToString("、") {
                when(it) {
                    "STRENGTH" -> resources.getString(R.string.category_strength)
                    "CARDIO" -> resources.getString(R.string.category_cardio)
                    "CORE" -> resources.getString(R.string.category_core)
                    else -> it
                }
            }
            "侧重于：$labels"
        }
        sb.append("- 训练重心: $focusText\n")

        // 2. 器械场景
        val sceneText = equipmentScene.map { scene ->
            when(scene) {
                "GYM" -> resources.getString(R.string.scene_gym)
                "HOME_EQUIP" -> resources.getString(R.string.scene_home_equip)
                "HOME_NONE" -> resources.getString(R.string.scene_home_none)
                "OUTDOOR" -> resources.getString(R.string.scene_outdoor)
                else -> resources.getString(R.string.scene_gym)
            }
        }.joinToString(" + ") // 用 " + " 连接多个场景

        sb.append("- Scene: $sceneText\n")

        // 3. 伤病禁忌
        if (injuredAreas.isNotEmpty()) {
            val injuryText = injuredAreas.joinToString("、")
            sb.append("- ⚠️ 严重禁忌 (受伤部位): $injuryText。请务必避开对该部位有高冲击或高负荷的动作！\n")
        }
        sb.append("\n")

        // (B-2) 用户备注目标 (原 User Goal)
        val finalGoal = if (goal.isBlank()) "综合体能提升" else goal
        sb.append(resources.getString(R.string.prompt_user_goal, finalGoal)).append("\n\n")

        // (C) [新增] 作息设定 (Hard Constraints)
        sb.append(resources.getString(R.string.prompt_schedule_title)).append("\n")
        // 确保按周一到周日排序
        val sortedSchedules = schedules.sortedBy { it.dayOfWeek }
        sortedSchedules.forEach { config ->
            val dayName = getDayName(config.dayOfWeek) // 简易转换，或者用资源
            val typeName = getDayTypeName(config.dayType)
            sb.append("- $dayName: $typeName\n")
        }
        sb.append("\n")

        // (D) 历史记录
        sb.append(resources.getString(R.string.prompt_history_title, weeks)).append("\n")
        if (history.isEmpty()) {
            sb.append("(无近期记录)\n")
        } else {
            // [修复] 不再计算总容量，而是输出具体的 "重量 x 次数"，让 AI 能分析力量水平
            history.forEach { task ->
                val setsDetails = task.sets.joinToString(", ") { set ->
                    // 格式示例: "60kg*12" 或 "30min"
                    if (set.weightOrDuration.contains("min") || set.weightOrDuration.contains("sec")) {
                        set.weightOrDuration // 有氧/计时直接显示时长
                    } else {
                        "${set.weightOrDuration}kg*${set.reps}" // 力量显示 重量*次数
                    }
                }
                // 输出格式: "- 2023-10-27: 卧推 (60kg*12, 65kg*10, 65kg*8)"
                sb.append("- ${task.date}: ${task.name} ($setsDetails)\n")
            }
        }

        val langInstruction = getLanguageInstruction(context)

        return listOf(
            ChatMessage("system", systemContent+ langInstruction),
            ChatMessage("user", sb.toString())
        )
    }

    // [新增] 构建图片分析 Prompt
    fun buildImageAnalysisPrompt(context: Context, base64Image: String): List<ChatMessage> {

        val systemPrompt = context.getString(R.string.prompt_system_vision)
        val userQuery = context.getString(R.string.prompt_user_vision_query)
        // 构建多模态消息体
        val contentParts = listOf(
            ContentPart(type = "text", text = userQuery),
            ContentPart(
                type = "image_url",
                image_url = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
            )
        )

        return listOf(
            ChatMessage("system", systemPrompt),
            // 关键：这里直接传 List<ContentPart>，因为 ChatMessage.content 已经是 Any 类型
            ChatMessage("user", contentParts as Any)
        )
    }

    private fun getDayName(day: Int): String {
        return when(day) {
            1 -> "Mon"; 2 -> "Tue"; 3 -> "Wed"; 4 -> "Thu"; 5 -> "Fri"; 6 -> "Sat"; 7 -> "Sun"
            else -> "Day $day"
        }
    }

    private fun getDayTypeName(type: DayType): String {
        return type.name // 直接返回枚举名，AI通常能理解 (CORE, REST, STRENGTH...)
    }

    // [新增] 自由对话 Prompt (轻量级，无历史记录)
    fun buildFreeChatPrompt(context: Context, userQuery: String): List<ChatMessage> {
        val systemPrompt = context.getString(R.string.prompt_system_free_chat)
        return listOf(
            ChatMessage("system", systemPrompt),
            ChatMessage("user", userQuery)
        )
    }

    // [新增] 根据当前语言环境，追加强制语言指令
    private fun getLanguageInstruction(context: Context): String {
        val locale = context.resources.configuration.locales[0]
        return when (locale.language) {
            "en" -> "\n\n(Please answer in English.)"
            "ja" -> "\n\n(日本語で答えてください。)"
            "de" -> "\n\n(Bitte antworten Sie auf Deutsch.)"
            "es" -> "\n\n(Por favor, responda en español.)"
            else -> "\n\n(请务必使用中文回答。)"
        }
    }

    // [新增] 构建 CSV 生成请求
    // 注意：这里需要把之前的建议内容作为 context 传回去，或者依赖多轮对话 (如果是多轮对话架构)
    // 鉴于目前是单次请求架构，我们需要把之前的建议拼接在 User Prompt 里
    // [修改] 增加 userFeedback 参数
    fun buildCsvPlanPrompt(context: Context, adviceContent: String, userFeedback: String = ""): List<ChatMessage> {
        val systemPrompt = context.getString(R.string.prompt_system_csv_generator)

        // 1. 定义 App 支持的合法 Key (白名单)
        val validBodyParts = listOf(
            "part_chest", "part_back", "part_shoulders",
            "part_arms", "part_abs", "part_cardio",
            "part_hips", "part_thighs", "part_calves", "part_other"
        )
        val validEquipment = listOf(
            "equip_barbell", "equip_dumbbell", "equip_machine", "equip_cable",
            "equip_bodyweight", "equip_cardio_machine", "equip_kettlebell",
            "equip_smith_machine", "equip_resistance_band", "equip_medicine_ball",
            "equip_trx", "equip_bench", "equip_other"
        )

        // 2. 构建约束指令
        // 显式告诉 AI：如果遇到 "Triceps"，必须映射到 "part_arms"；遇到 "Core"，必须映射到 "part_abs"
        val constraintPrompt = """
            
            IMPORTANT - STRICT FORMAT RULES:
            1. For 'BodyPart' column, you MUST ONLY use one of these exact keys:
            [${validBodyParts.joinToString(", ")}]
            (Rule: Map 'triceps'/'biceps'/'forearms' -> 'part_arms'. Map 'core' -> 'part_abs'. Map 'glutes' -> 'part_hips'. Map 'quads'/'hamstrings' -> 'part_thighs'.)
            
            2. For 'Equipment' column, you MUST ONLY use one of these exact keys:
            [${validEquipment.joinToString(", ")}]
            
        """.trimIndent()

        // 3. 拼接用户 Prompt
        var userPromptContent = context.getString(R.string.prompt_user_generate_csv) +
                "\n$constraintPrompt" + // 插入约束
                "\n\nBased on advice:\n" + adviceContent

        // Append user feedback if present
        if (userFeedback.isNotBlank()) {
            userPromptContent += "\n\nUser Feedback/Correction: $userFeedback"
        }

        return listOf(
            ChatMessage("system", systemPrompt),
            ChatMessage("user", userPromptContent)
        )
    }
}