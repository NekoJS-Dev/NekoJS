# NekoJS 稳定 API 与版本 API 规范提案

日期：2026-11-06。

状态：**设计提案，尚未实现新入口或兼容承诺**。本文规定建议的分类、调用、发布和迁移方式；代码块中的新模块、`Native` 桥接、`ID.resource`、`Capabilities` 和新事件字段都是拟议接口。现有 `ID` / `NekoId` 等实现会复用，不能把新入口当作当前版本已经提供的功能。

本文面向维护者审阅，使用中文。正式交付的内嵌手册、类型说明、Skill 和示例注释保持英文。

## 先看这几条

- **脚本作者不用写元数据。**插件作者注册一次实现，引擎自动整理名字、类型、方法和导入路径；不用再手写 API 清单或 `.d.ts`。
- **默认保持全局。**沿用现在的 `registry.register(Binding.of(...))`，脚本直接使用这个名字。
- **明确指定模块，才需要 import。**注册时选择 `stable()` 或 `versioned()`，这个注册项就只进入相应模块，不再自动创建同名全局变量。
- **普通 Binding 不再二次登记。**不要求再到 `registerApiSurface` 重写一遍名字、来源和公开类型。上一稿的 `exportBinding` 流程撤回。
- **稳定承诺需要人决定，清单由程序生成。**程序能读出参数类型，但不能替作者保证事件时机、线程和业务语义；这些仍写在实现、测试和正常的 API 说明里。

下面是设计中的调用形式；新增重载还没有实现。插件作者日常主要看第 12 节，元数据的作用和生成方式看第 9 节，后面的实现约束可由维护者处理。

## 1. 决定采用什么方式

采用“自有稳定能力 + 稳定桥接 + 原生扩展”的方式。**稳定性按具体操作和数据位置规定，不按是否调用 Java、是否使用原生实现或名字里是否带 Native 一刀切。**

保留两个明确的调用入口，但调整它们的含义：

| 入口 | 用途 | 作者如何理解 |
|---|---|---|
| `nekojs/api/stable` | 已审定的 NekoJS 自有契约和稳定桥接操作 | 自有类型/行为保持兼容；桥接中的原生参数、返回值和回调对象另行标注版本依赖 |
| `nekojs/api/version` | 当前 MC/loader profile 的原生入口与遗留原生视图 | 可以深入原生对象，迁移 MC/loader 时核对它们的实际类型和行为 |

两类都保持现有的调用风格：事件用 `ServerEvents.xxx(event => {})`，助手用 `Item.of(...)`，配置回调放最后，访问器使用 Bean 属性。导入路径帮助选择入口，成员契约说明承诺范围；从 `nekojs/api/stable` 导入一个原生桥接助手，不代表其接收的任意 Minecraft 类也被稳定化。

保留已有 `nekojs/jsx-runtime`、`nekojs/jsx-workspace` 等领域模块路径。它们的成员单独标注稳定性和支持范围，不要求全部塞进一个大模块，也不因模块已存在就自动成为稳定 API。

现有全局 Binding、事件组先保留。新入口采用增量引入方式，不立即改写旧脚本的事件对象或删除原生字段。

第三方插件在相同前缀下使用自己的 owner 路径，例如 `nekojs/api/stable/exampletech`，不直接把全部导出塞进内置根模块。注册方式见第 12 节。

具体放置规则：

| 契约情况 | 导出位置 |
|---|---|
| 已审定的通用稳定 Binding/事件组 | `nekojs/api/stable` |
| 已审定的稳定桥接操作，如 `NativeEvents.onEvent` | `nekojs/api/stable`，原生输入和载荷单独标注 |
| 有独立领域模块的稳定成员 | 保持原领域路径，在目录中标注 stable，不强制重复导出 |
| 原生类、直接原生助手、遗留原生事件视图 | `nekojs/api/version`，提供当前 profile 的真实类型 |
| 仍在试验的 NekoJS 自有能力 | 保持或新增有实际领域含义的模块，标注 experimental；审定前不放入 `nekojs/api/stable` |
| 内部实现 | 不从公共脚本模块导出 |

不能仅因某个自有接口还在试验，就把它塞进版本模块。版本依赖和成熟程度是两件事。

本次修订纠正上一稿对 `NativeEvents` 的放置：它可以是稳定桥接，不应仅因回调接收原生对象就整体归为不稳定。`ServerEvents` 也不能整组贴标签，应分别审定订阅方式、每个事件的时机和载荷成员。

## 2. 分清三个问题

### 2.1 这是谁维护的契约

- **NekoJS 契约**：名称、参数、对象字段和行为由 NekoJS 定义，Java 实现负责适配底层差异。
- **原生契约**：直接依赖 Minecraft、NeoForge、Fabric 或其他模组的类、成员、资源结构或注入目标。

一项调用可以同时涉及两种契约。例如 `NativeEvents.onEvent` 的注册方式由 NekoJS 维护，而传入的事件类名称和回调对象成员由原生平台决定。目录需要记录依赖具体发生在参数、返回值还是回调载荷，不把整个方法强行压成一个“稳定/不稳定”结论。

| 例子 | NekoJS 可以承诺的部分 | 仍随原生版本变化的部分 |
|---|---|---|
| `ID.of` / `NekoId` | 构造、默认命名空间、字段和字符串表示 | 无；转换后的原生对象另算 |
| `NativeEvents.onEvent` | 注册方式、处理器规则、取消约定和监听器清理 | 事件类是否存在、原生字段及原生触发事实 |
| 当前 `ServerEvents.tickPre` | 事件组调用方式及经验证的分发约定 | 当前直接透传的原生 Event Object |
| 拟议稳定 `ServerEvents.tickPre` | 调用、阶段及 NekoJS Event Object 的已声明成员 | 通过原生出口取得的附加对象 |

这里的“版本 API”指原生依赖部分，不是给每个 NekoJS 发布版本再造一套入口。底层用原生代码实现稳定行为很正常；真正的问题是是否把底层变化直接交给脚本承担。

### 2.2 承诺多大程度的兼容

- **stable**：有明确契约、覆盖测试、支持矩阵和变更规则。
- **experimental**：尚未形成稳定承诺，调整必须明确记录；原则上在次版本调整，补丁版本不随意改变写法。
- **未审定**：初始盘点状态，不得自动导出到稳定模块；仍遵守已有公开行为的兼容要求，不能以未审定为由任意删除。

“内部”属于可见性，不是另一种稳定等级。内部实现不进入公共脚本入口；反射或类型依赖闭包里能看到某个 Java 类型，也不使它成为受支持的公共 API。

### 2.3 哪些环境提供它

另行记录 MC 节点、loader、ScriptType、客户端/专用服务端和必要模组依赖。

**稳定性和支持范围分别判断。**例如未来经过审定的 `ClientUI.screen` 可以属于稳定契约，同时只支持 NeoForge 26.x CLIENT。没有该能力的节点不导出它，也不放一个空实现冒充支持。

## 3. 稳定 API 怎么调用

### 3.1 常规写法

拟议 CLIENT 脚本：

```ts
import { I18n } from "nekojs/api/stable";

const doneLabel: string = I18n.translate("gui.done");
const language: string = I18n.languageCode;
```

这里承诺的是翻译操作的参数、返回类型、缺失键规则和当前语言读取方式。Minecraft 的翻译键、语言包内容及实际翻译结果不因此被冻结。

拟议 SERVER 脚本：

```ts
import { ServerEvents } from "nekojs/api/stable";

ServerEvents.tickPre(event => {
    if (event.tick % 20 === 0) {
        console.info("Twenty server ticks elapsed.");
    }
});
```

本例原提议将只读 `tick: number` 从服务端会话首个 tick 计为 0。后续按维护者要求调整：直接使用两平台事件所持服务端的原生 `getTickCount()`，不维护额外计数、不重新归零。已核对支持节点的原生阶段：计数先递增，再触发 pre/post，因此普通首个 tick 为 1，同一 tick 的两阶段读到相同原生值；脚本重载不修改服务端计数。

稳定载荷 `StableServerTickEvent` 仅保存该原生值的只读快照，不暴露原生服务端。整数溢出和新服务端初始化沿用 Minecraft 行为。若未来 backend 的阶段或计数语义不一致，先不收录该事件，不能让同一字段在不同节点代表不同计数。

### 3.2 CommonJS

同一入口也可提供：

```js
const { I18n } = require("nekojs/api/stable");
const doneLabel = I18n.translate("gui.done");
```

生成器必须为这些已知模块提供有类型的 `require` 重载，不能因换成 CommonJS 就整体返回 `any`、丢失事件参数和返回值补全。

### 3.3 支持范围仍然生效

`I18n` 只在 CLIENT 上下文导出。SERVER 脚本导入它，应在当前 ScriptType 的类型检查或模块链接阶段得到明确错误。

`nekojs/api/stable` 的模块 ID 保持一致，具体导出由当前 profile 和 ScriptType 决定。客户端、服务端分别生成声明，不能把所有侧的导出并成一份“到处都能用”的类型文件。

## 4. 稳定桥接和版本对象怎么配合

### 4.1 NativeEvents 是稳定桥接候选

拟议的 NeoForge STARTUP 脚本：

```ts
import { NativeEvents } from "nekojs/api/stable";

NativeEvents.onEvent(
    "net.neoforged.neoforge.event.tick.ServerTickEvent$Pre",
    event => {
        const nativeServer = event.server;
    }
);
```

这里的 `NativeEvents.onEvent` 从稳定入口导入。NekoJS 承诺其参数顺序、默认优先级、可取消事件的 `return true` 处理方式、错误处理和归属/清理规则。传入的 NeoForge 类名与回调内的 `event.server` 仍按当前 profile 解释，不获得跨 MC/loader 的保证。

“稳定桥接”不是“所有地方都有原生事件总线”：按当前实现，`NativeEvents` 是 NeoForge STARTUP 能力。可以在经过验证的 NeoForge 节点间承诺同一种桥接规则；Fabric 不因为它稳定就自动获得 NeoForge 类或这个导出。

现有 [NativeEventsJS](../../src/main/java/com/tkisor/nekojs/bindings/static_access/NativeEventsJS.java) 仍需完成线程、资源归属及清理审计，才能正式承诺这些行为；这属于实现和验证要求，不改变它应该归入稳定桥接的设计方向。

对于尚未审定的直接原生助手，可先保留明确的原生入口：

```ts
import { Item } from "nekojs/api/version";

const stack = Item.of("minecraft:stone");
const count = stack.count;
```

这个例子表达“返回当前 profile 的原生 ItemStack”。暂放这里是审定和迁移安排，不是“只要返回原生对象就一定不稳定”的规则。后续可以审定为稳定构造桥接，也可以为常见物品操作提供完整稳定视图；两者的返回契约必须分清，不得只换导入路径就宣称 ItemStack 的所有方法已经稳定。

### 4.2 稳定调用中需要访问原生对象

拟议 NeoForge SERVER 脚本：

```ts
import { ServerEvents, Native } from "nekojs/api/stable";

ServerEvents.tickPre(event => {
    const nativeEvent = Native.unwrap(event);
    const nativeServer = nativeEvent.server;
});
```

`Native.unwrap` 是稳定的桥接操作，显式表达离开稳定对象视图。它只支持具有对应原生 backing 的已知事件或 handle；没有原生对应物的对象不提供该重载。其操作规则保持兼容，不同 profile 的原生返回类型由实际 backing 决定，随后访问的原生成员属于版本依赖。

例如某个 Fabric callback 只提供服务器参数，没有 NeoForge 事件对象，就不能凭空生成 `$ServerTickEvent$Pre`。该 profile 不提供这个事件的解包能力。

解包必须在有效的事件调用范围内执行。脚本保存了原生对象之后，适用原生对象的线程和生命周期规则；不能承诺让已经交出的任意 Java 引用自动失效。

`Native.loadClass(name)` 可作为已有 Java 类加载能力的显式入口，沿用当前访问规则。任意字符串的动态加载保持保守类型；只有能验证的字面量/类型引用才能获得更精确补全，不能靠 `any` 假装所有类都已知。

### 4.3 不按每个 MC 补丁号复制命名空间

原生入口统一使用 `nekojs/api/version`，由当前 profile 提供真实导出，不默认设计 `nekojs/1.21.1`、`nekojs/26.1.2` 等整套重复模块。稳定桥接仍从 `nekojs/api/stable` 获得。

版本差异记录在 API 目录和依赖范围中。只有确实需要同时存在、语义不兼容的两种操作时，才考虑有具体含义的新名称，不自动生成 `fooV26` 一类入口。

### 4.4 保留直接使用原生事件的方式

拟议 NeoForge SERVER 脚本：

```ts
import { ServerEvents as NativeServerEvents } from "nekojs/api/version";

NativeServerEvents.tickPre(event => {
    const nativeServer = event.server;
});
```

这里仍使用现有原生载荷。`NativeServerEvents` 是脚本作者的本地别名，不是新增的全局事件组。新稳定视图、这个原生视图及旧全局入口复用同一事件来源和 owner，不能变成三个独立底层监听系统。

## 5. NekoJS 自己提供稳定类型和能力

### 5.1 三种实现方式

| 类型 | NekoJS 负责什么 | 合适的实现方式 |
|---|---|---|
| 自有值类型，如 NekoID、文本描述、纯数据 | 字段、校验、操作和序列化的完整语义 | common 中的不可变值/数据模型，不依赖 MC |
| 活对象能力，如玩家、服务器、窗口 | 常用操作、线程、寿命及状态变化 | 明确的业务 Interface，版本树 Adapter 操作真实底层对象 |
| 原生互操作，如 NativeEvents、ID 转换 | 调用协议和边界检查 | 稳定桥接操作，原生输入/输出在 profile 中有精确类型 |

稳定接口可以由 Minecraft 实现支撑。对于活对象，不复制一份世界、玩家或库存再维持双向同步；视图背后仍是实际对象，读写时机、失效和线程规则由 NekoJS 规定。只有明确叫作快照的数据才承诺固定内容。

由调用方自己传入并取回的泛型数据例外：Signal 的 `T` 可以是作者提供的原生对象，稳定的是 Signal 的存取行为，不是 `T` 的所有成员。

### 5.2 NekoID 直接复用现有 ID / NekoId

这里的“NekoID”指统一的资源标识能力，不要求按这个拼写新增一套类型。仓库已有：

- [NekoId](../../common/src/main/java/com/tkisor/nekojs/api/data/NekoId.java)：不可变的 `namespace` / `path` 值，默认命名空间为 `nekojs`。
- [IdFacade](../../common/src/main/java/com/tkisor/nekojs/api/facade/IdFacade.java) 和 [DefaultIdFacade](../../common/src/main/java/com/tkisor/nekojs/core/api/facade/DefaultIdFacade.java)：已有 `ID.of`、字段读取和字符串操作。
- [IdentifierAdapter](../../src/main/java/com/tkisor/nekojs/js/type_adapter/IdentifierAdapter.java) 和 [ResourceLocationAdapter](../../src/main/java/com/tkisor/nekojs/js/type_adapter/ResourceLocationAdapter.java)：已有输入转换，可接收 `NekoId`。
- [NekoIdCompat](../../common/src/main/java/com/tkisor/nekojs/platform/NekoIdCompat.java)：已有平台转换接点，需审计后复用；不再并列引入第二套 ID 平台服务。

首批稳定能力优先从这些实现形成契约，不重新造一个 `NekoID` 类替代 `NekoId`，也不只为重命名增加一层转发包装。

拟议所有 ScriptType 均可使用：

```ts
import { ID } from "nekojs/api/stable";

const stone = ID.of("minecraft:stone");
const custom = ID.of("example", "machines/press");
const namespace: string = stone.namespace;
const path: string = custom.path;
const text: string = stone.asString();
```

稳定契约至少明确：

- 值不可变；属性只读，不保留原生对象、Context、世界或 generation 引用。
- `ID.of("example")` 仍得到 `nekojs:example`，不改成 Minecraft 的默认命名空间。示例优先使用带命名空间的字符串。
- `namespace`、`path` 和 `asString()` 的含义各版本一致，字符串形式为 `namespace:path`；不因底层类叫 Identifier 或 ResourceLocation 而改变。
- 以内容比较值，不依赖 JS `===` 或 Java 对象身份；持久化使用明确的字符串/数据编码，不依赖 guest 包装对象的默认 JSON 行为。
- 可跨平台重建同一数据值，不意味着可以在不同 Graal Context 间直接传同一个 guest 包装对象。

### 5.3 ID 校验不能暗中改变旧行为

现有 `NekoId` 主要检查两部分非空白，并没有实现完整的 Minecraft 资源 ID 语法。不能把它描述为已经完成严格校验，也不能在这次分类时悄悄收紧旧 `ID.of`。

推荐保留 `ID.of` 的现有基础构造规则；增加一个有明确资源语义的稳定构造操作 `ID.resource(value)` / `ID.resource(namespace, path)`，返回同一种 `NekoId`：

- namespace 使用固定的 `[a-z0-9_.-]+`；path 使用固定的 `[a-z0-9/._-]+`。
- 单字符串形式只允许一个命名空间分隔符；无分隔符仍使用 `nekojs`。
- 拒绝空值、空白部分和不符合规则的字符；不隐式 trim、转小写或改写路径。
- 这些是 NekoJS 选择维护的资源 ID 规则，实施前对支持节点进行原生构造往返验证。后续底层放宽语法不自动改变这套规则。
- `ID.of` 构造出的普通命名值未必是合法原生资源 ID；用于资源操作或转换时统一检查，失败报告稳定的输入错误，不改写 ID 或回退到默认资源。

这使兼容旧值和提供可靠资源构造可以同时成立。`ID.resource` 是拟议新增操作；稳定基线仍需分别记录两种构造的具体错误契约。

### 5.4 原生转换由稳定桥接负责

拟议同一份源代码可在各个具有 ID 转换支持的 profile 使用：

```ts
import { ID, Native } from "nekojs/api/stable";

const id = ID.resource("minecraft:stone");
const nativeId = Native.ids.toNative(id);
const again = Native.ids.fromNative(nativeId);
const text: string = again.asString();
```

`toNative` 在当前节点返回对应的真实原生 ID 类型，`fromNative` 接收当前节点支持的真实原生 ID 类型并返回 `NekoId`。转换规则稳定，原生类名和原生成员仍属于版本依赖。Probe 不能将所有节点的原生类合成一个 `any` 或伪造同名类。

转换以 namespace/path 内容为准，不承诺对象身份；无损支持的资源 ID 应通过往返测试。无法由稳定资源规则表示的原生扩展值要明确失败，不静默截断。不能用任意对象上恰好存在的同名属性冒充原生 ID。

已有原生 API 若通过 Adapter 声明支持 `NekoId` 输入，作者可以直接传入，无需每次手动转换。只有确实要调用原生接口而现有 Adapter 不支持时，才显式转换。运行时允许的输入形状与 Probe 声明必须一致。

稳定 ID 的构造、比较、拼接等纯数据操作留在 common；原生 ID 构造和识别留在版本树。先补充现有转换接点的必要方向和类型映射，不为每个小操作建立一个新 Facade。

### 5.5 稳定层如何避免能力不足

稳定层应覆盖脚本作者的常见工作，并持续增加能力，按以下规则选择：

1. **多个版本有同一项业务能力，原生写法不同**：由版本 Adapter 消化差异，稳定层提供同一种操作。例如统一 ID、常用文本操作、注册查询和事件控制。
2. **只有部分版本有能力，但能写清并维护相同语义**：仍可提供稳定操作，支持矩阵限制到具备该能力的节点。稳定不等于所有历史版本的最小交集。
3. **只对原生实现细节有意义，或无法定义一致语义**：保留原生出口，给出真实类型，不在稳定层捏造效果。作者只在这部分承担版本依赖。
4. **已有稳定操作能力不足**：优先增加兼容的具体方法/可选参数或有领域含义的可选能力；避免扩张成接收任意字符串和 `any` 的万能接口。

第 2 类能力按第 7 节的环境范围和入口隔离规则使用。`Capabilities.has` 不能自动让缺失的导出通过类型检查；等出现具体跨 profile 的可选调用需求，再设计有实际返回类型的可选接口。

不要把稳定对象定义为 `StableType & NativeType`，不要让未知属性自动回退到原生对象；否则原生成员混入日常补全，作者无法分辨兼容边界。原生出口应显式、局部、可定位，使用稳定部分的脚本不需要顺带学习整套底层类型。

对于玩家、世界、物品等活对象，先选择常见业务能力形成有限视图，不机械复制原生类的每个方法，也不禁止为当前真实需求增加有价值的稳定能力。性能上不要求每次读取都创建新快照或序列化整份原生对象；复用现有 owner 和对象转换机制，缓存仍需明确寿命与失效规则。

对于 NekoJS 自己的 Java 实现对象，稳定声明只列审定成员。实现类偶然多出的公开方法不属于稳定承诺。分类用于管理接口合同，不替代现有沙盒。

资源是否实际存在、原生 JSON 格式、注册内容、翻译键和世界状态不因助手稳定而被冻结。稳定 builder 可以适配数据格式；直接提交原生 JSON 的接口应单独标明格式依赖。

## 6. 事件如何规定

每个稳定事件必须有独立契约，至少写明：

| 内容 | 需要冻结的规则 |
|---|---|
| 调用形式 | 事件组、名称、selector、回调参数和返回值 |
| 触发 | 所处阶段、次数、前后顺序、是否可能不触发 |
| 环境 | ScriptType、逻辑侧、物理环境、允许线程 |
| 载荷 | 字段类型、单位、可空性、只读/可写及写入何时生效 |
| 控制 | 是否可取消、如何取消、取消影响什么、是否有结果替换 |
| 异常 | 回调失败后的行为和诊断方式 |
| 寿命 | 回调、事件控制对象和监听器的有效期 |
| 重载 | 候选不生产分发、成功切换、失败保留和关闭清理 |

取消方式必须逐事件声明。保留已有经过审定的 `return true` 语义或提供明确的控制方法，不能同名事件在不同 backend 上一个能取消、另一个静默忽略。需要同步决定的取消/返回值不能依赖 Promise 稍后完成。

目前 [ServerEvents](../../src/main/java/com/tkisor/nekojs/bindings/event/ServerEvents.java) 的 tick 事件直接使用 NeoForge 原生事件类型；这说明载荷存在原生依赖，不能据此判断所有订阅方法都不稳定。反过来也不能据名字一致把整个 Event Object 标为跨版本 stable。

稳定版本应提供 NekoJS 自己定义的 Event Object：纯数据可以用稳定值；需要服务器/玩家等对象时，逐步提供有实际能力的稳定视图。Adapter 从原生事件提取或委托，不重新模拟一套底层事件。暂未提供的高级操作可以在当前 profile 通过显式原生出口完成。

新稳定事件视图与旧全局原生视图可以在迁移期共存，但必须消费同一次底层事件事实：

- 原生监听来源和生命周期由已有桥接/事件 owner 统一管理。
- 不能因同时提供两个入口就把一个注册分发两次。
- 保持已声明的 priority、注册顺序及取消传播规则；需要时在交付回调时转换视图。
- 若暂时无法保留旧序列关系，应暂缓收录或明确另一个语义名称，不能悄然替换旧事件。

先选无取消、只读载荷的小事件做试点，再扩展有写入或取消的事件。

## 7. 可用性、能力查询和模块失败

所有判断使用当前 catalog/profile 的同一份可用性记录：MC 节点、loader、ScriptType、物理环境和必要模组。

可提供只读的 `Capabilities.has(id)` 作为稳定查询入口，例如查询 `nekojs.client.ui.screen`。实施时先复用已有 `Platform.capabilities()` 的能力来源，统一能力 ID 和实际语义；只有确实减少调用负担时增加这个查询门面，不能再维护第二份独立功能清单。结果表示当前上下文是否支持该能力，不代表任意操作都已获得授权，也不执行类加载来试探。

需要区分：

- 原本不支持：不导出对应成员，声明和运行时一致。
- 支持但初始化失败：报告初始化错误，不能伪装成“这个版本不支持”。
- 已暴露但调用条件不满足：按接口报告线程、生命周期或参数错误，不能返回空对象掩盖问题。

能力查询不会使无效的静态 import 变合法，也不会替 TypeScript 自动消除其他 profile 的不兼容代码。首版跨平台脚本优先使用包的环境范围和独立入口隔离版本代码；仅把两个会被自动执行的文件放在同一目录不构成隔离。

复用现有 [PackDescriptor](../../common/src/main/java/com/tkisor/nekojs/core/pack/PackDescriptor.java) 的 `environment.mods`、`loaders` 和 `minecraft` 等范围规则。内置 API 能力目录与脚本包自己的 `capabilities` 提供/依赖关系要区分，不能把包声明的字符串直接当作原生功能已经存在。

对可选动态模块加载，如需统一的强类型辅助接口，再单独规定其导入、缺失和类型语义。首版不以返回 `any` 的动态 require 作为多平台解决方案。

## 8. 稳定性和版本号怎么承诺

以下规则针对 NekoJS 本体及其框架公开契约；第三方插件自己的契约版本和责任见第 12.7 节：

1. **稳定 API 以 NekoJS 主版本为兼容线。**同一主版本内，使用已声明成员且满足支持范围的脚本保持调用及约定语义兼容。
2. 新增兼容能力放次版本；兼容修复放补丁版本。破坏性变化最早在下一主版本实施，并提供迁移说明。
3. 计划废弃时仍保持 stable，记录 `deprecatedSince`、替代方式和移除版本。至少保留一个正式次版本的迁移窗口；不通过改标 experimental 撤销旧承诺。
4. 实验 API 的破坏性调整有明确发布记录，原则上放次版本；不因调用方使用了实验 API 就省略错误说明和迁移帮助。
5. 新增 MC 节点不自动继承已验证支持。先验证同一契约，再扩展支持范围。
6. 从仍在支持的 profile 删除稳定能力，按破坏性变化处理。MC 节点停更另外公告支持周期；不修改已经发布的旧 JAR。

这种版本号规则参考 [SemVer](https://semver.org/)，稳定/实验的分级方式可参考 [Node.js stability index](https://nodejs.org/api/documentation.html#stability-index)。迁移窗口是本提案增加的维护政策，不是上游替 NekoJS 作出的承诺。

不立即再引入一套独立的脚本 API 版本计数器。NekoJS 版本、MC 支持范围、快照 schema 版本、内容包版本分别承担自己的职责。

## 9. 元数据是什么，谁来生成

### 9.1 用人话说，就是引擎记下来的 API 清单

假设插件注册了一个叫 Machines 的 Binding，它有一个接收 ID、返回 boolean 的 has 方法。引擎需要记住：

| 记下什么 | 从哪里得到 | 用来做什么 |
|---|---|---|
| 名字是 Machines | Binding 注册的名字 | 让脚本找到它 |
| 属于哪个插件 | 引擎确认的插件身份 | 区分不同插件的同名能力 |
| 全局、stable 模块或 version 模块 | 本次注册选择 | 决定直接使用还是必须 import |
| 有哪些公开方法，参数和返回值是什么 | 实际公开类型、泛型、已有类型注解和 Adapter | 生成 Probe 补全和检查调用 |
| 在哪些执行侧、节点可用 | 注册上下文、实际 backend 和必要的显式范围 | 避免把当前环境没有的能力显示为可用 |
| 说明、引入和废弃信息 | 现有文档注解和发布记录 | 在补全、文档和升级检查中提示作者 |

这份清单就是这里所说的“元数据”。**它由程序整理，脚本作者不用写，普通插件作者也不用再抄一份 JSON。**

### 9.2 为什么需要它

它主要服务三个实际需求：

1. **Probe 补全。**编辑器知道 Machines 有 has 方法，知道参数应该是 ID，知道返回 boolean，而不是只显示一个 Function。
2. **正确暴露。**全局注册就创建全局入口；模块注册就生成对应 import 导出，当前没有安装或不支持的插件不会凭空出现在声明里。
3. **升级检查。**对比上一版生成的清单，可以发现删了方法、改了参数或移动了导入路径，提醒维护者检查兼容性。

方法的实际执行仍来自原来的 Java/JS 实现。清单帮助引擎和工具理解这个实现，不要求作者通过另一套描述语言重新实现它。

### 9.3 哪些自动生成，哪些需要作者决定

自动提取的内容不重复填写：Binding 名字、公开类型中的方法/属性、已有泛型和回调类型、注册的 ScriptType，以及 owner 派生的模块路径。

作者主要负责：

- 写实现，并选择是否放进 stable/version 模块。
- 给动态代理提供已有的 valueType，给反射无法表达的回调/转换补充现有类型注解；不要求普通方法手写全部签名。
- 在确有额外限制时声明范围。只在当前节点发现实现，不足以自动宣称支持所有节点。
- 为稳定行为写必要的说明和测试，例如单位、线程、取消规则、事件时机和资源寿命。程序无法从一个 Java 方法签名推断出这些保证。

稳定桥接的方法可以 stable，同时将某个参数、返回值或回调载荷标成原生依赖；已有类型映射能够确定的原生位置自动提取，动态情况才补充标注。不能将“自动提取到一个原生类型”变成整个 Binding 都不稳定，也不能遗漏原生依赖。

### 9.4 什么时候生成，是否要管理文件

推荐流程：

1. 插件通过原有注册入口提交实现和可选的暴露方式。
2. 引擎收集、检查并冻结注册结果，形成内存中的统一清单。
3. 模块安装器、Probe 和查询工具读取同一份清单，避免实际能调用的内容与补全不一致。
4. 插件构建/验证工具从相同来源生成可打包的说明和兼容基线，正式发布前对比上一版。维护者审阅差异，修正 Java 声明或实现后重新生成，不手工修补生成结果。

开发时可以直接从实际注册结果生成内存描述，不要求先写一个契约 JSON 才允许注册。正式发布需要的契约/基线也应由工具生成并接入已有验证流程；这不是再给普通插件作者增加一份独立的手填清单。

Probe 的 .d.ts 仍走现有生成流程；不为这个设计额外在玩家的 nekojs/config 目录生成需要维护的配置文件。描述格式需要扩展时复用现有 catalog、manifest 和契约模型，不另造互相重复的事实源。

生成清单不等于自动证明业务行为稳定。发布 stable 仍是维护者作出的兼容承诺；兼容检查既要看签名，也要看时机、线程和生命周期的测试。

## 10. Probe、文档和运行时必须一致

Probe 应能显示：操作稳定性、支持范围、引入版本、废弃替代入口，以及具体哪些参数、返回值或回调成员属于原生依赖。

稳定模块的事件参数不能退化成 `Function`、`Object` 或大范围 `any`。版本模块同样需要提供当前 profile 的真实类型；“不保证跨版本”不意味着“不提供补全”。无法证明的动态类型保守使用 `unknown`。

针对不同 ScriptType/profile：

- 当前不存在的导出不给声明，导入时报错。
- 当前存在的稳定成员按其稳定接口发出，不直接照搬实现类全部 public 方法。
- `NativeEvents.onEvent` 显示稳定桥接说明，其回调仍获得当前 profile 的真实原生类型；不能为统一接口而把载荷降为 `Object` / `Function`。
- 原生取消桥接的回调应有明确同步返回类型，例如 `boolean | void`；生成器及运行时须阻止把 Promise 当作同步取消结果。泛泛的 `Function` 或单纯 `void` 回调声明不足以表达这一约束。
- `Native.unwrap` 的对应返回类型通过已知原生映射生成，作者不必自己猜 cast。
- `NekoId` 只读字段和字符串操作在所有支持节点保持相同声明；底层 ID 改名只影响桥接处的原生类型。
- `require` 的已知模块重载与 ESM 导出一致。
- 文档、MCP/查询结果和实际模块导出从同一记录派生，标注字段不是单纯的 JSDoc 装饰。

模块 ID、源码或 IR 可以缓存；Context 内的 Binding、JS 对象和闭包仍按 generation 所属创建和释放，不因公共模块可跨脚本导入就跨 Context 保存 guest 值。

## 11. 内部实现怎么安排

保持 `NekoRuntimeRoot` 为唯一运行时 owner，复用现有 Binding、Event Group、Plugin Hook/Point、模块解析和 Probe 流程。

- 公共契约和纯数据模型位于 common 的适当 API/引擎包；common 保持零 MC/loader 依赖。原生桥接的签名通过节点类型映射发出，不为把 `NativeEvents` 归为稳定就把 NeoForge Event 导入 common。
- MC 字段、原生事件、线程和版本差异在版本树 Adapter 处理。
- 稳定层增加的是明确的契约和必要的视图，不再建立第二套 runtime、事件总线或状态存储。
- 复用 `ID` / `NekoId`、现有原生 ID Adapter、已验证的 managed API 契约/调用机制和生命周期 owner。缺少的行为在原 owner 补齐，不按“稳定层/版本层”再平行复制全部实现。
- 当前 [BindingCatalogEntry](../../common/src/main/java/com/tkisor/nekojs/api/catalog/BindingCatalogEntry.java) 允许助手与原生静态成员组合；稳定模块投影要审定成员，不能把整个组合对象直接改名后导出。
- 当前 [EventCatalogEntry](../../common/src/main/java/com/tkisor/nekojs/api/catalog/EventCatalogEntry.java) 需要携带相应的事件契约资料，而不只记录 Java 事件类和名称。
- 新导入路径不放宽已有 Java 访问、远端来源、owner-thread 或 generation 清理规则。

## 12. 插件怎样注册：默认全局，指定模块才 import

### 12.1 保持现在的入口，只登记一次

普通 Binding 仍在 registerBinding(BindingRegistry) 中注册。增加一个可选的配置回调重载，让作者在同一次登记里选择暴露方式：

| 注册方式 | 脚本怎么使用 | 是否自动创建同名全局 |
|---|---|---|
| 不传暴露配置，沿用当前 register | 直接使用名字 | 是，保持现状 |
| 配置 stable() | 从 stable 模块 import | 否 |
| 配置 versioned() | 从 version 模块 import | 否 |

**这里的 stable()/versioned() 以及新 register 重载是设计示例，当前尚未实现。**指定模块后，这一注册项不会再隐式创建全局别名；模块选择也不是“先注册全局，再导出全局对象”。

同一配置必须明确选择一种目标；空配置、同时选择两种目标或重复配置应报错，不能悄悄回退为全局。默认全局并不自动获得 stable 承诺；名字的暴露位置和稳定性仍按本文分别管理。

下面三种方法分别示意三种选择。实际插件只声明一个 registerBinding 方法，将需要的登记语句放进去；machines/nativeMachines 和两个公开类型由插件提供。

### 12.2 不指定：就是现有全局 Binding

使用现有写法：

```java
@Override
public void registerBinding(BindingRegistry registry) {
    if (registry.scriptType() != ScriptType.SERVER) return;
    registry.register(Binding.of("Machines", machines, MachineOps.class));
}
```

SERVER 脚本直接调用，不需要 import：

```ts
const known: boolean = Machines.has(ID.of("exampletech:press"));
```

既有全局 Binding 的关闭、可见侧和重复名字处理保持原有规则；本次分类不自动移动所有旧 API。

### 12.3 指定 stable：只通过稳定模块使用

目标写法：

```java
@Override
public void registerBinding(BindingRegistry registry) {
    if (registry.scriptType() != ScriptType.SERVER) return;
    registry.register(
            Binding.of("Machines", machines, MachineOps.class),
            expose -> expose.stable());
}
```

模块名由引擎根据插件身份派生。本体进入 nekojs/api/stable；exampletech 插件进入 nekojs/api/stable/exampletech。作者不必再写一遍 sourceBinding、exportBinding、publicType 或契约 ID。

MachineOps 是作者选择公开的类型，例如提供 has(NekoId): boolean。正常方法的参数和返回值由程序提取；引擎在运行时也只暴露这个范围，不能只把 .d.ts 写窄、实际仍开放实现类的全部方法。

stable() 表达作者愿意承担稳定契约，并驱动自动生成和发布检查。它不要求普通作者预先手填一份规范性契约；也不会代替线程、资源或业务行为的验证。

### 12.4 指定 version：只通过版本模块使用

目标写法：

```java
@Override
public void registerBinding(BindingRegistry registry) {
    if (registry.scriptType() != ScriptType.SERVER) return;
    registry.register(
            Binding.of("Machines", nativeMachines, CurrentProfileMachineOps.class),
            expose -> expose.versioned());
}
```

exampletech 插件的对应路径是 nekojs/api/version/exampletech。原生参数和返回值采用当前节点的真实类型，不能退化成 any。

若同一个插件分别提供稳定版和原生版 Machines，它们可以同名，放在两个模块中。脚本按需选择：

```ts
import { ID } from "nekojs/api/stable";
import { Machines } from "nekojs/api/stable/exampletech";
import { Machines as NativeMachines } from "nekojs/api/version/exampletech";

const known: boolean = Machines.has(ID.resource("exampletech:press"));
const nativeMachine = NativeMachines.find("exampletech:press");
```

### 12.5 事件也复用原登记入口

事件继续通过 registerEvents/registerClientEvents 登记，不新增两套稳定/版本事件生命周期。拟议的配置重载可以在同一处指定模块、成员名单和必要的视图转换。

例如原生来源组 ExampleMachineEvents.GROUP 已定义 changed 事件：

```java
@Override
public void registerEvents(EventGroupRegistry registry) {
    registry.register(ExampleMachineEvents.GROUP, expose -> {
        expose.stable();
        expose.events("changed");
        expose.view("changed", MachineChangedView.class, MachineViews::changed);
    });
    registry.register(
            ExampleMachineEvents.GROUP,
            expose -> expose.versioned());
}
```

这个例子的组名为 MachineEvents。MachineChangedView 是只读稳定数据，例如 machineId 和 revision；MachineViews.changed 从对应原生事件提取这些数据。版本入口直接提供当前原生视图。

同一来源发布两个视图时，底层组、原生监听和 owner 仍只有一份。引擎在原分发位置按订阅所选的视图交付；一次订阅只触发一次。显式订阅两个入口可以收到两次各自的回调。

events 是稳定入口的明确成员名单，后来增加原生事件不会自动进入它。来源本来就是自有稳定载荷时，可直接使用，不再机械包装。可取消、带写入或替换结果的事件还需要控制映射，不能只复制字段就宣称适配完成；无 backing 的纯数据视图也不提供原生解包。

### 12.6 引擎负责接线，不让作者重复填表

- Binding 和事件的注册选项由各自现有 Point 收集，加入来源 owner、模块目标、公开类型和范围等信息，冻结后汇入同一个 API 模型。
- 不新增 registerStableApi/registerVersionApi。普通 Binding 和上述事件不再要求覆写 registerApiSurface；该既有 Hook 留给确实需要按符号提供 handler/provider 的高级扩展，原有契约校验不撤掉。
- 模块注册使用 owner + 模块类别 + 名字 + ScriptType 定位，不先经过全局名字表。因此不同插件可以在自己的模块里都导出 Machines；同一位置重复导出则明确失败。
- 已有全局注册继续采用原来的重复名字规则。新模块项不进入全局表，不能因为别的插件有同名全局就被错误挤掉。
- 普通插件不能占用本体根模块或其他插件路径。所有模块解析入口统一保留这些发布路径，避免普通 Node 源码模块抢占名字。
- 插件身份优先由加载器根据所属模组和代码来源取得；无法确定时才要求额外声明并验证。不把 legacy 类名直接拼成公开稳定路径，也不默认要求每个作者再填一遍 owner。
- 适用侧来自现有注册上下文，其他必要范围用同一配置补充。范围不匹配就不导出；CLIENT 原生类也必须使用已有客户端/节点拆分，不能先加载不支持的类再靠标记补救。

这里的“同一 API 模型”就是第 9 节的自动清单，服务运行时、Probe 和文档。它不是要求作者在 registerBinding 之外重新维护一份相同的声明。

### 12.7 当前代码要补什么，以及保持什么边界

现有实现不能直接满足上述形式，后续实现至少包括：

- 给 BindingRegistry/EventGroupRegistry 增加配置重载，扩展 BindingsPoint/EventsPoint/ClientEventsPoint 的产物，使模块项不再被当作 legacy 全局；保留旧重载的默认行为及 Point/Hook/Contributor 配对。
- 从真实登记结果自动提取公开签名和已有类型注解，补齐实际模块导出与 Probe。默认参数、Bean 读写和动态代理类型须与实际 wrapper 一致，不能只生成漂亮的类型文件。
- 普通模块注册不依赖 registerApiSurface 是否因“owner 没有预载契约”被跳过；它走自己所属 Point 的统一收集。已有细粒度 managed API 的验证继续存在，生成的模块描述按其角色并入，而非伪造一个未经验证的已有契约对象。
- [NekoJSBasePluginManager](../../common/src/main/java/com/tkisor/nekojs/core/NekoJSBasePluginManager.java) 的普通发现路径目前会生成 legacy 身份，需要补齐真实模组 owner 的传递。模块身份由引擎处理，不让每个 Binding 重填。
- [JsApiSurfaceResolver](../../common/src/main/java/com/tkisor/nekojs/core/api/JsApiSurfaceResolver.java) 的 nativeReturn 限制、原生类型泄漏检查及尚未填充的模块导出需要适配。稳定桥接只在已声明的原生位置放行，不整体删除稳定类型的防线。
- [EnvironmentScope](../../common/src/main/java/com/tkisor/nekojs/api/surface/EnvironmentScope.java) 当前尚未实际匹配 minecraftVersionRange；范围校验必须落实，不能只是把字段生成出来。
- 数据默认留在内存；构建/Probe 需要落盘时通过已有工具生成。旧的基线和契约兼容规则需明确衔接，不新增要求玩家手填的配置或 API 清单。

继续保持 NekoRuntimeRoot 为唯一 runtime owner，common 不引入 MC/loader 类型。稳定对象可以委托原生实现，纯值复用 ID/NekoId 等既有类型，不并列复制一套世界/玩家状态。

Plugin Runtime 只持有适合其寿命的宿主服务和冻结描述，guest 包装、回调和订阅按 generation 隔离。成功重载才切换；候选失败保留旧 active；关闭不能误清理另一代。默认全局或改用 import 都不改变这些要求。

第三方插件对自己的稳定行为负责，使用其发布/契约兼容线；NekoJS 负责注册框架和本体契约，不替第三方担保业务正确。初始化或契约不匹配时明确报告 owner、模块和成员，不降级成 any、空实现或另一种暴露方式。

## 13. 现有 API 的建议盘点方向

以下全部是候选分类，收录前需要审定，并非宣布现有实现已经获得这些保证。

| 内容 | 建议处理 |
|---|---|
| `ID` / `NekoId` | 首批自有稳定候选；保留默认命名空间和旧构造规则，补充严格资源构造与双向转换 |
| `I18n` 的字符串/语言操作 | 首批稳定候选；保持 CLIENT 限制 |
| Signal、Store 等 NekoJS 状态协议 | 稳定候选；单独审定关闭、重入和异步行为 |
| `ServerEvents.tickPre/tickPost` | 分开审定订阅、时机和载荷；稳定入口使用 NekoJS 视图，保留原生视图 |
| `Item.of` 等助手 | 审定助手与返回对象两层，不直接把 ItemStack 全面稳定化 |
| recipes 等领域 builder | 可审定统一操作；支持范围、原生 JSON 和底层对象分别标注 |
| ClientUI/Workspace | 稳定性与 NeoForge 26.x 等支持范围分开；未完成审定前保留领域模块 |
| `NativeEvents.onEvent` | 稳定桥接候选，从 `nekojs/api/stable` 导入；原生类/回调载荷与 NeoForge STARTUP 支持范围单独标注 |
| 原生 ID 转换、原生对象解包和类加载 | 桥接操作可稳定；输入/返回的原生类型按 profile 提供 |
| 原生类、遗留原生事件视图、直接原生助手 | `nekojs/api/version` 提供真实类型，不作原生成员跨版本保证 |
| MixinJS 的声明 builder | 先 experimental；目标类、descriptor、注入点始终携带版本依赖 |
| 引擎实现类和内部缓存 | 不作为公共稳定入口 |

与 [MixinJS 方案](2026-11-06-mixinjs-design.md) 的关系：builder 可以在未来稳定，具体 Minecraft 注入目标仍需要对应 profile 的字节码和映射验证。本文不改变该方案的结构重启与回调重载边界。

## 14. 迁移顺序

### 阶段 A：盘点与标注

列出当前公开入口、载荷、原生依赖发生的位置、支持范围和现有测试。建立未审定清单，先挑 `ID` / `NekoId`、I18n、纯状态协议、NativeEvents 桥接及少量只读事件形成基线。优先审计已有实现，不能把盘点变成重新包装所有类。

### 阶段 B：增加入口，保留旧行为

实现 `nekojs/api/stable` 和 `nekojs/api/version` 的 Context 本地导出及对应声明。前者包含审定的自有能力与稳定桥接；后者包含原生入口/视图。旧全局入口可按明确迁移安排保留兼容别名；新登记的模块项本身不自动创建全局别名。

对当前透传原生对象的全局事件，保持旧载荷；新稳定模块提供经过定义的视图。明确记载两者差别，不把旧脚本里的 `event.server` 悄悄改成别的对象。

先完成 ID 构造、属性、输入 Adapter、显式双向转换和 Probe 类型的纵向试点，再扩展复杂活对象。`ID.resource` 是新增操作，旧 `ID.of` 不借机改变语义。

### 阶段 C：示例与工具优先迁移

规范示例优先使用明确 import；内置脚本按实际支持范围采用稳定或版本入口。Probe 和查询工具同时提供契约标注，类型消费者覆盖正确与错误调用。

### 阶段 D：按发布政策处理遗留入口

长期保留简单、无冲突且语义完全等价的别名。确需移除的入口按废弃窗口和主版本政策处理，记录替代方式及移除条件；不长期维持两套独立实现。

稳定基线形成前不自动给全部老 API 打 stable，也不通过本次分类撤销已有兼容责任。

## 15. 验收规则

| 检查 | 必须证明 |
|---|---|
| 契约目录 | 每个稳定成员有唯一 ID、行为说明和实际支持矩阵 |
| 类型消费者 | 稳定事件没有意外原生字段；只读字段不能写；错误侧/错误 loader 的导入被拒绝 |
| 桥接类型 | NativeEvents 操作标记稳定且原生载荷精确；解包和 ID 转换不伪造跨平台类型 |
| 稳定 ID | 默认命名空间不变；基础/严格构造各守契约；非法值一致失败；各节点资源 ID 无损往返 |
| 模块运行 | ESM/CommonJS 一致；未知导出明确失败；无跨 Context/generation guest 值复用 |
| 事件 | 一次注册一次触发；阶段、priority、取消传播和旧视图行为符合约定 |
| 重载 | 候选不提前生效，失败保留，移除生效，关闭清理，迟到回调不复活 |
| 支持范围 | 只对真正通过验证的 profile 声明支持，不输出空实现 |
| 插件发布 | owner/资源归属一致；生成说明与实际注册不符、重复导出和占用其他 owner 路径均明确失败；发布检查验证稳定基线 |
| 暴露方式 | 无配置保持全局；选择模块不创建同名全局；空配置或冲突目标不回退 |
| 插件环境 | 未装插件、错误执行侧、MC 范围不匹配不生成对应可用导出；运行时和 Probe 一致 |
| 兼容差异 | 删除、改名、必填参数、返回形状、可空性、取消/线程/阶段变化均被检查 |
| 文档 | Probe、手册、Skill、示例和查询 API 与 runtime 标注一致 |

稳定 API 的测试既包含签名，也包含时机、单位和生命周期。相同 TypeScript 类型不能证明相同行为。

实现时按影响执行 common 完整检查、所涉节点构建、Probe 类型消费者和原生/实机回归；发布前更新规范内嵌资料及生成物。本文本身不授予任何现有 API 新的实机验收结论。

## 16. 本轮交付与未决事项

本轮只交付分类与调用规范，不修改生产实现，不更名现有全局入口，也不新增用户配置文件。

首版入口明确选用 `nekojs/api/stable` 与 `nekojs/api/version`，其中稳定桥接属于前者，不能把 NativeEvents 与其原生载荷整体判为不稳定。NekoJS 自有稳定层复用现有 `ID` / `NekoId` 等能力，通过版本 Adapter 支撑通用操作，并允许支持范围较窄的稳定扩展。

后续实施前还需确定首次稳定基线版本、首批具体成员、ID 错误契约和每个事件的字段契约，并通过对应 profile 验证。本文的新模块、`ID.resource`、显式转换、解包、tick 字段、注册配置重载和相应自动生成接线尚未实现。

本轮验证结果：

- 本地链接 15 个、成对代码块 15 个及 TS/JS 示例语法检查通过。
- 运行临时检查器 `node common/build/api-stability-design-check/verify.cjs`：41 个 TypeScript 模型用例通过，覆盖十一段脚本示例、跨 loader/ID 原生类型、只读字段、桥接类型、CommonJS 类型、插件模块隔离、默认全局及模块项不泄漏全局。
- 四段 Java 注册示例在最小拟议接口模型下编译通过；仅验证示例的类型与调用形式，不代表现有 Plugin API 已有这些方法。
- 临时检查器位于构建目录，不属于生产生成器或正式回归用例。声明模型只验证拟议的类型约束可以表达，不代表模块加载、事件转换或重载机制已经实现。
- 未运行游戏构建或实机验收；本轮仅修订方案文档，没有修改生产代码。
