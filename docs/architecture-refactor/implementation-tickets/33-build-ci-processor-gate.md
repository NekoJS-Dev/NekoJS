# 33: CI 用途子集与 Fabric processor 延期替代 gate

**What to build:** 收口构建约定与 CI：各类手写节点子集按用途核对，Fabric processor 在 1.2.0 明确延期，并用真实非 processor 的 contract/spec、event/surface 与 declaration 覆盖 gate 补足延期说明。

**Blocked by:** [32: Fabric 五层源唯一性与 bridge 删除条件](32-build-fabric-trace-cutover.md)、[09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md)

**Status:** in-progress

**Assignee:** 33-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- W9

## Acceptance criteria

- [x] Fabric common-api-processor 在 1.2.0 保持未接入，延期原因和未覆盖范围公开，不被描述为 NeoForge processor 等价或临时接通。
- [x] contract/spec、event/surface、declaration 三类非 processor gate 分别有 owner、输入、逐项输出和失败诊断，并能指出缺失的 contract、method、platform、domain、binding、member 或 type。
- [x] 没有证据的项保持 not verified 并阻塞对应域验收；不得因缺测试直接改判为 unavailable 或 partial，也不得用改表掩盖规范 ALL 与实际能力的差异。
- [x] NeoForge-only NBT、Fabric-only artifact/smoke、全节点 build、release/publish 子集分别按用途与节点事实源核对，不用单一等值检查冒充所有用途。
- [x] 每个 CI 子集都有用途说明、节点覆盖、 intentional skip 和一致性检查；manifest 仅作为派生快照，不成为第二节点事实源。
- [x] 五个节点的 check、artifact 与 source trace 结果进入同一报告；Fabric artifact 验证和已声明能力 smoke 不被静默省略。
- [x] NeoForge 既有接线保持，过时 Graal lint 更新且不放宽 Minecraft/loader 隔离；不新增 Gradle project、API jar，不删除 Stonecutter，不改变支持矩阵。
- [x] CI 或 gate 失败输出能定位节点、输入、期望结果和 owner，而不是只留下任务失败摘要。
- [x] 本票完成表示 CI 与替代 gate 可发现并验证输入，不代表尚未迁移的新功能域已验收；后续域票提交其真实 fixture 并通过同一 gate。

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [维护者模块设计与运行时所有权规格](../specs/01-maintainer-module-design.md)
- [版本与加载器支持矩阵规格](../specs/02-support-matrix.md)
- [平台构建与 Stonecutter 策略规格](../specs/03-platform-build-strategy.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [32: Fabric 五层源唯一性与 bridge 删除条件](32-build-fabric-trace-cutover.md): CI 消费者、节点 source trace 和 Fabric 制品 gate 必须基于最终 raw 根与 bridge 判定结果。
- [09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md): 非 processor 的 contract/spec、event/surface 与 declaration gate 需要规范契约、coverage ledger、golden 和 declaration fixture 作为真实输入。

## Scope and coordination

**Rationale:** 它交付可运行的 CI/覆盖 gate 和诚实的 processor 延期结论，独立于具体功能实现且可由报告逐项验收。

**Coordination:**

- Managed Surface/Probe owner 提供契约与 declaration fixture，build owner 负责接线与报告；CI secret、runner 或共享环境只作协调，不作为 blocked by。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。

## Closure record（2026-09-19）

- 执行者：33-agent。工作流：认领（in-progress）→ 读票/31/32 closure/09 closure/spec 03/07/handoff W9/README/11 全部 addendum/
  baseline MIGRATION/module-examples → 现状盘点（CI 子集、processor 接线、已有 gate 入口）→
  建最小可运行 gate（工具 + 节点内测试 + 只读基线）→ 逐项跑红/绿 → 接线 CI 与节点 check →
  收尾门禁 → 双轴自查 → 勾选 AC → 关票提交（不 push）。

- 先决输入（31/32/09 均已 closed，不重做）：W8 的 `src/fabric` raw 根与五层 trace 结论、
  09 的 NormativeApiContract/manifest/Probe/declaration 派生链与三态 capability。

- 交付物（工具）：`tools/nekojs-ci-gates.py`（4 个只读 check + source-roots 探针 + selftest）、
  `tools/nekojs-source-roots.init.gradle`（source trace 探针）。

- 交付物（节点内 gate，3 个测试类）：`PlatformSpecContractGateTest`（contract/spec，@Tag platform-gate）、
  `EventSurfaceDomainGateTest`（event/surface，@Tag platform-gate）、
  `ManagedDeclarationCoverageGateTest`（declaration，普通 :common:test）。
  前两者跑在新增的 `platformGateTest` 任务（独立 JVM；同 JVM 实测会污染 30 例），并挂进节点 check。

- 交付物（只读基线）：`spec-coverage-{neoforge,fabric}.txt`（各 83 行）、`event-surface-domains.txt`
  （17 domain × 5 节点 = 85 行）、`common/.../declaration-parity.txt`（5 行）。既有 golden 零变化。

- 交付物（示例/迁移/证据）：`common/src/test/resources/nekojs/ci-gate-examples/README.md`、
  `docs/architecture-refactor/baseline/2026-09-19-ci-processor-gate/{MIGRATION.md,REPORT.md,evidence/}`。

- 逐条 AC 判定（seam + 精确命令 + 结果，完整证据见 REPORT §2–§9）：
  1. processor 延期：convention + 节点 properties + 处理器源码词表四源核对，
     `python tools/nekojs-ci-gates.py processor` → neo 三节点 wired/option 齐、fabric 两节点均无。
  2. 三类 gate：五个节点 `:<node>:platformGateTest` 全绿 + `:common:test --tests ...ManagedDeclarationCoverageGateTest`
     全绿；逐项输出落 `build/nekojs-gates/*.json`（spec 83 rows、event 17 rows、declaration 141 members/150 signatures）。
  3. not verified：event 基线状态词表只有 present/not-verified，写入 unavailable/partial 直接判 undeclared-evidence；
     实际 8 行 not-verified 保持阻塞。
  4. CI 子集：`python tools/nekojs-ci-gates.py subsets` 四类子集按用途分别核对（含 intentional skip 登记）。
  5. 五节点报告：`source-roots --node` ×5 + `all --out build/nekojs-gates-report.json`；
     check/artifact(名字+大小+sha256)/source trace/两个节点内 gate 全在报告。
  6. guardLint 过时 Graal 规则更新（保留 MC/loader 隔离）：`gradlew guardLint` → 428 文件 / 0 违规。
  7. 失败可定位：selftest 在仓库副本上植入 6 类故障，全部变红；输出格式 `<check> <subject> node= input= expected= owner= ::`。
  8. 收尾：`:common:test --rerun-tasks`（233 suites/1725 tests/0 failed）、`:common:check`、
     `:26.2.0:compileJava`、`:26.2.0-fabric:compileJava`、五节点 `:test --rerun-tasks`、`guardLint` 全绿；
     `git diff --check` exit 0。

- 双轴自查：Standards —— 复用既有 Seam（`CoreManagedApiBootstrap`/`EventGroupRegistry`/`EventGroup`/`ManagedApiDeclarationGenerator`/
  `SpecCoverageProcessor` 词表），无新抽象、无第二事实源、无新 Gradle project/依赖；gate 用 `item()` 输入声明、
  基线为只读快照。Spec —— 每条 AC 有其可复现命令与证据，且 gate 是反向可证伪的（selftest）。
  自查中修正的两处：① 初版 event gate 只读静态声明（顺序敏感、且把未注册 domain 误当 present）→ 改为驱动
  节点真实插件注册入口；② 初版 gate 与普通 test 同 JVM 造成 30 例污染 → 拆出独立 `platformGateTest` 并
  `excludeTags`，不靠放宽断言绕过。

- 限制与未做事项（owner 已列，详见 REPORT §10）：Ubuntu CI 全链路未跑（本地 Windows 等价）；Fabric runtime
  smoke 沿用票 31/32 证据未重跑；gate 只覆盖注册面/声明面（非 runtime smoke）；8 行 event domain 保持
  not-verified；declaration gate 只覆盖 SERVER 脚本类型与 TS 声明；未执行真实 MC/loader/network smoke。
