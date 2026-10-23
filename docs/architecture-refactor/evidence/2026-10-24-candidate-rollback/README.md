# 当前候选的制品与数据分离回退

2026-10-24，NeoForge26.1.2.71 / Java25。当前源码975496d3、JAR SHA256 `2206b92413bb4c9ac3f1d5f5cc2d7b3a9df95631e7defdd5c0d6ee368b9b2132`，对应[五节点安装候选](../2026-10-24-method-bounds/README.md)。沿用既有四阶段真实生产pdata与物理世界证明，使用全新隔离目录、世界和端口；四次RCON正常退出0，无强制清理。

| 阶段 | 制品 | 实体pdata | 实际方块 |
|---|---|---|---|
| baseline-initial | 已知旧d91ca974 | proof-1 / revision1 | stone |
| candidate-advance | 当前975496d3 | proof-2 / revision2 | gold_block |
| artifact-rollback | 旧JAR，沿用推进后世界 | proof-2 / revision2 | gold_block |
| data-restore | 当前JAR，恢复停服备份 | proof-1 / revision1 | stone |

旧JAR SHA256仍为 `8b7c6179ef00f442b041d95b5c379820144d101cd4479615d5f1cf19d5194e57`。真实entity.pdata()写读和物理NeoForgeData NBT、条件方块命令相互核对。停服复制完整世界，逐文件SHA比较；恢复前副本与备份完全一致，重复复制一致，原备份和推进后的原世界摘要始终保留。换JAR没有恢复世界，恢复数据才回到旧值。

归档包含当前实际JAR及1641个生产类绑定、原始四阶段命令/日志/进程结果、全世界文件摘要和精确执行工具，不含世界二进制、运行库、properties或完整客户端参数；实际测试RCON密码字节按UTF8/UTF16脱敏，制品保持原始字节。39条目，16,934,617字节。复核：`python docs/architecture-refactor/evidence/2026-10-24-candidate-rollback/verify.py`。本机完整世界与备份保留在build/closeout-method-bounds-rollback。

边界仅为这对已知候选、该实体pdata与完整世界夹具；不能外推任意旧版本、不兼容schema、世界外配置/文件/服务或插件任意副作用。未代签最终回滚策略或维护者发布结论。完整严格TSC5565条及重载313.8/308.6ms仍FAIL；客户端/TCP按独立当前制品证据验收，不据本项关闭票37。
