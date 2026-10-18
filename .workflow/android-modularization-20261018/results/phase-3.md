# Phase3：Novel、聊天输入与渲染

最终状态：PASSED_WITH_EXPLICIT_EXTERNAL_GATES。最后 Manuscript 标题已真实红→精修→相关8项绿，最终同APK实际320大字及411完整route均通过，最后增量已独立亲图复核。Phase3正式关闭，继续Phase4。

## 实际职责边界

- Novel：平台无关作者编辑/提交/提案/恢复等真实 owner 迁入既有 `feature:novel-workspace`；Android Runtime 保留入口，Jobs/CAS/restore monitor 不复制。Ghostwrite 分为具体生成、审核、章节与批次工作流。VM 保留唯一 UI 状态、admission 和 lifecycle，查询/历史/交互/审计有实际协作者；页面按 panes 收窄状态与回调。
- Chat：Page 仍拥有唯一 VM/Input/SubmissionOwner/LazyListState，Scaffold、ComposerHost、TimelineHost 等直接被默认生产入口调用。附件导入、picker、crop 与 usage 各守明确责任，草稿归属、被接受附件与迟到结果沿同一 token/owner 判断。
- Timeline/Markdown：唯一列表及 anchor/effect owner 保留；计划、缓存、prewarm、搜索/选择/展示分工。Markdown 的文档/解析缓存/流式收集/AST/HTML 两路径分别具体实现，不混用原合同。JNI 默认开关和 ABI 保留。
- Widget/Sandbox/MessageTools：三个 runtime 分别保有原实例/ready/height/security/dispose；Dialog 借用 native handle。Sandbox 当前 load 与 last-good 缩略图职责分离。消息工具展示仍接普通与 virtual 两条真实生产消费链。

原始13个大文件都已按职责实施，处置记录以 `production-scope.csv` 为准。典型入口物理行数：Markdown3674→109、NovelPage2831→618、ChatPage2600→407、WidgetCard2363→324。NovelVM2450→1564 的64行超预算暂留到Phase6明确复核：唯一UI状态/admission/lifecycle内聚性优先，不靠压缩空行或把同一owner搬成扩展函数凑数。

## 已证实并精准处理的问题

1. Input 原有验证/接受/替换草稿归属失败，以及未跟踪附件验证期间删除的真实竞态；State/SubmissionOwner 保持单一归属，迟到导入不污染新草稿。
2. READY 私有副本被真实 Page 历史编辑/排队移动替换后遗留：State 返回确实释放的本地URI，VM 交原 FilesManager 清理；保留或历史引用不被扩删。
3. 普通附件和长粘贴在 `withContext(NonCancellable+IO)` 返回取消父上下文之前丢失外侧赋值，已落盘副本无法清理。真实流/Room DAO 门控先得到实际文件泄漏红态；仅把局部 handle 赋值放回既有块内，范围与取消传播不变。
4. AuditSession 提取时把原嵌套 NonCancellable→IO 合并，导致已保存报告被返回时取消误报失败。这是本轮新回归，已恢复原嵌套边界，原VM24项通过。
5. Worker 最后一章Polish进度已满、pointer未补时提前完成；真实 Worker 恢复红态后，在同一 epoch/job/execution monitor 内补原pointer repair再完成，不调用provider。
6. Sandbox 把last-good URL混入当前load身份；真实Store/presentation红态后仅移除此匹配，缩略图回退保留。
7. Widget父Dialog与具体runtime重复destroy；真实计数红态后只让runtime拥有destroy。归档canonical资源映射与VChart bundled URL漏接分别在原具体策略中补齐，未放宽网络默认。
8. VChart sandbox向数值尺寸API注入`'100%'`，真实native容器正常但canvas宽0；设备尺寸与PixelCopy先红，仅去掉错误缺省注入并使显式`'100%'`走原autoFit。修后真实canvas和A/B柱形可见。
9. Kotlin/Rust预处理既有代码围栏、偏移和hostname尾界错误均按已有真实断言修正，保JNI/prefs/ABI。
10. 实际320dp/font1.5的Editor动作被固定比例与高度挤压；Cancel/Save改自然测量、title独占剩余宽度、header/专用Save使用原值最小高度。全局字号/QuietButton不动；真实320/411全文链已通过。
11. 独立亲看完整320图发现Manuscript Contents逐字竖排。真实320标题单行回归先红、默认411绿；仅将标题和原动作整组改为自然换排的FlowRow。新Header2、Editor2、原Pane4共8项通过，最终实际320/411完整route均绿，最后增量已独立亲图关闭。

新测试夹具的错误另行纠正，没有算成产品缺陷或放宽业务断言：ContentResolver API34实际重建AFD使自定义read latch失效；Compose手动clock仅应用在active streaming；静态滚动恢复自动clock；水平表格容器需先滚入外层竖向视口；Text语义合成paragraph需与实际node统一坐标；IME decoration spans不属于正文字符串合同；Dialog同名Create须限定真实窗口。两次root诊断停止的task-only instrumentation已保存JDB与abort记录，不能报成产品自行崩溃。

## 实际验证及来源

- 完整候选 `phase-3-final-candidate-behavior-and-build`：67类459项中其余450项通过；Importer原4失败来自无效暂停fixture，之后以真实流重建夹具并加入长粘贴取消回归。
- `phase-3-importer-cancellation-green`：Importer10、State13、Submission13、Warning3、HostDraft4，新鲜43项0失败0跳过；真实copy/paste取消副本、managed row和草稿归属都核对。该轮FullDebug app assemble通过；独立输入增量review已关。
- 纯Novel模块新鲜24类158项：157通过，1项需AMBER_W15_ROOT和真实iOS导出树而跳过；没有读取兄弟产品仓或冒称跨端通过。
- Rust真实cargo20项通过；改变后的native so已实际打包并加载。
- 最后Editor响应布局：`phase-3-novel-editor-responsive-import-fixed` 新Layout2与原PaneInteraction4共6项通过，FullDebug app/test组装并安装成功。最终对应 main APK `10fe4bf3a6cac6ee0634fa611856ac4cd0b66c9921c497b169de3a2622fe95e4`，详见安装JSON。之后Manuscript的最终APK为 `96a9405d6679d3b97df442337c00151c0c5d2475fc8f32060d94eaccdddce883`；`phase-3-novel-manuscript-responsive-green` 相关8项0失败0跳过并app/test组装、安装通过。

测试轮次重叠，不把不同轮次重复项相加成所谓新全量通过。冻结源码见 `phase-3-verified-source-snapshot.json`；真实Gradle命令、XML、APK哈希和设备命令保存在各 `evidence/` 子目录。

## 设备UI与生产链

- Sandbox：真实WebView hidden→expanded→return→background→dispose与Terminal窄大字sheet，2项设备通过，独立亲看稳定图及语义；只用稳定sheet作最终画面。
- Widget：Safe payload/lifecycle、Guizang2/2、Slides第二页、真实Card streaming→final→Dialog→close→action均通过；native实例1→2→1、单次detach/action有实际计数。最终VChart尺寸/PixelCopy断言真实红→绿；各图均独立亲看。未知HTTPS夹具只证明真实client策略返回空，不冒称浏览器实际联网负向验收。
- Markdown：411默认字与320/font1.5完整组件方法均通过且独立看图。真实列表/表格/两种代码围栏、旧inline HTML字面与trueHTMLblock分流、新HTML样式、两批追加及final旧块/新表格/尾部、citation和capsule回调完整。active raw帧只声称当帧已显示内容，代码水平滚动不是内容丢失。
- Chat：`phase-3-real-chat-route-regression-411` 最新生产Page/Input经真实Koin与本地SSE完成发送/排队/Stop/FIFO/Room、HOME同Activity/同run、ask_user同run恢复，两项通过且当期IME/队列/审批图独立复核。
- Novel：`phase-3-novel-responsive-complete-320-large-font` 与 `phase-3-novel-responsive-complete-device-411` 均真实Home→Projects→默认KoinVM完整route通过：正文/ledger保存、真实新branch/activeSlug/分支独立保存/切回主线、材料保存重开。各50份当期证据和5commit/三saved/cleanup全true。Editor与320目录标题均已精修；最后 `phase-3-novel-final-route-320-large-font` 18.245s及 `phase-3-novel-final-route-device-411` 18.637s各完整1项通过0skip，最终标题与动作在两视口的独立像素结论均已关闭，行为和视觉分别实证。
- 最后一轮global320配置记录 `phase-3-novel-responsive-320-configuration.json` 精确恢复wm/density/font；第一次原font key absent被Android归一默认1.0的raw差异另录，不冒称raw完全一致。没有新增权限、卸载或清用户数据。

## 独立审查与明确边界

`phase-3-independent-review.md` 主审及三个窄轨已追到真实调用者、唯一owner及副作用，并分别亲看当期UI。最终Manuscript像素增量已关闭，正式独立结论PASSED_WITH_EXPLICIT_EXTERNAL_GATES；未确认的问题不建兜底/重试/第二状态机。

实际provider生成、Camera/系统chooser/UCrop与SAF手势、通知权限/点击、物理设备/OEM后台及真实帧性能、跨端导出runner仍未实测，不以构建或本地SSE冒称这些门槛通过。已授权阶段内代码/本地行为和UI完成后继续Phase4，不因外部环境伪造通过。
