# Android 广泛审查与精准修复

本轮确认的 26 组生产问题（P1 8 组、P2 18 组）均已完成修复、回归与三阶段独立 review；最终 JVM、Native 和三个产品面的普通配置 Debug APK 构建通过。本次确认范围内没有未关闭的修复或 review 阻塞。

## 范围与基线

审查本独立 Android 构建根的当前生产源码、已有 WIP 与实际测试。基线 HEAD 为 `d983c0b782c6eedce7c826f62003aa26973cc17c`，分支 `fix/issue-21-tail-growth-crash`。启动时已有 8 个 tracked 修改、3 个 untracked 文件；原始摘要在 baseline.json，差异在 baseline.patch，原文件副本在 `/tmp/amber-review-20261018-initial-wip`。

按 provider/搜索/文档、聊天运行与记忆、工具/Jev/手机自动化、Novel/DeepRead、Compose 与生成 UI、备份/调度/WebMount/终端六条责任线审查。候选必须核对入口、可达触发、领域 owner 与持久化/副作用，再纳入修复。没有读取兄弟产品源码，没有提交、推送、创建 PR、发布或安装设备。

## 问题与阶段

本轮接受 **26 组**生产问题，完整清单与分阶段范围见 [findings.md](findings.md)，具体证据在 audit-A 到 audit-F、repair-A 到 repair-F 的记录中。

| 阶段 | 范围 | 独立 review |
|---|---|---|
| Phase 1 | A1–A5、B2–B4、C1–C4：provider 数据完整性、文档顺序、记忆与预算、工具效果和手机验收 | phase1-standards-review.md、phase1-spec-review.md |
| Phase 2 | B1、D1–D4、F1–F5：恢复归属、后台任务、备份、小说与终态通知 | phase2-standards-review.md、phase2-spec-review.md |
| Phase 3 | E1–E4：文档点击、附件编辑、发送快照和图片预取 | phase3-standards-review.md、phase3-spec-review.md |

独立 review 实际促成三类补修：C4 合成 wrapper 重复占用列表条目；F5 Cron 终态通知与前台清理冲突；F2 同任务 REPLACE 与真实 run 清理交接。三类补修均有修复前实际失败证据。F2 的取消快照并不代表真实清理结束，因此收尾等待放在持有 Job 的 runner，未用快照或 DB 中段写入冒充完成，也未等待可能处理后继用户消息的整个 dispatch。三个阶段最终 Standards 与 Spec review 均已通过，没有未关闭的确认问题。

## 排除项与范围控制

- 主题包后手动改色：真实设置 owner 已清掉活动包，初始怀疑为假阳性。
- Novel 分支 plot stale、Reminder 固定 epoch 周期、workspace 镜像保留额外文件、终端 detached/INTERRUPTED：有明确产品契约与测试依据。
- collect 匿名同名跨屏去重：计划与执行契约不一致，缺少稳定业务身份，不额外设计猜测去重。
- 未被当前生产引用的旧 WebDav/S3 helper 只标记，不删除死代码。
- Jev shadow 外发与 in-flight scope 行为按当前明确契约保留；未新增权限框架。
- Markdown regex 边缘与截图绑定等疑点没有取得足够端到端证据，不混入确认问题数量。

修复复用已有 CAS、Room 事务、restore gate/journal、文件事务边界与 coroutine owner；没有增加依赖、通用重试或兼容框架。每个实施包对自有 diff 做一次有限可维护性检查，主 agent 检查跨包接线与基线保护。

## 验证记录

修复前失败与阶段证据：phase1-red-evidence.json、phase1-green-evidence.json、phase2-ui-red-evidence.json、cron-followup-red-evidence.json、cron-actual-cleanup-red-evidence.json。后续最终验证以 final-verification.json 为准。

普通配置最初有两项 Antigravity OAuth mock 用例被空客户端配置门禁阻挡；使用仅本次测试命令的合成配置后通过，未修改生产配置。正式 APK 单独用普通配置构建。

最终 JVM 检查覆盖本仓所有有 `src/test` 的 22 个模块：3700 项中实际通过 **3676 项**，24 项按既有条件跳过，0 failure/error。app 为 2920 项中 2906 通过、14 跳过；真实 InProcess runner 的 24 项全部通过。未变模块由 Gradle 复用有效结果，发生最终接线/夹具变更的 app 已完整重跑。逐模块统计及跳过原因在 final-verification.json，执行日志为 final-jvm-tests.log。

24 项 JVM 跳过包括默认关闭的实验 Markdown AST 对等检查 12 项、SSH 实连夹具 8 项、跨端夹具 2 项、真实 Novel 大包 1 项及 WebMount 页面回调 1 项。没有读取兄弟产品来补齐跨端夹具；这些跳过不能算作通过。

Native 执行 `cargo test --workspace --locked --offline`：**81 passed、0 failed、1 ignored**；忽略项为 `jni-common` 日志初始化文档示例。日志为 final-native-tests.log。

最终全量检查最初暴露三个过时工具夹具：它们用真实外部动作工具名构造无需审批的假工具，修正效果分类后停在了既有审批门。对齐真实工具的审批声明、显式授权以到达原被测边界后，所有原执行/去重/沙箱断言保留并通过。两位独立 reviewer 已复核；RED 副本为 final-full-test-first-pass.log 和 final-full-test-first-pass-failures.json。

普通配置执行 `:app:assembleFullDebug :app:assembleNovelDebug :app:assembleDeepreadDebug` 成功；没有传入合成 OAuth 值。三个产品面各生成 arm64 和 universal 两个 APK，共 6 个：

| 产品面 | arm64 APK |
|---|---|
| Full | [app-full-arm64-v8a-debug.apk](../../../app/build/outputs/apk/full/debug/app-full-arm64-v8a-debug.apk) |
| Novel | [app-novel-arm64-v8a-debug.apk](../../../app/build/outputs/apk/novel/debug/app-novel-arm64-v8a-debug.apk) |
| DeepRead | [app-deepread-arm64-v8a-debug.apk](../../../app/build/outputs/apk/deepread/debug/app-deepread-arm64-v8a-debug.apk) |

6 个 APK 均通过 `apksigner verify`，包含全部 8 个 required `.so`，不含已移除的 tokenizer `.so`。普通配置生成的 ai BuildConfig 与每个 APK 的 dex 均不含测试 OAuth 标记；各产物的大小、SHA256、包名和版本记录在 final-verification.json，构建日志为 final-assemble.log。中间任务名或测试夹具编译失败不算作产品问题或通过证据。

本轮源码与测试变化的精确文件清单为 change-manifest.json，独立 patch 为 task-changes.patch，二者均排除了初始 WIP。

最终核对：原有 11 个 WIP 文件 SHA256 全部匹配，分支和 HEAD 未改变，任务源码与清单摘要一致，`git diff --check` 通过。修改留在当前工作区，没有提交或推送。

## 外部验收边界

JVM、Robolectric、受控 SSE/Job/Room 测试不能替代真实收费 provider、物理设备上的无障碍动作、OEM 通知与后台保活、真实账号/Keystore/SAF 环境及完整视觉验收。本轮未安装或启动设备应用，也未进行外部发布。
