# Spec: Fabric 平台 ServerEvents 资源/生命周期面收口（第 15 批）

> **Triage Label**: `ready-for-agent`（2026-09-05 实证后实施）
> **Scope**: lootTableLoad / datapackSync / tagsUpdated / Item·Block MODIFICATION 重放

## Problem Statement

`ServerEvents` 的三个资源生命周期总线（lootTableLoad / datapackSync / tagsUpdated）与
posted-object 模式的两个 modification 总线（`ItemEvents.modification` /
`BlockEvents.modification`）在 fabric 节点缺位——脚本无法改战利品表、无法在数据包
同步时机做事、无法做启动期物品/方块属性修改（这是 KubeJS 系整合包最高频的面之一）。

## 实证事实（javap / 补丁源码 / fabric-api jar）

| 总线 | NeoForge 现状 | fabric 方案（实证） |
|---|---|---|
| lootTableLoad | `LootTableLoadEvent` 直传（registries/name/table+setTable，可取消） | fabric-loot-api-v3 `LootTableEvents.MODIFY(ResourceKey, LootTable.Builder, source, registries)`（jar 实证）；builder 模式、不可取消——差异记录 |
| datapackSync | `OnDatapackSyncEvent` 直传（playerList/player 可 null=reload 全员） | `ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS(ServerPlayer, hasJoinedBefore)`（jar 实证；player=null 语义一致） |
| tagsUpdated | `TagsUpdatedEvent` 直传（registries/updateCause/shouldUpdateStaticData） | `CommonLifecycleEvents.TAGS_LOADED(RegistryAccess, updated)`（jar 实证；由 fabric 的 `ReloadableServerResourcesMixin` 服务端触发，时机与 NF 对齐；updateCause 无对应） |
| modification ×2 | posted-object：`ServerEventListener.onServerAboutToStart` fire ×2 + `/nekojs reload server` fire ×2 | 同位次：`SERVER_STARTING` 段（ABOUT_TO_START post 后）fire ×2 + `FabricNekoJSCommands.reloadType(SERVER)` 里 fire ×2 |

**关键结构事实**：`ItemModificationEventJS`/`ItemModificationJS`/`ItemModificationComponents`/
`BlockModificationJS` 的依赖全是 vanilla 类型（`DataComponentMap`、
`Holder.Reference#bindComponents`、`BlockBehaviour.Properties`——均为 26.x API），
现被整文件 `//? if neoforge` 守卫挡在 fabric 外；1.21.1 另有自己的
ItemModificationEventJS/ItemModificationJS 副本（stonecutter 同路径覆盖）。

## Solution

1. **去守卫复用**：四个 modification 类去掉 `//? if neoforge`（保留/补 `//? if >=26`
   ——26.x API，1.21.1 走自己的副本）。`ItemModificationEventJS` post 目标是
   `ItemEvents.MODIFICATION`（fabric 解析到孪生，零改动）；`BlockModificationEventJS`
   的 post 目标 `NeoForgeBlockEvents.MODIFICATION` 是 NF 类——把总线声明**上移共享层**
   `BlockEvents.java`（`//? if >=26` 块内），NF 侧 `NeoForgeBlockEvents` 删本地声明。
2. **fabric 纯回调接线**（零 mixin）：`FabricServerEventBindings` 增
   DATAPACK_SYNC / TAGS_UPDATED / LOOT_TABLE_LOAD 三总线 + 注册三回调；
   SERVER_STARTING 段与 reload 命令补 fire ×2。
3. **中立载荷 ×3**（共享树 wrapper，vanilla 类型，NF 侧继续直传原生事件、不改）：
   `LootTableLoadEventJS`（id/table builder/registries）、`DatapackSyncEventJS`
   （player 可 null/playerList）、`TagUpdatedEventJS`（registries/shouldUpdateStaticData）。

## Testing Decisions

- 冒烟：`runServer` 启动零注入失败（本批零 mixin，风险最低）；modification fire 在
  启动期真实执行（改日志可观测：`NekoJS item modifications applied`）。
- 五节点编译 + guardLint（守卫块变化是本批主要风险点：1.21.1 编译共享 BlockEvents
  的新增块必须被守卫掉）。
- 语义差异全部入载荷 javadoc + 台账：loot builder vs 整表、TAGS_LOADED 无 updateCause。

## Out of Scope

- lootTables/tags（JSON 管理总线，另有挂点）；generateData（datagen 模型，B 类）；
  村民交易重放；NF 侧任何行为变更（共享 MODIFICATION 上移后脚本面不变）。
