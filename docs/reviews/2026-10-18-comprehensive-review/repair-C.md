# C1–C4 修复记录

对应已二次复核的 `audit-C.md`。修复文件与测试由 C 工作包独占；没有覆盖其他工作包或用户修改，没有运行 Gradle。root 已统一执行修复前失败验证：`ExternalActionEffectPolicyTest` 两个测试失败，`ScreenAutomationAcceptanceTest` 四个测试失败；日志 `/tmp/amber-review-20261018-red-phase1.log`。

## 实际修改

- **C1**：`ToolRegistry` 给已确认的低层手机动作、执行任意命令的 terminal 入口、workspace flush、call_phone / intent_open 显式声明 mutation，效果账本随既有规则登记为 `NON_IDEMPOTENT_WRITE`。这与危险等级/用户审批是不同维度：即使仅打开拨号器或外部页面，重复执行也会再次改变前台、覆盖用户当前操作，不能声称只读恢复安全；`call_phone(direct_call=true)` 还可能再次拨出电话。未把“需要审批”泛化成写入；screen_stop 与观察工具保留既有约定。
- **C2**：`ScreenNode.editableValue` 只保存非密码输入框实际 text，不读取 hint，不按显示 label 截断；值也进入 snapshot hash，保证空框 hint 与真实输入相同字符串仍是不同观察。既有 label/hint 候选定位行为保留。
- **C3**：expectation 使用新 snapshot 中真实可见节点作精确 label 匹配，`absent` 动作前观察使用同一语义。`hasOwnLabel` 区分节点自己的可见文本/描述和为了导航合成的父标签，避免一个真实目标因父聚合标签又被计数一次。原 `screen_find_text` / wait 的 substring 搜索行为保留。
- **C4**：collect_list 只采集唯一目标容器 ref 的严格后代，且只保留有自身 label 的节点，避免同一行的 synthetic wrapper 与真实文本 child 重复占用 item 预算。去重算法、跨屏行身份契约未改，也没有新增推断身份的机制。

`editableValue` 的外发面已逐处核对：`ScreenNode` / `ScreenSnapshot` 均没有 `@Serializable`，生产消费者只有 service、ScreenAutomationTools 与 JevScreenGoalRunner。Jev state、goal visible_text 和 collect items 均手工投影既有截断 label，没有原样 encode / toString(snapshot)。新 value 仅用于本地验收与 snapshot hash，不扩大原始字段的外发长度或 Jev 数据范围。

## 回归与检查入口

- `:feature:tools:api`：`app.amber.feature.tools.ExternalActionEffectPolicyTest`、既有 `ScreenRunGoalPolicyTest`。
- `:app`：`app.amber.feature.tools.ScreenAutomationAcceptanceTest`、`ScreenExpectationTest`、`app.amber.feature.runtime.RunRecoveryServiceTest`、`ToolEffectLedgerTest`、`app.amber.core.jev.JevScreenGoalRunnerTest`。
- 新增实际 ledger 回归 `RunRecoveryServiceTest.interruptedScreenAndCommandActionsUseTheirRealClassificationDuringRecovery`：用生产 `Tool.effectClass()` prepare `screen_click` 和 `terminal_execute`，推进到 STARTED 后执行真实 `RunRecoveryService.recover()`，断言 effect 和 run 进入 OUTCOME_UNKNOWN。
- 新的 acceptance 回归通过公开工具 `execute` 走生产 probe。hint / 长值 / 密码值使用真实 Android AccessibilityNodeInfo 经既有 service snapshot 投影；另锁定隐藏 legacy 搜索节点、子串、父聚合标签唯一性与目标容器边界。
- 本包已做限定 diff 可维护性检查：新增字段均在宿主事实投影层赋值，验收没有复制状态；列表只用已有 ref 边界；效果分类集中在原 owner，未分散到工具实现。
- `git diff --check` 已通过。修复后 JVM 与编译结果由 root 统一补充；尚未声称物理设备审批、前台切换和真实控件动作已验收。

## 独立 review 后的补修

独立 Spec reviewer 指出：仅限制 ref 子树仍会把容器内无自身文本的行 wrapper 聚合 label 与它的真实 TextView child 都当成 item，导致本屏同一行重复，并提前耗尽 `max_items`。这与有争议的跨屏业务身份无关。

先新增 `ScreenAutomationAcceptanceTest.listCollectionDoesNotCountSyntheticRowWrappersAsAdditionalItems`，root 在 `/tmp/amber-review-20261018-red-phase2-ui.log` 取得该测试独立失败、其余 screen 测试通过的证据后，补上已有 `hasOwnLabel` 事实过滤。没有新增身份推断、数据状态或兜底。

## 全量测试中的夹具语义对齐

`final-full-test-first-pass.log:1169` 起以及 `final-full-test-first-pass-failures.json` 保留三个失败：跨步相同 screen 调用应执行四次、同批 screen twins 应执行一次、已收窄 session 的 shell 调用应收到 policy_denied 后进入下一模型轮。它们是本目录内不会被后续 GREEN 覆盖的证据副本。

三者都用真实外部动作工具名构造了 `needsApproval=false` 的假工具，且 session 未授权。C1 修正 mutation 后，既有 invocationPolicy 将其识别为需审批调用；kernel 在审批 gate 正确进入 WaitingUser，因此尚未到达这些测试原本要验证的重复守卫或执行策略边界。实际 screen / terminal 生产工具原先已声明 needsApproval，未出现新增生产审批回归。

只对这三个测试的夹具做语义对齐：假工具声明与实际工具一致的审批要求，各 session 显式授权，以到达被测边界。原来的执行次数、模型请求次数、终态、结构化拒绝及最终回复断言全部保留，没有修改生产代码或全局测试默认值。root 将串行重跑全量 app 测试。

## 未纳入修复

匿名同名行的跨页去重契约有计划/执行记录冲突，只记录，不扩展 C4。scope revoke / screen_stop 在途行为、低层跨 run 工具与截图 observation 绑定均按审查中的证据边界报告，不把推测转换成新增状态机、重试或兜底。
