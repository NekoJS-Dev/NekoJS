# 34: P4 五节点整体验证与能力矩阵收口

**What to build:** 在主整合完成后执行一次跨五节点的 P4 总体验证，汇总 build/check/artifact、contract/golden、runtime smoke、source trace、coverage ledger、能力矩阵和旧新对照，形成 release 是否可继续的判定。

**Blocked by:** [33: CI 用途子集与 Fabric processor 延期替代 gate](33-build-ci-processor-gate.md)、[04: P4 前性能发布政策确认](04-perf-release-policy.md)、[39: Runtime Item/Block modification 候选计划与 snapshot ownership](39-item-block-modification.md)、[08: 真实外部 PluginAddon 从 discovery 到贡献消费与 reload 存活](08-plugin-addon.md)、[18: PData 与 ClientData 数据同步路径保护和 generation 边界](18-data-sync.md)、[20: 管理命令权限、生命周期入口与阶段诊断结果](20-runtime-commands.md)、[23: Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径](23-recipe-data-surface.md)、[24: Block/Item/Level/Player/Command/Capability/goal/实体行为既有事件面覆盖路径](24-gameplay-event-surface.md)、[25: DataMap 与 EntitySelectors 查询 binding/Adapter/declaration 路径](25-query-tools.md)、[30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md)、[15: 启动期注册、typed Builder 与连带注册垂直收口](15-registry-startup.md)、[21: Dynamic Registry 多人 prepare/ack/commit 门禁](21-registry-dynamic-sync.md)、[22: Villager Trades 声明事件与稳定查询](22-villager-trades.md)、[26: CLIENT 输入与 HUD callback 生命周期](26-client-input-hud.md)、[27: CLIENT GUI 与 render Adapter 资源呈现清理](27-client-gui-render.md)、[28: PostEffects 声明事件与运行 binding 分离](28-post-effects.md)、[29: Assets/Lang 资源生成与回读收口](29-assets.md)

**Status:** in-review（12 项 AC 全部附证据标注：本票证据包 `baseline/2026-09-29-release-p4/`。五节点 build/artifact/gates、架构审计、golden 完整性、全套件、GameTest 冒烟本轮全绿；两项 release 硬阻塞如实记录——probe-types 预存红(F1)与性能 gate pending-35(F-perf)；owner-deferred 真机窗口未伪造、按域阻塞满验证。性能节以票 35 落地报告为准（`baseline/2026-09-29-release-perf/` 截至本票收口仍未出现），维护者验收后闭合）

**Assignee:** zed-flash-34（main-session agent；GLM-5.3 subagent worktree）

**Claim record (2026-09-29):** worktree `../NekoJS-mult-t34` on branch `ticket-34-release-p4`（基于 mult HEAD）。预计改动范围：五节点 build/check/artifact、contract/golden 一致性审计、runtime smoke 汇总(消费各票 baseline/evidence 已有 transcript+补必要缺口)、coverage ledger 汇编、能力矩阵收口、物理架构一致性审计(实施交接单规则自动清单+例外说明)、跨领域真实集成四链路核对、性能 gate 状态对照(消费票 04 政策与票 35 结果——35 并行进行时本票先出其余部分,性能节以 35 为准)、`baseline/2026-09-29-release-p4/` 证据。不代替各域票的验收;失败按节点/输入/期望/实际/owner 记录并阻塞 release,不降级。

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- P4
- W10

## Acceptance criteria

- [x] 主节点通过编译/检查、artifact与 metadata、contract、data fixture、runtime smoke 和维护者试做所需输入；任一必要缺口阻塞 release。【证据包 `baseline/2026-09-29-release-p4/`：26.1.2 build/artifact/metadata/gates 全绿(command-output/01,04–07,10–11)；GameTest 冒烟绿(12)。两项必要缺口如实阻塞并记录于 FAILURES-LEDGER——F1 probe-types 预存红(CI 步骤失败,owner JSX 链/09)、F-perf 性能 gate pending-35。维护者试做输入不在本票消费(票 36 trial-prep 已备)。BUILD-AND-ARTIFACT-MATRIX §3/AC1 判定 satisfied-with-recorded-gaps】
- [x] 次级 NeoForge 节点保持可构建、可发布验证和公开契约可追踪，版本差异进入 capability matrix 与发布说明。【26.2.0 全绿(runs 01/04/05/10,490/0/58)；差异入 CAPABILITY-MATRIX(Assets `>=26` 守卫、PostEffects JSON 分支、能力行)】
- [x] 三个 experimental 节点至少通过可重复构建、artifact 验证和已声明能力 smoke；不静默承诺完整 parity。【1.21.1(341/0/14)+两 fabric(269/0/25×2)build 绿；fabric 制品按 CI 规则全项核验(06)；能力钉面 golden/query/capability-matrix-* + 票 27 CAPABILITY-MATRIX；explicit unavailable 不升格,BUILD-AND-ARTIFACT-MATRIX §4 明示 no-parity-fabrication】
- [x] 每项能力只以 supported、partial、unavailable 表达实际能力；not verified 与 deferred 作为独立证据/安排维度并阻塞对应未闭合域。【CAPABILITY-MATRIX：S/P/U 只表实际能力,nv/def 为独立维度并阻塞对应域满验证(如 Dynamic Registry 跨节点 nv、fabric 真机腿 def)】
- [x] managed、legacy、plugin、registry、packet、diagnostic、语言和功能的 contract/golden 均有旧新 diff、原因、影响和审阅记录，普通测试没有改写基线。【GOLDEN-CONTRACT-AUDIT：10 个 golden 家族=守卫测试+最新绿跑+旧新 diff/审阅指针(events-declared ×15 两批、api-manifest、probe d.ts、declaration-parity、platform-gates、capability rows、query/registry/inspector/per-node pins)；本轮再生成 roundtrip 为内容 no-op(command-output/09,EOL-only 差异已还原)——无手改输出；唯一再生成调用即 run 09 且零内容变更；独立 typecheck gate 的预存红如实记录(F1)】
- [x] runtime smoke 使用最终 remap 制品和真实 mods 场景，不把 checkout 开发运行冒充 P4 证据；每个节点输出发现/跳过/执行与失败日志。【RUNTIME-SMOKE-LEDGER：17 行会话账本逐行 discovery/skip/run/fail;真机(09-28/09-29 维护者会话、票 08 真实 addon jar 装载)与 dev-run 分类标注,dev-run 不计为 P4 证据；票 34 各 closed 票 deferred 窗口(fabric/1.21.1 真机腿、28 真渲染、29 真回读、23 JEI hook)未伪造,按域阻塞满验证】
- [x] 最终物理架构一致性审计通过：按实施交接单的包/目录规则自动生成生产 source/resource 清单，并覆盖全部文件；例外逐文件说明 owner 和保留原因，不要求手写全仓矩阵，也不强制所有源码物理搬迁。`common` 无 Minecraft/loader import，根 `src` 与 `versions/<node>` 未堆积重复业务逻辑，旧路径/bridge/duplicate manager 删除账本与实施交接单一致。【ARCHITECTURE-AUDIT + architecture-audit.py + command-output/08：1084 生产文件全清单;R1/R2/R3/R4 全 PASS(common 627 文件零 MC/loader import;root↔node 与跨 node 零内容重复;src/fabric 零 NeoForge 源);58 个 1.21.1 whole-file override 为交接单允许模式、逐文件入册附 owner/理由;删除账本(31/32 bridge 已删、05/06/07/11 旁路已删、26 ClientRenderPlugin 已删、23/24/27/29 零删除裁决)与交接单一致;未强制物理搬迁】
- [x] coverage ledger 每行都有 current path、target owner、gate、证据和删除条件；没有任何功能域因整体通过而被遗漏。【COVERAGE-LEDGER：README 功能覆盖账本 23 行全映射(23a–23c 对应末三行),每行 path/gate/owner/证据/删除条件;17 个 blocker 票全部出现(04 在 PERF-GATE-STATUS)】
- [x] 跨领域真实集成至少覆盖诊断 record 到 GUI/workspace、事件 candidate 到 runtime commit、global/shared 写集到领域计划、构建 trace 到能力矩阵；分域 contract fixture 不能替代最终真实链路。【INTEGRATION-CHAINS：链 1(30→27,真机 record→面板→VS Code)、链 2(14/26/08/21,真机 CLIENT reload commit+真实 addon reload 存活+真机 registry 手术)、链 4(33→31/32+本包,自动化 source-trace→能力行)为真证据;链 3(10/39)真实腿 fixture-only,如实声明并路由真机轮(N1/F12),未以 fixture 冒充】
- [x] 维护者确认的性能发布政策已存在；P4 验证按该政策记录性能 gate 状态，不在本票临时设置或修改数字。【PERF-GATE-STATUS：引用票 04 维护者 2026-09-28 确认(startup ≤41173ms、reload ≤285.3ms 均值,仅 primary 26.1.2,余观测);票 35 复测目录 `baseline/2026-09-29-release-perf/` 收口时仍不存在 ⇒ gate 状态记 pending-35 并入 FAILURES-LEDGER F-perf 阻塞;本票零采样、零数字改动】
- [x] Point、Contributor、Hook、显式依赖、freeze 与 Handle 的既有收益没有被重造或回归；新增通道仍只有一个事实源。【README §4：票 08 零新增 Point/框架,fixture 用既有 V2 builder+dependsOnId;`Ticket08ExternalAddonChainTest` 11/0(红绿档存)与 `PluginHookPairingTest` 在本轮 `:common:check`(1963/0/4,run 03)复绿;通道单一事实源=declaration-parity 过(run 05)+TS golden(TS-first 裁决)+5 jar 隔离干净(run 01);票 21 31 用例 prepare/ack/commit 账本语义并引,其跨节点真实腿维持 nv】
- [x] 所有失败按节点、输入、期望、实际和 owner 记录并阻塞 release，不因单次失败自动降级或 EOL 任何节点。【FAILURES-LEDGER：F1(阻塞,CI 红)、F-perf(阻塞,pending-35)+15 项开放记录(W1–W3、A1–A3、F12、D6 复验、Python .pyi、binding-scope、fabric 真机腿、28 真渲染、29 真回读、JEI hook、DynReg 同步、N1、JSX UI in-review)逐项 node/input/expected/actual/owner/影响;无降级、无 EOL、无 gate 弱化】

## Delivery record (2026-09-29)

- **改动范围**：仅新增证据包 `baseline/2026-09-29-release-p4/`（README、BUILD-AND-ARTIFACT-MATRIX、CAPABILITY-MATRIX、GOLDEN-CONTRACT-AUDIT、RUNTIME-SMOKE-LEDGER、ARCHITECTURE-AUDIT、COVERAGE-LEDGER、INTEGRATION-CHAINS、PERF-GATE-STATUS、FAILURES-LEDGER、architecture-audit.py、command-output/01–12）与本票面标注。零生产/测试代码改动、零 golden 改动（run 09 再生成后已还原，内容 no-op）、零 gate 弱化。
- **本轮执行**（worktree `../NekoJS-mult-t34`，base mult@28283cc4）：五节点 build+guardLint(341块/487文件/0警告)+nbt×3+addon隔离(5 jar 干净)；`:common:check`(1963/0/4,含 checkCommonIsolation)+processor(13/0)；ci-gates source-roots×5+all(4 checks 0 failures)；两 fabric 制品 CI 规则全项核验+三 NeoForge 制品元数据；架构审计 1084 文件全 PASS；golden roundtrip 内容 no-op；全套件 490/490/341/269/269 零失败；GameTest `All 1 required tests passed`；probe-types 复现预存红(14 错误,F1)。
- **release 判定输入**：conditional-go——两项硬阻塞(F1 probe-types CI 红；F-perf pending-35)，其余开放窗口按域阻塞满验证、已全部入册未伪造。人工验收=维护者结论。
- **pending**：票 35 报告落地后按 PERF-GATE-STATUS §2 消费规则补记性能判定；真机轮(fabric/1.21.1 腿、28 真渲染、29 真回读、链 3 真实腿、D6 一分钟复验)与维护者试做(票 36)不在本票代替。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [PR 37 维护体验回归约束规格](../specs/00-pr37-maintainer-research.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [版本与加载器支持矩阵规格](../specs/02-support-matrix.md)
- [平台构建与 Stonecutter 策略规格](../specs/03-platform-build-strategy.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [33: CI 用途子集与 Fabric processor 延期替代 gate](33-build-ci-processor-gate.md): 最终节点与能力验证必须包含 CI 用途子集和 Fabric processor 延期替代 gate 的结果。
- [04: P4 前性能发布政策确认](04-perf-release-policy.md): P4 总体验证开始前，性能发布政策必须已由维护者基于 P0 基线确认，后续验证只执行已定政策。
- [39: Runtime Item/Block modification 候选计划与 snapshot ownership](39-item-block-modification.md): 既有 Item/Block modification 是公开行为且涉及 live mutation，P4 必须消费其候选计划、snapshot ownership、恢复与迁移证据。
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

**Rationale:** 它是最终 release 前唯一的全域证据汇总点，但以已完成的主整合和各域 gate 为输入，避免横向重做实现。

**Coordination:**

- 由主整合 owner 汇总本票 Blocked by 已列出的真实先决票后并行收集各域报告；本票不拆解或代替各功能域实现。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
