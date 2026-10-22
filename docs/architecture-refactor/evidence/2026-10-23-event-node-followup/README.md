# 五节点事件依赖派生文件复验

生产源码 `1f877b0f`，运行 checkout `85f90856`。这是事件依赖修复的派生文件后续，不引入第二份手写事件规范。

首次现代 NeoForge 构建因旧事件 golden 缺少真实交易事件 import 失败，首个 XML 与构建日志保留。通过已有五节点生成任务更新后，逐字节比较确认：十个 server/startup 文件各增加同一行 `$VillagerTradeDeclarationEventJS`、`$VillagerTradeReloadEventJS` import；全部方法、参数、回调及 client 文件保持原样。生成模式的跳过不算验收。

随后普通命令（无 regenerate 开关）运行三个其他节点 `platformGateTest` 和 `:26.1.2:build :26.2.0:build`，**3m14s PASS，81 tasks**。两个 build 包含本节点普通 platform gate；最终五节点各 3 条门禁测试均 0 failure/error/skip，共 15 条。归档保存普通 XML、原失败、生成与普通构建日志、逐字节差异检查及两个新制品 SHA-256。

主代理技术审阅确认差异与生成器修复相符；没有代签维护者人工验收。此处不代表当前制品已安装联网验收，也不表示完整严格 TSC、性能或发布门禁通过。

复核：`python docs/architecture-refactor/evidence/2026-10-23-event-node-followup/verify.py`。
