# 当前候选三节点安装客户端渲染

源码975496d3，三个NeoForge节点的当前生产JAR均完成真实安装客户端复验，30张原图由主代理逐张查看；13次原生F3+T按键派发，六个进程全部正常退出0，无强制清理。原始采集结果保留screenshotsReviewed=false，之后独立pixel-review记录实际查看结论，不覆盖采集时状态。

| 节点 | 阶段/原图 | 原生F3+T | 生产类绑定 |
|---|---:|---:|---:|
| 1.21.1 | 16 | 7 | 1570 |
| 26.1.2 | 7 | 3 | 1641 |
| 26.2.0 | 7 | 3 | 1641 |

legacy覆盖invert、原生资源重载保留、失败候选后无set保留及恢复、显式clear/退役/未激活状态、原版creeper链替换和退役时不误清。现代两节点覆盖clear/blur、资源重载、失败保留和恢复。所有壁面ROI均位于教程/聊天/HUD以外；完整原图保留了这些原生提示，未裁切或改图冒充验收。沿用既有ROI与判断，逐图摘要及像素度量复算通过。

每个客户端核对实际ModList、CodeSource、class resource与生产JAR字节；NekoJS来自JAR，只有验收helper作为source mod。后置binder逐项核对1100个生产/构建源码与提交、全部common/node class与客户端/服务端实际JAR。legacy客户端使用普通JAR（b776a790…），服务端使用server分类JAR（fe0d89d9…）；两现代JAR为2206b924…、14282b90…，与[同批完整候选](../2026-10-24-method-bounds/README.md)和[当前TCP验收](../2026-10-24-candidate-tcp/README.md)一致。

归档165条目、24,982,875字节，包含30原图、原始阶段/命令/日志、helper源码/编译class、精确工具与独立绑定。初版独立verifier把RGBA转为RGB再比较原通道度量而失败；原工具/失败日志和初始包摘要保留，修正为读取原通道后复算通过，图像、ROI、门槛及已记录度量未改。legacy两份实际JAR同包；现代JAR按归档摘要分别引用同源码[正式性能包](../2026-10-24-method-bounds-performance/README.md)与[TCP包](../2026-10-24-candidate-tcp/README.md)，复核需要这些同仓归档。运行 `python docs/architecture-refactor/evidence/2026-10-24-candidate-render/verify.py` 独立核对字节、制品/加载证明、原图度量、13次派发及六次正常退出；需要NumPy和Pillow。不含world、properties、完整启动参数或运行库，实际RCON密码字节按UTF8/UTF16脱敏。

loader沿用既有缓存：legacy21.1.227，26.1.2两端26.1.2.71，26.2.0客户端26.2.0.57/服务端26.2.0.75。ModDev helper启动器、synthetic native按键、RenderFrameEvent.Post捕获不等于外部官方安装器、物理键盘/显示器present或维护者人工验收。

此项将当前候选的该范围渲染复验补齐，不代签发布。完整严格TSC5565条和原门槛重载313.8/308.6ms仍FAIL；票37仍in-progress，票28工程closed的范围不扩大。
