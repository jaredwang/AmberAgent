# Phase 1 独立行为复核

基准：`d983c0b` 与 Phase 1 当前 diff；范围 A1–A5、B2–B4、C1–C4。未参与实现，只读检查生产代码、调用者与回归 fixture；未运行 Gradle，执行结果以 root 验证记录为准。

## 初次结论：需要修复

### P2 / C4 尚会把列表内聚合父节点重复计为条目

- 位置：`app/src/main/java/app/amber/feature/tools/ScreenAutomationTools.kt:954-969`；事实来源 `app/src/main/java/app/amber/core/automation/AmberAccessibilityService.kt:471-493,531-545`。
- 触发：目标列表下存在无自有文本的 row ViewGroup，内部 TextView 有“Target row”。真实快照会为父 wrapper 合成同一 label，并同时输出父、子节点。新的 ref 后代过滤会接受两者；`max_swipes=0` 也产生两条 item，重复计数并提前耗尽 `max_items`。
- 二次复核：这不是已排除的匿名同名跨屏去重争议；同一快照内有明确树结构和 `hasOwnLabel=false` 的合成来源。容器外节点污染已消除，列表内部 wrapper 污染仍可达。应复用现有字段排除纯合成 wrapper，并补嵌套 fixture；无需新增业务身份系统。
- 状态：root 已接受，交由 owner 补失败回归后修复；此处保留初次发现，待补丁复核。

## 其余检查通过

- A1 的 Gemini index 改为 decoder 生命周期递增，跨 event 调用不再撞号；真实 id 与无 id 的 SSE 回归经过 provider、accumulator 与续轮编码。
- A2 保存空 opaque reasoning，并用 Claude 流作用域和 block index 保持边界；signature 增量合并、跨轮 index 复用已有回归。Anthropic wire 只输出合法 signature/data，内部 identity 不外发；Responses 的加密空块也经过 snapshot 与 replay 检查。
- A3 完整 data URI/HTTP URL 保持独立，显式 image identity 的片段处理保留。A4 使用 r:id namespace 和 basename 数字排序。A5 为旧 Claude 生成 manual budget，预算低于实际 max_tokens，旧型号不发送不支持的 effort；测试主要验证共同 request adapter，真实收费 provider 尚未验收。
- B2 的缺失 invalidation 目标不再插入记忆，nullable 结果与唯一生产调用者兼容；B3 在 scope 和画像来源边界阻止已禁用事实注入；B4 的预算不再越过实际窗口余量。
- C1 的真实动作分类经过 dispatcher → ledger → recovery 接线复核，新增恢复回归消费实际分类；观察工具保持 READ_ONLY。C2 使用非密码实际 value，未见新增字段进入 Jev 或工具输出。C3 的动作前后验收都读取可见 snapshot，精确匹配自有 label，未改变既有 find/wait 的宽松搜索契约。

未确认其他行为缺陷。Android 节点投影/fake-controller 与 mock SSE 不能替代真实 provider、设备动作与审批体验验收。

## C4 补丁复核：通过，代码问题关闭

`ScreenAutomationTools.kt:954-955` 现仅采集目标容器内 `hasOwnLabel=true` 的非空节点，精确排除由子节点合成 label 的 wrapper。新增 `listCollectionDoesNotCountSyntheticRowWrappersAsAdditionalItems` 使用两组 wrapper/child、`max_swipes=0`、`max_items=2`，通过真实工具 execute 断言返回 First row、Second row，能够检出重复计数挤掉第二行的原始影响。已核实 `/tmp/amber-review-20261018-red-phase2-ui.log` 中该回归在补修前失败。

补修未改变 rowKey、previousScreenRows、相邻屏合并或 no_progress 规则，保留原跨屏去重契约；未新增身份推测或兜底。独立行为 review 最终通过，当前无未关闭的确认问题。补修后的 GREEN 正由 root 执行，本文不把尚未结束的测试描述为已通过。

## 全量回归 fixture 收口：审查通过，重跑待 root 完成

DefaultRunKernelTest 仅对三个既有 screen_swipe、screen_click、terminal_execute fixture 增加 needsApproval=true、allowsAutoApproval=false，并在其 session 显式授权 autoApproveHighRiskTools。已核对实际 ScreenAutomationTools/TerminalTools 注册具备相同审批标记；PermissionDecisionResolver 把该高风险开关明确定义为无人值守执行授权。因此这使 guard 与收窄沙箱测试穿过独立审批门，符合生产可达路径。全部原执行次数、请求次数、终态、去重和 policy_denied 输出断言保留；该测试 diff 只新增 12 行，未删除断言、未改生产审批或保护逻辑。未确认借 fixture 调整绕开真实回归。

## 最终验证关闭

已读取 root 统一执行的 final-verification.json 与 final-jvm-tests.log：22 个有 JVM 测试的模块合计 3700 项，0 failure/error，24 skipped，即实际通过 3676 项；app 2920 项、0 failure/error、14 skipped。三个 fixture 最后收口与 C4 补修均已进入此轮编译和测试。Phase 1 行为审查与回归验证关闭，无未关闭的确认问题。Native 的独立结果为 81 passed、0 failed、1 ignored。普通配置 APK 构建仍由 root 执行；本结论不代表安装、运行、真实 provider 或物理设备验收。
