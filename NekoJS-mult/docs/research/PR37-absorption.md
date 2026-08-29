# PR #37 吸收分析（fork 分支 `genericregistry`）

> 分析基线：`gh pr diff 37` 全量（15 文件，+914/−5）对照 `docs/adr/0001–0009` 与
> master（NekoJS-mult，2026-08-29 收束态）已落地实现。只产分析，不改代码。
> 结论速览：**可直接吸收 0 文件；需重写（已被吸收后重写）6 块；丢弃 9 文件**——
> PR 的设计意图在路线图 P1/P2 期间已全部被 ADR 采纳并以新接缝落地，
> PR 的代码本体（`platforms/` 布局 + 旧版扩展点 API）在当前树上没有落位。

---

## 0. PR #37 内容概览

作者：ZZZank（`@author ZZZank` 标注全部核心类）。15 个文件分三组：

| 组 | 文件 | 内容 |
|---|---|---|
| ① 扩展点小改 | `NekoPluginExtensionPoint`（+13/−3）、`NekoPluginBootstrap`（+5/−2） | 给旧版 Point 加 `onFinish` 回调（`of(...)` 重载 + bootstrap 末尾执行） |
| ② 注册表核心 | `platforms/neoforge-26.2/.../registry/base/` 下 7 个新类 | `RegistryInfo`（元信息 record）/`RegistryInfos`（静态单例 + 反射扫描）/`RegistryObjectBuilder`（抽象基类）/`RegistryObjectType`（类型标识+工厂）/`RegistryObjectTypeRegistry`（Scope 模式注册表）/`RegistryObjectTypes`（冻结产物）/`RegistryEventJS`（`custom`/`register` 脚本面） |
| ③ 插件装配 + 首个 Builder | `plugin/` 4 个类 + `impl/ItemBuilderJS` | `RegistrySupportPlugin`（Contributor 接口）/`RegistryInfosPlugin`（Provider，两阶段 + onFinish 串联）/`RegistryInfosRegistry`（收集器）/`BuiltinRegistrySupport`（扫描 `Registries.class` + 注册 `basic` Item 类型）/`ItemBuilderJS`（public field + stackSize/damage/fireResistant/rarity/glowing/burnTime/food） |
| ④ 文档 | `docs/MINECRAFT_REGISTRY.md`（+218） | 设计说明 + §2.2 扩展点三大痛点 + §3 语法约束 + §4 使用示例 + 待办清单 |

PR 描述原话：*"剩下需要补充的东西我记录在 docs/MINECRAFT_REGISTRY.md 了"*——
即 PR 自视为骨架，文档是后续工作的移交清单。issue #38 的 RE1/RE2 两轮研究
正是从这份文档的 §2.2 三大痛点出发的。

---

## 1. 逐块三档归类

### ① 可直接吸收——无

没有任何一个文件能原样落进当前共享版本树。两类硬性障碍：

- **布局障碍（M1/ADR-0007）**：全部代码住在 `platforms/neoforge-26.2/src/`——这个目录布局
  在 master 上已不存在（现为共享版本树 `src/` + `versions/<node>/`）；且 `base/` 类大量
  `import net.minecraft.*`，按 ADR-0007 判据属于"差异可守卫表达"的 src 共享树层，
  需要重新评估每类的守卫/无守卫归属。
- **API 面障碍（ADR-0001/0002）**：① 组直接修改的 `NekoPluginExtensionPoint.of(...)` 工厂
  与 `onFinish` 回调，在当前树上已不存在——V2 落地时七个过渡工厂全部删除，
  依赖语义由 `dependsOn`/`result` 双轨取代，`onFinish` 被 `finisher` 取代。

### ② 需重写——设计意图已被 ADR 采纳，实现不符（共 6 块）

#### ②-1 反射扫描根发现注册表（`RegistryInfos` 的 `scanFromClass`）→ **已按 ADR-0004 重写落地**

- **PR 设计**：扫描类中 `public static final ResourceKey<Registry<XXX>>` 字段；泛型链解包
  （`ParameterizedType` 两层 → 元素类型作为 `objectBaseType`）写得相当完整，连
  `Class/ParameterizedType/GenericArrayType/TypeVariable/WildcardType` 五种 Type 形态的
  可行性注释都留了。这就是 ADR-0004"贡献式扫描根（adapter 贡献 Registries.class，
  反射扫 ResourceKey 字段，版本自适应）"的直接来源。
- **当前实现**：`RegistryInfosPoint.scan()`——同一扫描思路，但作为扩展点 `finisher` 的
  纯函数（`initializer→collector→finish` 三段式），产物是不可变 `RegistryInfos` record、
  键去重首胜、无静态单例。差异点：当前版元素类型记为 `token`（诊断/校验用），
  PR 版记为 `objectBaseType`（参与工厂构造）——当前版把类型校验收敛到 `RegistryObjectType`
  层，RegistryInfo 保持纯元信息。
- **裁决：②（已重写完成）**。注意：PR 版的"rawType"（`Type`，支持泛型注册表元素如
  `Codec<? extends Number>` 注释）在当前版未保留——若未来出现泛型元素注册表，
  PR 的五种 Type 形态注释值得回看（见 §3 测试清单）。

#### ②-2 两阶段扩展点（registry_infos → registry_object_types）+ onFinish 串联 → **被 ADR-0002 依赖语义取代**

- **PR 设计**：`RegistryInfosPlugin` 在 `registry_infos` 的 `onFinish` 里构建 `RegistryInfos`
  实例、顺手初始化第二个扩展点的累积器——PR 文档 §2.2 自己承认这是 hack：
  *"一个扩展点需要的资源在需要之前就初始化了，同时又干涉另一个扩展点的初始化"*。
- **当前实现**：`RegistryInfosPoint` / `RegistryTypesPoint` 两个独立 Point 文件，
  `RegistryTypesPoint` 声明 `dependsOn(registry_infos)` + initializer 里 `result(registry_infos)`
  读产物（ADR-0002 双轨语义的 V2 用例②）。onFinish 机制消亡，被 `finisher` 取代。
- **裁决：②（已按 ADR-0001/0002 重写完成，机制保留、hack 消亡）**。PR 的 `of(...)` 工厂
  与 `NekoPluginBootstrap` 的 onFinish 循环本体**丢弃**（见 ③）。

#### ②-3 `RegistryEventJS` 脚本面（`custom(id, type, cb)` + `register(id, supplier)`）→ **已按 ADR-0006 重写落地**

- **PR 设计**：`custom` 从类型表查工厂、`register` 收裸 Supplier、id 缺省补 `nekojs:` 前缀、
  重复 id 抛错、`viewProviders()` 不可变视图。
- **当前实现**：`RegistryEventJS`（ProxyObject）——`custom(id, typeName, cb)` 保留（类型名改
  **全局唯一**，跨注册表同名要求限定写法）、`event.<sugar>(id, cb)` 糖方法从 default 类型
  自动派生（`RegistryInfo.sugarName()`）、`register` 有两种形态、id 冲突在
  `RegistryRepository` 层对象级 fail-fast（`putIfAbsent` + 连带条目查重）。
- **裁决：②（已重写完成）**。PR 的 `custom(id, type)` 双参重载（不带 callback）未保留——
  当前 custom 强制 callback；差量可忽略（脚本可用 `custom(id,type,b=>{})`）。

#### ②-4 `RegistryObjectBuilder` 基类 + public field 约定 → **已按 ADR-0005 重写落地**

- **PR 设计**：抽象基类持 `info`/`id`（public final）+ `abstract T build()`；javadoc 写明
  public field 而非链式方法的原因（子类返回类型无法表示自身）——这段理由原文进了 ADR-0005。
- **当前实现**：`RegistryObjectBuilder<T> implements Supplier<T>`（get() 即 build，连带注册
  懒互引三件套之一）+ `handleAdditionalObjects(AdditionalObjectRegistry)` 钩子（连带注册
  回调式，PR 文档 §1.2 第 4 点设想的 `handleAdditionalRegistry` 落地形态）。
- **裁决：②（已重写完成）**。PR 版缺 Supplier/连带钩子，直接合会破坏 Fluid 四子与
  Block→BlockItem 的现有连带链。

#### ②-5 类型注册（`RegistryObjectTypeRegistry` + Scope 模式）→ **已按 ADR-0004/0005 重写落地（Scope 被否决）**

- **PR 设计**：`scope(Registries.ITEM)` 返回 AutoCloseable Scope，try-with-resources 注册；
  `RegistryObjectType` 持 `resourceKey + registryInfo + type名 + 工厂` 四元组；
  `RegistryObjectTypes.Internal.REGISTERED` 静态 Map 承载冻结产物。
- **当前实现**：`RegistryTypesPoint`（Collector + `MergePolicy.overrideWarn`——内置类型可被
  第三方覆盖 + warn，取代 PR 版 `HashMap.put` 静默覆盖）；**Scope 模式被 ADR-0004 明确否决**
  （"弃 Scope"，R1 裁决记录在案）；`RegistryObjectType` 缩为
  `record(name, registry, factory)` 三元组——registryInfo 不再随类型携带（扫描层统一供），
  类型名改为**全局唯一**（跨注册表同名在 RegistryEventJS.custom 报错并要求限定写法，
  与 PR 的 per-registry 命名空间不同）。`Internal.REGISTERED` 静态可变产物被 Point 产物
  + `RegistryRepository` 取代（reload 可重入）。
- **裁决：②（设计意图保留、三个实现细节按 ADR 改写：merge 策略化、Scope 删除、
  类型名全局化）**。

#### ②-6 `ItemBuilderJS` + `BuiltinRegistrySupport` → **已按 ADR-0005 重写落地（字段面几乎一致）**

- **PR 设计**：`ItemBuilderJS` 字段面 = `maxStackSize/maxDamage/fireResistant/rarity/glowing/
  burnTime/groupTab/food(Consumer<FoodBuilderJS>)`；`buildProperties()` 独立方法供 BlockItem
  复用；匿名子类仅在有方法覆盖需求时用。`BuiltinRegistrySupport` 扫 `Registries.class` +
  注册 `"basic"` 类型。
- **当前实现**：`ItemBuilder` 字段面几乎逐字段一致（maxStackSize/fireResistant/glowing/
  burnTime 同名同默认值；`maxDamage`→`durability` 语义同；food 改 void 方法 + 私有 builder，
  food 的 CONSUMABLE 组件同款处理；`buildProperties()` 同名方法同样供 BlockBuilder 的
  预创建 `b.item` 子 builder 复用）。`BuiltinRegistrySupport` 的职责并入
  `NekoRegistryPointsPlugin`（版本树 provider）+ `RegistryInfosPoint` 固定追加
  `Registries.class` 扫描根 + `RegistryTypesPoint` 内置类型清单。
- **裁决：②（已重写完成，字段命名有两处差异：`maxDamage` vs `durability`、
  `groupTab` 去向——当前版 groupTab 经 `groupTab` 字段或侧通道，PR 版是 Identifier 字段。
  若有脚本按 PR 草稿写过 `b.maxDamage`，迁移表需覆盖）**。

### ③ 丢弃——被 ADR 否决或已被现有实现覆盖（9 项）

| # | 项 | 理由 |
|---|---|---|
| ③-1 | `NekoPluginExtensionPoint.of(...)` 四参/三参工厂（+13/−3） | **ADR-0001**：builder 唯一入口，七个过渡工厂已删。PR 的 of() 属于被删的旧工厂家族；且其第 4 参 `onFinish` 被 finisher 语义取代 |
| ③-2 | `NekoPluginBootstrap` 的 onFinish 执行循环（+5/−2） | **ADR-0001/0002**：bootstrap 的 Point 收集循环由 V2 引擎接管（拓扑排序 + finisher），onFinish 循环不存在也不应存在 |
| ③-3 | `onFinish` 回调概念本身 | **ADR-0001/0002**：`finisher`（累积器→产物，纯函数、reload 可重入）+ `dependsOn/result` 覆盖其全部合法用例；PR 文档 §2.2 自己论证了 onFinish 的三大问题 |
| ③-4 | `RegistryInfos.setInstance/getInstance` 静态单例 | **ADR-0004**：产物经扩展点 finisher 产出、随 Point 句柄/`result` 访问；静态可变单例破坏 reload 可重入（V2 的"扩展点自身不持有跨轮状态"） |
| ③-5 | `RegistryObjectTypes.Internal.REGISTERED` 静态可变 Map | 同上：**ADR-0004** 三阶段生命周期（收集→抽干→after-all），产物经 finisher；静态直赋（`= Map.copyOf(...)`）不可重入 |
| ③-6 | Scope 模式（`RegistryObjectTypeRegistry.Scope` + AutoCloseable + try-with-resources） | **ADR-0004 明确否决**（R1："弃 Scope"）。当前注册面是 Collector 的 `registerType(registry, name, factory)`，冲突走 overrideWarn |
| ③-7 | `RegistryObjectType` 携带 `registryInfo` 四元组 | **ADR-0004/0005**：类型缩为 `(name, registry, factory)`，registryInfo 由扫描层统一供给——类型条目与元信息解耦（否则每个类型条目都要背一份 info 引用，且扫描层晚于类型层完成时会出现悬空引用——恰是 PR §2.2 痛点 2 的变体） |
| ③-8 | `RegistrySupportPlugin`（`extends NekoJSPlugin` 的双钩子接口） | **ADR-0001/0003**：Contributor 接口模式（`RegistryInfosPoint.Contributor` / `RegistryTypesPoint.Contributor` 自包含文件）取代"一个接口管两个扩展点"；且内置点不再走 `@RegisterNekoJSPlugin` 注解路径（NekoBuiltinPointsPlugin 清单 + bootstrap 显式提升，E3） |
| ③-9 | `RegistryInfosPlugin`（Provider 内嵌 onFinish 串联两阶段） | **ADR-0002/0003**：`NekoRegistryPointsPlugin`（声明 `dependsOn` 挂两 Point）取代；onFinish 串联正是 §2.2 自证的反模式 |

**`platforms/neoforge-26.2/.../registry/base/` 全部 11 个新文件因此没有"直接吸收"的落位**：
它们的职责已被 `src/main/java/com/tkisor/nekojs/wrapper/registry/gen/` 的对应实现承担
（RegistryInfo/RegistryInfos/RegistryObjectBuilder/RegistryObjectType/RegistryEventJS →
gen 包同名类；RegistryObjectTypeRegistry+Scope/RegistryObjectTypes/RegistryInfosRegistry →
RegistryTypesPoint/RegistryInfosPoint 的 Collector；RegistrySupportPlugin/RegistryInfosPlugin/
BuiltinRegistrySupport → NekoRegistryPointsPlugin）。

---

## 2. 值得抢救的测试用例与边界场景清单

PR 本体**零测试**。以下是从 PR 代码与文档中挖出的、当前实现值得补测（或验证已覆盖）的
边界场景，按来源标注：

**来自 `RegistryInfos.scanFromClass`（PR 反射扫描器，注释含五种 Type 形态推演）**：

1. 扫描根字段形态矩阵：`ResourceKey<Registry<Class>>`（Class 元素）、
   `ResourceKey<Registry<ParameterizedType 元素>>`（如 `Registry<Codec<? extends Number>>`）、
   `ResourceKey<Registry<T[]>>`（GenericArrayType）、`ResourceKey<Registry<T>>`（TypeVariable）、
   通配符形态——PR 版对非 Class/ParameterizedType 直接 `continue` 跳过；
   **当前 `RegistryInfosPoint.scan` 只做 `ResourceKey.class.isAssignableFrom` 判断，
   不解析元素类型，天然免疫**——但值得一条负向测试锁死"任意形态字段不炸扫描"。
2. 非 public / 非 static / 非 final 的 ResourceKey 字段必须被忽略（PR 有三重 modifier 过滤；
   当前版只查 static + 类型兼容——**语义差异**：当前版会拾取非 public 的 static 字段，
   行为更宽，需确认是特性还是疏漏）。
3. 多扫描根重复贡献同一 ResourceKey → 键去重首胜（当前 `putIfAbsent` 已实现，值得显式测试）。
4. 扫描根类抛 `IllegalAccessException`（非 public 字段 `field.get`）→ 单字段失败不中断整扫
   （PR 与当前版都有 try-catch continue；值得测试锁死）。

**来自 `RegistryEventJS.createNewId`（PR 的 id 规范化）**：

5. 无命名空间 id 自动补 `nekojs:` 前缀（当前 `parseId` 已实现，值得显式测试补全结果）。
6. 同一事件回调内重复 id 抛错（PR 在 `createNewId` 即查 `providers.containsKey`；
   当前版把冲突检查移到 `RegistryRepository.putIfAbsent`（对象层 fail-fast）——
   **覆盖存在**，但"同回调内先 custom 后 register 同 id"这个具体序列值得一条测试。
7. `Objects.requireNonNull(types.get(type), "No registry object type matching ...")` ——
   未知类型名的报错形态（当前版 custom 对全局唯一类型名的查表失败路径同样值得对齐测试）。

**来自 `ItemBuilderJS.buildProperties`（属性覆盖矩阵）**：

8. `maxDamage > 0` 与 `maxStackSize` 互斥分支（durability vs stacksTo 二选一）——
   当前 ItemBuilder 同逻辑，值得参数化测试锁死。
9. `glowing`/`burnTime` 的匿名子类降级路径：仅当需要覆盖方法时才生成子类
   （影响伪装优化与 object identity）——当前已实现，值得断言"无 glowing/burnTime 时
   build() 返回 Exactly `Item` 类型而非匿名类"。
10. `food` 同时设置 `props.food(...)` 与 `props.component(CONSUMABLE, ...)` 双组件
    （26.x 食物拆双组件的坑）——值得一条组件断言。

**来自 `RegistryInfosPlugin`（两阶段时序，PR §2.2 的教训本身）**：

11. `registry_object_types` 在 `registry_infos` finish 之前被 collector 调用 → 必须失败
    （当前由 dependsOn 拓扑保证 + result 违序抛 IllegalStateException——
    V2 原型 9 违例场景已覆盖，可对照 PR 场景补一条"注册表点链"专项用例）。
12. 空 `classesToScan` + 空 `additionalInfos`（无任何注册表贡献）→ 产物为空 map 不炸
    （当前 `RegistryInfos` record 直接成立；值得一条空产物测试）。

**来自 `RegistryObjectTypeRegistry.ScopeImpl`（Scope 构造副作用）**：

13. Scope 构造时 `registryInfos.get(key)` 为 null（未扫描到的注册表）→ PR 版会在后续
    `createBuilder` NPE；当前版类型层不持 info，`RegistryTypesPoint.registerType` 对
    未知 registry 的处理值得对齐测试（fail-fast 还是延迟到对象注册时）。

---

## 3. `docs/MINECRAFT_REGISTRY.md` 中未进 ADR 的设计点

逐节核对 §1–§5 后，**核心设计全部进了 ADR**（§2.2 三痛点 → ADR-0001/0002/0003；
§3.1 public field → ADR-0005；连带注册思路 → ADR-0005 回调三件套；§2.1 两阶段 → ADR-0002）。
以下是**未进 ADR 的残余点**，按价值排序：

1. **「跨平台改造待办」的自我修正（§1.2 第 1 条，原文残句）**：
   *"RegistryEventJS 应当避免引用 neoforge 的 RegisterEvent，而"*（句子未写完）。
   意图可辨：脚本事件对象与平台事件解耦。这条**实质已由 ADR-0004 三层解耦吸收**
   （平台适配层订阅 loader 事件 → 平台无关 RegistryEventJS），但 PR 作者"RegistryEventJS
   不 import 任何 loader/平台事件类"的**显式约束**值得补进 ADR-0004 的实现注记——
   当前 gen 版 RegistryEventJS 确实零平台 import，是无意为之还是有意维持，无记录。
2. **Builder 扩展路线图（§1.2 第 2 条）**：axe/hoe/shovel 等工具变体、helmet/chestplate
   装甲变体、Block/Fluid/EntityType/Potion/Enchantment 待办——当前已落地
   item/block/fluid/entityType/enchantment/particleType/creativeModeTab/mobEffect/potion/
   soundEvent/villagerType/paintingVariant 共 12 类，但**工具/装甲变体**（需要
   `Tier`/`ArmorMaterial` 参数化）未做，PR 把它们列为第一优先。这是真实的后续 backlog，
   建议记录到 wiki《注册新内容》或 issue（不进 ADR——ADR 只锁模型不锁 backlog）。
3. **`builder.additionalBuilders()` 与 `handleAdditionalRegistry(Consumer<RegistryObjectBuilder>)`
   双形态设想（§1.2 第 4 点）**：ADR-0005 只采纳了回调式（`handleAdditionalObjects`）。
   PR 设想的**另一种形态**——`additionalBuilders()` 返回"需要连带注册的其它 builder 列表、
   在事件发布完成后与直接注册的 builder 一起处理"——未被 ADR 讨论。当前实现里
   Block 预创建 `b.item` 子 builder 实质就是这个语义（子 builder 经
   handleAdditionalObjects 投递），但"子 builder 列表作为一等 API 暴露给脚本"
   这条路没有记录。低优先级：现有回调式已覆盖需求，留档备查。
4. **§3.3 不可变集合约定**：`viewProviders()` 返回 `Collections.unmodifiableMap`。
   未单独立 ADR，但已被实现吸收（当前各产物 record 均 `Map.copyOf` / `List.copyOf`）。
   可视为 ADR-0001"finish 后置空累积器、产物不可变"的子条款，无需补记。
5. **§4.1 脚本示例中 `event.register("my_custom_item", () -> ...)` 单参形态**：
   当前 `register` 的形态是 `event.register(registryName, id, supplier)` / 糖方法——
   PR 设想的**不带注册表名的裸 register**（靠闭包推断？）未采纳，当前脚本必须指定
   注册表。差异微小（PR 示例本身可能笔误），记录备查即可。

**结论**：MINECRAFT_REGISTRY.md 的设计价值已在 issue #38 的 RE1/RE2 研究与 ADR-0001~0006
中兑现完毕；残余 5 点中仅第 1 点（RegistryEventJS 零平台 import 约束显式化）与第 2 点
（工具/装甲 Builder backlog）有记录价值，其余已被覆盖或可忽略。

---

## 4. 总表

| PR 文件 | 档 | 对应 ADR | 现实现落点 |
|---|---|---|---|
| `NekoPluginExtensionPoint`（of 工厂 + onFinish） | ③ | 0001/0002 | V2 builder + finisher（过渡工厂已删） |
| `NekoPluginBootstrap`（onFinish 循环） | ③ | 0001/0002 | V2 引擎收集循环 |
| `RegistryInfo` | ② | 0004 | `wrapper/registry/gen/RegistryInfo`（token 化 + sugarName） |
| `RegistryInfos`（反射扫描 + 静态单例） | ②（扫描逻辑）/③（单例） | 0004 | `RegistryInfosPoint.scan()` finisher |
| `RegistryObjectBuilder` | ② | 0005 | `gen/RegistryObjectBuilder`（+Supplier +连带钩子） |
| `RegistryObjectType`（四元组） | ② | 0004/0005 | `gen/RegistryObjectType`（三元组，类型名全局唯一） |
| `RegistryObjectTypeRegistry`（Scope） | ②（类型注册意图）/③（Scope） | 0004 | `RegistryTypesPoint`（overrideWarn Collector） |
| `RegistryObjectTypes`（静态产物） | ③ | 0004 | Point 产物 + `RegistryRepository` |
| `RegistryEventJS` | ② | 0004/0006 | `gen/RegistryEventJS`（ProxyObject + 糖方法派生） |
| `RegistrySupportPlugin` | ③ | 0001/0003 | `XxxPoint.Contributor` 自包含接口 |
| `RegistryInfosPlugin`（onFinish 串联） | ③ | 0002/0003 | `NekoRegistryPointsPlugin`（dependsOn） |
| `RegistryInfosRegistry`（收集器） | ② | 0001 | `RegistryInfosPoint.Collector`（三段式） |
| `BuiltinRegistrySupport` | ② | 0003/0004 | `NekoRegistryPointsPlugin` + 固定扫描根 + 内置类型清单 |
| `impl/ItemBuilderJS` | ② | 0005 | `gen/ItemBuilder`（字段面基本一致） |
| `docs/MINECRAFT_REGISTRY.md` | ②（§2.2 已进 ADR）/存档 | — | RE1/RE2 研究输入 + 本文 §3 残余点 |

**给合并决策的一句话**：PR #37 不应以原样合并——它的 914 行里有价值的是**设计意图**
（已全部进 ADR 并以更完整的形态落地）与**文档**（本文 §2/§3 已榨干），
代码本体与当前树的接缝（布局、扩展点 API、Scope/单例语义）全面失配。
建议：保持 PR 开放状态作为历史存档，或以"设计已吸收、按 ADR 重实现"为由关闭；
若作者（ZZZank）希望继续参与，判据③真人测试（或 gen 包 Builder 的工具/装甲变体扩展，
见 §3-2）是现成的接入点。
