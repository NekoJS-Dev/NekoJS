# Managed Surface golden / manifest / Probe / declaration 显式 regenerate 流程（ticket 09 / AC9）

> 本文件是 golden 与派生产物基线的**唯一**更新流程说明。普通测试只读基线，任何基线变化
> 必须走本流程并留下旧新 diff、原因、影响与维护者审阅记录（spec 07）。不存在也不允许
> 引入手写第二规范 JSON：manifest 与 declaration 均由 `NormativeApiContract` 反射派生。

## 1. 基线清单与守护测试

| 基线 | 路径 | 守护测试 | 派生输入 |
|---|---|---|---|
| core API manifest | `common/src/test/resources/nekojs/golden/api-manifest-core.json` | `ApiManifestGoldenTest` | `CoreManagedApiBootstrap.buildContract` 反射 → `ApiSurfaceSnapshot` → `ApiManifestGenerator` |
| managed TS declaration fixture | `common/src/test/probe-ts/generated/index.d.ts` | `ProbeTypeScriptFixtureWriterTest`（字节对比；实际输出写 `common/build/probe-ts-actual/`） | 固定 surface fixture → `ManagedApiDeclarationGenerator`；该 golden 同时被 `npm run test:probe-types`（tsc 契约校验）消费 |
| legacy probe bindings/events | `common/src/test/resources/nekojs/probe/legacy-bindings.expected.d.ts`、`legacy-events.expected.d.ts` | `LegacyProbeCompatibilityTest`（经 `ProbeGoldenSupport`） | catalog fixture → TS probe backend |
| legacy probe 树 | `common/src/test/resources/nekojs/probe/legacy-tree/**` | `LegacyProbeTreeTest`（经 `ProbeGoldenSupport`，整树镜像语义） | `LegacyProbeFixture` → TS probe backend |
| query 域 declaration golden | `src/test/resources/golden/query/datamap-binding.d.txt`、`entityselectors-binding.d.txt` | `DataMapQueryBindingTest`、`EntitySelectorsQueryBindingTest`（根测试树，文本对比） | 生产 probe TS backend `BindingDeclarationGenerator` 对 DataMap / EntitySelectors binding 的 declaration 输出（ticket 25） |
| query 域 capability matrix golden | `src/test/resources/golden/query/capability-matrix-neoforge.txt`、`capability-matrix-fabric.txt` | `QueryToolCapabilityMatrixTest`（loader 探针自动选 golden；fabric 行经由 `:26.1.2-fabric:test` 消费） | capability 探针矩阵：类存在性 + source trace 判定的 supported/partial/unavailable（ticket 25） |
| startup registry typed Builder 契约 golden | `src/test/resources/golden/registry/startup-builders.d.ts`（26.x）、`startup-builders-1.21.1.d.ts`（1.21.1 成员面有真实差异，按版本各冻一份） | `RegistryBuilderSurfaceGoldenTest`（根测试树，守卫按版本选 golden；fluid 条目不进 golden——NeoForge 面，守卫内内存断言） | 生产 `registry_types` 同款 builder 清单 → `RegistryBuilderContract` 反射 → `RegistryBuilderSurfaces.derive` → `RegistryBuilderTsRenderer`（probe TS 后端同一渲染器）（ticket 15） |
| 运行期动态注册声明 golden | `common/src/test/resources/nekojs/dynamic/dynamic-registry-events.expected.d.ts`、`dynamic-builders.expected.d.ts` | `DynamicRegistryEventsDeclarationGoldenTest`（经 `ProbeGoldenSupport`，住 probe 包以复用本表 regenerate 入口） | facade 事件声明：`NekoScriptCatalog.events`（`DynamicRegistryEvents.GROUP`）→ `EventDeclarationGenerator`；动态 Builder 声明：`DynamicBuilderSurfaces.derive()` → `RegistryBuilderTsRenderer`（ticket 16） |
| 节点声明事件面 golden（票 23/24/26/27 域） | `src/test/resources/golden/events-declared/<node>.<startup\|server\|client>-events.d.ts`（每节点 × 每脚本侧一份；跨节点在场事实复用 `nekojs/platform-gates/event-surface-domains.txt` 的 present 行） | `DeclaredEventSurfaceGoldenTest`（根测试树，`platform-gate` tag，随各节点 `:<node>:platformGateTest` 运行；regenerate 开关 `-Dnekojs.golden.regenerate=true` 透传进 gate JVM，写回共享树） | 节点真实注册入口（`EventRegistrationSurfaces`：注解扫描 ∪ fabric 内置清单 → `registerEvents`/`registerClientEvents`）→ `NekoScriptCatalog.events` → `EventDeclarationGenerator`（生产 probe 同链）。只冻 recipe/data（ServerEvents/RecipeViewerEvents）、gameplay 八族、ClientEvents、KeyBindEvents（票 26，2026-09-29 扩展；`Assets` binding 与 `generatedLangs()` 是 binding/plugin 面，不属本家族派生）四域；审阅材料见 `evidence/2026-09-29-golden-decl-prep/`（首批复审批）与 `evidence/2026-09-29-golden-decl-ext/`（票 26 扩展批） |

> 动态注册两行（ticket 16 落地，owner registry-dynamic）冻结的是**声明形状**：事件声明的组名
> `DynamicRegistryEvents`／成员名 `dynamicRegistry` 是票面命名的记录义务（spec 08 工作名
> `ServerEvents.dynamicRegistry` 未采用，理由见该票 baseline REPORT 「命名决策」）；Builder
> 声明 golden 的头注是共享渲染器（ticket 15 所有）的固定文本，其派生输入是
> `DynamicBuilderContract`（不是 `RegistryBuilderContract`）——条目形状复用、路径独立。
> 两份 golden 不构成能力结论：公开激活与 capability 仍由事务/同步 gate（票 21）裁决。

> 查询域两行（ticket 25 落地；本登记是其报告 §7 N9 的收尾，owner managed-surface）与
> startup registry 一行（ticket 15 落地，owner registry-startup）**暂不支持
> regenerate 开关**：`:common:regenerateGoldens` 与 `-Dnekojs.golden.regenerate=true` 只覆盖本表
> common 树基线，根测试树 `src/test/resources/golden/query/**`、`golden/registry/**` 的更新路径是「先改行为/探针输入 →
> 手工比对 → 更新 golden → 按 §3 留旧新 diff、原因、影响与审阅记录」（首例：ticket 25 REPORT
> §8.3）。为这些域补 regenerate 开关是已登记的后续债。

## 2. 显式 regenerate 命令

前置条件：工作树干净（`git status` 无未提交改动），避免把无关改动混入 diff。

```bash
# 全部 probe golden（legacy-tree / legacy-bindings / legacy-events / probe-ts fixture）：
./gradlew :common:regenerateGoldens --console=plain

# 仅 manifest golden（单测 + 开关；开关必须显式为 true，普通测试恒为只读）：
./gradlew :common:test --tests "com.tkisor.nekojs.core.api.ApiManifestGoldenTest" \
    -Dnekojs.golden.regenerate=true --console=plain

# 节点声明事件面 golden（票 23/24/26/27 域；每节点各跑一次，写回共享树 resources）：
./gradlew :<node>:platformGateTest -Dnekojs.golden.regenerate=true --console=plain

# regenerate 后必须重跑 TS 契约校验（probe-ts golden 被 tsc 消费）：
npm run test:probe-types
```

机制说明：
- `-Dnekojs.golden.regenerate=true` 经 `common/build.gradle` 透传为 test system property；
  golden 测试在 regen 模式下把实际产物**写回 src/test/resources 源树**（`ProbeGoldenSupport`
  会把 Gradle 的 `build/resources/test` 副本映射回源树），随后 assumption 跳过断言。
- 普通测试（不带开关）只读 golden；`ApiManifestGoldenTest.regenerateSwitchIsOffForNonTrueValues`
  守护开关不会被普通运行误开（build.gradle 无条件透传字符串 "false" 也不会触发写回）。
- 本票新增的派生测试（`ManagedSurfaceDerivationDeterminismTest`、
  `PythonDeclarationDeterminismParityTest`、`ManagedSurfaceEndToEndChainTest`）不写任何
  golden：确定性验证在内存/临时目录完成。

## 3. 旧新 diff、原因、影响与审阅记录（必填）

regenerate 之后、提交之前：

```bash
git diff -- common/src/test/resources/nekojs common/src/test/probe-ts
```

1. **逐文件审阅 diff**，确认每一处变化都能对应到一个已审阅的契约变化
   （facade/数据类型/事件注册类的增删改、能力条件变化、版本号 bump）。
   出现无法解释的 diff 即回滚重跑（先 `git checkout -- <路径>`）。
2. 在提交说明（或本目录 REPORT 的「golden 差异」小节）记录：
   - **原因**：哪个契约/输入变化触发（引用 commit/facade 符号清单）；
   - **影响**：受影响符号、module、capability 与迁移面（breaking 与否，是否进迁移表）；
   - **旧新 diff**：粘贴或引用 `git diff` 输出要点；
   - **审阅记录**：维护者确认记录（谁、何时、结论）。
3. 只有带上述记录的基线变化才允许提交；普通测试不允许带 regen 开关跑 CI。

## 4. 纪律红线

- 普通测试永远不写 golden / manifest / Probe 基线 / declaration 产物（spec 07 Testing Decisions）。
- regenerate 产物只来自反射派生；**手工编辑基线文件**（而不是修输入再 regenerate）视为无效变更。
- 不新增手写第二规范 JSON；契约唯一规范源是 `CoreManagedApiBootstrap.buildContract` 的反射输入
  （见 `NormativeApiContractOwnerTest`）。
- 收缩 gate（工单 Work item 7）：只有替代 contract/manifest/Probe/declaration 的 behavior、
  golden 与 trace 均通过且旧路径无调用者后，才移除对应旧生成或观察旁路。

## 2026-10-23：事件签名的真实依赖导入

生产 `EventDeclarationGenerator` 不再用默认主扫描白名单过滤已输出事件签名的依赖，交由现有闭包执行当前 exclude/hide 策略。动态事件 golden 因此仅新增 `$DynamicRegistryEventJS` 的实际 import 及其空行；组名、方法名、参数及回调类型均不变。默认排除 core 的完整输出仍须报告该依赖不可用，没有借此允许 Java.type 或生成被排除的类。

更新前该文件字节与 HEAD 一致（SHA-256 `b9bd89c5e3610f9898ec481e91dd0d5309182282d58dfe5abd72def33903452e`），已保存原字节；本次只运行 `:common:test --tests com.tkisor.nekojs.probe.DynamicRegistryEventsDeclarationGoldenTest.facadeEventDeclarationMatchesGolden -Dnekojs.golden.regenerate=true`，没有运行整树镜像或改动其他 golden。工作树已有其他工作，按用户的保留要求采用单方法生成并逐项核对，不清理工作树。

技术审阅由主代理核对生产生成器、失败差异与生成后的两行增量；Luna xhigh 只读执行规范/规格分轴审查，规格发现的未输出 dispatch-key 依赖已另加回归并修正。后续普通测试不带 regenerate 开关。此记录不是维护者人工验收，也不表示完整严格 TSC 或发布验收通过。

同日五节点制品后续：首次 `:26.1.2:build` 在节点声明 golden 门禁失败，核对差异仅新增 `$VillagerTradeDeclarationEventJS`、`$VillagerTradeReloadEventJS` 的真实 import。主代理先备份全部节点基线，再通过五节点各自的 `platformGateTest -Dnekojs.golden.regenerate=true` 生成（1m37s，通过仅表示生成完成）。共十个 server/startup 文件各增加同一行 import，所有方法、参数、回调和 client 文件字节保持原样；本地逐字节检查工具与 JSON 记录已保存。随后正常五节点 platform gate 与两个现代 NeoForge build 复验不带 regenerate 开关，实际结果另记验收材料；不将生成模式中的跳过当作门禁通过。

## 2026-10-24：事件局部身份与raw dispatch元数

生产701dd511为跨包同名事件分配私有import alias；7e484118补齐非静态owner泛型，并让raw dispatch使用已有Java原始类型投影。初次05afbd33完整矩阵在1.21.1节点声明golden比较失败，日志保留为`build/priority-closeout/owner-stamp-matrix.log`，未记作通过。

主代理先保存15份节点基线原字节为`owner-stamp-node-goldens-before.zip`，再仅对五节点DeclaredEventSurfaceGoldenTest使用原生regenerate开关，43秒完成；生成模式中的abort不算普通门禁通过。逐文件检查得到恰好10个server/startup变化、5个client字节不变：五节点raw EntityType、BlockEntityType dispatch补`<any>`元数；两个Fabric节点item/player包同名事件的import及回调引用分别使用私有alias。所有事件组、函数名及顺序不变，Java运行时身份/公开订阅方法不变，不删除、合并事件。

主代理的`check-owner-stamp-goldens.py`以保存原字节检查上述确切变换并输出完整旧新diff、15文件SHA和事件声明数量，结果通过；技术审阅与Luna只读分轴审查另存本轮交接，维护者人工验收尚未记录，不代签。随后的正式门禁必须不带regenerate开关，不能以更新golden代替完整严格声明或发布通过。

## 2026-10-25：枚举实例接口合同恢复

TypeScriptClassRenderer.renderEnum 从编辑后的 IR 保留 Java 枚举的接口继承。初次普通 common/check 的两项 legacy tree golden 比对失败已保留于 build/enum-interface-check.log；不能记作通过。已先将原始 golden 备份到 build/enum-interface-evidence/goldens-before.zip，再仅运行 :common:regenerateGoldens --tests com.tkisor.nekojs.probe.LegacyProbeTreeTest。

主代理核对仅四个文件变化：IsoEra 补 Era，DayOfWeek/Month 补 TemporalAccessor/TemporalAdjuster，ChronoField 补 TemporalField，LinkOption 补 OpenOption/CopyOption。最后两个空接口因新增依赖关系调整声明顺序，内容不变；没有删除或放宽成员。生成模式的跳过不作为门禁通过，随后普通 common/check 和严格消费需要独立通过。该技术核对不代替维护者人工接受。
