# 01: P0 五节点构建与契约基线

**What to build:** 在旧行为改变前，建立可移植的 JDK/Gradle 输入并生成五节点实际源/产物、测试发现、契约与声明能力的可复现基线，保留原始结果与差异；性能另行采样。

**Blocked by:** None (can start immediately)

**Status:** closed

**Assignee:** zcode-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:** W0；建立可移植 Gradle/JDK 输入：先归档旧配置、旧命令和失败证据，再移除被跟踪的本机 `org.gradle.java.home` 绝对路径，改用文档化 `JAVA_HOME` 或用户级 Gradle 配置，并让 CI 不再用临时删除配置的 workaround。

## Acceptance criteria

- [x] 删除或替换本机 Gradle/JDK 配置前，旧配置内容、来源 revision、原启动命令、失败输出和缓存/工具链状态先归档；清理动作本身可审计并可回滚。
- [x] 被跟踪的 Gradle 配置不含本机绝对 `org.gradle.java.home`；每个节点记录实际使用的 JDK launcher、Java compatibility/toolchain、Gradle、Graal、Stonecutter 和 loader 版本，不以“统一 JDK 25”或本机默认路径替代逐节点事实。
- [x] 本票与 02 并行执行时绑定同一记录的源 revision，并使用各自隔离 checkout、Gradle/caches 与 run 目录；环境修复、构建产物或日志不得泄漏进性能采样输入。
- [x] 至少各完成一次 Windows 与 Linux 的 Gradle 配置阶段验证；路径、换行、JDK discovery 或实际 toolchain 差异进入基线差异，不用“仅本机可运行”作为结论。
- [x] 基线在记录的环境和输入下可重复生成，原始输入、生成方式、结果和失败日志均留档，不用缓存目录或历史日志冒充本轮证据。
- [x] 五个既有节点及其支持等级完整出现在基线中；本基线不删除、降级或新增节点，也不改变 26.2 节点身份、Minecraft 坐标语义或 Fabric 制品命名。
- [x] 每个节点分别记录实际参与构建的源、资源、模板、metadata、mixin、processor、能力声明、测试发现/跳过/数量和生成物 trace，并能与节点事实源对齐。
- [x] manifest、能力矩阵和测试清单是节点图与实际产物的派生快照，不成为第二事实源；手工记录与实测差异逐项列出。
- [x] 最终 class、jar entries、metadata 与声明能力可互相追溯；重复、缺失、跳过和有意子集都有原因、owner 和失败诊断。
- [x] 契约、golden、Probe 与语言基线只被读取；普通验证不得更新基线，任何必要基线变化都保留旧新差异与审阅记录。
- [x] 每个 not-verified 或 deferred 项都有独立证据状态和 owner，不被改写为 supported、partial 或 unavailable。
- [x] 基线报告明确性能采样不属于 W0，并指向独立的 P0 性能基线，而不是用性能不阻塞作为省略结论。
- [x] 对后续迁移有影响的差异按 Fabric 源所有权、CI 子集、契约覆盖和数据保护归类，形成可执行处置项。

## Closure record（2026-09-12）

- 执行者：zcode-agent；revision 链：`9f702195`（改动前）→ `8301dc14`（W0 去 pin）→ `62d5163a`（基线证据）→ `14de611f`（fabric 最小修复，见下）→ `d95f816b`（fabric 事实补采）。
- 证据入口：基线报告 `../baseline/2026-09-12-build-baseline-report.md`（§11 复现序列、§10 not-verified 清单、§7.1 失败诊断与修复验证）、实测 manifest `../baseline/node-source-artifact-manifest-measured-2026-09-12.md`（含手写版 vs 实测逐项差异表）、改动前归档 `../baseline/w0-config-archive-2026-09-12/`、fabric 修复验证 `../baseline/2026-09-12-fabric-build-fix/`。
- 边界决策：`14de611f`（fabric convention 自源节点跳过重复注入）是五节点基线可复现的必要前提，超出 W0 物理落点，已在报告 §7.1"边界决策留痕"记录理由与回滚方式；31 号票源根所有权迁移不受其约束。
- 第 7 条验收说明：fabric 两节点的测试/制品事实在 `14de611f` 修复冷构建失败后补采闭环（test 8/37/6/0 ×2、verifyFabricRuntimeArtifact ✓、最终 jar hash 与 entries 落档），本条按闭环后事实勾选。
- code-review（双轴）结论已落实：gradle.properties 优先级注释更正、报告/manifest 一致性修正、Graal 实际版本（GraalMC 25.1.3.7）补录、`wiki/构建系统.md` 过时 CI 描述同步。
- 遗留 not-verified（均有独立 owner，不影响本票验收）：CI 远程验证（维护者，push 后）、1.21.1 toolchain 路径推断（低风险）、运行时 smoke（34 号票）、性能采样（02 号票，建议其绑定 `14de611f`）。

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

No direct blockers.

## Scope and coordination

- **Rationale:** 它独立交付一份可复核的重构前事实快照，是所有构建迁移和 P4 对照的共同输入。
- **Coordination:**
  - 与运行时、插件、语言、registry、功能域 owner 确认契约与能力输入的取舍；共享资产或同时采集只作协调，不作为阻塞。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
