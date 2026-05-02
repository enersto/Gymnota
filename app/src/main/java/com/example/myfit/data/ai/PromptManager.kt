package com.example.myfit.data.ai

import android.content.Context
import com.example.myfit.R
import com.example.myfit.data.api.ChatMessage
import com.example.myfit.model.*
import java.util.Locale

object PromptManager {

    /**
     * 构建周计划生成 Prompt
     * 集成了用户基础档案、最新的身体成分指标、长期记忆上下文以及训练偏好
     */
    fun buildWeeklyPlanPrompt(
        context: Context,
        profile: AppSetting,
        latestWeight: WeightRecord?,    // [新增] 最新体重及成分数据
        memory: String?,                // [新增] 长期记忆上下文
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

        // (A) 用户档案 + 身体指标 (解决问题 1)
        val genderStr =
            if (profile.gender == 0) resources.getString(R.string.gender_male) else resources.getString(
                R.string.gender_female
            )
        sb.append("【").append(resources.getString(R.string.settings_profile)).append("】\n")
        // 使用 profile.height.toString() 确保类型匹配或使用 formatted string
        sb.append(
            resources.getString(
                R.string.prompt_user_profile,
                genderStr,
                profile.age,
                profile.height
            )
        ).append("\n")

        latestWeight?.let {
            sb.append("- ").append(resources.getString(R.string.label_weight_kg)).append(": ")
                .append(it.weight).append("kg\n")
            it.bodyFatKg?.let { bf ->
                val rate = it.bodyFatRate ?: 0f
                sb.append("- ").append(resources.getString(R.string.label_body_fat_kg)).append(": ")
                    .append(bf).append("kg (")
                    .append(String.format(Locale.getDefault(), "%.1f", rate)).append("%)\n")
            }
            it.skeletalMuscleKg?.let { sm ->
                sb.append("- ").append(resources.getString(R.string.label_skeletal_muscle_kg))
                    .append(": ").append(sm).append("kg\n")
            }
        }
        sb.append("\n")

        // (B) 插入长期记忆上下文 (解决问题 2)
        if (!memory.isNullOrBlank()) {
            sb.append("【历史偏好/长期记忆 (Long-term Context)】\n")
            sb.append(memory).append("\n\n")
        }

        // (C) 训练偏好与限制
        sb.append("【训练偏好与限制】\n")
        val focusText = if (focusAreas.contains("COMPREHENSIVE") || focusAreas.isEmpty()) {
            resources.getString(R.string.focus_comprehensive)
        } else {
            val labels = focusAreas.joinToString("、") {
                when (it) {
                    "STRENGTH" -> resources.getString(R.string.category_strength)
                    "CARDIO" -> resources.getString(R.string.category_cardio)
                    "CORE" -> resources.getString(R.string.category_core)
                    else -> it
                }
            }
            "侧重于：$labels"
        }
        sb.append("- 训练重心: $focusText\n")

        val sceneText = equipmentScene.map { scene ->
            when (scene) {
                "GYM" -> resources.getString(R.string.scene_gym)
                "HOME_EQUIP" -> resources.getString(R.string.scene_home_equip)
                "HOME_NONE" -> resources.getString(R.string.scene_home_none)
                "OUTDOOR" -> resources.getString(R.string.scene_outdoor)
                "POOL" -> resources.getString(R.string.scene_pool)
                "YOGA_STUDIO" -> resources.getString(R.string.scene_yoga)
                "LIMITED_GYM" -> resources.getString(R.string.scene_limited_gym)
                "CROSSFIT_BOX" -> resources.getString(R.string.scene_crossfit)
                else -> resources.getString(R.string.scene_gym)
            }
        }.joinToString(" + ")
        sb.append("- Scene: $sceneText\n")

        if (injuredAreas.isNotEmpty()) {
            val injuryText = injuredAreas.joinToString("、")
            sb.append("- ⚠️ 严重禁忌 (受伤部位): $injuryText\n")
        }
        sb.append("\n")

        // (D) 目标
        val finalGoal = if (goal.isBlank()) resources.getString(R.string.val_default_goal) else goal
        sb.append(resources.getString(R.string.prompt_user_goal, finalGoal)).append("\n\n")

        // (E) 作息设定
        sb.append(resources.getString(R.string.prompt_schedule_title)).append("\n")
        schedules.sortedBy { it.dayOfWeek }.forEach { config ->
            sb.append("- ${getDayName(config.dayOfWeek)}: ${getDayTypeName(config.dayType)}\n")
        }
        sb.append("\n")

        // (F) 历史记录
        sb.append(resources.getString(R.string.prompt_history_title, weeks)).append("\n")
        if (history.isEmpty()) {
            sb.append("(无近期记录)\n")
        } else {
            history.forEach { task ->
                val setsDetails = task.sets.joinToString(", ") { set ->
                    if (set.weightOrDuration.contains("min") || set.weightOrDuration.contains("sec")) {
                        set.weightOrDuration
                    } else {
                        "${set.weightOrDuration}kg*${set.reps}"
                    }
                }
                sb.append("- ${task.date}: ${task.name} ($setsDetails)\n")
            }
        }

        val langInstruction = getLanguageInstruction(context)
        return listOf(
            ChatMessage("system", systemContent + langInstruction),
            ChatMessage("user", sb.toString())
        )
    }

    /**
     * 自由对话 Prompt：集成上下文历史 (解决问题 2)
     */
    fun buildFreeChatPrompt(
        context: Context,
        userQuery: String,
        history: List<AiChatRecord> = emptyList(), // [新增] 历史对话
        memory: String? = null                       // [新增] 长期记忆
    ): List<ChatMessage> {
        val systemPrompt = context.getString(R.string.prompt_system_free_chat)
        val langInstruction = getLanguageInstruction(context)

        val messages = mutableListOf<ChatMessage>()

        // 1. 系统角色 + 长期记忆
        val fullSystemPrompt = if (!memory.isNullOrBlank()) {
            "$systemPrompt\n\n【Known User Context / Long-term Memory】:\n$memory\n$langInstruction"
        } else {
            systemPrompt + langInstruction
        }
        messages.add(ChatMessage("system", fullSystemPrompt))

        // 2. 注入历史对话 (按时间正序排列给 AI)
        history.reversed().forEach { record ->
            messages.add(ChatMessage(record.role, record.content))
        }

        // 3. 当前用户输入
        messages.add(ChatMessage("user", userQuery))

        return messages
    }

    /**
     * [新增] 生成记忆摘要的 Prompt (解决问题 3 - 节省 Token)
     * 用于从对话历史中提取关键信息存入本地 AiMemory
     */
    fun buildMemorySummaryPrompt(context: Context, history: List<AiChatRecord>): List<ChatMessage> {
        val systemPrompt =
            "You are a fitness assistant. Summarize the user's preferences, injuries, and goals from the provided chat history. " +
                    "Keep it concise (under 200 words). Focus on: 1. Training likes/dislikes 2. Physical constraints 3. Specific equipment availability. " +
                    "Output ONLY the summary text in the user's language."

        val historyText = history.reversed().joinToString("\n") { "${it.role}: ${it.content}" }

        return listOf(
            ChatMessage("system", systemPrompt),
            ChatMessage("user", "Please summarize this conversation history:\n\n$historyText")
        )
    }

    /**
     * 构建图片分析 Prompt
     */
    fun buildImageAnalysisPrompt(context: Context, base64Image: String): List<ChatMessage> {
        val systemPrompt = context.getString(R.string.prompt_system_vision)
        val userQuery = context.getString(R.string.prompt_user_vision_query)
        val contentParts = listOf(
            ContentPart(type = "text", text = userQuery),
            ContentPart(
                type = "image_url",
                image_url = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
            )
        )
        return listOf(
            ChatMessage("system", systemPrompt),
            ChatMessage("user", contentParts as Any)
        )
    }

    private fun getDayName(day: Int): String {
        return when (day) {
            1 -> "Mon"; 2 -> "Tue"; 3 -> "Wed"; 4 -> "Thu"; 5 -> "Fri"; 6 -> "Sat"; 7 -> "Sun"
            else -> "Day $day"
        }
    }

    private fun getDayTypeName(type: DayType): String {
        return type.name
    }

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

    /**
     * 自由对话的多模态 Prompt
     */
    fun buildMultimodalChatPrompt(
        context: Context,
        userQuery: String,
        base64Image: String
    ): List<ChatMessage> {
        val systemPrompt = context.getString(R.string.prompt_system_free_chat)
        val langInstruction = getLanguageInstruction(context)
        val contentParts = mutableListOf<ContentPart>()
        if (userQuery.isNotBlank()) {
            contentParts.add(ContentPart(type = "text", text = userQuery))
        }
        contentParts.add(
            ContentPart(
                type = "image_url",
                image_url = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
            )
        )
        return listOf(
            ChatMessage("system", systemPrompt + langInstruction),
            ChatMessage("user", contentParts as Any)
        )
    }

    private fun getUserLanguageName(context: Context): String {
        val locale = context.resources.configuration.locales[0]
        return locale.displayLanguage
    }

    /**
     * 构建 CSV 生成请求
     */
    fun buildCsvPlanPrompt(
        context: Context,
        adviceContent: String,
        userFeedback: String = ""
    ): List<ChatMessage> {
        val systemPrompt = context.getString(R.string.prompt_system_csv_generator)
        val userLang = getUserLanguageName(context)
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

        val constraintPrompt = """
            IMPORTANT - STRICT FORMAT RULES:
            0. For 'Category' column, you MUST ONLY use one of these exact keys: ['STRENGTH', 'CARDIO', 'CORE']
            - The output MUST be strictly in CSV format with exactly 9 columns.
            - Do NOT use any half-width commas (,) inside the cell values. Use full-width commas (，) if needed.
            1. For 'BodyPart' column, you MUST ONLY use one of these exact keys: [${
            validBodyParts.joinToString(
                ", "
            )
        }]
            2. For 'Equipment' column, you MUST ONLY use one of these exact keys: [${
            validEquipment.joinToString(
                ", "
            )
        }]
            3. OUTPUT CSV COLUMNS MUST BE EXACTLY: Day,Name,Category,Target,BodyPart,Equipment,IsUni,LogType,Instruction
            4. STRICT RULES for 'IsUni': true if one side at a time, false otherwise.
            5. Columns 'Name' and 'Instruction' MUST be in $userLang.
        """.trimIndent()

        var userPromptContent =
            context.getString(R.string.prompt_user_generate_csv) + "\n$constraintPrompt" + "\n\nBased on advice:\n" + adviceContent
        if (userFeedback.isNotBlank()) {
            userPromptContent += "\n\nUser Feedback/Correction: $userFeedback"
        }
        return listOf(
            ChatMessage("system", systemPrompt),
            ChatMessage("user", userPromptContent)
        )
    }
}
