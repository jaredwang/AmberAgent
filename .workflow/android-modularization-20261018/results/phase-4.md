# Phase4 功能域拆分与验证

状态：**PASSED_WITH_EXPLICIT_EXTERNAL_GATES**。独立review已闭7owner、指定行为和最后两视口像素；正式报告为phase-4-independent-review.md。

## 交付与owner

- CouncilRoomManager 2501→1470：room/mutation/job/close/claim/ask/epoch仍单owner，Planner/HostTurns/AutoRun/TaskProjection是真消费者；批次Manager1122→968，CLI按既有接口单独实现。唯一auto cursor跟实际run，不复制watermark。
- CouncilTimelineTab1419→659：scroll/follow/gesture/IME/token/key保host，叶组件只接具体投影/callback，Page唯一绑定VM。历史ASK无接收者时真实只读，不显示无效输入。TopBar/ModePanel、附件chip与roster已按实际像素精准修复。
- DeepReadScreen2436→321，TodayBoard1543→228：route/history/WorkManager/visit latch仍同owner，Reader、Hero、文章和设置pane接真实命令。Manager1697→553、Writer1003→610：topic/epoch/cache与writer锁保唯一，Magazine/Synthesis/Prompt/Artifact/纯content分别负责完整用例。
- Live Page1521→368，Manager971→784：generation/run/analysis/pending/Engine与终态接受归Manager；完整DraftFiller、真实BubblePresenter分工，确认/denylist/重挂水位/通知决策保持。
- SubAgentManager1394→1310：thread/generation/epoch/mailbox/admission/terminal CAS保唯一；具体TurnLauncher/TaskProjection接生产。未drain消息按本代真实ID条件退QUEUED，已消费正常PERSISTED，旧callback不碰新代。
- TerminalRuntime1213→812、Workspace784→260：jobs/admission/install/终态仍Runtime；完整session map/process/lease owner，native process、Termux、具体SAF与mirror IO分别内聚。无新后端框架/DI对象图/重复锁。

## 精准修复与证据边界

1. 正常Settings取消误halt：两处onFailure先原样抛CancellationException，非取消fatal合同不变；原Terminal teardown真正执行，不遗留scope逃避。
2. Council本地room发布→task注册半交付：真实红后只短NonCancellable本地交付，前置取消/网络/close不扩不可取消区。
3. Terminal注册失败漏admission：仅未被backend接手的本次reservation在同admissionMutex短补偿；原3项真实红→绿。
4. 默认Broad gate查出HEAD已有身份bug：URL完整lowercase误合并path/query；Worker sourceUrl请求错误分支跨topic按同标题克隆；Jev评分cache遗漏实际bounded state。均按原owner最窄修复，原断言保留。
5. Importer真实ContentResolver stream supplier证实部分读取后IO异常漏temp；open/copy/parse同finally、实际64MiB+1上限、40k与取消传播。四具体parser新增严格正文入口，旧String消费者保诊断/格式，空格式容器不当正文，真实字符/alt与合法标点保留。EPUB unresolved entity空串走原Html解码。Office平台SDK/非法XML头/旧输出尾空格是明确fixture问题，未改产品去迎合错误fixture；两空表strict首次执行即绿，未冒称失败曾到达strict。
6. 真UI问题有当期PNG/实际glyph证据：Council role被长姓名挤为0宽→局部FlowRow；TopBar副标题被56dp裁→自然最小高+模式overlay自然锚定；文件名整段不绘制→nowrap/ellipsis；DeepRead初始masthead压固定圆→实际statusBar inset+列表初始reserve。原字体/tokens/回调/状态保持。
7. 非产品失败分别修fixture：JUnit非void方法、真实无障碍root sealed、fakeProcess清理自计destroy、Robolectric nativeLibraryDir、恢复scroll后未composed标题、截图早于draw、命中peek冒充Dialog、父行中心落disabled箭头触区。原业务断言保留，严格Dialog/实际glyph替换无效oracle，无重试/语义点击绕真实触摸。

## 实际验证（分别记录，不重叠相加）

| Gate | 实际结果 |
|---|---|
| Council最终模块 | 3类32项全绿0skip；真实公共Manager/Executor的STANDARD/FULL/ask/close与短交付 |
| Terminal/Workspace模块 | 5类21项中13pass/8需SSHloopback fixture跳过/0fail；未冒称SSH已执行 |
| 第一完整app候选 | 84类452项432pass/20fail/0skip，真问题与fixture按上面分类修复；其余原green来源保留 |
| 最后指定app行为 | phase-4-final-specified-behavior-and-apk：20类118项117pass/1旧Hero布局flag fail/0skip；后phase-4-final-hero-glyph-and-terminal-target-apk严格实际glyph1green0skip，闭唯一指定失败 |
| 关键实际链 | SubAgent28、TerminalAdmission4/Session2/UTF8 2/Workspace4、真实DeepReadKernel fallback6/Importer11/Worker3/URL5、Live默认Accessibility adapter2、Koin3等在上述fresh XML真绿；Live普通Filler7/真实BubblePresenter3此前fresh绿 |
| document | Office17+Bounded2在phase-4-last-ui-layout-red-and-office-fixture fresh19green0skip |
| 设置取消 | SettingsFlowExt2 freshgreen；真实Aggregator teardown由Terminal链验证 |
| app/AndroidTest | FullDebug两APK已组装并覆盖安装；最终main6ac522ef、test05dd69f8，详见phase-4-final-apks.json；default-on native preprocess仍dadbc3234... |

默认411和320/font1.5的真实默认Koin Council历史/引用/附件/roster/synthesis/recreate、DeepReadpartial→ready→recreate/冷重开且无Work请求、Live真实保存/去重/配置/recreate均有当期PNG/semantics/owner/cleanup并独立审图。最终C/D证据为phase-4-*-final-411及phase-4-*-320-large-font-final-chrome-hero；Live用phase-4-live-411与phase-4-live-320-large-font-live-first。

Terminal真实ANDROID_SHELL写读中文/emoji→输出Dialog→Stop→durable CANCELLED→lease可fresh acquire→新job COMPLETED→Back仍原Chat/recreate。最终严格title真实触摸320green9.648s；411同fixture实际green13.095s/0skip（phase-4-terminal-final-title-touch-411）。触区JSON实测父Row中心落disabled箭头扩展touchBounds，title中心不落，成功仅证明标题入口，未声称父Row任意坐标都打开。全部数据清理与原rows/files/settings/prefs复原逐项assert，wm/density/font finally精确恢复；系统UI ANR失败轮保留，不算产品或绿态。

## 外部门槛与后续

未把本地宿主/模拟器fixture算真实provider/CLI、overlay权限/第三方采集填入/通知托盘、真实SAF/Alpine session/Termux/SSH、OEM/物理设备/性能/跨端验收。未新增权限、清用户数据、提交、推送或发布。现有未接Workspace file-sheet入口HEAD就无true赋值，只标记，未删除/新增入口。

Phase5已只读准备与计划细化（packets/P5-sequence.md、四份P5-preparation）；本phase独立最终review已关闭，现开始P5生产。当前source/config/resource hash在phase-4-verified-source-snapshot.json，最终报告/状态与该快照保持一致。
