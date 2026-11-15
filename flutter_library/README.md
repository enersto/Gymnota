# Gymnota Flutter 嵌入模块

这是现有 Android 应用的增量模块。原生主导航、打卡、个人动作管理、图表、设置、计时、照片和备份恢复继续由原实现提供。

## 入口与衔接

- 管理动作库 → 内置动作库，在原标签页内直接显示 Flutter 内容，没有额外入口。搜索中英文名称、ID、说明，按本地 bodyPart 筛选，预览动图、放大查看，跟随全局五种语言设置。
- 详情「使用此动作」仅回传上游 sourceId 和显示语言；原生重新查询动作，匹配现有个人动作（优先动画 URI），打开原有编辑窗口。只有用户在原生窗口保存时才写入个人库。
- 历史 →「Flutter 历史」。默认读取当前 Room 数据的事务快照；可选择备份副本查看。展示已完成训练、日期、组号、负重/次数/时长、左右侧数据，以及日期对应体重。原有图表和操作通过返回原页面继续使用。
- 初始语言、主题继承现有设置；右上角语言菜单只预览模块的显示语言，不修改全局语言，也不翻译个人历史名称。

## 数据边界

内置 SQLite、翻译及 1,324 个无损动画 WebP 由 Android assets 中的同一份资源提供。Flutter 经 `gymnota/library` MethodChannel 调用 Rust 只读目录查询与搜索；Android 负责安装资源和本地化字段标签，列表传递首帧 PNG，详情传递 WebP。列表按需加载，媒体请求缓存最多 64 项。

个人数据通过 HistorySnapshot 的显式字段白名单读取，不向 Dart 传递 AI 密钥、设置和对话。当前库读取在 Room 事务中完成，包含已提交 WAL 内容，不直接复制正在打开的数据库文件。备份通过系统文件选择器复制到 noBackupFilesDir，SQLite 只读打开；校验失败不会替换上一次成功选择的副本。单个备份限制 100 MiB。正常关闭时删除副本；进程异常退出可能留下临时副本，不属于备份范围。

没有增加个人表字段或数据库迁移。现有照片和动图的独立存储规则保持原实现。Flutter 玻璃采用透明卡片和局部模糊；尚未移植 Compose 的镜头折射效果。

## 构建

使用 Flutter 3.47.9 / Dart 3.13.7、JDK 21，宿主 Gradle 8.14.3、AGP 8.13.2。Flutter 当前对 Gradle/AGP 的后续版本支持会打印提示，本次不升级到 AGP 9。初次构建会下载 Flutter 引擎与 NDK。

```powershell
.\tools\build_flutter.ps1 -FlutterSdk '<Flutter SDK 路径>' -Jdk '<JDK 21 路径>' -ChinaMirror -Abi arm64-v8a
```

脚本默认构建 `preview`：使用 Flutter release 引擎和 Android 调试签名，便于本地安装；不是用于商店发布的正式签名包。省略 `-Abi` 可构建三架构通用包，`-BuildType debug` 用于调试。宿主和模块统一使用 NDK 28.2.13676358，使打包时正常剥离引擎符号。

可选 `-Proxy http://127.0.0.1:7897` 仅作用于本次 Gradle 进程，不修改全局代理。先在模块中运行 `flutter pub get` 生成 `.android` 包装工程，再在 Android Studio 同步宿主项目。`.android`、`.ios`、SDK、缓存及构建产物不提交。此模块依赖宿主桥接，不能直接将生成的独立 Runner 当完整应用运行。

```powershell
cd flutter_library
flutter analyze --no-pub
flutter test --no-pub
```

Android 仪器测试 `com.example.myfit.flutter.HistorySnapshotTest` 使用内存数据库验证历史字段、单边组数据和隐私白名单，并读取真实内置动作资源验证五种名称语言和 WebP。不要把个人备份放入源码或测试 assets。


## Rust 历史核心

历史页现在默认经 Android JNI 调用隔离 Rust 核心，返回原记录和按日/月/动作的统计。界面增加可展开的训练统计与无法明确解析数值的提示，新增字段覆盖中英西德日。个人名称、照片/动图体系和原生功能保持原有行为。

构建前安装 Rust Android targets；本机已通过不提交的 rust.local.properties 配置路径，Android Studio 可直接同步和构建。详见 ../rust/history_core/README.md。`tools/build_flutter.ps1 -WithoutRust` 或 Gradle `-PrustHistory=false` 可生成完全不含 Rust 库的回退版本。

## 动画格式实验

tools/benchmark_animation.py 对所有源 GIF 建立元数据清单，按帧数桶的最小/中位/最大体积以及全库大小分位点选样。编码结果仅写入指定实验目录，不替换 app assets。脚本验证无损结果的完整像素时间线、总时长和循环，并分别记录首帧与全动画桌面解码时间。

```powershell
python tools/benchmark_animation.py --assets <归档中提取的原始GIF目录> --gif2webp '<gif2webp.exe>' --output '<实验目录>' --android-assets app/build/generated/mediaBenchAssets
```

需要 Pillow 和 Google libwebp 1.6.0 工具。生成的 Android 样本只进入 androidTest APK，不进入应用包；MediaAndRustCoreTest 验证 Android AnimatedImageDrawable、首帧像素、循环设置和首帧位图字节数。测试完整解码内存与真机 FPS 需要另行实测。原始 GIF 保留在迁移前项目归档，当前 APK 仅打包 WebP，最低系统为 Android 9 / API 28。


## 历史图表

入口：历史 → Flutter 历史 → 图表。支持力量、核心、有氧的动作搜索选择、力量单边左右侧、日/月切换、有氧总时长柱图、体重/BMI/BMR/体脂肪/骨骼肌/水分/腰臀比曲线及像素肌肉热力图。点按曲线可查看日期和值，表格图标可查看全部数据点。个人名称不翻译。

图表由隔离 Rust 核心计算，Flutter 使用自带 Canvas 绘制，没有新增图表包。复用现有五种语言资源和共享玻璃组件。读取当前快照或选定备份均可查看图表，源切换会重置动作选择，防止沿用旧曲线；刷新时按新数据更新。原生图表入口保留。

图表新增 height/age/gender、bodyPart 和可选身体指标的白名单，不读取 AI 配置。保持原生月均值及单边逻辑；详见 Rust 核心 README。图表数据点不受列表搜索文本过滤。

## Rust 内置动作库

原标签页和动画选择器复用同一个 Flutter 目录组件，经 ExerciseCatalog.search 读取 Rust 结果；正常路径由 Rust/rusqlite 只读打开已安装的目录 SQLite，使用绑定参数选择译名与说明、部位过滤，再按译名/英文名/编号/说明进行 Unicode 小写包含搜索。Flutter 输入延迟 180 ms，并通过请求序号丢弃过时回复。Rust 不接收 Dart 文件路径，不打开个人 Room；禁用 Rust 或 JNI 失败时回退 Kotlin。详情播放与“使用此动作”连接保持不变，个人命名优先。

历史 SQLite 快照仍由 Android/Room 读取；不能将本次动作库改造等同于个人数据库已经迁移到 Rust。动画仍在 Android assets，不额外在 Flutter assets 打包。CatalogMedia 与 Coil 拦截器兼容旧 `.gif` URI，私有照片路径不改写。

## 替换原内置动作库（2026-11-08）

原 HorizontalPager 的“内置动作库”直接嵌入透明 TextureView FlutterView，保留原管理页标题、搜索栏、我的动作/内置动作滑动栏、个人分类和底部导航。移除独立浏览按钮、旧 Compose 目录列表及旧 Flutter 全屏目录组件；动画选择器复用 EmbeddedCatalog。

LibraryBridge 在 FlutterLibraryActivity（历史）与 EmbeddedCatalogHost（目录）间共享，目录不会启动新 Activity。“使用此动作”通过 sourceId 返回现有原生编辑窗口，按动画关联优先复用个人动作，只有保存才写个人数据。Native query/language/theme 经 embeddedState 更新；Dart 详情弹窗、放大和返回由同一个 Navigator 处理。Android 宿主转发前后台事件并销毁引擎，引擎和着色器提前预热，原管理页只在选择内置库后挂载 Flutter。

列表按原页面保留部位顺序、64 dp 首帧、动作名称/部位和底部留白；详情保留类别、版权信息、180 dp 动画、放大、部位/器械/单边、目标、记录方式和说明。Flutter 的玻璃面板沿用宿主颜色并对齐背景坐标，不等同于原生玻璃着色器的逐像素折射。

## 详情弹窗使用整屏边界

Flutter 的 Navigator 默认只能覆盖 FlutterView 的区域；嵌入标签页不代表它能覆盖原生导航。目录详情/放大图现在通过 CatalogOverlayWindow 临时将同一个 FlutterView 移入 Activity 的整屏透明 Dialog，关闭后放回原容器，不增加引擎，不改变整页导航。原位置通过异步窗口截图保留列表背景，整屏窗口统一遮罩与拦截底层标题栏/根导航操作。

Native 布局完成后才返回 openCatalogOverlay，Flutter 在新视口中显示 DialogRoute；等待路由退出动画完成后再 closeCatalogOverlay。先恢复原视口、搜索/筛选与滚动位置，再执行“使用此动作”，防止原生编辑窗口被浮层遮挡。快速重复点击只打开一次。动画选择器复用同一整屏宿主。

CatalogOverlayWindowTest 验证整屏尺寸、屏幕中央与两次打开/关闭后复位；Flutter 测试覆盖放大图正方形居中、逐层返回、先关闭整屏层再选用及列表偏移恢复。

## Liquid Glass 组件（2026-11-08）

固定使用 MIT 许可的 liquid_glass_widgets 1.17.0，许可证与说明在 docs/third-party/liquid_glass_widgets。目录部位筛选、历史操作、分段切换及可搜索选择器通过 lib/glass.dart 统一接入；长列表卡片使用 solid 表面，避免逐项分配折射着色器。初始化预载着色器，主题跟随原应用，原生背景用全局坐标渐变对齐。

详情弹窗采用单层 minimal 磨砂背景，取消每个按钮的独立折射；操作按钮使用有尺寸约束的胶囊形状。整屏宿主关闭原生窗口动画，由 Flutter 统一控制入场；去除同步 TextureView.getBitmap、串行隐藏键盘及额外一帧等待。480 px 可选窗口截图异步到达，超时或失败不阻止显示；晚到结果不会覆盖已恢复的列表。关闭后仍恢复搜索、筛选和列表偏移，随后连接原生动作编辑。

15 项 Flutter 测试通过，包括小屏长德语按钮、慢截图、放大居中、重复点击及先关闭再选用。Android 核心回归 12 项通过，最终弹窗/渲染 2 项复测通过。硬件图形模拟器 Debug 单次测量：完整显示约 308 ms，相同测试较前一版约 447 ms 缩短；此结果不代表真机帧率或稳定耗时。lib/glass_frame_probe.dart 只保留有限帧计时与弹窗耗时，不含训练内容。

AI 私教本轮仅完成迁移边界评估，没有替换业务。下一步可直接替换原 AI 标签页，先保留原生密钥、网络、照片及 Room 写入，通过 Rust 接管上下文整理和计划校验。

## 管理页启动与手势修复

取消顶层内容横向拖动和 Flutter 的右滑切库手势，顶层按钮直接切换；部位栏保持横向滚动，使用 tap-only 的 CrystalControl，最后一个部位可以拖动后选取。详情按钮也复用透明高光胶囊，保持稳定轮廓。

CatalogEnginePool 异步初始化并预执行 warmCatalog，真实宿主激活后才读数据；“我的动作”首屏不挂载不可见 Flutter。完整目录桥接对象组装移到后台。具体渲染边界、玻璃层级、手势和后续迁移规则见 [Flutter Liquid Glass 实现说明](../docs/flutter-liquid-glass.md)。前文“相邻页预布局预热”已被本方案替代。

## 详情稳定首帧与真实折射（更新）

上一版详情的透明渐变白边方案已替换：GlassDialogAction 使用完整 premium 采样折射，并在少量操作控件局部固定质量，使用圆角矩形胶囊而非默认椭圆。筛选栏仍保留 tap-only 轻量按钮。

CatalogEnginePool 从“读入引擎/着色器”升级为真正绘制目录、缩略图与折射按钮后才完成预热；临时视图不接收触摸，准备完成后移除并暂停引擎。详情先确定并解码一张背景、准备动图首帧，再通过 catalogOverlayReady 显示窗口；关闭用 catalogInlineReady 确认列表新视口，避免显示窗口搬移过程。此方案替代前文“背景在详情显示后异步补齐”的实现。详细规则与性能测量边界见 docs/flutter-liquid-glass.md。

## 退出重进与部位通条

普通退出管理页后，暂停并缓存一个已准备引擎；重新绑定新桥接及catalogSession后复用，重置新会话筛选状态。仅在弹窗未完成的强制退出或已有缓存占槽时销毁对应引擎，避免未完成路由和无界保留。此前“每次宿主销毁即销毁引擎”的做法已替换。

部位筛选使用GlassSegmentedControl.scrollable：与“我的动作”次级栏同为64dp连续胶囊轨道，横向滑动滚动、点按选择；说明文字移到下面。详情加入按下时提前取图，但跨窗口弹窗的结构性开销仍未消除；参见统一实现文档。

## 固定宿主与AI流式准备（当前实现）

详情现已在固定全屏透明FlutterView内覆盖显示，原列表由CatalogViewport定位。开关详情不再搬动view、创建原生详情窗口或等待截图；背板在空闲时准备，动画可用缩略图先呈现。原生菜单区域平时透明并透传，详情统一拦截与处理返回。此实现替代前文搬窗口/恢复视口方案，规范以docs/flutter-liquid-glass.md为准。

AI新增Rust SSE/事件归一、原生流式Call和事件契约、Flutter纯状态机、五语资源及离线测试。当前私教页面和实际一次性调用未切换，也未发真实服务请求。公开推理摘要与服务声明的公开推理字段分开，详见docs/ai-coach-streaming-migration.md。

## AI Rust请求接线（后续进度）

AI已从准备推进到实际请求：现有Compose私教页可使用Rust JNI解析的增量回答与停止生成；CSV和记忆独立消费完整结果。Flutter私教UI/通道仍待替换，不应称为整个私教页已Flutter化。支持手动关闭流式保持旧服务兼容；不自动重发失败POST。当前能力和测试边界以docs/ai-coach-streaming-migration.md为准。

## 历史玻璃界面（2026-11-09）

历史默认显示原图表，移除验证阶段的总统计和当前/备份选择；统一从现用Room读取，备份恢复只在设置处理。列表采用日期/体重分组、单边与完成标记、左右侧及原记录类型排版，使用懒列表solid卡片；列表与图表切换保留状态。详细规则见docs/flutter-liquid-glass.md，AI界面评估见docs/ai-flutter-ui-estimate.md。

## 主历史入口已替换（2026-11-09）

历史根标签直接使用预热的嵌入式Flutter及实时只读快照，不再启动独立Activity；主菜单移到完整标题下方并带系统触感。动作库一级菜单同步反馈。长弹窗/已选名称换行，动作选择器压缩操作区，图表轴宽按刻度实测。详见docs/flutter-history-host.md；旧独立历史预览描述已由本节替代。
