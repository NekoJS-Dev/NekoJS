# 票 23 证据报告：Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径

- 执行者：zed-flash-23（GLM-5.3 subagent worktree，接续一次中断的同票运行并继承其未提交工作）。
  实施区间 `97aab2b0..`（分支 `ticket-23-recipe-data-surface`）。
- 基线：mult@97aab2b0（已含票 29 Assets/Lang、票 19 pack-trust、票 08 loader-smoke）。
- 输入（characterization，本票不改其语义）：`RecipeLifecycle(Point/Register)`、`RecipeEventJS`、
  `MinecraftRecipeHandler`、`RecipeManagerMixin`（26.x/1.21.1 孪生）、catalog snapshot
  （`NekoScriptCatalog`/`EventSurfaceOwnershipTest`）、`TagEventJS`/`TagLoaderMixin`、
  `LootTableEventJS`、`RecipeViewerEvents`+JEI 门控插件。

## 0. 中断运行继承（kept / dropped）

| 中断产物 | 处置 |
|---|---|
| `common/.../wrapper/DataGenerationBatch.java`（候选→校验→原子发布，manifest/回滚/用户文件保护） | **保留**，逐行复核：manifest 只增不删（行 16 数据保护核对无误）、发布中段失败回滚、`.nekojs-datagen` 状态目录与 pack 根隔离均符合 AC6/AC7 |
| `PluginGenerationHooks.runGenerateData(...)` | **保留**：插件先、脚本后、同一 generator、单批收口；javadoc 已核对与实现一致 |
| `DataGeneratorJS` 的 WriteObserver/sealed seam | **保留**；新增注释由中文改写为英文（语言规则） |
| `DataGenerationBatchTest`（7 用例） | **保留**，全部通过 |
| `DataGenerationAggregationTest`（4 用例） | **保留**，全部通过 |
| 平台接线（`ServerEventListener.postGenerateData` 走批路径） | 中断运行**未开始**，本票补齐（26.x + 1.21.1 双侧） |

无丢弃项。中断产物未覆盖 afterRecipes 时序、loot/tags fixture、viewer trace、示例与迁移材料，
本票补齐。

## 1. 范围与实现摘要（what changed）

### 1.1 主源码

| 文件 | 变更 |
|---|---|
| `common/.../wrapper/DataGenerationBatch.java`（新） | 非 Assets 数据生成批：`open`（清同阶段陈旧 scratch、建候选区）→`validate`（候选根包含性、`.json` 可解析、可回读）→`publish`（备份日志 + 逐文件原子 move + 中段失败全量回滚；用户手改/未知文件跳过并上报；manifest sha256 只增不删）；`discard` 显式丢弃。状态目录 `<gameDir>/nekojs/.nekojs-datagen/<stage>/`（pack 不可见）。 |
| `common/.../core/plugin/PluginGenerationHooks.java` | 新增 `runGenerateData(Path, String, Consumer<DataGeneratorJS>)`：插件 `generateData`（逐插件贡献者标注 + 异常隔离）→ 脚本阶段（平台 lambda 内 post `ServerEvents.GENERATE_DATA`）→ 单批 `validate+publish`。旧 `fireGenerateData` 保留（公开面不删）。 |
| `common/.../wrapper/DataGeneratorJS.java` | 包内 seam：`WriteObserver`（逐写归属/覆盖追踪）、`sealForReadBack`（发布后读回切 active 根、写入永久拒绝）。脚本公开面不变。 |
| `src/main/.../listener/ServerEventListener.java` + 1.21.1 孪生 | `postGenerateData` 改为委托 `runGenerateData`（唯一数据生成管线）；失败日志明示「nothing published / previous active retained」。 |
| `src/main/.../mixin/RecipeManagerMixin.java` + 1.21.1 孪生 | **AC3 修复**：`AFTER_RECIPES.post` 与插件 `afterRecipes` 从「codec 解析前」移到「整批解析完成并提交（`RecipeMap.create`/`replaceRecipes`）之后」，after 阶段独立 try/catch；提交仍为单引用替换（无半更新）。 |
| `versions/1.21.1/.../wrapper/event/server/TagEventJS.java` | **1.21.1 孪生缺陷修复**（本票 fixture 捕获）：`remove()` 的 `(id, isTag)` RemovalKey 匹配此前未从 26.x 同步，旧实现按 `TagEntry` 恒等匹配导致源表既有条目永远删不掉；补回 `RemovalKey` record 并对齐匹配逻辑。 |
| `wiki/事件参考.md`、`wiki/命令.md`、`wiki/插件开发.md` | afterRecipes 新时序的玩家可见文档同步（3 行）。 |

### 1.2 测试/fixture（详见 §3）

- `common`：`DataGenerationBatchTest`（7）、`DataGenerationAggregationTest`（4）、
  `SchemaRecipeBuilderContractTest`（6，schema/type/namespace 契约 + 值转换 + 数组包装）。
- `src/test`（共享树，随节点求值）：`Ticket23RecipeDataPhaseTraceTest`（8，纯文件读源 trace：
  afterRecipes 后置×两时代、单数据生成管线×两侧、lootTables→generateData 次序、viewer JEI 门控、
  fabric 缺席为显式）、`RecipeEventJSJsonContractTest`（8，JSON 生成/修改/值转换/id 重绑定）、
  `RecipeFilterApplicationTest`（4，id 族 filter 应用；`VanillaRegistryProbe` 守卫）、
  `LootTableEventJSJsonSurfaceTest`（5，修改/冲突/删除/stale）、`TagEventJSApplyContractTest`（5，
  add/remove/replaceAll/removeAll/reload 再断言）。

### 1.3 明确不动

- 21（registry dynamic sync）域文件、tickets README 导航、AGENTS/CONTEXT、`docs/adr/`、
  票 29 的 `ClientEvents.generateAssets`/`generateLang`/`generatedLangs` 及 Assets 管线。
- 未新增事件 bus、未新增 Assets 事件、未改任何 golden/declaration/manifest。
- 未删除公开事件/helper（AC15：`fireGenerateData` 等旧入口保留；被替换的只是平台监听器内部
  的「直写 active 根」旁路——它不是公开 API，替代行为即 `runGenerateData` 本身）。

## 2. 逐条 AC 状态（判定明细见票 23 文档 AC 区）

| AC | 状态 | 依据 |
|---|---|---|
| 1 无第二声明/catalog 派生 | 部分满足（不勾选） | 无第二声明已由票 14 `EventSurfaceOwnershipTest` + 本票 trace（单次 `EventGroup.of("RecipeViewerEvents")`）钉住；TS/Python 声明对 recipe 域 0 命中（同票 26 缺口）→ owner 09/33/34 |
| 2 schema/type/namespace JSON + builder 值转换/filter/generated id | 满足 | `SchemaRecipeBuilderContractTest` 6/6、`RecipeEventJSJsonContractTest` 8/8、既有 `RecipeEventJSGeneratedIdTest`/`RecipeFilterAdapterTest`；filter **应用面**用例 `RecipeFilterApplicationTest`（4）在无头 JVM 两侧均按 probe 守卫 skip（`RecipeHolder` 类初始化需注册表），真实运行归 ModDev unitTest/票 34——已如实计入 §5-G3 |
| 3 afterRecipes 提交后触发/无半更新 | 满足（静态 trace + 提交语义） | trace×两时代 + mixin 单引用提交；**运行期 smoke 未跑**（真 RecipeManager）→ 缺口记票 34 |
| 4 plugin+script 同一路径/相对次序 | 满足 | `DataGenerationAggregationTest` 4/4（插件先/同 generator/单批）+ trace（lootTables→generateData；recipe 管线不泄漏） |
| 5 错误隔离可观察 | 满足 | 聚合测试（抛错插件隔离点名、脚本阶段失败整批不发布）；脚本源定位沿既有 `ScriptErrorReporter`+`ScriptEventSourceIdTest`（引用，未重跑） |
| 6 候选→校验→原子发布/失败保留 | 满足 | `DataGenerationBatchTest` 7/7（含注入式发布中段失败回滚，红→绿见 §4） |
| 7 非 Assets 输出可验证/无 Assets 事件 | 满足 | 同上 + ServerEvents.java 本票 diff 0 行 |
| 8 loot/tags fixture | 满足 | `LootTableEventJSJsonSurfaceTest` 5/5、`TagEventJSApplyContractTest` 5/5；LootTableLoadEvent 应用路径未跑（需真实 reload 管线）→ 票 34 |
| 9 viewer 条件能力 | 满足（trace 面） | 门控插件注解 + 只经其注册 + JEI 插件唯一 post + fabric 零接线，全部 trace 钉住；**真 JEI 运行未跑** → 票 34 |
| 10 MC 类型留在版本树/节点 | 满足 | `DataGenerationBatch`/hooks 无 MC import（guardLint 复核）；mixin/监听器在 `src/`+`versions/1.21.1` |
| 11 artifact/source trace/runtime smoke 按节点 | 部分满足 | `:26.1.2:test` 383 用例仅 1 个**基线既有**失败（见 §5-G2）；1.21.1/fabric 见 §5；JEI/runGameTestServer 未跑 |
| 12 脚本错误定位 | 满足（既有面引用） | `ScriptEventSourceIdTest`/`ScriptEventsDeclarationDiagnosticsTest` 为既有覆盖；本票新增错误均为普通 IAE/ISE 英文消息、无 validator 提示 |
| 13 示例+迁移材料 | 满足 | `examples/generate-data.js`、`recipe-loot-tags.js`、`recipe-viewer.client.js` + `MIGRATION.md` |
| 14 收缩 gate/不升格 Extension Point | 满足 | 无新 Point、无第二 registry、公开面未删；平台内部直写旁路已由单管线替代（trace 断言 `new DataGeneratorJS`/`fireGenerateData` 在监听器内为 0） |

## 3. 红与绿（red → green 证据）

1. **发布中段失败保留（AC6，TDD 主证）**：`DataGenerationBatchTest.midPublishFailureRollsBackEverythingAndRetainsOldActive`
   以 `setMoveFailureForTest` 在第二个文件替换点注入确定性 IO 失败。**突变验证**（mutation
   red→green，transcript `command-output/03-red-green-mutations.txt`）：
   - 突变 A（删除 `publish()` catch 块中的 `rollback(...)` 调用）→ 该用例红
     （`expected: ...old... but was: ...new...`）；还原 → 绿。
   - 突变 B（`validate()` 不再拒绝非法 JSON）→ `invalidJsonCandidateFailsValidationAndRetainsPreviousActiveData` 红；还原 → 绿。
2. **afterRecipes 时序（AC3）**：把 26.x mixin 的 after 阶段临时移回提交前（恢复旧序）→
   `Ticket23RecipeDataPhaseTraceTest.afterRecipesFiresOnlyAfterTheParsedBatchIsCommitted_26x` 红；还原 → 绿（同一 transcript）。
3. **单管线（AC4）**：`ServerEventListener` 临时恢复旧直写形态 →
   `generateDataRunsThroughTheSingleBatchPipeline_26x` 红（连带 lootTables→generateData 次序断言红）；还原 → 绿（同一 transcript）。
4. **1.21.1 tag 孪生缺陷（真红→绿，非注入）**：`TagEventJSApplyContractTest.addAppendsAndRemoveDeletesByIdIgnoringSource`
   首次在 `:1.21.1:test` 红（`expected: <[minecraft:dirt, nekojs:added]> but was: <[minecraft:stone, ...]>`）——
   26.x 侧的 RemovalKey 匹配从未同步到 1.21.1 孪生（旧实现按 `TagEntry` 对象恒等匹配，源表既有
   条目永远删不掉，文件尾还留着孤儿注释）。修复（对齐 26.x 的 `(id, isTag)` 匹配 + 补回
   `RemovalKey` record）后 1.21.1 全绿。这是跨时代孪生同步债的真实缺陷，由本票 fixture 捕获。

## 4. 验证命令与结果（真实执行）

| 命令 | 结果 |
|---|---|
| `gradlew :common:test --tests "*DataGenerationBatchTest*" --tests "*DataGenerationAggregationTest*"` | BUILD SUCCESSFUL（11 用例） |
| `gradlew :common:test --tests "*SchemaRecipeBuilderContractTest*"` | BUILD SUCCESSFUL（6 用例） |
| `gradlew :common:check --console=plain` | **BUILD SUCCESSFUL in 1m32s**（含隔离检查、`PluginHookPairingTest`） |
| `gradlew :26.1.2:test --tests "*RecipeEventJSJsonContractTest*" --tests "*Ticket23RecipeDataPhaseTraceTest*"` | BUILD SUCCESSFUL（17 用例） |
| `gradlew :26.1.2:test --tests "*LootTableEventJSJsonSurfaceTest*" --tests "*TagEventJSApplyContractTest*"` | BUILD SUCCESSFUL（10 用例） |
| `gradlew :26.1.2:test --tests "*RecipeFilterApplicationTest*"` | BUILD SUCCESSFUL（26.x 裸 JVM：4 skipped，probe 守卫按设计跳过） |
| `gradlew :1.21.1:test --console=plain` | 280 tests, **1 failed**（`Ticket08LoaderDiscoveryTest.initializationError`，基线既有同类）, 14 skipped；**本票新增 fixture 全绿**。另：首次运行时本票 `TagEventJSApplyContractTest` 在 1.21.1 捕获真实缺陷（§3.4），修复孪生后全绿 |
| `gradlew :26.1.2-fabric:test --console=plain` | 236 tests, 1 failed（同一 `Ticket08LoaderDiscoveryTest` 基线既有）, 25 skipped；本票在 fabric 编译的共享测试（含 phase trace）全绿 |
| `gradlew guardLint --console=plain` | BUILD SUCCESSFUL in 2s（`common` MC/loader 隔离未放宽） |
| `gradlew :common:check :26.1.2:test --tests <本票全部新 fixture>`（最终态复核，突变还原后） | BUILD SUCCESSFUL（`06-final-state-verification.txt`） |

节点汇总：

| 节点 | 结果 |
|---|---|
| `:common:check` | pass（1m32s；含隔离检查与 `PluginHookPairingTest`） |
| `:26.1.2:test` | pass 除 Ticket08 基线既有失败（基线 stash 复现 352/1 同用例；本票 383/1/58） |
| `:1.21.1:test` | pass 除同一 Ticket08 基线既有失败（280/1/14） |
| `:26.1.2-fabric:test` | pass 除同一 Ticket08 基线既有失败（236/1/25） |

## 5. 未运行项与 owner

- **G1（NEKO- 码缺口）**：本分支无 `wiki/en_us/Error-Reference.md` 码表；按约束未发明码号，
  新增错误沿用 `IllegalStateException`/`IllegalArgumentException`（英文）。码表建立归
  diagnostics 域（票 30）。
- **G2（基线既有失败）**：`Ticket08LoaderDiscoveryTest` 的 `Platform has already been initialized`
  为测试顺序敏感的基线缺陷（基线 stash 复现，352/1 failed）。owner：票 08 域/维护者 triage，
  本票不改其文件。
- **G3（运行期 smoke 未跑）**：真实 RecipeManager reload 的 afterRecipes 时序、
  `LootTableLoadEvent` 应用、JEI viewer 真集成、`runGameTestServer`、artifact smoke 均未运行
  （无头环境）。owner：票 34（P4 runtime smoke）。
- **G4（declaration 面）**：TS/Python 声明对 recipe 域 0 命中（同票 26 结论）。owner：09/33。
- **G5（fabric 能力）**：`generateData`/`lootTables`/`tags`/viewer 在 fabric 节点 unavailable
  （显式，非静默）；fabric 移植归 LoaderBridge 票（`TagEventJS` 文件头既有 TODO(fabric)）。

## 6. golden / 声明 / 契约

- 本票**未改动任何 golden/declaration/manifest**；`ServerEvents.java`、`RecipeViewerEvents.java`
  声明 diff 0 行。
- 有意的玩家可见行为变化共 2 项（afterRecipes 时序、generateData 落盘语义），均入 `MIGRATION.md`
  与 wiki；无 wire/persistent-data 变化。

## 主会话复核修正（2026-09-22，合并后）

- PublishResult 可见性（复核发现 AC5 缺口）：`postGenerateData` 原丢弃
  `runGenerateData` 返回的 PublishResult——用户文件被跳过等保护决策对运维不可见。现两
  `ServerEventListener` 双胞胎记录 INFO（published/skipped 计数）+ 跳过非空时 WARN 列出
  user-owned 文件（>10 截断）。
- 文件内重复 JSON key（复核发现 AC6 缺口）：Gson 默认对同一对象内重复成员名 last-wins，
  两个贡献者写同一 key 会静默丢一个。`validate` 现以 lenient JsonReader 严格遍历拒绝重复
  对象键（解析接受面与原先 JsonParser 一致，仅新增重复键拒绝）：
  `duplicateJsonObjectKeysFailValidationInsteadOfSilentlyLastWinning`。
- manifest 不可读降级（编码规范：fallback 必须可观察）：`readManifest` 静默回退现补 WARN
  日志，说明「所有 active 文件按 user-owned 处理，不会覆盖任何文件」。
