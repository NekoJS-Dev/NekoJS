# 确切候选的制品与数据分离回退

NeoForge 26.1.2.71 / Java 25，四个隔离服务器阶段全部通过、四次正常退出 0。旧候选 `d91ca974` JAR SHA256 `8b7c6179ef00f442b041d95b5c379820144d101cd4479615d5f1cf19d5194e57`；新候选 `4405b0b0` JAR SHA256 `e1fdc7d59b8b91232f50f956cfe8022eb02ebc45fa6e5d86e1775e61224fd763`。

| 阶段 | 制品 | 实际实体 PData | 实际世界方块 |
| --- | --- | --- | --- |
| baseline-initial | 旧 | proof-1 / revision 1 | stone |
| candidate-advance | 新 | proof-2 / revision 2 | gold_block |
| artifact-rollback | 旧，沿用推进后的世界 | proof-2 / revision 2 | gold_block |
| data-restore | 新，恢复停服备份 | proof-1 / revision 1 | stone |

通过真实生产 `entity.pdata()` 写读，并用 RCON `data get entity ... NeoForgeData` 核对物理 NBT、条件方块命令核对世界内容。旧候选停服后复制完整世界，逐文件 SHA 核对；新候选推进后再换旧 JAR，数据保持推进值，证明换 JAR 不会自动回滚世界。恢复原停服备份后，新候选读取原值。启动前恢复副本与备份摘要完全一致；重复复制相同，原备份与推进后的原世界均保持原摘要，没有覆盖或删除它们。

运行 `python docs/architecture-refactor/evidence/2026-10-22-candidate-rollback/verify.py` 可离线核对封包、四阶段制品身份、实际命令响应、正常退出和备份/副本摘要关系。完整原始记录与操作脚本封存，不含 world 二进制、JAR、依赖或 server.properties；本机原始世界和备份保留在 `build/closeout-4405b0b0-rollback`。

边界：仅证明这对已知候选、该实体 PData 和完整世界夹具。两候选的数据/网络生产实现未变，不能外推至任意旧发行版或不兼容数据 schema；世界外配置、外部文件/服务、插件任意副作用也不在此数据回退范围。此演练不等于完整 TSC、性能、最终发布或维护者人工验收。
