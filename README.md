# Lingua

以真实生活为语料的英式英语学习 Android 应用。把日常记录转成英文素材，通过对话、听读、跟读和间隔复习，逐步积累能用得上的表达。

[下载 Android 安装包](https://github.com/charlotteamian/Lingua/releases/latest) · [查看构建状态](https://github.com/charlotteamian/Lingua/actions/workflows/android-apk.yml)

## 功能

| 模块 | 可以做什么 |
| --- | --- |
| 今日 | 查看每日学习任务、学习日历和连续学习记录，按当天状态安排练习。 |
| 对话 | 与 Poppy 进行文字或语音交流，使用 Gemini Live 实时通话，收藏对话中的句子和词块。 |
| 语料 | 将生活随记转成英文素材，提取词块，进行英音朗读和录音跟读点评。 |
| 听读 | 导入 YouTube 视频或本地音频、播客，结合字幕听读、点词查询和收藏句子。 |
| 复习 | 集中管理生词、词块和句子，按间隔复习计划练习，遗忘内容可在当天重学。 |

内置离线词典，支持英式音标和点词查询；朗读可选系统语音或 Gemini 云端语音，并可调整语速。跟读练习会根据录音给出发音建议。

## 安装与更新

支持 Android 9 及以上版本。

1. 前往 [最新 Release](https://github.com/charlotteamian/Lingua/releases/latest)，在 **Assets** 中下载 `Lingua-build-编号.apk`。
2. 在手机上打开 APK，按系统提示完成安装。
3. 以后进入 **设置 → 应用更新**，点击「检查更新」，有新版时选择「下载并安装新版本」。

下载安装包和检查更新无需登录 GitHub。首次安装或更新时，Android 可能要求允许 Lingua「安装未知应用」。更新需要网络能够访问 GitHub 及其下载服务。

App 会在安装前校验下载文件、应用包名、版本号和签名。使用同包名、同签名的新版覆盖安装可保留学习记录；需要保留记录时，请勿先卸载应用。当前 Releases 提供开发构建版 APK。

## AI 配置与数据

在设置中选择 AI 服务商，并填写自己的 API Key。文字对话支持 OpenAI 兼容接口和 Anthropic Messages 接口；Gemini Live 通话、云端朗读、跟读点评及音频转写需要相应的 Gemini 服务支持，调用费用由所用服务商计收。

学习记录与设置存储在设备本地。使用 AI 功能时，相关文字或录音会发送给所配置的服务商；YouTube 播放、字幕获取和在线查词也需要联网。本地词典查询和已保存内容的复习可离线使用，系统朗读取决于设备是否安装相应语音包。

YouTube 字幕获取受视频字幕可用性、网络环境和平台限制影响；本地音频生成字幕需要 AI 转写。

## 开发

使用 Android Studio 打开仓库根目录，配置 JDK 17 和 Android SDK 35。

```bash
git clone https://github.com/charlotteamian/Lingua.git
cd Lingua
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

本地配置由 Android Studio 生成。请勿将 API Key、签名私钥或用户数据提交到仓库。

| 目录或文件 | 内容 |
| --- | --- |
| `app/src/main/java/com/lingua/english/ui/` | Compose 界面与交互 |
| `app/src/main/java/com/lingua/english/data/` | 本地存储、AI、词典、媒体与更新 |
| `app/src/main/java/com/lingua/english/data/ai/Prompts.kt` | AI 提示词 |
| `app/src/main/java/com/lingua/english/domain/` | 每日计划与间隔复习规则 |
| `app/src/main/assets/` | 离线词典等资源 |
| `app/build.gradle.kts` | Android 构建配置 |
| `.github/workflows/android-apk.yml` | 自动检查与 APK 发布 |

可通过 GitHub 网页编辑器或本地开发工具修改代码。自动化流程会执行单元测试、Android Lint 和 APK 构建；合并到 `main` 的变更通过检查后，会发布可供 App 检查更新的安装包。
