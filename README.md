# 知时 HabitDock

知时是 Android 应用启动与桌面小组件：根据时间、星期和近期使用习惯，在本机推荐应用。主页分为「推荐」「全部」「设置」三栏；既可在现有桌面上使用小组件，也可自愿将知时设为系统默认桌面。

- 「推荐」展示智能推荐与置顶应用；「全部」提供应用名称／包名搜索、系统应用筛选和字母索引。两页各有显示抽屉，可独立调整平铺／宫格、图标及文字大小。
- 「设置」管理排除与置顶、推荐数量、更新频率、图标和组件样式、权限与数据。主界面样式可选择默认打开「推荐」或「设置」，并开关液态材质。
- 支持 4×2、2×2 桌面小组件；多个组件按添加顺序接续展示推荐。正式版保留旧组件入口，覆盖更新的条件见[发布说明](docs/RELEASING.md)。
- 主界面壁纸可关闭，或选择原生、兼容读取、自定义图片三种来源。兼容读取需要系统授权；模糊和材质效果受系统及设备能力限制。
- 推荐与学习完全在本机进行，无账号、广告或分析 SDK。仅手动检查更新时请求 GitHub 正式版信息。

支持 Android 8.0+；使用 Java 17、Android Views 和 Material Components。当前源码版本见 [version.properties](version.properties)。

## 安装和使用

从仓库 Releases 下载正式版 APK。打开知时，在「设置」中按需排除应用并授予「使用情况访问」权限；可在「组件样式」添加小组件，也可选择「设为默认桌面」。两种用法均不要求采用另一种。

时间预览和立即刷新位于「推荐」的显示抽屉，具体操作、权限与限制见[使用说明](docs/USAGE.md)。CI 的 `HabitDock-debug` artifact 用于开发验证；debug 包与正式包的包名和签名不同，不能当作正式包的覆盖更新。

## 本地开发

安装 JDK 17、Android SDK 35 与 Build Tools 35.0.0。配置 `ANDROID_HOME`，或在本机的 `local.properties` 写入 `sdk.dir=/你的/Android/SDK`；该文件不入库。

```sh
./gradlew spotlessApply
./gradlew spotlessCheck assembleDebug lintDebug
sh scripts/test-predictor.sh
python3 -m unittest discover -s tests -p 'test_*.py'
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`；也可用 Android Studio 打开根目录。Java 格式化使用 [Spotless + Eclipse JDT](https://github.com/diffplug/spotless/tree/main/plugin-gradle#eclipse-jdt)，配置见 [config/eclipse-java.xml](config/eclipse-java.xml)。

## 自动构建与发布

[Android workflow](.github/workflows/android.yml) 对分支和 PR 执行格式、构建、Lint 与测试检查，并上传 debug APK。推送与 `version.properties` 一致的 `v*` Tag 后，工作流使用仓库配置的签名构建和校验正式 APK，生成 SHA-256 并发布 GitHub Release；带预发布后缀的版本标记为 prerelease。未配置签名时 release 构建会失败。

签名配置、版本及覆盖安装约束见[发布说明](docs/RELEASING.md)；更多开发说明见 [CONTRIBUTING.md](CONTRIBUTING.md)。
