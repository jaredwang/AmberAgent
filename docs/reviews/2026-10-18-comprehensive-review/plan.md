# Android 全仓审查与分阶段修复

## 目标与完成标准
审查当前生产源码与已有 WIP；每个候选问题复核入口、可达性、状态所有权和用户影响。只修复证实的问题，不新增假想兼容、重试或架构。每阶段具备回归证据、独立 subagent review；最后运行覆盖影响面的 JVM 测试、编译/构建并报告未验证门槛。

## 基线与授权
- 根目录：本独立 Android 仓库；禁止读取其他产品仓库。
- HEAD: d983c0b782c6eedce7c826f62003aa26973cc17c。已有 WIP 的哈希与 patch 见 baseline 文件。
- 用户已授权审查、分阶段修复和 subagent review。未授权提交、推送、发布、设备数据重置。

## 审查分工
- A：AI provider、流解析、取消及消息协议。
- B：聊天运行生命周期、消息持久化、记忆与上下文。
- C：Jev、工具执行与权限、子代理与 Council。
- D：Novel / Novel workspace 的状态与持久化。
- E：Compose UI、主题、生成 UI 与已有 WIP。
- F：同步、导入导出、后台自动化及文件边界。
主 agent 复核候选、基线构建和整合。审查者只读，修复者互斥文件所有权。

## 阶段与实际推进
0. 已完成六条责任线的并行审查与二次复核，建立 accepted / rejected / by-design 列表；主 agent 审核生产可达性，接受 26 组问题。
1. Phase 1：A1–A5、B2–B4、C1–C4，共 12 组。取得失败回归后修复 provider/文档、记忆/预算、工具效果和手机验收；独立 Standards + Spec review 后补齐 C4 wrapper 重复计数。
2. Phase 2：B1、D1–D4、F1–F5，共 10 组。修复恢复归属、Novel、Cron、Reminder、备份与终态通知。独立 review 发现 F5，以及 F2 的 REPLACE/真实清理交接遗漏；均先取得失败回归，再修复并复核。
3. Phase 3：E1–E4，共 4 组。修复文档打开、附件编辑、异步发送快照和 selected variant 预取；独立 Standards + Spec review 无剩余阻塞。
4. 收尾：各实施 owner 对本轮 diff 做一次有限维护检查，主 agent 检查跨包接线；执行 22 个模块的完整 JVM 测试、Native 测试与普通配置 APK 构建，核对 WIP 保留情况。精确执行结果见 final-verification.json 和 final-report.md。

最终全量检查暴露的三个旧工具夹具已按实际审批契约对齐，全部原断言保留，未更改生产权限。阶段记录保留 RED、发现、补修及独立复核全过程。

## 验证纪律
先获得失败测试或可观测复现，再修复并验证通过。Gradle 单一串行入口，避免并行构建抢占。测试、构建、安装、启动、真机/provider 行为分别报告。
