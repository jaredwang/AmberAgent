# Phase 0 完成：计划与实际基线

基线137f57e2；分支refactor/modularization-phases-20261018。52个生产大文件/20个>1500逐项登记，阶段与小文件归属、状态不变量、UI矩阵已完善。独立审查 phase-0-plan-review 的3项必要修订已落地并复核通过。

实际验证：初始8类定点90项中9失败；指定ChatRuntimeOwnershipTest基础Application隔离真实Koin启动后，对两类17项重跑得到7个行为失败、0个Koin启动错误。未删改断言。3个运行停止/settled屏障归Phase2；4个输入附件/编辑归Phase3，精确可达链路见phase-0-failure-triage.md。Phase0完成表示基线已获得，不表示既存产品行为已全部通过。

当前源码 :app:assembleFullDebug :app:assembleFullDebugAndroidTest -PuiSmokeTest=true 构建成功。主APK app.amber.agent.graphite SHA256 2548a93942ea8b0ebb37edab6f9c60199777350fa9da5ee8b2a9ac4688134f47；测试APK efc983e23cc0059866a86ef255599e9fd2f46f9f788ec088d1ed26a594962d4c。安装到本任务read-only API34 emulator-5580成功，不覆盖原有产品包。

UI基线：截图管道已验证；当前首次启动停在splash，am start超时，进程仍在。UI agent正在核对main线程和资源压力，未把它报为启动/界面通过。该未解决门槛明确带入Phase1，Phase1完成前必须取得真实可交互页面和独立截图审阅；不以静态源码替代。5554与其他已安装产品未操作。

本阶段产品源码未改；唯一测试改动是@Config指定基础Application。计划、基线、审查与证据保存在本workflow目录。继续Phase1六个互不冲突工作包；仍未授权提交/推送。

后续UI基线闭环：指定现有homeSettingsDisplayThemeAndEmptyChat通过（34.824秒）；本次7张截图已逐张独立view_image，首页/设置/空聊天有PNG与UI XML；默认视口未发现可确认布局缺陷。首次GPU fence ANR作为基线限制保留；之后无重装的COLD启动9.225秒Status:ok。首页Council rail省略经源码/完整语义核实为设计，未修误报。
