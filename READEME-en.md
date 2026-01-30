# Gymnota - Personal Workout Memo & Tracker (V6.0)

**Gymnota** is a personal fitness tracking and memo application built with **Android Jetpack Compose**. It is designed to help users plan weekly training schedules, log daily workouts, and deeply visualize training volume and body status trends through **native charts**.

🚀 **V6.0 Major Update: The AI Coach Evolution** — A perfect closed loop from "Advice" to "Actionable Planning".

---

## ✨ New in V6.0

**🧠 AI Coach Integration**
* **Context-Aware Planning**: The AI no longer provides generic advice. It reads your **User Profile**, **Training Volume from the last 3 weeks**, and **Weekly Schedule Configuration** to tailor a specific plan for the upcoming week.
* **Multi-Dimensional Constraints**: Supports custom **Training Focus** (Strength/Cardio/Core), **Equipment Scenario** (Gym/Home/Outdoor), and **Injury Avoidance**, ensuring the plan is safe and feasible.
* **Execution Loop (Generate & Import)**: Introducing the exclusive "Refine & Import" flow. AI-generated suggestions can be converted into structured data with one click and automatically written into your weekly schedule, eliminating manual entry.
* **AI Visual Assistant (Snap & Ask)**: Encountered an unfamiliar machine? Snap a photo, and the AI will immediately identify it and explain the target muscle groups and operation techniques.

**⚙️ Model Freedom & Enhanced Connectivity**
* **Multi-Provider Support**: Pre-configured support for mainstream large model services including **OpenAI, DeepSeek, Kimi (Moonshot), Qwen (Aliyun), Gemini, and SiliconFlow**.
* **Smart Configuration Memory**: Automatically remembers API Keys and Base URLs for different providers, allowing seamless switching.
* **Connectivity Enhancement**: Built-in smart URL cleaning logic (automatically handles `v1/` suffixes) to significantly improve connection success rates for custom Base URLs.

**🎨 Markdown Rendering Engine 2.0**
* **Perfect Typography**: Comprehensively upgraded text display, supporting code block highlighting, multi-level headers, blockquotes, and list formats (essential for weekly plan details).
* **Enhanced Interaction**: Added long-press to copy and text selection functions, making it easy to extract key information from AI responses.

**🔒 Privacy First**
* **Local Storage**: API Keys are stored strictly locally via SharedPreferences and never pass through intermediate servers.
* **Data Masking**: Historical data sent to the AI includes only concise summaries (e.g., total volume, max weight) to maximize privacy protection.

---

## 🔥 Core Features

**📊 Training Volume Heatmap**
* **Visual Distribution**: Intuitively displays daily training intensity through color depth on the History page.
* **Calculation Standard**: Based on "Historical Cumulative Volume Load (Weight × Reps)".

**⏱️ Pro Training Timer**
* **Lock Screen Persistence**: Real-time countdown in the notification bar, supports lock screen viewing, and keeps running in the background without being killed.
* **Permission Guide**: Smartly guides users to enable necessary permissions to ensure timer stability.

**💪 Body Status & Metrics**
* **Auto-Calculation**: Automatically calculates **BMI** and **BMR** based on height/age/gender after logging weight.
* **Local Privacy**: All body profile data is stored only in the local database.

**📈 Visual Statistics Center**
* **Native Charts**: High-performance line and bar charts drawn with Compose Canvas.
* **Multi-Dimensional Analysis**: Track trends in Weight, Total Cardio Duration, and 1RM (One Rep Max) for specific exercises.

**⚖️ Smart Data Logic**
* **Unilateral Merger**: Automatically identifies and merges volume data for left/right split exercises (e.g., Single-Arm Rows).
* **Smart Import**: Automatically creates new exercise templates, fills in body part info, and deduplicates during CSV import.

**🌍 Multi-Language Support**
* Full support for **English, Simplified Chinese, German, Spanish, and Japanese**.

---

## 📱 User Guide

### 1. Enable AI Coach `NEW`
* Go to the **AI Coach** tab.
* Click the status bar at the top to configure your model (DeepSeek or OpenAI is recommended).
* **Generate Plan**: Click "Generate Weekly Plan", set your Training Focus and Equipment constraints, and wait for the AI analysis.
* **Import Schedule**: Once satisfied with the AI-generated plan, click the import button at the bottom to apply it directly to your weekly schedule.

### 2. Visual Assistant & Free Chat `NEW`
* Click the camera icon on the AI page to snap a photo of equipment or an exercise for guidance.
* Use the Free Chat mode to ask any fitness-related questions.

### 3. View Training Heatmap
* Located at the top of the **History** page.
* The legend displays volume levels from low to high.

### 4. Training Timer & Lock Screen
* Enter the time (in minutes) next to the exercise set and click the play button to start.
* **Lock Screen View**: Light up the screen (no unlock needed) to see the countdown in the notification area.

---

## 📂 CSV Import Format

Supports expanding the exercise library via CSV import. The recommended full format is as follows:

**Header**:
`Day,Name,Category,Target,BodyPart,Equipment,IsUni,LogType`

**Notes**:
* **Day**: 1-7 (Monday to Sunday)
* **Category**: STRENGTH / CARDIO / CORE
* **IsUni**: true / false (Unilateral)
* **LogType**: 0=Weight x Reps, 1=Duration, 2=Reps Only

**Key Reference**:
* **BodyPart**:
    * Upper/Torso: `part_chest`, `part_back`, `part_shoulders`, `part_arms`, `part_abs`
    * Lower: `part_hips`, `part_thighs`, `part_calves`
    * Other: `part_cardio`, `part_other`
* **Equipment**: `equip_barbell`, `equip_dumbbell`, `equip_machine`, `equip_cable`, `equip_smith_machine`, `equip_bodyweight`, `equip_cardio_machine`, `equip_other`

**Example:**

```text
Day,Name,Category,Target,BodyPart,Equipment,IsUnilateral,LogType
1,Bench Press,STRENGTH,3x12,part_chest,equip_machine,false,0 
1,Plank,CORE,3x60s,part_abs,equip_bodyweight,false,1 
2,Squat,STRENGTH,4x10,part_thighs,equip_barbell,false,0 
3,Running,CARDIO,30min,part_cardio,equip_cardio_machine,false,1 
4,Crunches,CORE,4x20,part_abs,equip_bodyweight,false,2

```

---

## 🛠️ Tech Stack

* **Language**: Kotlin
* **UI Framework**: Jetpack Compose (Material3)
* **Architecture**: MVVM (Model-View-ViewModel)
* **Database**: Room (SQLite) with Migrations (v9)
* **Service**: Android Foreground Service (Keep-alive Timer)
* **Graphics**: Compose Canvas (Heatmap & Custom Charts)
* **Concurrency**: Coroutines & Flow

---

## 📝 Version Info

* **Version**: 6.0
* **Version Code**: 60
* **Author**: Designed & Built by enersto & Hajimi

