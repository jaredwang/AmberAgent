# Phase 3 Standards 独立复核

结论：**无阻塞项，未发现本阶段 diff 对仓库标准的硬违反。**

范围：`d983c0b` 后 E1–E4 的 ChatInput、ChatInputSubmissionOwner、ChatInputState、ChatPage、文档点击、图片预取与对应回归，结合 audit-E/repair-E/findings 复核。Theme、TwoPane、ChatInputComposers 的原 WIP 不计为本次修改；前两阶段不重审。本轮仅写报告，不改生产或测试，不运行 Gradle，不读取兄弟产品。

- **单一 owner 与直接数据流（用户规则 4、5）**：submission owner 只持有 composer 的 in-flight guard；parts、编辑 ID 与会话 ID 在挂起前捕获。ChatPage 按捕获的编辑 ID 调 VM，不再读取后来选中的编辑目标；受理结果由 Boolean 直接返回，owner 负责唯一清空动作。没有 durable queue、第二份 draft、DI 或通用状态框架。
- **消费与取消（用户规则 3、5、7）**：普通发送仍提交点击时的 snapshot；只在 accepted、会话/编辑目标/内容仍相同且无未完成导入时消费输入。后来文本、不同编辑目标、新导入均保留。校验后重验 coroutine cancellation 和会话归属，finally 释放 guard；拒绝与取消不清空输入。Main/UI confinement 与 UNDISPATCHED 启动使捕获发生在回调返回前，无必要加锁。
- **Compose 生命周期（用户规则 5）**：协程属于 rememberCoroutineScope，composition 销毁时取消；owner 随 input state 创建。已有双栏 route 使用 detail.contentKey 分隔 composition，因此不能仅凭 owner 中的旧 state 引用推断正常换会话仍会 late-send。延迟键盘隐藏也检查后来 draft/edit/import，没有新增后台生命周期。
- **范围与可验证行为（用户规则 4、7、11）**：tap/long-press 共用一条 validation/submission 路径，仅队列模式与 answer 行为不同。附件编辑只补无 Text 原消息的新增文字；远程文档 URI 直接交既有 opener，file URI 保持 FileProvider；预取只读 selected variant，无额外下载、缓存或重试。

Fowler smells 仅作 heuristic：新 owner 封装实际挂起边界与输入消费职责，不是 Middle Man；UI callback 明确返回受理结果，减少跨层隐式清空。未找到支持额外架构整理的证据。

测试审阅覆盖受控挂起、重复点击、捕获编辑目标、后续草稿/导入、取消与重试、实际 Compose 文档点击和 selected variant。单元/Compose 测试、编译及真机键盘/系统 opener 体验仍由各自执行证据证明，本报告不替代它们。

## 最终验证关闭

本阶段 Standards review 已关闭，无剩余阻塞。已核对 root 的 `final-verification.json`：最终 JVM 22 模块退出码 0，3700 用例中 3676 实际通过、24 跳过、0 failure/error；包含本阶段代码和回归的 app 为 2920 用例、14 跳过、0 failure/error。执行证据为 `final-jvm-tests.log`。此前审查与 RED 证据保留。APK 构建由 root 继续完成，真机键盘、系统 opener 及用户体验验收仍是独立门槛；本 reviewer 未自行运行 Gradle。
