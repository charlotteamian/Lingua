# Lingua

个人英式英语学习 Android App：生活记录转英文语料、Poppy 对话和 Gemini Live 通话、英音朗读、跟读点评、YouTube/播客听读、词块收藏与间隔复习。

公开仓库：[charlotteamian/Lingua](https://github.com/charlotteamian/Lingua)。源码、提交历史、构建日志和 [Releases 安装包](https://github.com/charlotteamian/Lingua/releases/latest)均可公开查看；下载安装包和在 App 内检查更新无需登录 GitHub，也无需填写 Token。AI API Key 在手机 App 的设置里填写。

## 直接在 GitHub 上修改、更新

1. 打开仓库，在 **Code** 里找到要修改的文件，点铅笔 **Edit this file**。
2. 修改后点 **Commit changes**，提交到 `main`。需要同时修改多个文件时，可在仓库页面按 `.` 打开 `github.dev` 编辑器，再提交并推送。
3. 打开 [Actions → Android APK](https://github.com/charlotteamian/Lingua/actions/workflows/android-apk.yml)，等待本次运行出现绿色成功标记。成功后会自动发布到 [Releases](https://github.com/charlotteamian/Lingua/releases/latest)，供 App 检查更新。
4. 打开最新 Release，在 **Assets** 下载 `Lingua-build-编号.apk`。另附 `SHA256SUMS`、签名信息和构建信息。
5. 把 APK 发到手机，安装到现有 Lingua 调试版上。同包名、同签名且版本号不低于已安装版本时，Android 支持覆盖安装并保留应用数据；不要先卸载。

小修改可以直接提交 `main`。复杂修改可先建分支、提交 Pull Request，检查通过后合并到 `main`，系统再生成可安装 APK。

不改代码也能重新打包：进入上述工作流，点 **Run workflow**，选择 `main` 后运行。Releases 的 APK 可直接下载；Actions 内的 **Artifacts** 另存一份构建产物，需登录 GitHub 下载，保留 30 天。

GitHub 文件编辑和下载操作参考：[编辑文件](https://docs.github.com/en/repositories/working-with-files/managing-files/editing-files)、[下载构建产物](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts)。

## 在 App 内更新

第一次需要从 [最新 Release](https://github.com/charlotteamian/Lingua/releases/latest) 下载并安装新版 APK，覆盖原有 Lingua；以后进入 **设置 → 应用更新**，无需登录 GitHub 或填写 Token：

1. 点「检查更新」。如果 GitHub 最新版本的版本号高于当前版本，会出现「下载并安装新版本」。
2. 点「下载并安装新版本」。下载完成后，App 检查完整文件的 SHA-256、应用包名、版本号和签名，并打开 Android 安装确认页。
3. 在 Android 确认安装。第一次可能需要允许 Lingua「安装未知应用」。安装会覆盖同签名的现有调试版，学习数据保留。

更新需要手机网络能访问 GitHub 和其安装包下载服务。

## 自动构建与签名

工作流 `.github/workflows/android-apk.yml` 在 `main` 更新、Pull Request 和手动运行时执行单元测试、Android Lint 和 Debug APK 编译。只有 `main` 的非 Pull Request 运行会使用固定签名并提供可下载 APK。

检查通过后，独立发布任务将完整 APK、`build-info.json`、校验和与签名信息一起发布到 `build-运行编号` 的公开 Release，再设为最新版本。App 读取结构化更新信息；较早构建的重跑不会把最新版本回退。创建 Release 的写权限仅授予发布任务。

- 安装包名：`com.lingua.english.debug`，与本项目原有本机调试版一致。
- 固定签名：沿用建立仓库时本机的 `~/.android/debug.keystore`，存放在 GitHub 加密 Secret `LINGUA_DEBUG_KEYSTORE_BASE64`，不会提交到源码。
- 签名核验：工作流按 `ci/debug-signing-cert.sha256` 检查 APK 的证书指纹，指纹不一致则停止上传。
- 云端版本号：`1000 + 本工作流运行编号`，版本名带 `build.编号`；重新运行同一次构建不会改变编号。保留这个工作流的运行历史，避免编号重置后生成旧版本号。
- Pull Request：执行检查并提供报告，不读取固定签名，也不提供更新包。

不要删除或重新生成签名 Secret，否则后续 APK 可能无法覆盖已安装的版本。若更换电脑，且希望本地构建仍能覆盖安装，应安全迁移原来的调试签名文件。当前产物是个人使用的调试版。

## 本地开发

Android Studio 打开仓库根目录，使用 JDK 17 和 Android SDK 35。`local.properties` 由 Android Studio 在本机生成，不进仓库。

```bash
git clone https://github.com/charlotteamian/Lingua.git
cd Lingua
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

这台 Mac 可使用 Android Studio 自带的 JDK：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

本地默认仍使用源码里的版本号。如果手机已安装云端构建，给本地构建传入最新的工作流编号，使版本号匹配：`./gradlew :app:assembleDebug -PlinguaBuildNumber=编号`。

GitHub 和本机交替修改时，先同步远端，再改代码：

```bash
git pull --ff-only
# 修改文件后，检查 diff，明确选择本次文件。
git add <本次修改的文件>
git commit -m "说明这次修改"
git push
```

有未提交改动或分叉时，先处理改动/冲突；不要强制推送覆盖 GitHub 上的更新。

## 文件位置

| 想调整什么 | 文件/目录 |
| --- | --- |
| AI 提示词 | `app/src/main/java/com/lingua/english/data/ai/Prompts.kt` |
| 今日、对话、语料、复习和设置界面 | `app/src/main/java/com/lingua/english/ui/screens/` |
| 每日计划与 SRS 规则 | `app/src/main/java/com/lingua/english/domain/` |
| 模型与学习设置 | `app/src/main/java/com/lingua/english/data/prefs/SettingsPreferences.kt` |
| 应用版本与 Android 构建 | `app/build.gradle.kts` |
| 自动检查、生成 APK | `.github/workflows/android-apk.yml` |
| 项目约定与历史协作背景 | `AGENTS.md`、`CLAUDE.md` |

Gradle Wrapper、依赖版本清单、Room schema、离线词典和字体都已纳入源码。`.gitignore` 排除本机配置、API 密钥文件、签名私钥、构建产物与个人运行数据。手机上的聊天、录音、学习记录和已填写的 API Key 保留在设备内；上传源码不会备份这些手机数据。
