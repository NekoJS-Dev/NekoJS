# 分离 JAR 回退与数据恢复的重启验收

2026-10-16，26.1.2 NeoForge 26.1.2.71 / JDK 25.0.2，专属 `build/domain-closeout-rollback-installed-2`；四阶段各使用独立目录、端口和新克隆世界。当前 JAR 为 `20809213` 领域切换提交的实机绑定制品；旧 JAR SHA256 为 `ade011a0bb7252d93f6374c234ec9353dbf6105f1793c448a0f64d5d6771c52b`，未知其精确源码 commit，因此不推定它属于旧性能组或正式发布版本。

| 阶段 | JAR / 世界输入 | 实际运行结果 |
|---|---|---|
| 初始候选 | 当前 JAR，新世界 | 通过实际 `entity.pdata()` 写入 proof-1/revision=1，条件方块检查为 stone；保存并正常停止 |
| 世界推进 | 当前 JAR，克隆初始停机世界 | 写入 proof-2/revision=2，改为 gold_block；保存并正常停止 |
| JAR 回退 | 旧 JAR，克隆推进后的世界 | 实际 pdata reader 仍读到 proof-2/revision=2，方块仍为 gold_block；证明仅回退代码没有恢复数据 |
| 数据恢复 | 当前 JAR，完整克隆停机备份 | 运行前逐文件摘要等于备份；重启实际 reader 读回 proof-1/revision=1，方块为 stone |

各阶段另用原版 `data get entity … NeoForgeData` 确认真实保存结构含 `NekoJSPersistentData`，没有仅凭脚本内存值判定。四次正常 stop，exitCode=0。完整停止后的世界备份覆盖所有现存文件；复制到第二个独立目标的摘要也一致。原备份、推进后的世界和旧 JAR 回退克隆均保留，不覆盖唯一原件。

第一次验收因将 `say` 广播误当作直接 RCON 反馈而失败；原始日志已显示条件广播成功，但该轮仍保存为 NOTGREEN。修正验收器从实际服务器广播日志验证条件结果，在全新目录重跑四阶段通过。未修改生产代码或放松世界状态断言。

原始命令、连续日志、四阶段准备/结果、所有世界文件摘要及首次失败见 [rollback-evidence.zip](rollback-evidence.zip)；摘要见 [rollback-summary.json](rollback-summary.json)，完整性清单见 [rollback-manifest.json](rollback-manifest.json)。归档没有世界文件、JAR、口令或用户 profile。

这证明所列节点、JAR 字节和合成完整世界的恢复流程；没有外推到其他节点、任意玩家存档、断电原子恢复、全 profile 恢复、外部网络/Java 副作用或最终回滚政策。本轮不改变 pdata/default 路径/key/wire/schema，不需要数据迁移。最终发布和真实部署回退仍需要维护者明确决策。
