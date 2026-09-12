# Ticket 09 实施报告：Managed Surface 单一规范源与声明/Probe 派生链

> 工单：`docs/architecture-refactor/implementation-tickets/09-managed-surface.md`（W5 行：Managed Surface + Probe）。
> 权威 spec：`docs/architecture-refactor/specs/04-public-contract-and-plugin-model.md`（本票）、`07-validation-and-migration.md`（golden 纪律）、`01-maintainer-module-design.md`（W5 owner）。
> 基线 commit：`80b4442d`（ticket 05 已关闭）。本报告 commit 区间：`9626d3a7..本提交`。

## 0. Commit 清单

| Commit | 内容 |
|---|---|
| `9626d3a7` | `refactor(api): ticket 09 normative contract owner + highest-caller test`（AC1） |
| `5fc68c87` | `test(api): ticket 09 deterministic derivation fixtures`（AC2/AC5） |
| `ca1acaa8` | `test(api): ticket 09 legacy shadow characterization`（AC3） |
| `609e7013` | `feat(api)+test: ticket 09 capability conditions + advanced-java smoke`（AC4/AC8） |
| `25469dda` | `feat(api): ticket 09 regenerate flow + minimal example`（AC9/AC7/AC11/AC6 fixture） |
| 本提交 | `docs(baseline): ticket 09 managed surface report`（Phase 6） |

## 1. 现状测绘（Phase 0，不改代码）

### 1.1 NormativeApiContract 今天以什么形态存在？

- **形态**：内存 record（`api/contract/NormativeApiContract.java`，schemaVersion=2），**没有**契约 JSON 文件、没有注解扫描。`resourceName = "nekojs/api-contract/synthesized"`。
- **唯一写入者**：`CoreManagedApiBootstrap.buildContract(URI)`（`common/src/main/java/com/tkisor/nekojs/core/api/CoreManagedApiBootstrap.java:336-372`）——经 `ContractReflector` 反射 7 个 facade 接口（`global:X` + `member:X.*` + receiver 双产出）、5 个数据类型自身方法（record 访问器/实例方法）、common 侧 `ScriptEventRegistrationEvent`，共 117 个符号；integrity/compatibility 哈希 = SHA-256(`NormativeApiContract.toString()`)。
- **生产读取者**：`NekoPluginRuntime.bootstrapOwned`（:119，`NekoPluginBootstrap.bootstrapOwned` 注入 contracts+contributions+globals）→ `JsApiSurfaceResolver.resolve`（类型闭环校验、LEGACY 保留名校验、CONTRIBUTION_NO_CONTRACT/DUPLICATE_CALL_KEY/NATIVE_TYPE_LEAK/RAW_TYPE_LEAK 校验，merge 出冻结 `ApiSurfaceSnapshot`/`ApiEnvironmentSnapshot` + `FrozenApiRegistry`）→ `installManagedCallbackSchemas`（事件契约运行时反射）。
- **测试读取者**：`CoreManagedApiBootstrapTest`、`CoreContractReflectionTest`、`ApiManifestGoldenTest`、`Phase3AFacadeIntegrationTest`、`ManagedApiEnvironmentTest`（均为既有 prior art）。

### 1.2 派生链与第二输入排查

```
CoreManagedApiBootstrap.buildContract (反射，唯一规范源)
        │  VerifiedApiContract (+sha256)
        ├─→ NekoPluginBootstrap / ApiContributionRegistry (contributions 校验)
        ├─→ JsApiSurfaceResolver.resolve → FrozenApiRegistry + ApiEnvironmentSnapshot(冻结)
        │        ├─→ ApiManifestGenerator.generate → ApiManifest → golden api-manifest-core.json
        │        ├─→ NekoScriptCatalog.snapshot().managedApis()
        │        │        └─→ TypeScriptProbeBackend → ManagedApiDeclarationGenerator → .d.ts
        │        └─→ ScriptEnvironmentFactory(:122) → ApiFacadeProxy.global（脚本环境内全局对象）
        ├─→ NekoScriptCatalog.snapshot().legacySurface()（LegacySurfaceAdapter，观察面，独立字段）
        │        └─→ Probe legacy 输出（legacy-bindings/events/legacy-tree golden）
        └─→ ProbeIrBuilder/TypeReflector（catalog 反射 BFS）→ PythonProbeBackend .pyi
                 （TypeScriptClassRenderer 共享 IR；确定性与 probe 内部排序已保证）
```

- **是否存在绕过契约的第二输入**：未发现生产第二规范 JSON。两个"非契约输入"的派生面均为**观察/编辑器面**而非规范源：(a) Python/TS IR 走 catalog 反射（legacy binding 面），(b) `EventContractReflector` 运行时反射 eventGroups 派生 `ContractEvent`（事件 schema 观察）。二者不写回契约。
- TS managed declaration 消费 `managedApis`（冻结 snapshot）；**Python 后端不消费 managedApis**（managed 面在 Python 侧无独立渲染，走 IR 反射路径）——本票不改此结构，Python parity fixture 以 runtime member ↔ .pyi 表达（AC5）。

### 1.3 legacy catalog / LEGACY_PREVIEW 现状

- `LegacySurfaceAdapter.convert` 把 bindings/events/adapters/hostExtensions 转成观察 kind（`global:`/`event:`/`adapter:`/`hostExt:`）+ 占位 void 签名的 `ApiSymbol`，存入 `NekoScriptCatalogSnapshot.legacySurface`（**独立字段**，与 `managedApis` 不合并）→ legacy 不会混入 managed stable。
- `ApiTier.LEGACY_PREVIEW` 枚举保留（贡献分层词汇），但 `ApiSymbol` record 无 tier 字段——javadoc 声称"以 LEGACY_PREVIEW tier 转换"与实际构造不符，本票以 characterization 固化实际行为（观察 kind + 独立字段），不改语义。
- 同名冲突既有防护：resolver `validateLegacyReservations`（LEGACY_NAME_COLLISION）；`ScriptBindingSchema` managed vs legacy 成员集合对比。

### 1.4 能力条件现状

- `CapabilityResolver` + `EnvironmentScope`（scriptType/dist/requiredMods/allowedLoaderIds/loaderVersionRange/minecraftVersionRange）已支持条件判定（provider 侧）。
- 缺口：`NormativeApiContract.ContractCapability(id, contractVersionRange, docs)` 无三态、无条件字段；`JsApiSurfaceResolver.collectCapabilityDefinitions` 把条件硬编码为 null；CORE_ONLY 硬编码 owner `"nekojs"` 与契约 owner `"nekojs-core"` 矛盾（该路径此前无生产使用者、未被测试暴露）。→ Phase 4 补最小实现（见 §3）。

## 2. 逐 AC 判定与证据指针

| AC | 判定 | 证据 |
|---|---|---|
| AC1 唯一反射输入（最高调用者测试） | **pass** | `NormativeApiContractOwnerTest`（6 用例）：双记账断言 contract symbols 逐元素等于反射输入并集；load==buildContract；冻结 surface 与 manifest 恰好等于契约符号集；全部 contribution 引用落契约内。owner 收口**既有已满足**（buildContract 纯反射），本票以测试固化，不新增第二规范 JSON |
| AC2 重复生成稳定 + 普通测试不写 golden | **pass** | `ManagedSurfaceDerivationDeterminismTest`（契约 canonical form/integrity hash、manifest、TS SERVER/CLIENT 逐字节稳定）；`PythonDeclarationDeterminismParityTest`（.pyi 整树逐字节）。golden 写路径仅在 `-Dnekojs.golden.regenerate=true`（`ProbeGoldenSupport`/`ApiManifestGoldenTest`），守护测试 `regenerateSwitchIsOffForNonTrueValues` 通过；本票所有新测试不触碰 golden |
| AC3 legacy 可观察、不升 stable、不覆盖 managed | **pass** | `LegacySurfaceShadowCharacterizationTest`（4 用例）：同名（Text）binding 进 legacySurface 观察面；managed surface 符号集合逐 id 保持契约全集、签名不被 legacy 占位签名覆盖；resolver 判 `LEGACY_NAME_COLLISION`；转换产物只落观察 kind + 占位签名。迁移观察职责未改 |
| AC4 能力三态 + loader/version/context 条件 + 显式失败 | **pass** | `CapabilityStatus` 三态 + `ContractCapability.status/conditions` + `CapabilityDefinition.declaredStatus` + resolver 接线（`ManagedSurfaceCapabilityConditionsTest` 7 用例：条件匹配激活、loader 越界显式 UNAVAILABLE、PARTIAL 可观察、声明 UNAVAILABLE 即使有 provider 也不激活（DECLARED_UNAVAILABLE）、loader 版本门控、manifest 只暴露 active 结论、兼容构造）。保持既有 capability 模型，**未新增通用框架**（AC10） |
| AC5 TS/Py declaration 与 runtime member parity fixture | **pass** | `ManagedSurfaceDerivationDeterminismTest.tsDeclarationMembersHaveRuntimeParity`（interface $Owner/const 全局与契约符号双向恰好相等 + 每签名 anchor）；`PythonDeclarationDeterminismParityTest.pythonDeclarationMembersHaveRuntimeParity`（方法/@property/@staticmethod 语义、嵌套类名展平、包路径 module 归属、负样本 phantom 成员） |
| AC6 Builder 显式 setter = property 写入语义 + 代表性 fixture | **pass（fixture 级）** | `ManagedBuilderWriteSemanticsTest`（4 用例）：显式 setter 与类型化 put 同一规范化路径、build() final identity、只读成员只承诺访问器、builder 创建的契约/manifest/TS 派生 parity；Graal 天然 Bean 行为不作为承诺（不做契约，javadoc+MIGRATION 注明）。启动期/动态/修改域覆盖归各域票（工单 AC6 明示不反向 blocker） |
| AC7 最小可运行示例 + 迁移材料 | **pass** | `docs/architecture-refactor/baseline/2026-09-12-managed-surface/minimal-example.js`（与链路测试同一脚本，只用已过 gate 能力）+ `MIGRATION.md`（变化要点/迁移要点） |
| AC8 java:/Java.type/Java.loadClass/Graal interop/HostAccess 既有行为 | **pass** | `AdvancedJavaInteropSmokeTest`（5 用例，真实 `NekoSharedHostAccess`+`ClassFilter` 生产接线）；`java:` ESM 协议由既有 `NekoModuleResolverTest` 覆盖；`CapabilityResolverTest` 8 用例回归通过 |
| AC9 显式 regenerate + 旧新 diff + 审阅记录 | **pass** | `REGENERATE.md`（基线清单/守护测试映射、显式命令、git diff 审阅、原因/影响/审阅记录模板、普通测试只读红线）；既有 `./gradlew :common:regenerateGoldens` 入口与 regen 开关机制核对无误 |
| AC10 无新独立 API artifact / 全仓 catalog / 第二规范 JSON / 通用 capability 框架 | **pass** | 全部新增类型为既有模型的最小扩展（`CapabilityStatus` enum + 2 record 字段）；无新 Gradle project/jar/JSON；能力条件复用既有 `EnvironmentScope`/`CapabilityResolver` |
| AC11 至少一个真实脚本穿透完整链路 | **pass** | `ManagedSurfaceEndToEndChainTest`：同一脚本（ID/Text/NBT 调用，Graal 运行时经 ApiFacadeProxy 契约 invoker）→ 段 1 契约反射断言 → 段 2 manifest 断言 → 段 3 TS declaration 断言 → 段 4 Python IR 反射断言 → 段 5 运行时结果 `{id:"nekojs:chain",label:"chain-example",count:3}`。`npm run test:probe-types` 通过（TS 契约校验）。外部 addon/Probe 差异按工单属协调项，不在本票冒充验收 |

## 3. 改了什么 / 没改什么

**改了（主代码，最小化）**：
- `api/capability/`：新增 `CapabilityStatus`；`CapabilityDefinition` + `declaredStatus`（8 参构造兼容）；`CapabilityResolver` 接线契约条件 + DECLARED_UNAVAILABLE 显式报告 + CORE_ONLY 接受核内 owner 两种拼写（`nekojs-core`/`nekojs`，addon 仍拒绝）。
- `api/contract/NormativeApiContract.ContractCapability`：+ `status`/`conditions`（3 参构造兼容）。
- `core/api/JsApiSurfaceResolver.collectCapabilityDefinitions`：传递 conditions+status（原硬编码 null）。

**没改（既有已满足，仅补测试固化）**：
- 契约 owner 收口（`buildContract` 纯反射已成立）、contributions 校验与 contract identity；
- manifest/TS/Python 派生器（确定性已由既有排序保证）、Probe 后端结构；
- legacy catalog/LEGACY_PREVIEW 语义（观察面不变）；
- 数据面路径/格式/key/wire；06/07 candidate 语义；JSX UI（归 40 号票）。

## 4. 测试计数对比

| 套件 | 基线（05 号票后） | 本票后 | 差值 |
|---|---|---|---|
| `:common` | 181 suites / 1366 tests / 4 skipped | **189 suites / 1400 tests / 4 skipped / 0 failures** | +8 suites / +34 tests（全为本票新增：OwnerTest 6、DeterminismTest 5、PythonParityTest 2、LegacyShadowTest 4、CapabilityConditionsTest 7、InteropSmokeTest 5、ChainTest 1、BuilderSemanticsTest 4） |
| `:common-api-processor:test` + `guardLint` | 通过 | **通过**（`./gradlew :common:check :common-api-processor:test guardLint` 与显式 `./gradlew guardLint` 均 BUILD SUCCESSFUL） | 0 |
| `:26.1.2:check` | 33 suites / 155 tests / 34 skipped | **BUILD SUCCESSFUL；33 suites / 155 tests / 34 skipped / 0 failures（与基线一致）** | 本票 0 新增节点测试 |
| `npm run test:probe-types` | 通过 | **通过** | 0 |

## 5. golden 是否发生变化及审阅记录

**无变化。** 本票未执行 regenerate；`api-manifest-core.json`、`probe-ts/generated/index.d.ts`、`probe/legacy-*` 全部保持原样（工作树 diff 为零，`git status` 仅含源码/测试/文档新增与工单认领改动）。依据：契约输入（facade/数据类型/事件注册类）零变化 → 反射派生逐字节稳定（AC2 fixture 直接证明）；golden 对比测试（ApiManifestGoldenTest、ProbeTypeScriptFixtureWriterTest、LegacyProbe*Test）全数通过。

## 6. not-verified + owner

| 事项 | 状态 | Owner |
|---|---|---|
| `NBT.compound()`（CompoundBuilder host object）经契约 invoker 返回触发 `NATIVE_TYPE_LEAK`（契约承诺 ↔ `ApiValueMarshaller` raw-return 守卫不一致；生产 `ScriptEnvironmentFactory:122` 同路径） | not-verified / 既有不一致，本票不改 marshaller 语义（属 managed surface 行为变更，需 regenerate+审阅） | W5 Managed Surface owner（建议后续票：给 CompoundBuilder 定义 SYMBOL 返回类型并走 proxy 包裹，或降级契约承诺） |
| 外部 addon 经真实 loader discovery/fat jar 消费本票派生契约 | not-verified（工单明示为 PLUGIN_ADDON 协调项） | 插件组（PLUGIN_ADDON） |
| `:26.2.0` / `:1.21.1` / 两个 Fabric 节点 check | 本票未跑（Java 源仅 `:common`，五节点源零改动；主节点 26.1.2 已验） | W9/W10 发布 gate |
| Python 侧 managed globals 独立渲染（Python 后端不走 managedApis，走 IR 反射） | 结构性事实记录，未改变 | W5（后续如需 managed API 的 Python stub，另行立项） |

## 7. 问题与处理

1. **CORE_ONLY owner 词表矛盾**（Phase 4 发现）：provider registry owner 必须匹配契约 owner（`nekojs-core`，`ApiContributionRegistry.validateOwnership`），而 `CapabilityResolver.validateProviderPolicy` 硬编码只认 `"nekojs"` → 生产路径上任何核内 capability provider 都会失败。处理：CORE_ONLY 接受两种核内拼写，addon 一律拒绝；`CapabilityResolverTest` 8 用例回归通过。
2. **`NBT.compound()` NATIVE_TYPE_LEAK**（Phase 5 链路测试发现）：契约 invoker 拒绝 host object 返回（`ApiValueMarshaller:459`）。处理：链路测试与最小示例改用已验证的 `NBT.of` 值路径；问题记入 §6 归后续处理。这也说明 AC6 的"同一写入语义进入同一校验路径"目前只在 Java 侧 fixture 成立，JS 侧 builder 调用要等上述修复。
3. **`LegacySurfaceAdapter` javadoc 与实现不符**（Phase 0 发现）：javadoc 声称产出 `LEGACY_PREVIEW` tier，实际 `ApiSymbol` 无 tier 字段。处理：characterization 固化实际行为（观察 kind + 独立字段），不改语义；tier 词汇保留给贡献层。
4. **工单文件只读**：本票未改 `implementation-tickets/09-managed-surface.md`（工作树中该文件的改动为协调者认领 status/assignee，不属于本票）。
