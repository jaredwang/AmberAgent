# Phase 4 最终独立审查与实际 UI 验证

独立审查者：phase4_final_review；非实现者，只读审查，不运行 Gradle 或操作设备。

整合源码审查和一次限定本轮 diff 的维护性审查通过。14 项保留问题均有实际 owner 接线和行为回归；没有增加来源预算、自动失败重试、兼容框架或泛化抽象。最终源码及 UI 审查均无剩余阻断。

## 实际发现并修复的 UI 问题

1. 搜索页滚动后，小标题栏坍缩且列表正文进入状态栏。旧截图 21、22、28 可观察。SettingSearchPage 局部改用 pinnedScrollBehavior，将 Scaffold padding 应用到列表视口并消费 inset；保留内部页面边距。没有修改共享 WorkspaceTopBar。最终截图 35、36 显示标题栏完整，正文在其下方裁切，状态栏无透字。
2. 320dp、font1.3 下，删除文案两行使三个操作按钮的视觉中心不齐。仅为编辑 sheet 底部 Row 增加 Alignment.CenterVertically。最终截图 37、38 确认中心一致；键盘上方三个操作完整可见。

独立审查者实际查看初始 11 张截图及最终 35–39 五张截图后确认闭环。供应商标题单行省略、说明两行省略、窄屏说明增行和删除文案换行是既有设计，不追加布局改造。

## 主 agent 实际验证

- Android 14 / API34，任务独占 emulator-5592，只读 AVD overlay。保留另一工作流使用的 emulator-5580。
- 基线 411dp/font1.0；中文 320/360dp/font1.3；最终英文 360dp/font1.3。最终 APK 的滚动、编辑、键盘与按钮均已截图核验。
- 最后一个配置服务关闭，返回/重进及真实进程重启后仍关闭。19-search-after-restart 为新鲜层级与截图；16-relaunch.xml 因 dump 失败后旧文件被拉取，明确排除。
- 假 key 的临时 Tavily 经实际 UI 添加、保存、重开；最终替换安装保留此配置；最终 UI 删除成功，39 仅剩原 Bing，仍关闭。没有触发真实付费 provider 调用。
- 结果数量：30 后点加仍30，1 后点减仍1，完成输入后恢复10。40–44 XML 记录实际值。
- 最终 FullDebug assemble 成功；apksigner v2 验证通过；替换安装成功；RouteActivity mResumed=true，PID6818；crash buffer 为空。安装包 SHA256 与最终制品记录一致。

原始截图/XML 保存在 results/ui；构建、签名、安装产物与设备状态分别见 phase4-final-apk.log、phase4-signature.txt、phase4-artifact.json、phase4-ui-verification.json。真实付费供应商、真实模型发起的完整会话和物理设备没有验收；不以模拟器、socket fixture 或 Compose 测试代替这些边界。
