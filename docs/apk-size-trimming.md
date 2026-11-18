# 发布体积裁剪（6.4 开发版）

## 打包策略

`preparePackagedAssets` 只向构建生成目录复制运行资源，不改动源码存档：

- 排除 `catalog/videos/*.gif`，保留 1,324 个 WebP。旧 GIF URI 继续经 `CatalogMedia.bundledUri` 转换为 WebP，个人备份中的旧 URI 无需改写。
- 排除原版 `gymnota-catalog-v4.sqlite.bin`，打包精简的 v5。原版及其分步说明 JSON 仍保留在源码中。
- release 和 preview 均启用 R8 与资源裁剪，debug 保留便于开发的未混淆构建。

模型、HTTP DTO、计时状态的 JSON 字段名，以及 JNI、Flutter 自动插件注册的反射入口明确保留。Flutter 按名称读取的 Android 字符串也保留，避免资源裁剪破坏本地化。

## 动作库的重新生成

```powershell
python tools/compact_runtime_catalog.py app/src/main/assets/catalog/gymnota-catalog-v4.sqlite.bin app/src/main/assets/catalog/gymnota-catalog-v5.sqlite.bin
```

脚本只移除运行端未读取的 `instruction_steps_json` 列，整理 SQLite 后重新 gzip；逐表比较其余所有字段，并执行完整性及外键检查。保留全部 1,324 个动作、12 种语言和 15,888 条翻译。

动作库解压体积由 20,307,968 字节降为 9,568,256 字节。首次使用 v5 时清理同一免备份目录中的旧版本内置库，个人数据库不在该目录。

后续修改目录内容时，需要同步提升运行文件名版本，避免安装端继续使用已缓存旧文件。

## APK 验证

```powershell
python tools/verify_slim_apk.py <新APK> --baseline <裁剪前APK> --report <报告.json>
```

检查没有旧 GIF 或原版数据库，所有动画路径均有对应 WebP，逐字节比较动画，并逐表比较保留的数据库内容。

实际混淆包可以使用独立的 Android framework instrumentation 检查：

```powershell
./gradlew.bat :app:assembleDebugAndroidTest -PpackagingInspector=true
adb -e install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# 先在目标应用中打开动作页，使新版内置库完成安装。
adb -e shell am instrument -w com.example.myfit.test/com.example.myfit.flutter.PackagingInstrumentation
```

检查器不依赖目标应用的未混淆类名或 AndroidX 测试库 ABI；它读取实际安装的发布包资源，抽样解码播放 WebP、检查新版库及旧副本清理，并通过 StorageStatsManager 报告应用和数据的安装占用。普通 JUnit 回归测试仍针对 debug 构建使用。

R8 的 `mapping.txt` 应与每个发布 APK 一起归档，用于还原混淆堆栈；映射文件不打入 APK。

## 本轮实测（2026-11-18）

| 项目 | 裁剪前 | 裁剪后 |
|---|---:|---:|
| ARM64 APK | 259.19 MiB | 124.14 MiB |
| APK 中 DEX 压缩数据 | 12.75 MiB | 1.77 MiB |
| 安装后独立动作 SQLite | 19.37 MiB | 9.13 MiB |
| 同一 x86_64 模拟器的应用安装文件（StorageStatsManager） | 307.82 MiB | 129.39 MiB |
| 同一模拟器应用数据（包含缓存） | 20.26 MiB | 10.16 MiB |

模拟器的安装文件与应用数据合计由 328.08 MiB 降至 139.55 MiB；这是同机对照，不代表每部 ARM64 手机上的精确安装占用。没有清除个人数据来实现该结果。APK 减少约 52.1%。

实际混淆包验证了默认五页入口、个人动作新增/编辑/删除、语言切换、后台计时完成并立即刷新历史、备份导出/恢复自动重启。独立包检查器确认 1,324 个 WebP、抽样动画播放、15,888 条翻译、新版库体积及旧库清理。包内动画逐字节一致，运行库保留字段逐表一致。

本轮保留原动画分辨率、离线图库和语言内容，源码中的旧版 GIF、原版动作库与 Kotlin 回退代码继续存档。没有通过重新编码动画或删除用户记录取得体积减少。
