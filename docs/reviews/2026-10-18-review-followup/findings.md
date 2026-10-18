# 追加复核与修复台账

当前基线包含先前审查的 26 组修复。本轮已核对它们的 67 个源码/测试文件和 6 个 APK 摘要，并核对 22 个模块的测试 XML；这些是经过核对的历史结果，不冒充本轮重跑。

以下候选经过审查 agent 的入口核查和主 agent 的二次判定，进入失败回归及修复。详细入口、触发、误报排除与建议验证在六份 audit 报告中。状态将在阶段实际验证和独立 review 后关闭。

| ID | 问题及影响 | 阶段 |
|---|---|---|
| AP1 | Gemini thinking 配置越过模型支持范围，正常请求被拒绝 | 1 |
| AP2 | DOCX 内容控件内正文及表格丢失 | 1 |
| AP3 | EPUB 内联空白和 XHTML 实体丢失，正文串连 | 1 |
| R1 | 通知 Stop 后 dispatch 继续消费排队消息 | 1 |
| R2 | 普通 dispatch 先于 runner 清理释放，旧写入与后继生成并发 | 1 |
| R3 | 置顶记忆被语义排序重复加入，挤掉其他召回 | 1 |
| R4 | Dream 单条多行建议 CAS 失败时留下部分修改 | 1 |
| R5 | 子代理 report 补交回合将审批暂停误报完成 | 1 |
| R6 | 子代理 steer 仅进入 channel 即标交付，未消费消息丢失 | 1 |
| R7 | Jev 排队或重试请求未复核配置，旧认证失败污染新 Key | 1 |
| R8 | Council 排序缓存锚遗漏实际任务与模型能力 | 1 |
| R9 | 历史展开实际正文绕过字符预算，授权扣账和截断提示错误 | 1 |
| BG1 | 本地备份上传给 SAF 的 parent URI 形状错误 | 1 |
| BG2 | 终端 admission 注册中断遗留并发槽及安装门 | 1 |
| BG3 | WebDAV sidecar 删除失败伪报成功，残留条目又无法重试清理 | 1 |
| D1 | DeepRead 失败回写把其他来源的同标题文章绑定到新任务 | 1 |
| D2 | DeepRead URL path/query 被转小写，缓存身份碰撞 | 1 |
| D3 | DeepRead 导入把 parser 诊断当正文；同边界大小与临时文件问题待 RED 结果 | 1 |
| NB1 | Regex 规则缓存只比较 Int 摘要，同 hash 编辑继续用旧规则 | 1 |
| NB2 | Java replacement 被 Rust 引擎按不同语义改写 | 1 |
| NB3 | LaTeX 第二遍转换使用旧代码范围，改写代码原文 | 1 |
| NB4 | Markdown preprocess 未保护波浪号代码围栏 | 1 |
| NB5 | 裸域名 TLD 前缀匹配产生指向错误主机的链接 | 1 |
| NB6 | Native HTML 自动链接改写代码内 URL | 1 |
| NB7 | Native HTML 安全链接替换误改代码及普通文本示例 | 1 |
| U1 | 预检和提交接受后附件归属缺失，可删掉已发送附件 | 2 |
| U2 | 替换 composer 后旧 import 未失效，显示或发送混入旧附件 | 2 |
| U3 | 编辑确认入口绕过 import gate，静默丢失附件 | 2 |
| U4 | Widget action 清掉 edit owner，历史附件失去文件保护 | 2 |
| U5 | MiniApp 固定 JS 超时先于人工确认，报失败后仍执行副作用 | 2 |
| U6 | MiniApp 返回系统模式与 App 配色混合的旧主题快照 | 2 |

原审查明确的 by design、死代码和证据不足项继续保留。本轮未据此删除旧 helper、改变 Reminder 周期、改变终端 detached 契约或新增权限系统。新增排除项详见各 audit 报告。

测试接线编译错误单独修正，不算产品缺陷或失败回归证据。Gradle 由主 agent 串行执行，各阶段完成后交非作者 subagent 独立复审。
