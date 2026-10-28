# Phase 0 独立复核结论

当前 Android HEAD 0971aa41。原报告 15 项中保留 14 项；五 API 排除免费源的单次来源预算判断收回，不增加第六源或自动失败重试。MiniApp 独立 adapter 与旧内置开关合并仍属设计。

保留：显式空启用列表回填、Brave 日志认证头、SearXNG count 字段、Serper/SerpAPI news 接线、Tavily 时间参数、Firecrawl 多源优先级、已知日期丢失、智谱自动查询长度、正文读取取消、provider answer 丢失、URL/标题错误去重、配置型 Bing captcha 误报、DeepRead 内置开关、子代理兼容引用点击。

复核证据：当前生产源码、现有原始响应/真实延迟 socket 回归用例、前轮 /tmp/amber-search-review-20261025 复现和独立 reviewer phase0_final_triage。另有本仓独立审计 verify-search.md，可复用其已经备好的相关回归用例，但不接管其余审计范围。

收窄：时间过滤只给已确认支持的供应商接原生参数；日期只映射已知协议字段；SearXNG 不扩为全字段宽松解码；智谱不截断用户原始关键词；引用问题限兼容 citation 格式。

UI 未凭源码尺寸猜测增加问题。最终现场检查：320/360dp 与大字体供应商行；内置源说明截断；结果数量步进器；编辑 sheet 键盘与底部按钮；开关关闭/返回/重建；来源引用点击。
