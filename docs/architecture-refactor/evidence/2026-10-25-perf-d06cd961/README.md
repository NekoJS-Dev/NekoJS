# d06cd961 原政策正式性能：两组重载与启动均 PASS

本页绑定当前 HEAD `d06cd9612`（= 370e8105 UI resolver supplier 重构 + d38c14dd 混合引号导入名修复 + d06cd961 push 摘要标注修正）的原政策正式采样，按[交接任务](../../../research/2026-10-25-glm-next-tasks.md) §2 用当前确切产物与原采样流程重测基线。

## 绑定与条件

- 专属性能工作树 `D:/mcmodDemo/NekoJS-perf-d06cd961`（git worktree）；采样前后核对 rev 均为 `d06cd9612`，tracked tree 干净；`bench/perf/run/26.1.2` junction 指向 `versions/26.1.2/run`。
- 原采样器 `bench/perf/sample.ps1` SHA256 `1f9c5d14a1a586da4b3f1ca8e71b2407ce089ee8bee185418963e1b5745df0b4`，与 f236a983 / 6e43b827 轮一致，未改动；fixtures 与主仓库逐字节一致（`diff -r` 核对）。
- 固定 Gradle 缓存 `D:/mcmodDemo/NekoJS/.gradle-perf02`（`org.gradle.java.home` = JDK 25.0.2），端口 25871/25872，NeoForge 26.1.2；实测 JAR `nekojs-neoforge-26.1.2-1.1.0-preview3.jar` SHA256 `10e43cae8f92ed7c753aaedbff102d51523b44e5caedcaf9ffec25b319442e0c`，共 1648 个 class，全部由该工作树源码构建；world 由首个预热会话建立后按 harness 约定复用。绑定精度：原采样器经 Gradle `:26.1.2:runServer` 启动开发运行，不是把该 JAR 安装进独立服务端实测；编译输出与 JAR 的逐类一致支持制品关联，最终候选仍需以实际加载来源重新绑定。
- 命令（原口径，顺序执行）：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode reload  -Node 26.1.2 -Warmup 2 -Samples 5 -Reloads 5 -GradleUserHome D:/mcmodDemo/NekoJS/.gradle-perf02 -ServerPort 25871 -RconPort 25872   # 两组
powershell.exe -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode startup -Node 26.1.2 -Warmup 2 -Samples 5                -GradleUserHome D:/mcmodDemo/NekoJS/.gradle-perf02 -ServerPort 25871 -RconPort 25872   # 一组
```

- 默认系统线程策略（未用 HighQoS 或任何条件修改）；未改采样器、门槛、口径、日志刷新或完成时机；保留全部首值，未按成绩重试或择优。采样期间无并行构建/测试/游戏验收；唯一并行活动是主仓库上一个只读源码检查子代理（仅 git 只读命令）。

## 正式结果

| 组 | 全部正式样本 ms | 均值 ms | 原门槛 ms | 结果 |
|---|---|---:|---:|---|
| reload-1 | 301, 137, 163, 250, 248 | 219.8 | 285.3 | **PASS** |
| reload-2 | 335, 122, 246, 121, 246 | 214.0 | 285.3 | **PASS** |
| startup | 20180, 20989, 21110, 20475, 18948 | 20340.4 | 41173 | **PASS** |

- 17 条有效记录：reload 两组正式索引各 1–5，startup 预热 2 + 正式 3–7；全部 `forced_kill=false`、`killed=false`；startup 行显式 `timeout=null`，reload 行无该字段，均未标记任何超时。
- 九次独立会话（2 个 reload 会话 + 7 个 startup 会话）全部正常 RCON 停服，结束后 25871/25872 释放（仅余 TIME_WAIT）。
- 重载口径保持命令发送 → 原 ASCII marker（字典序最后的 server 脚本 load 行）；`rcon_rtt_ms` 另列于原始 JSONL，不参与门槛。

## 归因边界

26844b7e 曾以默认策略三组 PASS（244.4/248.2/23117.0），其后 6e43b827 又 FAIL（288.2/299.4/26174.8），跨会话波动已有记录。本轮 PASS 精确绑定 `d06cd9612` 源码与上述 JAR；不把降幅归因于任何单个源码改动（含本系列提交），不改动、不覆盖历史 FAIL 记录。历史 FAIL 与本页 PASS 并存，以本页为当前 HEAD 的原政策性能证据。

对 26844b7e..d06cd961 全部 88 个提交的只读路径复查结论：仅 9 个触碰每次重载执行路径，其中 7bea6d6d（跨代复用纯源码分析）、05afbd33（stamp 属性一次读取）、3ccea55a（共享虚拟 id 前置短路）均为减法；仅有的加法是 7e484118/7d17de4e 的反射包装（亚毫秒）与 39d405a6 的 Fabric 每 tick 定时器 flush（Fabric-only，与 NeoForge 测量节点无关）。probe 声明生成不在重载路径上（仅 `/nekojs probe` 命令与默认关闭的启动配置触发）。既有阶段诊断（模块准备非嵌套阶段约 5.51 ms/轮等）也解释不了 6e43b827 轮对门槛的超标。**本轮未作额外性能源码修改而通过**；两轮差异的根因未查明，不声称环境为唯一原因，也不据此预覆盖未来最终候选的性能结论。

## 未覆盖

票 37 的完整严格 TS（当前 1609 条 exit 2）、最终候选冻结的完整安装/客户端/TCP/分离回退验收与 1.2.0 包装复验仍开放；本页仅覆盖原政策性能门禁。原始样本见 [reload-1-samples.jsonl](reload-1-samples.jsonl)、[reload-2-samples.jsonl](reload-2-samples.jsonl)、[startup-samples.jsonl](startup-samples.jsonl) 与 [summary.json](summary.json)。
