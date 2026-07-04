# CLAUDE.md — Lingua 英语学习 App

> 此文件是 Claude 的工作记忆。每次新会话进入这个目录时会自动加载。

## 项目是什么

charlotte 的**个人英语学习 Android app**,代号 **Lingua** (拉丁语"语言")。
- 包名: `com.lingua.english` (debug 自动加 `.debug` 后缀)
- 主题色: 深色背景 `#06080f` (和 Vita / Fortuna 同款),主色皇家蓝 `#8FA8FF`
- 方法论: **OIO (Output-Input-Output)**,来自 KevinFeng 馮凱文 (视频 youtube.com/watch?v=7qcIHARZ7f8, 产品 oiosync.com)
  - 第一轮 Output: 把自己的生活记录变成**专属英文语料** (不是课本例句)
  - Input: 在自己的真实语境里反复遇见这些**词块 (chunks)** 内化,弱化"翻译脑"
  - 第二轮 Output: 用内化的英文迭代输出 (和 AI 对话聊真实生活)
  - 执行系统: 好天/正常/保底三档日常,保证可持续
- 学习目标: **RP 英音 (Received Pronunciation / British English)**,贯穿三处: AI 提示词强制英式用词拼写、词典显示英式音标、TTS 用 en-GB
- 纯本地数据,AI 走 BYOK API 直连 (复用 Vita 的双协议方案)
- 以后成熟了再做 iOS

## 当前进度

- ✅ **2026-07-04 v1 全量脚手架** (assembleDebug 编译通过,未真机验证):
  - **四 tab 信息架构** (按用户动作划分,沿用 Vita 五轮教训): 今日(看今天) / 对话(迭代输出) / 语料(生活→素材) / 复习(词块内化),设置从今日页齿轮进 (`settings` 路由)
  - **今日页**: streak 卡 + 三档任务卡 (档位由当天计数自动判定,不用手动打卡; 规则在 `domain/DailyPlan.kt`) + 三个快捷入口; 未配 API 时顶部引导卡
  - **对话页**: 和「Poppy」(伦敦腔陪练 persona) 英文聊天;提示词规则: 只说英式英语、回复短 (2-4 句+一个追问)、✎ 开头纠正行、▸ 开头词块行 (UI 解析后可一键 ＋ 收进复习)、用户写中文时教 TA 怎么用英文说。「新话题」按钮 = 下一条消息起清空模型上下文 (历史保留展示)。上下文取最后一个 break 后 ≤20 条,相邻同角色消息合并 (Anthropic 角色交替要求)
  - **语料页**: 随手记 (中文/混写) → AI 转写成「你自己会说的英文」+ 3-8 个词块 (JSON,`Prompts.materialConversion`) → 预览勾选 → 保存;详情页整段英音朗读 (0.7/0.9/1.0/1.1× 变速)、词块点读、可删除
  - **复习页**: 输出导向 SRS — 正面中文意思,自己先说英文再看答案,忘了/模糊/记得三档自评 (简化 SM-2,`domain/Srs.kt`);到期队列响应式;下方全部词块列表可朗读/删除
  - **点词查词 (全 app)**: `TappableText` 给每个英文单词挂 `LinkAnnotation.Clickable` (无下划线,保持纯文本观感),点击弹 `WordLookupSheet` 底部卡: 英式音标、英音朗读、考纲标签、中文释义、英文释义、词形变化、加入生词本 (进同一套复习)。入口通过 `LocalWordLookup` CompositionLocal 全局注入,控制器在 MainActivity 层唯一实例
  - **离线词典**: ECDICT (MIT) 过滤成 4.9 万核心词 (考纲标签∪Collins∪Oxford∪词频前4万) + 4 万 BNC 词形还原映射,11.8MB sqlite 打进 assets,首启拷到 filesDir 只读打开 (不走 Room,`DictDatabaseHelper`)。查词链: 精确 → lemma 表 → 后缀剥离 → 在线兜底 (dictionaryapi.dev,进程内缓存)。重新生成词典: `python3 tools/build_dict.py <ecdict.csv> <lemma.en.txt> app/src/main/assets/dict/ecdict.db` (源数据在 GitHub skywind3000/ECDICT,不进本仓库; 换数据时把 `DictDatabaseHelper.ASSET_VERSION` +1)
  - **TTS**: `TtsProvider` 接口 + `SystemTtsProvider` (系统 TTS 锁 en-GB,挑非联网 voice;缺英音语音包时降级默认英语并在设置页提示)。**接口是留给将来 API TTS 的,别绕过它直接 new TextToSpeech**
  - **AI**: `ChatApiClient` 双协议 (OpenAI Chat Completions + Anthropic Messages),9 预设,BYOK,移植自 Vita 的 `DirectApiInsightProvider` 改多轮+system;设置页测试连接。OpenAI 路径刻意不发 temperature/max_tokens (兼容性)
  - **执行系统计数**: `daily_log` 表四个计数器 (复习/听/聊/记),repo 层动作时自增;streak = 保底 (任一动作) 连续天数
- ✅ **2026-07-04 语音输入** (charlotte: 先加语音输入):
  - `data/speech/SpeechInputController.kt`: 系统 `SpeechRecognizer` 封装 (不引第三方 SDK), StateFlow 状态机 Idle/Listening/Error, 错误码转人话; 设备缺识别服务 (无 Google app 的 ROM) 给友好提示不崩。Manifest 加 `RECORD_AUDIO` + `RecognitionService` 包可见性。
  - `ui/components/SpeechInput.kt`: `rememberSpeechInput` (跟组合生命周期销毁) + `MicButton` (内置运行时权限申请) + `appendDictation` (CJK 之间不加空格)。
  - **对话页说英文 (en-GB)**: 麦克风在输入框左侧; **随手记说中文 (zh-CN)**: 麦克风在标题栏右侧。听写实时进输入框 (partial), 从按下时的 `dictationBase` 往后拼, 已手动编辑的部分不被覆盖; **不自动发送**, 说完可改再发。引擎停顿自动结束一段, 继续说再按一次。
- ✅ **2026-07-04 真机首轮反馈四连修 (charlotte: 语音生硬/页面乱/识别报错/要通话模式)**:
  - **Gemini 云端语音**: `GeminiTtsProvider` (generateContent + AUDIO 模态, PCM 16bit 24kHz → AudioTrack 播放, 支持变速 PlaybackParams)。英式口音靠 STYLE_PREFIX 提示词控制; 走「AI 对话服务」的 Gemini Key (`prefs.aiApiKey`) 按量计费; **没 Key/请求失败静默退回系统 TTS**; 同句音频进程内 LRU 缓存 (24 条) 防复习重复扣费。`RoutingTtsProvider` 按 `prefs.ttsEngine` (system/gemini) 路由, 全 app 调用方零感知; AppContainer 新增 `appScope`。设置页「英音发音」卡: 引擎双 chip + 4 个精选声音 (Kore/Aoede/Leda/Puck) + 语音模型输入框 (默认 `gemini-2.5-flash-preview-tts`, 出新版直接改)。**注意: 语音模型走的是 v1beta/models/{model}:generateContent 原生端点, 不是 OpenAI 兼容层; 若换非 Gemini 对话服务商, 云端语音会因 Key 不对退回系统音**。
  - **语音识别 code 5 (ERROR_CLIENT) 修复**: 每次 start 前销毁重建 SpeechRecognizer 实例 (部分厂商引擎出错一次后旧实例卡死), onError 也销毁; 授权回调后延迟 400ms 再启动 (权限弹窗还占着麦克风时立刻启动就是 code 5 的典型来源)。
  - **自动朗读 + 通话模式**: 设置页新增「对话」卡两个开关——`autoReadReplies` (回复到达即朗读, ChatViewModel.send onSuccess 里读) / `hideChatText` (Poppy 气泡只显示「语音回复 · 点按显示文字」+ 重听按钮, 点文字才展开; **仅自动朗读开着时生效**, ChatViewModel 里 combine 两个 pref)。
  - **界面整理**: 今日页任务行/快捷入口全部 maxLines=1 + ellipsis + 计数 softWrap=false (不再折行错乱), 任务文案缩短 (在 DailyPlan.kt); 复习页词块行卡片化 (英文 titleMedium 一行、中文释义 muted 一行、到期信息小字, 不再挤成一团); 对话页副标题单行、空状态进 LinguaCard、输入框占位语缩短。
- ✅ **2026-07-04 二轮真机反馈 (Gemini 语音成功后三连修)**:
  - **云端语音失败可诊断**: `GeminiTtsProvider.lastError` (StateFlow) 记录最近一次合成失败原因 (429 限额/400 等, 从错误体抠 message), 成功即清空; 设置页 Gemini 引擎下红字展示「上次云端合成失败: …」。charlotte 报告语料页退系统音而对话页正常——最大嫌疑是**免费档 TTS 每日限额小, 聊天自动朗读先烧完额度**, 之前失败是静默的无从确认, 现在能看到了。同时修: 长文本音频分多个 inlineData part 时全部拼接 (之前只取第一段)、采样率从 mimeType 的 `rate=` 解析 (之前写死 24k, 若模型换采样率会变速变调)。AppContainer 的 `geminiTts` 改为公开具体类型供设置页读 lastError。
  - **语速慢根因**: STYLE_PREFIX 里写了 "at a relaxed conversational pace"——TTS 模型真会照做放慢。改成 "lively intonation, brisk natural pace"。**经验: 给 TTS 的风格词会被忠实执行, relaxed/slow/calm 这类词慎用。**
  - **麦克风连续报错兜底**: `SpeechInputController.consecutiveFailures` 统计 CLIENT/BUSY 连续失败, ≥2 次后 `shouldUseDialog=true`, MicButton 改 launch `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` 系统听写弹窗 (厂商自带识别 UI, 兼容性最好), 结果回填 `controller.onFinal`; Manifest queries 加 RECOGNIZE_SPEECH 可见性。静默识别成功会清零计数回到无弹窗路径。
- ✅ **2026-07-04 跟读练习 (charlotte: AI 听我朗读并标记发音问题) + 语料页两处显示修复**:
  - **语料详情显示修**: 语速档 (0.7/0.9/1.0/1.1×) 之前和播放/停止按钮同一行被挤出屏幕 (Row 不换行) 只见 0.7/0.9, 改为独占一行 4 个等宽 chip; 词块列表套用复习页同款卡片样式 (英文/中文/note 分行, 尾部朗读钮)。
  - **跟读练习** (`practice/{materialId}` 路由, 语料详情「跟读练习」卡进入):
    - `data/practice/PronunciationCoach.kt`: MediaRecorder 录 AAC-ADTS 16kHz 到 cacheDir → **音频 inlineData 直发 Gemini generateContent** (用对话模型, 默认 gemini-3.5-flash 的原生音频理解) + 带编号单词表提示词 → 返回 JSON `{overall, words:[{i,w,issue,tip}]}` (RP 标准、明确要求不吹毛求疵); 下标消毒 (编号对不上按词文本找回, 找不到丢弃)。**仅支持 Gemini 预设** (要音频输入), 其它服务商抛可读错误。
    - `PronunciationScreen`: 原文中问题词红色下划线+浅红底, **点问题词 = 听 AI 读该词**, 其余词照常点词查词典; 问题词卡片列出 issue/tip + [听 AI 读] [听我的录音] (MediaPlayer 回放本地录音); 大按钮 开始朗读→读完交给 AI 点评→再读一遍; **每次评估整体替换标记, 读对了标记自然消失**。空标记时绿卡「这一遍没有需要标记的发音 ✓」。
    - 已知简化: 「听我的录音」回放整段而非单词片段 (逐词切片需时间戳对齐, 待模型能可靠返回再做); 标记不落库, 退出页面即清。
- ⚠️ **待真机验证**: 跟读全链路 (录音→Gemini 点评→标记→复读消标; 免费档下音频请求是否受限)、云端合成失败原因显示、新语速/语调、麦克风弹窗兜底——首启词典拷贝、TTS 英音可用性、点词弹卡、AI 对话/转语料 (要先在设置填 Key)、SRS 队列流转、语音输入 (依赖设备有语音识别服务, 通常来自 Google app; 国产 ROM 无 GMS 时会提示不可用)。charlotte Android Studio 打开 → Sync → Run

## 技术栈

- Kotlin 2.0.21 + Compose (BOM 2024.12.01) + Material 3,JVM 17
- Room 2.6.1 (KSP) `lingua.db` v1: chat_message / material_entry / chunk / daily_log
- DataStore Preferences (API 配置、语速、学习偏好)
- OkHttp 4.12 + kotlinx.serialization
- AGP 8.7.3 / Gradle 8.9,minSdk 28,targetSdk 35
- 手摇 DI: `AppContainer` + `LinguaViewModelFactory` (不引 Hilt)

## 常用命令

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

## 工作约定 (继承 Vita)

- 永远深色 `#06080f`;不引 Hilt;新代码函数式 + 不可变 data class
- 数据库加字段必须升 version + 写 Migration (现在 v1 还没这个问题)
- charlotte 的 UX 硬偏好: **零开关零嵌套**,内容全部铺开,靠编辑内容控制长度;文案讲意义不堆数字;一切可编辑;功能归对页面
- Kotlin 字符串模板 `$var` 后面直接跟汉字会把汉字并进标识符,必须写 `${var}汉字`
- 提示词都在 `data/ai/Prompts.kt`,只此一份 (没有 Vita 的 Mac/app 双份同步问题)

## 已知妥协 (v1 有意为之,别当 bug 修)

- 聊天不是流式输出,等完整回复 (转圈提示);要更跟手再上 SSE
- 「听一条语料」在点播放时计数,不是真听完 (TTS 完成回调没接)
- 发送失败时用户消息保留且聊天计数已加;重发即可 (上下文层已做同角色合并兜底)
- 词典底部卡的在线音频 URL (`WordDefinition.audioUrl`) 拿了没播,朗读一律走 TTS (保证都是英音)
- API Key 明文存 DataStore (纯本地单人设备,和 Vita 同策略)

## 下一步候选 (待 charlotte 决定优先级)

1. 真机跑通全链路,特别是 TTS 英音、词典、语音输入
2. 聊天流式输出 (SSE)
3. 数据备份 (JSON 导出/导入,抄 Vita 的 BackupManager)
4. 记录提醒 (抄 Vita 的 ReminderScheduler)
5. 图标换正式设计 (现在是占位 L 字)
6. 发音打分/跟读对比 (说一句和 TTS 原句对照) — 要真机验证语音识别质量后再评估

## 关联项目

- **Vita**: `/Users/restartday/Documents/Vita` — 健康追踪 app,大量模式来源 (双协议 AI 客户端、DataStore 偏好、手摇 DI、深色主题)
- **Fortuna**: `/Users/restartday/Documents/Fortuna` — 资产管理 app
- 三个 app 平行,数据不互通
