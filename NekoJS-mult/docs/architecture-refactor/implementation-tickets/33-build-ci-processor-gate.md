# 33: CI 用途子集与 Fabric processor 延期替代 gate

**What to build:** 收口构建约定与 CI：各类手写节点子集按用途核对，Fabric processor 在 1.2.0 明确延期，并用真实非 processor 的 contract/spec、event/surface 与 declaration 覆盖 gate 补足延期说明。

**Blocked by:** [32: Fabric 五层源唯一性与 bridge 删除条件](32-build-fabric-trace-cutover.md)、[09: Managed Surface 单一规范源与声明/Probe 派生链](09-managed-surface.md)

**Status:** closed

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

## Review-round-1 addendum（2026-09-19）

协调者复核票 33 的 Standards 轴 code-review 后确认 7 条真问题，并判定 **AC2/AC8 属于证据不足**、
票应先回到 `in-progress`。本轮按此执行：先改回 `in-progress`，逐条修完并补反向证据，再重新关闭。
**每条 finding 的修法都不止改注释/文档**：F1/F6 加了能变红的 JUnit 测试，F2/F4/F5 加了能变红的 selftest case。

### F1 [中] 三处隔离强制点前缀表不一致

- **finding**：`common/build.gradle` 的 `checkCommonIsolation.forbiddenPrefixes` 只有 3 个前缀（漏
  `net.fabricmc` / `com.mojang`），而 `guardLint` 有 4 个、`ModulePipelineIsolationTest` 有 5 个。
  后果：common 里新增只 import `net.fabricmc` 的文件时，只跑 `:common:check` 不会红。
- **实际修法**：三处对齐为同一集合（`net.minecraft` / `net.minecraftforge` / `net.neoforged` /
  `net.fabricmc` / `com.mojang`）。`com.mojang` 按事实裁决加入：`common/src` 里 `com.mojang` 与
  `net.fabricmc` 的 import 数均为 0（grep 实证），只有 probe 文档/配置文本提到；判据是
  「shared 引擎不得依赖任何 MC 侧类型」。新增 `CommonIsolationPrefixAgreementTest` 从三个文件的
  **实际文本**解析前缀表并断言同集合，任一处再被改窄即红。
- **精确命令**：`./gradlew.bat :common:test --tests com.tkisor.nekojs.core.module.CommonIsolationPrefixAgreementTest`
- **结果**：2 tests / 0 failures。反向证据：把 `'com.mojang'` 从 `common/build.gradle` 删除后
  该测试红（`... 前缀表与期望集合不一致`）。`guardLint` 加强前缀后仍 0 违规。

### F4 [中低] `passes_option` 纯文本包含匹配（假阳）

- **finding**：`tools/nekojs-ci-gates.py` 用 `PROCESSOR_OPTION + "=" in text` 匹配，注释里留一行
  `// "-Anekojs.platform=nf26"` 而删掉真实传参时仍判 pass；AC1 因此不成立。
- **实际修法**：新增 `strip_comments()` 状态机（剥 `//` 行注释与 `/* */` 块注释，保留字符串字面量内的 `//`），
  选项判定改为在剥注释后的文本上做。
- **精确命令**：`python tools/nekojs-ci-gates.py selftest`
- **结果**：新 case「option 被注释掉（F4）」——把真实传参注释掉、注释里保留
  `-Anekojs.platform=$platformTag` → gate 判红（`NeoForge convention 不再传平台 option`）。

### F2 [中] `gate_nodes` 吞 failure 条目 + `source-roots` 失败直接退出

- **finding**：每个 gate 报告只取 `failures[0][:160]`，其余被吞；`source_roots` 失败 `raise SystemExit`，
  不产出任何可定位诊断行；CI 的 `set -euo pipefail` 会在跑到 `all` 之前退出。
- **实际修法**：`gate_nodes` 逐条输出该节点**全部** failures（带 `failure[i/n]` 计数与完整 detail）；
  `source_roots` 不再 raise，改为按统一格式 `<check> <subject> node= input= expected= owner= ::`
  打一条诊断并返回非零，`main()` 也相应返回 1。
- **精确命令**：`python tools/nekojs-ci-gates.py selftest`
- **结果**：新 case「节点报告多条 failure（F2）」铺 3 条 failure，断言 `domain=A`、`domain=B`、`domain=C`
  三条都进报告（只留第一条会红）；「source-roots 失败诊断（F5）」断言诊断行含 node/input/expected/owner。

### F5 [中低，Linux CI 必红] wrapper 硬编码 `gradlew.bat`

- **finding**：`tools/nekojs-ci-gates.py` 硬编码 `gradlew.bat`，而 CI 新步骤跑在 `ubuntu-latest`，
  必失败 → 新步骤红，且 `build/nekojs-gates-report.json` 在 runner 上永不产出。
- **实际修法**：新增 `gradle_wrapper(repo)`，按 `sys.platform` 选 `gradlew.bat`（Windows）或
  `./gradlew`（其余）。
- **精确命令**：`python tools/nekojs-ci-gates.py selftest`
- **结果**：本地（Windows）验证的是「wrapper/探针不可用 → 非零返回 + 可定位诊断行」这条契约
  （case 删掉 wrapper 后断言）。Linux 分支在真实 runner 上的执行仍属合并后观察项。

### F6 [低] `missing-type` 是不可达断言

- **finding**：`renderedTypes = interfaceOwners(ts)` 与 `renderedOwners` 同集合同正则，且全仓没有
  `kind="type"` 的 `ApiSymbolId` 生产者（实际 kind 只有 `global`/`member`/`event`/`adapter`/`hostExt`/`java`），
  该断言永远为真——正是 AC3 要消灭的「把没证据写成覆盖」。
- **实际修法**：删掉不可达断言；抽出 `typeKindGaps(List<ApiSymbol>)` 改为 `type-symbol-observed`
  诊断（真的会因 type-kind 符号出现而报）；`types=` 行明确写成空集事实
  （`<empty: no kind=type producer in the contract>`），并在 fixture 注释里说明它是事实输出而非断言。
- **精确命令**：`./gradlew.bat :common:test --tests com.tkisor.nekojs.core.api.ManagedDeclarationCoverageGateTest`
- **结果**：3 tests / 0 failures。反向证据：`typeKindSymbolProducesObservableGap` 用合成 type-kind 符号
  断言必须产出 `type-symbol-observed type=Synthetic`；把 `typeKindGaps` 的 kind 过滤改成恒 true 后该测试红
  （已实测）。`contractCarriesNoTypeKindSymbolsToday` 把「当前契约无 type-kind」这个事实钉住。

### F7 [低，文档漂移] Graal 禁令撤了但文档仍写成现役

- **finding**：`docs/adr/0007-module-boundaries.md:42`、`docs/module-boundary-common-api.md`（多处）、
  `common/build.gradle:2` 仍把 L1 Graal 禁令写成现役硬边界，其中一处还要求「故意在 `api.*` 加一行 Graal import 确认 lint 报错」——照做现在会静默通过。
- **实际修法**：**按时间修订加注，不重写历史正文**。ADR-0007 该条补「已按票 33 实施（2026-09-19）：
  `guardLint` 的 L1 Graal 禁令已移除」并说明它早已空转（relocated 包名匹配不到）+ MC/loader 隔离
  保留并加强；`module-boundary-common-api.md` 顶部加 `票 33 修订注记` 并给正文相关行加 ⚠️ 历史标注
  （含那条已失效的验证步骤）；`common/build.gradle` 头注释同步为「Graal 不是禁止项」。
- **精确命令**：`./gradlew.bat guardLint`
- **结果**：`guardLint: 守卫块 275，扫描 428 个文件；超限豁免 0 个；警告 0 条` + BUILD SUCCESSFUL
  （前缀表加强后仍零违规）。

### 修订后的门禁结果

```text
python tools/nekojs-ci-gates.py all        → 4 个 check，0 个失败条目
python tools/nekojs-ci-gates.py selftest   → 8/8 全部变红（含本轮新增 F2/F4/F5 三个 case）
./gradlew.bat guardLint                    → 428 文件 / 0 违规 / BUILD SUCCESSFUL
./gradlew.bat :common:compileJava :common:compileTestJava :common:check   BUILD SUCCESSFUL
./gradlew.bat :common:test --rerun-tasks   → 235 suites / 1736 tests / 0 failed
./gradlew.bat :26.2.0:compileJava :26.2.0-fabric:compileJava            BUILD SUCCESSFUL
./gradlew.bat :<五节点>:test --continue     → BUILD SUCCESSFUL
git diff --check                           → exit 0
```

### 本轮仍未覆盖的边界

1. F1 的 `com.mojang` 判据是「common 源码零 import + shared 引擎不得依赖 MC 侧类型」；若将来某功能确需
   在 common 里用 `com.mojang`（例如纯 math 工具类），必须走 ADR 裁决而不是删规则。
2. F5 的 Linux 分支（`./gradlew`）未在真实 `ubuntu-latest` 上执行，属合并后观察项。
3. F2 的 selftest 用合成报告验证聚合逻辑；真实故障现场（某节点 gate 真的失败导致多节点报告同时带
   failures）未在本轮复现。
4. 其余同 Closure record 的限制：Ubuntu CI 全链路、Fabric runtime smoke、runtime smoke、
   8 行 `not-verified`、declaration 仅 SERVER+TS。
5. **并行构建干扰**：修订期间仓库内有其它 agent 的 Gradle 构建并发占用同一 `common/build`，出现过
   `Failed to delete some children` / `in-progress-results-generic.bin` 缺失 / `NoClassDefFoundError` 等
   与本票无关的瞬态失败；最终结论全部取自连续绿的窗口，未把瞬态失败记为通过或回归。