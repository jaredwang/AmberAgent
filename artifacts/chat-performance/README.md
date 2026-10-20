# Android 会话页流式性能与真机验证

日期：2026-10-20。分支 `codex/chat-streaming-performance`，基线 `25c3f68c1cfca9ded53db62554125d9c28a69b36`。首批优化提交 `fa03d938efbf266708767988f83bf1fe7e8c7ac7`。未推送、未创建 PR。

此前的锁屏/安装限制已解除。现已完成同一真机、相同测试 APK、内容和操作的三组前后采样。结果支持长消息的明显改善，仍存在长混合 Markdown 卡顿；短答没有稳定收益证据。单次成对采样不构成统计显著性或所有设备的 FPS 保证。

## 实现与测量驱动调整

- 首次解析、后续完整解析及 AST 到 AnnotatedString 映射迁到 `Dispatchers.Default`。每消息的稳定顶级块映射复用；引用定义（原文含 `]:`）和高亮变化保守失效。流式临时前缀不挤占历史 Markdown LRU。
- 原方案只把映射放到后台，真机混合 Markdown 仍有整条 Text 布局瓶颈。因此增加长文本稳定分块：渲染长度 ≥3072 UTF-16 单元、已有换行处约768单元分块，保留≥96单元活跃尾部。已完成 Text 使用 RenderNode 显示列表复用；只对活跃尾部重新排版。短文本、无换行、CR 和 ParagraphStyle 输入保留单 Text。
- 分块不插入源文字符、不切 surrogate、不改变最终文本。边界移除的一个换行由原生两行 bridge 测量计算基线距离，保留其注解；整个 SelectionContainer 仍包住所有子 Text。真机原生测量在320/393/700px宽度下比较混合标题、代码、表格、中文、emoji、组合字符的每行基线、行数和高度，误差均为0。单纯叠加子 Text 高度曾产生86px偏差，已删除该方案。
- 帧内追加/reveal 合并一次构建；reveal 仅检查末尾≤48码点。积压计数到432释放单位饱和后停止扫描；每帧单次可见字符串更新，维持原有速度及Unicode规则。
- 仅静止钉底启用高度动画；拖动、惯性及离底阅读使用自然高度和锚点补偿，延迟补偿执行前复查是否已回到底部。
- 流式单项保护跟随可见性和视觉实例：生成、仍可见或显示实例活跃时保持保护，生成结束且离屏/视觉实例排空或销毁后恢复切片。移除“最近两个”的永久保护策略。
- UI 缓存未命中立即显示完整原文，样式/切片异步准备；时间线读取缓存后排队预热，通过revision更新。后台同步API仍保留，生产UI不再同步parse或等 `Future.get()`。
- 纯尾部更新共享历史 entries、索引列表和查询表，只对头段加位移；保留历史身份检查识别等长替换。
- 未更改公开组件签名、持久化格式或生成生命周期。初始后台样式准备可能短暂显示 Markdown 原始标记；长单段仍会整段布局。没有延迟源文显示或丢弃输出。

## 同真机前后数据

设备 `392288d7` / `25128PNA1C`，fullDebug arm64，同一系统/字体。窗口提示被MIUI触摸升频忽略，最终脚本临时把系统 peak/min 都设为60Hz，测试结束均检查display=60.000004Hz，并通过EXIT trap恢复原来的peak=120、min=null。原始设置保存在两侧 `refresh-before.txt`。

两侧使用完全相同的测试APK SHA-256 `3ed038fdb25829b30874b07ae72c1a5b547f1d3174d8d0bcd877e99556e1db1a`。基线主APK SHA-256 `93972df5fe5ff93d55063cf0e0013b65c215acaa669ec794f1051572c8cbaa5e`；优化主APK见 `device-optimized-final/apk-sha256.txt`。基线选择原高度动画组合，优化选择生产新策略；渲染器来自对应主APK。

夹具：内存 reverseLayout LazyColumn，40条历史项；短答111、中文5185、混合Markdown11645 UTF-16单元。长答先显示最终长度减1600的前缀并预热2.5s，随后Default工作线程每33ms输入12单元；同一时刻派发真实View拖动/释放、等待惯性、回到底部、等待完整排空 `onSettled`，最后滚动历史。所有6次 `settled=true`、最终源文长度一致。独立上游生产线程避免主线程卡顿反过来放慢测试输入。

| 场景 | FrameMetrics P50 前→后 ms | P95 前→后 ms | P99 前→后 ms | >32ms 前→后 | gfxinfo 卡顿 前→后 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 短答 | 9.330→9.324 | 17.397→19.054 | 31.536→29.550 | 0.82%→0.68% | 1.17%→0.98% |
| 长中文 | 15.484→12.041 | 23.123→21.241 | 41.085→26.486 | 2.47%→0.23% | 0.44%→0.67% |
| 代码/表格/中文 | 578.952→42.769 | 826.096→107.983 | 1025.134→143.693 | 77.78%→72.84% | 36.09%→7.36% |

FrameMetrics样本数前/后：短244/294，中文890/872，混合108/464。系统gfxinfo涵盖预热与窗口绘制，FrameMetrics在预热后重置，二者统计口径不同；固定>16.667ms也不等价于Android deadline卡顿定义。混合场景>16.667ms比例77.78%→89.22%，虽然严重长帧下降，仍不能声称稳定60FPS。不能由回调样本数直接推导FPS。

Perfetto按 `Amber fixture pinned` 到 `Amber fixture history` 截取主线程（含拖动/惯性、回底和排空，不含历史/退出）。调度状态R/R+是可运行等待，不等于Future阻塞；S是睡眠/等待，不能仅凭调度跟踪归因锁。

| 场景 | 窗口 前→后 ms | 主线程Running 前→后 ms | R/R+ 前→后 ms | 单次Text measure最大 前→后 ms | GC次数/总耗时 前→后 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 短答 | 2291→2391 | 1201→1366 | 51.20→55.36 | 7.593→6.702 | 0/0ms→1/67ms |
| 长中文 | 7642→7597 | 5972→4746 | 84.62→228.34 | 24.157→7.835 | 12/306ms→4/157ms |
| 混合 | 13624→7614 | 13495→7071 | 60.48→166.75 | 162.646→15.428 | 41/801ms→16/444ms |

GC为ART运行统计前后差值，耗时是进程GC总工作时间，不是主线程暂停。分配量：短3.63→4.14MB，中文396.84→139.24MB，混合1379.59→553.51MB。短答优化侧发生1次67ms blocking-GC，其余5组为0；不隐藏该回归样本。调度可运行等待在长场景增加，后台工作竞争未消除。混合测量窗口缩短44%，但平均/尾帧仍高于一帧预算。

原始证据（本目录本机保留、被 `.gitignore` 忽略）：

- `device-baseline-controlled/`、`device-optimized-final/`：每场景JSON原始逐帧/阶段数据、进程内gfxinfo、sched/dalvik `.ctrace`、安装结果、设备信息、APK SHA。
- `device-comparison.json` / `.txt`：由 `scripts/summarize-chat-perf.py` 生成，含每阶段分位数、GC差值、主线程调度和Compose测量inclusive耗时。嵌套slice不可相加当作CPU时间。
- `chunk-layout-device-test.txt`、`chunk-layout-native-recheck.txt`、`chunk-layout-native-logcat.txt`：原生几何测试通过，三种宽度每行基线误差0，复查保留了具体高度/行数日志。
- 早期诊断目录只用于定位瓶颈：`device-baseline-final`、`device-optimized-mixed-diagnostic`、`device-optimized-chunk-probe`，输入生产线程/刷新率控制不同，不混入最终成对结果。

## 测试和复现

前一提交已通过187个定点JVM测试（0失败/错误/跳过），涵盖流式Unicode/替换/reveal、Markdown引用/样式、历史结构共享/索引、保护释放/虚拟化、取消/结束及持久化契约。此次只新增运行3个分块语义JVM测试，均通过；未重复整组187项。原日志 `verification.log`、`lifecycle-regressions.log`、新增 `chunk-streaming-build.log`。

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew \
  :app:testFullDebugUnitTest --tests '*StreamingTextChunksTest' \
  :app:assembleFullDebug :app:assembleFullDebugAndroidTest -PchatDeviceFixture=true --console=plain
scripts/chat-render-perf.sh baseline artifacts/chat-performance/baseline.apk \
  artifacts/chat-performance/optimized-test.apk artifacts/chat-performance/device-baseline-controlled
scripts/chat-render-perf.sh optimized artifacts/chat-performance/optimized.apk \
  artifacts/chat-performance/optimized-test.apk artifacts/chat-performance/device-optimized-final
# 可选 perfetto Python 包只用于读取已有本地trace。
artifacts/chat-performance/perfetto-env/bin/python scripts/summarize-chat-perf.py \
  artifacts/chat-performance/device-baseline-controlled artifacts/chat-performance/device-optimized-final \
  artifacts/chat-performance/device-comparison.json
scripts/chat-device-regression.sh artifacts/chat-performance/device-regression-final-v7
bash -n scripts/chat-render-perf.sh scripts/chat-device-regression.sh
git diff --check
```

测试Runner需 `-PchatDeviceFixture=true`。只使用自定义测试Application的独立 `files/chat-device-fixture/`，关闭实际应用启动恢复/后台调度；渲染测试纯内存。交互测试仅替换Provider边界，保留真实UI→ChatService/domain CAS→Room，假Provider不访问网络、不消费用户模型。测试还备份/恢复Activity的UI启动偏好和原剪贴板，模型DataStore测试前后做SHA比对。未使用会修改模型配置的旧emulator-only测试。

SDK arm64 adb在当前Darwin27 USB后端崩溃，脚本用 `arch -x86_64 /Users/hugo/Library/Android/sdk/platform-tools/adb`。MIUI可能冻结刚启动的测试进程，脚本仅一次shell前台拉起空测试Activity，没有修改系统freezer权限。

## APK与边界

优化主APK：`/Users/hugo/Desktop/git/AmberAgent/artifacts/chat-performance/optimized.apk`（最终测量对应二进制）。标准输出：`/Users/hugo/Desktop/git/AmberAgent/app/build/outputs/apk/full/debug/app-full-arm64-v8a-debug.apk`。

成对性能测试APK：`/Users/hugo/Desktop/git/AmberAgent/artifacts/chat-performance/optimized-test.apk`。交互测试APK：`/Users/hugo/Desktop/git/AmberAgent/artifacts/chat-performance/regression-test.apk`（后续仅修正交互测试入口，与成对性能夹具无关）。标准测试输出：`/Users/hugo/Desktop/git/AmberAgent/app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk`。

## 真实交互与持久化回归

最终 `device-regression-final-v7/` 的两个独立进程各 `OK (1 test)`（14.671s、3.806s）：

- 输入/发送真实UI点击，经生产CAS生成；停止在348字符，严格短于3785字符脚本回复，取消后Room与内存逐字相同。
- 产品长按菜单“重新生成”，完整3286字符长Markdown/代码/中文/emoji/组合字符与脚本源文逐字一致，Room持久化一致。
- 产品“选择并复制”→“复制全部”，剪贴板原文SHA-256 `1eac2e291640eb589cdda5a042008a4d60a4b9dcbc9fa8940e1e16e53fdbe0b0`；随后恢复原剪贴板。
- 切换到120节点历史会话：初载80，UI滚动触发加载至120，检查最早历史节点；切回原会话完整文本一致。
- 新应用进程（PID 27329→28171）重开原会话，Room和显示原文仍为3286字符，同SHA；最后删除本轮测试会话。
- 日志确认实际数据库仅为 `/data/user/0/app.amber.agent.graphite/files/chat-device-fixture/databases/amber_agent`。
- 用户模型设置测试前后SHA均为 `02a081c2b6956b0769efdc3af22ab775676f571bedf60c72272abed3354bee14`。UI启动偏好通过脚本备份恢复。没有清除应用数据或更改用户模型。

中间失败日志保留用于追溯：JUnit表达式方法返回值、Compose自动空闲等待和手动测试时钟未推进、IME退出后按钮位置改变均在测试入口修复；最终取消使用真实触摸，显式推进测试帧并按IME偏移校准，未直接调用服务伪装UI取消通过。

钉底生成、生成中拖动/惯性、回底排空、结束后历史滚动由6次真实渲染夹具执行完成；没有额外加入像素级阅读锚点位置断言。长消息虚拟保护释放/结构索引已有定点JVM断言；真实交互回归验证了历史分页与切会话，未从帧数字反推内部虚拟化一定触发。

未把模拟服务端结论推广到外部模型/网络；无真实付费模型请求。整段不换行或显式ParagraphStyle仍走单Text，极长消息可能卡顿。多个Text跨边界的原生手动拖动选区尚需单独验收，当前选择复制验证走产品“选择并复制”入口。


最终 `:app:assembleFullDebug` 成功（`final-main-build.log`）；`final-install-unlocked.txt` 为Success，`final-launch-unlocked.txt` 为Status: ok / COLD，`final-state-unlocked.txt` 确认应用进程存活、keyguard=false、peak=120/min=null。最终标准输出主APK SHA-256 `cf1982ed0aa9d52ad2e52f4f938a355c41bbef894799c9b38e56b593411beb3c`；与测量APK的差异仅为后续源码注释/调试行号及重打包，生产行为未继续更改。所有APK校验在 `final-apk-sha256.txt`。

正常应用两次启动按原有 `AmberAgentApp.incrementLaunchCount()` 增加了 `launch_count`，所以最终整个DataStore哈希为 `4447cbebe21028a21fb79ad189e6667f36576334b9de0753faa263a36fd4b38e`。`launch-count-only-proof.json` 的字节级证明：仅在内存将这一个protobuf整数减2、保留其余字节，恰好得到测试前SHA `02a081c2…bee14`；模型及所有其他设置完全相同。没有把计数写回手机。用于比对的临时设置二进制已删除，未提交用户设置。
