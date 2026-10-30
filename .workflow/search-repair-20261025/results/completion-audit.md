# 搜索审查修复完成核对

范围：本 Android 独立产品根的搜索设置、供应商协议、工具聚合及现有消费者。基线 main 0971aa41ca7a2526e9ee17eb9497b7edc1db07be；未提交或推送。保留同工作区另一 Android 审计的修改。

原15项二次复核：14项真问题已修复；来源预算项收回为设计。每个 phase 均由独立 subagent 审查，发现的接缝已关闭。

| 保留问题 | 修复及验证闭环 |
| --- | --- |
| 显式空启用列表被回填 | 区分缺 key 与显式空；退休 selected 清理保持禁用；真实迁移→落盘→fresh reader 回归，UI 重启后仍关闭 |
| Brave 认证头进入日志 | x-subscription-token 同时接入请求记录与 debug logger 的公共脱敏集合；真实 helper/logger 测试 |
| SearXNG 旧 count 字段强制解码 | 仅将已废弃 number_of_results 设默认值；原始响应 fixture |
| Serper/SerpAPI 新闻接线 | topic 传至真实 adapter；保留时间 query hints；请求参数回归 |
| Tavily 时间过滤参数 | recency_days 计算 start_date 并优先，合法 time_range，保留原 query；实际 HTTP fixture |
| Firecrawl 多源优先级 | 按请求 source 顺序映射并执行总 cap；映射回归 |
| 已知日期丢失 | 映射供应商原始已知字段，Jina publishedTime 优先否则 date；不猜测日期含义或新增解析启发式 |
| 智谱自动查询超长 | 跳过超长自动变体；原 query 不截断，adapter 明确校验70字符；实际参数回归 |
| 正文阶段无法及时取消 | continuation 保持到 body 读取完毕，底层 Call.cancel；Exa/智谱接入；真实延迟 socket 三项回归 |
| provider answer 被丢弃 | answers[] 独立承载来源及原查询；answer-only 状态正确；MiniApp 保持仅页面投影、空列表为 empty；真实桥接测试 |
| URL/标题身份错误去重 | 有 URL 按完整规范 URL 身份，保留路径/查询大小写及显式端口；tracking 合并保留；Free URL 路径处理；RED→GREEN 与双聚合器回归 |
| 配置型 Bing CAPTCHA 误报 | 先解析结果，仅空结果判验证页；保留真正验证页/空页异常；实际 parser 回归 |
| DeepRead 忽略内置开关 | 根据最终六源前缀补齐允许能力，自动 keyless Reader 仅抓取；现有配置优先级与六源上限不变；关键截断边界回归 |
| 子代理引用无法点击 | 实际 Markdown sheet 接入 liveParts citation handler；实际 Compose 点击测试 |

收回项：五来源/十个来源变体调用预算与配置优先级是现有契约。未新增第六来源、预留免费槽或失败重试；仅修正工具描述的并发/优先级说明。MiniApp 独立 adapter 保留。

## 分阶段完成证据

- Phase0：独立复核，phase0-revalidation.md。
- Phase1：独立 review；选择持久化6、Helpers13、迁移30、日志2通过。审查发现的退休选择迁移接缝已修复；实际关闭/重启 UI 补齐。
- Phase2：独立 review；协议、参数、日期和完整正文取消定点通过。审查发现 Jina date 字段后补 fixture 并重跑。
- Phase3：独立 review；MiniApp answer-only 投影、DeepRead 最终前缀能力等接缝已关闭。最终相关 JVM117项，14份报告，0失败/错误/跳过，保存于 phase3-tests；不以此宣称全仓测试全绿。
- Phase4：整合及一次有限维护性审查通过。实际 UI 额外确认并精准修复滚动 inset 与底部按钮中心两个问题，最后独立视觉复核通过。只新增两处页面布局调整，重跑受影响 assemble 与实际 UI；未重复无关 JVM。

## 产物与真实验证边界

最终 :app:assembleFullDebug BUILD SUCCESSFUL，日志 logs/phase4-final-apk.log。apksigner v2 验证通过。应用 app.amber.agent.graphite 2.6.8(396)，最终 APK SHA256 130fdf0b21a011486496013dcf2834df07e154b4778a3402e1094117da19ad62。

Android34独占只读模拟器替换安装、启动、实际搜索设置交互与320/360dp大字体截图分别有记录；安装文件哈希与 APK 一致，RouteActivity 已恢复前台，无 crash buffer。设备操作及证据见 phase4-ui-verification.json，独立审查见 phase4-review.md。

没有消耗真实付费 key，没有物理设备和真实模型完整会话验收。HTTP fixture、延迟 socket 与模拟器验收只证明各自覆盖的链路。离线初次打包缺 Guava Android 缓存，通过在线 Gradle 下载解决，未变更依赖或 packaging 规则。
