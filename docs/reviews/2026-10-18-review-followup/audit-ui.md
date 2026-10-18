# UI follow-up audit

本轮只读复核 E1–E4、初始 UI WIP，以及聊天输入、附件、生成 UI、主题和 MiniApp 的生产调用链。未改生产代码、未运行 Gradle、未读取兄弟产品。以下六组在第二次核对入口、状态归属和实际副作用后接受；UI 行为回归尚待修复 packet 的 RED/GREEN 执行。已有 E1/E2/E4 修复在读取的生产接线中成立，E3 的快照文字与 edit target 保护成立，但附件文件所有权和编辑确认栏仍有缺口。

## U1 · P1 · 预检快照没有保住附件文件，且接受后留下可删除的已发送附件

- **位置：** `app/src/main/java/app/amber/feature/ui/components/ai/ChatInputSubmissionOwner.kt:20–34`；`ChatInputAttachments.kt:141–152`；`ChatInputState.kt:375–378`。
- **入口和触发 A：** 普通聊天添加本地图片，发送时走 `ChatInput.prepareSend`（326–349）。当前模型需要 vision fallback，真实 validator 在 `ImageAttachmentValidator.kt:163` 挂起进行 health probe。此时点击附件 chip 的移除，`removeImport`/`removePart` 即调用 `FilesManager.deleteChatFiles`。probe 返回成功后，submission owner 仅检查 conversation，不检查附件是否仍由本次草稿拥有，继续发送原捕获 URI。
- **入口和触发 B：** 同样的预检期间只修改文字。发送成功后 `input.getContents() != parts`，owner 保留整个 composer，包括已发送的附件与 READY import。用户随后移除该附件或清空输入，`shouldDeleteFileOnRemove` 因非 edit 返回 true，物理删除已发送消息引用的文件。
- **用户影响：** 新发送或已发送消息保留失效的本地附件 URI，图片读取失败，文档也可永久失去内容。`FilesManager.kt:377–379` 确实 unlink upload 文件并删除 managed row；发送并不复制附件，`ChatService.sendMessage:970–975` 将同一 parts 放入 pending，`FileEncoder.kt:133–138` 随后按原路径读取。
- **为何不是误报/by design：** E3 明确支持预检期间继续输入下一份草稿，但值快照不等于文件所有权。附件移除和成功后 composer 中仍存在的附件都是现有可见操作；损坏已经送出的历史消息与保留下一份文字草稿没有合理关联。无需假设外部进程删除文件。
- **最小修复：** 将“后续文字变化”与“提交附件被移除/清空/替换”区分。后者撤回本次待提交附件所有权时阻止晚提交；接受后消费本次已提交附件/import，保留后续文字和后来新增附件，避免已发送文件再由 unsent cleanup 删除。沿现有 composer owner 修复，无需全局文件租约、引用计数服务或重试队列。
- **有效回归：** 扩展真实 `ChatInputSubmissionOwnerTest` 的 Deferred validator 入口：(1) barrier 内移除快照附件再放行，不能发送失去文件的快照；(2) barrier 内只改文字，accepted 后保留新文字但不保留本次已发送的可删除附件/import；(3) 后加附件继续保留且可独立移除；(4) 清空/取消 edit 不会晚提交已丢弃输入。用真实临时 upload 文件和现有 chip/remove seam 核对 cleanup，不只断言 List 相等。

## U2 · P2 · 替换 composer 内容没有使旧附件 import 失效

- **位置：** `ChatInputState.kt:113–123,290–313`；`ChatPage.kt:1299–1301`；`ChatInputAttachments.kt:166–193`。
- **入口和触发：** 普通草稿已选一个附件，再点击历史用户消息的“编辑”。真实 callback 先设置 editingMessage，再 `setContents(it.parts)`。此方法替换 text/messageContent，却保留 attachmentImports 与 source 去重状态。
- **用户影响：** 旧附件为 READY 时，旧 chip 仍由 attachmentImports 独立绘制，却不再存在于 `getContents()` 将发送的 parts，UI 与实际内容不一致。旧附件为 IMPORTING 时，晚完成只检查相同 conversation/import id，仍通过并把旧草稿附件加入新的历史消息编辑。
- **为何不是误报/by design：** 历史 edit 替换旧 text/messageContent 是当前明确行为；一份被替换掉的附件既不能“显示却不发送”，也不能以完成时机决定是否加入新 edit。现有 conversation gate 无法隔离同 conversation 内的 composer 替换。
- **最小修复：** 在替换完整草稿/进入另一个 edit 的 owner 同步失效旧 imports，并清理仅属于被丢弃草稿的新副本；保护原历史附件。复用 `drainAttachmentFilesForDiscard` 的归属规则，但在改变 edit owner 前取得旧草稿 cleanup 结果。不要添加新的持久草稿系统。
- **有效回归：** 扩展 `ChatInputStateTest`：READY import 后替换内容，chip 来源与 `getContents()` 一致；IMPORTING 后替换再完成，completion 返回 false；编辑 A 的原附件不因转去编辑 B 而删除，A 中新加的临时副本可清理。还需覆盖生产 `ChatPage.onEdit`，不能只新增一个没有接线的替换方法。队列移回输入的 `ChatVM.moveFirstPendingMessageToInput` 也调用 setContents，改 owner 时一并核对。

## U3 · P2 · 编辑确认栏绕过附件导入 gate，成功保存时静默丢弃尚未就绪的附件

- **位置：** `ChatPage.kt:872–886,1042–1046`；正常发送对照 `ChatInput.kt:355–375`。
- **入口和触发：** 编辑历史用户消息，添加一个仍在导入/解析的大文件，然后点击确认栏“仅保存”或“保存并重新生成”。按钮仅按 generating 禁用，直接调用 confirmEdit。
- **用户影响：** IMPORTING/FAILED 项尚不在 messageContent；confirmEdit 将不完整的 getContents 交给 VM。VM 返回 accepted 后立即 clearInput，旧 import 被丢弃；真实导入回调 `ChatInput.kt:551–571` 此后 completion 失败并删除副本。用户看见保存成功，选中的附件却丢失。“保存并重新生成”还绕过正常发送已有的 image validator。
- **为何不是误报/by design：** 相同 composer 的发送按钮明确阻止 unresolved import，并显示 importing/failed 提示。两个编辑确认动作保存的仍是当前完整输入，不能以入口不同静默忽略附件。issue 不依赖假想解析失败，正常大文件的短暂导入窗口即可触发。
- **最小修复：** 让确认栏共用同一份 unresolved import gate 和提交 owner；保存并重新生成复用实际发送的图片校验，纯保存不强制要求可用 provider。保留 `regenerate=false` 本来允许无模型保存的行为。
- **有效回归：** 复用 `ChatInputStateTest`/`ChatInputSubmissionOwnerTest` 及真实确认 callback seam：IMPORTING、FAILED 时两个按钮都不接受、不清输入；导入完成后才完整保存；仅保存无模型仍可用；regenerate 路径图片阻断时保留 edit。测试必须调用确认栏所接生产 owner，避免只测普通 send。

## U4 · P1 · 生成 UI action 清掉 edit owner，后续移除附件会删历史消息文件

- **位置：** `ChatPage.kt:1386–1397` 尤其 `inputState.editingMessage = null`；`ChatInputState.kt:375–378`；`ChatInputAttachments.kt:141–144`。
- **入口和触发：** 编辑一条带本地 upload 图片/文档的历史用户消息，随后点击已有生成 UI 卡片 action。`GenerativeWidgetCard.kt:302–305,683–685` 的真实 action chip 将独立 instruction 送回 ChatPage；enableActions 默认 true（`core/settings/.../PreferencesStore.kt:267`）。callback 先把 editingMessage 清空，却保留原编辑内容/附件。随后移除 composer 附件或清空输入。
- **用户影响：** `shouldDeleteFileOnRemove` 原来用 edit owner 保护历史附件，owner 被错误清除后返回 true。FilesManager 删除历史消息仍引用的原 upload 文件；原编辑文字也丢掉“编辑历史消息”的身份，会变成新消息。
- **为何不是误报/by design：** 生成 UI action 本身发送独立新 instruction，完全不使用 composer 的 parts；同页 onMiniAppModify 也以独立发送保留 composer。单独清 editingMessage 既没有成功消费 edit，也没有丢弃并清理当前输入，是一个可达的归属错误。原文件必须在 upload 范围内，符合正常文件选择器产物，非任意外部路径猜测。
- **最小修复：** 独立 action 发送时保留现有 composer/edit owner，移除该单独 mutation。无需改 action 协议，也无需给所有文件删除增加猜测性兜底。
- **有效回归：** 通过生产 widget-action callback seam 发送 instruction，验证原 edit id、text、attachment 不变；随后 remove/discard 应保护原历史文件。若 callback 从 ChatPage 提取为小 owner 以便测试，必须让真实 callback 使用它，不能以源字符串检查代替行为验证。

## U5 · P2 · MiniApp 的 5 秒 JS 截止早于用户确认，失败回执后仍执行副作用

- **位置：** `app/src/main/assets/miniapp/miniapp_bridge.js:8–17,165–192`；`app/src/main/java/app/amber/feature/miniapp/bridge/MiniAppBridge.kt:274–348,728–733`。
- **入口和触发：** MiniApp 调用 host.sendToConversation（send 或 draft）、host.createArtifact 等会等用户确认的方法，用户正常阅读确认框超过 5 秒再允许。JS call 默认 5000ms，先删除 pending 并 reject。native confirmation 尚在等待，允许后仍执行 writeAndSend/writeDraft/createArtifact。
- **用户影响：** 页面收到 timeout，实际消息/产物稍后仍写入。按失败重试会重复发送/创建。sensor.subscribe 同样会在超时后注册，但 JS 拿不到 subscriptionId，无法通过其返回对象退订直到 runner 清理。
- **二次复核：** 独立子审使用 Node VM 执行真实 asset，注入可控 timer/native 桩，得到 `request host.sendToConversation timeoutMs 5000`；触发计时器后 promise 为 `{kind:"rejected",message:"MiniApp bridge timeout"}`；再投递真实成功结构 `{ok:true,data:{status:"sent"}}` 后仍 rejected。原生 `confirm` 确认后直接执行 block，JS 没有取消消息，native 无 pending 取消检查。不是网络延迟假设。
- **最小修复：** 交互确认方法显式使用没有 JS 固定截止的 call 模式；保留 fetch 等非交互操作原有期限，以及原生操作 owner 现有期限。不要增加自动重试、新队列或复杂取消协议。
- **有效回归：** 运行真实 asset + controllable timers：用户等候超过 5 秒后成功/拒绝都按实际 native receipt settle；普通 Amber.fetch 仍按既有期限超时。原生 bridge 可复用 `MiniAppSystemCapabilityBridgeTest` 的 request 入口核对等待确认之后副作用的实际结果。

## U6 · P2 · MiniApp getTheme 混用系统模式与 App 配色，并捕获启动时主题

- **位置：** `MiniAppRunnerPage.kt:352–355,532–539,549–550`；真实入口 `miniapp_bridge.js:188` → `MiniAppBridge.kt:229–231`。
- **入口和触发：** 系统浅色而 App 显式深色时打开 MiniApp 并调用 host.getTheme；或保持 runner 打开，系统/host 主题发生重组后再次调用。
- **用户影响：** 第一个场景拿到深色 background + `dark=false`；第二个场景仍拿到 AndroidView factory 创建时捕获的旧颜色/模式。MiniApp 因此选错自己的文字、控件或深色资源。
- **为何不是误报/by design：** `Theme.kt:158–162` 明确按 SYSTEM/LIGHT/DARK 决定实际 App 模式，并在 346 提供 `LocalDarkMode`。接口每次执行 themeProvider，配色来源也是 MaterialTheme，语义是当前 host theme。factory 闭包不是 Compose state holder，update 只改 debugging，没有另一路刷新配色。
- **最小修复：** 用 LocalDarkMode.current 和当前三种颜色构成 MiniAppTheme，用 rememberUpdatedState 让长期存活的 provider 读取当前值，保留 WebView 与页面状态。
- **有效回归：** 实际 Compose provider 行为测试：系统浅色 + host 深色，以及主题重组后，同一已捕获 provider 返回当前模式/颜色。结合已有 native request 入口验证 host.getTheme JSON。不能只测与 factory 无关的复制 helper。

## 建议互斥修复分组与阶段 review

1. **Composer packet：U1–U4。** 一个 worker 独占 ChatInput.kt、ChatInputState.kt、ChatInputSubmissionOwner.kt、ChatInputAttachments.kt、ChatPage.kt 及对应测试。四组共享同一 composer 生命周期，不宜再拆给会碰同文件的 worker。先为 U1/U2/U3 建 RED，修归属/提交入口，再做 U4 的单行 owner 修复与行为回归。定点 JVM/Robolectric GREEN 后，独立 agent review 真实 UI gate → composer owner → 文件 cleanup/VM 接受链。
2. **MiniApp packet：U5–U6。** 另一个 worker 独占 MiniAppRunnerPage.kt、assets/miniapp/miniapp_bridge.js 及 MiniApp 回归，与 composer packet 无文件冲突。真实脚本 timer 回归与 Compose provider 回归后，独立 review 交互 pending 生命周期、当前主题 provider、已有非交互 timeout 保留情况。
3. 协调者序列化 Gradle gate，最后 app compile/assemble。构建、安装、启动、真实 provider、实机行为分别报告，本审查没有这些验收证据。

## 不列入缺陷的候选

- E1 之外的 Audio remote URI：当前可达生产来源只确认本地 picker/share；仅看到 toFile 不足以证明接受的远程 audio 会进入此处，不报。
- ThemeTransfer 内置 id 与导入 id 冲突：导出后重导入的业务契约未明确，不据猜测新增 id 命名体系。
- E audit 已排除的手动改色主题失效、远程视频封面与布局宽度：本次未取得新反证，保留原判断，不重复修复。
- Widget 渲染/WebView 执行的泛化生命周期疑点：没有真实可达状态与实际影响链，不为候选添加防御分支。
