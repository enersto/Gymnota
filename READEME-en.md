# Gymnota - Personal Workout Memo & Tracker (V5.8.1)

**Gymnota** is a personal fitness tracking and memo application built with **Android Jetpack Compose**. It is designed to help users plan weekly training routines, log daily workouts, and utilize **native charts** to deeply visualize training volume and body metric trends.

🚀 **V5.8.1 Major Update**: Introduces the **Training Volume Heatmap** for visualizing workout frequency and load history; refactors data logic for **Unilateral Exercises**; optimizes chart legend layout and multilingual experience.

---

## ✨ Core Features

**🔥 Training Volume Heatmap** `NEW`

* **Visual Distribution**: Added a Heatmap component to the History page, intuitively showing daily training intensity through color depth.
* **Volume Definition**: Uses "Historical Cumulative Volume Load (Weight × Reps)" as the statistical standard.
* **UI Optimization**: Redesigned bottom legend area with a horizontally centered layout and clear hint text.

**⚖️ Smart Data Logic** `NEW`

* **Unilateral Merging**: Comprehensively refactored data processing logic. It now automatically identifies and correctly merges data for left/right split unilateral exercises (e.g., Single-Arm Dumbbell Row) to ensure accurate total volume calculations.

**💪 Body Stats & Metrics**

* **Smart Recording**: Added a "Record Body Stats" entry point on the Check-in page, supporting Weight, Height, Age, and Gender.
* **Auto Calculation**: The system automatically calculates daily **BMI (Body Mass Index)** and **BMR (Basal Metabolic Rate)** based on the input profile.
* **Privacy Protection**: Basic profile data (Height/Age/Gender) is stored strictly in the local database and can be modified in Settings at any time.

**⏱️ Pro Workout Timer**

* **Foreground Service**: Refactored timing logic ensures the timer is not killed by the system when the screen is locked, off, or the app is in the background.
* **Lock Screen Display**: Real-time countdown shown in the notification bar, allowing users to check remaining time directly from the lock screen.
* **Permission Guide**: Added a smart guide dialog for "Lock Screen Notification" permissions.

**📈 Visual Statistics Center**

* **Native Charts**: High-performance Line and Bar charts drawn using Compose Canvas.
* **Multi-dimensional Analysis**:
* **Body Stats**: Track long-term trends for Weight, BMI, and BMR.
* **Cardio/Strength**: Analyze daily/monthly total cardio duration, as well as Max Weight or Reps trends for specific exercises.


* **View Switching**: Supports toggling between "Day" and "Month" granularity.

**🔄 Smart CSV Import**

* **Auto-Collection**: When importing a weekly plan via CSV, if new exercises are included, the system automatically creates templates and supplements body part/equipment information.
* **Smart De-duplication**: Automatically detects and cleans up duplicate data in the Exercise Library during import.

**🌍 Internationalization (i18n)**

* Full support for **Simplified Chinese, English, Deutsch, Español, and 日本語**.
* **Deep Adaptation**: V5.8.1 adds multilingual support for the Heatmap hint "Values represent historical cumulative volume", ensuring a fully localized UI.

**📅 Smart Weekly Routine**

* Visual interface for arranging fixed workout routines for each day of the week, automatically generating daily to-do lists.
* Support for setting type tags for each day (e.g., Core Day, Rest Day), with the home page theme color changing automatically based on the tag.

---

## 📱 User Guide

### 1. Viewing the Heatmap `NEW`

* Go to the **History** page.
* The **Training Volume Heatmap** is displayed at the top of the page.
* **Legend**: The bottom legend shows volume levels from low to high. The text hint "Values represent historical cumulative volume (Weight × Reps)" automatically adapts to the current system language.

### 2. Recording Body Stats

* Click the **"Record Body Stats"** button below the date on the Home page.
* **First Use**: A dialog will ask for Height, Age, Gender, and Weight to establish a baseline profile.
* **Daily Log**: For subsequent entries, only Weight is required. The system reuses baseline info to calculate BMI/BMR.

### 3. Timer & Lock Screen Display

* Enter the time (in minutes) to the right of an exercise set and click the Play button to start.
* **Lock Screen**:
* Once the timer starts, you can lock your phone.
* Wake the screen (no need to unlock) to see the countdown in the notification area.
* *Note: If not displayed, please follow the App's prompt to enable "Lock Screen Notifications" in system settings.*



### 4. Managing Library & Plans

* Go to the **Settings** page.
* **Exercise Library**: Efficiently manage exercises via a three-level classification (Category -> Part -> Equipment).
* **Weekly Plan**: Manually check boxes or batch import weekly fixed routines via CSV.

---

## 📂 CSV Import Format

Supports expanding the Exercise Library by importing CSV files. The following complete format is recommended:

`Day(1-7), Name, Category(STRENGTH/CARDIO/CORE), Target, PartKey, EquipmentKey`

**Key Reference**:

* **Body Parts (PartKey)**:
* Upper/Torso: `part_chest`, `part_back`, `part_shoulders`, `part_arms`, `part_abs`
* Lower: `part_hips`, `part_thighs`, `part_calves`
* Other: `part_cardio`, `part_other`


* **Equipment (EquipmentKey)**: `equip_barbell`, `equip_dumbbell`, `equip_machine`, `equip_cable`, `equip_smith_machine`, `equip_bodyweight`, `equip_cardio_machine`, `equip_other`

**Example:**

```text
1, Barbell Bench Press, STRENGTH, 4x8, part_chest, equip_barbell
3, Burpee, CORE, 4x15, part_abs, equip_bodyweight

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

* **Version**: 5.8.1
* **Version Code**: 581
* **Author**: Designed & Built by enersto & Hajimi
