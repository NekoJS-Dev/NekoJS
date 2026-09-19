# 票据 33 W9 报告：CI 用途子集与 Fabric processor 延期替代 gate

工单：[33: CI 用途子集与 Fabric processor 延期替代 gate](../implementation-tickets/33-build-ci-processor-gate.md)
基线日期：2026-09-19。执行者：33-agent。分支：master（不 push）。

本报告只记录**实际执行过**的命令与结果。需要远程 CI 或数十分钟五节点完整 build 的检查，在 §10 如实标注为 not verified，不外推。

---

## 1. 交付物

### 1.1 gate 工具（新建，只读）

`tools/nekojs-ci-gates.py` —— 四个 check + 探针 + 自检，全部只读：

| check | owner | 输入事实源 |
|---|---|---|
| `ci-subset-consistency` | build-convention/CI-build owner | `settings.gradle.kts` 节点图 + `.github/workflows/ci-build.yml` + `versions/*/gradle.properties` |
| `fabric-processor-deferral` | build-convention owner | 两个节点 convention + `versions/*/gradle.properties` + `SpecCoverageProcessor` 源码词表 |
| `declaration-parity` | Managed Surface/Probe owner | `CoreManagedApiBootstrap` + `ManagedApiDeclarationGenerator` + 只读基线 |
| `node-report` | build-convention owner | 各节点 `build/test-results/test`、`build/libs`、`build/nekojs-gates` |

`tools/nekojs-source-roots.init.gradle` —— source trace 探针（--init-script），把节点 main sourceSet 实际挂载的 srcDirs 写盘为 `source-roots-<node>.json`。

### 1.2 节点内 gate（新建）

| 测试类 | 标签/任务 | 覆盖 |
|---|---|---|
| `src/test/java/com/tkisor/nekojs/platform/PlatformSpecContractGateTest.java` | `@Tag("platform-gate")` | contract/spec：spec 声明 + 实现面逐 method 覆盖 |
| `src/test/java/com/tkisor/nekojs/platform/EventSurfaceDomainGateTest.java` | `@Tag("platform-gate")` | event/surface：驱动节点真实插件注册入口，逐 domain/bus |
| `common/src/test/java/com/tkisor/nekojs/core/api/ManagedDeclarationCoverageGateTest.java` | 普通 `:common:test` | declaration：契约成员 vs TS 声明逐项 |

前两者跑在独立的 `platformGateTest` 任务（独立 JVM）：它们建立进程级事件 schema，与普通 test 同 JVM 会污染其它用例（**实测 30 例失败**），因此普通 `test` 显式 `excludeTags`，`platformGateTest` `includeTags`，并 `check.dependsOn(platformGateTest)`。

### 1.3 只读基线

- `src/test/resources/nekojs/platform-gates/spec-coverage-neoforge.txt`（83 行，3 个 NeoForge 节点共用）
- `src/test/resources/nekojs/platform-gates/spec-coverage-fabric.txt`（83 行，2 个 Fabric 节点共用）
- `src/test/resources/nekojs/platform-gates/event-surface-domains.txt`（17 domain × 5 节点 = 85 行）
- `common/src/test/resources/nekojs/platform-gates/declaration-parity.txt`（5 行汇总）
- 示例：`common/src/test/resources/nekojs/ci-gate-examples/README.md`
- 迁移材料：`docs/architecture-refactor/baseline/2026-09-19-ci-processor-gate/MIGRATION.md`

**既有 golden 零变化**（`api-manifest-core.json` 等未被本票触碰）。

---

## 2. AC1 — Fabric processor 延期（pass）

**命令**：`python tools/nekojs-ci-gates.py processor`

**结果（实际输出）**

```text
== fabric-processor-deferral (owner=build-convention owner) -> pass
   processor-options declared   accepts=nf26,nf121,cr (source of truth: .../SpecCoverageProcessor.java)
   1.21.1           platform=neoforge wired=true  option_arg=true  platform_tag=nf121
   26.1.2           platform=neoforge wired=true  option_arg=true  platform_tag=nf26
   26.2.0           platform=neoforge wired=true  option_arg=true  platform_tag=nf26
   26.1.2-fabric    platform=fabric  wired=false option_arg=false platform_tag=None
   26.2.0-fabric    platform=fabric  wired=false option_arg=false platform_tag=None
结果：1 个 check，0 个失败条目
```

gate 是**反向可证伪**的：任一项变化都会红（`selftest` 的 "fabric 偷接 processor" case 实证）。延期原因、未覆盖范围、与 processor 的逐项关系、以及"不得描述为等价/临时接通"的措辞，全部写在 `MIGRATION.md §2`。NeoForge 接线与 MC/loader 隔离均未放宽。

## 3. AC2 — 三类 gate 的 owner / 输入 / 逐项输出 / 失败诊断（pass）

**命令**

```text
./gradlew.bat :1.21.1:platformGateTest :26.1.2:platformGateTest :26.2.0:platformGateTest :26.1.2-fabric:platformGateTest :26.2.0-fabric:platformGateTest --continue
./gradlew.bat :common:test --tests com.tkisor.nekojs.core.api.ManagedDeclarationCoverageGateTest
```

**结果**：五个节点各 2 个测试全绿（`BUILD SUCCESSFUL`）；declaration gate 全绿。逐项输出（实际落盘）：

- `versions/<node>/build/nekojs-gates/spec-coverage-<node>.json`：83 rows / 0 failures
- `versions/<node>/build/nekojs-gates/event-surface-<node>.json`：17 rows / 0 failures
- `common/build/nekojs-gates/declaration-parity.json`：globals=7、owners=16、members=141、signatures=150

失败诊断词表（每条都带 domain/member/type 与缺失方向，MIGRATION §2.3 列全）：`missing-contract`、`missing-method`、`missing-binding`、`undocumented-domain`、`member-drift`、`undeclared-evidence`、`missing-member`、`extra-member`、`missing-type`、`owner-missing`、`type-fallback`。

**契约零改动时 gate 为绿**（本次实测），**证明红侧**由 `selftest` 覆盖（declaration 报告带缺口 / 报告缺失两 case 均变红）。

## 4. AC3 — 无证据项保持 not verified（pass）

实现方式：`event-surface-domains.txt` 的状态词表**只有两种** `present` / `not-verified`。gate 遇到任何其它词（`unavailable` / `partial`）直接判 `undeclared-evidence` 失败——即"缺证据改判能力"在机制上不可能通过，而不是靠约定。

实际基线里 8 行是 `not-verified`（真实无运行时注册证据，对应域验收保持阻塞）：

```text
CapabilityEvents      | 26.1.2-fabric / 26.2.0-fabric
DynamicRegistryEvents | 1.21.1 / 26.1.2-fabric / 26.2.0-fabric
KeyBindEvents         | 1.21.1
ProbeEvents           | 26.1.2-fabric / 26.2.0-fabric
```

`types=`（declaration 基线）是**空集事实**：当前契约不带 `type:` 符号，不是被省略；一旦新增 type 而渲染器跟不上，`type-fallback` 会逐条报出来。

## 5. AC4/AC5 — CI 子集用途、覆盖、intentional skip、一致性（pass）

**命令**：`python tools/nekojs-ci-gates.py subsets`

**结果**：`1 个 check，0 个失败条目`。四类子集逐条核对（实际输出含 purpose / coverage / intentional-skip 行，完整见 `evidence/gate-subsets.txt`）：

| 子集 | 覆盖 | intentional skip | 核对到的事实 |
|---|---|---|---|
| neoforge-nbt | 1.21.1 / 26.1.2 / 26.2.0 | 两 Fabric 节点 | 三条 `nbtSmokeTest` 在 run 块 + 步骤名存在 |
| fabric-artifact-smoke | 26.1.2-fabric / 26.2.0-fabric | 三 NeoForge 节点 | 两处制品路径 + 两个 smoke 步骤名 |
| all-node-build | 五节点全等 | 无 | 手写列表与节点图逐个等值 |
| release-publish | 1.21.1 / 26.1.2 / 26.2.0 | 两 Fabric 节点 | 三条 release 制品路径 + 两个发布步骤 |

skip 的正确性由**节点平台事实源**校验（`versions/<node>/gradle.properties` 的 `deps.platform`），不是靠注释：把一个 Fabric 节点写进 NeoForge-only 子集的 expect 会立刻失败。反向覆盖也检查：没有任何子集覆盖且未列入 skip 的节点 = 覆盖缺口。

manifest 未被 gate 读取（`docs/architecture-refactor/baseline/node-source-artifact-manifest.md` 保持派生快照身份，未升级为第二事实源）。

## 6. AC6 — 五节点 check/artifact/source trace 同一报告（pass）

**命令**

```text
for node in 1.21.1 26.1.2 26.2.0 26.1.2-fabric 26.2.0-fabric; do
  python tools/nekojs-ci-gates.py source-roots --node "$node"
done
python tools/nekojs-ci-gates.py all --out build/nekojs-gates-report.json
```

**结果（实际，`--out` 产物见 evidence/gates-all.json）**

```text
== node-report (owner=build-convention owner) -> pass
   1.21.1         check  46 suites / 213 tests / 0 failed
                  artifact nekojs-neoforge-1.21.1-1.1.0-preview3.jar size=2545601 sha256=db4fff9b70c04e4c
                  source-trace java=2 resources=6   spec-coverage 83 rows/0 failures   event-surface 17 rows/0 failures
   26.1.2         check  61 suites / 306 tests / 0 failed   artifact sha256=a4192fc472e12ca1 ...
   26.2.0         check  61 suites / 306 tests / 0 failed   artifact sha256=d79815d100dc4196 ...
   26.1.2-fabric  check  39 suites / 209 tests / 0 failed   artifact sha256=06e6e19fa2eddb0d ...
   26.2.0-fabric  check  39 suites / 209 tests / 0 failed   artifact sha256=1b5156cee6aca7f0 ...
结果：4 个 check，0 个失败条目
```

**Fabric artifact 验证不静默省略**：两个 Fabric jar 的名字/大小/sha256 与 `verifyFabricRuntimeArtifact` 都在报告里；缺失该节点制品会记 `artifact 验证对该节点 not verified（Fabric artifact 验证不得静默省略）`。

**source trace 的实质**（探针实际读出，非文档声明）：Fabric 节点包含 `../../src/fabric/java` 与 `../../src/fabric/resources`（W8 raw 根），NeoForge 三节点不含——与票 31/32 结论一致。

**已声明能力 smoke**：本次只到"artifact 存在 + 节点 gate 与普通 test 全绿"。真实 Fabric runtime smoke 未在本票重跑（票 31/32 已有产出），见 §10。

## 7. AC7 — NeoForge 接线保持、过时 Graal lint 更新、隔离未放宽（pass）

- `guardLint` **修订前**：L1 把 `import org.graalvm.*` 判为 `api.*` 违规。
- **修订**：删掉该硬失败规则与规则 6 的 Graal 措辞（ADR-0007 2026-08-30 修订已允许 `common`/`api.*` 用 GraalJS）；**保留** L1/L2 的 MC/Loader 隔离。
- **命令**：`./gradlew.bat guardLint`

```text
guardLint: 守卫块 275，扫描 428 个文件；超限豁免 0 个；警告 0 条
BUILD SUCCESSFUL
```

未新增 Gradle project / API jar；未删 Stonecutter；节点身份、`deps.minecraft` 坐标与 `nekojs-fabric-26.2-*` 制品命名不变；支持矩阵不变。

## 8. AC8 — 失败输出能定位节点/输入/期望/owner（pass）

统一格式（工具与 Java gate 都遵守）：`<check> <subject> node=<node> input=<file> expected=<...> owner=<owner> :: <detail>`

**可运行检查**：`python tools/nekojs-ci-gates.py selftest`（在仓库副本上植入故障）

```text
selftest（每个 case 在临时副本上植入故障）:
  OK   ci-subset 漏节点          -> 子集缺少期望的节点任务（节点 26.2.0）
  OK   fabric 偷接 processor     -> Fabric convention 挂上了处理器 —— 延期被偷换成临时接线
  OK   declaration 报告带缺口     -> missing-member owner=Text member=of owner_ref=Managed Surface/Probe owner
  OK   declaration 报告缺失       -> 缺少 declaration gate 报告：not verified（...）
  OK   节点无证据 -> not verified -> build 目录不存在：本报告对该节点是 not verified，不做任何通过推断
  OK   节点报告带失败 -> 聚合     -> build 目录不存在：...（副本复制忽略 build/，走同一 not verified 路径）
selftest 全部通过：每个 gate 都会因对应输入变化而变红
```

## 9. 收尾门禁

```text
./gradlew.bat :common:compileJava :common:compileTestJava   BUILD SUCCESSFUL
./gradlew.bat :common:check                                 BUILD SUCCESSFUL
./gradlew.bat :common:test --rerun-tasks                     BUILD SUCCESSFUL（233 suites / 1725 tests / 0 failed）
./gradlew.bat guardLint                                      BUILD SUCCESSFUL
./gradlew.bat :26.2.0:compileJava :26.2.0-fabric:compileJava BUILD SUCCESSFUL
./gradlew.bat :<node>:test（五节点 --rerun-tasks）            BUILD SUCCESSFUL
git diff --check                                             exit 0（无空白错误）
```

日志见 `evidence/`（`gate-common-check.log`、`gate-common-test-full.log`、`gate-guardlint.log`、`gate-platform-compile.log`）。

---

## 10. 限制与未覆盖边界（not verified，不外推）

1. **Ubuntu CI 全链路未跑**：本票所有结论来自本地 Windows 等价执行。新增 CI 步骤（`python3` 在 ubuntu-latest 上可用）未在真实 runner 上验证；`for node in ...; do python3 ... source-roots` 的 Gradle 并发与 CI 环境差异属合并后观察项。
2. **Fabric runtime smoke 未在本票重跑**：`spawnLightning` 三标记证据沿用票 31/32 产出，本票只核对 artifact 与 gate 结果，不重复断言 smoke 通过。
3. **gate 是注册面/声明面证据，不是 runtime smoke**：不施加 `requiredMods`/`clientOnly` 运行期过滤，不执行平台原生回调。已声明能力的 runtime 验证仍属各功能域票。
4. **event/surface 基线中 8 行 `not-verified`**（§4 列出）：这些域在本票输入内没有运行时注册证据，对应域验收保持阻塞；本票**不**把它们改判为 `unavailable`/`partial`。
5. **declaration gate 只覆盖 SERVER 脚本类型的 managed global/member/type 成员集合与签名计数**，不冻结渲染字节；Python declaration 不在本 gate 输入内（其确定性/parity 由票 09 的 `PythonDeclarationDeterminismParityTest` 承担）。
6. **未执行真实 Minecraft 客户端/服务端、loader runtime 或 network session smoke**；不从本票的 JUnit/编译/制品结果推断为已通过。
7. **本票不代表尚未迁移的新功能域已验收**（AC9）：后续域票提交其真实 fixture 并通过同一 gate。

## 11. 已知 classfile 告警

平台与 `common` 编译的既有 Gson/Guava `InlineMe`/`DoNotCall`/`DoNotMock` 注解缺失告警、deprecation 与 `this-escape` 告警均为**既有**输出，不是本票引入，不影响 BUILD SUCCESSFUL。
