# NekoJS 脚本 API 表面盘点（api-surface-inventory）

> 阶段 A 交付物（1.0.0 冻结准备，2026-08-15）。只读盘点，不含改动方案；差距分析与整理方案见 `docs/api-rework-plan.md`。
> 数据来源：四平台 + common 源码级注册代码（`NekoJSCorePlugin.registerBinding/registerEvents/registerAdapters`、`CoreManagedApiBootstrap`、
> `common-api/spec`），并与 `NekoScriptCatalog` 机制、`common/src/test/resources/nekojs/golden/api-manifest-core.json` 交叉验证。
> 注意：`platforms/neoforge-26.1/run/.neko_probe/` 下的生成声明是**过期快照**（缺多个新 bus），不得作为事实源；本表以注册代码为准。

## 0. 版本事实与冻结基线现状

| 项 | 值 | 来源 |
|---|---|---|
| `api.version` | `0.12.0` | `common/src/main/templates/nekojs/api-runtime.properties`（唯一事实源） |
| `nekojs.version` | `1.1.0-preview2`（= mod_version，模板注入） | 同上 |
| `spi.version` / `runtime.contract.version` | `0.0.0`（未门控） | 同上 |
| `catalog.schema.version` | `1` | 同上 |
| 冻结基线 | `ApiManifestGoldenTest` + golden JSON（901 行，147 符号） | 仅覆盖 **core 契约 facade**（loader=`test`），不含平台绑定/事件/适配器 |

平台拓扑：`neoforge-26.1` 与 `neoforge-26.2` 的绑定/事件/适配器**完全一致**（各自仅 4 个网络/客户端差异文件，均不在脚本 API 面上），
统一记作 **NF26**；`neoforge-1.21.1` 记作 **NF121**；`cleanroom-1.12.2` 记作 **CR**。共享源集：`neoforge-shared`（NF26+NF121）、
`neoforge-26-shared`（仅 NF26）。

**覆盖标注**：`PORTABLE` = 四平台都暴露同一 JS 名（底层类可不同，备注说明）；否则列出平台（NF26/NF121/CR 或组合）。

## 1. 契约 Facade（core managed，已进 golden 的 147 符号）

由 `CoreManagedApiBootstrap` 反射构建 `nekojs-core` portable 契约，7 个 global + 140 个 member，全部 tier=`GLOBAL`、全部 ScriptType。
Java facade 接口在 `common-api/.../api/facade/`，默认实现在 `common/.../core/api/facade/`。

### 1.1 Global（7）

| JS 名 | facade 接口 | 默认实现 | member 数 |
|---|---|---|---|
| `ID` | `IdFacade` | `DefaultIdFacade` | 4（`of`×2、`namespace`、`path`、`asString`） |
| `Platform` | `PlatformFacade` | `DefaultPlatformFacade` | 9 |
| `Text` | `TextFacade` | `DefaultTextFacade` | 36 |
| `JsonIO` | `JsonFacade` | `DefaultJsonFacade` | 5 |
| `NBT` | `NbtFacade` | `DefaultNbtFacade` | 20 |
| `Registry` | `RegistryFacade` | `DefaultRegistryFacade` | 1 |
| `Performance` | `PerformanceFacade` | `DefaultPerformanceFacade` | 4 |

### 1.2 Member 域（按 owner，140）

| owner（契约名） | 符号数 | 成员（`@Remap` 后的 JS 名） |
|---|---|---|
| `Text` | 36 | append, aqua, black, blue, bold, click, color, darkAqua, darkBlue, darkGray, darkGreen, darkPurple, darkRed, empty, font, gold, gray, green, hover, insertion, italic, join, keybind, lightPurple, obfuscated, of, ofValues, red, score, selector, strikethrough, translatable, translateWithFallback, underlined, white, yellow |
| `TextValue` | 29 | append, aqua, black, blue, bold, click, color, darkAqua, darkBlue, darkGray, darkGreen, darkPurple, darkRed, font, gold, gray, green, hover, insertion, isEmpty, italic, join, lightPurple, obfuscated, red, strikethrough, underlined, white, yellow |
| `NBT` | 20 | byte, byteArray, compound, double, entries, float, fromObject, int, intArray, kind, long, of, parse, read, scalar, short, toObject, toSnbt, values, write |
| `Platform` | 9 | capabilities, getInfo, getList, getLoaderId, getLoaderVersion, getMcVersion, isClient, isDevelopment, isLoaded |
| `PerfTimer` | 7 | elapsedMillis, end, ended, label, mark, marks, report |
| `NbtValue` | 7 | entries, kind, of, scalar, toObject, toSnbt, values |
| `RegistryView` | 6 | all, dataMapIds, dataMapValue, exists, has, tag |
| `JsonIO` | 5 | parse, read, toString, toPrettyString, write |
| `Performance` | 4 | bench, now, start, time |
| `ID` | 4 | of×2, namespace, path, asString |
| `NekoId` | 3 | asString, namespace, path |
| `ModInfo` | 3 | id, name, version |
| `ScriptEventRegistrationEvent` | 2 | register（4 个重载）, targetType |
| `NbtEntry` | 2 | key, value |
| `JsonValue` | 2 | toPrettyString, toString |
| `Registry` | 1 | get |

数据类型 receiver：`@ContractReceiver` 标注 `NekoId`/`JsonValue`/`NbtValue`/`NbtEntry`/`TextValue`/`RegistryView`，
`@ContractReceiver("ModInfo")`/`("PerfTimer")` 做 override。golden **不含 tier 字段**（tier 只在 `ApiContribution` 上）。

## 2. 全局绑定（平台 `registerBinding` 注册）

分域列出。kind 说明：`proxy`=DelegatingBinding 代理、`factory`=工厂、`helper`=静态 helper 实例、`class`=vanilla/loader 类静态访问、
`core`=CoreManagedApi 注入（全平台恒同）。

### 2.1 基础工具域

| JS 名 | kind | 底层（NF26 / NF121 / CR） | 覆盖 | 备注 |
|---|---|---|---|---|
| `ID`/`Platform`/`Text`/`JsonIO`/`NBT`/`Registry`/`Performance` | core | common facade | PORTABLE | §1 |
| `Color` | helper | `ColorJS`（common） | PORTABLE | |
| `UUID` | helper | `UUIDJS`（common） | PORTABLE | |
| `StringUtils` | helper | `StringUtilsJS`（common） | PORTABLE | |
| `Time` | helper | `TimeJS`（common：`SECOND/MINUTE/HOUR` 字段、`seconds/minutes/hours/parseTime/parseMs`） | PORTABLE | **未进 core 契约**（§7 冻结候选） |
| `Utils` | helper | `UtilsJS`（common） | PORTABLE | 同上 |
| `global` | helper | `NekoGlobal.shared()` 共享 Map | NF26+NF121 | **CR 缺失**（API 缺口） |
| `Test` | helper | `TestJS`（common，TEST-only） | PORTABLE | |
| `RecipeSchema` | helper | `RecipeSchemaBinding`（common） | PORTABLE | 配方域，见 §2.5 |
| `Identifier` / `ResourceLocation` | class | NF26=`net.minecraft.resources.Identifier`；NF121/CR=`ResourceLocation` | 全平台（**JS 名分裂**） | 同一概念三个名字（26.x 改名） |
| `MutableComponent` | class | `net.minecraft.network.chat.MutableComponent` | NF26+NF121 | |
| `TextComponent` | class | `net.minecraft.util.text.ITextComponent`（接口） | CR | 1.12.2 对应物；与 NF 的 `Component` 非同名 |
| `Component` | class | `net.minecraft.network.chat.Component` | NF26+NF121 | |
| `DyeColor` | class | NF: `DyeColor` / CR: `EnumDyeColor` | PORTABLE（类名不同） | |

### 2.2 Item / Block 域

| JS 名 | kind | 底层 | 覆盖 | 备注 |
|---|---|---|---|---|
| `Item` | proxy | `DelegatingBinding(ItemJS helper, Item.class)`：`of/empty/id/idOf`→helper，其余→类静态 | PORTABLE | `ItemJS` 三平台各一份（CR 本地、NF26=26-shared、NF121 本地） |
| `Block` | proxy | `DelegatingBinding(BlockJS helper, Block.class)`：`id/idOf`→helper | PORTABLE | 同上 |
| `ItemStack` | class | `ItemStack` | PORTABLE（类名不同） | |
| `Items` / `Blocks` | class | `Items`/`Blocks`（CR 为 `net.minecraft.init.*`） | PORTABLE | |
| `SoundEvents` | class | `SoundEvents` | PORTABLE | |
| `EntityType` | class | `EntityType` | NF26+NF121 | CR 用 `EntityEntry`（见 2.4） |
| `EntityEntry` | class | `net.minecraftforge.fml.common.registry.EntityEntry` | CR | CR 的 EntityType 对应物 |
| `MobEffects` | class | `MobEffects` | PORTABLE（类名不同） | |
| `MobEffectInstance` | class | `MobEffectInstance` | NF26+NF121 | CR 用 `PotionEffect` |
| `PotionEffect` | class | `net.minecraft.potion.PotionEffect` | CR | |
| `Potion` | class | CR=`net.minecraft.potion.Potion`（效果）；NF 无此绑定 | CR | NF 的药水类型无全局绑定 |
| `SoundEvent` | class | `net.minecraft.util.SoundEvent` | CR | |
| `DamageTypes` | class | `DamageTypes` | NF26+NF121 | |
| `ParticleTypes` | class | CR=`EnumParticleTypes` / NF=`ParticleTypes` | PORTABLE（类名不同） | |

### 2.3 Ingredient / Fluid 域

| JS 名 | kind | 底层 | 覆盖 | 备注 |
|---|---|---|---|---|
| `Ingredient` | factory | `IngredientFactory`（返回 wrapper `IngredientJS`） | PORTABLE | 类名与绑定名不一致（命名规则见 rework plan）；`of(Value...)` **Value 泄漏** |
| `Fluid` | factory | `FluidJS`（NF26/NF121=neoforge-shared 版、CR 本地版） | PORTABLE | `of(Value)` 族 **Value 泄漏**；CR 版 `empty()` 返回 `null`、解析失败可返回 `null` |
| `FluidIngredient` | factory | `FluidIngredientJS` | PORTABLE | CR 无 FluidIngredient 类 → 返回 `List<FluidStack>`（刻意降级，javadoc 说明）；`of(Value...)` **Value 泄漏** |
| `FluidStack` | class | NF=`FluidStack`（NeoForge）/ CR=`FluidStack`（Forge） | PORTABLE | |
| `Fluids` | class | NF=`net.minecraft...Fluids` / **CR=`FluidRegistry`** | 全平台（**语义分裂**） | 同名不同物：CR 绑的是注册表工具类 |
| `FluidAmounts` | class | `FluidAmounts`（common 常量类） | PORTABLE | |
| `Capabilities` | helper | `CapabilitiesJS`（NF26 26-shared / NF121 本地；返回类型不同） | NF26+NF121 | platform 层能力 |
| `TriState` | class | NF26=`net.minecraft.util.TriState` / NF121=`neoforge.common.util.TriState` | NF26+NF121 | |

### 2.4 空间 / 通用域

| JS 名 | kind | 底层 | 覆盖 | 备注 |
|---|---|---|---|---|
| `BlockPos` | class | `BlockPos` | PORTABLE（类名不同） | |
| `Direction` | class | NF=`Direction` / CR=`EnumFacing` | PORTABLE（类名不同） | |
| `Vec3` | class | `Vec3`/`Vec3d` | PORTABLE（类名不同） | |
| `AABB` | class | `AABB`/`AxisAlignedBB` | PORTABLE（类名不同） | |
| `CompoundTag` | class | `net.minecraft.nbt.CompoundTag` | NF26+NF121 | CR 对应 `NBTTagCompound`（名不同） |
| `NBTTagCompound` | class | `net.minecraft.nbt.NBTTagCompound` | CR | |
| `NativeEvents` | helper | `NativeEventsJS`（STARTUP-only） | 全平台（**语义分裂**） | NF=可用原生事件桥；CR=`onEvent` 显式抛 `UnsupportedOperationException`（诚实降级） |

### 2.5 配方 / Network / 客户端域

| JS 名 | kind | 底层 | 覆盖 | 备注 |
|---|---|---|---|---|
| `Network` | wrapper | `NetworkJS`（静态访问；三平台各一份，行为对齐） | PORTABLE | §7 冻结候选 |
| `Minecraft` | class | `Minecraft`（CLIENT-only） | PORTABLE | |
| `Screen`/`Window`/`KeyMapping`/`InputConstants` | class | mojang 类（CLIENT-only） | NF26+NF121 | |
| `RecipeSchema` | helper | 见 §2.1 | PORTABLE | |

CR 独有未注册残留：`bindings/static_access/TextJS`（cleanroom）**未注册任何绑定、零引用**（死代码，见 rework plan L-2）。

## 3. 事件组（14 组 + 1 未注册组）

组注册位置：NF26=neoforge-26-shared `NekoJSCorePlugin.registerEvents`；NF121=本地；CR=本地。
`ScriptEvents`/`ProbeEvents` 定义在 common（全平台同源）。`Cancellable` 判定：NF 用 `ICancellableEvent` 谓词（精确），
CR 用 Forge `eventhandler.Event` 谓词（**更宽**——凡 Forge Event 均可 `setCanceled`）。dispatch=按 ID 分发 bus。

覆盖列：全=四平台；其余标平台。同一 bus 各平台事件类/键不同时在备注说明。

### 3.1 `PlayerEvents`（SERVER）

| bus | dispatch key | cancellable | 覆盖 | 备注 |
|---|---|---|---|---|
| `loggedIn` / `loggedOut` | — | NF 否 / CR 是 | 全 | 谓词差异（下同，CR 全组可取消面更宽） |
| `chat` | — | 是 | 全 | |
| `tickPre` / `tickPost` | — | CR 是 / NF 否 | 全 | CR 用 phase filter 拆分单一事件类 |
| `tick` | — | 是 | **CR only** | CR 保留的无 filter 兼容别名 |
| `cloned` / `respawned` / `changedDimension` | — | CR 是 / NF 否 | 全 | |
| `advancement` | — | CR 是 / NF 否 | 全 | 事件类不同（CR 单类） |
| `containerOpened` / `containerClosed` / `inventoryOpened` / `inventoryClosed` | — | CR 是 / NF 否 | 全 | `inventory*` 为 `container*` 别名 bus |
| `entityInteract` | — | 是 | 全 | |
| `crafted` / `smelted` / `destroyed` | `Item` | CR 是 / NF 否 | 全 | |
| `inventoryChanged` | `Item` | 否 | 全 | 包装事件 `InventoryChangedEventJS` |

### 3.2 `ServerEvents`（SERVER）

| bus | dispatch key | cancellable | 覆盖 | 备注 |
|---|---|---|---|---|
| `tickPre` / `tickPost` | — | CR 是 / NF 否 | 全 | CR phase filter |
| `recipes` / `afterRecipes` | — | 否 | 全 | 包装事件 `RecipeEventJS`；触发时机 CR=postInit（注册表早填充） |
| `generateData` | `String` stage | 否 | 全 | `DataGeneratorJS` |
| `aboutToStart`/`starting`/`started`/`stopping`/`stopped` | — | 否 | 全 | CR 为 FML 生命周期事件手动转发 |
| `datapackSync` | — | 否 | **NF only** | |
| `tagsUpdated` | — | 否 | **NF only** | |
| `lootTableLoad` | — | 是 | 全 | |
| `lootTables` | — | 否 | **NF only** | 包装事件 `LootTableEventJS` |
| `tags` | NF=registry id / **CR=固定 `"ore_dict"`** | 否 | 全 | dispatch 键类型分裂；CR 仅物品/方块 |
| `worldLoad` / `worldUnload` | — | 是 | **CR only** | 与 CR `LevelEvents.loaded/unloaded` **语义重复**（双入口） |

### 3.3 `BlockEvents`（SERVER；dispatch=`Block`）

| bus | cancellable | 覆盖 | 备注 |
|---|---|---|---|
| `broken` | 是 | 全 | 事件类不同（NF26 `BreakBlockEvent` / NF121 `BlockEvent.BreakEvent` / CR `BreakEvent`） |
| `placed` | 是 | 全 | NF=`EntityPlaceEvent`；**CR=deprecated `PlaceEvent`（刻意保留经典绑定，注释说明）**——同名不同事件类 |
| `entityPlaced` / `entityMultiPlaced` / `neighborNotify` / `fluidPlaced` / `farmlandTrample` / `portalSpawn` | 是 | 全 | |
| `rightClicked` / `leftClicked` | 是 | 全 | |
| `toolModification` | 是 | **NF only** | |
| `randomTick` / `blockEntityTick` | 否 | **NF only** | mixin 注入（dispatch：`randomTick`=Block、`blockEntityTick`=BlockEntityType） |
| `harvestDrops` | 是 | **CR only** | 1.12.2 独有 |

### 3.4 `ItemEvents`

| bus | side | dispatch | cancellable | 覆盖 | 备注 |
|---|---|---|---|---|---|
| `rightClicked` | SERVER | `Item` | 是 | 全 | |
| `tooltip` | **CLIENT(NF) / SERVER(CR)** | `Item` | 否 | 全 | **tier 分歧**，未进契约（ROADMAP 已记录） |
| `canPickUp` | SERVER | `Item` | 否 | 全 | |
| `pickedUpPre` | SERVER | `Item` | 否 | **NF only** | CR 无独立 Post 事件 |
| `pickedUp` | SERVER | `Item` | 是(CR) | 全 | CR 与 `canPickUp` 绑同一 Forge 类（别名）；NF=`...PickupEvent.Post` |
| `dropped` | SERVER | `Item` | 是 | 全 | |
| `entityInteracted` | SERVER | `Item` | 是 | 全 | |
| `foodEaten` | SERVER | `Item` | 否 | 全 | |
| `expire` | SERVER | `Item` | 是 | **CR only** | |

### 3.5 `EntityEvents`（SERVER）

| bus | dispatch key | cancellable | 覆盖 | 备注 |
|---|---|---|---|---|
| `damagePre` / `damagePost` | NF=`EntityType` / **CR=`Entity` 实例** | CR 是 / damagePre NF 否 | 全 | dispatch 键类型分裂；CR 事件类不同（`LivingHurtEvent`/`LivingDamageEvent`） |
| `death` / `drops` / `finalizeSpawn` | 同上 | 是 | 全 | CR `finalizeSpawn`=`CheckSpawn` |
| `tickPre` / `tickPost` | `EntityType` | 否 | **NF only** | |
| `joinLevel` | NF=`EntityType` / CR=`Entity` | 是 | 全 | |
| `leaveLevel` | `EntityType` | 否 | **NF only** | |
| `useItemStarted`/`useItemStopped`/`useItemFinished`/`useItemTick` | `Item` | 是（finished 否） | 全 | |

### 3.6 其余组

| 组 | side | bus（dispatch / cancellable） | 覆盖 | 备注 |
|---|---|---|---|---|
| `GoalEvents` | STARTUP | `register`（— / 否） | 全 | 包装事件 `GoalRegisterEventJS` |
| `CommandEvents` | SERVER | `register`（— / 否）；`command`（— / 是） | 全 | CR `register`=`FMLServerStartingEvent` 手动转发 |
| `RegistryEvents` | STARTUP | `item/block/entityType/fluid/creativeModeTab/soundEvent/mobEffect/potion/villagerType/enchantment`（全 PLAIN） | 全 | NF 多 `particleType`/`paintingVariant`（CR 无注册表，刻意不支持）；CR 全为手动转发包装事件 |
| `CapabilityEvents` | STARTUP | `register` | **NF only** | platform 层 |
| `LevelEvents` | SERVER | `loaded/unloaded/saved/tickPre/tickPost/explosionStart/explosionDetonate` | 全 | NF 另有别名 bus `tick`、`beforeExplosion`、`afterExplosion`（CR 无）；CR 全可取消（谓词宽） |
| `NetworkEvents` | SERVER+CLIENT | `server`/`client`（dispatch `String` channel / 否） | 全 | `NetworkDataEventJS` getter 跨平台一致 |
| `ScriptEvents` | STARTUP | `server`/`client`（— / 否） | 全 | common 同源；payload=`ScriptEventRegistrationEvent`（契约 member×2）；`register(Value config)` **Value 泄漏** |
| `ProbeEvents` | SERVER | `modifyType/assignType/addGlobal/snippets` | 全 | common 同源 |
| `ClientEvents` | CLIENT | NF：`tickPre/tickPost/tick/loggedIn/loggedOut/cloned/commandRegistry`（game bus）+ `registerKeyMappings/registerMenuScreens/registerRenderers(+2 别名)/registerParticleProviders`（mod bus）+ `generateData/lang`（dispatch String）+ `hud`/`screenRender`（bindTransformed）；CR：仅 `tick/chatReceived/generateAssets/lang` | **组全平台，bus 大量 NF only** | CR 缺 14 个 bus；`chatReceived` 为 CR 独有 |
| `RecipeViewerEvents` | CLIENT | `addEntries/removeEntries`(dispatch `String`)、`removeRecipes/removeCategories/addInformation` | **定义未注册**（NF） | 仅 JEI 插件 post，核心插件从不 `EventGroupRegistry.register`；CR 无对应 |

## 4. 类型适配器（JSTypeAdapter 注册）

| 适配器 | 目标类型 | 覆盖 | 备注 |
|---|---|---|---|
| `ItemStackAdapter` / `IngredientAdapter` / `SizedIngredientAdapter` / `FluidStackAdapter` | `ItemStack` / `Ingredient` / `SizedIngredient`(NF) / `FluidStack` | PORTABLE | NF26 与 NF121 各自副本；SizedIngredient 目标 CR=`SizedIngredientJS`（wrapper） |
| `ResourceLocationAdapter` / `IdentifierAdapter` | `ResourceLocation` / `Identifier` | NF121+CR / NF26 | 同概念分裂 |
| `BlockAdapter` / `BlockStateAdapter` / `BlockPosAdapter` / `Vec3Adapter` | `Block` / `BlockState` / `BlockPos` / `Vec3` | `BlockStateAdapter` NF only，余 PORTABLE | |
| `ItemAdapter` / `ComponentAdapter` / `CompoundTagAdapter` / `EntityTypeAdapter` / `PotionAdapter` / `SoundEventAdapter` / `MobEffectAdapter` | 对应平台类 | PORTABLE（目标类名不同） | CR 无 `MobEffectAdapter` |
| `ParticleTypeAdapter` / `BlockEntityTypeAdapter` / `CreativeModeTabAdapter` / `TagKeyAdapter` | 对应类 | NF only | CR `TagKeyAdapter` 为抛异常 stub，**未注册** |
| `FluidIngredientAdapter` / `SizedFluidIngredientAdapter` | `FluidIngredient` / `SizedFluidIngredient` | NF only | CR 刻意省略 `FluidIngredientAdapter`（曾劫持 `List` 映射，注释说明）；CR `SizedFluidIngredientAdapter` 目标=`Object[]` 透传 |
| `RecipeFilterAdapter` / `RecipeJsonValueAdapter` / `JsonObjectAdapter`(common) | `RecipeFilter` / `RecipeJsonValue` / `JsonObject` | PORTABLE | |
| `CodecAdapter<Fireworks>`（DSL 注册） | `Fireworks` | NF only | precedence 最低的 codec 兜底 |
| `TileEntityAdapter` | `Class<? extends TileEntity>` | **CR only** | 反射 `TileEntity.REGISTRY` |

## 5. Spec 接口（`common-api/.../api/spec`，编译期 SpecCoverageProcessor 强制覆盖）

| 接口 | Scope | `neko$` 方法数 | 实现链（Extension→Mixin） |
|---|---|---|---|
| `BlockSpec` | ALL | 1 | 三平台 `BlockExtension`；`MixinBlock`（CR、NF121） |
| `ItemSpec` | ALL | 1 | CR/NF（neoforge-shared `ItemExtension`）；`MixinItem` |
| `ItemStackSpec` | ALL | 5 | 三平台；`MixinItemStack`（CR、NF121） |
| `EntitySpec` | ALL | 4 | 三平台；`MixinEntity` |
| `LivingEntitySpec` | ALL | 8 | 三平台；`MixinLivingEntity` |
| `PlayerSpec` | ALL | 9 | 三平台；`MixinPlayer` |
| `BlockStateSpec` | NF_ONLY | 3 | NF121/NF26 |
| `EventSpec` | NF_ONLY | 2 | NF121/NF26；**CR 不可实现**（Event 基类先于 mixin 配置加载，javadoc 说明） |
| `LevelSpec` | NF_ONLY | 10 | NF121/26.1/26.2 |
| `MutableComponentSpec` | NF_ONLY | 30 | NF121/NF26 |
| `ServerSpec` | NF_ONLY | 1 | neoforge-shared |

注：NF26 mixin json 仅注册 4 个 mixin（Level/MutableComponent/Player/MinecraftServer）；Block/BlockState/Entity/Event/ItemStack/LivingEntity
的 Extension 接口在 26-shared 存在但 **mixin 未注册**（26.x 平台 inject 面实际比接口面窄——差距项）。

## 6. 事件取消面现状（三套习惯并存）

1. **返回 `true` 即取消**（全平台主通道）：`EventBusJS` 可取消 listener 路径把 JS 返回值 `true` 翻译为原生 `setCanceled(true)`
   （CR/NF 两个 `EventBusForgeBridge` + NF `ScriptEventsJS`）。
2. **`event.cancel()` / `event.isCancelled()`**（仅 NF）：`EventSpec.neko$cancel`（`@RemapByPrefix` 去前缀）经 `MixinEvent`
   注入 NeoForge `Event` 基类；CR 无对应（基类不可 mixin）。
3. **原生 `setCanceled(true)` / `isCanceled()`**：NF/CR 原生事件对象自身方法始终可见。

拼写分裂：注入的 `isCancelled`（双 l）vs 原生 `isCanceled`（单 l）在 NF 脚本侧同时可见。设计基线 §7.3 要求 stable 统一为
显式 `event.cancel()` 并弃用「返回 true」隐式约定——**尚未落地**（待用户决策，见 rework plan H-3）。

## 7. tier 现状

- 生产代码唯一 `ApiContribution` 生产者是 `CoreManagedApiBootstrap`，**所有 core 符号（含 member）tier 一律 `GLOBAL`**；
  `MEMBER/MODULE_MEMBER/FEATURE/PLATFORM/VERSION/UNSAFE_NATIVE/LEGACY_PREVIEW` 目前只在模型/测试出现，运行时无人使用。
- `LegacySurfaceAdapter` 把现有 catalog 条目（绑定/事件/适配器）只读转换为 `ApiTier.LEGACY_PREVIEW` 符号（明确不视为 stable）。
- feature/platform/version 三层**尚无任何运行时模块注册**（manifest `modules: []`）；`@nekojs/feature/*` 等模块 ID 只存在于设计文档。
