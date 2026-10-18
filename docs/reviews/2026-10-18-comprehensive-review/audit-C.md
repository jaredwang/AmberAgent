# 工作包 C：工具、Jev 与手机自动化二次复核

审查基准：`d983c0b7` 及当前生产源；只读检查 `core/jev`、`app/core/jev`、`feature/tools*`、`app/feature/tools`、`feature/subagent`、`feature/modelcouncil`、Android automation。未读取兄弟产品仓库，未改生产或测试，未运行 Gradle。工作树已有用户与其他工作包改动，均未动。

## 已确认问题

### C1 / P1：真实外部动作被登记为 READ_ONLY，故障恢复不要求结果确认

- owner：`feature/tools/api/src/main/kotlin/app/amber/feature/tools/ToolRegistry.kt:618` 的 `mutatesState()` 主要依赖名称提示，只额外列举 `screen_run_goal` / `screen_run_flow` / `screen_add_app`。`screen_click`、`screen_long_click`、`screen_swipe`、`screen_input_text`、`screen_tap_text`、`screen_back`、`screen_home`、`screen_open_app`、`screen_open_url`、`screen_scroll_until`、`screen_collect_list` 均不命中写入语义。
- 实际效果：例如 `app/.../feature/tools/ScreenAutomationTools.kt:388` 已调用 `service.tap()`；`:492` 写入输入框；`:986` 已派发列表滚动。
- ledger 接线：`app/.../feature/runtime/AgentToolDispatcher.kt:577` 调用 `toolDef.effectClass(tool.input)`；`ToolRegistry.kt` 的 `effectClass()` 在 `!mutatesState()` 时返回 `READ_ONLY`。
- 恢复效果：`RunRecoveryService.kt:458` 对 STARTED + READ_ONLY 保持可重试，只有 NON_IDEMPOTENT_WRITE 才 `markOutcomeUnknown()`。已点击后、记录 finish 前进程死亡，恢复允许重发动作，且旧坐标可已变成另一个提交类按钮。
- 二次复核：单次审批不是防线，因为审批与 args 仍可有效，结果是否已经发生是独立的 ledger 语义；新 goal/flow 已被显式列为非幂等，低层遗漏没有对应例外契约。
- 同根因另有确定实例：`feature/tools/impl/.../TerminalTools.kt:39` 的 `terminal_execute`、`:104` 的 `terminal_job_start`、`:246` 的 `terminal_session_exec` 会执行任意命令，却也不命中上述名称提示；`terminal_workspace_flush` 会写回 SAF，同样遗漏。`feature/tools/access/.../PhoneAccessTools.kt:50` 的 `call_phone` 也有直接拨号分支，需按 invocation 区分直接拨号和仅打开拨号页，或保守统一登记外部动作。
- 最小修复：为已验证的外部动作明确声明 mutation/effect 类别，不用 `needsApproval` 反推所有工具都写入；保持观察工具 READ_ONLY。覆盖实际工具名的静态 policy 测试与 STARTED 恢复测试，断言点击/命令工具进入 OUTCOME_UNKNOWN，观察工具仍可重试。

### C2 / P2：`value_equals` 比较展示标签，空输入框的 hint 会被当成已输入值

- 入口：新 expectation 可通过 `screen_click` / `screen_long_click` / `screen_run_flow` 使用 `value_equals`。
- 数据链：`AmberAccessibilityService.kt:426` 将空 `node.text` 回退为 `node.hintText`，`:477` / `:526` 将其投影为 `ScreenNode.label`；`ScreenAutomationTools.kt:1340` 的 `editableLabels()` 直接取 label；`:1404` 至 `:1408` 对 label 与期望值做相等比较。
- 确定触发：非密码可编辑框实际 text 为空、hint 为“搜索”，验收 value 为“搜索”；当前返回 `met=true`，输入框实际没有该值。展示 label 的截断也使长输入无法精确验值。
- 二次复核：hint 回退对候选定位有意义，但不能作为字段 raw value；现有纯 probe 测试只提供字符串数组，没有覆盖 Android 生产投影，所以不能排除此误判。
- 最小修复：在 screen snapshot 的 editable 节点提供独立、未截断、非密码的实际 text/value，展示 label 继续保留 hint；验收只比较实际 value。用空 text + 非空 hint、非空实际值、密码字段、长值做回归。

### C3 / P2：新 visible/enabled/absent 验收把隐藏节点及子串当成可见的精确目标

- 生产 probe：`ScreenAutomationTools.kt:1337` 用 `service.findTextNodes()`；`AmberAccessibilityService.kt:738` 至 `:755` 遍历节点，既不检查 `isVisibleToUser`，也不检查有效可见 bounds；匹配用 `haystack.contains(needle)`。
- 影响：缓存于同一 Accessibility 树的隐藏/屏外节点可使 `visible` / `enabled` 假通过；`absent` 的 anchor 也可来自隐藏节点。目标“成功”还能匹配“未成功”，与 expectation schema 的 exact text / label 语义不一致。
- 二次复核：`screen_find_text` 既有子串搜索本身可保留；缺陷是新“验收”复用了该宽松读取面。`captureScreenSnapshot()` 已独立过滤可见节点，可以复用其事实来源。
- 最小修复：新验收 probe 在新 snapshot 上按明确的精确可见文本/标签匹配，避免全局改变已有 find/wait 的搜索契约；动作前 absent 的 targetVisibleBefore 也要使用同一读取语义。测试隐藏节点、屏外节点、近似子串、唯一可见 enabled 和缺失 anchor。

### C4 / P2：`screen_collect_list` 将目标容器外的节点当成列表内容

- 入口：`screen_collect_list`，`max_swipes=0` 已能触发。
- owner：`ScreenAutomationTools.kt:954` 唯一定位容器后，`:960` 至 `:963` 只按纵向范围筛节点，既不限定 ref 后代，也不限定横向范围。
- 触发/影响：并排列表或列表旁的侧栏，只要 y 范围重叠就被返回为 items；包围列表的 root/父节点聚合 label 也可能被当成一条“行”。返回已观察行混入非目标列表内容，后续模型会据此错误汇总。
- 二次复核：snapshot 的 ref 为真实树路径（`AmberAccessibilityService.kt:452`），因此 host 已有足够证据严格区分容器子树，无需猜测屏幕几何关系。
- 最小修复：只采集目标容器 ref 的严格后代，明确保留实际有文本的行/叶节点，避免父节点聚合 label 与子节点重复。测试目标列表、并排无关节点、父 root 聚合标签与嵌套容器。

## 候选、假阳性与 by design

- **collect 去重身份**：当前 `ScreenAutomationTools.kt:964` 至 `:971` 用 label + 屏幕坐标在相邻页去重，没有业务稳定 id，也没有唯一性计数。工具描述与 plan W11 写“只稳定 identifier 合并、匿名同名保留”，但同一计划的执行记录又明确改成“相邻两屏完全一致行合并”。这确实无法保证不同记录同文本同位置不被丢弃，但当前契约冲突，不把它作为已确认独立缺陷；如修 C4，应明确保守采集契约，不能冒充业务身份去重。
- **scope revocation during an in-flight goal**：允许列表在 tool entry 检查，runner 每步仅重验 Jev config；但 remove-app 描述明确不停止已在途动作，stop 描述明确禁止后续 tool 调用而不打断在途 tool。现有生产契约不足以把正在执行的 goal 看作必须即时终止，不列为确认漏洞。
- **低层工具不受 screen_stop**：计划执行记录明确声明低层复用工具缺少 run 绑定，仍逐次审批。已如实缩小契约，不能以原始更宽设想判 bug。
- **screen_run_flow 审批后前台变化**：普通聊天卡审批确实让 Amber 保持前台，与 goal 不同 flow 不主动恢复目标；但存在 `AgentTaskBubbleController` 的悬浮审批入口，可以保持目标应用前台，且 flow 描述要求当前已授权应用。当前不判“功能必然不可用”，待设备体验验证。
- **截图跨应用竞态**：Jev screenshot provider 截取整屏，snapshot 检查与截图之间有异步窗口，且 ImageReader 可返回先前缓存图。截屏 scope 已单独授权；当前没有可复现的 capture-to-package 绑定证据，不把纯竞态推测列为已确认高危。若未来要承诺截图仅限允许应用，应按 window/observation 绑定另外验证。
- **Jev shadow 外发**：`JevDataScope` 文档明确 shadow 同样外发；coordinator 对 requiredScopes 做同样检查，非本地 shadow，不是泄露漏洞。
- **Jev low-confidence 回退**：screen runner 同时做有限候选、禁止类目标、safe 阈值、fresh native snapshot/节点身份和 DONE 新快照核验。不能仅因分类服务不保证正确就推断所有动作绕过权限。
- **子代理扩权**：start 交集过滤 parentTools，动态 allowlist 有校验；runner 对 parentPolicy 与 childPolicy 做 narrow。未确认能通过正常生产入口扩大权限。
- **子代理跨 conversation threadId**：manager 的 read/followup/cancel 多数以全局 threadId 查找，但目前无足够的私有线程隔离契约来判越界；不以 UUID 可引用本身推断安全缺陷。

## 建议修复顺序与验收

1. **Phase 工具效果语义**：先修 C1 的明确外部动作登记，以表格测试锁定工具名及恢复结果。特别保留观察与 sandbox 工具的既有语义，不把“需要审批”等同“写入”。
2. **Phase 手机验收事实**：修 C2/C3，在 host snapshot 层区分 label 与 value，验收只消费真实可见事实；增加生产投影边界回归。
3. **Phase 列表采集范围**：修 C4，限制真实容器子树，明确父聚合标签与匿名记录的保守处理。列表身份不要另造复杂框架。

各 phase 完成后由独立 subagent 检查本 phase diff、触发链和测试；JVM 定点测试与 app compile 由 root 统一调度，避免多个 Gradle 同时写缓存。设备上的审批→目标应用→真实动作仍是独立验收，不可用 compile 或 fake-controller 测试替代。
