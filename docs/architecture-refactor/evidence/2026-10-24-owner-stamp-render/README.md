# 6e43b827三节点安装客户端渲染

三个NeoForge节点使用确切6e43b827生产JAR，30个阶段及原图全部通过。主代理实际逐张打开30张原始PNG，再执行原有ROI/阈值检查；三套客户端与服务端共六个进程均正常退出0，无强制清理。原始run-result继续保留screenshotsReviewed=false，独立agent-image-review与pixel-review记录后续技术复核，不覆盖采集时状态或代签维护者。

| 节点 | 原图/阶段 | 原生F3+T调度 | 生产class |
|---|---:|---:|---:|
| 1.21.1 | 16 | 7 | 1571 |
| 26.1.2 | 7 | 3 | 1642 |
| 26.2.0 | 7 | 3 | 1642 |

Legacy实际图像覆盖invert、资源重载保留、失败候选保留/恢复、显式clear、退役、未激活状态及原版creeper链接管后不误清。资源链能力仍为既有resources-only范围，不声称任意inline定义均可激活。两个现代节点覆盖clear/blur、资源重载、失败保留及恢复。完整原图仍有聊天安全提示、教程和HUD，墙面ROI位于它们之外；未裁切或修改原图冒充验收。13次调度为helper对原生KeyboardHandler的合成按键，不是物理键盘输入；RenderFrameEvent.Post捕获也不是显示器present或官方安装器验收。

每个客户端核对ModList、CodeSource、class resource及JAR字节，NekoJS从生产JAR加载，只有helper是source mod。后置binder验证1101个生产/构建源码与6e提交、全部生产class和两端JAR。legacy普通client JAR为33550fc8…、server分类JAR为42737343…；现代分别为7b0dd154…与bc4aaeeb…，与[初次六制品清单](../2026-10-24-owner-stamp-handoff/README.md)和[当前TCP](../2026-10-24-owner-stamp-tcp/README.md)一致。loader沿用legacy21.1.227、26.1.2两端26.1.2.71、26.2客户端26.2.0.57/服务端26.2.0.75。

封存168项、25,002,021字节，包含全部30原图、阶段/命令/日志、helper源码/class、三份实际查看记录及独立绑定。legacy两份JAR同包；现代按摘要引用同仓[正式性能包](../2026-10-24-owner-stamp-performance/README.md)及[TCP包](../2026-10-24-owner-stamp-tcp/README.md)。不含world、properties、完整启动参数或运行库。原封存工具实际替换文件数为0、redactedOriginalHashes为空；后续私有模式扫描见[验收汇总](../2026-10-24-owner-stamp-acceptance/README.md)，不把空映射说成已有替换前原摘要或公开验证器可独立重做私有扫描。

`python docs/architecture-refactor/evidence/2026-10-24-owner-stamp-render/verify.py`实际PASS，需要NumPy、Pillow、本仓6e Git对象与上述同仓归档；重算原图ROI、30阶段/13调度/六正常退出，逐个检查1101个Git blob、LF/CRLF工作摘要、制品/class/加载证明。它只验证封存物，不重新运行游戏，不把主代理查看记录当作维护者签字。完整严格TSC5537及重载288.2/299.4ms仍FAIL；票37仍in-progress，没有PR、push或发布。
