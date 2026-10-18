# Phase6 最终职责与产品收口

状态：**PASSED_WITH_EXPLICIT_EXTERNAL_GATES**；Phase0–6全部实施及独立review完成，无本轮指定剩余阻塞。

## 同口径统计

| 范围 | 基线 | 最终 |
| --- | ---: | ---: |
| 手写生产文件 | 1335 | 1609 |
| 手写生产物理行 | 347820 | 355469 |
| 手写生产 >1000 行 | 52 | 11 |
| 手写生产 >1500 行 | 20 | 1 |
| 最大手写生产文件 | ChatService 4514 | NovelMarkdownWorkspaceViewModel 1564 |
| 全仓UTF8文本 >1000 行 | 110 | 69 |
| 全仓UTF8文本 >1500 行 | 61 | 42 |

最大全仓文本仍为jieba词典348982行。生产统计包含tracked+untracked实际main源码、仓内维护Web/JS/HTML/CSS与Rust，具体vendor、生成/构建物、测试/词典/资源单列；过滤器在fixtures/audit_inventory.py，完整11文件清单见evidence/final-source-inventory.json。原52路径全部存在，每一项均追到当前生产入口和职责归处。

## 职责与合理保留

ChatService4514→646，唯一Store/Queue/Editor/Coordinator/Presenter与Hooks实际接回，DI构造环真实解除；Generation/Transformer/Memory/Context改具体settings快照，Settings事务writer保持唯一。Novel/Council/DeepRead/Live按展示、查询、执行和durable owner划分，原epoch/CAS/claim/取消责任保留。Backup恢复session、OAuth授权代次、SSE编码/事件/传输、Route交付、WebView bridge与Web会话编辑均有实际caller和唯一状态归属。

唯一超1500的NovelVM1564显式保留64行内聚例外：UI state、admission、job、reload/epoch验收属于同生命周期；Queries/Interactive/Audit/History/TurnRequests及domain commit已分离。五个大声明/表单/模板文件按实际变化原因保留，MemoryRepository1037仍为同durable records/candidates/events writer，无UI/网络/job混绑。完整理由见P6-final-boundary-audit.md。未以压缩格式、无意义透传或移动到Misc文件达标。

48模块160条生产依赖边为DAG，无library→app/API→impl反向边；独立2803输入hash0drift。最后布局只变BackupProviderPanels/McpImportDialog/PromptPage三文件，未改业务、回调、enable、主题或共享按钮。原死代码/历史占位候选只登记，未删除或强行接线。

## 实际验证

- P0–P5各阶段独立源码/实际行为/当期UI结论已通过，复用未变代码证据，未重复扩大测试矩阵。
- 完整Responses/Settings/ScreenAPI 352项通过。正常配置全app500类3178项首次3142通过、22失败、14跳过；原失败修复或夹具校准后集中11类62项中58通过，History4先被Native SQLite缺JSON1阻塞，最窄test-only summary metadata投影后原4预算断言通过。没有修后再次全跑3178，局部结果不重叠累计。Antigravity原2项另在受控HTTP/任务配置门通过；正常APK无dummy配置。History夹具不验证生产summary JSON preview SQL，14跳过及其他平台门槛保留。
- 最后四UI修复后的正常Full/Novel/DeepRead及AndroidTest构建全部通过。最终Full Debug主APK SHA256 d6a5eaad20248b1ecbf3df82a7c25ab7122bcaa5208c7e3883376979530bbaa0，已覆盖安装且设备base.apk实际hash一致。三产品完整hash/DEX扫描见evidence/final-normal-product-apks.json。
- 最终受影响MCP/Prompt与Backup实际411两项通过33.284s、320/font1.5两项通过27.74s，均0skip；独立双宽新PNG确认MCP风险标签/导入按钮、Prompt页签、WebDAV按钮四处实际裁切全部关闭。此前Settings全3方法各宽通过，未变SSH/WebMount结果保留。
- Route411与320/font1.5各3通过，真实Android WebView17通过；Web React原8case修后全部通过，typecheck/SPA build/parts5及1280/390/320 Workbench真实交互和独立像素通过。fmt仅原20未改文件失败，逐项HEAD无diff。
- 业务最终修复APK ebd88634实际Chat2通过29.145s（本地SSE驱动完整生产链：发送/排队/Stop/FIFO/Room、ask_user同run续跑）、Novel1通过21.326s（读/编辑/分支/素材持久化）。其后只三处布局变更，业务/runtime/route未变，复用这些结果；未冒称在d6a5eaad重新跑Chat/Novel。

## 独立review、资源与未验边界

P5正式结论见phase-5-independent-review.md，P6最终结论见phase-6-independent-review.md与P6-final-boundary-audit.md。原红、夹具错误、取消的一次中途构建143及最终绿证据全部保留，不覆盖历史。

原设备配置1080x2400/density420/font1.0精确恢复，Settings/draft/prefs按fixture清理；无新增权限，通知仍ignore、accessibility仍null，adb reverse空。任务Playwright context/browser、三个本地server及只读5580模拟器已关闭，用户预存资源未触碰。HEAD仍137f57e2，分支refactor/modularization-phases-20261018；改动保留工作区，未提交/推送/发布。

真实provider/OAuth账号/外部catalog、SSH/Alpine/Termux、SAF实际写恢复、overlay/第三方填写/托盘、物理OEM、帧率/性能、跨端以及不存在的Android Web后端仍未验；构建、模拟器和受控HTTP不代替这些产品外部门槛。未清设备用户数据或读取其他产品仓库。

提交前统计说明：19个新增源码文件仅移除末尾空行，总物理行据当前文件更新；可执行内容和原构建/行为验收不变，详见commit-preflight.md。
