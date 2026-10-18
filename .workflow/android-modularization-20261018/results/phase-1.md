# Phase 1 · 协议、设置与首页职责归位

状态：完成。独立subagent最终源码/调用链/截图review为PASSED_WITH_EXPLICIT_EXTERNAL_GATES；已进入Phase2。

## 职责与生产接线

- Intelligent UI：wire声明、parser、唯一CardState与process store分开；renderer按布局/文本/状态/输入/数据/媒体/语义/action分类，唯一dispatcher/递归参数/回调不变。协议与renderer归一化源码等价核对零增删。
- Memory：route保留条件订阅、唯一编辑草稿/冲突/operation事件，现有VM/Repository继续唯一CAS/durable owner；各设置子页、Library rows、Dialogs与纯文档投影分开。
- Provider：Detail唯一草稿；纯字段/组件/协议与endpoint策略分开，Android文档launcher仍在原Lazy item；OAuth进一步归Phase5。Model settings单草稿表单两项按内聚性保留。
- Model：Selector、Accordion、Query、Rows、Presentation与主列表分别归属，选中/收藏writer不变。
- Home：页面持搜索/focus/导航，Header/Rail/List/Row/Search分工；Council创建和Continue持久存在性核查回VM命令。Profile改为自身字段级VM命令，不依赖HomeVM更新整份Settings。明确Koin creator factory与Profile绑定。

上述原超限P1入口全部降到1500以下；行数只是结果，owner和调用链见P1包报告与phase-1-independent-review.md。ProviderConfigTools主包已明确Phase5，未借表单拆分声称工具命令完成。

## 已复现并精准修复

- Model分组gap坐标与真实发项不一致；currentModel缺remember key；follow捕获旧位置与header下界；Elvis默认展开优先级导致显式false无效。四个实际输入/点击/滚动用例及真实折叠断言取得红证据后修复，无新状态层。
- Home创建取消后留空占位；Room提交后返回取消窗口丢insert owner；旧restore清理/cleanup初检后restore拒绝；Council已接管未返回Ok时误删。短insert+mark NonCancellable，gate/room网络保持可取消；finally仅清空占位，真实peek owner/epoch共同限制归属。真实Room owned-row红回归已闭环。
- footer在320dp/font1.5被挤成单字竖排，已以自然换行FlowRow修复；短label保持原SpaceBetween间距和callback。
- Compaction静态文案双%%，仅同一资源key六语言改literal percentage并formatted=false，无设置/压缩策略改动。

## 实际验证

- 当前相关JVM合并证据100项通过，覆盖协议/解析、Memory投影/懒列表/CAS、Provider候选/转换/default endpoint/OAuth config/context输入、Home路由/流/compact布局/取消/restore/真实Room、Model真实Compose交互、Koin全graph。证据phase-1-jvm-summary.json；后续只对新增/修正点复测，未为数量重跑无关检查。
- main/androidTest Kotlin compile、fullDebug app与test APK均构建成功；当前最终SHA见phase-1-acceptance-apks.json（study-build.log、resource-build.log及final-fix-build.log）。未提交推送发布。
- second-run Home compact真实回放OK（搜索展开/过滤/清空/收起），09/10/11截图已独立目视。
- final-run Intelligent5 OK；A/B新增断言随最终prefix修复仅复测Study1 OK（6.936s）；Memory2 OK（6.874s）；single-%资源截图/XML实际确认。Intelligent为生产card+本地callback；不能冒充真实provider发送。
- Profile与Memory全部子页以及真实IME editor已由实际AX路径取证；没有修改开关/凭据，临时未保存draft已取消，原compact fixtures已清。

## 排除的误报与测试承载

- Compose scroll-range raw值不是Lazy item index；Model改为真实header.top/badge.left物理锚点断言，纠正后四项均真实红再修复。
- TextLayoutResult保留paragraph可用宽766px、Text缩到550px，字形right548.5px。raw hasVisualOverflow会误报；改校验逐行真实glyph边界、高度、省略/丢行，仍记录原容器诊断，生产正常title未改。
- Home header在HEAD已随列表滚动，旧device test“固定search”前提过时；只让测试滚回顶部后操作，保留全部过滤/清空/收起断言。
- 新Memory对话框Robolectric测试两轮在setContent idle超时、未进入业务断言；生产窗口/Insets/indicator与HEAD相同。完整迁到真实Android窗口，disabled字段按精确EditableText查询，避免要求不可编辑字段暴露SetText；label与EditableText分别识别，原draft/错误/回调/禁用断言不减。两项真实设备方法均已通过，稳定07/08截图已确认原draft/错误提示/Save-Cancel禁用断言及像素可读。

## 已知后续与验收边界

Phase0确证的3个Chat runtime settlement/stop和4个input attachment/edit bug分别留在既定Phase2/3，不是本Phase引入，也未删除原断言。Council域task注册半提交在Phase4治理；本Phase已保护被其接管的会话不遭新回收误删。

未声称全仓所有测试、三个flavor构建、真实provider/OAuth、物理设备验收通过。它们属于后续相应阶段或最终Phase6，实际不可用的门槛将明确报告。当前不能把尚未完成的Phase2–6写成完成。

Study圆标额外精准修复：独立目视确认A/B prefix Text继承大lineHeight导致22dp固定圆裁切，只有该11sp Text显式lineHeight11sp，新增同一glyph断言检查A/B。新06字母居中且完整；没有扩展为字体框架/缩放状态。
