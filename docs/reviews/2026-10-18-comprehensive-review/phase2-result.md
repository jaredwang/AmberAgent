# Phase 2 结果

B1、D1–D4、F1–F5 共 10 组已完成生产修复、失败回归、补修与独立复核。

- 普通 resume 使用实际 activation 屏障；Cron 按捕获的 runId 等待真实终态，取消覆盖启动前、Main 返回交接、审批及 timeout。
- 独立 review 发现 F5 结果通知 ID 冲突，并进一步查明 F2 的 WorkManager REPLACE 不等待旧 Worker、快速 CANCELLED 早于实际 Job 与持久化清理。分别取得失败证据后补齐 taskId 执行锁、真实 runner settlement 和 dispatch 归属交接；未 join 后继用户 run。
- Reminder 在已有 scheduler 锁内重读 durable owner；restore journal 在启动消费者前恢复，保持既有 DB marker、文件 gate 与 Novel bridge。Novel 导出只在已有事务 owner 内读取 snapshot；伏笔状态投影和 planned 上下文已修复。DeepRead/Cron 结果通知均独立于前台清理 ID。
- session 收尾复用已有引用，避免 cleanup 后重建。独立 Standards 与 Spec reviewer 已复核全部最终接线，无剩余确认阻塞。
- 最终 22 模块统一 JVM 检查通过；app 2920 项中实际通过 2906、跳过 14；真实 InProcess runner 24 项全部通过；Novel 49 项中 48 通过、1 跳过，Novel workspace 158 项中 157 通过、1 跳过。没有用跳过项或模拟 cleanup 代替真实 runner 检查。

证据：phase2-ui-red-evidence.json、cron-followup-red-evidence.json、cron-actual-cleanup-red-evidence.json、final-full-test-first-pass-failures.json、final-verification.json。两个独立 review 报告保留原问题与逐次补修结论。

JVM/Room/文件事务与受控 coroutine/NotificationManager 验证不等于真实设备的后台保活、通知权限、闹钟、系统杀进程及外部 provider 验收。
