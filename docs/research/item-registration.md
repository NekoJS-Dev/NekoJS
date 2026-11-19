# NekoJS 物品与近邻注册调查

维护笔记，依据本次工作树（HEAD `2901bb84`，含未提交改动）。这里区分三件事：脚本看得到方法、引擎收到了定义、加载器实际注册了对象。这三者不等价。

## 现在怎么注册

新物品、方块等通常写在 `nekojs/startup_scripts/`，从 `nekojs:event/versioned` 导入 `RegistryEvents`，订阅 `register`。回调放在最后，设置普通属性用赋值，复合配置用方法。以下取自[随包示例](../../common/src/main/resources/nekojs/authoring/examples/startup/registry.js#L1-L14)；它不是旧 Wiki 索引里的 `StartupEvents.registry`，也不是早期的 `RegistryEvents.item(...event.create...)`（参见[旧索引](../../wiki/event-reference_cn.md#L35-L41)、[迁移对照](../../wiki/registering-new-content_cn.md#L505-L523)和[实际事件定义](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryEvents.java#L20-L28)）。

```js
import { RegistryEvents } from "nekojs:event/versioned";

RegistryEvents.register(event => {
    event.item("nekojs_example:ruby", build => {
        build.maxStackSize = 16;
        build.rarity = "rare";
    });
    event.block("nekojs_example:ruby_block", build => {
        build.hardness = 2;
        build.resistance = 6;
        build.lightLevel = 3;
    });
});
```

`Item.of(...)` 是构造/读取已有注册项的 `ItemStack` 的 Binding，不注册 ID；`ItemEvents.modification` 是 SERVER 侧既有物品修改事件，也不是注册入口。配方、标签和资源刷新同样不会产生新的 ITEM 注册项。证据：[ItemJS](../../src/main/java/com/tkisor/nekojs/bindings/static_access/ItemJS.java#L11-L37)、[NeoForge 物品修改事件](../../src/main/java/com/tkisor/nekojs/bindings/event/ItemEvents.java#L56-L65)、[Fabric 物品修改事件](../../src/fabric/java/com/tkisor/nekojs/bindings/event/ItemEvents.java#L137-L138)、[脚本类型说明](../../common/src/main/resources/nekojs/authoring/handbook/script-types.md#L3-L10)。

## 有哪些入口

| 范围 | `RegistryEvents.register` 里的方法 | 核查点 |
|---|---|---|
| 五个构建节点都注册的常用 builder | `item`、`block`、`entityType`、`creativeModeTab`、`mobEffect`、`potion`、`soundEvent`、`particleType`、`villagerType` | [内置工厂清单](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/NekoRegistryPointsPlugin.java#L54-L87)；这是入口清单，不保证任意跨表依赖都能解析。 |
| NeoForge | `fluid` | [工厂的 NeoForge 守卫](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/NekoRegistryPointsPlugin.java#L74-L79)；同时登记 flowing fluid、FluidType、可选 LiquidBlock 和桶，`noBlock()` / `noBucket()` 只关闭相应联动。[FluidBuilder](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/FluidBuilder.java#L229-L267) |
| 入口存在，但不要当作可用的启动注册 | `enchantment` | 内置工厂对附魔并未按版本剔除；26.x 两种加载器都不能经这条静态注册链提交。Legacy 的文档与上游规则有冲突，见下文。 |

默认工厂均为 `basic`。也可用 `event.<registry>(id, typeName, callback)` 指定该表下的插件类型；`event.custom(id, typeName, callback)` 要求名称跨表唯一，因此不能拿共享的 `basic` 来用。`event.register(registry, id, supplier)` 是低层入口；表名可用 `item` 或 `minecraft:item`，supplier 在注册阶段调用，检查非空及已知原生类型，不会打开原版数据包注册表，也不回滚任意 supplier 的副作用。[事件参数与 factory](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryEventJS.java#L135-L229)、[表名解析](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryEventJS.java#L252-L270)、[supplier 校验](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/StartupRegistryRuntime.java#L243-L263)。

元数据由 `registry_infos` 反射原版 `Registries` 的键并接收插件贡献，`registry_types` 负责 factory/default；有默认类型才出现在运行时枚举目录中，并生成省略类型名的重载；只有命名工厂的表仍可生成显式类型名的声明。一个键能被扫描、甚至存在相应 Java builder，都不等于有默认入口：例如 [PaintingVariantBuilder](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/PaintingVariantBuilder.java#L20-L38) 存在，却未列入[内置工厂](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/NekoRegistryPointsPlugin.java#L54-L87)。[元数据收集](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryInfosPoint.java#L42-L108)、[成员目录](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryEventJS.java#L97-L109)、[生成声明组装](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/StartupRegistryEventSurface.java#L19-L39)。

## 从脚本到实际注册

1. 两个加载器在游戏进程启动时新建登记轮次；共同装配流程先初始化插件、发现脚本并执行 STARTUP 脚本，脚本此时只挂上监听器。平台进入注册阶段后，`collectOnce()` 才投递事件；每个启动轮次投递一次。[启动装配](../../common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeAssembly.java#L157-L169)、[收集入口](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/StartupRegistryRuntime.java#L118-L139)。
2. 工厂先建 builder，配置回调通过 `BuilderSurface` 立即执行；回调成功后才存进 `RegistryRepository`；入库会调用 builder 的冻结钩子，Item 定义在此冻结。同注册表同 ID 重复不会覆盖；主项与派生项冲突也会拒绝。此时尚无注册完成的物品。[回调执行与暂存](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryEventJS.java#L214-L229)、[暂存、冻结与重复检测](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryRepository.java#L46-L95)。
3. 平台按表消费 supplier；主项之后调 `handleAdditionalObjects`，派生项等目标表的 pass。同名 BlockItem 默认登记，可改 `build.item` 或用 `noItem()` 关闭，不要另建同 ID 的 `event.item`；实体请求 `spawnEgg(...)` 才生成 `<id>_spawn_egg`。目标 pass 已结束则报告并跳过，不补跑。普通 STARTUP 注册不是整批原子事务：某项失败可记录错误并继续；`DrainResult.registered()` 在调用 sink 前已写记录，不宜当作 native 成功回执。[运行时 drain](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/StartupRegistryRuntime.java#L146-L241)、[方块子 builder](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/BlockBuilder.java#L134-L179)、[BlockItem 构造](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/BlockBuilder.java#L207-L219)、[刷怪蛋构造](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/EntityTypeBuilder.java#L415-L427)。

## Loader 与版本差异

[构建图](../../settings.gradle.kts#L35-L41)有 NeoForge `1.21.1` / `26.1.2` / `26.2.0` 与 Fabric `26.1.2-fabric` / `26.2.0-fabric` 五个节点。

- **NeoForge**：构造时 `beginBoot`、挂 mod event bus；第一次 `RegisterEvent` 触发收集，此后按加载器的 pass 消费，`FMLLoadComplete` 报告始终没轮到的表。实体属性和 `groupTab` 分别通过 `EntityAttributeCreationEvent`、`BuildCreativeModeTabContentsEvent` 补接；普通物品燃料由 `Item.getBurnTime` 的 NeoForge override 提供。[入口接线](../../src/main/java/com/tkisor/nekojs/NekoJSMod.java#L65-L97)、[适配器](../../src/main/java/com/tkisor/nekojs/listener/RegistryEventAdapter.java#L50-L110)、[燃料 override](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/ItemBuilder.java#L209-L225)。
- **Fabric**：在 `onInitialize`、STARTUP 脚本执行后直接收集并向 `BuiltInRegistries` 写入。每轮刷新待处理的键，非 ITEM 先跑，最后单次 ITEM（无自定义物品也要完成空 pass）；查不到表时记录 `platform-register` 错误，而非放进原版动态表。实体属性、创造栏追加和燃料分别接 `FabricDefaultAttributeRegistry`、`CreativeModeTabEvents` 和 `FuelValueEvents.BUILD`；燃料表重建时重放已登记数值，不重注册物品。[Fabric 入口](../../src/fabric/java/com/tkisor/nekojs/fabric/NekoJSFabricMod.java#L128-L141)、[Fabric 适配器](../../src/fabric/java/com/tkisor/nekojs/fabric/FabricRegistryAdapter.java#L39-L119)。
- **1.21.1**：共用注册框架，但 `Block`、`Item`、`Fluid`、`EntityType`、`Enchantment` 等由节点内同名实现覆盖；有些共享类经 [Stonecutter replacements](../../stonecutter.gradle.kts#L49-L81) 将 `Identifier` 等调用转换为 legacy API。不要用 26.x builder 的代码或生成声明来推断 1.21.1 的细节。[1.21.1 ItemBuilder](../../versions/1.21.1/src/main/java/com/tkisor/nekojs/wrapper/registry/gen/ItemBuilder.java#L130-L220)、[1.21.1 BlockBuilder](../../versions/1.21.1/src/main/java/com/tkisor/nekojs/wrapper/registry/gen/BlockBuilder.java)、[1.21.1 EntityTypeBuilder](../../versions/1.21.1/src/main/java/com/tkisor/nekojs/wrapper/registry/gen/EntityTypeBuilder.java)。

### 附魔：声明不等于可提交

`event.enchantment` 的默认工厂和 builder（包括 [1.21.1 override](../../versions/1.21.1/src/main/java/com/tkisor/nekojs/wrapper/registry/gen/EnchantmentBuilder.java#L127-L150)）说明引擎可以接受定义，不证明加载器拥有静态注册 pass。[26.x 移植台账](../../docs/fabric-port-status.md#L110-L121)记录了附魔是数据驱动表，Fabric 无 built-in 表，NeoForge 实测不会派发对应 pass。项目 [Wiki](../../wiki/registering-new-content_cn.md#L365-L386)还列 1.21.1 脚本附魔可用，但 [NeoForge 1.21.1 官方文档](https://docs.neoforged.net/docs/1.21.1/resources/server/enchantments/)明确说从 1.21 起附魔定义用 JSON，而 [NeoForge 21.1 的 `GameData.postRegisterEvents`](https://github.com/neoforged/NeoForge/blob/21.1/src/main/java/net/neoforged/neoforge/registries/GameData.java)只对 `BuiltInRegistries` 派发事件。本次没有找到 NekoJS 1.21.1 的独立附魔提交接线或实机成功证据，不能把 Wiki 与 builder 存在当成可用性保证；建议两条版本线都走 `nekojs/data/<namespace>/enchantment/<path>.json`，不要把附魔 effect component type 的静态注册误当成附魔本身。

### 物品组件、模型和标签

- **组件**：共享 `ItemBuilder` 有堆叠/耐久（耐久优先）、稀有度、抗火、发光、燃料、创造栏、食物，以及 `component(...)` / `components`、`jukeboxPlayable`、`trimMaterial`。组件输入在收集时存为不可变 JSON 形状；26.x 在原生延迟组件初始化时以 registry-aware codec 解码，并保护 `item_name` / `item_model`。1.21.1 用构造期 `JsonOps`（需要 registry ops 的 codec 会失败），有 `jukeboxPlayable` 但没有 `trimMaterial`，食物走 legacy `FoodProperties`。[26.x builder](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/ItemBuilder.java#L140-L255)、[现代组件 codec](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/ItemComponentSupport.java#L20-L53)、[1.21.1 builder](../../versions/1.21.1/src/main/java/com/tkisor/nekojs/wrapper/registry/gen/ItemBuilder.java#L130-L220)、[legacy 组件 codec](../../versions/1.21.1/src/main/java/com/tkisor/nekojs/wrapper/registry/gen/ItemComponentSupport.java#L20-L47)、[输入约束](../../common/src/main/java/com/tkisor/nekojs/core/registry/ItemDefinitionConfig.java#L29-L101)。
- **模型与资源**：显式配 `parentModel` 或 `texture(...)`/`textures` 才生成 JSON；ITEM pass 完成后，平台资源加载从「sink 接受且 supplier 成功执行」的登记凭据取冻结定义。生成 `assets/<ns>/models/item/<id>.json`；26.x 另生成 `assets/<ns>/items/<id>.json`，1.21.1 没有后者。PNG 和语言文件要自己提供，外部修改的文件不会覆盖。F3+T 只刷新资源，不重跑 STARTUP，改脚本的生成参数仍要重启。[成功凭据](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/StartupRegistryRuntime.java#L192-L218)、[生成内容](../../common/src/main/java/com/tkisor/nekojs/core/registry/ItemAssetDefinition.java#L17-L34)、[文件所有权](../../common/src/main/java/com/tkisor/nekojs/wrapper/RegisteredItemAssets.java#L34-L81)、[NeoForge 资源](../../src/main/java/com/tkisor/nekojs/resource/NekoJSPackLoader.java#L26-L34)、[Fabric 资源](../../src/fabric/java/com/tkisor/nekojs/fabric/resource/FabricDirectoryPackSource.java#L97-L103)、[26.x 格式](../../src/main/java/com/tkisor/nekojs/resource/NekoJSPathPackResources.java#L21)、[legacy 格式](../../versions/1.21.1/src/main/java/com/tkisor/nekojs/resource/NekoJSPathPackResources.java#L19)。
- **builder 标签**：`build.tag(...)` 的接线只在 NeoForge 26.x 生效，那里每次标签加载都先注入暂存条目，再让脚本改。1.21.1 同名类的注释还描述这件事，但构造器实际没调用 `flushInto`；Fabric 也没有这条接线。跨平台应优先使用数据包标签。[实际 flush 及 loader 守卫](../../src/main/java/com/tkisor/nekojs/wrapper/event/server/TagEventJS.java#L1-L34)、[legacy 构造器](../../versions/1.21.1/src/main/java/com/tkisor/nekojs/wrapper/event/server/TagEventJS.java#L26-L33)、[项目文档](../../wiki/registering-new-content_cn.md#L414-L420)。

## Reload：普通注册必须重启，实验性动态注册是另一条链

- 新增、改动或移除普通物品注册，都要退出并重启游戏/专服进程；原版 `/reload`、`/nekojs reload server`、重进存档和资源刷新都不会重开启动轮次。NeoForge/Fabric 的 `/nekojs reload startup` 明确拒绝，作者工具返回 `RESTART_REQUIRED`。内部 STARTUP reload 还保留脚本 reset/load 路径，但结果明确要求重启，并不回滚或重放加载器注册。[NeoForge 命令](../../src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java#L555-L564)、[1.21.1 命令](../../versions/1.21.1/src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java#L515-L525)、[Fabric 命令](../../src/fabric/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java#L277-L286)、[作者工具](../../common/src/main/java/com/tkisor/nekojs/authoring/AuthoringControl.java#L192-L200)、[内部 reload](../../common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeRoot.java#L495-L502)。
- **只有 NeoForge 26.x** 接了独立的实验性 `DynamicRegistryEvents.dynamicRegistry`：在服务端及客户端配置开启 `dynamicRegistry.enabled`（默认关闭）并重启后，在 `server_scripts` 写声明。只允许 `item`、`soundEvent`、`mobEffect` 的窄 builder，无 `register` / `custom`；服务端首次收集和 SERVER reload 的候选计划，经验证、事务及客户端同步门控后才能激活。相同 ID 仅允许相同指纹的重声明；删除声明只标 stale/retired，不物理注销，改同 ID 定义或收集失败会拒绝该批。实验性功能不是重载 STARTUP，也不是原版附魔等数据包注册表。[平台守卫](../../src/main/java/com/tkisor/nekojs/dynamic/DynamicRegistryPlugin.java#L1-L29)、[SERVER 收集入口](../../src/main/java/com/tkisor/nekojs/listener/ServerEventListener.java#L129-L133)、[动态 payload](../../common/src/main/java/com/tkisor/nekojs/core/dynamic/facade/DynamicRegistryEventJS.java#L17-L47)、[SERVER 候选阶段](../../common/src/main/java/com/tkisor/nekojs/core/dynamic/facade/DynamicRegistryFacadeRuntime.java#L269-L336)、[重复/更改校验](../../common/src/main/java/com/tkisor/nekojs/core/dynamic/plan/DynamicCandidateRegistryPlan.java#L110-L141)、[stale 台账](../../common/src/main/java/com/tkisor/nekojs/core/dynamic/plan/DynamicRegistryPlanStore.java#L137-L158)、[配置与适用范围](../../wiki/registering-new-content_cn.md#L469-L503)。

## 测试证据与未验证项

- [最小示例测试](../../src/test/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryStartupMinimalExampleTest.java#L68-L149)、[运行时 E2E 测试](../../src/test/java/com/tkisor/nekojs/wrapper/registry/gen/StartupRegistryRuntimeEndToEndTest.java#L44-L60)、[epoch 负例](../../src/test/java/com/tkisor/nekojs/wrapper/registry/gen/StartupRegistryEpochNegativesTest.java#L66-L102)覆盖真实脚本回调到替代 sink 的链路、重复/失败隔离和跨 pass 等；这里的 E2E 不是原生加载器端到端测试。
- [声明 Golden](../../src/test/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryBuilderSurfaceGoldenTest.java#L96-L198)核对 TS/Python 的可见 API；[ItemBuilder 回归](../../src/test/java/com/tkisor/nekojs/wrapper/registry/gen/ItemBuilderIssue36Test.java#L20-L87)核对冻结、组件输入与资源凭据；[Fabric 26.1.2](../../versions/26.1.2-fabric/src/test/java/com/tkisor/nekojs/fabric/FabricRegistryDrainBehaviorTest.java) / [26.2.0](../../versions/26.2.0-fabric/src/test/java/com/tkisor/nekojs/fabric/FabricRegistryDrainBehaviorTest.java)以替代 sink 核对派生项顺序及空 ITEM pass。声明可以显示某种 builder，不能替代该表的原生提交验证。
- 本次复核尝试 `:26.1.2:test`，筛选 `RegistryStartupMinimalExampleTest`、`StartupRegistryRuntimeEndToEndTest`、`StartupRegistryEpochNegativesTest`；卡在 `:buildSrc:compileKotlin` 无法删除 Kotlin `cacheable` 目录，三项未执行。未启动 Minecraft；仍缺各节点原生注册、客户端模型/燃料和动态注册多端同步的本次实机结果。
