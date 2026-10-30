# 全量 JVM 队列时间戳失败：生产/fixture 判定

结论：**测试 fixture 与实际序列化配置不一致，不新增 R4，不修改生产 serializer/queue。**

## 实际失败

最终首次全量执行中，ChatRunCoordinatorTest 的 `matching old Stop preserves input accepted while the cancelled handler settles` 在原有完整队列相等断言失败。新鲜 XML 时间为 2026-10-25T16:06:23.989Z：同一个 message id、parts、answer、mode 完全一致，只有 createdAtMs 从 1792944384065 变为 1792944384089（24ms）。之前该类的定点运行通过；时序差异不能被误判为 Stop 丢失输入。

该 case 在真实 queue writer 后重载 PendingMessageStore，然后比较完整 PendingUserMessage 列表；原断言有意义，保持不变。

## 精确原因与生产反证

- PendingUserMessage.createdAtMs 的默认表达式是 System.currentTimeMillis()。
- 对当前编译 class 的只读 javap 检查确认，生成 serializer 在 encodeDefaults=false 时比较实际 createdAtMs 与编码当刻重新计算的 System.currentTimeMillis()；若恰好同毫秒，则省略该字段。之后 decode 会重新计算默认时间，导致完整对象不相等。不存在字段的重新读取时间不代表新入队或内容丢失。
- 失败 fixture 在 ChatRunCoordinatorTest 中构造 PendingMessageStore(fileContext, Json)；plain Json 默认不编码默认值，因此允许上述时间相关省略。
- 生产唯一构造入口为 ChatModule 的 PendingMessageStore(context=get(), json=get())。AppModule 的 single<Json> 绑定 JsonInstant；core/agent-utils/Json.kt 明确 encodeDefaults=true。生产始终写出该字段，不存在该配置下的同毫秒遗漏窗口。
- 对当前 main/core/feature 的调用搜索未发现第二个生产 PendingMessageStore 构造或直接对 PendingUserMessage 编码的旁路。不能把任意可注入的 hypothetical Json 配置当作当前产品入口。

## 最小修正与验证状态

按主 agent 放行，仅将该 coordinator fixture 的 PendingMessageStore 参数改为实际 DI 使用的 JsonInstant，并加一句说明；其他 Json fake、生产代码和完整 timestamp equality 断言不变。不加 EncodeDefault 注解、不改动态默认、不增加 serializer 框架或冗余测试。

唯一 Gradle coordinator 应先跑 ChatRunCoordinatorTest，再按主 agent 决定跑完整 app 一次。本文作者未运行 Gradle；目前修正 ready，GREEN 结果等待 coordinator，不能预先填写。
