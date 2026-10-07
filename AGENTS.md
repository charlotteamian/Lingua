# AGENTS.md — Lingua 英语学习 App

> 此文件是 Codex 的工作记忆。每次新会话进入这个目录时会自动加载。

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
- ✅ **2026-07-04 对话改语音消息 + 三连需求 (提示词可改/收藏句子/纠正后置)**:
  - **对话语音输入重做 (charlotte: 听写一停顿就断、第一次按必报错)**: 对话页麦克风从 SpeechRecognizer 听写改成**录语音消息直发 Gemini** (`VoiceRecorder` MediaRecorder AAC → `ChatApiClient.voiceChatGemini` 原生端点 systemInstruction+文字历史+音频 inlineData → JSON `{transcript, reply}`)。想说多久说多久, 录音条有计时/取消/发送, 90s 自动发。用户气泡 = 可播放语音条 (MediaPlayer) + 模型转写文字 (可点词); 转写回填 `chat_message.content`, 历史上下文用文字不重复传音频。**DB v1→v2** (`MIGRATION_1_2`: chat_message 加 audioPath)。录音归档 `files/chat_audio/`, 清空对话时一并删。随手记的中文听写保持原样 (弹窗兜底仍在)。
  - **✎ 纠正后置 (charlotte: AI 开头总复述我的话很疑惑)**: tutorSystem 规则 5/6 重写——先像朋友一样回应内容, ✎ better 行放在**回复最末尾**且至多一条; 中文输入也是先回应最后给英文说法。
  - **跟读点评标准可编辑**: `PronunciationCoach.DEFAULT_RULES` (公开常量) + `prefs.pronRules` (空=默认); 设置页新卡「跟读点评标准」预填默认文本可改 (严/松/只盯重音…), 「恢复默认」清空。提示词结构部分 (目标文本/单词表/JSON 格式) 固定不可改, 只开放评判标准段。
  - **收藏句子 → 复习「出中文说英文」**: ChunkEntity 新 kind=`sentence`; `ReviewRepository.addSentence` 收藏时 AI 顺手翻中文当卡面 (失败不阻塞); 入口: 对话每条消息气泡的 BookmarkAdd 图标 + 语料详情播放行的 BookmarkAdd → `SentenceCollectSheet` (按句切分, 跳过 ✎/▸ 行, 点一句收一句)。复习页句子卡: 正面中文 + **「开口说英文 (录音对比)」**——录音 → `PronunciationCoach.assess(原句)` → 自动亮答案 + 列差异 (漏词/读错词/总评); 也可「直接看答案」。评完一张 speakCompare 状态重置。
  - 验证: assembleDebug 通过。**待真机**: 语音消息全链路 (录→发→转写回填→气泡回放)、DB v2 迁移后旧聊天无恙、✎ 是否真的到末尾 (模型遵循度)、收藏句子→复习句子卡→说英文对比闭环。
- ✅ **2026-07-04 六连优化 (charlotte 第二轮反馈)**:
  - **✎ 句子可收藏**: `splitSentences` 把 ✎ 行的英文说法抽出来放句子清单最前 (最值得收的就是纠正句)。
  - **自动朗读的 ✎ 播报口吻**: `stripMarkup` 把 ✎ 行读成 "By the way, a more natural way to say that is: …", ▸ 词块行不读。
  - **中英夹杂**: tutorSystem 新规则 7——学习者英文里夹中文词 (不会表达) 时, 回复里引用中文并给英式说法, 最后 ✎ 行给完整改写句。
  - **收藏状态 DB 驱动**: `ReviewRepository.savedTexts` (全部 chunk 文本归一化小写集合) 供对话/语料/往期各处显示 ✓ 已收; ▸ 词块行已收藏显示绿勾不再给 ＋; `addSentence` 改先落库再后台翻译 (`ChunkDao.updateMeaning`), ✓ 秒现。
  - **对话分话题归档**: 对话页只显示**当前话题** (最后一个 contextBreak 之后, `sessionsOf` 切分); 顶栏「往期」→ `chat_history` 路由列出旧话题卡 (时间/条数/首句预览) → `chat_session/{anchorId}` 只读回看 (气泡渲染复用 `MessageBubble`, 已改 internal; 点词/朗读/收藏照常)。无新表, 全部由 contextBreak 派生。
  - **复习会话化**: `ReviewFilter` (类型多选 词块/生词/句子 + 数量 10/20/50/全部, 默认全类型 20 张) → 「开始复习 (N 张)」把到期卡快照成 `_sessionIds`, 队列 = 到期∩快照 (评完自动缩短), 顶部「结束」可中断; 完成卡显示本次张数+剩余到期。收藏库列表按 句子/词块/生词 分组显示, 不再平铺。SRS 仍是简化 SM-2 (到期早的优先)。
  - 验证: assembleDebug 通过。**待真机**: 往期归档/回看、复习会话选择流、✓ 已收状态各入口一致性。
- ✅ **2026-07-04 复习范围+收藏库独立页 + RP 纠偏 + TTS 分段修超时**:
  - **复习范围**: `ReviewScope` DUE(今日到期)/ALL(全部收藏, 按到期先后排序提前加练); 会话快照改有序 id List, grade 显式出队 (ALL 范围下评过的卡不会因仍在库里而滞留队列)。收藏库从复习页平铺改**独立页面** (`library` 路由, 复习页只留入口卡+计数; 筛选 chips, 最近收藏在前) —— Anki 牌组/墨墨词库范式。
  - **RP 纠偏 (charlotte: 要的是无口音 RP 不是伦敦腔)**: tutor 人设去 "from London" 改 neutral BBC-style RP; TTS 风格指令同改 (**经验: 风格里写 London 会被读成伦敦腔, relaxed 会放慢——风格词都会被忠实执行**)。`prefs.ttsStyle` + 设置页「发音风格指令」多行框可自改 (空=默认, 恢复默认按钮), 生效于 Gemini 语音。
  - **语料页退系统音真凶**: 整段语料一次合成, 音频生成时间超过 60s 读超时 → 静默降级 (聊天回复短所以没事)。改**分段合成+顺序播放** (`segment()` 按句聚合 ≤220 字符, 逐段 fetch+play, 段级缓存含 style hash), 第一段很快出声; 某段失败剩余文本退系统音并记 lastError。
  - 验证: assembleDebug 通过。**待真机**: 语料整段播放是否全程 Gemini 音、段间衔接是否自然、风格改成中性 RP 后口音听感。
- ✅ **2026-07-04 听读模块 (charlotte: YouTube 看视频想查词 + podcast 也要)**:
  - **架构**: 通用「(音/视频源, 带时间戳字幕)」播放页, 两种来源—— **YouTube** (免费字幕, 0 token) / **本地音频·播客** (Gemini 转写, 花 token 用户确认)。DB v3 加 `media_item` 表 (type/title/source/transcribed); 字幕正文存 `filesDir/transcripts/<id>.json`。
  - `data/media/YouTubeCaptionClient`: 抓 watch 页 (手机 UA) → 解析 captionTracks → 英文轨 (人工优先, 兜底 asr 自动字幕) → `fmt=json3` 拉全文 → ASR 碎片合并 (~80 字符/条, 停顿>4s 断句)。**非官方接口, YouTube 改版可能失效**; 需手机网络能通 YouTube (charlotte 有 Clash)。
  - `data/media/MediaRepository`: importYouTube / importAudio (SAF uri + takePersistableUriPermission, 不复制文件) / transcribe (inline ≤18MB ≈ 64kbps 35 分钟, 超限提示裁剪; maxOutputTokens 65536; 提示词要求 `[{"s":秒,"t":"句子"}]`) / 字幕文件读写。
  - **UI**: 语料页顶部「听读 · 视频/播客」入口卡 → `media` 素材库 (URL 输入+拉字幕 / 本地音频挑选, 列表卡) → `media_player/{id}` 播放页: YouTube 用 WebView + IFrame API (JS `Bridge.postTime` 每 500ms 回报进度, `seekTo()` 定位) / 音频用 MediaPlayer (播放/暂停/±10s); 下半字幕 LazyColumn **同步高亮当前句 + 自动跟随滚动** (选中行时暂停跟随), 点词查词典, 点行出操作: ▶从这句播 / 收藏(进句子复习) / AI 解释 (行内琥珀色显示, 每次几百 token)。
  - **系统分享接入**: Manifest ACTION_SEND text/plain + launchMode=singleTask; MainActivity 收到分享文本 → `container.pendingSharedUrl` → 自动跳听读页并填进输入框。
  - 验证: assembleDebug 通过。**待真机**: YouTube 字幕抓取成功率 (代理环境)、WebView 播放器加载、时间同步/定位、本地音频导入+转写、系统分享入口。播客转写上限 18MB 是 inline 限制, 以后要更长的再上 Gemini Files API (2GB)。
  - **字幕抓取三层通道 (charlotte 真机报「没拿到字幕」后加固)**: Mac 实测该代理出口 IP 被 YouTube 风控——watch 页和 Innertube 全部客户端 (WEB/MWEB/TVHTML5→LOGIN_REQUIRED, ANDROID/IOS→FAILED_PRECONDITION) 裸 HTTP 全灭。通道链: ① Innertube ANDROID player API → ② watch 页抓取 → ③ **WebViewCaptionFetcher 终极兜底**——真 WebView 加载 watch 页 (完整执行 botguard JS, 和浏览器看视频同通道), 页内 JS 等 `ytInitialPlayerResponse` → 选英文轨 → 页内 `fetch(baseUrl+fmt=json3)` (带 cookie/PO token 上下文) → JSBridge 回传; 预置 `SOCS=CAI` cookie 防欧盟 consent 页; 45s 超时。失败报错会分别给出直连/网页通道原因。
  - **WebView 通道二轮加固 + app 内登录 YouTube**: 真机诊断显示 WebView 已能加载真实视频页 (标题正确) 但站内 API 仍 LOGIN_REQUIRED——该出口 IP 被风控到「未登录一律拒绝」。改: WebView 桌面 UA (防 m.youtube.com 重定向) + domStorage + 页面变量等不到时用页面自己的 ytcfg 密钥/INNERTUBE_CONTEXT 调站内 player API + 报错附 [host·标题]。最终解: **`yt_login` 路由** ——用户自己在 Google 页面登录 YouTube (app 不经手密码), cookie 持久存 WebView CookieManager, 字幕通道带登录态即放行; 登录页 UA 去掉 "; wv" 标记 (Google 拒内嵌 WebView 登录的已知对策); 素材库报错含 LOGIN_REQUIRED 时显示「登录 YouTube 后重试」引导。**若 Google 登录页仍报「此浏览器不安全」, 备选方案: 换 Clash 节点 / 引导用户从系统浏览器分享 cookie (未做)。**
  - **字幕下载空正文攻坚 (2026-07-05, 登录打通后的新卡点)**: 登录态生效后字幕轨地址能拿到, 但裸下载字幕地址 (OkHttp 或页内 XHR, 不带 pot) 返回 **HTTP 200 空正文**——YouTube 对 timedtext 要求 PO token (播放器会话校验参数)。下载改三层: ① 页内 XHR 直接下载; ② 页内 JS 触发真播放器加载字幕 (`movie_player.loadModule('captions')`+`setOption`+静音起播), `shouldInterceptRequest` 截获它自己发的带 `pot=` 的 timedtext 请求, 用该地址页内再下一次; ③ 都空手把地址 (截获的优先) 交回 OkHttp 带 cookie 下载。`RawCaptions` 带 `captionJson`(页内下载好的全文)/`pottedUrl`; WebView 通道超时 45s→60s。同轮修掉: 上一轮 Kotlin 侧下载跑在主线程抛无消息的 `NetworkOnMainThreadException` (界面只显示兜底文案「导入失败」), 现在报错兜底带异常类名、`parseJson3` 空输入给可读报错。**经验: timedtext 返回 200 空正文 = 缺 pot 校验参数, 不是网络问题。** 二轮修 (真机: 听到一秒声音后 60s 超时): **未挂载 View 的 post/postDelayed 永远不执行** (排在等挂载的队列里)——隐藏 WebView 的调度一律用主线程 Handler; 播放器触发改等就绪重试 (movie_player 没出来时 800ms 重试), `<video>` 元素和 player API 双重静音 (光 player.mute 下早了会漏声音); 直连通道带登录 cookie 后 watch 页已能拿到字幕轨 (只剩下载被 pot 挡)。**2026-07-05 字幕导入全链路真机跑通** ✓。
  - **播放页真机首轮修 (字幕有了但视频黑屏/点不出整句操作)**: ① 播放器 WebView 补 `domStorageEnabled` (缺它 YouTube 播放器直接黑屏) + `mediaPlaybackRequiresUserGesture=false` (「从这句播」是原生按钮发起, 不算页内手势会被吞); iframe onError 回传, 101/150 (作者关站外嵌入) 等黑屏原因直接显示在播放器区。② TappableText 每个单词都是查词热区, 整行几乎没有可点的空白 → 每行行尾加 ⋯ 把手弹整句操作; 「解释」改名「翻译」且提示词改为先整句翻译再点评表达。
- ✅ **2026-07-05 播放器 watch 页降级 + 模型三分工 (Gemini Live 实时通话)**:
  - **播放器 code 152 (嵌入被风控拒)**: iframe 报错 (非 code 100) 自动降级加载真 `m.youtube.com/watch` 页——该通道在此设备已验证可播 (拉字幕时能出声)。注入 `WATCH_PAGE_JS` 挂同名全局 `seekTo` + 500ms `video.currentTime` 轮询, 调用方无感; 播放器 WebView UA 去掉 "; wv" 标记。
  - **模型三分工 (charlotte: 严禁 STT/TTS 拼接语音流, 按 Google 推荐拆)**: ① 文字/讲解 = 「AI 对话服务」模型 (gemini 预设默认改 `gemini-3.5-flash`); ② 朗读 = TTS 模型 (默认改 `gemini-3.1-flash-tts`); ③ **实时语音对话 = Gemini Live 端到端** (`prefs.liveModel` 默认 `gemini-3.1-flash-live-preview`, 设置页对话卡可改——**模型 ID 是按 charlotte 说法猜的, 连不通让她贴官方文档里的名字**)。
  - **`GeminiLiveClient`** (data/ai): BidiGenerateContent WebSocket; setup 带 systemInstruction (`Prompts.liveTutorSystem` 口语版人设, 无 ✎/▸ 标记) + AUDIO 模态 + 声音名 (复用 ttsVoice) + 双向转写开关; 麦克风 AudioRecord **VOICE_COMMUNICATION 源 (自带回声消除)** 16kHz PCM 100ms/片直传; 模型 24kHz PCM → AudioTrack 流式播; `interrupted` 清播放队列 (插话打断); `turnComplete` 把双方转写经 `ChatRepository.addLiveExchange` 落库进聊天记录 (挂断时半轮也落)。对话页输入框左侧 ☎ 按钮拨打, `LiveCallBar` 显示状态/实时转写/挂断。
  - 验证: assembleDebug 通过。**待真机**: 通话全链路 (模型名对不对/转写落库/打断), 播放器 watch 页降级效果。
- ✅ **2026-07-11 新话题立即归档 + 全 app 视觉对齐设计稿 + 换 Nunito 字体 (charlotte 二连)**:
  - **新话题当场生效**: 之前点「新话题」只挂 pendingNewTopic 旗子, 下一条消息才真正分界。改为点击即落一条 **break 标记行** (`ChatRepository.startNewTopic`, role=`break` 空内容 contextBreak=true): 旧话题立刻归档进「往期」、对话页立即空白。`sessionsOf` 分段时跳过标记行 (不进任何段); buildContext 的空内容过滤天然把它挡在模型上下文外; send/sendVoice 的 newTopic 参数和 pendingNewTopic 状态全部删除。当前话题为空时按钮禁用、重复点不落重复标记。
  - **Nunito 字体**: 下载 Google Fonts 官方仓库的 Nunito 变量字体 (OFL, 277KB) 进 `res/font/nunito_variable.ttf`; Type.kt 重写——15 个字型全套 Nunito (变量字体 API 26+ 按 weight 实例化非假粗体), 标题 Bold、正文 Medium, 中文无字形自动回退系统字体。
  - **视觉对齐设计稿** (charlotte 发的四张图): ① 今日页重构——「这一周」独立卡 (标题+看全部→+周条, 火焰挪回成就页) / 三档卡每档**色点+档位色标题** (保底琥珀/正常蓝/好天薄荷) + 任务行右侧 x/y (完成薄荷色) / 三个快捷入口拆成独立卡; ② 新建 `ui/components/LinguaChip` (胶囊形, 选中=蓝描边蓝字), 复习页+收藏库全部换用; 复习「开始复习」按钮 50dp 高 14dp 圆角; ③ 语料页「＋记一条」蓝调 pill, 日期 "2026-07-06"→"7月6日"; ④ 对话气泡圆角 20dp, ▸ 词块 + 按钮加玫红圆底; ⑤ 底栏 containerColor=背景色 tonalElevation=0, 选中蓝/未选灰、淡蓝指示。
  - 验证: assembleDebug 通过。**待真机**: 字体渲染 (中英混排观感)、新话题即时归档、四页视觉与设计稿的差距。
- ✅ **2026-07-11 打卡日历 · 成就页 (合入外部补丁 android-patch)**:
  - 来源: charlotte 从别处拿到的「完整文件」式补丁 (Downloads/android-patch, 含 INSTALL.md)。逐个 diff 确认全是基于当前版本的纯增量、无冲突后叠加覆盖, 非盲目替换。
  - **新页面** `stats` 路由 (`ui/screens/stats/StatsScreen.kt` + `StatsViewModel.kt`): 火焰 hero (连续天数/最长纪录/累计天数) + 本月网格 (三色分档: 保底琥珀/正常蓝/好天薄荷, 今日描边) + 近一年 GitHub 式热力图 (53 周横滚, 月份标签)。今日页 streak 卡片重做成会呼吸的火焰 + 迷你周视图, 点击进成就页。
  - **配套增量**: `DailyPlan.tierForLog()` (历史某天档位, 好天判定用「听≥1」代替「到期清零」, 因历史行不存当天到期数); `PlanRepository.allLogs()` 暴露全表流; `TodayViewModel.weekLogs` (本周7天); `Routes.STATS` + ViewModelFactory 注册 + MainActivity 路由 + TodayScreen `onOpenStats`。三色配色函数 `dayTierColor/dayTierTextColor` 在 TodayScreen 里 internal, StatsScreen 复用。
  - 补丁作者漏了 `import ...layout.height` (无编译环境), 合入时补上。assembleDebug 通过。补丁 README 说 Chat/Corpus/Review 三页视觉重做未做 (风险高), 只落地了打卡日历。
- ✅ **2026-07-11 播放器黑边/暂停攻坚 + 暂停回落字幕定位 + 复习重学循环 (charlotte 三轮反馈)**:
  - **黑边根治换思路**: 不再追着修 YouTube 的排版 (藏顶栏后播放器残留旧偏移, CSS 打补丁是无底洞)——watch 页播放器容器直接 `position:fixed` 钉满 WebView 视口, video 绝对定位填满 + `object-fit:contain` 自行居中, 完全绕开 YouTube 的尺寸计算; 播放窗格恢复纯 16:9 (16:9 视频正好铺满无黑边)。
  - **播放/暂停状态机**: 弃用「单次 pause 事件里判断+抢救」(600ms 触屏窗口误判 = 用户暂停被抢回去/自动暂停没救回来的根源), 改「用户意图标志 + 看门狗」——触屏 900ms 内的 pause 记为用户主动 (`__linguaUserPaused`), 无触屏的 = 平台自动暂停, 由 1.2s 轮询的看门狗恢复 (事件丢失/play() 被再掐都兜得住); 最近 2.5s 有触屏时看门狗不动手, 防抢用户的暂停。
  - **暂停 → 画面缩回 → 字幕定位** (charlotte 的核心诉求): 新增 Bridge 事件 postPaused/postPlaying (watch 页走 video 捕获事件, iframe 走 onStateChange, 双模式统一); 用户暂停 → 程序化退全屏 (页内 `document.exitFullscreen()` + 400ms 兜底直接收 custom view) → 字幕滚动定位到暂停那句并自动弹出整句操作行 (翻译按钮就在手边); 恢复播放自动清掉「暂停自动选中」回到跟随滚动, 手动点选的行不受影响 (autoSelectedFromPause 区分)。字幕正文/翻译字号 bodyMedium→bodyLarge (真机上太小)。
  - **字幕拉取加固**: WebView 通道超时 60s→90s; 隐藏 WebView layout 成 1080×1920 (0×0 尺寸下播放器经常不建 video 元素, 触发字幕/截 pot 地址就挂); onPageFinished 迟迟不来时 8s/20s 定时补注入提取脚本 (脚本幂等); 截 pot 地址轮询 12s→20s; importYouTube 的网页通道失败自动重试一次再报错。
  - **复习重学循环 (Anki relearning 范式; charlotte 要参考主流方案——她要的「遗忘当天再循环」正是 Anki 机制, 于是照搬)**: 「忘了」的卡 interval=0 且 dueEpochDay **留在今天** (不再推到明天), 会话内挪到队尾循环, 答「模糊/记得」才出队毕业到明天 → 「到期清零」天然要求所有忘掉的卡当天重学过关, 中途退出会话忘掉的卡也仍在今日到期里, 完整实现「今天没有遗忘卡才算完成任务」; 只有从已毕业状态 (interval>0) 摔下来才扣 ease/记 lapse, 学习中/重学中反复答错不重复惩罚 (Anki 同款, 防 ease 螺旋下坠); 重学答对那次才计入「复习 N 张」计数; UI: 会话副标题显示重学数、重学卡片红色「重学 ·」标识、完成卡文案带重学统计。
  - 验证: assembleDebug 通过。**待真机**: 黑边是否消失、暂停/播放是否跟手、暂停缩回+字幕定位链路、字幕拉取成功率、重学循环手感。
- ✅ **2026-07-04 复习范围加维度 + 收藏库独立页 (charlotte: 参考一般背单词 app)**:
  - 调研确认主流范式 (Anki 牌组/墨墨词库): 复习首页只放今日任务+设置+开始, 词库是独立入口页。
  - `ReviewScope` (DUE 今日到期 / ALL 全部收藏): 范围 chips 在类型/数量之上; ALL = 提前加练未到期的卡, 按到期先后排序取卡。会话快照改**有序 id 列表**, `grade()` 显式移除 (支持未到期卡不依赖 due 流缩短); `sessionQueue` 改从 allChunks 映射。
  - **收藏库独立页** (`library` 路由, `LibraryScreen`): 复习页平铺列表删除, 换成「收藏库」入口卡 (句子/词块/生词计数 + →); 库内: 全部/句子/词块/生词筛选 chips + 最近收藏在前 + 朗读/删除 (`ChunkRow` 改 internal 复用)。
  - 验证: assembleDebug 通过。——首启词典拷贝、TTS 英音可用性、点词弹卡、AI 对话/转语料 (要先在设置填 Key)、SRS 队列流转、语音输入 (依赖设备有语音识别服务, 通常来自 Google app; 国产 ROM 无 GMS 时会提示不可用)。charlotte Android Studio 打开 → Sync → Run

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
