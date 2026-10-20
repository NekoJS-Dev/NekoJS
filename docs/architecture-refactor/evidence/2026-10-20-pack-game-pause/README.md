# 完整游戏成本观测：原版空服暂停导致中止

`cc55c4ce` 的 v3 原生 Fabric 26.2 观测已运行并失败，不能标为五十次重载通过。修复后的 SERVER 计时器已使 1/4/8/4 包阶段及 window10/20/30 的即时任务检查通过；随后 window40 的一个包没有满足即时任务执行一次断言。共 44 次 COMMIT/no errors，服务端 RCON 正常停服、launcher exit 0、无强制终止。

原始日志在 19:33:28 明确记录 `Server empty for 60 seconds, pausing`，实际隔离配置默认为 `pause-when-empty-seconds=60`。之后观察器 `checkedTicks` 固定为 643，RCON 仍执行 reload38/39/40。原生 tick 已停止，依赖 tick 的定时器自然不能保证在后续一秒窗口执行。该失败不证明定时器仍丢任务；也不能因为已找到测试条件错误而把本次失败改成通过。首次观察器未记录失败计数，无法从原始断言区分零次与重复执行。

后续应只在 owned 空服观测 fixture 中禁用原版空服暂停，并增加 tick 前进与具体次数诊断，再完成原计划的 1/4/8、50 次重载和禁用检查。生产暂停策略、计时器协议及正式性能门槛均不修改。

实际 Engine 报告 `Interpreted`、`supportsCompilation=false`；GraalMC 25.1.3.7 已加载，但不能把模组存在当作 guest 编译生效。唯一 PID 为 2324。八个采样窗口及全部原始日志、脚本、生产类摘要、观察器源码/摘要在归档内；私有 server.properties、存档及环境未收录。独立复核运行 `python -X utf8 docs/architecture-refactor/evidence/2026-10-20-pack-game-pause/verify.py`。
