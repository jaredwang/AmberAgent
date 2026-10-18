# 审查工作快照

用户在追加审查和修复进行中要求提交、推送。本次保存当前功能分支上的累计审查工作，提交性质为 WIP，不代表追加问题全部修复完成。

## 提交范围

- 包含 comprehensive-review 记录的 26 组既有修复、追加审查的 31 组问题台账、已经写入的局部修复、失败回归和测试接线。
- comprehensive-review/baseline.json 记录的 11 个原始 WIP 在提交前 SHA256 全部保持一致。其中 TwoPaneScene.kt 的 18 行布局定义已被本分支 HEAD 的聊天页面引用，TwoPaneLayoutTest.kt 包含对应回归；两者作为当前分支的直接编译依赖纳入。其余 9 项无关 WIP 不提交，全部原始文件内容均未改写。
- 构建日志、崩溃/replay 日志、baseline.patch 和 task-changes.patch 留在本机，不随提交推送。报告中的这些路径及 `/tmp` 路径属于本机证据，克隆仓库后不保证存在。
- 当前分支为 fix/issue-21-tail-growth-crash，远端为 origin。没有合入 main、创建 PR、发布版本或安装设备。

独立提交范围 review 发现上述布局定义只在本地 WIP 中，排除它们会继承 HEAD 的缺失符号。该依赖例外已向用户说明；没有因此纳入其他主题或输入框样式改动。

## 完成状态

首轮 26 组修复的历史验证记录位于 ../2026-10-18-comprehensive-review/final-report.md。追加审查开始时，67 个源码/测试文件和 6 个 APK 摘要与记录相符，22 个模块的测试 XML 统计也吻合；该历史结果不能套用到本次追加修改后的完整树。

追加问题尚未全部修复。Gemini thinking 请求配置、DOCX 内容控件读取、Jev 配置 revision 检查和 runner 当前 activation settlement 边界已有局部实现；EPUB 修复仍有已知失败。其他追加项主要是问题证据、RED 测试及保持原行为的测试接线，不能标为关闭。阶段独立复审尚未完成。

用户要求提交时，上一条 app RED 命令在编译阶段被交互中断，没有取得 app 新增回归的完整结果；不能把旧的 app 测试 XML 当作此次运行结果。Native 与 MiniApp 的新增失败回归已实际暴露问题，但对应生产修复尚未完成。

提交前最后一次定点验证及退出码记录在 delivery-checks.json。此次没有对追加修改后的完整树重新完成全量 JVM、Native 或三个产品面 APK 构建，也没有设备、真实 provider 或完整 UI 行为验收。

## 后续入口

从 findings.md 的 Phase 1 接续：按各 audit/repair 记录完成已接受问题，先运行尚未执行完的 app RED 回归，再逐包修复、验证；每个 phase 后安排非作者 subagent review。Phase 2 的 composer/MiniApp 仍需实际生产修复。不要以本次 WIP 提交推断阶段已经通过。
