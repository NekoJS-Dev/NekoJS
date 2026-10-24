# 6e43b827的制品与数据分离回退

已知d91ca974旧JAR与当前6e43b827确切26.1.2 JAR，在全新隔离目录完成四阶段、四次RCON正常停止与exit0，没有强制清理。通过公开pdata脚本、实体物理NBT和实际世界方块共同验证，完整世界副本只在进程停止后复制。

| 阶段 | JAR | 实际pdata | 世界方块 |
|---|---|---|---|
| baseline-initial | d91ca974 | proof-1 / revision1 | stone |
| candidate-advance | 6e43b827 | proof-2 / revision2 | gold_block |
| artifact-rollback | d91ca974 | 仍为proof-2 / revision2 | 仍为gold_block |
| data-restore | 6e43b827 + 停服备份 | proof-1 / revision1 | stone |

换回旧制品不恢复数据；只有显式恢复停服完整世界备份才回到旧值。初始备份、推进后的原世界和独立重复复制副本均保留，完整路径→SHA清单一致。恢复前副本与备份逐字节相同，两次复制摘要相同；不把这些事实外推成任意旧版本、任何custom registry存档、世界外文件/网络/Java副作用或维护者最终回滚政策。

当前JAR SHA为7b0dd154…，1642个生产class与[当前安装/源码候选](../2026-10-24-owner-stamp/README.md)一致；已知旧JAR为8b7c6179…。39项、16,938,938字节归档包括当前真实JAR、四阶段原始命令/日志/结果、备份摘要和精确工具；world二进制、libraries、properties和完整启动参数不入档。原封存工具实际替换文件数为0、redactedOriginalHashes为空；后续私有模式扫描见[验收汇总](../2026-10-24-owner-stamp-acceptance/README.md)，不把空映射说成已有替换前原摘要或公开验证器可独立重做私有扫描。此处没有给未记录的实际类加载来源添加CodeSource探针；制品文件核对与其他当前候选安装/TCP/渲染加载证明各保留自身范围。

`python docs/architecture-refactor/evidence/2026-10-24-owner-stamp-rollback/verify.py`实际PASS：四阶段/四正常退出、物理pdata及方块结果、当前JAR和全部class绑定均通过；本机还重新核对四个阶段mods目录里的实际JAR摘要，以及保留的备份、重复复制和推进世界三个完整目录的全部文件摘要。缺少本地目录时明确输出localSnapshots=NOT PRESENT和localArtifactsChecked=0，仅验证封存记录，不能冒称重验未归档世界/JAR或重新运行游戏。

本项补齐当前候选的已知版本对、26.1.2完整世界夹具范围。完整严格TSC5537和重载288.2/299.4ms仍FAIL，票37仍in-progress；无版本切换、PR、push、远程上传或维护者代签。
