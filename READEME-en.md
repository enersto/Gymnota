# myFit - Personal Workout Tracker & Memo (V5.2)
**myFit** is a personal fitness tracking and memo application built with Android Jetpack Compose. It is designed to help users plan weekly training routines, log daily check-ins, and track workout and weight changes through visualized history records.

🚀 **V5.2 Major Update**: Introducing a new Chart Statistics module, multi-dimensional exercise attributes (Body Part/Equipment), a new Core training category, and full Database Backup functionality.

---

## ✨ Core Features

* **📊 Visual Statistics** `NEW`
* **Multi-dimensional Analysis**: Provides four analysis modules: Weight, Cardio Duration, Strength Load, and Core Reps.
* **Trend Tracking**: Supports switching between **Daily/Monthly** granularity to intuitively view long-term trends in training volume and weight.
* **Native Rendering**: High-performance Line and Bar charts implemented using Compose Canvas.


* **💪 Exercise Library 2.0** `NEW`
* **Category Expansion**: Added a **"Core"** category (e.g., Crunches, Planks) alongside "Strength" and "Cardio".
* **Detailed Attributes**: Supports tagging **Target Body Part** (e.g., Chest, Back, Abs) and **Equipment Used** (e.g., Barbell, Dumbbell, Machine).
* **Structured Sets**: Supports recording multiple sets (Set No. / Weight or Duration / Reps).


* **💾 Data Management & Security** `NEW`
* **Dual Backup**: Supports exporting CSV tables and now includes full backup and restore of **Database (.db)** files.
* **Lossless Migration**: Automatically migrates legacy data during app updates to ensure no records are lost.


* **🌍 Internationalization Support**
* Fully supports **Simplified Chinese, English, Deutsch, Español, and 日本語**.
* Full localization adaptation for UI text and statistical charts.


* **📅 Smart Weekly Routine**
* Visual interface to schedule fixed training exercises for each day of the week, automatically generating daily to-do tasks.
* Supports setting daily type tags (e.g., Core Day, Rest Day), with the home page theme color changing automatically based on the type.


* **✅ Daily Check-in**
* Pill-button interaction; clicking check-in triggers a 🎉 celebration animation.
* Dynamically add/remove exercise sets to accurately record every lift.
* Quick weight recording via the top-right corner.



---

## 📱 User Guide

### 1. Building the Exercise Library

* Go to **Settings -> Manage Exercise Library**.
* When adding an exercise, in addition to basic info, you can now set:
* **Body Part**: Chest, Back, Legs, Shoulders, Arms, Abs, Cardio, etc.
* **Equipment**: Barbell, Dumbbell, Smith Machine, Bodyweight, etc.
* **Default Target**: e.g., "4 sets x 12 reps".



### 2. Planning the Weekly Routine

1. Click **Weekly Routine** in Settings.
2. Select a day (e.g., "Monday") and add fixed exercises for that day.
3. Set the training type for that day (e.g., "Strength Training Day").

### 3. Daily Training & Check-in

* The Home page displays today's plan.
* Click a card to expand details, then click **"+ Add Set"** to record the specific weight and reps for each set.
* Check the box when finished.

### 4. Viewing Statistics

* Go to the **History** page.
* Click the toggle at the bottom to switch between **"List / Chart"** views.
* In **Chart Mode**, you can view max weight trends for single exercises (e.g., "Bench Press") or total duration bar charts for cardio.

---

## 📂 CSV Import Format (V5.2 Update)

To adapt to the new exercise attributes, the CSV format for batch import has been upgraded (legacy format is compatible, but the new version is recommended for the full experience):

`Day(1-7), Name, Category(STRENGTH/CARDIO/CORE), Target, BodyPartKey, EquipmentKey`

**Body Part Key Reference**: `part_chest` (Chest), `part_back` (Back), `part_legs` (Legs), `part_abs` (Abs), `part_cardio` (Cardio)
**Equipment Key Reference**: `equip_barbell` (Barbell), `equip_dumbbell` (Dumbbell), `equip_bodyweight` (Bodyweight), `equip_machine` (Machine)

**Example:**

```text
1, Barbell Bench Press, STRENGTH, 4x8, part_chest, equip_barbell
1, Pec Deck Fly, STRENGTH, 4x12, part_chest, equip_machine
2, Running, CARDIO, 30min, part_cardio, equip_cardio_machine
3, Plank, CORE, 3x60s, part_abs, equip_bodyweight

```

---

## 🛠️ Tech Stack

* **Language**: Kotlin
* **UI Framework**: Jetpack Compose (Material3)
* **Architecture**: MVVM (Model-View-ViewModel)
* **Database**: Room (SQLite) with Migrations (v7 -> v8)
* **Graphics**: Compose Canvas (Custom Charts)
* **Concurrency**: Coroutines & Flow

---

## 📝 Version Info

* **Version**: 5.2
* **Version Code**: 52
* **Author**: Designed & Built by enersto & Hajimi

---

> **Note**: This project is for personal tracking and learning purposes. Data is stored locally on your device.

