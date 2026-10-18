# Phase 1 Standards 独立复核

结论：**无阻塞项，未发现本阶段 diff 对仓库标准的硬违反。**

范围：基线 `d983c0b` 的 A1–A5、B2/B3/B4、C1–C4，覆盖对应生产 diff、已修改与新增回归测试。读取根 `AGENTS.md`、用户工作原则及 audit/repair A–C；未读取兄弟产品，未改生产或测试，未运行 Gradle。本报告不能替代 root 的测试、构建及设备验收。

- **职责和数据流（用户规则 4、5）**：Gemini ordinal 保存在单次 decoder；Claude block identity 由 provider 流作用域与协议 index 组成；共享 reasoning/image 合并判断位于消息 owner。wire encoder 只回传协议字段，内部 identity 不出 wire。没有新增框架或跨产品依赖。
- **持久化契约（根 AGENTS 状态 owner 原则；用户规则 5）**：B2 nullable 返回值表达已满足的 invalidation，生产唯一调用点忽略结果；writer 与 Room transaction 仍包围 memory、candidate 与 event 更新。未用虚构记录维持旧类型。B3 scope/source gate 在 recall owner；过期或关闭来源不能借 profile 绕过，复用既有 freshness 契约。B4 直接修正窗口算术，没有新估算器或兜底分支。
- **外部效果与验收（用户规则 3–5）**：C1 在现有效果分类 owner 明列已核实动作，观察工具保持只读。C2 的实际 value 由 Android projection 提供，hint/展示 label 继续负责定位，密码不投影原值。C3 使用可见 snapshot 和精确标签；C4 使用已有 ref 子树，未引入推断身份或重试。
- **验证针对性（用户规则 7、11）**：回归覆盖 wire→provider→accumulator→replay、实际 Android 节点投影、持久化无新增结果与 tight-window 拒绝；正控制保留，未看到删断言掩盖失败。

Fowler smells 仅作 heuristic：共享 metadata/completeness helper 减少容易漏改的判断；既有双消息合并实现、private helper 与小型 version pair 不足以支持额外架构重构。未要求为行数或形式统一改动。

二次复核未接受的疑点：Responses completed 按 reasoning id 去重是否丢 encrypted 内容。常规 added→summary→done 路径的 done metadata 会与同 block 合并；缺少实际 provider 省略 done 的证据，不将推测列作缺陷。独立 Spec review 仍应核对最终行为与原问题契约。

## 最终全量前：三项 kernel fixture 窄复核

无新增阻塞。`DefaultRunKernelTest` 本次只有 12 行新增：screen_swipe、screen_click、terminal_execute 三个 fake 各声明 `needsApproval=true`、`allowsAutoApproval=false`，与真实工具注册一致；各自 session 显式启用 `autoApproveHighRiskTools=true`，以到达测试原本验证的 guard/沙箱边界。生产 `PermissionDecisionResolver` 明确把该设置作为 settings_unattended 授权，不是假造 Approved 状态或绕过生产 resolver。

所有原断言保留，包括跨轮执行 4 次/请求 5 次、同批仅执行 1 次、不误报 pause/guard stop、allowShell=false 时执行 0 次/请求 2 次，以及 structured policy_denied 输出与最终文本。未改生产或放宽被测断言，属于真实审批语义对齐；单独的审批 pause 回归保留。最终全量 GREEN 待 root 实跑。本复核未运行 Gradle。

## 最终验证关闭

本阶段 Standards review 已关闭，无剩余阻塞。已核对 root 的 `final-verification.json`：22 个有 src/test 模块的最终 JVM 门禁退出码 0，3700 用例中 3676 实际通过、24 跳过、0 failure/error；app 为 2920 用例、14 跳过、0 failure/error。三个 kernel fixture 的审批语义对齐已进入此轮编译与测试，原 guard/denial 断言保留。执行证据为 `final-jvm-tests.log`；Native 门禁另有 81 通过、0 失败、1 ignored，见 `final-native-tests.log`。此前审查及失败证据保留。APK 构建仍在 root 进行，安装、运行、真实 provider 与设备行为不由这些测试代替。本 reviewer 未自行运行 Gradle。
