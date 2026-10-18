# Phase5 IO、入口、设置与协议模块化

状态：**PASSED_WITH_EXPLICIT_EXTERNAL_GATES**。独立源码、生产调用链和最终双宽像素review全部通过；所有已确认问题已修并完成受影响复验。

## 已交付的完整责任

- Archive：Manager883保恢复gate、DB事务marker、partial commit与顺序；具体Codec251/Journal204/Exporter221真实消费。旧fork补偿显式携captured epoch，避免删掉实际restore采用文件。
- Backup：VM822保原传输/冲突协议；完整RestoreSession228持source/verification/apply/retry/metadata/明确completed，GoogleConnection231持授权/连接；Page419与具体panes/dialogs只接实际state/callback。保留encrypted source供fresh verify；成功不依赖Page观察短暂Loading。
- Provider：Status/Apply/Catalog/Slot实际命令；Codex/Gemini具体durable generation guard覆盖注销/新授权后的迟到保存，catalog检查实际请求身份，Apply在最新Settings事务准备。具体OAuth console已迁，P1字段/草稿原样。无通用OAuth框架。
- Route：Activity保唯一backStack/consumed/冷热优先级/lifecycle，Screen/全部真实Entries/IntentTargets/ShareDelivery按职责。真实share配置重建覆盖编辑红已由同ChatVM初始接收标记精确修复，新VM仍接收新delivery。
- Automation：catalog→同OperationOwner+Scope/Primitive/Flow/List/Expectation；Manifest服务→同根NativeScreen/LiveWindows/verifiedEditableText/LegacyTree。锁/stop/活动/receipt/resource生命周期不复制，无新权限。
- Web bridge：核心保realm/ref/snapshot；network、Feishu、ZCode完整职责，单manifest实际供Kotlin与Node/Chrome两消费者，固定私有IIFE bootstrap。主窗/popup/documentstart/reinject同字符串，SessionHandle原RPC pending不改。
- React：真实route直接消费detail/composer/attachments/editor/timeline/workbench。原4个实际竞态与await后SyntheticEvent问题最窄修复；唯一Zustand草稿/activeId仍保留。
- Responses：原公开API182→RequestEncoder325/EventDecoder557/EventTransport512；每流唯一reconciler、cursor save-before-emit、once reconnect/401 once refresh/真实取消均保持。
- MCP/Prompt：三个publisher同awaited latest-transform batch+final names检查，preflight取消透传。Prompt持久操作在最新集合按ID执行；nested子确认/父取消仍同草稿。具体route/pane/editor/import责任已实际接回。
- SSH/WebMount设置：composition command session/纯卡片/具体dialog/editor，原Store/Runtime/Pool/LoginController唯一owner保持；slash installer的大小写不敏感判断与追加在同最新Settings事务内。
- App启动：同步file journal恢复显式保在WorkManager/eager之前；具体RuntimeRecovery完整驱动原gate，JS清理独立；六条订阅/调度完整迁BackgroundSchedulers，八channel配置原样。

## 实际集中验证

全部运行门已收口；独立最终结论见phase-5-independent-review.md。

- 完整 Responses/Settings/ScreenAPI：51 fresh classes、352 tests、0失败、0跳过。
- 全 app 正常配置集中运行：500 fresh classes、3178 tests，3142通过、22失败、14跳过。实际22失败集中修复后，11类62项中58通过，剩History4先由真实Native SQLite缺JSON1的夹具前置阻塞；最窄test-only summary metadata projection后，原4预算/actual consumption断言全部通过。**没有再次全跑3178，也不重叠累计局部结果。** 生产summary JSON preview SQL未被该JVM夹具验证。Antigravity原2项在单独受控HTTP/任务配置门实际通过，正常配置整app门显式排除这2项；正常安装APK无任务dummy marker。
- 真问题修复：Memory dream records/events同原gate内Room事务，失败不发布local map；WebDAV删除404只作已不存在，其余异常透传；SAF createDocument使用真实document parent URI；Regex两层cache使用完整规则值key，Java/Rust replacement不等价时按原JVM实现执行；MiniApp回调读取当前host主题；History共享预算按实际返回文本计费并保留首条长消息前缀。Locale、永久stateIn observer、缺JSON1、有效表单/legacy-ref测试输入分别作为夹具处理，没有制造生产兜底。
- Web React typecheck、SPA build、parts5通过；原8浏览器case4红→8绿，额外编辑/附件/取消及1280/390/320 Workbench实际通过，六张图独立亲看。fmt:check仅原20未改文件失败，逐项HEAD无diff，未全仓格式化。
- Bridge三个Node/真实Chrome Lexical门通过；实际Android WebView17项全部通过，0跳过，3.658s，使用生产WebViewPool/bootstrap/SessionHandle，覆盖ref/snapshot/跨会话/run终结/迟到dialog。offscreen无截图目录，pullExit1如实保留，不据此冒称像素验证。
- 最后四UI修复后的正常Full/Novel/DeepRead+AndroidTest构建全部通过；Full主APK SHA256 d6a5eaad20248b1ecbf3df82a7c25ab7122bcaa5208c7e3883376979530bbaa0，已覆盖安装到任务模拟器5580；三个产品六路径三唯一hash与DEX无dummy扫描见final-normal-product-apks.json。
- Route实际411与320/font1.5各3通过、0跳过，共8张图独立复核，share编辑重建、notification目标、OAuth草稿均闭合。320附件chip遵循原横滚合同，未误报为裁切。
- ebd88634上的Chat真实2项通过（29.145s）及Novel完整route1项通过（21.326s）；其后仅三处UI文件布局调整，runtime/domain/route未变，P6复用这些已验关键路径，不声称在最终d6a5eaad重跑。
- 设置411实际3通过40.841s、320/font1.5实际3通过38.6s，均0跳过。真实Backup系统picker取消、MCP导入preview未apply、Prompt有效nested条目只改父draft再Cancel、SSH/WebMount编辑取消/recreate与Settings canonical不变全闭合。旧失败是placeholder重选、启动基线边界和缺keyword导致Confirm合法禁用，均精准修fixture，未改生产/放宽断言/延长timeout。两轮各47证据文件；wm/density/font精确恢复，adb reverse为空。

最终2803 source/config/resource/test输入冻结于evidence/final-source-checkpoint.json。所有生产改动，包括最后三处布局文件，已包含在最终三产品APK构建中；未再改变source。每包有限维护检查和独立调用链审查已完成，独立reviewer已对最终当期双宽PNG逐张确认。

不对设备现有DB做整量restore；JVM真实Room/files/archive验证事务，设备真实picker/editor/取消/布局。真实provider/OAuth/外部catalog、SSH/Alpine/Termux、权限/overlay/托盘、物理OEM/性能/跨端及当前不存在的Android Web backend不计作已验。未新增权限、清数据、删除保留候选、提交推送或发布。

已落实加速：不再扩候选或局部矩阵，集中发现的实际失败修后只复验受影响项；设置双宽与P6最终review均已完成，不重复已验未变代码。

最后独立320/font1.5像素review确认四处UI问题，已只改三文件：MCP摘要/风险与操作按钮FlowRow，Prompt页签自然最小高度同行等高，WebDAV单组按钮FlowRow；未动共享按钮/主题/文案/状态或新增测试。最终制品构建exit0；同两个既有方法411实际2green33.284s、320/font1.5实际2green27.74s，均0skip。独立reviewer亲看新三张相关PNG双宽对照，四处问题全部关闭；wm/density/font精确恢复、reverse空。
