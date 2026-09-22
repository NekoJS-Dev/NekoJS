# Ticket 23 迁移材料（recipe / data generation / loot / tags / recipe viewer）

适用范围：NeoForge 26.x / 1.21.1（fabric 节点的能力差异见 §4）。本票不删除任何公开事件或
helper（AC15：旧入口保留），但两处**行为语义**按票据验收收紧，已有脚本需要按下表核对。

## 1. `ServerEvents.afterRecipes` 触发时机（行为变化）

| 项 | 旧（26.x/1.21.1 实现现状） | 新（本票，AC3） |
|---|---|---|
| 触发点 | `recipes` 事件后、**最终 codec 解析前** | 最终 JSON **整批解析并提交**到 `RecipeManager` 之后 |
| 在该事件里改 JSON | 会进入本次提交 | **不再生效**（提交已完成） |
| 插件 `NekoJSPlugin.afterRecipes` | 同上先于提交 | 同上，改为提交后 |

- **迁移**：原来在 `afterRecipes` 里做修改（`setJson`/`remove`/builder）的脚本，把修改移到
  `ServerEvents.recipes`；`afterRecipes` 保留给提交后的查询/后处理。
- 依据：`NekoJSPlugin#afterRecipes` 的既有 javadoc（「全部配方注册完成后触发」）本来就是提交
  后语义，本票把实现对齐到该文档；wiki《事件参考》的旧行描述已同步更新。
- 固定该顺序的 fixture：`Ticket23RecipeDataPhaseTraceTest.afterRecipesFiresOnlyAfterTheParsedBatchIsCommitted_26x/_1211`。

## 2. `generateData`（脚本）与插件 `generateData` hook 的落盘语义（行为变化）

| 项 | 旧 | 新（本票，AC4/AC6/AC7） |
|---|---|---|
| 写入路径 | 插件 hook 与脚本事件共用 generator，但**直写** `<gameDir>/nekojs/data`（active 根） | 同一 generator，先写**候选区**（`<gameDir>/nekojs/.nekojs-datagen/<stage>/candidate`），校验（路径包含性/JSON 结构/可回读/重复 key 覆盖策略）后**原子发布** |
| 用户手改过的生成文件 | 再生成时被覆盖 | **永不覆盖**：跳过并在结果里报告 |
| 从未生成过的既有文件 | — | 同样视作用户文件，永不覆盖 |
| 批次失败/脚本阶段抛错 | 已写文件留在 active 根（半写） | **不发布任何文件**，旧 active 保留；候选区保留至同阶段下一批 |
| 早期批次产物 | — | 后续批次不再生成时**保留**（脚本生成数据不视为无条件可再生，数据保护清单行 16） |
| 批内同 key 重复写 | 最后写入覆盖 | 不变（最后贡献者胜），但跨贡献者覆盖进入 `PublishResult.overwriteTrace` |

- **迁移**：脚本 API 无变化（`ServerEvents.generateData('after_mods', event => ...)` 写法不变）。
  依赖「删掉声明 = 下次 reload 数据消失」的脚本需要改为显式覆盖内容（本票从不删除用户可见数据）。
- 新增磁盘状态：`<gameDir>/nekojs/.nekojs-datagen/<stage>/{candidate,backup,manifest.json}`——
  纯临时/簿记状态，可再生，可整目录安全删除（发布成功后 candidate 已清空）。
- 固定行为的 fixture：`DataGenerationBatchTest`（7 用例）、`DataGenerationAggregationTest`
  （4 用例，含红→绿证据见 REPORT §4）。

## 3. 插件作者（Plugin API）

- `NekoJSPlugin.generateData(DataGeneratorJS)` 签名不变；现在于共享批内触发（先于脚本阶段），
  单插件异常被隔离（日志点名插件类），不影响其余贡献。
- `RecipeLifecyclePoint` / `beforeRecipeLoading` / `afterRecipes` 语义不变；`afterRecipes` 见 §1。

## 4. 节点能力矩阵（显式，非静默 no-op）

| 能力 | NeoForge 26.x | NeoForge 1.21.1 | fabric 26.x |
|---|---|---|---|
| `ServerEvents.recipes` / `afterRecipes` | supported | supported | supported（AFTER_RECIPES bus 已声明，mixin 共享） |
| `ServerEvents.generateData` | supported（候选→校验→原子发布） | supported（同左，孪生接线） | **unavailable**：bus 未声明，脚本引用为未知成员（显式失败） |
| `ServerEvents.lootTables` / `ServerEvents.tags` | supported | supported | **unavailable**（`TagEventJS` 文件头既有 TODO(fabric)） |
| Recipe viewer（JEI） | conditional：客户端 + JEI 在场才注册组 | conditional（同左，共享接线） | **unavailable**（JEI 接线整文件 neoforge 守卫） |

- 固定 fabric 缺席不是静默 no-op 的 fixture：`Ticket23RecipeDataPhaseTraceTest.fabricNodesHaveNoGenerateDataBusAndNoViewerWiring`。
- viewer 条件注册（`@RegisterNekoJSPlugin(clientOnly = true, requiredMods = "jei")`）：由
  `viewerSurfaceIsRegisteredOnlyThroughTheJeiGatedPlugin` 钉住。

## 5. 显式不做 / 未删除项

- 不删除任何公开事件、helper 或 `MinecraftRecipeHandler` 一族的注册面（AC15：替代行为已备，
  公开入口保留；进一步收缩在维护者 sign-off 后另行处理）。
- 不新增 Assets 事件（票 29 域）；`ClientEvents.generateAssets` / `generateLang` /
  `generatedLangs` 未被本票触碰。
- golden / declaration 只读：本票未改任何 golden；catalog 派生 TS/Python 声明的缺口归
  Managed Surface/Probe owner（票 09/33），见 REPORT §6。
