# NekoJS 全版本统一 JS API 与版本专属 API 设计

> 状态：设计已确认（2026-07-26）
>
> 目标：建立一套真正跨 Minecraft 1.12.2、1.21.1、26.1、26.2 的稳定 JS API，同时为 loader 和具体 Minecraft 版本保留显式、可识别、可演进的专属 API。
>
> 范围：脚本 API 分层、稳定对象模型、平台桥、模块命名、能力模型、Probe/catalog、兼容治理、错误语义、测试门禁和迁移路线。
>
> 关系：本文取代 `long-term-api-design.md` 中“直接冻结当前 bindings/events”的决定，也取代 `plan-v2.md` 中“直接让 Probe 使用现有 MemberVisibilityQuery”的实现前提。旧文档中的注解、物理模块拆分和 feature backlog 仍可复用，但必须按本文的 canonical surface 与 manifest 架构重新编排。
>
> 现状调查基线：主仓库提交 `9895375`。后续源码变化若与本文“当前架构事实”冲突，必须先更新事实基线和 current-surface dump，不能静默沿用旧结论。

---

## 1. 决策摘要

| 主题 | 决策 |
|---|---|
| 总体路线 | Facade-first 分层架构 |
| 统一层门槛 | 只收录所有支持平台都能提供相同语义的能力 |
| 缺少原生 hook | 能用事件桥、Mixin 或 Coremod 可靠补齐时，仍属于统一层 |
| JS 入口 | 高频稳定 API 使用全局；feature、平台和版本能力使用显式模块 |
| 对象模型 | 平台中立值对象 + 窄接口实时 Facade |
| 原生对象访问 | stable Facade 的 unwrap 只能通过匹配 loader + MC 的 version module |
| 版本专属粒度 | loader 平台层 + Minecraft 版本层 |
| 稳定版本 | `apiVersion` 独立于 `nekojsVersion` |
| 首次冻结范围 | 核心脚本工作流，不冻结全部现有 API |
| 旧 preview API | 允许在 1.1.0 前破坏式整理，不冻结错误边界 |
| 平台能力 | 稳定字符串 ID；`NATIVE`、`EMULATED`、`UNAVAILABLE` 三态 |
| 规范性 API 真源 | 人工审阅并提交的 `NormativeApiContract` |
| 运行时公开面真源 | 从 normative contract + 注册贡献解析出的 frozen canonical surface |
| 观测与工具输出 | 可序列化 `ApiManifest`，用于证明实现符合规范，不用于反向决定规范 |
| Probe | 从 canonical surface/manifest 生成，不再自行裸反射公开面 |
| 兼容门禁 | 四平台 stable manifest 一致性、API diff、原生类型泄漏检查、契约脚本和 `tsc` |

---

## 2. 当前架构事实

本节以当前源码为准，不以 Wiki 或既有规划中的超前描述为准。

### 2.1 构建和平台结构

根工程包含：

- `:common`
- `:platforms:neoforge-1.21.1`
- `:platforms:neoforge-26.1`
- `:platforms:neoforge-26.2`
- `:platforms:cleanroom-1.12.2`

26.1 和 26.2 额外吸收 `platforms/neoforge-26-shared` 源码。1.21.1 和 Cleanroom 各自维护平台实现。当前平台构建同时使用 `srcDir common` 与 `implementation project(':common')`，形成双重装配边界，后续必须消除。

NekoJS 是编译期平台选择架构，不是一个 jar 在运行时按版本字符串切换实现。这个方向是正确的，应继续保留：

- 版本选择由 Gradle 源集、loader 入口和平台实现决定。
- `Platform.getMcVersion()` 只用于元数据和脚本判断。
- 业务实现不得通过 `getMcVersionInt()` 堆叠版本分支。

### 2.2 已经存在的可复用基础

当前系统已有以下正确接缝：

- `NekoJSPlugin` 的 typed hooks 和 bootstrap/freeze 生命周期。
- `Binding`、`JSTypeAdapter`、`EventGroup`、recipe schema/namespace 注册机制。
- `NekoId` 与 `NekoIdCompat` 的平台中立 ID 方向。
- `NekoScriptCatalog.snapshot(...)` 的运行时 API 收集入口。
- `EventBusJS` 的事件订阅、dispatch key、取消和 reload listener 清理内核。
- recipe 的 handler、schema、raw JSON 分发模型。
- adapter 输入形状同时服务运行时转换和 Probe 的思路。
- 26.1/26.2 的 shared 源集粒度。

这些机制无需推倒重来，但它们目前只提供“注册和收集能力”，尚未形成稳定脚本契约。

### 2.3 当前 JS API 的核心问题

#### 2.3.1 common 隔离了源码依赖，但没有隔离 JS 语义

`common/src/main/java` 当前基本不直接 import Minecraft/Forge/NeoForge 类型，这是好的源码边界。然而脚本公开面仍大量包含平台原生对象：

- 26.x 暴露 `Identifier`，1.21.1/Cleanroom 暴露 `ResourceLocation`。
- 现代平台事件多数把原生 NeoForge event 直接传给 JS。
- Cleanroom 事件名称、参数、取消语义和事件数量与现代平台不同。
- registry builder、NBT、实体类型、配方、network payload 继续使用具体版本类型。
- `ID.platform()` 直接返回平台对象，静态类型只能退化为 `Object`。
- `NativeEvents`、`ScriptEvents`、`java:` 和原生 class globals 进一步扩大了版本依赖。

因此当前只能做到“某些全局名字相似”，不能保证同一脚本签名和行为跨版本稳定。

#### 2.3.2 平台能力目前是广告，不是契约

当前 `PlatformCapability` 已有 23 个枚举值，但生产逻辑基本不消费它：

- binding、event 和 module 是否注册不由 capability 驱动。
- Probe 不从 capability 推导声明可用性。
- capability 与实际实现存在漂移，例如已实现 tags 的平台未必声明 `TAGS`。
- 一个布尔能力无法表达“服务端已刷新，但客户端 recipe viewer 仍陈旧”之类的细分语义。

#### 2.3.3 runtime、preflight、Probe 不是同一公开面

当前至少存在三套成员解析：

- Graal runtime 使用 remapper 和 HostAccess。
- preflight 使用自己的成员索引。
- Probe 的 `ClassDeclGenerator` 直接反射 declared public members。

现有 `MemberVisibilityQuery` 也不能直接成为答案，因为它会折叠 overload，并且收集范围和实际 HostAccess 不完全一致。结果包括：

- `@HideFromJS`/`@Remap` 在 runtime、校验和 `.d.ts` 中可能不一致。
- 内部 public helper 可能意外成为 API。
- host extension runtime 可见，但 Probe 不一定生成。
- 可取消事件 runtime 接受取消结果，Probe 却生成 `void` callback。
- Cleanroom Probe 可能出现现代 `CompoundTag`、`FluidIngredient` 或现代 builder 声明。

#### 2.3.4 catalog 还不是稳定数据契约

`NekoScriptCatalogSnapshot` 当前包含 `Class<?>`、`Method`、`Path`、predicate 等运行时对象，不可作为跨工具、跨版本的 wire format。它也缺少：

- `apiVersion`
- catalog schema version
- stable symbol ID
- owner/origin
- stability tier
- since/deprecated/replacement
- required capabilities
- platform/loader/version/module 归属

#### 2.3.5 现有规划不能直接执行

`long-term-api-design.md` 和 `plan-v2.md` 的方向部分正确，但有三个必须修正的前提：

1. 当前 bindings/events 不能直接冻结，因为同名 API 背后不是同一类型和语义。
2. Probe 不能直接切换到现有 `MemberVisibilityQuery`，必须先建立保留 overload、匹配 HostAccess 的 canonical resolver。
3. 注解收集不能只扫描 plugin implementation class，必须扫描 catalog roots：binding value type、event payload、handler、host extension 和 descriptor 显式 roots。

---

## 3. 目标与非目标

### 3.1 目标

1. 同一份只使用 stable API 的脚本可在 1.12.2、1.21.1、26.1、26.2 上保持同名、同签名、同语义。
2. 没有原生 loader 事件但能通过 Mixin/Coremod 稳定补齐的能力，仍可进入统一层。
3. 原生 loader/MC 能力仍可使用，但依赖必须通过 import 明确显示。
4. runtime、preflight、Probe、文档、capability 和兼容测试共享同一 API 数据源。
5. `apiVersion` 的破坏性变化与 NekoJS 内部发布解耦。
6. 第三方 addon 只能依赖明确的 Java SPI，不依赖 `core.*`、Probe 实现或平台内部类。
7. 任何“未支持”都必须在 module link、runtime 调用或静态类型阶段明确失败，不允许静默近似或假成功。

### 3.2 非目标

- 不保证当前 preview 脚本零修改迁移到 API 1.0.0。
- 不让 stable API 模拟完整 Minecraft/Forge/NeoForge 类树。
- 不要求所有 feature 在所有平台存在。
- 不通过运行时版本字符串把所有平台源码合并成一个实现。
- 不冻结 `.d.ts` 文件布局细节，只冻结其表达的 stable symbol 和签名。
- 不把 `java:`、直接 `Java.type` 或 NativeEvents 视为跨版本稳定 API。

---

## 4. 四层脚本 API

### 4.1 第一层：全版本 stable 全局 API

用途：高频脚本工作流。所有支持平台必须提供完全一致的 JS 名称、签名、错误和核心行为。

规则：

- 参数和返回值只能是 JS 基础类型、稳定值对象或 stable Facade。
- 不得出现 `net.minecraft.*`、`net.minecraftforge.*`、`net.neoforged.*`、Graal `Value`。
- 不得要求脚本检查 MC/loader 版本才能完成该 API 的正常路径。
- 原生平台缺少 hook 时，只要能可靠注入相同语义，应由平台 bridge 补齐。
- 达不到同一语义时，不得以空方法、假 JSON、静默忽略方式留在 stable 层。

首批 stable 全局候选：

- `ID`
- `Platform`
- `Item`
- `Ingredient`
- `Fluid`
- `Text`
- `NBT`
- `JsonIO`
- `NbtIO`
- `Time`
- `Utils`
- `GlobalData`
- `Network`
- `ServerEvents`
- `PlayerEvents`
- `BlockEvents`
- `ItemEvents`
- `EntityEvents`

“候选”不是模糊承诺：每个 symbol 只有通过四平台 contract tests 并进入 API 1.0.0 baseline manifest 后才成为 stable。未通过者必须降到 feature/platform/version 层，不得带病冻结。

### 4.2 第二层：stable feature 模块

模块格式：

```text
@nekojs/feature/<feature-id>
```

示例：

```text
@nekojs/feature/recipe-json
@nekojs/feature/tags
@nekojs/feature/client-keybinds
@nekojs/feature/recipe-viewer
```

feature 模块的语义和签名稳定，但不是每个平台都必须提供。它适用于“功能本身平台中立，但底层 Minecraft 模型并非所有版本都有”的场景。

规则：

- 模块注册由 capability 决定。
- 同一 feature 在任何声明支持的平台上必须通过相同 contract tests。
- 不支持的平台不注册模块，静态 import 在 link 阶段明确失败。
- 跨平台脚本使用 `Platform.hasModule()` 或 `Platform.hasCapability()` 后动态 import。
- 不能为了让模块“看起来存在”而在不支持的平台注册只会抛错的空壳实现。

### 4.3 第三层：platform 模块

模块格式：

```text
@nekojs/platform/neoforge
@nekojs/platform/cleanroom
```

用途：loader 专属、但能用 stable Facade/JS 值表达签名的服务和第三方 mod 集成。

规则：

- 同一 loader 主线内遵循平台层 SemVer。
- 导出签名仍使用 stable Facade、JS 值或平台模块自己的稳定值对象。
- 不直接返回随 Minecraft 版本变化的 Mojang/loader host class。
- 不注册通用全局名。
- Probe 只在当前平台输出对应声明。
- API 文档必须标明它不是 portable stable API。
- 若操作必须接收或返回具体 MC/loader host object，则该操作属于 version module，而不是 platform module。

### 4.4 第四层：version 模块

模块格式：

```text
@nekojs/version/cleanroom-1.12.2
@nekojs/version/neoforge-1.21.1
@nekojs/version/neoforge-26.1
@nekojs/version/neoforge-26.2
```

用途：仅存在于一个 loader + MC 版本线的字段、类、临时兼容入口、Mojang API 和原生对象 unwrap。

规则：

- 只保证对应版本线。
- 升级到下一 MC 版本允许删除或重做。
- 不进入 stable API diff。
- 必须显式 import；禁止注入全局。
- 若后续证明多个版本共享同一语义，应上移到 platform 或 feature 层。
- 所有返回原生 `Player`、`Level`、event、registry、NBT/component 等 host object 的 API 都归入本层。

---

## 5. Java 依赖和模块边界

### 5.1 模块职责

#### `:common-api`

保存第三方 addon 和 stable script contract 可以依赖的 Java 类型：

- stable 值对象和 Facade 接口。
- `NekoJSPlugin` 与公开 registry/SPI 接口。
- `ScriptType`、binding/event/adapter/recipe 的公开契约。
- API lifecycle 注解。
- 可序列化 manifest DTO。
- stable error code 和异常接口。

约束：

- 不依赖 Minecraft、Forge、NeoForge、具体平台实现。
- 不依赖 Graal。公开 conversion SPI 使用 NekoJS 自己的 `JsValueView`、`ConversionContext` 和 `ConversionPrecedence`，由 runtime 将 Graal `Value`/HostAccess precedence 适配进去。
- public API 必须有明确 stability 标记。

当前 `JSTypeAdapter` 直接扩展 `Predicate<Value>`/`Function<Value,T>` 并暴露 `HostAccess.TargetMappingPrecedence`，不能原样迁入 `:common-api`。迁移时定义 Graal-free SPI：

```java
interface JsTypeAdapter<T> {
    Class<T> targetType();
    boolean supports(JsValueView value, ConversionContext context);
    T convert(JsValueView value, ConversionContext context);
    ConversionPrecedence precedence();
    List<AdapterInputShape> inputShapes();
}
```

`JsValueView` 只提供稳定的 JS 类型判断、属性读取、数组/标量访问和受控 host-object 查询；它不能暴露 Graal `Value`。preview 期间旧 adapter SPI 可以由 runtime 内部兼容桥承接，但不进入 `spiVersion 1.0.0` baseline。

#### `:common-runtime`

保存实现细节：

- Graal Context、HostAccess、sandbox、module loader。
- plugin bootstrap 和 frozen runtime registry。
- canonical `JsApiSurfaceResolver`。
- binding/event dispatch 实现。
- Probe、preflight、manifest emitter、API diff。
- common helper 的具体实现。
- `JsValueView` 到 Graal `Value`、`ConversionPrecedence` 到 HostAccess precedence 的适配。

这些 public Java 类对 addon 不是稳定 SPI，应标记 internal。

#### 平台和版本源集

保存：

- `PlatformApiProvider` 的领域 bridge 实现。
- loader 生命周期入口。
- 原生事件订阅、Mixin/Coremod、AT/interface injection。
- registry flush、payload 注册、pack finder、renderer 等必须在平台阶段执行的逻辑。
- platform module 的 loader integration 实现，以及 version module 的 native interop/unwrap 实现。

### 5.2 依赖方向

```text
platform/version implementation
        -> common-runtime
        -> common-api

addon
        -> common-api (compileOnly)
```

禁止：

- `common-api -> common-runtime`
- `common-api -> MC/loader/Graal implementation`
- stable Facade -> platform class
- addon SPI signature -> `core.*` 或 `probe.*`

### 5.3 平台桥拆分

`PlatformApiProvider` 是组合入口，不是巨型业务接口。它提供窄领域服务：

- `IdBridge`
- `ItemBridge`
- `IngredientBridge`
- `FluidBridge`
- `TextBridge`
- `NbtBridge`
- `EventBridge`
- `RecipeBridge`
- `NetworkBridge`
- `NativeInteropProvider`

统一 Facade 只依赖对应领域 bridge。平台可以复用 shared 实现，也可以只替换发生漂移的单个 bridge。

26.1/26.2 共用逻辑继续放 `neoforge-26-shared`；仅 26.1 的 API 放 26.1 源集，仅 26.2 的 API 放 26.2 源集。1.21.1 和 Cleanroom 分别实现相同 bridge contract。

---

## 6. Stable 对象模型

### 6.1 值对象

值对象可跨 tick、reload 和序列化边界保存：

- `NekoId`
- `Vec3Value`
- `BlockPosValue`
- `ColorValue`
- `TextValue`
- `NbtValue`
- `JsonValue`
- UUID 和其他 JDK 稳定值

规则：

- immutable 或具有明确 copy-on-write 语义。
- equality/hash/serialization 在所有平台一致。
- 不持有 world、entity、registry manager 或 Graal Context。
- 平台转换只发生在 bridge 内部。

### 6.2 实时 Facade

实时 Facade 是对当前游戏对象的受控视图：

- `PlayerRef`
- `EntityRef`
- `LivingEntityRef`
- `LevelRef`
- `ItemStackRef`
- `BlockRef`
- `ServerRef`

规则：

- 只暴露跨版本稳定成员。
- 可变操作使用明确方法，例如 `player.give(stack)`，而不是暴露原生字段。
- 提供 `isValid()` 或由调用操作抛 `INVALID_REFERENCE`。
- 不保证跨 tick/reload/world unload 有效。
- 需要长期保存时，脚本保存 UUID、ID、位置或 NBT/JSON 快照。
- stable Facade 不提供 `.native`、`.raw`、`.handle`。

### 6.3 原生对象解包

原生解包只存在于匹配 loader + MC 的 version 模块：

```javascript
if (Platform.hasModule('@nekojs/version/neoforge-26.1')) {
  const native = await import('@nekojs/version/neoforge-26.1')
  const rawPlayer = native.unwrapPlayer(player)
}
```

解包后的对象：

- 可以使用 Graal host interop。
- 类型只出现在当前 loader + MC version module 的 Probe 声明中。
- 不受 portable `apiVersion` 兼容保证。
- 不得重新注入 stable Facade 或 stable event payload 的声明。

platform module 可以提供使用 stable Facade 的 loader 专属操作，但不能承担原生对象解包。例如 `@nekojs/platform/neoforge` 可以提供 NeoForge-only integration service；`unwrapPlayer()` 必须位于当前 `@nekojs/version/neoforge-<mc>` module。

### 6.4 Runtime 强制公开面

canonical surface 必须是实际权限边界，不能只服务 Probe。stable global、stable value 和 stable Facade 一律以 registry-backed `ProxyObject`/`ProxyArray` 暴露：

- `ApiFacadeProxy` 的 member keys 只来自 `FrozenApiRegistry`。
- 可调用成员由 registry 中已解析的 invoker 生成 `ProxyExecutable`。
- 实现类额外的 public Java 方法不会被 Graal 看到。
- stable global 不直接绑定 Java class 或普通 host object。
- stable value 即使内部使用 Java record，也通过显式 proxy view 暴露。

`HostAccess.ALL` 只能服务 `UNSAFE_NATIVE`、version module 的显式原生对象，以及尚未完成迁移的 preview legacy 层。API 1.0.0 freeze 前，portable stable 路径必须全部迁出 raw HostAccess 暴露。长期可进一步收紧默认 HostAccess，但这不是建立 stable 权限边界的前置条件。

runtime、preflight 和 Probe 都读取同一个 `FrozenApiRegistry`：runtime 使用 invoker，preflight 使用 signature，Probe 使用 type/doc metadata。这样 manifest 未登记的 public helper 在 runtime 也不可访问。

该规则覆盖所有 contract-managed tier：portable stable globals、feature module、platform module 和 addon module。所有 module namespace 本身也必须是 registry-backed proxy。只有 version module 中被 descriptor 显式标记为 `nativeReturn=true` 的 export、显式 `UNSAFE_NATIVE` module，以及迁移期 legacy 层可以返回 raw host object。

每个 invoker 同时执行边界转换：

- 参数按 canonical input type/adapter shape 校验和转换。
- stable/feature/platform/addon 返回值按 canonical type graph 转换为值对象或 registry-backed proxy。
- callback payload 使用同样的 proxy/validation 路径。
- 未声明的 raw host return 立即抛 `NATIVE_TYPE_LEAK`；缺失 wrapper/invoker 抛 `API_CONTRACT_VIOLATION`。
- version module 的 raw return 必须同时满足 module tier 为 version、export 显式声明 `nativeReturn=true`、返回 host type 与当前 Probe type reference 一致。

### 6.5 `java:` 与 `Java.type`

`java:`/`Java.type` 是高级逃生口，不属于 portable API。它们应：

- 继续受 ClassFilter 和 sandbox 配置控制。
- 在文档和 manifest 中标为 `UNSAFE_NATIVE` tier。
- 不进入 stable API diff。
- 默认不被 stable contract scripts 使用。

---

## 7. 首个 API 1.0.0 冻结范围

### 7.1 基础工具

#### `ID`

- 始终收发 `NekoId` 或字符串。
- 不再提供 stable `ID.platform()`。
- namespace/path/string conversion 在所有版本一致。

#### `Platform`

- `nekojsVersion`
- `apiVersion`
- `catalogSchemaVersion`
- `minecraftVersion`
- `loaderId`
- `isClient`
- `isDevelopment`
- `hasCapability(id)`
- `capabilityState(id)`
- `hasModule(id)`

版本元数据用于诊断和显式专属分支，不用于 stable API 的正常执行路径。

#### `Text`、`NBT`、`JsonIO`、`NbtIO`、`Time`、`Utils`

- JS 签名与序列化行为统一。
- NBT 以 `NbtValue` 表达，不暴露 `CompoundTag`/`NBTTagCompound`。
- Text 以 stable component model 表达，不暴露具体版本 Component 类。
- IO 路径统一受 NekoJS sandbox root 约束。

### 7.2 Item、Ingredient、Fluid

stable 只冻结所有平台可等价实现的操作：

- 通过 ID 创建。
- count/copy/empty/matches。
- 通用 OR ingredient 组合。
- 可稳定表达的 item/fluid stack 属性。
- ID 和显示数据的稳定查询。

以下内容不直接进入 stable：

- DataComponents 具体类型。
- 1.12 capability/NBT 与现代 component patch 的底层表示。
- 原生 Item、Fluid、Ingredient 类。
- 只在部分版本存在的 tag/fluid ingredient 语义。

这些能力按是否具备稳定跨支持平台语义进入 feature 模块；loader 专属但仍使用稳定类型的操作进入 platform 模块；必须出现原生 host 类型的操作进入 version 模块。

### 7.3 事件

首版冻结事件组：

- `ServerEvents`
- `PlayerEvents`
- `BlockEvents`
- `ItemEvents`
- `EntityEvents`

事件 symbol 的准入标准：

1. 四个平台都能在等价生命周期点触发。
2. payload 能由 stable Facade 完整表达核心语义。
3. 取消、优先级和 receive-cancelled 语义一致。
4. reload 后不会保留旧 Context listener。
5. 四平台 contract script 对触发次数、顺序和可变效果的断言一致。

缺少原生事件不自动导致降级。例如 inventory change、random tick、block entity tick 若能通过 Mixin/Coremod 可靠注入并满足上述标准，可进入 stable。

事件取消统一为显式对象方法：

```javascript
PlayerEvents.hurt(event => {
  if (event.player.hasTag('nekojs:immune')) {
    event.cancel()
  }
})
```

stable callback 不再使用“返回 `true` 即取消”的隐式约定。`event.cancel()`、`event.isCancelled()` 和 `event.cancellable` 构成统一契约。

### 7.4 配方

stable 共同面只包含四平台能真正执行的操作：

- `ids()`
- `count(filter?)`
- `exists(idOrFilter)`
- `remove(idOrFilter)`
- shaped
- shapeless
- smelting

最终进入 1.0.0 baseline 的具体 overload 由四平台 contract manifest 验证后确定。

现代 JSON/codec/schema 能力进入：

```text
@nekojs/feature/recipe-json
```

Cleanroom 不再注册只修改内存 JSON map、却不改变实际 `IRecipe` registry 的假实现。若某操作只能在 legacy registry 上以不同语义完成，应放入 `@nekojs/platform/cleanroom`，而不是伪装为 stable/feature 等价实现。

### 7.5 Network

stable 协议：

- channel 使用稳定字符串或 `NekoId`。
- payload 使用 `NbtValue`/`JsonValue`。
- 方向统一为 client-to-server、server-to-player、server-to-all。
- 接收事件使用 stable `PlayerRef` 和 payload value。
- reload 时 handler 生命周期可预测且无旧 Context 泄漏。

底层 NeoForge custom payload、Forge `SimpleNetworkWrapper` 和版本 packet 类型完全隐藏在 `NetworkBridge` 后。

### 7.6 Runtime Contract

ESM/CJS、TypeScript、JSX、Node shim、timers、Buffer、process 等属于 Runtime Contract，不与游戏 API `apiVersion` 混为一谈。Runtime Contract 具有独立版本字段，并在 manifest 中记录，以便：

- 游戏 API 可保持 1.x，编译器/runtime 独立升级。
- Node shim 缺失 API 不被误认为某个 MC 平台能力。
- Probe、错误诊断和发布工具可以分别判断 game API、Java SPI 与 runtime contract 的兼容性。

---

## 8. Capability 契约

### 8.1 标识和状态

能力使用稳定字符串 ID，而不是只依赖不断扩大的 Java enum：

```text
recipe.json
recipe.server_reload
recipe.viewer_refresh
tags.item
tags.fluid
client.keybinds
client.screens
registry.block_variants
native.neoforge
native.cleanroom
```

Java 可提供常量包装，但 manifest 和 JS 使用字符串 ID。

状态：

- `NATIVE`：底层平台原生提供，满足完整契约。
- `EMULATED`：通过 Mixin、Coremod 或 bridge 补齐，满足同一完整契约。
- `UNAVAILABLE`：无法满足契约，不注册对应模块。

不提供 `PARTIAL`。当能力看起来部分支持时，应拆成更细的 capability。例如：

- `recipe.server_reload = NATIVE`
- `recipe.viewer_refresh = UNAVAILABLE`

### 8.2 capability 与实现一致性

capability 不能由平台手写集合单独宣称，也不能在 bootstrap 时运行完整游戏行为测试。它采用“规范定义 + provider 贡献 + 启动期结构验证 + CI 行为认证”的无环流程：

```text
CapabilityDefinition（规范中的契约 ID/版本/最大作用域/必需 service keys）
  -> ProviderContribution（实现、NATIVE/EMULATED 声明、环境前提）
  -> 启动期结构验证（services、依赖、scope、provider 完整性）
  -> 环境解析（dist、ScriptType、loader/MC、可选 mod）
  -> capability activation
  -> module activation
  -> runtime Platform 查询
  -> Probe/manifest 输出
```

完整行为 contract suite 在 CI/发布阶段执行，并以 `contractSuiteId + contractVersion` 记录认证关系；bootstrap 不伪装成行为测试。核心 provider 未通过对应行为认证不得进入发布 baseline。

每个 `CapabilityDefinition` 明确定义：

- `id`
- `contractVersion`
- `requiredServiceKeys`
- `providerPolicy`（`CORE_ONLY` 或 `ALLOWLIST`）
- `allowedProviderOwners`（`CORE_ONLY` 时必须为空；`ALLOWLIST` 时必须非空）
- `scriptTypes`
- `dist`（CLIENT、DEDICATED_SERVER、BOTH）
- `requiredMods`
- `loaderRange`
- `minecraftRange`
- `contractSuiteId`

每个 `ProviderContribution` 明确定义：

- `owner`
- `capabilityRef`（`id + contractVersion`，必须与当前 runtime 中该 ID 的唯一 definition 精确匹配）
- `implementationMode`（NATIVE 或 EMULATED）
- `implementation`
- `scriptTypes`
- `dist`
- `requiredMods`
- `loaderRange`
- `minecraftRange`
- `services`（service key 到实现对象的映射）

同一个 runtime descriptor universe 中，每个 capability ID 只能存在一个 `CapabilityDefinition` 和一个 `contractVersion`；重复 ID 即使版本不同也属于结构冲突。module 的 `requiredCapabilities` 使用 `id + contractVersionRange`，其 SemVer range 必须接受当前 definition 的版本。

`requiredServiceKeys` 指 provider contribution 必须实现的内部 bridge/service slot，不是 JS module export。JS module export 由 module descriptor 和 symbol contribution 单独验证，因此 capability activation 不依赖尚未激活的 module。

确定性解析算法：

1. 收集尚未激活的 capability definitions、provider contributions、module descriptors 和 symbol contributions。
2. 先验证 owner、namespace、tier 和 descriptor 双向授权，不执行任何环境激活。
3. 验证 provider 的 `services` 覆盖 definition 的 `requiredServiceKeys`。
4. provider 的 scriptTypes、dist、requiredMods、loaderRange、minecraftRange 组合成 environment predicate；该 predicate 对应的允许环境集合必须是 definition 允许环境集合的子集。这里按语义集合判断：增加 `requiredMods` 会缩小环境集合，不能机械地对字段做同方向集合比较。越界直接报 contract violation，不能取并集扩大能力。
5. 对当前 environment 过滤 eligible providers。同一 capability 在同一 environment 必须恰好有一个 eligible provider；零个表示 `UNAVAILABLE`，多个表示 `DUPLICATE_CAPABILITY_PROVIDER` 并 fail-fast。允许多个 provider 只在它们的作用域互不重叠时成立。
6. capability activation 针对单个 `EnvironmentKey(scriptType, dist, loader, minecraft, installedMods)` 独立计算；active scope 就是该 EnvironmentKey 下选中的唯一 provider，不对多个 provider scope 求并集。
7. 先按 module 自身的 environment predicate 过滤 descriptor。predicate 不匹配是正常 inactive，不是错误。
8. module 所需 capability 为 `UNAVAILABLE` 或 contractVersionRange 不匹配时，该 module 正常 inactive，并记录 `CAPABILITY_UNAVAILABLE` 诊断，供 static import 错误使用；不会导致全局 bootstrap 失败。
9. 在未按环境过滤的完整 descriptor universe 上验证 dependency schema 和依赖环。格式错误或依赖环属于 descriptor contract violation 并 fail-fast。
10. 对当前 EnvironmentKey 解析依赖：目标 descriptor 不存在时 module inactive，原因 `MISSING_MODULE_DEPENDENCY`；目标存在但环境不活跃时 module inactive，原因 `DEPENDENCY_INACTIVE`；版本/revision 不匹配时 module inactive，原因 `MODULE_VERSION_MISMATCH`。这些情况不使其他无关 module 失败。
11. 在 dependency 指向 dependent 的 DAG 上按拓扑顺序计算 active 状态：module 只有在自身仍是 candidate 且全部 required dependencies 已 active 时才 active；任何 dependency inactive 都使 dependent 标记为 `DEPENDENCY_INACTIVE`。由于依赖先于依赖者处理，该规则自动传播到全部传递依赖闭包，直到所有节点都有最终状态，不需要实现自行选择一次裁剪或固定点。
12. 同一批 ready nodes 按 module ID 的 Unicode code-point 升序处理，得到确定性 active module/export 顺序。

核心 capability 默认 `providerPolicy = CORE_ONLY`。只有 `CapabilityDefinition` 显式列出 `allowedProviderOwners` 时，指定 addon 才能提供 core capability；不允许任意 addon 抢占 core provider。

### 8.3 不可用行为

- feature 模块未注册：ESM link 阶段抛 `UNSUPPORTED_MODULE`。
- 动态查询 capability：返回 `UNAVAILABLE`，不抛错。
- stable API 内部依赖失配：抛 `API_CONTRACT_VIOLATION`，视为 NekoJS/平台实现 bug。
- 不允许 NPE、`UnsupportedOperationException` stub、静默跳过或假成功。

同一物理客户端进程中的 STARTUP/SERVER/CLIENT/TEST context 可以得到不同 module 集合；专用服务端不得输出或注册 client-only module。安装/卸载可选 mod 会改变 environment manifest hash，并触发 Probe staleness 检测。

---

## 9. Canonical JS Surface 与 ApiManifest

### 9.1 规范性契约与观测 manifest

`ApiManifest` 是实现观测结果，不能自行决定产品 API。规范性输入是人工审阅、提交并参与 code review 的 `NormativeApiContract`：

```text
api-contracts/
  portable/core-1.0.0.json
  features/<feature-id>/<contract-version>.json
  platforms/<loader>/<contract-version>.json
  spi/<spi-version>.json

<addon-jar>!/META-INF/nekojs/api-contract.json
```

每个 domain 在进入实现计划前必须先提交对应 contract section，精确定义：

- global/module/member 的 JS 名和 stable symbol ID。
- 每个 overload 的参数名、输入形状、返回类型和错误。
- event payload 字段、生命周期、取消、优先级和触发断言。
- Facade 有效期和可变操作。
- capability/module scope。
- 跨平台行为 contract cases。

domain contract 先通过用户/维护者设计审查，再允许平台实现。实现生成的 `ApiManifest` 与 normative contract 比较；四个平台“实现得完全一致”仍不足够，必须一致且符合 normative contract。

本文是 program-level architecture spec，不试图在一个文件里冻结所有领域的精确 overload。ID/Text/NBT、Item/Ingredient/Fluid、事件、配方、Network 各自必须先形成独立 domain contract spec 和 machine-readable contract，然后才进入对应实施计划。这是明确的阶段 gate，不是由实现结果后补设计。

### 9.2 注册数据流

```text
NormativeApiContract
                +
内置插件 / addon / platform provider
                |
                v
带 owner、tier、module、signature metadata 的贡献描述
                |
                v
JsApiSurfaceResolver
  - 实际 public/HostAccess 过滤
  - Hide/Remap 规则
  - overload 保留
  - adapter 输入形状
  - docs/since/deprecated/type refs
  - 冲突检测
                |
                v
FrozenApiRegistry + contract conformance result
  |             |             |              |
runtime       preflight      Probe         ApiManifest
```

runtime、preflight 和 Probe 不能再独立推导公开面。`ApiManifest` 从 frozen registry 导出并附带 contract conformance 结果。

### 9.3 `JsApiSurfaceResolver`

resolver 必须：

- 只接受 catalog roots 和显式 descriptor roots。
- 按实际 HostAccess 规则过滤 public 成员。
- 过滤 synthetic、bridge、内部 helper。
- 应用 `@HideFromJS`、`@Remap`、`@RemapByPrefix`。
- 保留完整 overload set，不按 JS 名压成单个 Method。
- 将 adapter 输入形状并入 TS 参数类型。
- 使用结构化 type reference：`module + symbol`，不能只存一个自由字符串。
- 为每个 symbol 生成确定性 stable ID。
- 检查 duplicate ID、duplicate JS signature、owner 冲突和非法 tier 引用。
- 为所有 contract-managed global/module/type 生成 runtime proxy descriptor 和 invoker，确保 surface 同时约束 HostAccess。
- 对 normative contract 中存在但实现缺失的 symbol fail-fast。
- core contribution 未被对应 core contract/tier 授权时一律 fail-fast。
- addon contribution 必须被 addon-owned descriptor 和 addon contract 双向授权，否则 fail-fast。
- `UNSAFE_NATIVE` contribution 必须在 descriptor 中显式声明该 tier，不能由 resolver 自动降级。

### 9.4 stable symbol ID

示例：

```text
global:Item
global:Platform
event:PlayerEvents.hurt
type:nekojs.api.PlayerRef
member:nekojs.api.PlayerRef.give
module:@nekojs/feature/recipe-json
module-member:@nekojs/version/neoforge-26.1.unwrapPlayer
```

member/module-member symbol ID 不包含参数；同一 JS 成员的 overload 由该 symbol 下的 signature compatibility keys 区分。参数列表相同而仅返回类型不同的两个签名不是合法 JS overload，resolver 必须拒绝。

symbol ID 用于：

- API diff。
- docs/since/deprecated 绑定。
- descriptor provides 校验。
- Probe symbol import。
- replacement 存在性检查。
- 四平台 stable subset 比较。

### 9.5 可序列化 `ApiManifest`

manifest 不得包含 `Class<?>`、`Method`、`Path`、Graal `Value` 或 Java lambda。至少包含：

```json
{
  "catalogSchemaVersion": 1,
  "apiVersion": "1.0.0",
  "spiVersion": "1.0.0",
  "runtimeContractVersion": "1.0.0",
  "nekojsVersion": "1.1.0-preview1",
  "platform": {
    "loader": "neoforge",
    "minecraft": "26.1"
  },
  "capabilities": [],
  "modules": [],
  "symbols": []
}
```

每个 symbol 记录：

- `id`
- `kind`
- `jsName`
- `tier`
- `module`
- `owner`
- `scriptTypes`
- `signature`
- `typeRefs`
- `since`
- `deprecatedSince`
- `replacement`
- `requiredCapabilities`
- `documentation`

module descriptor 使用 `tier` 作为 discriminator。schema tier 枚举固定为：

```text
PORTABLE_STABLE
FEATURE
PLATFORM
VERSION
ADDON
UNSAFE_NATIVE
LEGACY_PREVIEW
```

`PORTABLE_STABLE` 用于全局/类型 symbol 归类，不创建可导入 module；`LEGACY_PREVIEW` 只在迁移期存在，不能进入任何 baseline。所有可导入 module 共有字段：

- `id`
- `owner`
- `tier`
- `exports`（stable symbol ID 列表）
- `requiredCapabilities`（`id + contractVersionRange`）
- `dependencies`（使用下述按目标 tier 判别的 union）
- `scriptTypes`
- `dist`
- `requiredMods`
- `loaderRange`
- `minecraftRange`

按 tier 增加互斥字段：

- feature/platform/addon module：必须有 SemVer `contractVersion`；禁止 `moduleRevision`。
- version module：必须有正整数 `moduleRevision`、精确 loader ID 和精确 MC compatibility line；禁止 `contractVersion`。
- `UNSAFE_NATIVE` module：必须有正整数 `moduleRevision`；禁止进入任何 stable/module baseline，只进入 environment manifest。

version/unsafe export 若返回 raw host object，symbol descriptor 必须显式记录 `nativeReturn=true`。其他 tier 禁止该字段为 true。

dependency 使用目标 tier 判别的 union schema：

- portable stable：`target = "PORTABLE_STABLE" + apiVersionRange`。
- feature/platform/addon：`target moduleId + targetTier + contractVersionRange`。
- version：`target moduleId + target = "VERSION" + moduleRevision`，revision 必须精确匹配，不使用范围。
- `UNSAFE_NATIVE`：`target moduleId + target = "UNSAFE_NATIVE" + moduleRevision`，revision 必须精确匹配。

portable stable 不是 module，因此它只作为 API 版本前提，不参与 module DAG 节点。DAG 只包含真实可导入 module ID。

每个 active capability record 精确定义：

- `id`
- `contractVersion`
- `state`
- `providerOwner`
- `implementationMode`
- `contractSuiteId`
- `scriptTypes`
- `dist`
- `requiredMods`
- `loaderRange`
- `minecraftRange`

规范和观测输出四类 hash，职责不能混用：

- `portableContractHash`：人工 normative portable core contract 的 canonical hash，所有平台发布物相同。
- `portableSurfaceHash`：当前实现的 core-owned portable stable observed surface hash；必须符合 `portableContractHash` 对应的规范，并在四平台相同。
- `moduleContractHash[moduleId]`：每个 feature/platform/addon module 自己的 normative contract hash；不要求不同平台的 module 集合相同，只比较同 module ID + contractVersion。
- `environmentSurfaceHash`：当前 loader、MC、dist、ScriptType、可选 mod、addon、active capability/module/symbol 的 observed hash，用于 Probe staleness，不用于四平台 parity。

hash 使用 UTF-8 canonical JSON：object key 排序，symbols/exports/capabilities/modules 按 stable ID 排序；排除文档文本、时间戳、绝对路径和构建机器信息。portable/module compatibility hash 只包含兼容性显著字段；environment hash 额外包含平台元数据、激活 scope、addon 版本和可选 mod 集合。

### 9.6 addon 贡献治理

- `nekojs-core` 独占 portable `stable` tier 和 `@nekojs/*` module namespace。
- addon 不得向 core stable globals 追加成员，也不得覆盖 core symbol。
- addon module 使用 `@<modid>/<module>` namespace，tier 为 `addon`。
- addon capability ID 必须使用 `<modid>:<capability>` namespace；不得定义 core 保留的无命名空间 capability ID。
- addon 可以声明自己的 contract version、namespaced capability 和 module dependencies，但不推动 NekoJS `apiVersion`。
- addon 不得提供 core capability，除非 core `CapabilityDefinition.allowedProviderOwners` 明确列出该 addon owner。
- addon-owned symbol 不进入四平台 core parity 比较或 NekoJS core API baseline。
- addon descriptor 声明的 exports 必须与实际注册双向一致。
- addon 安装集合变化只改变 `environmentSurfaceHash`，不改变 portable contract/surface hash。

addon normative contract 的载体固定为 jar 资源 `META-INF/nekojs/api-contract.json`。`@RegisterNekoJSPlugin`/plugin descriptor 记录 `ownerModId`、`contractResource`（默认该路径）和 `contractHash`。逻辑身份键为：

```text
(ownerModId, moduleId, contractVersion)
```

加载顺序：

1. loader 发现 plugin descriptor。
2. bootstrap 在调用 addon 注册 hook 前读取 contract resource，并验证 hash、owner namespace 和 schema。
3. contract 中的 module/symbol/capability definitions 成为 resolver 的 normative addon 输入。
4. 再执行 addon 注册 hook，收集实际 contributions。
5. descriptor exports、addon contract 和实际 contribution 三方双向一致后，才能进入 frozen registry。

缺少、hash 不符或 owner/module identity 不一致时，该 addon 的 contract-managed API 注册失败；不能自动降为 `LEGACY_PREVIEW` 或 `UNSAFE_NATIVE`。具体 loader 如何定位 jar resource 可由平台实现，但 resolver 接收的输入必须是已验证的 `AddonApiContract`。

NekoJS 自带的可选 mod integration 仍以 owner `nekojs-core` 注册 feature/platform module，因此纳入对应 module contract baseline；它不因目标 mod 未安装而污染当前 environment manifest。

### 9.7 注解和 programmatic docs

注解与 programmatic registry 最终都转换为 surface metadata。建议保留并修正旧设计中的：

- `@Doc`
- `@Param`
- `@Return`
- `@Example`
- `@Since`
- `@DeprecatedNekojs`

`@TypeOverride` 不应只保存 FQN 字符串，应改成结构化 `module + symbol`，或者引用 stable catalog symbol ID。

注解 collector 扫描 catalog roots，而不是只扫描 plugin class。

---

## 10. Probe、静态校验与文档

### 10.1 Probe 输出层次

Probe 生成：

1. portable stable globals 和 Facade 声明。
2. 当前已注册 feature module 声明。
3. 当前 platform module 声明。
4. 当前 version module 声明。
5. runtime contract 声明。
6. `api-manifest.json`。
7. manifest hash/staleness metadata。

### 10.2 声明正确性

- 可取消事件必须生成 `event.cancel()`，不再只生成 `void` callback 却依赖 boolean runtime 约定。
- host extension 只有进入 surface 的 stable 成员才进入 stable `.d.ts`。
- addon 包不受硬编码 `com.tkisor.nekojs` relevance prefix 限制；roots 由 descriptor/catalog 决定。
- recipe overload、参数名和 adapter input shapes 来自 surface，不依赖“参数最多的方法”猜测。
- 原生 host 类型只能进入匹配 loader + MC 的 version module 或 `UNSAFE_NATIVE` module；feature、platform、addon module 的公开类型图仍必须使用受管值对象/Facade。

### 10.3 文档生成

手写 Wiki 负责概念、教程和迁移指南。以下表格由 manifest 生成或在 CI 中对照 manifest 校验：

- stable globals/API 表。
- event 列表和 payload。
- feature/platform/version module 表。
- capability matrix。
- since/deprecated/replacement。
- loader/MC 可用性。

这样避免 Wiki 再次描述尚未实现的注解、descriptor 或 API。

---

## 11. 版本与兼容治理

### 11.1 版本轴

#### `nekojsVersion`

NekoJS 实现和发布版本。可以包含 preview/rc。

#### `apiVersion`

portable stable JS API 的版本：

- PATCH：文档和行为 bugfix，不变更签名。
- MINOR：新增 symbol、overload 或可选字段；不破坏 1.x。
- MAJOR：允许删除、重命名或改变 stable 语义。

#### `spiVersion`

公开 Java addon SPI 的 source/ABI 契约版本。它与 JS `apiVersion` 分离：

- baseline 来自 `api-contracts/spi/<version>.json` 和构建出的 `common-api` jar。
- CI 使用 Java API/ABI diff 工具检查 public/protected type、method、field、generic signature 和 annotation contract。
- `spiVersion` major 才允许删除或改变 addon SPI。
- `JSTypeAdapter` 的 Graal-free 重设计完成后才建立 `spiVersion 1.0.0` baseline。

#### module `contractVersion`

每个 feature/platform module 自带 SemVer `contractVersion` 和独立 baseline：

- feature module 的 contract 变化不自动提升 portable `apiVersion`，除非它同时修改 portable stable symbol。
- platform module 在同一 loader compatibility line 内按自己的 contract SemVer 演进。
- module import 由当前环境 manifest 解析到该环境唯一版本；版本范围用于 module descriptor 的依赖校验。
- version module 以 loader + MC 版本作为兼容线，只记录正整数 `moduleRevision` 和 `nekojsVersion`，schema 中禁止 `contractVersion`，不承诺跨 MC 的 SemVer。

#### `catalogSchemaVersion`

manifest wire format 的单调整数版本。只在 JSON schema 不兼容时提升，与 JS API 是否变化无关。

Runtime Contract 另有独立 SemVer 字段。

### 11.2 tier 兼容承诺

| tier | 兼容承诺 |
|---|---|
| stable | 同一 API major 内不破坏；删除只能进入下一 API major |
| feature | 按该 module 的 `contractVersion` 遵循 SemVer；可用性由 capability 决定 |
| platform | 同一 loader compatibility line 内按 module `contractVersion` 遵循 SemVer |
| version | 只保证明确 MC 版本线；跨版本可重做 |
| addon | 按 addon 自己的 module `contractVersion` 治理；不影响 NekoJS core `apiVersion` |
| `UNSAFE_NATIVE` | 不提供 portable 兼容承诺 |

### 11.3 弃用

- stable symbol 弃用至少保留一个 API minor。
- manifest 记录 `deprecatedSince` 和 `replacement`。
- replacement 必须指向真实存在、tier 不低于原 symbol 的 stable ID。
- `.d.ts` 输出 `@deprecated`。
- runtime 可以按配置输出一次性弃用警告。
- 删除只允许在下一 API major。

### 11.4 API diff

CI 按 tier 选择对应 baseline 执行 normalized diff：

- 删除 stable symbol：breaking。
- 改 JS 名、script type、参数、返回类型、事件取消语义：breaking。
- 收窄 adapter input shape：breaking。
- 新增 optional overload/symbol：additive。
- 文档修正：non-breaking。
- platform/version symbol 不影响 portable baseline，但受各自 tier 规则检查。
- Java SPI 由 `spiVersion` 的 source/ABI baseline 单独检查，不混入 JS symbol diff。
- addon-owned symbol 只与 addon 自己声明的 baseline 比较，不进入 NekoJS core diff。

---

## 12. 错误和生命周期语义

### 12.1 稳定错误代码

至少定义：

- `UNSUPPORTED_CAPABILITY`
- `UNSUPPORTED_MODULE`
- `INVALID_REFERENCE`
- `API_CONTRACT_VIOLATION`
- `DUPLICATE_API_SYMBOL`
- `DUPLICATE_CAPABILITY_PROVIDER`
- `NATIVE_TYPE_LEAK`
- `STALE_API_MANIFEST`

JS error 至少包含：

- `code`
- `message`
- `symbolId`（如适用）
- `requiredCapability`（如适用）
- `platform`
- `minecraftVersion`
- `replacement`（如适用）

### 12.2 Facade 生命周期

- event Facade 只在 callback 生命周期内有效。
- player/entity/level Facade 在底层对象卸载后失效。
- reload 关闭旧 Context 前，所有 binding/event/native listener 必须 unregister/close。
- `Binding.close(ScriptType)` 必须成为真实清理契约，不再只是预留点。
- NativeEvents 和 ScriptEvents 不得持有旧 Graal `Value`。
- 对失效 Facade 的操作抛 `INVALID_REFERENCE`，不能调用旧世界对象或 NPE。

### 12.3 contract violation

stable API 已注册但平台 bridge 缺失、签名不匹配或返回原生类型时，视为实现 bug：

- bootstrap fail-fast。
- 报 `API_CONTRACT_VIOLATION`。
- 错误指出 provider、symbol 和预期契约。
- 不允许把此类错误伪装成用户未开启 capability。

---

## 13. 测试和 CI 门禁

### 13.1 canonical surface 单测

同一 fixture 同时验证：

- runtime member resolution。
- preflight schema。
- Probe declaration。
- manifest symbol。

覆盖：

- public/non-public。
- synthetic/bridge。
- Hide/Remap/RemapByPrefix。
- overload。
- adapter input shape。
- docs/type refs/deprecated。
- duplicate name/signature。

### 13.2 common 边界测试

- `common-api` 不允许 MC/loader/Graal implementation import。
- stable symbol signature 不允许原生 FQN。
- `common-runtime` 不允许通过版本字符串实现平台业务分支。
- addon SPI 不允许引用 `core.*`、`probe.*` 或平台 implementation。

### 13.3 四平台 manifest 测试

每个平台导出 normalized manifest：

- portable stable subset 必须完全相同。
- stable symbol 的签名、script type、错误语义、required capability 必须一致。
- feature module 只有 capability contract 通过时才能出现。
- platform/version module 只能出现在匹配环境。
- capability 必须由 provider/module 派生，不能出现“声明支持但未注册实现”。

### 13.4 Probe 端到端

- 生成两次必须字节稳定。
- 生成失败保留旧有效输出。
- manifest hash 能检测过期声明。
- 运行固定 TypeScript 版本执行 `tsc --noEmit`。
- 不允许悬空 import、重复声明、非法参数名或错误平台原生类型。

### 13.5 行为契约脚本

仓库增加 `test_scripts/contracts/`：

- 同一 stable 脚本在四平台运行。
- 事件触发次数、取消、顺序、可变效果一致。
- ID/Text/NBT/Item/Ingredient/Fluid 序列化和匹配一致。
- recipe stable 操作真实改变游戏 recipe 状态。
- Network 方向、payload 和 reload 清理一致。
- 失效引用和 unsupported module 返回稳定 error code。

能单测的 bridge 使用 fake provider；必须依赖游戏生命周期的行为使用 NeoForge GameTest 或平台 server harness。Cleanroom 必须有可重复运行的等价测试入口，不能长期只靠人工 smoke test。

### 13.6 API baseline 门禁

API 1.0.0 发布后保存 baseline manifest。CI 必须阻止：

- 非 API major 删除/修改 stable symbol。
- stable 原生类型泄漏。
- 四平台 stable subset 漂移。
- capability 与 module/provider 不一致。
- deprecated replacement 不存在。
- Wiki/API 表与 manifest 漂移。

---

## 14. 迁移路线

本项目是一个包含十个阶段的架构迁移 program，不应压成一个巨型可执行计划。每个阶段或领域必须有独立 spec、plan、提交序列和验收点。master plan 负责依赖顺序，领域 plan 负责精确 symbol/overload 和 TDD 步骤。

### Phase 0：现状基线和规范格式

独立计划：current-surface audit + normative contract schema。

产物：

- 四平台 current-surface dump。
- 当前 global/event/adapter/host-extension 矩阵。
- 原生类型泄漏、假实现和语义不一致清单。
- preview API 到目标 tier/module 的分类表。
- `NormativeApiContract` JSON Schema。
- module descriptor、capability definition/provider contribution、symbol/type reference 的规范 schema。
- core owner/namespace/tier 授权规则测试。

要求：

- 修正文档中不存在的注解、descriptor、BindingComposer 等超前描述。
- current snapshot 仅用于迁移比较，不成为 API 1.0.0 baseline。
- Phase 0 不决定具体领域 API；它决定后续领域 contract 如何被机器验证。

### Phase 1：runtime-enforced 契约内核

独立计划拆为四个可验收子阶段：

1. canonical symbol/type/signature model 和 stable ID。
2. `JsApiSurfaceResolver`、normative conformance 和冲突检测。
3. `FrozenApiRegistry`、registry-backed `ApiFacadeProxy`/invoker 和 stable runtime 权限边界。
4. 最小 module/capability registry、结构验证、scope/environment resolver、`ApiManifest` 与 API diff。

同时提供 runtime、preflight、Probe 的消费接口和同 fixture 一致性测试。Phase 1 只要求 Probe 能消费测试 surface，不要求此时完成全部 production `.d.ts` emitter 重构。

module/capability registry 必须在本阶段建立，避免基础 `Platform.hasModule()` 和后续 feature capability 出现倒置依赖。

### Phase 2：物理模块与 Graal-free SPI

独立计划：

- 建立 `:common-api`。
- 将当前 `:common` 收敛为 `:common-runtime`。
- 消除平台的 `srcDir common + implementation common` 双重装配。
- 将公开 registry/SPI 从 `core.*` 迁出。
- 用 `JsValueView`/`ConversionContext`/`ConversionPrecedence` 替换 public SPI 中的 Graal 类型。
- runtime 提供旧 preview adapter 到新 SPI 的内部迁移桥。
- internal implementation 标记、Java source/ABI baseline 和架构依赖测试。
- addon 只对 `common-api` compileOnly。

完成本阶段后才建立 `spiVersion 1.0.0` 候选 baseline。

### Phase 3：基础 stable Facade（分三个领域计划）

#### Phase 3A：Platform、错误和 ID

先提交 exact normative contract，再实现：

- stable errors。
- `NekoId` / `ID`。
- `Platform` 版本字段。
- `hasModule`、`hasCapability`、scope-aware environment view。
- 四平台 ID bridge。

#### Phase 3B：Text、NBT/JSON、IO 和通用工具

先提交 exact normative contract，再实现：

- Text value/facade。
- NBT/JSON values 与 IO。
- Time/Utils/GlobalData。
- sandbox 路径和序列化行为契约。

#### Phase 3C：Item、Ingredient 和 Fluid

先提交 exact normative contract，再实现：

- values、refs、factories 和 bridge。
- 精确 overload/input shapes。
- 跨平台匹配、复制、数量和序列化契约。
- 不等价 tag/component 能力的 feature/version 分层。

每个领域只有满足四平台 normative conformance、无原生类型、behavior contract 和 Probe `tsc` 后才完成。

### Phase 4：实时引用和统一事件（分多个计划）

#### Phase 4A：实体/世界引用

- Player/Entity/LivingEntity/Level/Server/Block Facade exact contract。
- validity/lifetime/error contract。
- 四平台 bridge 和 proxy 实现。

#### Phase 4B：事件内核

- stable EventContext、显式取消、priority/receive-cancelled。
- listener ownership、transactional reload、unregister/close。
- event payload proxy 和 runtime/preflight/Probe 同源。

#### Phase 4C 及后续：逐事件组准入

Server、Player、Block、Item、Entity 分别建立 domain contract 和实施计划。缺失事件的 Mixin/Coremod 补齐与对应 contract tests 放在该事件组计划中。未通过准入标准的 event 不进入 stable baseline。

### Phase 5：配方与网络（两个独立计划）

#### Phase 5A：Recipe

- exact stable common operation contract。
- Cleanroom legacy registry 真实行为。
- `recipe.json` 等 feature contract/capability/provider。
- server reload、viewer refresh 能力拆分。

#### Phase 5B：Network

- exact channel/payload/direction/error contract。
- `NetworkBridge` 和 player Facade 集成。
- handler reload 生命周期。
- 四平台端到端 contract scripts。

### Phase 6：完整 module interop 和 native 层

Phase 1 已有最小 registry；本阶段补齐生产 ESM 体验：

- static/dynamic import 的结构化 unsupported 错误。
- feature module version/dependency 解析。
- `@nekojs/platform/neoforge` 和 `@nekojs/platform/cleanroom` stable-Facade exports。
- 四个 `@nekojs/version/<loader>-<mc>` module。
- 只在 version module 提供 native unwrap。
- addon `@<modid>/<module>` namespace。
- ScriptType/dist/requiredMods 环境差异和 staleness。

### Phase 7：Probe、文档和 workspace 收口

- production Probe 全面消费 canonical surface。
- manifest/hash/staleness 和幂等生成。
- addon roots 和 descriptor seeds。
- runtime/preflight/Probe 一致性门禁。
- manifest 驱动 API 表和 capability matrix。
- workspace 只引用当前有效 module 声明。
- 固定 TypeScript 版本执行 `tsc --noEmit`。

### Phase 8：preview 破坏式收口和 API 1.0.0 freeze

- 删除 `Identifier`/`ResourceLocation` 等原生 stable 全局。
- 删除 stable globals 中直接暴露的 MC 类。
- 将 NativeEvents 和原生 unwrap 移入 version module。
- 删除假兼容 stub 和假成功操作。
- 移除 callback 返回 `true` 的取消语义。
- 输出 preview-to-1.0.0 migration guide 和 API diff。
- 审阅并冻结四平台一致、符合 normative contract 的 `apiVersion 1.0.0` baseline。
- 审阅并冻结 `spiVersion 1.0.0` 及首批 feature/platform module baseline。

由于用户明确选择“立即整理破坏”，不要求为 preview 旧入口保留一个 minor alias 周期。迁移指南和结构化错误仍是必需的。

### Phase 9：持续治理

- 每次 PR 运行按 tier 的 API/SPI/module diff。
- 每个新 stable symbol 必须先更新 normative contract，再有四平台实现和 contract test。
- 每个新 feature module 必须有 capability/provider 一致性和 module contract 测试。
- 每个新 platform/version symbol 必须带 tier、owner、module 和 since/revision。
- 每次发布保存 normative contracts、portable contract/surface hash、各 module contract hash 和 environment manifest artifact。
- Wiki 和 `.d.ts` 从相同 manifest 更新。

---

## 15. 分阶段验收标准

### 契约基础完成

- normative contract JSON Schema、module/capability descriptors 和 owner/tier 授权规则已提交。
- observed manifest 必须通过 normative conformance，不能反向生成产品契约。
- runtime、preflight、Probe 对同一 fixture 的 symbol 集完全一致。
- stable fixture 的实现类额外 public 方法无法通过 Graal runtime 访问。
- manifest 可稳定序列化。
- API diff 能识别 breaking/additive/documentation-only。
- overload、remap、hide、adapter shape 不丢失。
- module/capability 按 ScriptType、dist、requiredMods 和 loader/MC scope 正确激活。

### 统一 API 迁移完成

- 四平台 portable stable manifest 完全一致。
- stable manifest 中不存在 MC/loader/Graal FQN。
- stable contract scripts 在四平台通过。
- 缺失原生事件已通过注入补齐，或该事件未进入 stable。
- Cleanroom 不再提供假 JSON recipe 等假实现。

### 专属 API 完成

- feature/platform/version module 只在正确环境注册。
- native unwrap 不进入 stable 类型图。
- native unwrap 只出现在 loader + MC version module，不出现在跨版本 platform module。
- unsupported static import 在 link 阶段给出稳定错误代码。
- dynamic import 可以通过 `Platform.hasModule()` 安全守卫。

### API 1.0.0 可冻结

- baseline manifest 已审阅并提交。
- API diff、四平台 parity、native leak、Probe `tsc` 和 contract scripts 都是 CI 必过项。
- preview-to-1.0.0 migration guide 完整。
- 所有 stable symbol 都有 owner、since、文档和测试。
- 没有未决占位项、未登记例外或只靠人工验证的 stable 能力。

---

## 16. 对现有计划的处理

### 16.1 保留但重排

以下内容方向正确，可以进入新实施计划：

- `@Doc`、`@Param`、`@Return`、`@Example`、`@Since`、`@DeprecatedNekojs`。
- `common-api` 与 runtime/impl 物理拆分。
- 消除双重装配。
- plugin descriptor 和 owner/conflict metadata。
- Probe 输出稳定性测试。
- platform parity 和 feature backlog。

### 16.2 必须替换

- “冻结当前所有 binding/event 名称”替换为“只冻结四平台 contract 通过的 Facade surface”。
- “Probe 直接用 MemberVisibilityQuery”替换为“先建立 canonical JsApiSurfaceResolver”。
- “TypeOverride 使用 FQN 字符串”替换为结构化 module/symbol reference。
- “collector 扫 plugin class”替换为扫描 catalog roots 和 descriptor roots。
- “capability 由平台枚举集合声明”替换为 provider/module contract 派生。
- “Cleanroom 补齐所有现代 API 名称”替换为“相同语义才补齐，否则进入 feature/platform 层”。

### 16.3 暂不混入本计划

Registry/data-gen、复杂 builder、recipe viewer adapter、Web、stage 等 feature backlog 不应阻塞 API 契约内核。它们在新架构中应作为 API 1.x additive feature 进入，而不是在 freeze 前扩大首版范围。

---

## 17. 最终架构不变量

1. stable JS 签名永远不引用平台原生类型。
2. stable 正常路径不根据 MC/loader 版本分支。
3. 能通过 Mixin/Coremod 实现相同语义的能力可以进入 stable，但必须通过同一契约测试。
4. 不能等价实现的能力不伪装为 stable。
5. stable Facade 的原生解包只能通过匹配 loader + MC 的 version module；`UNSAFE_NATIVE` 可以独立暴露不受管 host object，但不得定义以 stable Facade 为输入的 unwrap export；跨版本 platform module 不能返回原生 MC 类型。
6. 产品契约来自人工审阅的 normative contract；capability、module、runtime、Probe、manifest 和文档来自与该契约一致的同一 frozen surface。
7. `apiVersion` 的 breaking change 只能发生在 API major。
8. version module 不向 stable 类型图反向泄漏。
9. preview 旧 API 可以破坏式整理，但 API 1.0.0 baseline 一旦发布必须受门禁保护。
10. 新增 API 前先确定 tier、owner、module、capability、生命周期和 contract test，不允许注册后再补治理信息。
