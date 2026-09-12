# 23: Recipe/数据生成/loot/tags/recipe viewer 既有事件域路径

**What to build:** 整合包作者在既有 recipe/data 事件中编写 recipe schema、JSON builder、数据生成（不含 Assets）、loot 和 tags，并配置 recipe viewer 信息；plugin `generateData` Hook 与脚本 `ServerEvents.generateData` 进入同一条数据生成路径；调用经过 managed surface、平台/version Adapter、资源生成或回读，得到可验证产物、afterRecipes 时序、reload 清理、TS/Python declaration 和节点 capability。所有事件均复用原 bus，不新增平行事件。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)

**Status:** ready-for-agent

**Assignee:** unassigned

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

- [ ] 既有 recipe 事件名和 bus 没有第二份声明；catalog/golden 能从真实 runtime member 推导 TS/Python 声明。
- [ ] representative recipe schema/type/namespace 输入生成或修改预期 JSON，builder 值转换、filter 和 generated id 行为有测试。
- [ ] afterRecipes 只在 recipe 数据完整提交后的既有生命周期触发；失败、取消或 reload 中断不产生半更新。
- [ ] plugin `generateData` 与脚本 `ServerEvents.generateData` 使用同一候选收集、生成、发布和回读路径；二者与 recipe/loot/tags 的相对阶段顺序由真实 fixture 固定，不形成第二数据生成管线。
- [ ] plugin 与脚本贡献的错误隔离可观察：单个贡献的插件 id、脚本 source、字段和阶段可定位；无法证明安全隔离时整批失败并保留旧数据，不产生半写或混合来源产物。
- [ ] 非 Assets 数据生成先写候选区域，验证路径包含性、重复 key/覆盖策略、JSON 结构和可回读后再原子发布；失败保留旧 active，不覆盖用户编辑文件，cache/生成物可再生性遵守数据保护清单。
- [ ] 非 Assets 数据生成的路径、JSON、覆盖策略、失败保留和回读结果可验证，且不新增 Assets 事件。
- [ ] loot 与 tags 的修改、冲突、删除/清理和 reload 后 stale 状态有外部行为 fixture。
- [ ] recipe viewer 信息仅在相应 JEI/平台条件成立时声明 supported，否则显式 partial/unavailable，不静默 no-op。
- [ ] MC 类型、mixin和平台 registry/loader 操作留在 共享 MC-facing 或节点 Adapter，common 不复制平台业务逻辑。
- [ ] NeoForge/Fabric 的 artifact、source trace 和 runtime smoke 按节点等级记录，不能用局部单测冒充。
- [ ] 脚本错误能定位到事件域、owner、源文件和生成/修改阶段，普通错误不嵌 validator 提示。
- [ ] 随实现交付 recipe/data/loot/tags/viewer 与 plugin `generateData`/脚本 `ServerEvents.generateData` 聚合的最小可运行示例和必要迁移材料；示例只使用已通过 gate 的能力。
- [ ] 不把每个 recipe helper升格为新 Extension Point，不造第二 recipe registry或万能数据 catalog；旧旁路仅在替代 behavior、declaration、trace 通过且无调用者后移除，公开事件/helper不删除，清理不推迟 final release。

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
