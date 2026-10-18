# P5 W-react 独立审查

状态：**PASSED_WITH_EXPLICIT_EXTERNAL_GATES — W-react packet only**。

本结论仅关闭本次 React conversations 包的源码、已执行的受控浏览器行为与六张指定截图审查，不代表 Phase5 整体通过。当前范围没有未关闭的 confirmed finding；没有为假想问题新增修复建议。审查者未修改生产或测试，未启动服务、浏览器、Gradle 或设备，仅读取源码、已归档执行结果和实际 PNG，并写本报告。

## 范围与方法

对照 `137f57e2` 的原 route/input 与当前九个生产文件，读取 `results/P5-web-react.md`、本地 `web-ui/CLAUDE.md`、真实 home/c.$id 入口、list/store/API/Workbench consumers。由独立只读 child 专审 Detail/Composer/List/Store 主链，本 reviewer 核对附件、编辑 parts、Timeline、Workbench 装配及全部指定像素。行为 fixture 作为可观测证据，不代替生产 caller 核对。

## 源码与调用链结论

| 责任 | 当前真实入口及 owner | 审查结论 |
| --- | --- | --- |
| 路由与选择 | `routes/home.tsx`、`routes/c.$id.tsx` 均 re-export `conversations.tsx`；原 `useConversationList.activeId` | 选择、导航、Assistant/Stop/消息动作仍从实际 route 装配，没有新 selected-conversation owner。 |
| Detail 与订阅 | `use-conversation-detail.ts` 的 REST、ky SSE、selected-node projection | effect 内 mounted/AbortController 隔离旧订阅；真正接受 snapshot 或应用 node update 后屏蔽同 effect 的旧 REST 成功及失败。无法应用的 update 不会先屏蔽所需 REST。原 node-ID 优先/index 回退和分支投影保留。 |
| 草稿与提交 | 原 Zustand `chat-input-slice` 唯一 Draft store；`use-conversation-composer.ts` 发命令 | 提交捕获 key、Draft 对象与编辑 session；成功清理只针对仍为同对象的 Draft。原 slice 每次 text/parts mutation 均产生新 Draft，后到输入不会被旧成功清空。home 仅成功且仍拥有可见 key 时旋转/导航，失败保留原草稿。 |
| 附件副作用 | `ChatInput` 的 file/paste/drop → `useConversationAttachments(draftKey)` → API 与原 store | MIME 检测、上传响应映射沿原规则；上传目的 key 在 await 前已由闭包捕获，A 上传后切 B 仍归 A。删除先完成应删除的新文件 API，再在原 key 的当前 parts 中找原对象，避免旧 index 删除相邻附件；失败保留原 part 并显示错误。 |
| 编辑重建 | 实际 Edit → Composer → `conversation-message-edit.ts` → `/edit` | 最后一个 text 的编辑规则、此前 text/reasoning/tool/metadata 与历史 source 顺序保留；历史附件移除不删除历史文件，新附件维持原删除合同；wire 移除内部编辑标记。没有改 DTO、协议或 source index 含义。 |
| Timeline | `ConversationTimeline` 接收 route 的真实 selected messages 与动作 | 原 message key、branch、loading/typing、QuickJump anchor/preview、滚动组件和最大内容宽度保留，组件没有复制会话状态或缓存。 |
| Workbench | 原 `useWorkbenchController` → 同一 `WorkbenchProvider` → `ConversationWorkbenchLayout` | 桌面 panel expand/collapse、移动 Drawer 与 close 回调真实相连；布局组件只拥有 panel imperative ref，没有再造 panel/draft owner。 |

`ChatInput` 保留上传/提交 busy、错误和交互状态，网络及草稿副作用迁到对应 hook。文件 input 在 await 前捕获 DOM input 和 File 数组，await 后不再读已释放的 React event.currentTarget。新接口是具体命令与数据，没有传整 route context、万能 service、重复持久状态或重试兜底。原 route 的 fork/move/delete/标题/导出等动作对照原实现未发现本次接线遗漏。

## 已证缺陷与修复事实

`evidence/phase-5-web-react-current-source-baseline/results.json` 与 `results/P5-web-react-baseline.json` 记录原八项中的四个实际失败，不把布局通过项算成红：

1. POST 期间新输入被旧成功响应清空 → 成功结算增加原 Draft identity gate。
2. home POST 失败前已旋转 key，原草稿隐藏 → key 旋转/导航移到成功且仍拥有可见草稿时。
3. 新 SSE 已到达后旧 REST 覆盖新内容 → 同 effect 接受 stream 后拒绝旧 REST 结算。
4. 删除附件等待期间前方附件先移除，旧 index 删错其他附件 → 在捕获 key 中用原 part 对象定位。

另记录 owner 浏览器 pageerror 实测的原 upload handler `event.currentTarget` 在 await 后为 null，已用同步捕获 input 修复。它不是本次新造的产品契约。编辑后转普通发送可能携内部标记的候选没有本轮实际 POST 红证据，未认定新缺陷、未擅自扩修。

## 实际验证与限制

| 证据 | 可支持的结论 |
| --- | --- |
| `output/playwright/p5-web-after/results.json` | 同原八场景全部 PASS，包括四个原红回归与 A/B/A、三宽基础布局。 |
| `output/playwright/p5-web-contracts/results.json` | A 发送后切 B、home 继续输入、A 上传切 B、删除失败、USER/ASSISTANT 历史附件编辑等业务断言实际通过。此文件保留 Cancel edit locator 大小写错误的历史 FAIL，不能宣称整个原文件全绿。 |
| `output/playwright/p5-web-cancel/results.json` | fixture 修正为实际 `Cancel Edit` label 后，该独立用例 PASS；生产没有为 locator 改动。 |
| `output/playwright/p5-web-layout-final/results.json` | HTML code 的真实 Preview 按钮 → iframe 内容 → Close 三宽全部 PASS；稳定截图在 Drawer 动画结束后采集，没有改产品布局迎合截图。 |
| `results/phase-5-web-react-typecheck.log` 与 root 执行回报 | `react-router typegen && tsc` 成功。 |
| `results/phase-5-web-react-build.log` | React Router/Vite client 构建完成并生成 SPA `build/client/index.html`；日志仍有既有 sourcemap、chunk-size 和 Node deprecation 警告，没有把警告写成零警告。该证据不是 `copy.ts` 导出/部署通过。 |
| `results/phase-5-web-react-format.log` | `fmt:check` 实际失败，列出 20 个本包未修改的原文件；当前九个本包生产文件不在失败列表，未批量改无关格式。 |

上述浏览器 fixture 使用真实 React Router 页面、ky REST/SSE 与 multipart/delete 交互，通过受控 HTTP 响应和 barrier 复现归属/时序；没有启动实际 Android Web 后端。测试文件源码可核请求 endpoint/payload、页面状态和 pageerror，不是仅调用提取 helper 得到近似绿。编辑 helper 的五项 Bun 检查由 owner 实跑记录，本 reviewer 读取其有效 parts 顺序/历史文件保护/metadata 断言，没有自行重跑。

## 独立像素审查

已亲看以下六张原始 PNG，而非只读 PASS JSON：

| 图片 | 目视结论 |
| --- | --- |
| `output/playwright/p5-web-after/desktop-baseline.png`，1280 | sidebar 与 header 对齐，消息与输入区在主内容栏内，输入卡片左右留白一致；suggestion、模型工具与发送按钮可见，底部提示未被遮挡。 |
| `output/playwright/p5-web-after/mobile-390.png` | sidebar 收起后主内容使用窄屏宽度，消息操作、输入卡片与发送按钮完整，底部提示在视口内。 |
| `output/playwright/p5-web-after/mobile-320.png` | 输入卡片两侧仍留白，工具图标与发送按钮无重叠；header 副文案单行省略是原 truncate 合同，未出现逐字竖排或视口外溢。 |
| `output/playwright/p5-web-layout-final/workbench-1280.png` | 右侧真实 HTML preview 正常呈现，分隔条、工作台标题/tabs/关闭按钮和左侧输入区相互不覆盖。 |
| `output/playwright/p5-web-layout-final/workbench-390.png` | Drawer 稳定帧中 handle、标题、关闭、Preview/Source tabs 和 iframe 标题完整；overlay 后方内容按原合同变暗。 |
| `output/playwright/p5-web-layout-final/workbench-320.png` | 同一 Drawer 在 320 宽仍有完整关闭入口与 tabs，`Preview fixture` 可读且未裁切，预览左右内距正常。 |

这些图片覆盖本次指定的静态视口和实际 Preview 交互，不宣称屏幕阅读器、系统软键盘、真实移动浏览器、复杂长 Timeline 性能或任意长文件名均已验收。

## 包级最终结论与外部门槛

本次九个生产文件按 Detail/Composer/Attachments/编辑重建/Timeline/Workbench 的变化原因划分，边界具备实际职责，状态与副作用链路可以从真实路由定位；未发现需要交 root 修复的新可达问题。当前 W-react 包可作为已审完成包并入 Phase5 汇总。

保留明确未验证范围：实际 Android WebServer → Room/backend 的端到端接线、真实 provider、JWT/auth 与持久线上 SSE、`copy.ts` 旧路径导出/发布、系统文件选择与移动 IME、物理浏览器/OEM、长会话与复杂消息性能。`web-ui/CLAUDE.md` 的旧 `:web` 描述不构成这些当前 gate 通过的证据；当前未因此新增后端实现或改生产部署。Phase5 其他 owners 与最终整体 gate 继续由 root 统一审验。
