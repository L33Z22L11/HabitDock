# 知时 HabitDock

根据时间、星期和近期使用习惯推荐应用的 Android 桌面小组件。保留现有桌面，本机学习，无广告或账号；仅手动检查更新时连接 GitHub。

- 推荐页与 Widget 共用推荐结果，多个组件按添加顺序接续展示、不重复；圆角和图标填色由全局「图标样式」统一管理，自适应颜色延续可识别的边缘渐变，复杂图标回退系统深浅底色，也可自选。
- 设置中直接点 4×2 或 2×2 添加桌面组件；每个组件可在设置中命名，独立设置内部行列数、相对图标大小、更多入口与时间显示，跟随系统深浅色。
- 图标样式与组件布局调整立即生效；右上角撤销可恢复本次进入页面时的设置或出厂默认值。
- 「不推荐的应用」支持名称／包名搜索、选中置顶；排除即清除相关本地记录并停止学习。
- 固定应用数量不限，排除名单始终优先；组件只展示当前布局放得下的应用。
- 智能推荐上限默认 20 个，可在设置中调整为 1–100 个，固定应用另计；修改上限保留推荐排序与更新时间。
- 长按推荐项打开小气泡：固定、不推荐、应用信息、分享 APK；标题行包名右侧的小图标可复制包名。
- 默认 30 分钟批量更新，可调刷新间隔，无常驻服务或轮询。Widget 普通图标直接打开目标应用。
- 独立开源仓库入口；「关于知时」描述展示学习天数与记录数，右侧按钮手动检查正式版更新；发现新版后前往浏览器中的 Release 页面查看与下载。

Android 8.0+；Java 17、原生 Android Views。当前开发版本见 [version.properties](version.properties)。

## 安装和使用

从仓库 Releases 下载 APK；CI 的 `HabitDock-debug` artifact 用于开发验证。
打开后先设置不推荐的应用，再授予系统「使用情况访问权限」，最后添加桌面组件。

完整操作说明、推荐算法、隐私与耗电说明见 [使用说明](docs/USAGE.md)。

## 本地开发

安装 JDK 17、Android SDK 35 与 Build Tools 35.0.0。配置 `ANDROID_HOME`，或在本机的 `local.properties` 写入 `sdk.dir=/你的/Android/SDK`；该文件不入库。

```sh
./gradlew spotlessApply
./gradlew spotlessCheck assembleDebug lintDebug
sh scripts/test-predictor.sh
python3 -m unittest discover -s tests -p 'test_*.py'
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。也可用 Android Studio 打开根目录。

Java 格式化使用 [Spotless + Eclipse JDT](https://github.com/diffplug/spotless/tree/main/plugin-gradle#eclipse-jdt)：四空格、120 列、展开方法体和语句，可在 [格式配置](config/eclipse-java.xml) 调整。不重命名、不重排成员或强制替换通配符 import。

## GitHub 自动构建与发布

[Android workflow](.github/workflows/android.yml) 对分支／PR 做格式、编译、Lint 与测试检查，并上传 debug APK。
推送与 `version.properties` 对应的 `v*` Tag（例如 `v0.4.7`）后，自动构建签名 APK、校验、生成 SHA-256，并发布 GitHub Release。预发布版本带 `-beta.1` 等后缀会标记为 prerelease。

首次需在仓库配置四个 Android 签名 Secrets，详见 [发布说明](docs/RELEASING.md)。未配置签名时明确失败，不发布不可安装或临时签名的 APK。

## 目录

| 路径 | 内容 |
| --- | --- |
| `app/src/main` | 应用代码与资源 |
| `app/src/androidTest` | 模拟器集成与实际触摸测试 |
| `tests` | JVM 场景与发布工具测试 |
| `config` | 可调整的格式化规则 |
| `scripts` | 测试、源码打包、签名及 Release 工具 |
| `.github` | Actions 与依赖更新配置 |
| `docs` | 使用、验证、发布文档 |

本机 SDK、缓存、签名、截图和打包产物均被 `.gitignore` 排除，已有本地产物仍保留。
更多开发说明见 [CONTRIBUTING.md](CONTRIBUTING.md)，验证记录见 [docs/VERIFICATION.md](docs/VERIFICATION.md)。
