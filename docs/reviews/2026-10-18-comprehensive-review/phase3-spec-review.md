# Phase 3 独立行为复核

结论：**通过，无未关闭的确认问题。**

范围为 E1–E4 的当前生产 diff 与对应回归；未重审 Phase 1/2，未改生产/测试，未运行 Gradle。逐项按生产入口和可观测结果二次核对，未把风格或缺乏触发证据的边缘列为缺陷。

- **E1 文档打开**：真实 MessagePartsBlock 点击对 `file:` 保留 FileProvider 与 read grant，已接受的 HTTPS Document 直接交给 ACTION_VIEW chooser，避免 toFile 异常。Compose 回归由真实 MiniApp attachment parser 生成 HTTPS Document、点击真实 chip 并检查 opener URL；本地回归另外通过 opener 的 content URI 读回文件内容，未以 helper 代替生产点击路径。
- **E2 附件消息编辑**：原消息没有 Text part 时，getContents 只在新文字非空时插入一次 Text。既有多 Text 消息仍修改末段、保留其他文字顺序及附件；两个形状均由真实 ChatInputState 回归覆盖。save bar 和发送回调都消费该 owner，未增加平行状态。
- **E3 发送归属**：实际 tap 与 long-press 都进入 prepareSend → 同一 remember(state) submission owner。UNDISPATCHED 使点击返回前捕获 parts 和 editing ID；inFlight 在验证挂起期间拒绝第二次验证/发送，并在 finally 释放。验证后重新检查 coroutine cancellation 与 conversation；ChatPage 用捕获 editing ID 路由 edit，而非读取后来目标。回调返回同步受理结果，只有受理成功、当前内容/目标仍等于该 snapshot 且无未完成 import 才消费输入。新文字、不同 edit、后来 import 保留；拒绝和取消可重试。延迟收键盘也检查新草稿/编辑/import。Deferred-barrier 回归直接调用生产 owner，覆盖这些状态转换；未把它宣称为 Compose 点击或设备体验验收。
- **E4 图片预取**：收集改为 selected variant 的 parts；Image 与 Markdown 图片规则不变，换 selection 的实际 MessageNode 回归覆盖隐藏 variant 不请求。没有新增缓存或调度层。

本轮范围未触及 Theme、TwoPane、ChatInputComposers 的原 WIP；已按 baseline.json 复核，11 个基线文件内容哈希全部匹配。上述结论是行为与接线 review；GREEN 由 root 串行执行，真实 image-provider preflight、应用安装/运行及物理设备交互尚未验收。

最终验证关闭：已读取 root 的 final-verification.json 与 final-jvm-tests.log，统一 JVM gate exit code 0；app 2920 项、0 failure/error、14 skipped，全 22 个测试模块实际通过 3676 项、另 24 skipped。E1–E4 生产与回归已进入该轮检查。Phase 3 审查与回归验证通过，无未关闭的确认问题；普通配置 APK 构建仍在进行，本记录不扩大为设备或真实 provider 验收。
