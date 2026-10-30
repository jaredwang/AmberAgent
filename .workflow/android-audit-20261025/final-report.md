# Android 审查与修复交付记录

基线 main@0971aa41ca7a2526e9ee17eb9497b7edc1db07be。**65 组确认问题已完成修复，四个阶段独立复审及最终 JVM、普通配置三款 APK、签名和 Native 产物检查均已通过。** 最终 APK 未安装或启动，设备和真实 Provider 验收单列。

完整问题、触发与影响、源码、独立二次复核、修复状态和排除项见 [findings.md](findings.md)。其中 P1 22 组、P2 43 组；本审查流程负责 49 组，既有搜索流程负责 16 组（功能 14、实际 UI 2）。C2b、R3/U2 同 owner 的追补及 Native 多个断言均按问题组计数，没有把测试失败逐个另算问题。

## 已交付内容与阶段关闭

| 阶段 | 交付内容 | 已取得的验证及独立复审 |
| --- | --- | --- |
| Phase 1 / 20 组 | subagent/审批生命周期、工具真实效果分类、记忆当前任务/去重/导入关系、secret migration/引用、SAF 与终端 mirror、WebDAV、Termux 任务投影 | 各 owner 先 RED 后 GREEN；同目录命名与私有镜像目录移动的阶段追补同样 RED→GREEN，最后 14 项通过。[验证](results/phase1-verification-pre-r3.md)、[runtime/memory/tools 复审](results/review-phase1-runtime.md)、[data/sync/mirror 复审](results/review-phase1-data.md)。阶段已关闭。 |
| Phase 2 / 23 组 | Provider 完整请求取消、Mistral content arrays、Gemini signed Text、key cache、Responses background/replay/created ownership、Native Markdown 及外部搜索 14 组功能闭环 | 完整 AI 257/257、实际 recovery 18/18；native workspace 92 passed，另保留 1 个原有忽略 JNI 文档示例。Responses suffix 重复数据与 raw HTML 边界经有效追补 RED 后关闭。[AI/恢复验证](results/phase2-ai-green.md)、[Native 实测](results/phase2-native.md)、[Provider 复审](results/review-phase2-provider.md)、[协议/Native 复审](results/review-phase2-protocol-native.md)。阶段已关闭。 |
| Phase 3 / 11 组 | Novel targeted rewrite/标题/文件边界/上下文开关、Board 来源/晚间 review、DeepRead 可携带正文、disabled Cron 与 Council mode/question/steering | 真实 generator、Room/store、后台 owner、Council 编排与 UI fixture 回归通过；复审发现的 chat source 和 disabled manual-run startup 投影已补有效 RED→GREEN。[验证](results/phase3-verification.md)、[Novel 复审](results/review-phase3-novel.md)、[后台复审](results/review-phase3-background.md)、[Council 复审](results/review-phase3-council-sidecar.md)。阶段已关闭。 |
| Phase 4 / 11 组 | composer 对称 admission、metadata/message 原子 owner、MiniApp/Web/JSCell 协议及 Jev evidence/完成/截图 scope；外部搜索 2 组实际 UI 修正 | 初版 75 项 GREEN 后复审继续找到 U2 auxiliary/projector/atomic 和 Jev capture 配置间隙，均先有效 RED 再修；最后 U2 受影响 46/46、JevScreen 17/17 GREEN，其他未变更批次结果复用，不把重叠用例相加。[验证](results/phase4-verification.md)、[UI 最终复审](results/review-phase4-ui.md)、[Web/Jev 最终复审](results/review-phase4-web-jev.md)。阶段已关闭。 |

所有修复保持既有 owner、CAS、Room transaction、restore epoch 与产品边界；没有追加来源预算、自动失败重试、通用 merge、权限框架或全局网络/运行时锁。仅在实际复现的 metadata/message 交错处统一既有 StateFlow 原子变换，未重写完整保存与分支替换语义。

一次限定于本轮 71 个自有生产文件的维护性检查已完成，结论为 **no changes warranted**；没有为额外优化再修改代码。搜索流程自己的有限维护性检查另有独立记录。[本轮检查](results/final-maintainability.md)、[搜索完成核对](../search-repair-20261025/results/completion-audit.md)。

## 外部搜索完成证据与实际 UI 边界

既有搜索流程已完成 14 组功能问题及 2 组实际 UI 问题，各阶段均独立复审。相关 JVM/Compose/桥接回归 117 项通过；模拟器实际确认关闭最后来源后进程重启仍关闭、供应商添加/保存/重开/删除、结果数量上下限，以及 320/360dp 大字体滚动、编辑、键盘与按钮布局。[完成核对](../search-repair-20261025/results/completion-audit.md)、[相关测试](../search-repair-20261025/results/phase3-verification.json)、[独立最终 UI 审查](../search-repair-20261025/results/phase4-review.md)。

该流程交付时点的 FullDebug APK 已构建、通过 v2 签名验证，并在任务独占 API34 模拟器替换安装/启动；安装哈希、前台及交互证据有单独记录。[制品](../search-repair-20261025/results/phase4-artifact.json)、[安装与实际 UI](../search-repair-20261025/results/phase4-ui-verification.json)。这些证明搜索交付时点的制品与行为；**本轮最终 APK 为之后生成的新制品，不能用该次安装证明新制品已在设备验收**。未使用真实付费 key，没有物理设备或真实模型完整会话验收。

## 最终集成验证

| 门槛 | 结果 | 证据与边界 |
| --- | --- | --- |
| 21 个有测试模块的完整 JVM | 4098 cases：4075 passed、23 skipped，0 failure/error | [逐模块统计与完整 XML](results/final-jvm-combined-verification.json)。初次全量后校准两个 fixture，再完整重跑 app；其他未变更模块结果复用，Jev 缺失依赖单独补齐校验。没有把失败运行或重叠用例累加为通过。 |
| Rust host workspace | 92 passed、0 failed，1 个原有 JNI 文档示例 ignored | [Native 验证](results/phase2-native.md)。此后 Rust 源未变更，未重复运行。 |
| 普通配置 Full / Novel / DeepRead Debug APK | 三款构建成功，5m12s，共 6 个 APK | [构建日志](logs/final-assemble-normal.log)、[逐 APK 包名、SHA256 和工具记录](results/final-apk-verification.json)。普通配置没有测试 OAuth 参数，也没有 JNI task exclusions。 |
| APK Native .so 与签名 | 6/6 签名验证成功；每个 APK 均有 8 个 required Rust .so，均为 ELF64/AArch64 shared | [产物核验](results/final-apk-verification.json)。证明编译与打包，不代替设备加载和行为验收。 |
| fixture 标记清除 | 全部 APK DEX 与 4 份生成 BuildConfig 均无 amber-audit-fixture | JVM 使用的 mock OAuth 配置已由普通配置构建替换；[逐项核验](results/final-apk-verification.json)。 |
| 工作区与交付状态 | main / HEAD 0971aa41 保持；git diff --check 通过；未提交、推送或发布 | 保留既有搜索及协作改动。最终源码指纹 7a99d306761f2a0e10642c2b8ec842884c9a077e99fae4fd460fd1cee5947661，与最终 JVM 验证后及普通配置构建后相同。 |
| 最终 APK 安装/运行/用户行为 | 未执行本轮最终新制品验收 | 之前的搜索 API34 模拟器记录属于较早制品；真实 Provider、物理设备和 OEM 后台仍未验收。 |

23 个条件跳过分别为：实验性 Markdown AST 对等 12、SSH loopback 8、跨端交换 fixture 1、大包导入 fixture 1、WebMount 页面生命周期 1；未算入 passed。

最终全量过程中有两处 fixture 校准，均未修改生产行为或删减有效断言：队列测试改用生产唯一构造链所用的 JsonInstant，保留精确时间戳与对象相等断言；子代理 citation 测试改用隔离 Koin，只关闭自身实例，保留实际点击和 URL 断言。[队列诊断](results/final-pending-queue-fixture.md)、[UI fixture 诊断](results/final-ui-fixture.md)。

## 普通配置产物

以下为 universal 文件；当前产品仅打包 ARM64。每个文件另有内容相同的 arm64-v8a 命名副本，均已核验。

| 产品 | APK | SHA256 |
| --- | --- | --- |
| Full | [app-full-universal-debug.apk](../../app/build/outputs/apk/full/debug/app-full-universal-debug.apk) | 579587686e094a25a1fcd4b62e8fac25806da06988a541708547d0173ee09f4d |
| Novel | [app-novel-universal-debug.apk](../../app/build/outputs/apk/novel/debug/app-novel-universal-debug.apk) | 8bb48dee18f1cc0ffd914d01d8ae8be61eafe1787b26c0cba9312577af7f81f1 |
| DeepRead | [app-deepread-universal-debug.apk](../../app/build/outputs/apk/deepread/debug/app-deepread-universal-debug.apk) | 015b6f0e49171d6514e7b694e198c69895e34a2105042ab90c2448c4103d6694 |

真实 Provider/付费搜索、实际 WebDAV、Termux/SSH、MediaProjection/WebView/OEM 后台与物理设备未验收。源审查、mock/socket fixture、Rust host 测试、JVM、APK 构建、签名、安装及用户行为分别记录，不互相代替。

完成时间：2026-10-26。详细问题清单保留每项触发、影响、修复和复核证据；本轮不追加额外优化。
