# 开发说明

使用 JDK 17、Gradle Wrapper、Android SDK 35 / Build Tools 35.0.0，不依赖全局 Gradle，也不需要修改 shell 启动文件。

Debug 包名为 `dev.habitdock.debug`，显示名为「知时 HabitDock Debug」，可与 `dev.habitdock` 正式版共存。两者的数据、权限和桌面组件分别管理。

## 格式

先运行 `./gradlew spotlessApply`，提交前运行 `./gradlew spotlessCheck`。Eclipse JDT 配置在 `config/eclipse-java.xml`，可以由 Eclipse／支持 Eclipse 配置的编辑器导入；普通编辑器遵守 `.editorconfig` 即可。格式化不自动整理 import 或排序成员。

## 验证

```sh
./gradlew spotlessCheck assembleDebug assembleDebugAndroidTest lintDebug
sh scripts/test-predictor.sh
python3 -m unittest discover -s tests -p 'test_*.py'
```

下面的 Android 测试会清空应用测试数据，只在可重置的模拟器运行；不要在个人手机上运行。

```sh
adb -s <emulator> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <emulator> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <emulator> shell appops set dev.habitdock.debug GET_USAGE_STATS allow
adb -s <emulator> shell am instrument -w dev.habitdock.debug.test/dev.habitdock.SmokeRunner

./gradlew assembleDebugAndroidTest -PtouchTests
adb -s <emulator> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <emulator> shell am instrument --no-hidden-api-checks -w dev.habitdock.debug.test/dev.habitdock.PickerTouchRunner

./gradlew assembleDebugAndroidTest -PtestRunner=dev.habitdock.StyleSettingsRunner
adb -s <emulator> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <emulator> shell appwidget grantbind --package dev.habitdock.debug --user 0
adb -s <emulator> shell am instrument -w dev.habitdock.debug.test/dev.habitdock.StyleSettingsRunner

./gradlew assembleDebugAndroidTest -PtestRunner=dev.habitdock.SettingsPageRunner
adb -s <emulator> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <emulator> shell am instrument -w dev.habitdock.debug.test/dev.habitdock.SettingsPageRunner
```

WidgetSizingRunner 可通过 `-PtestRunner=dev.habitdock.WidgetSizingRunner` 构建测试包，验证图标在实际容器内的居中与相对缩放。WidgetDiagnosticRunner 默认只读输出桌面上报尺寸和是否参与列表；显式传入 `hostHints`（`组件ID=宿主包名`，逗号分隔）可补录已由 ADB 独立核实的旧组件归属，只修改所属知时实例的本地宿主提示，不改变系统绑定。

WidgetCompatibilityRunner 只在模拟器运行，覆盖图标包映射、资源缺失回退、宿主绑定过滤、隐藏恢复与空列表更多入口。Android 测试 APK 内置图标包资源夹具，不安装到个人手机作为实际图标包。

SmokeRunner 的无组件任务检查需要先移除测试模拟器上的知时 Widget。普通 GitHub CI 编译 Android 测试包，执行纯 JVM 与 Python 检查；设备测试在模拟器上另外执行。

SettingsPageRunner 检查上限输入与保存、更新弹窗和外链目标，默认使用本地版本响应。需要额外验证真实 GitHub HTTPS 查询时，在 `am instrument` 后添加 `-e checkNetwork true`；不要让网络可用性成为离线回归检查的前提。

## 打包与发布

`python3 scripts/package-source.py` 按 Git 文件清单导出 `dist/HabitDock-source.zip`，不要求先提交；输出目录本身不会进入源码包。发布步骤见 [RELEASING.md](docs/RELEASING.md)。

保留隐藏应用展示过滤、离线学习和低频批处理的约束。隐藏只影响展示，不删除记录或停止采集。Widget 普通点击必须直接使用目标应用的 Activity PendingIntent，不增加知时中转启动步骤。

PickerTouchRunner 与 WallpaperBackRunner 使用 Android 测试专用触摸注入接口，避免等待窗口动画造成事件时间戳过期；仅在模拟器用 `am instrument --no-hidden-api-checks -w` 运行。应用本身不使用隐藏 API。

BackgroundImageRunner 仅在可清空的模拟器运行，检查图片导入、原图保留、图片来源隔离、模糊持久化、图片页面背景、桌面预览区域的原生壁纸，以及混合背景下的返回回调策略。通过 `-PtestRunner=dev.habitdock.BackgroundImageRunner` 构建；不能在用户手机运行。

FormAppearanceRunner 仅在可重置模拟器运行，覆盖所有页面的原生壁纸遮罩、表单重置与窄屏布局，并在悬浮窗保持打开时比较模糊从 0 调回后的实际截图。使用 `-PtestRunner=dev.habitdock.FormAppearanceRunner` 构建后，执行 `adb -s <emulator> shell am instrument -w dev.habitdock.debug.test/dev.habitdock.FormAppearanceRunner`。
