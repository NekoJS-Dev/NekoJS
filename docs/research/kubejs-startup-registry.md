# KubeJS 与 NekoJS 的 STARTUP 注册 API 对照

**结论：可以采用 `StartupEvents.registry("item", event => { … })` 的外形，但建议把它做成 NekoJS 的 versioned Event Group，沿用当前 Builder 和注册流水线，不把它当作 KubeJS 脚本兼容层。** 保留现有 `RegistryEvents.register` 供旧脚本和一次订阅多个表的脚本使用；两个入口进入同一个启动轮次、同一个仓库。

核查范围：KubeJS 本地源码快照 `D:/kubejs-2101`，[gradle.properties](<D:/kubejs-2101/gradle.properties#L12-L19>) 标明 Minecraft 1.21.1、KubeJS 2101.7.2、NeoForge 21.1.199；NekoJS 为本次工作树。本文只做源码对照；未运行构建、测试或游戏。NekoJS 注册细节另见 [物品与近邻注册调查](<../../docs/research/item-registration.md>)。

## KubeJS 2101 的确切写法

```js
// kubejs/startup_scripts：StartupEvents 是 KubeJS 注入的 Binding，无 import。
StartupEvents.registry("item", event => {
    event.create("ruby").displayName("Ruby").maxStackSize(16);
    event.create("mymod:ruby_sword", "sword");
});
```

- **订阅与选择器：** `StartupEvents.GROUP` 由内置插件注册并注入脚本绑定；`registry` 定义是 STARTUP + `requiredTarget(EventTargetType.REGISTRY)`，不是接受任意表的无键订阅。Rhino 的事件函数把两个参数解释成 selector、callback；第一项也可以由 `ListJS.orSelf` 展开成列表逐个订阅。只有 callback 会走缺少 selector 的校验；事件函数捕获订阅异常并记入脚本日志。监听只允许在脚本装载时挂上，不是在回调里再挂监听。见 [内置组及注入](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/plugin/builtin/BuiltinKubeJSPlugin.java#L398-L419>)、[registry 定义](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/plugin/builtin/event/StartupEvents.java#L14-L20>)、[订阅校验](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/event/EventHandler.java#L111-L149>)、[事件函数入口](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/event/EventHandler.java#L256-L274>)、[列表展开](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/util/ListJS.java#L17-L46>)。
- **selector 解析：** 这是注册表的键，不是物品 ID，也不是 Builder 类型名。监听时先解包，再转换：已有 `ResourceKey` 原样使用，`ResourceLocation` 变为 root registry key，其他值取 `toString()`，空白转 null，否则 `ResourceLocation.parse` 再 `ResourceKey.createRegistryKey`。`"item"` 与 `"minecraft:item"` 同键；不会把裸 selector 补成 `kubejs:item`，也不把 `entityType` 驼峰自动转成 `entity_type`。该 selector 使用 identity map；没有「现有静态表」白名单，语法合法的未知键可以挂上，但没有对应的投递时 callback 不会执行。见 [registry target 与转换](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/event/EventTargetType.java#L28-L33>)、[转换与默认校验器](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/event/EventTargetType.java#L97-L132>)、[target 桶](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/event/TargetedEventHandler.java#L105-L123>)、[按键投递](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/event/EventHandler.java#L189-L215>)。
- **`create` 与 Builder：** `create(id)` 选该表的默认工厂；`create(id, type)` 先按完整类型 ID 查找，再按 path fallback，缺失类型报错，显式类型路径还拒绝带 direct codec 的数据包注册表类型。默认 ITEM 工厂是 `ItemBuilder`，`sword` 是注册过的命名类型。返回 Builder，`displayName(...)` / `maxStackSize(...)` 返回自身，所以可链式配置；没有末尾配置回调，也不需要脚本再调 `build()`。另有 `createCustom(id, supplier)`，用于直接提供对象。见 [create 的三个入口](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/registry/RegistryKubeEvent.java#L23-L88>)、[命名类型索引](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/registry/BuilderTypeRegistryHandler.java#L39-L56>)、[内置 ITEM 工厂](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/plugin/builtin/BuiltinKubeJSPlugin.java#L327-L342>)、[displayName](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/registry/BuilderBase.java#L79-L95>)、[maxStackSize](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/item/ItemBuilder.java#L117-L125>)。
- **对象 ID 的命名空间不同于 selector：** `create` 的对象 ID 和显式类型 ID 的参数都是 `KubeResourceLocation`。Rhino wrapper 调用 `ID.kjs`：裸文本补 `kubejs:`，已有 namespace 保留。因此上例选择 `minecraft:item` 表，却注册 `kubejs:ruby` 和 `mymod:ruby_sword`；裸 `"sword"` 先成为 `kubejs:sword` 再查类型。见 [Rhino wrapper 注册](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/plugin/builtin/BuiltinKubeJSPlugin.java#L509-L516>)、[KubeResourceLocation](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/util/KubeResourceLocation.java#L9-L17>)、[ID.kjs](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/util/ID.java#L75-L104>)。

## KubeJS 的回调与原生注册时序

1. 模组初始化先加载插件，再创建并 reload STARTUP ScriptManager（普通启动，datagen 跳过 reload）；此时顶层脚本挂监听，还没有原生物品。见 [模组启动](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/KubeJS.java#L113-L141>)、[装载期间开启监听](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/script/ScriptManager.java#L151-L204>)。
2. NeoForge 对每个表派发 `RegisterEvent`；KubeJS 用 LOW 优先级接收，针对当前表投递同键的 STARTUP 事件。脚本的 `create` 立刻把未构造的 Builder 放进该表的 `RegistryObjectStorage`，检测该表重复 ID，再返回给脚本继续配置；事件结束调用 `afterPosted`，处理该事件新 Builder 的派生对象。见 [原生事件入口及投递](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/registry/RegistryEventHandler.java#L20-L39>)、[追加派生项及检测重复](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/registry/RegistryKubeEvent.java#L90-L119>)、[afterPosted 调用点](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/event/EventHandler.java#L240-L248>)。
3. **同一个原生 pass** 中遍历当前表的存储，以 `builder::createTransformedObject` 交给 NeoForge，跳过 dummy Builder；这时才创建、转换并保存原生对象。其他表的派生对象进入其各自存储，该 handler 没有重放已过的原生 pass。KubeJS 的 startup reload 命令确实重新评估脚本并通知客户端，但不重新发 NeoForge RegisterEvent，不能据此热加物品。见 [提交 supplier](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/registry/RegistryEventHandler.java#L35-L65>)、[Per-table 存储](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/registry/RegistryObjectStorage.java#L24-L54>)、[原生对象构建](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/registry/BuilderBase.java#L154-L158>)、[startup reload 命令](<D:/kubejs-2101/src/main/java/dev/latvian/mods/kubejs/command/KubeJSCommands.java#L360-L365>)。

## 与 NekoJS 当前接口和建议方案的对照

| 面向脚本/实现 | NekoJS 当前行为 | 借鉴 KubeJS 时的边界 |
| --- | --- | --- |
| 事件入口 | `import { RegistryEvents } from "nekojs:event/versioned"`，再用 `RegistryEvents.register(event => …)`；Event Group 不注入全局。[当前事件定义](<../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryEvents.java#L13-L26>)、[发布规则](<../../docs/agents/coding.md#L25-L35>)。 | 新增 versioned `StartupEvents.registry(selector, callback)`；不恢复 KubeJS 式全局，不承诺 stable 或第三方脚本 ABI。 |
| selector | 现有订阅不按表过滤；`event.item(...)` 指定表。低层 `event.register(registry, id, supplier)` 已能按已知 metadata/sugar 目录解析 `item` 和 `minecraft:item`。[现有解析](<../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryEventJS.java#L184-L208>)、[键目录](<../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryEventJS.java#L252-L270>)。 | 用同一元数据目录归一化到规范 key，确保 `item` 与 `minecraft:item` 只表示一张表。首版只接受字符串及当前节点明确支持的静态表／既有别名，未知、空白或节点不支持的 selector 提前报错；不照搬 Rhino 的任意对象转换、列表或 identity map。未承诺静态提交的键不能因可扫描或有 Builder 而宣传支持。 |
| `create` 与对象 ID | `event.item(id, build => { build.maxStackSize = 16; })` 或 `event.item(id, typeName, build => { … })`，当前 ITEM 默认工厂名为 `basic`。通过 `BuilderSurface` 配置，回调成功后才暂存并冻结；ID 用原版 `Identifier.parse`（1.21.1 经版本映射到 `ResourceLocation.parse`），裸 ID 默认 `minecraft:`，不是 `kubejs:` 或 `nekojs:`。[回调/ID 解析](<../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryEventJS.java#L214-L270>)、[默认工厂](<../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/NekoRegistryPointsPlugin.java#L54-L87>)、[冻结与去重](<../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryRepository.java#L46-L95>)、[版本映射](<../../stonecutter.gradle.kts#L49-L81>)。 | 新事件对象固定在订阅选定的表，新增 `create(id, build)` 和 `create(id, typeName, build)`，委托现有工厂与 Bean 表面；不为语法相似改变 ID、工厂、冻结、重复检查或返回类型。`build.maxStackSize = 16` 保持为属性赋值；不假定 KubeJS 的 `sword` 工厂存在于 NekoJS。迁移现存 KubeJS 世界时把原裸 ID 显式写成 `"kubejs:ruby"`，以保留原注册键；新内容写明自己的 namespace。 |
| Loader 和收集 | STARTUP 脚本先完成执行；NeoForge 首次 `RegisterEvent` 前 `collectOnce()`，再按 NeoForge 的各表 pass drain。Fabric 初始化时直接收集、写入 BuiltInRegistries，先非 ITEM 后单次 ITEM pass；主项注册后生成派生项，目标 pass 已结束则报告而不重跑。[STARTUP 装配](<../../common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeAssembly.java#L157-L169>)、[一次收集与派生](<../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/StartupRegistryRuntime.java#L118-L152>)、[NeoForge 接线](<../../src/main/java/com/tkisor/nekojs/listener/RegistryEventAdapter.java#L50-L80>)、[Fabric 排序](<../../src/fabric/java/com/tkisor/nekojs/fabric/FabricRegistryAdapter.java#L39-L86>)。 | **两个接口在同一个 collectOnce 内触发**，为确定的顺序（例如先既有入口、再按规范 key 派发新入口）明确写出契约，并共享仓库及去重；不要加入每个 native pass 的脚本投递或新的全局 Builder 存储。保留 co-registration 顺序、现有异常隔离和重启要求，不让新语法变成热注册；NekoJS 的 STARTUP reload 命令明确拒绝。[重启边界](<../../docs/research/item-registration.md#L64-L66>)。 |

建议的 NekoJS 接口（**提案，当前不可运行**）：

```js
import { StartupEvents } from "nekojs:event/versioned";

StartupEvents.registry("item", event => {
    event.create("mymod:ruby", build => {
        build.maxStackSize = 16;
        build.rarity = "rare";
    });
});
```

**声明与运行时必须一起做。** NekoJS 的 [keyed startup 工厂](<../../common/src/main/java/com/tkisor/nekojs/api/event/EventGroup.java#L79-L128>) 和 [事件订阅参数](<../../common/src/main/java/com/tkisor/nekojs/api/event/EventBusJS.java#L369-L395>) 并不等价于 KubeJS `requiredTarget`：[参数解析](<../../common/src/main/java/com/tkisor/nekojs/core/api/EventSubscriptionArguments.java>) 支持 keyed bus 的仅 callback 订阅。若决定 selector 必填，需显式约束 `StartupEvents.registry` 的入口及发布元数据，并移除该事件的无键 TS/Python 重载；不能全局禁止其它 keyed bus 的无键订阅。当前 [TS 生成器](<../../common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/EventDeclarationGenerator.java#L251-L262>) 和 [Python 模块生成器](<../../common/src/main/java/com/tkisor/nekojs/probe/backend/python/PythonApiModuleRenderer.java#L379-L398>) 各自输出订阅签名；现有 [STARTUP payload 生成](<../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/StartupRegistryEventSurface.java#L19-L74>) 从 registry infos、factory 及 builder 元数据推导 TS/Python payload。新接口也应沿用该来源，为 `"item"` / `"minecraft:item"` 提供具体的 Item Builder 推断，为命名类型按节点推导重载；不要手改生成文件，也不要用一条 `any` 重载吞掉漏传或无效 selector。

## NekuJS addon：另一个兼容问题

[NekuJS README](<../../addons/nekujs/README.md#L1-L35>) 将其定义为单独安装的 NeoForge 1.21.1 addon；GraalJS 仍运行脚本，NekuJS 提供的是部分 KubeJS/Rhino Java ABI，不能与原 KubeJS/Rhino 同装。它会调用 addon 的 `registerBuilderTypes` 收集元数据，但 [LegacyRuntimeBridge](<../../addons/nekujs/src/main/java/com/tkisor/nekujs/bridge/LegacyRuntimeBridge.java#L43-L58>) 没把这张目录变成本机注册；[BuilderTypeCatalog](<../../addons/nekujs/src/main/java/dev/latvian/mods/kubejs/registry/BuilderTypeCatalog.java#L13-L43>) 的安装方法明确拒绝（`NEKO-4253`）。[legacy EventHandler](<../../addons/nekujs/src/main/java/dev/latvian/mods/kubejs/event/EventHandler.java#L52-L109>) 对真正使用的 targets 也明确拒绝（`NEKO-4217`）；[现有不支持清单](<../../addons/nekujs/README.md#L59-L67>) 还列出 addon Builder 注册、动态注册查询和 data generation context。**新增 NekoJS 原生 Script API 的同名事件，不代表解除这些 addon 限制，也不承诺老脚本原封不动运行。** 若将来确实桥接 Java addon Builder，应另做 NekoJS registry factory/supplier 生命周期与版本的验收。

后续实现时建议用测试核对：selector 短名与完整键等价、漏选/未知键在订阅时被拒、STARTUP 之外不可订阅、同表的不同监听各仅触发一次、旧新入口混用和重复检测、显式 ID 保持、回调失败不入库、方块/流体派生项与 ITEM 顺序，以及各节点 TS/Python 的必选 selector 和 Builder 推断。本文未执行这些测试。
