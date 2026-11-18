# Flutter + Rust 主运行链路（6.4 开发版）

## 当前职责

| 部分 | 当前实现 |
|---|---|
| 默认根导航及打卡、历史、动作、AI 私教、设置 | Flutter；旧 Compose 页面保留用于回退和对照 |
| 内置动作库查询、筛选、多语言搜索 | Rust，只读独立 SQLite |
| 个人数据库建库与 v7–v19 迁移 | Rust，沿用原有 v19 SQLite 表结构和个人名称 |
| 任务、分组、模板、周度方案、档案、身体指标、设置、AI 记录 | Rust SQLite 查询与事务 |
| 历史规范化、训练指标和图表数据 | Rust |
| 准备计时、工作计时、暂停恢复、结束判定 | Rust 状态转换；Android 提供单调时钟 |
| 页面订阅更新 | Rust 提交后经 Android 修订通知和 Flow，送达 Flutter MethodChannel |
| 备份导出、迁移、WAL checkpoint | Rust；Android 提供文件流与原子替换 |
| 系统接入 | Kotlin 提供通知、前台服务、权限、声音、照片/文件选择和 HTTP 传输等 |

原生旧入口的训练编辑、身体记录、模板及周计划操作也调用 Rust 写入，不使用单独的现用数据库。

## Room 边界

默认 Rust 模式下，`AppDatabase.getDatabase()` 明确拒绝打开现用 Room 数据库。初始化、日常查询与写入、历史订阅、备份导出和恢复时的现用库 checkpoint 均不调用该入口。Room 的 invalidation tracker 已退出 Rust 主运行路径。

Room 仍有两个用途：

- 恢复时，对已迁移的临时副本执行原有结构校验；校验后关闭，再安装 Rust 生成的一致快照。
- `-PrustHistory=false` 时保留旧实现。Kotlin 页面、Room 实体、DAO 及依赖尚未从源码和 APK 全量删除。

因此，这里表示现用数据所有权已切到 Rust，不表示整个项目已删除 Kotlin 或 Room。AI prompt 本地化和 Android 网络等适配仍有 Kotlin 代码。

## 事务和刷新

写入由私有、同 UID 的 `RustWorkoutProvider` 执行。个人库写入使用显式事务，更新基于事务内读取到的当前记录；正常提交后按任务、模板、身体指标、档案、设置或 AI 历史类别发送修订通知。跨页订阅不再依赖 Room 观察写入。

批量操作中途失败会回滚。传输错误可能发生在已经提交之后，因此只触发重新读取，不自动重放写入。历史数据包和备份通过文件描述符传递，避免 Binder 大消息限制。

恢复先把输入文件写入私有临时路径，迁移并校验副本，再生成一致快照。现用库 checkpoint、停止计时和同目录原子替换串行执行；输入错误时不替换现用数据，成功后自动重新启动。

内置目录和动画继续由 APK 提供，不进入个人数据库备份。WebP 资源保留既有 GIF URI 映射。

## 本轮验证（2026-11-18）

- Rust 纯业务单元测试：25 项通过。
- Android instrumentation：13 项通过，包括 Rust 写入后已加载历史的更新/删除、失败批量事务回滚、禁止现用 Room 打开，以及 AI 流式传输/协议测试。
- SQLite 副本验证：五语言首次建库、重复初始化、失败种子事务恢复；v7–v19 迁移逐表结构/数据对照原迁移 SQL；设置、CSV、AI 存储、实时查询、历史计算及备份快照通过。原始个人备份未修改。
- 模拟器 Flutter 界面：个人动作新增/编辑/删除立即刷新；主题、语言和周类型设置生效；身体记录更新已打开历史图表；CSV 导入立即出现在周度方案和个人动作搜索。
- 计时界面：准备 → 暂停保持时间 → 恢复 → 后台完成 → 返回显示记录 → 历史列表立即可见，无需重启。
- 旧 Compose 打卡页：输入重量/次数、添加组、打卡后，Flutter 历史立即显示 Rust 写入的结果。
- 备份恢复界面：导出 → 新增临时动作 → 恢复 → 自动重启，原动作保留、备份之后的临时动作消失。另验证了含 WAL 提交数据的 checkpoint。
- x86_64 和 ARM64 Preview 构建通过。界面测试使用模拟器，未操作用户手机。

纯业务测试：

```powershell
cargo test --manifest-path rust/history_core/Cargo.toml --locked --offline --lib
```

Android 回归类：

```text
com.example.myfit.flutter.HistoryRepositoryTest
com.example.myfit.flutter.AiStreamRuntimeTest
com.example.myfit.flutter.AiStreamContractTest
```

构建时可通过 `-PpreviewAbi=x86_64 -Ptarget-platform=android-x64` 指定模拟器，或通过 `-PpreviewAbi=arm64-v8a -Ptarget-platform=android-arm64` 指定 ARM64。测试 APK 由 `:app:assembleDebugAndroidTest` 生成。

## 迁移目标核对

本轮验收针对“核心业务逻辑与个人/内置数据持久化主要由 Rust 承担、绝大部分前端由 Flutter 承担”，并保留必要 Android 平台桥。

- 默认启动组件为 Flutter 宿主；使用 MAIN/LAUNCHER 启动后，五个主页面均正常进入，个人动作能读取。
- 现用个人库禁止 Room 打开；Rust 首次建库、升级、查询、事务和刷新链路均有运行验证，内置目录查询沿用已接入的 Rust 路径。
- 旧数据兼容由 v7–v19 副本迁移对照和实际界面备份恢复验证；个人备份格式、名称及独立动作库的免备份边界保留。
- 事务回滚、已加载历史的增删刷新、身体指标跨页刷新、后台计时及归档原生页写入后刷新均已验证。
- Kotlin 系统桥、部分 AI prompt 的本地化组织、临时恢复副本的 Room 校验及禁用 Rust 时的归档回退仍保留。它们不拥有默认模式下的现用个人数据持久化。

## 旧打卡入口撤下（2026-11-18）

Flutter 打卡页已删除旧版入口和相应原生路由。旧 `DailyPlanScreen.kt` 及其 `MainScreen.kt` 宿主已完整存档至仓库根目录 `legacy/kotlin/ui`，不在 Android 源集中，因此所有构建类型均不编译或打包它们。其他工具仍使用的身体记录弹窗已拆为独立文件。

此前针对旧 Compose 打卡页所做的验证属于撤下前的历史记录。当前默认及无工具参数的原生宿主入口统一回到 Flutter。Room 依赖、实体、DAO、备份校验和 Rust 数据通路没有因撤下页面而修改。

## 动作编辑迁至 Flutter（6.4）

新增、编辑、内置动作导入和打卡中的动作详情使用 Flutter 表单/详情。字段覆盖名称、目标、类别、部位、器械、记录方式、单边、说明、照片和动画；保存调用 Rust 模板事务，并通过现有变更事件刷新动作与打卡数据。Android `ExerciseEditorBridge` 仅接相册、相机、媒体读取和现有 Rust 保存入口，不绘制编辑界面。

照片和动画分别保留、移除；取消编辑会清理未提交的照片，保存失败保留草稿。旧 `ExerciseManagerScreen.kt` 的编辑弹窗已移到 `legacy/kotlin/ui`，不再进入任何构建源集。编辑文案覆盖中、英、西、德、日。Room 保持本轮之前的兼容与副本校验用途。
