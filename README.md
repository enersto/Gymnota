# 健身记 Gymnota - 个人健身记录备忘录 (V5.8)

**健身记** 是一款基于 Android Jetpack Compose 开发的个人健身追踪和备忘记录应用。它旨在帮助用户规划周度训练方案、记录每日打卡，并通过**原生图表**深度可视化追踪训练容量与身体状态变化。

🚀 **V6.0 重磅更新：AI 智能私教进化 (The AI Coach Evolution)** —— 从“给建议”到“排计划”的完美闭环。
---

[English Version](https://github.com/enersto/Gymnota/blob/main/READEME-en.md)

## ✨ V6.0 新特性 (New in V6.0)

**🧠 AI 智能私教 (AI Coach Integration)**
* **上下文感知计划生成**：AI 不再提供通用建议，而是读取您的用户档案、最近 3 周的训练容量及周作息配置，量身定制下周计划。
* **多维约束控制**：支持自定义**训练重心**（力量/有氧/核心）、**器械场景**（健身房/居家/户外）及**伤病避开部位**，确保计划安全可行。
* **闭环执行 (Generate & Import)**：独创的「Refine & Import」流程。AI 生成的建议可一键转化为结构化数据，自动写入日程表，省去手动录入。
* **AI 视觉助手 (Snap & Ask)**：遇到陌生器械？拍张照，AI 立刻识别并讲解目标肌群及操作要领。

**⚙️ 模型自由与连接增强**
* **多服务商支持**：预设支持 **OpenAI, DeepSeek, Kimi (Moonshot), 通义千问 (Qwen), Gemini, SiliconFlow** 等主流模型。
* **智能配置**：自动记忆不同服务商的 API Key 和 Base URL，支持无缝切换；内置 URL 清洗逻辑，提升连接成功率。

**🎨 Markdown 渲染引擎 2.0**
* **完美排版**：全面升级文本显示，支持代码块高亮、多级标题、引用及列表格式（如周计划详情）。
* **交互增强**：新增长按复制与文本选择功能，方便提取关键信息。

**🔒 隐私优先 (Privacy First)**
* **本地存储**：API Key 仅存通过 SharedPreferences 储在本地。
* **数据脱敏**：发送给 AI 的历史数据仅包含精简摘要（如总容量、最大重量），最大限度保护隐私。

---

## 🔥 核心功能 (Core Features)

**📊 训练容量热力图 (Heatmap)**
* **可视化分布**：在历史页通过色块深浅直观展示每日训练强度。
* **统计标准**：采用「历史累积容量负荷 (重量 × 次数)」计算。

**⏱️ 专业级训练计时器**
* **锁屏常驻**：通知栏实时显示倒计时，支持锁屏查看，后台运行不被杀。
* **权限引导**：智能引导开启必要权限，确保计时稳定。

**💪 身体状态与指标管理**
* **自动计算**：记录体重后，系统自动结合身高/年龄/性别计算 **BMI** 和 **BMR**。
* **隐私保护**：所有身体档案数据仅存储在本地数据库。

**📈 可视化统计中心**
* **原生图表**：基于 Compose Canvas 绘制的高性能折线图与柱状图。
* **多维分析**：追踪体重变化、有氧总时长、单项动作最大重量 (1RM) 走势。

**⚖️ 智能数据逻辑**
* **单边动作合并**：自动识别并合并左右分侧动作（如单臂划船）的容量数据。
* **智能导入**：CSV 导入时自动创建新动作模板、补充部位信息并去重。

**🌍 多语言支持**
* 完整支持 **简体中文、English、Deutsch、Español、日本語**。

---

## 📱 使用指南 (User Guide)

### 1. 启用 AI 私教 `NEW`
* 进入 **AI Coach** 标签页。
* 点击顶部状态栏配置模型（推荐使用 DeepSeek 或 OpenAI）。
* **生成计划**：点击「生成周计划」，设置训练重心与器械条件，等待 AI 分析。
* **导入日程**：对 AI 生成的计划满意后，点击底部的导入按钮，直接应用到您的周程表中。

### 2. 视觉助手与自由对话 `NEW`
* 在 AI 页面点击相机图标，拍摄器械或动作，获取指导。
* 使用自由对话模式询问任何健身相关问题。

### 3. 查看训练热力图
* 进入 **历史 (History)** 页面顶部查看。
* 图例展示了从低到高的容量级别。

### 4. 训练计时与锁屏显示
* 在动作组右侧输入时间（分钟），点击播放键开始计时。
* **锁屏查看**：点亮屏幕（无需解锁）即可在通知区域看到倒计时。

---

## 📂 CSV 导入格式说明

支持通过导入 CSV 自动扩充动作库。推荐使用以下完整格式：

`星期(1-7), 动作名称, 类别(STRENGTH/CARDIO/CORE), 目标, 部位Key, 器械Key`

**Key 参考**：

**Key 参考**：

* **部位**:
    * 上肢/躯干: `part_chest`, `part_back`, `part_shoulders`, `part_arms`, `part_abs`
    * 下肢: `part_hips` (臀部), `part_thighs` (大腿), `part_calves` (小腿)
    * 其他: `part_cardio`, `part_other`
* **器械**: `equip_barbell`, `equip_dumbbell`, `equip_machine`, `equip_cable`, `equip_smith_machine`, `equip_bodyweight`, `equip_cardio_machine`, `equip_other`
* 格式：\nDay,Name,Category,Target,BodyPart,Equipment,IsUni,LogType\n\n注意：\nDay: 1-7 (周一至周日)\nIsUni: true/false (单边)\nLogType: 0=重量 x 次数, 1=计时, 2=次数

**示例：**

```text
Day,Name,Category,Target,BodyPart,Equipment,IsUnilateral,LogType
 
1,坐姿推胸,STRENGTH,3x12,part_chest,equip_machine,false,0 
1,平板支撑,CORE,3x60s,part_abs,equip_bodyweight,false,1 
2,深蹲,STRENGTH,4x10,part_thighs,equip_barbell,false,0 
3,有氧跑,CARDIO,30min,part_cardio,equip_cardio_machine,false,1 
4,卷腹,CORE,4x20,part_abs,equip_bodyweight,false,2

```

---

## 🛠️ 技术栈 (Tech Stack)

* **Language**: Kotlin
* **UI Framework**: Jetpack Compose (Material3)
* **Architecture**: MVVM (Model-View-ViewModel)
* **Database**: Room (SQLite) with Migrations (v9)
* **Network**: Retrofit + OkHttp (AI API Integration) `NEW`
* **Service**: Android Foreground Service (Keep-alive Timer)
* **Graphics**: Compose Canvas (Custom Charts)

---

## 📝 版本信息

* **Version**: 6.0
* **Author**: Designed & Built by enersto & Hajimi

---
