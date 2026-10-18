# 已确认问题与修复台账

基线为 d983c0b 加 baseline.json 记录的既有 WIP。下表是源码和调用链二次复核后接受的问题。修复前执行结果、阶段 review 与最终状态持续记录；源码确认不代表设备验收。详细入口与证据见 audit-A 到 audit-F。

最终状态：以下 26 组（P1 8 组、P2 18 组）均已完成生产修复与相关回归；三个阶段的独立 Standards / Spec review 均无未关闭阻塞。统一 JVM 检查为 3676 项实际通过、24 项跳过、0 failure/error，Native 为 81 通过、1 ignored。普通配置 APK 与基线保护的最终证据见 final-verification.json；设备及真实 provider 验收边界见 final-report.md。

| ID | 级别 | 触发和实际影响 | Phase |
|---|---|---|---|
| A1 | P1 | Gemini 独立 functionCall 跨 SSE event 共用 index，合并成坏工具名和参数 | 1 |
| A2 | P1 | 空 opaque reasoning 被 snapshot 丢弃，相邻 thinking 块边界被合并，续轮回放损坏 | 1 |
| A3 | P2 | 跨 event 完整图片或 URL 被当片段拼接，第二张丢失 | 1 |
| A4 | P2 | PPTX 双 id 属性取错关系，fallback 数字排序也取错 basename，页序失真 | 1 |
| A5 | P1 | 旧 Claude reasoning 模型被一律发送 adaptive，正常请求 400 | 1 |
| B1 | P1 | 普通同 run 恢复读取旧 WAITING_USER，提前释放 session 与 Stop 归属 | 2 |
| B2 | P2 | 接受已删除目标的失效候选反而插入新 active 记忆 | 1 |
| B3 | P1 | 关闭记忆 scope 后已有画像仍注入模型提示词 | 1 |
| B4 | P2 | 硬预算 4000 下限超过模型实际余量，极小窗口抛错 | 1 |
| C1 | P1 | 手机底层动作及已核实终端命令误记 READ_ONLY，恢复可能重复外部效果 | 1 |
| C2 | P2 | value_equals 把 hint 标签当字段实际值，空框假通过 | 1 |
| C3 | P2 | visible/enabled/absent 验收消费隐藏节点或近似子串，验收假通过 | 1 |
| C4 | P2 | collect_list 按 y 范围采集，混入并排列表或父容器内容 | 1 |
| D1 | P2 | planned 未回收伏笔从生成上下文消失 | 2 |
| D2 | P2 | Novel 导出跨文件读取不持已有事务边界，可能混合章节、剧情及 ledger 版本 | 2 |
| D3 | P2 | DeepRead 终态通知与 WorkManager 前台通知共 ID，返回时被取消 | 2 |
| D4 | P2 | 批准伏笔文件的 kind 及 status 未正确投影，目录和回收状态不生效 | 2 |
| E1 | P2 | MiniApp 文档允许 https，点击却强制 toFile，抛异常 | 3 |
| E2 | P2 | 编辑原本无 Text part 的附件消息时，新输入文字丢失 | 3 |
| E3 | P2 | 发送预检挂起时可重复提交同一快照，完成后可能清掉后来输入 | 3 |
| E4 | P2 | 图片预取遍历节点所有隐藏 variants，额外请求未展示内容 | 3 |
| F1 | P2 | Cron 把 dispatch 结束当成功，失败或待审批也发成功通知 | 2 |
| F2 | P1 | Cron 取消或超时不停止其独立生成，模型与工具继续执行 | 2 |
| F3 | P2 | Reminder 旧 snapshot 延迟排程覆盖新状态，旧 fireKey 被拒后漏发 | 2 |
| F4 | P1 | 备份 restore journal 仅下次 restore 恢复，普通冷启动可读到中断文件树 | 2 |
| F5 | P2 | Cron 终态提醒与前台通知共 ID，WorkManager 清理后结果提醒消失（阶段独立 review 发现） | 2 |

## 分阶段计划

1. Phase 1（12 组）：provider 与文档、记忆与预算、工具效果与手机验收。先运行失败回归，再按 owner 精确修改、定点验证并独立 review。
2. Phase 2（10 组）：普通 resume、Cron、Reminder 与备份恢复、Novel 与 DeepRead。复用已有锁、CAS 和 journal，保证 run 或文件事务归属；定点验证并独立 review。F5 为独立 review 补充接受项。
3. Phase 3（4 组）：文档点击、编辑与发送输入、预取 selected variant。可控挂起验证快照归属，保护已有 WIP；定点验证并独立 review。
4. 对本轮 diff 做一次有限可维护性检查，执行相关模块全量 JVM 测试与普通配置 APK 构建，核对基线 WIP，报告未验证门槛。

## 明确排除项

- 主题包后手动改色：真实设置 owner 已清除活动包，排除初始误报。
- collect 匿名同名行去重：已有契约与执行记录矛盾，缺少业务身份；不据此增加推测性去重系统。
- Novel 分支 plot stale 重置、workspace 镜像保留额外文件、终端只标 INTERRUPTED：已有明确 by design 与测试依据。
- 当前生产未引用的旧 WebDav/S3 restore helper：只标记，不列可达缺陷，不删除。
- Jev shadow 同样外发、in-flight scope 撤回不打断当前工具、低层工具逐次审批：当前契约明确，不擅扩权限框架。
- Markdown 图片 regex 边缘、跨 provider 历史隔离、截图包绑定等：无足够端到端失败证据，本轮不接受。
- 两项 Antigravity mock 基线失败按已有客户端配置门禁复核，不修改生产配置以让测试通过。
