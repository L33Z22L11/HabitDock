# 开发说明

使用 JDK 17、Gradle Wrapper、Android SDK 35 / Build Tools 35.0.0，不依赖全局 Gradle，也不需要修改 shell 启动文件。

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
adb -s <emulator> shell appops set dev.habitdock GET_USAGE_STATS allow
adb -s <emulator> shell am instrument -w dev.habitdock.test/dev.habitdock.SmokeRunner

./gradlew assembleDebugAndroidTest -PtouchTests
adb -s <emulator> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <emulator> shell am instrument -w dev.habitdock.test/dev.habitdock.PickerTouchRunner

./gradlew assembleDebugAndroidTest -PtestRunner=dev.habitdock.StyleSettingsRunner
adb -s <emulator> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <emulator> shell appwidget grantbind --package dev.habitdock --user 0
adb -s <emulator> shell am instrument -w dev.habitdock.test/dev.habitdock.StyleSettingsRunner

./gradlew assembleDebugAndroidTest -PtestRunner=dev.habitdock.SettingsPageRunner
adb -s <emulator> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <emulator> shell am instrument -w dev.habitdock.test/dev.habitdock.SettingsPageRunner
```

SmokeRunner 的无组件任务检查需要先移除测试模拟器上的知时 Widget。普通 GitHub CI 编译 Android 测试包，执行纯 JVM 与 Python 检查；设备测试在模拟器上另外执行。

SettingsPageRunner 检查上限输入与保存、更新弹窗和外链目标，默认使用本地版本响应。需要额外验证真实 GitHub HTTPS 查询时，在 `am instrument` 后添加 `-e checkNetwork true`；不要让网络可用性成为离线回归检查的前提。

## 打包与发布

`python3 scripts/package-source.py` 按 Git 文件清单导出 `dist/HabitDock-source.zip`，不要求先提交；输出目录本身不会进入源码包。发布步骤见 [RELEASING.md](docs/RELEASING.md)。

保留隐私排除优先、离线学习和低频批处理的约束。Widget 普通点击必须直接使用目标应用的 Activity PendingIntent，不增加知时中转启动步骤。
