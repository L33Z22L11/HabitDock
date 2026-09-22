# GitHub 发布

## 一次性设置

将本项目推送到你的 GitHub 仓库，保证 `.github/workflows/android.yml` 已合入默认分支。
在仓库 Settings → Secrets and variables → Actions 中添加：

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 发布 keystore 文件的 Base64，可包含换行 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 签名 key 的 alias |
| `ANDROID_KEY_PASSWORD` | 该 key 的密码 |

在本机创建发布密钥的示例（密码由 keytool 交互输入，不写进命令历史）：

```sh
keytool -genkeypair -keystore habitdock-release.keystore -alias habitdock \
  -keyalg RSA -keysize 3072 -validity 10000
```

妥善保留同一份发布密钥，以后更新需要相同签名。keystore、密码、Base64 都不要提交到 Git。可用 `base64 < habitdock-release.keystore` 生成 Secret 值；不要将输出粘贴到 issue、日志或文档。

当前通过本机 ADB 安装的是个人 debug 签名版本。新的发布密钥签名 APK 无法直接覆盖它；这不影响继续使用本机 debug 包更新。要保持公开发布版本之间可覆盖升级，请固定发布签名，不要每次构建重新生成密钥。

实现依据：[Android 应用签名](https://developer.android.com/studio/publish/app-signing)、[GitHub Actions 权限](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#permissions)、[gh release create](https://cli.github.com/manual/gh_release_create)。

## 每次发布

1. 修改 `version.properties` 的 `versionName`，并递增 `versionCode`；两者是所有构建唯一的版本来源。
2. 提交并推送到默认分支，等待检查通过。
3. 创建并推送对应的 Tag：

```sh
git tag -a v0.4.6 -m 'HabitDock 0.4.5'
git push origin v0.4.6
```

工作流只在推送 `v*` Tag 时发布，Tag 必须与该提交中的版本号一致。支持 `v0.5.0-beta.1` 一类预发布版本，需要把 `versionName` 写成 `0.5.0-beta.1`，同时递增 `versionCode`。

流程：格式／测试／Lint → 签名 release 构建 → APK 签名与版本校验 → 创建草稿 Release → 上传 APK 和 `SHA256SUMS` → 发布。GitHub 自动附带对应提交的源码归档。

上传失败时保留草稿，可重跑继续上传；已经发布的 Release 不会被流水线覆盖，请创建新版本 Tag。只有发布任务申请 `contents: write`；分支和 PR 检查没有写权限或签名 Secrets。

## 本地验证发布构建

以环境变量设置 `ANDROID_KEYSTORE_PATH`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 后运行：

```sh
./gradlew assembleRelease lintRelease
python3 scripts/release.py validate v0.4.6
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify app/build/outputs/apk/release/app-release.apk
python3 scripts/release.py package v0.4.6
```

输出位于 `dist/HabitDock-0.4.5.apk` 与 `dist/SHA256SUMS`。未设置完整签名配置的 release 构建会失败；debug 构建仍可直接运行。不要把发布密钥用于不可信 PR 的构建。
