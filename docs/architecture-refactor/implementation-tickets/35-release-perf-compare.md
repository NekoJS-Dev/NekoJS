# 35: P4 性能复测与政策对照

**What to build:** 在主整合完成后按已确认政策复测 startup、Probe、reload、tick、Adapter 与内存表现，对比 P0 基线和最终结果，并给出是否满足政策、是否需维护者重裁决的可审计结论。

**Blocked by:** [04: P4 前性能发布政策确认](04-perf-release-policy.md)、[33: CI 用途子集与 Fabric processor 延期替代 gate](33-build-ci-processor-gate.md)、[08: 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](08-plugin-addon.md)、[18: PData 与 ClientData 数据同步路径保护和 generation 边界](18-data-sync.md)、[20: 管理命令权限、生命周期入口与阶段诊断结果](20-runtime-commands.md)、[23: Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](23-recipe-data-surface.md)、[24: Block/Item/Level/Player/Command/Capability/goal/实体行为既有事件面覆盖路径](24-gameplay-event-surface.md)、[25: DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](25-query-tools.md)、[30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md)、[15: 启动期注册、typed Builder 与连带注册垂直收口](15-registry-startup.md)、[21: Dynamic Registry 多人 prepare/ack/commit 门禁](21-registry-dynamic-sync.md)、[22: Villager Trades 声明事件与稳定查询](22-villager-trades.md)、[26: CLIENT 输入与 HUD callback 生命周期](26-client-input-hud.md)、[27: CLIENT GUI 与 render Adapter 资源呈现清理](27-client-gui-render.md)、[28: PostEffects 声明事件与运行 binding 分离](28-post-effects.md)、[29: Assets/Lang 资源生成与回读收口](29-assets.md)

**Status:** closed（2026-09-29 复测执行完毕,两个阻断维度均满足票 04 政策;结论与证据见
[2026-09-29-release-perf/REPORT.md](../baseline/2026-09-29-release-perf/REPORT.md) 与文末 Closure record）

**Closure record（2026-09-29）:**

- 执行：worktree `../NekoJS-mult-t35`（branch `ticket-35-release-perf`,行为源 `28283cc4`）,固定
  harness `bench/perf/`（本票未改动）与基线数据集,primary 节点 26.1.2,样本数与基线相同
  （startup 2 预热 + 5 正式 / bench 3 轮 / reload 5 / probe 1 预热 + 5 正式）,机器安静门每相位
  前判定通过（零等待重试,记录在 `raw/runner-logs/quiet-log.txt`）。
- **阻断维度判定（均值口径,不利样本不剔除,全部样本有效——无 forced_kill/timeout,无作废重采）**:
  - startup `wall_done_ms` 均值 **16,512.2 ms ≤ 41,173 ms → PASS**（余量 24,660.8 ms）;
  - reload `marker_ms` 均值 **278.0 ms ≤ 285.3 ms → PASS**（余量 7.3 ms,收窄,首发样本 353 ms
    主导,稳态 4 样本均值 234.3 ms 与基线持平;已列入报告 §5.2 供 release handoff 判读）。
- 观测维度（tick/adapter/eval/heap/probe）逐项差值与方差见报告 §4.3–§4.7;显著变化清单
  （含 probe 输出 389→390 文件、RCON reload 回执文本演进）见报告 §5,均不阻塞。
- 环境漂移如实留档:基线的隔离 `GRADLE_USER_HOME` 已不存在,按票面指示重建（冷缓存,发行版与
  modules-2 自本机用户级缓存引导）;当日 `maven.neoforged.net` 直连 TLS reset,构建期依赖获取经
  本机既有代理 127.0.0.1:7890 路由（仅构建期,采样计时通道不含网络解析）;Docker Desktop 停止、
  无 IDE 进程（比基线更安静,方向有利,已按政策标注不换口径）。失败构建尝试日志全部保留在
  `raw/runner-logs/`。
- 验证:Phase B `./gradlew :26.1.2:build :1.21.1:build :common:check` BUILD SUCCESSFUL in 7m 10s
  （含 `:common:test`、`:common:checkCommonIsolation` 与两节点 `:test`,安静机窗口执行）。

**Assignee:** zed-flash-35（main-session agent；GLM-5.3 subagent worktree）

**Claim record (2026-09-29):** worktree `../NekoJS-mult-t35` on branch `ticket-35-release-perf`（基于 mult HEAD）。预计改动范围：按票 04 已关闭的政策(2026-09-28 维护者确认)复测——startup/reload 各 ≥5 正式样本对照阻断阈值(≤41173ms / ≤285.3ms,均值口径,固定 harness `bench/perf`、同口径热缓存+复用 world、env-snapshot 同字段)、tick/adapter/eval/heap/probe 五维观测记录并对照基线差值方差;forced_kill/timeout 样本作废重采,不利样本不得剔除;机器安静前置(与并行代理错峰,采样窗口独占);`baseline/2026-09-29-release-perf/` 证据。不设新阈值不改政策数字。

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- P4

## Acceptance criteria

- [x] 最终采样复用 P0 基线与已确认政策的节点范围、负载、预热、重复次数、统计口径和样本规则；任何必要偏离都记录原因与可比性影响。
  （同 harness/数据集/节点/样本数/均值口径/不利样本不剔除;偏离仅为环境面——隔离 Gradle home 重建+代理路由、机器更安静——均记录于报告 §2,口径本身未动。）
- [x] startup、Probe、reload、tick、Adapter 与 heap/memory 的最终观测逐项与 P0 基线对照，并保留原始样本、汇总方法和离群值处置记录。
  （报告 §4.1–§4.7 逐维对照;原始 samples.jsonl/CSV/全文日志在 `raw/formal/`;离群处置:startup 预热 1 与 probe 正式 4（1,393 ms）均保留并单列,无剔除。）
- [x] 复测结果逐项套用已确认政策，明确 pass、fail、not applicable 或需维护者重新裁决；实现者不新增或修改阈值数字。
  （startup PASS / reload PASS / 五个观测维度按政策不阻断不设阈值;未触碰任何政策数字。未采样范围（secondary/experimental/CLIENT/冷缓存等）按政策属不适用外推,报告未越界。）
- [x] 环境、数据集、初始化状态、样本剔除和执行中断留档；不得选择性删除不利样本。
  （每相位 env-snapshot、安静门记录、网络失败尝试日志、channel-test 全部留档;全部 21 个采样会话有效,无剔除、无作废重采。）
- [x] 每个显著变化或政策失败有 owner、定位方向、是否阻塞 release 的结论和必要的维护者重裁决记录。
  （报告 §5 六条判读清单（owner/定位/阻塞结论）;无政策失败,故无强制重裁决记录;reload 余量收窄已标注供票 37 handoff 与维护者判读。）
- [x] 政策或测量口径在复测中不可临时调整；若环境或实现变化使政策失效，停下取得维护者新确认后再继续。
  （政策与测量口径全程未调整;环境网络问题只以代理路由解决构建期依赖获取,不涉及采样口径;未发生政策失效情形。）
- [x] 若后续发生新的性能相关行为改动，明确必须重新复测或重新裁决，不沿用过期对比结论。
  （报告 §8 列明重测触发条件;本结论绑定 `28283cc4` 与本次环境口径。）

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [版本与加载器支持矩阵规格](../specs/02-support-matrix.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [04: P4 前性能发布政策确认](04-perf-release-policy.md): 进入 P4 复测前必须已有维护者确认的阈值/不设阈值政策、适用范围与失败规则；复测只执行和对照政策，不临时造政策。
- [33: CI 用途子集与 Fabric processor 延期替代 gate](33-build-ci-processor-gate.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [08: 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](08-plugin-addon.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [18: PData 与 ClientData 数据同步路径保护和 generation 边界](18-data-sync.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [20: 管理命令权限、生命周期入口与阶段诊断结果](20-runtime-commands.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [23: Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](23-recipe-data-surface.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [24: Block/Item/Level/Player/Command/Capability/goal/实体行为既有事件面覆盖路径](24-gameplay-event-surface.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [25: DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](25-query-tools.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [15: 启动期注册、typed Builder 与连带注册垂直收口](15-registry-startup.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [21: Dynamic Registry 多人 prepare/ack/commit 门禁](21-registry-dynamic-sync.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [22: Villager Trades 声明事件与稳定查询](22-villager-trades.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [26: CLIENT 输入与 HUD callback 生命周期](26-client-input-hud.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [27: CLIENT GUI 与 render Adapter 资源呈现清理](27-client-gui-render.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [28: PostEffects 声明事件与运行 binding 分离](28-post-effects.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。
- [29: Assets/Lang 资源生成与回读收口](29-assets.md): 最终整合或复测必须消费该必选域的已验收输出，不能遗漏其契约、能力或迁移证据。

## Scope and coordination

**Rationale:** 它把政策执行、最终复测和基线对照连成一个可审计 release 输入，且不在 P4 临时决定阈值。

**Coordination:**

- 执行、Probe、平台和数据 owner 确认负载与样本；若出现政策边界问题，交回维护者裁决。
- 39 已通过 24 传递覆盖：若性能政策范围包含 Item/Block modification，复测消费 39/24 的既有 fixture 与样本，不为了重复阻塞而另加直接依赖。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
