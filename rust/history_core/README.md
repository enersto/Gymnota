# 隔离的训练历史核心

默认 feature 仅处理显式字段白名单的历史 JSON 快照，不访问数据库。`catalog-sqlite` feature 另行启用只读内置动作库模块（rusqlite + bundled SQLite）；`android-jni` 同时启用目录模块和 JNI 入口。历史模块本身仍不访问文件或数据库，个人 Room 不由 Rust 打开。

## 接入边界

Android 继续负责 Room 事务快照、备份副本的只读打开和字段白名单。Rust 负责组 JSON 解析、兼容字段规范化、数值校验和统计。结果通过原有 MethodChannel 返回 Flutter。原生图表、写入、通知、相机、语言设置和备份恢复均保留原实现。

`RustHistoryCore` 加载或处理失败时，回退到旧的 Kotlin 解码；Flutter 显示记录并提示统计暂不可用。也可使用 `-PrustHistory=false` 完全禁用本模块，构建无需 Rust 且 APK 不含 Rust 库。`tools/build_flutter.ps1 -WithoutRust` 提供相同选项。

目录模块使用短生命周期只读连接，只访问独立内置动作 SQLite；不连接个人数据库。未增加异步运行时或 Flutter FFI 插件。

## JSON 合约（schemaVersion 1）

输入是 `{tasks: [...], weights: [...]}`。读取端只提供已完成训练。任务字段为 id/date/name/category/logType/unilateral/target/actualWeight/sets；sets 可以是备份中的 JSON 字符串或已解析数组。每组只保留 setNumber/weightOrDuration/reps/rightWeight/rightReps。基础体重字段为 date/weight；图表读取额外允许 bodyFatKg/skeletalMuscleKg/bodyWaterPercentage/waistCircumference/hipCircumference，以及任务 bodyPart。可选 profile 仅允许 height/age/gender，不传 AI 配置。未知顶层或任务字段不传出，不接受 AI 配置和会话。

输出包含 `snapshot`、`summary`、`byDay`、`byMonth`、`byExercise`、`legacyCharts`、`charts`、`warnings`。snapshot 不翻译个人名称，不改动作 ID，不推断记录中不存在的组。输入上限 16 MiB；错误返回稳定错误码，不包含个人值。warnings 最多 1000 条，仅包含 taskId、字段名、错误码。格式不明确的原始字段保留为文本；有效数值才参与新统计。

统计字段：

| 字段 | 定义 |
|---|---|
| taskCount | 快照内已完成动作记录数 |
| setCount | 实际存储的组条目数，不从自由文本推断 |
| trainingDays | 有记录的有效 ISO 日期去重计数 |
| reps | 负重/自重记录的有效次数；单边记录左右相加 |
| volumeKg | 重量 × 次数之和，单位 kg·次；双边记录按已存重量计算，不额外翻倍 |
| durationMinutes | 计时记录的分钟数；支持裸数字、h/min/s、中文时分秒和 m:ss/h:mm:ss |
| maxWeightKg | 负重记录的最大已记录重量，包含单边两侧；不是 1RM |
| legacyFallbackCount | 无结构化组的旧记录数 |

无结构化组时继续原样展示 target/actualWeight；可明确解析的 actualWeight 可用于最大重量，计时 target 可用于时长，但不从“3 x 12”推断训练量或组数。无效日期保留在 snapshot 中，发出警告并排除在按日/月统计之外。数字格式严格；例如负数、逗号混合或自由文本不猜测含义。

`byMonth` 是新统计的期间汇总；不能拿它直接替换原生图表的月均值。`legacyCharts` 单独复刻原生每日解析语义，包括首个数值、h/s 单位、左右最大重量和旧字段回退，供旧图表一致性校验和未来迁移使用。现有原生图表的月均值逻辑本轮未改。

## 构建与维护

验证工具链：Rust 1.99.1，NDK 28.2.13676358，最低 Android API 28（Android 9）；Android 架构 arm64-v8a、armeabi-v7a、x86_64。Cargo.lock 固定依赖。release 启用体积优化、LTO 和符号剥离，保留 panic 捕获。

```powershell
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo test --manifest-path rust/history_core/Cargo.toml --locked
cargo build --manifest-path rust/history_core/Cargo.toml --release --bin history_cli --locked
```

宿主 Gradle preBuild 自动调用 tools/build_rust.ps1，从源码生成 JNI 库，不提交二进制。脚本当前支持 Windows NDK；纯核心可用于其他平台。若 Rust 不在 Android Studio 的 PATH 中，在项目根创建不提交的 `rust.local.properties`：

```properties
cargo.path=C:/path/to/cargo/bin/cargo.exe
cargo.home=C:/path/to/cargo
rustup.home=C:/path/to/rustup
```

已在正常用户位置安装 Rust 的开发者可以仅使用 PATH，不必创建此文件。NDK 默认从 local.properties 的 sdk.dir 定位，也可设置 ANDROID_NDK_HOME。生成库位于 app/build/generated/rustJniLibs，Cargo 缓存位于 rust/history_core/target；均不提交。不要把个人备份放进测试资源。

```powershell
python tools/verify_history_core.py --database '<备份副本>' --report '<核验报告.json>'
```

核验工具只读 SQLite，通过白名单生成快照，比较所有记录字段、按日/月/动作指标和旧图表每日数据点；报告仅含数量和通过状态，不写出动作名、训练值或密钥。

Android 仪器测试 MediaAndRustCoreTest 直接调用真实 JNI，并与旧解码及强制 Kotlin 回退比较。Flutter 测试覆盖统计区展开、统计失效时记录继续可见，以及原有动作库与历史交互。


## Flutter 图表数据扩展

charts.rs 生成独立的 `charts` 数据包（policy: native-history-v1），用于 Flutter 历史页的图表视图。原生历史页面继续可用。

- exercises：按类别和个人名称列出选项；logType 取该名称最早历史记录，unilateral 判断同名记录中是否有单边动作，兼容原生选择逻辑。
- exerciseSeries：按名称保存 duration/weight/reps/rightWeight 四种模式的 daily/monthly 数据点。同名跨类别记录按原逻辑共同参与曲线。
- cardio：有氧按日汇总左侧时长，月度取这些日值均值。
- body：weight/bmi/bmr/bodyFatKg/skeletalMuscleKg/bodyWaterPercentage/whr。日、月分别按原记录在对应日期桶中求均值。BMI/BMR 复用原有公式及相应快照的身高、年龄、性别；备份使用备份里的资料。
- heatmap：按原部位键累计历史负荷及归一化强度，保留原生的最低权重/次数和旧 target 回退规则。

图表计算单独保留原生 Float 运算及旧文本解析行为，不使用 summary/byMonth 的校验统计替代历史曲线。月度训练曲线是“每日值的平均值”，不是月合计或月最大；身体指标直接平均该月的记录，存在同日多条时不再平均日均值。图表日期非法时不输出该点，原记录仍可见。

只读一致性核验：

```powershell
python tools/verify_history_charts.py --database '<备份副本>' --report '<图表核验.json>'
```

新白名单不改变 Room schema 或备份格式。Rust 不可用时，列表继续走 Kotlin 回退；Flutter 图表显示提示并提供返回原有页面的入口，不把备份恢复进当前库。

## 内置动作库查询（catalog-sqlite）

输入为 Android 安装器提供的 path 和 language/query/part。SQLite 连接强制 READ_ONLY，不创建缺失文件；locale 与 bodyPart 使用绑定参数，ORDER BY 保留 canonical_name/source_id 顺序。名称空译文回退 canonical_name，说明空译文回退英文；未知语言同样回退。搜索使用 Unicode 小写后的字面包含匹配，百分号、下划线、引号均不作为 SQL 语法。可空 category/logType/isUnilateral/defaultTarget 原样保留。

RustCatalogTest 在模拟器直接调用 JNI（不能静默回退），逐项对比所有字段及顺序，并覆盖五语言、地区语言与未知语言、部位筛选、译名/英文/编号/说明搜索以及 SQL 特殊字符。默认纯历史 cargo test 不需 C 编译器；启用 catalog-sqlite 的桌面构建需要本机 C 工具链。Android 编译脚本使用 NDK clang/llvm-ar 编译内置 SQLite。
