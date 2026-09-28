# 23: Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径

**What to build:** 整合包作者在既有 recipe/data 事件中编写 recipe schema、JSON builder、数据生成（不含 Assets）、loot 和 tags，并配置 recipe viewer 信息；plugin `generateData` Hook 与脚本 `ServerEvents.generateData` 进入同一条数据生成路径；调用经过 managed surface、平台/version Adapter、资源生成或回读，得到可验证产物、afterRecipes 时序、reload 清理、TS/Python declaration 和节点 capability。所有事件均复用原 bus，不新增平行事件。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)

**Status:** in-review（实现/测试/证据已交付；AC1、AC11 部分满足未勾选，owner 09/33/34；维护者 sign-off 未代答）

**Assignee:** zed-flash-23（main-session agent；GLM-5.3 subagent worktree）

**Claim record (2026-09-22):** worktree `../NekoJS-mult-t23` on branch `ticket-23-recipe-data-surface`（基于 `3a6380b1`）。预计改动范围：recipe schema/type/namespace characterization 与 contract fixture、recipe JSON builder/值转换/filter/generated id 接入既有 managed surface 与平台 Adapter、plugin `generateData` 与 `ServerEvents.generateData` 聚合到同一 DataGenerator 路径、非 Assets 数据生成的候选落盘→校验→原子发布与失败保留、loot/tags fixture、recipe viewer 条件能力 trace、afterRecipes 时序断言、示例与迁移材料、`baseline/2026-09-22-recipe-data-surface/` 证据。不修改 21 registry 域文件，不新增 Assets 事件（29 域）；golden 只读，需更新的项如实标注为门禁未过而非改写。

**Optional:** false

**Selected:** true

**Human input:** none

**Work items:**

- 以现有 RecipeLifecycle、RecipeEventJS、MinecraftRecipeHandler、RecipeManagerMixin 和 catalog snapshot 为 characterization，建立 recipe schema/type/namespace contract fixture。
- 把 recipe JSON builder、值转换、filter、generated id 和错误归属接入同一 managed surface 与平台 Adapter，MC 类型/mixin 留在 src/或节点。
- 以 characterization 固定 plugin `generateData` 与脚本 `ServerEvents.generateData` 的现有阶段顺序、贡献顺序和输出所有权，再让二者聚合到同一 DataGenerator/Adapter 路径。
- 验证 DataGeneratorJS 的非 Assets 数据输出、路径、JSON 结构、失败保留和资源回读；Assets 继续复用既有 ClientEvents.generateAssets，不由本票新增事件。
- 为 loot table/pool/entry 与 tags 的既有事件时机、修改、冲突、reload 清理和生成产物建立 fixture。
- 为 recipe viewer/JEI 条件能力建立 adapter/source trace 与 declaration，不在未安装 JEI 的环境伪装能力。
- 输出 afterRecipes 生命周期、transaction/reload/delete-cleanup、两 loader artifact/runtime smoke 和 capability matrix 证据。
- 收缩 gate：recipe/data/loot/tags/viewer 旧绑定或生成旁路只有在替代 behavior、declaration、artifact/source trace 与无调用者证据通过后移除；公开事件与 helper 功能不删除，清理随域内票完成而不是 final release 统一处理。

## Acceptance criteria

- [ ] 既有 recipe 事件名和 bus 没有第二份声明；catalog/golden 能从真实 runtime member 推导 TS/Python 声明。**部分满足（不勾选）**：无第二声明已钉住（票 14 既有 `EventSurfaceOwnershipTest` + 本票 `Ticket23RecipeDataPhaseTraceTest` 断言 `EventGroup.of("RecipeViewerEvents")` 全仓唯一、bus 只由 JEI 插件 post）；但 TS/Python declaration 对 recipe/data 域 0 命中（同票 26 缺口，`ServerEvents.java`/`RecipeViewerEvents.java` 本票 diff 0 行、golden 只读未动）——2026-09-29 维护者批准 golden 包合并:ServerEvents(含 recipes/generateData/lootTables/tags 及 dispatch 重载)与 RecipeViewerEvents 已入逐节点 events-declared TS golden(守护测试+再生工作流,evidence/2026-09-29-golden-decl-prep/),TS 腿闭合;Python .pyi 为包内提案未实施,随 09/34 统一收口。
- [x] representative recipe schema/type/namespace 输入生成或修改预期 JSON，builder 值转换、filter 和 generated id 行为有测试。【evidence: `common: SchemaRecipeBuilderContractTest` 6/6（schema 契约/必填/默认值/数组包装/只读面）；`src/test: RecipeEventJSJsonContractTest` 8/8（生成/修改/值转换/id 重绑定/非法 id 拒绝）；`RecipeFilterApplicationTest` 4 用例（id 族 + and/or/not，probe 守卫）；既有 `RecipeEventJSGeneratedIdTest`、`RecipeFilterAdapterTest`；命令输出 `command-output/06`】
- [x] afterRecipes 只在 recipe 数据完整提交后的既有生命周期触发；失败、取消或 reload 中断不产生半更新。【evidence: 本票修复 26.x+1.21.1 两 mixin 孪生（after 阶段移到 `RecipeMap.create`/`replaceRecipes` 单引用提交之后，独立 try/catch）；`Ticket23RecipeDataPhaseTraceTest.afterRecipesFiresOnlyAfterTheParsedBatchIsCommitted_26x/_1211` + 突变红→绿（`command-output/03` Mutation C）；运行期真 RecipeManager smoke 未跑→票 34（REPORT §5-G3）】
- [x] plugin `generateData` 与脚本 `ServerEvents.generateData` 使用同一候选收集、生成、发布和回读路径；二者与 recipe/loot/tags 的相对阶段顺序由真实 fixture 固定，不形成第二数据生成管线。【evidence: `DataGenerationAggregationTest` 4/4（插件先/同一 generator 实例/单批/抛错插件隔离）；trace 钉住两平台监听器只经 `PluginGenerationHooks.runGenerateData`（`new DataGeneratorJS`/`fireGenerateData` 出现 0 次）且 lootTables→generateData 次序、recipe 管线与数据管线互不泄漏；突变红→绿 Mutation D】
- [x] plugin 与脚本贡献的错误隔离可观察：单个贡献的插件 id、脚本 source、字段和阶段可定位；无法证明安全隔离时整批失败并保留旧数据，不产生半写或混合来源产物。【evidence: `DataGenerationAggregationTest`（插件类名进日志、脚本阶段失败整批不发布且旧 active 保留）；批内逐文件 owner/覆盖 trace（`DataGenerationBatchTest` duplicateKey 用例）；脚本 source 定位沿既有 `ScriptErrorReporter`+`ScriptEventSourceIdTest`（本票引用未改）；主会话复核追加：发布结果（published/skipped 计数与 user-owned 跳过清单）现进入 SERVER 日志（两 `ServerEventListener` 双胞胎 INFO+WARN），`PublishResult` 不再被调用方丢弃】
- [x] 非 Assets 数据生成先写候选区域，验证路径包含性、重复 key/覆盖策略、JSON 结构和可回读后再原子发布；失败保留旧 active，不覆盖用户编辑文件，cache/生成物可再生性遵守数据保护清单。【evidence: `DataGenerationBatchTest`（候选落盘/发布后读回/跨贡献者重复路径 last-wins+trace/非法 JSON 拒绝/注入式中段失败回滚/用户文件永不覆盖/早期产物保留/封印拒写）；数据保护行 16 语义（不无条件再生）与 `.nekojs-datagen` 状态目录可再生均有用例；突变红→绿 Mutation A/B；主会话复核追加：文件内 JSON 对象重复成员名现被严格校验拒绝——Gson 默认 last-wins 会静默吞掉一个贡献者的意图（`duplicateJsonObjectKeysFailValidationInsteadOfSilentlyLastWinning`），manifest 不可读降级现有 WARN 可观察】
- [x] 非 Assets 数据生成的路径、JSON、覆盖策略、失败保留和回读结果可验证，且不新增 Assets 事件。【evidence: 同上；`ServerEvents.java` 本票 diff 0 行，票 29 Assets 域文件未触碰】
- [x] loot 与 tags 的修改、冲突、删除/清理和 reload 后 stale 状态有外部行为 fixture。【evidence: `LootTableEventJSJsonSurfaceTest` 5/5（修改/setJson-vs-remove 冲突 last-wins/删除/stale 跨 reload 保留/方块表 id 映射）；`TagEventJSApplyContractTest` 5/5（add/remove/replaceAll/removeAll/reload 再断言）；**并捕获修复 1.21.1 孪生 remove 匹配缺陷**（REPORT §3.4）；`LootTableLoadEvent` 应用路径未跑→票 34】
- [x] recipe viewer 信息仅在相应 JEI/平台条件成立时声明 supported，否则显式 partial/unavailable，不静默 no-op。【evidence: trace 钉住组只经 `@RegisterNekoJSPlugin(clientOnly=true, requiredMods="jei")` 门控插件注册、bus 只由 `@JeiPlugin` post、JEI 接线整文件 neoforge 守卫（fabric 编译 0 行）、fabric 无 generateData bus（引用为未知成员，显式失败）；能力矩阵见 MIGRATION §4；真 JEI 运行未跑→票 34】
- [x] MC 类型、mixin和平台 registry/loader 操作留在 共享 MC-facing 或节点 Adapter，common 不复制平台业务逻辑。【evidence: `DataGenerationBatch`/`PluginGenerationHooks`/`DataGeneratorJS` 无 MC/loader import；`guardLint` BUILD SUCCESSFUL（`command-output/04`）；mixin/监听器在 `src/`+`versions/1.21.1`】
- [ ] NeoForge/Fabric 的 artifact、source trace 和 runtime smoke 按节点等级记录，不能用局部单测冒充。**部分满足**：三节点 test 已跑（26.1.2: 383/1 failed 仅基线既有 Ticket08 flake（基线 stash 复现 352/1 同用例）；1.21.1: 280/1 同上；26.1.2-fabric: 236/1 同上）+ source trace fixture 全绿。**2026-09-28 证据收口（`command-output/07..09`）**：26.1.2 真 `runServer` datagen smoke 绿——脚本 `generateData('after_mods')` 经候选→校验→原子发布写出 2 个文件，artifact 存在性、staged/published 布局（active 根无 `.nekojs-datagen`、成功发布后 candidate/backup 清空）、manifest sha256 与实文件逐一比对均过，`lootTables → published → recipes → afterRecipes(提交后) → Done` 次序钉住，全日志 0 ERROR；26.1.2-fabric 真 `runServer` 显式缺席 smoke 绿——引用 `ServerEvents.generateData` 的脚本在 binding preflight（`Binding 'ServerEvents' has no member 'generateData'`）与 execution（`Unknown identifier: generateData`）两层报错点名源文件，marker 未运行，服务器照常 `Done`；`:26.2.0:test` 合并树 448/0/58skip 全绿（Ticket08 flake 本轮未复现）。**仍开放**：`runGameTestServer`、JEI 真集成（viewer 保持 conditional）、插件 `generateData` hook 真机装载（当前无生产插件实现，运行期仅脚本侧；hook 聚合有 JVM 测试）——owner 票 34/维护者。**本轮新记录缺陷 ×2（未修，REPORT 证据收口 D-A/D-B）**：`DataGeneratorJS.json` 只收 pack 根相对路径、拒绝 `ns:` id 形（基线示例 `generate-data.js` 即 id 形，按现状不可运行）；`json(path, jsObject)` 的 JS 对象实参在真引擎落到 `String.valueOf` 写出 `[object Object]`（既有单测只覆盖 JSON 字符串形）——owner 本票域维护者 triage
- [x] 脚本错误能定位到事件域、owner、源文件和生成/修改阶段，普通错误不嵌 validator 提示。【evidence: 既有 `ScriptEventSourceIdTest`/`ScriptEventsDeclarationDiagnosticsTest` 覆盖 source 定位（引用）；本票新增错误均为普通 `IllegalStateException`/`IllegalArgumentException` 英文消息（无 validator 提示、无发明的 NEKO- 码——码表不在本分支，缺口记 REPORT §5-G1）】
- [x] 随实现交付 recipe/data/loot/tags/viewer 与 plugin `generateData`/脚本 `ServerEvents.generateData` 聚合的最小可运行示例和必要迁移材料；示例只使用已通过 gate 的能力。【evidence: `baseline/2026-09-22-recipe-data-surface/examples/generate-data.js`、`recipe-loot-tags.js`、`recipe-viewer.client.js`（viewer 示例明示 conditional 门控）；`MIGRATION.md` 5 节含能力矩阵】
- [x] 不把每个 recipe helper升格为新 Extension Point，不造第二 recipe registry或万能数据 catalog；旧旁路仅在替代 behavior、declaration、trace 通过且无调用者后移除，公开事件/helper不删除，清理不推迟 final release。【evidence: 无新 Point/registry/catalog；公开面零删除（`fireGenerateData` 保留）；被替换的「监听器内直写 active 根」内部旁路已删且 trace 断言不得重现（`new DataGeneratorJS`/`fireGenerateData` 在监听器内 0 次）】

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md): 本票需要事件名、payload、side、catalog 和 declaration 的单一规范链；事件基础未收口前会为 recipe 域另建第二 surface。

## Scope and coordination

**Rationale:** 这是既有事件域的窄垂直路径，按 recipe/data/loot/tags/viewer 的共同生命周期和资源结果验收，避免按 wrapper 类、mixin或测试层拆散，也避免与其他事件域合成巨票。

**Coordination:**

- RUNTIME_ROOT/RELOAD_COMMIT: recipe reload 事务、generation 可见性和失败保留由 runtime 语义承接；本票可在现有 runtime 上先交付域 fixture。
- BUILD_BASELINE: 现有 recipe/datagen catalog 与生成资源是 characterization 输入。
- REGISTRY_STARTUP: recipe schema/type 与启动 registry 元数据若有交集，注册事实源由 registry 组保留，本票只消费。
- client/JEI 集成拥有者: recipe viewer 的原生 JEI 接线如需客户端改动，本票提供 contract/source trace 并协调，不重写 client 域。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。

## Closure record（2026-09-22）

**Status:** in-review（实现/测试/证据已交付；AC2–AC10、AC12–AC15 满足并勾选；AC1/AC11 部分满足未勾选）

**接续说明**：本票由一次中断的同票运行恢复——其未提交产物（`DataGenerationBatch`、
`runGenerateData`、两个 datagen 测试类）经逐行复核后全部保留（无丢弃项），中断运行未开始的
平台接线/afterRecipes 时序/域 fixture/示例迁移由本轮补齐。明细见
[`baseline/2026-09-22-recipe-data-surface/REPORT.md`](../baseline/2026-09-22-recipe-data-surface/REPORT.md) §0。

**主要产出**

1. **generateData 聚合 + 原子发布**（AC4/AC5/AC6/AC7）：插件 hook 与脚本事件收口到
   `PluginGenerationHooks.runGenerateData` → `DataGenerationBatch`（候选→校验→原子发布→
   manifest 只增不删；用户手改文件永不覆盖；发布中段失败全量回滚）。两平台监听器（26.x +
   1.21.1）不再直写 active 根（trace 断言钉住）。
2. **afterRecipes 时序修复**（AC3）：两 mixin 孪生把 after 阶段移到整批提交之后（对齐
   `NekoJSPlugin#afterRecipes` 既有 javadoc「全部配方注册完成后触发」；wiki 3 行同步；
   MIGRATION §1 记录脚本可见行为变化：after 阶段修改不再生效）。
3. **1.21.1 tag 孪生缺陷修复**（AC8 fixture 捕获）：`remove()` 的 `(id, isTag)` 匹配未同步，
   源表既有条目在 1.21.1 永远删不掉；对齐 26.x 并补回 `RemovalKey`。
4. **域 fixture**：recipe schema/type/namespace 契约、JSON 生成/修改/值转换、id 族 filter、
   loot 冲突/stale、tags add/remove/replaceAll/reload、viewer JEI 门控 + fabric 显式缺席。
5. **示例与迁移**：`examples/`（generate-data / recipe-loot-tags / recipe-viewer.client）+
   `MIGRATION.md`（2 项行为变化 + 能力矩阵 + 不删除清单）。

**验证（真实命令与结果）**

| 命令 | 结果 |
|---|---|
| `gradlew :common:check --console=plain` | BUILD SUCCESSFUL in 1m32s（含隔离检查、PluginHookPairingTest） |
| `gradlew :26.1.2:test --console=plain` | 383 tests, 1 failed（`Ticket08LoaderDiscoveryTest` 初始化竞态，**基线既有**：stash 全部本票改动后基线复现 352/1 同用例），58 skipped |
| `gradlew :1.21.1:test --console=plain` | 280 tests, 1 failed（同一基线既有用例），14 skipped |
| `gradlew :26.1.2-fabric:test --console=plain` | 236 tests, 1 failed（同一基线既有用例），25 skipped |
| `gradlew guardLint --console=plain` | BUILD SUCCESSFUL in 2s |
| 突变红→绿（×4：回滚缺失/校验放水/after 前置/双管线） | 每项红→绿，`command-output/03-red-green-mutations.txt` |

**golden/declaration**：本票零改动（`ServerEvents.java`/`RecipeViewerEvents.java` diff 0 行；
无 golden 触碰）。AC1 的 declaration 派生缺口与 AC11 的 runtime smoke 缺口分别归 09/33 与 34。

**未覆盖项与 owner**：NEKO- 码表不在本分支（新错误用普通异常英文，不发明码号）→ 票 30；
Ticket08 基线 flake → 票 08 域 triage；真 RecipeManager/JEI/`runGameTestServer`/artifact smoke
→ 票 34；fabric 域移植（generateData/lootTables/tags/viewer unavailable，显式）→ LoaderBridge 票。

**证据目录**：`docs/architecture-refactor/baseline/2026-09-22-recipe-data-surface/`
（`REPORT.md`、`MIGRATION.md`、`examples/×3`、`command-output/01..06`，`.txt` 后缀）。
