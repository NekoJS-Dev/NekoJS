# NekoJS API 整理方案（api-rework-plan，1.0.0 冻结准备）

> 阶段 B/C 交付物（2026-08-15）。基于 `docs/api-surface-inventory.md` 的盘点，对照 `ai_arch/unified-js-api-design.md`
> §7（冻结范围）/§9（四层 + ApiManifest）与 `docs/API_VERSIONING.md` 的冻结治理。
> 本文档：差距分析 → 四层归类 → 命名规则 → 按风险排序的整理项 → 迁移路径。**高风险项只出方案不实施，标注「待用户决策」。**

## 1. 差距与问题分析（阶段 B）

### 1.1 命名不一致

| # | 问题 | 现状 | 影响 |
|---|---|---|---|
| N-1 | 契约名↔JS 名映射规则不统一 | core 契约名在 `CoreManagedApiBootstrap` 以**硬编码字符串**传入 `extractSymbols("ID", ...)`；数据类型用 `@ContractReceiver`/value。两条机制并存 | 规则可表述但代码里无单一事实源；新增 facade 容易写错名 |
| N-2 | helper 类后缀混乱（Java 内部，脚本不可见） | 同为 static_access 工厂：`IngredientFactory`（无 JS 后缀）、`FluidJS`、`FluidIngredientJS`、`CapabilitiesJS`；同为 helper：`ItemJS`/`BlockJS`/`TimeJS`/`UtilsJS` | 阅读与迁移成本；不影响脚本面 |
| N-3 | 同概念三套脚本名 | id：`Identifier`(NF26)/`ResourceLocation`(NF121,CR)；文本：`Component`/`MutableComponent`(NF) vs `TextComponent`(CR)；NBT：`CompoundTag`(NF) vs `NBTTagCompound`(CR)；实体：`EntityType`(NF) vs `EntityEntry`(CR) | 跨平台脚本必须条件分支；§4.1 stable 层不允许 |
| N-4 | 同名不同物 | `Fluids`：NF=vanilla `Fluids` 常量类，CR=`FluidRegistry`；`NativeEvents`：NF=可用，CR=显式抛异常 | 语义分裂最危险的一类（脚本不报错但行为不同） |
| N-5 | javadoc 与实际绑定名不符 | `IdFacade` javadoc 写 `{@code Id}`，实际全局名 `ID` | 文档错误（低风险可修，L-1） |
| N-6 | 事件取消拼写分裂 | 注入 `isCancelled()`（双 l）与原生 `isCanceled()`（单 l）在 NF 同时可见 | **已解决**（2026-08-15 裁决移除 mixin 注入面，见 H-3） |
| N-7 | 历史遗留 `IDJS.of` 返回 `Object` | 已被 `NekoId` + `IdFacade` 取代，`IDJS` 类已不存在 | **已解决**，记录为规则先例：新 facade 不返回 `Object` |

### 1.2 平台差异（缺口 vs 刻意降级）

**刻意降级（有注释依据，保留）**：CR `NativeEvents`/`ScriptEvents` 抛异常；CR `FluidIngredient` 返回 `List<FluidStack>`；
CR 省略 `FluidIngredientAdapter`（曾劫持 List 映射）；CR `RegistryEvents` 无 `particleType`/`paintingVariant`（无注册表）；
CR `Ingredient.not()` 抛异常（无 negation 类型）；CR `BlockEvents.placed` 保留 deprecated `PlaceEvent`；
CR 生命周期/注册表事件手动转发（FML 限制）。

**API 缺口（无注释依据，进入 H-2 待决策）**：

| 缺口 | 方向 | 备注 |
|---|---|---|
| `global` 共享 Map | CR 未注册 | `NekoGlobal` 在 common，补注册成本极低，但属跨平台对齐（H-2） |
| `CompoundTag`/`MutableComponent`/`EntityType`/`MobEffectInstance`/`DamageTypes`/`Component` | CR 无 | 1.12.2 无直接对应物；需别名策略而非硬补 |
| `Capabilities`/`TriState` | CR 无 | platform 层设计如此（NF-only），非缺口——归 platform 模块 |
| `ClientEvents` 14 个 bus | CR 无 | 多为现代客户端注册钩子；`chatReceived` 反向 NF 缺 |
| `ItemEvents.pickedUpPre`、`EntityEvents.tickPre/tickPost/leaveLevel`、`BlockEvents.toolModification/randomTick/blockEntityTick`、`ServerEvents.datapackSync/tagsUpdated/lootTables` | CR 无 | 部分可 mixin 补齐（randomTick 先例在 NF） |
| `harvestDrops`/`expire`/`tick`(Player) | NF 无 | CR-only，1.12.2 事件模型产物 |
| NF26 mixin 未注册 6 个 Extension 接口 | 26.x inject 面窄于接口面 | 补 mixin 或收窄声明（H-7） |

### 1.3 Value 泄漏（脚本 facing）

**业务 API 泄漏（违反设计原则 §4.1/§2.2，需修，M-1）**：

| 位置 | 签名 | 绑定暴露 |
|---|---|---|
| neoforge-shared `IngredientFactory` | `of(Value...)` | `Ingredient` |
| neoforge-shared `FluidJS` | `of(Value)`×2、`ingredient(Value...)`、`sizedIngredient(Value)`×2 | `Fluid` |
| neoforge-shared `FluidIngredientJS` | `of(Value...)`、`sized(Value)`×2 | `FluidIngredient` |
| neoforge-shared `NativeEventsJS` | 6 个 `on*(..., Value handler)` | `NativeEvents` |
| cleanroom `IngredientFactory` / `FluidJS` / `FluidIngredientJS` | 同上对应方法 | 同名绑定 |
| common `TestJS` | `assertThrows(Value, String)` | `Test`（TEST-only） |
| common `ScriptEventRegistrationEvent` | `register(Value)` | `ScriptEvents.server/client` payload（**契约符号，修改触发 golden**） |

**边界层（允许，记录边界）**：`JSTypeAdapter` SPI（`test/apply(Value)`，适配器本体）；`EventBusJS.execute(Value...)`
（ProxyExecutable 引擎边界）；`RecipeSchemaHost.encodeField/toJson(Value)` 与平台 `RecipeEventSchemaHost`（插件 SPI 缝）；
`putMember(String, Value)` 族（ProxyObject 接口）；probe/engine/module loader 全部内部层；`DelegatingBinding` 内部。
另有一类「`Object` 参数 + 内部 `instanceof Value`」模式（`DataGeneratorJS.json`、`LootTableJS.addPool`、
`PerformanceFacade.time/bench(Object)`、`NativeEventsJS` 的 `Object eventType`）：**这是修 M-1 应采用的目标形态**
（`Object` 透传 + 边界内转换），其中 facade 已 javadoc 声明意图，可接受。

### 1.4 deprecated / 遗留

- **脚本 facing `@Deprecated` 数量为零**：四平台 bindings/wrapper/js + common api/eventbus 全量 grep 无一命中——
  当前不存在任何弃用窗口机制，1.0.0 前的任何移除都会是静默破坏（治理风险，本方案以 L-3/M-2 建立跑道）。
- Java SPI 侧存在内部弃用（`NekoJS` legacy facade、`LegacyAdapterBridge`、`LegacySurfaceAdapter`、`NekoIdCompat` 等），
  不属脚本面，不处理。
- 遗留标记物：`ApiTier.LEGACY_PREVIEW` + `LegacySurfaceAdapter`（设计上「迁移期存在，不进 baseline」）。

### 1.5 冻结范围差距（对照 §7）

| 冻结域 | 差距 |
|---|---|
| 基础工具 | `Time`/`Utils`（+`Color`/`UUID`/`StringUtils`）是平台注册的 helper 类，**不在 core 契约/golden**；`NbtIO` 未独立（IO 并入 `NBT.read/write`，需在契约中确认归属）；`GlobalData` 现名 `global`（小写、NF-only、绑裸 Map） |
| Item/Ingredient/Fluid | stable 层（不暴露原生类）**完全未建立**：现有 `Item`/`Ingredient`/`Fluid` 全部返回原生 `ItemStack`/`Ingredient`/`FluidStack`（任务约束：保留 vanilla 类名绑定，不换 wrapper）→ stable facade 与原生绑定需分层共存（H-4） |
| 事件 | 五事件组的 payload/取消语义无 contract tests；`EntityEvents` dispatch 键类型分裂（`Entity` vs `EntityType`）。**取消统一已裁决（2026-08-15）**：移除 mixin 注入的 cancel/isCancelled，约定维持返回 `true` + 原生 `setCanceled`（见 H-3 实施记录） |
| 配方 | stable 子集（ids/count/exists/remove + shaped/shapeless/smelting）未从 `RecipeEventJS` 大面上标记或剥离；`@nekojs/feature/recipe-json` 模块未落地 |
| Network | `NetworkJS` 行为已跨平台对齐（NF26/NF121/CR 三份实现、getter 一致），但未进契约；channel/payload 协议未按 §7.5 承诺化 |

### 1.6 重复 / 重叠

| # | 重叠 | 判断 |
|---|---|---|
| D-1 | CR `ServerEvents.worldLoad/worldUnload` 与 `LevelEvents.loaded/unloaded` 双入口同语义 | 需去重（H-5） |
| D-2 | `NativeEvents`（NF）与 `ScriptEvents.register` 都能注册原生事件 | 两套面：前者任意原生类+Value handler，后者组/名声明式。方向：`ScriptEvents` 进 stable，`NativeEvents` 归 platform 模块（H-4） |
| D-3 | `IngredientFactory`（工厂）与 wrapper `IngredientJS`（`or/and/...`） | 设计如此（工厂造、wrapper 组合），保留；命名规则统一后缀（C-2） |
| D-4 | `Time`（tick 换算）与 `Performance.now()`（ms 计时） | 语义不同（游戏刻 vs 墙钟），保留但在文档互相指引 |
| D-5 | NF `ClientEvents.tick`/`LevelEvents.tick` 等 7 个别名 bus 与主名并存 | canonical 方向需决策（H-5） |
| D-6 | `RecipeViewerEvents` 组定义完整但从未注册 | 注册或删除（H-8） |

## 2. 四层归类提案（阶段 C-1）

归类依据 §4 规则：stable=四平台同名同签名同语义且无原生类型泄漏；feature=平台中立但非全平台可提供；platform=loader 专属；
version=版本专属/返回原生 host 对象。**当前能进 stable 的只有 core 契约已冻结子集；其余表面先明确归类，再逐域通过
domain contract 提升进 stable（§9.1 阶段 gate）。**

| 域 | stable（现为 core 契约） | feature（`@nekojs/feature/*`） | platform（`@nekojs/platform/*`） | version（`@nekojs/version/*`） |
|---|---|---|---|---|
| 基础工具 | `ID`/`Platform`/`Text`/`NBT`/`JsonIO`/`Registry`/`Performance`（147 符号）；**候选提升**：`Time`/`Utils`/`GlobalData`（先补 domain contract） | — | — | `Color`/`UUID`/`StringUtils` 可作 feature 或随 stable 提升（待定） |
| Item/Block | （无——待 stable facade 层建立） | `item-tags` 类 tag 语义（部分平台） | `Item`/`Block` 代理+`ItemJS`/`BlockJS`（id/of/empty 查询面） | `ItemStack`/`Items`/`Blocks`/`SoundEvents`/`MobEffects` 等原生类绑定；`EntityType`↔`EntityEntry`、`CompoundTag`↔`NBTTagCompound` 等版本名对 |
| Ingredient/Fluid | （无） | `fluid-ingredient`（NF 真类型；CR 无） | `Ingredient`/`Fluid`/`FluidIngredient` 工厂+wrapper（返回原生类型） | `SizedIngredient`/`FluidStack`/`Fluids`（CR 语义分裂物）、`TriState` |
| 事件 | 五组中满足准入五标准的 bus（**逐 bus 契约化后提升**；当前 0 个已验证） | `recipe-viewer`、`client-keybinds` 类（NF 有 CR 无） | `CapabilityEvents`、`NativeEvents`、`ScriptEvents`（原生注册桥）、CR 手动转发生命周期族 | `harvestDrops`/`expire` 等 1.12.2-only bus；NF26 mixin 未覆盖的 inject 面 |
| 配方 | ids/count/exists/remove + 三基础形状（**待从 `RecipeEventJS` 标记剥离**） | `recipe-json`（schema/codec/replacement 全家） | CR `tags`（OreDictionary 语义） | 各平台 `MinecraftRecipeHandler` 细节 |
| Network | `Network` 协议面（channel+NbtValue/JsonValue payload，待契约化） | — | 底层 payload 注册差异 | — |
| Probe/工具 | `ProbeEvents`（内部工具面，保持 common） | — | — | — |

归类理由示例：`Identifier`/`ResourceLocation` 进 version 而非 stable——JS 名与类名随 MC 版本变化，违反 §4.1「不得要求脚本检查版本」；
`Capabilities` 进 platform——loader 专属但可用稳定类型表达；`FluidIngredient` 的 `of` 语义在 CR 退化为 `List<FluidStack>`，
属「不同语义完成」，按 §7.4 原则禁止伪装 stable，归 feature（NF）+ platform（CR 降级实现）双轨。

## 3. 命名统一规则提案（C-2）

1. **JS 名 = 契约名**：脚本可见名唯一来源是契约（`facadeName` 字符串 / `@ContractReceiver` value / 绑定注册名字符串）。
   新增 facade 时契约名必须与注册名一致，javadoc 引用绑定名（修 N-1/N-5）。
2. **缩写保留全大写**：`ID`/`NBT` 维持现状（历史形成，改名是破坏性变更，收益低）。
3. **Java 类后缀规则**（脚本不可见，可渐进实施，见 M-2）：
   - common-api facade 接口 `XxxFacade` + 实现 `DefaultXxxFacade`（现状已符合）；
   - static_access 绑定 helper/工厂统一 `XxxJS`；工厂例外统一为 `XxxFactory`——二选一需定案（**待用户决策**，因涉及
     `IngredientFactory`↔wrapper `IngredientJS` 撞名取舍；本方案倾向：static_access 统一 `XxxJS`，
     `IngredientFactory` 改名 `IngredientJS`（与 wrapper 不同包不冲突），`FluidJS` 等不动）；
   - wrapper 值类统一 `XxxJS`（现状已基本符合）；
   - `neko$` 前缀保留给 inject-spec（现状已符合）。
4. **事件 bus 命名**：动词过去式（`loggedIn`/`placed`）+ Pre/Post 后缀拆分同类事件；别名 bus 必须在声明处注释
   「alias of <主名>」，主名进入 stable 前完成方向裁决（H-5）。
5. **跨平台同概念绑定**：stable 层一律用中立名（`CompoundTag` 类概念由 `NBT`/`NbtValue` 承担）；原生类绑定保留平台名
   但归 version 层（与任务约束「保留 vanilla 类名绑定」一致）。

## 4. 整理项清单（按风险排序，C-3）

### 低风险（本批已实施，见 git log）

| ID | 项 | 验证 |
|---|---|---|
| L-1 | `IdFacade` javadoc `{@code Id}`→`{@code ID}` | `:common-api:check :common:check` |
| L-2 | 删除 cleanroom 死代码 `TextJS`（未注册、零引用） | `:platforms:cleanroom-1.12.2:compileJava` 0 警告 |
| L-3 | CR `BlockEvents.placed`（deprecated `PlaceEvent`）与 `PlayerEvents.tick`（无 filter 兼容别名）补 `@Deprecated` + 替代指引 | cleanroom compileJava；若注册点产生 deprecation 警告按 SPEC §2.4 注释化抑制 |
| L-4 | 盘点与方案两文档入库 | review |

### 中风险（本批已实施，双/四平台编译 + 全量 common 测试）

| ID | 项 | 兼容策略 |
|---|---|---|
| M-1 | Value 泄漏修复（§1.3 表全部 9 文件）：公开参数 `Value`→`Object`，边界内 `Value.asValue` 转换；`TestJS.assertThrows` 同法（保留友好错误消息） | 脚本调用形状不变（JS 值经 `Object` 形参仍以 `Value` 到达）；`ScriptEventRegistrationEvent.register` callKey 变化 → golden 显式 regen + review（行为等价的签名变化，非破坏） |
| M-2 | 命名规则文档化（本文件 §3），类改名**不实施**（`FluidIngredientJS.java` 等文件正被并行修改，避免冲突） | 后续批次单独提交 |

### 高风险——已裁决实施（2026-08-15）

| ID | 项 | 裁决与实施 |
|---|---|---|
| H-3 | 事件取消统一 | **用户裁决：移除 mixin 注入方案，不再追求显式 `event.cancel()`。** 已实施：删除 `EventSpec`（含 `EventSpecTest`）、NF121/NF26S `EventExtension`、NF121 `MixinEvent` + mixins.json 条目、26-shared `nekojs.interface_injection.json` 的 `net/neoforged/bus/api/Event` 条目。跨平台取消约定 = 监听器返回 `true`（全平台）+ 原生 `setCanceled(true)`/`isCanceled()`（version 层原生面）。同时消除 `isCancelled/isCanceled` 拼写分裂与「CR Event 基类不可 mixin」的不对称（偏离设计基线 §7.3 的理由：基类注入在 CR 不可实现、且与原生方法拼写/语义双轨）。 |

### 高风险（**待用户决策，不实施**）

| ID | 项 | 决策点 |
|---|---|---|
| H-1 | 脚本可见符号改名/统一：`Identifier`↔`ResourceLocation`、`TextComponent`↔`Component`、`NBTTagCompound`↔`CompoundTag`、`EntityEntry`↔`EntityType`、`Fluids`（CR 语义分裂）、`global`→`GlobalData` | 改名即破坏；选主名+别名保留期；`Fluids` 需决定 CR 绑定改名（如 `FluidRegistry`）还是换绑 vanilla 常量 |
| H-2 | 跨平台 API 对齐：CR 补 `global`（成本最低，建议首个批准）；其余按「别名策略 vs platform 模块声明」逐项定 | 对齐方向与别名保留期 |
| H-4 | tier 落地与 core 契约扩充：`Time`/`Utils`/`GlobalData`/`Network` 进 stable 需先写 domain contract（§9.1 gate）；feature/platform/version 模块注册机制启用；`Item`/`Ingredient`/`Fluid` stable facade 层是否建立（与「保留原生类绑定」共存方式） | 工作量最大；决定 1.0.0 冻结的实际边界 |
| H-5 | 事件语义去重：CR `worldLoad/worldUnload` 双入口；7 个别名 bus 主名方向；`ItemEvents.tooltip` side 分歧（CLIENT vs SERVER） | 每个 bus 的 stable 准入判定 |
| H-6 | `EntityEvents` dispatch 键统一（`Entity` 实例 vs `EntityType` id） | 键类型变化对已有脚本分发的影响面 |
| H-7 | NF26 六个未注册 mixin 的 Extension 接口处置 | 补 mixin（扩 26.x inject 面）或收窄接口声明 |
| H-8 | `RecipeViewerEvents` 未注册组：注册进 ClientEvents 注册链 or 删除 | 是否作为 feature `recipe-viewer` 的载体 |

## 5. 迁移路径

1. **0.12.x（当前）**：实施 L/M 批次；所有改动保持运行时注册语义不变（constraint：EventGroup/BindingRegistry 行为不变，
   仅表面参数形态与文档）；golden 变化逐条 review 入库。
2. **0.13（建议）**：用户决策 H-1/H-2/H-5 中「别名可解决」的子集 → 加别名 + `@Deprecated` 主旧名（首次建立脚本侧弃用跑道：
   至少保留一个 minor）；事件取消已按 H-3 裁决落地（return-true 约定，无 mixin 面），契约按该约定描述取消语义。
3. **1.0.0 前**：H-4 按 domain contract 逐域推进（基础工具 → 事件 → 配方 → Network 顺序，与 §7 冻结范围一致）；
   每域「normative contract → 四平台实现 → ApiManifest conformance」闭环后进 golden。
4. **弃用窗口约定**：脚本侧符号弃用 = `@Deprecated` + javadoc 替代品 + wiki 条目；移除最早发生在下一个 minor；
   `ApiTier.LEGACY_PREVIEW` 符号不得进入任何 baseline（§9.5）。
