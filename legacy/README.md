# Kotlin 页面源码存档

`kotlin/ui/DailyPlanScreen.kt` 、`MainScreen.kt` 与 `ExerciseManagerScreen.kt` 是撤下入口时的完整原始源码，哈希记录在同目录。该目录位于 Android/Flutter 源集之外，不参与任何构建类型的编译和 APK 打包。

正式入口使用 Flutter。旧打卡的 MethodChannel 动作、原生路由及旧根导航回退已解除。其他原生工具仍需要的身体记录弹窗单独保留为 `app/src/main/java/com/example/myfit/ui/WeightDialog.kt`。

Room 的依赖、实体、迁移和临时备份恢复校验保持现状。若需要研究旧界面，可查看存档；恢复到可运行旧版本应在隔离分支中进行，不能只把文件复制回主源集。

动作新增、编辑和内置导入已迁至 Flutter `exercise_editor.dart`，保存使用 Rust 模板事务。旧 Kotlin 编辑弹窗随管理页源码一起存档，不参与构建。
