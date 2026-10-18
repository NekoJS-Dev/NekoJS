# 相同制品的工程位置对照

**结论：D 盘启动 PASS，重载两组仍 FAIL；总目标没有完成。** C 盘原失败全部保留，不把位置切换描述成源码性能修复。

## 控制与绑定

同一源提交 `606d1fff6b14e4a513bf16e247b0ec37437195c6`，主线生产对应 `280128e1`。C 盘性能树位于 `C:/Users/11515/.codex/worktrees/domain-perf-0965/NekoJS-mult`；新建普通 Git worktree `D:/mcmodDemo/NekoJS-perf-606d1fff`、分支 `codex/perf-project-location`，用于靠近原工程位置的测试。它不含需要合并的新源码。

D 盘仅运行 `:26.1.2:classes :26.1.2:jar :26.1.2:prepareServerRun --offline --console=plain`，PASS，13 秒，25 tasks（16 executed / 9 from cache）。两边 **1,625 个实际编译类逐项相同**，整个 JAR 的 SHA-256 也相同：`6645e07d9f96f172d1d035bfdaaf26891639453d847bdb96dbc681ac244529f1`。既有完整 common isolation／五节点验证见[同制品验证记录](../2026-10-19-source-map-decoder/README.md)，没有因移动位置再声称跑过一遍全矩阵。

启动前已核对两树 clean、精确 HEAD、原 sampler／fixture，以及 run 目录和所有祖先／子项无 symlink/junction。C 盘已停服的 world/config/mods、server.properties、eula 共 **42 个文件**复制到尚不存在的 D 盘目标，并逐项比较摘要；没有覆盖已有目标或归档 world NBT／凭据。sampler 仍按原策略复用 world，首次及所有冷值照常保留。

唯一的控制器文本差异是 `worktree` 常量。sampler SHA-256 仍为 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`；六脚本负载、JDK 25.0.2、Gradle cache、25871/25872 端口、两重载组及启动 2+5 计划均相同。没有 JFR、外部 QoS controller 或系统策略修改；期间无并行本任务构建、测试或其他游戏。

盘符、路径深度、项目级缓存状态及会话时点不同。两 cohort 顺序执行，没有做随机轮换或 C→D→C 回转；因此**不能只根据差值认定路径长度是根因**。这也不能替代真实用户机器的性能接受。

## 原标准结果

| D 盘组别 | 全部正式值（ms） | 均值 | 原门槛 | 判定 |
| --- | --- | ---: | ---: | --- |
| reload-1 | 431 / 357 / 296 / 309 / 197 | **318.0** | 285.3 | FAIL |
| reload-2 | 381 / 329 / 294 / 309 / 279 | **318.4** | 285.3 | FAIL |
| startup | 25226 / 25093 / 25112 / 24869 / 24709 | **25001.8** | 41173 | PASS |

预热为 24656 / 24545 ms，不纳入正式均值但完整保留。17 行全部有效，十次重载 COMMIT，九会话正常 RCON stop，无 forced kill，所有游戏对应 Gradle 正常完成。保留首值及原 marker 口径；没有用 RCON 自身耗时代替 marker。

同制品 C 盘结果为重载 **601.2 / 527.4 ms**、启动 **60346.8 ms**，均 FAIL。它们没有失效或被 D 盘结果覆盖。位置对照支持继续控制运行环境，但 D 盘本身仍不能使重载通过；不基于较快单值选样或宣布整体达标。

## 复核与后续

```text
python docs/architecture-refactor/evidence/2026-10-19-project-location/verify.py
```

复核 PASS：69 项、**1,410,665 bytes**，SHA-256 `1cc5b40b614cf4fbc2aebd855c46fcce37d9f0fd5b7ebbf96ee005eecf43a65e`。验证器检查控制器仅改位置、C/D 源／类／制品相同、sampler／fixtures、复制前置记录、17 行、九次正常停服及全部原门槛均值。

为避免重复提交相同的 17 MB JAR，本归档引用已提交的 `../2026-10-19-source-map-decoder/evidence.zip`，并绑定其完整 SHA-256；**复核需要两个目录同时存在**。它从引用归档中取出真实 JAR，逐类核对 D 盘绑定，不能仅凭 metadata 声称制品一致。D 盘 raw 日志／环境快照／samples、原工具及前后绑定均在本归档内；RCON 脱敏文件另记原摘要，不包含密钥或实际 world 文件。

后续先在明确、可比较的运行条件下缩小实际重载 CPU 工作，保留 schema／当前代诊断和真实路径安全复验。普通配方 apply 已复用 datapack JSON 解析，但仍依赖当前 registry 做 codec 校验；不得仅为过门槛跳过这一步。Luna 另发现 baseline map 与回调共享可变 JSON 的风险，需要独立行为复现与失效边界证据后再考虑缓存，而不是直接把 decoded Recipe 跨周期复用。本阶段没有改动配方行为或声称该风险已修复。
