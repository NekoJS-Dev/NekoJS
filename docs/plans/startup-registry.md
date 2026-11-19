# NekoJS 按注册表订阅 STARTUP：实施计划

状态：代码已实现，定向验证通过，完整验收待完成。**同一次迁移直接移除旧的 `RegistryEvents` 脚本事件组，改用唯一入口 `StartupEvents.registry(selector, callback)`。** 这是 versioned Script API 的破坏性迁移，不保留运行时别名或双入口；旧脚本需要改写，但注册对象 ID、资源路径、存档和网络格式不迁移。

## 对外契约与迁移

```js
import { StartupEvents } from "nekojs:event/versioned";

StartupEvents.registry("item", event => {
    event.create("mymod:ruby", build => {
        build.maxStackSize = 16;
        build.rarity = "rare";
    });
});
```

- 仅 STARTUP / versioned，显式导入，不新增全局。选择器必传；保留现有可选事件优先级，不支持列表或无键订阅。`item` 与 `minecraft:item` 归一化为同一键；原版多词表用原生路径（如 `entity_type`）；模组表用完整键（如 `example:machines`）。选择器的命名空间不影响条目 ID。不同拼写分别订阅时是两个监听，各执行一次。 只有在本节点的静态注册目录中显式开放的表才可订阅；合法但未开放的键在订阅时明确报错。
- `event.create(id, build)` 使用该表默认 Builder；`event.create(id, typeName, build)` 使用该表的命名 Builder。没有默认工厂就不能省略类型名。保留 Bean 属性赋值、末尾配置回调、既有 Builder 返回值和条目 ID 解析；不加链式 setter，不改成 `kubejs:`。回调成功才暂存并调用 Builder 的冻结钩子，不改变同表同 ID 的去重及错误隔离。
- `event.createCustom(id, supplier)` 是原低层 supplier 的替代：回调只提交 supplier，实际对象在原生提交阶段创建，仍受脚本访问控制与现有 supplier 校验。它不要求默认 Builder，也不是自动构造器；错误返回值、无法提交或违反加载器约束均按原生注册失败处理。没有可靠条目类型信息时不声称能验证任意第三方对象的 Java 类型。

| 旧写法 | 新写法 |
| --- | --- |
| `event.item(id, build)`、`event.block(id, build)` | 分别在 `StartupEvents.registry("item", …)`、`StartupEvents.registry("block", …)` 中调用 `event.create(id, build)`。原来一个监听注册多个表时拆成按表监听；不依赖跨表回调执行顺序。 |
| `event.item(id, typeName, build)` | item 回调中调用 `event.create(id, typeName, build)`。 |
| `event.custom(id, typeName, build)` | 显式订阅原类型所属表，再调用 `event.create(id, typeName, build)`；不再全局猜类型归属。 |
| `event.register(registry, id, supplier)` | 显式订阅对应表，再调用 `event.createCustom(id, supplier)`。 |

范围仅限普通内容的 STARTUP 注册。`CapabilityEvents`、`GoalEvents`、实验性 `DynamicRegistryEvents` 与数据包注册表不迁移。仍需完整重启才能增删改普通注册；不改 loader 的原生 pass 顺序、方块物品/流体桶等派生项规则、资源生成及存档 ID。

## 注册目标显式声明

本次不做自动发现：不枚举原生根表生成 Script API，不因扫描到键、看到原生注册事件或存在 Builder 就开放选择器。保留现有的只读查询、配方发现和注册表元数据扫描；这些不是 STARTUP 写入权限。

1. **核心显式开放内置目标。** 在版本树通过现有 [RegistryInfosPoint](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryInfosPoint.java#L42-L109) 的贡献方式表达「该节点开放此静态表」，与仅有表键元数据区分。核心贡献经过确认的内置表，按节点排除不支持的目标，如 Fabric 流体和数据包附魔。根表存在与否不改变可订阅目录。
2. **插件显式开放模组表。** 宿主模组先通过加载器创建原生静态表；NekoJS 插件再明确提供完整表键和节点支持范围。「先创建」是原生提交的前提，不要求 bootstrap 时表已安装。没有插件声明的表不会出现在这个入口和补全中。已有表新增命名 Builder 只需现有 [RegistryTypesPoint](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryTypesPoint.java#L39-L91)；没有 Builder 的显式目标仍可用 `createCustom`，前提是调用方能提供合法原生对象。复用既有 Contributor，不新增注册 Plugin Hook，也不让 `common` 依赖 Minecraft 类型。
3. **按目录投递，按加载器规则提交。** 订阅时规范化键并验证其在显式目录中，但不在 bootstrap 检查原生表是否已安装。保留一次 STARTUP 收集；按规范键确定性投递，每键一次，沿用同键监听的优先级。在 NeoForge [实际注册事件](../../src/main/java/com/tkisor/nekojs/listener/RegistryEventAdapter.java#L61-L76)提交对应表，缺少 pass 时在加载结束报告。Fabric 继续在[现有 drain 窗口](../../src/fabric/java/com/tkisor/nekojs/fabric/FabricRegistryAdapter.java#L71-L119)按键查已开放目标并提交；这是必要的原生查找，不是把查到的表自动开放。缺表、注册拒绝、缺 Builder 分别诊断；不绕冻结或重放 pass。

数据包表继续走资源加载。不猜条目构造器，不加通用 JSON 注册；配方的 Codec 机制不在本次迁移范围内。此前的自动发现对照仅作[调查记录](../research/native-registry-discovery.md)，不是实施项。

## 实施顺序与验收

1. **替换事件和对象接口。** 新增官方 versioned `StartupEvents.registry`，回调对象绑定单一表。共享 [现有工厂、配置、暂存](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryEventJS.java#L184-L249)及[启动运行时与派生项](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/StartupRegistryRuntime.java#L118-L152)，不建立第二仓库。删除旧 `RegistryEvents` 的发布、投递和仅为跨表 sugar / 全局类型解析服务的旧成员；共享底层代码按仍有消费者保留。新增迁移示例，测试旧事件已不再导出而非可选别名。
2. **接线两种 loader 和诊断。** 从显式开放目录投递 keyed 监听，沿原生路径提交。测试一个真实模组静态表：原生表已安装但未显式开放时不能订阅；插件声明后可使用 `createCustom`，另加命名 Builder 后可用 `create`。另测已声明但原生缺表、未收到 pass、Fabric 原生拒绝、同 path 不同命名空间及派生项顺序。
3. **同步声明与文档。** 延续 [Builder 声明来源](../../src/main/java/com/tkisor/nekojs/wrapper/registry/gen/StartupRegistryEventSurface.java#L19-L58)，运行时、TS/Python、Probe 补全和 authoring 事件目录共用同一份按节点显式开放的目标目录和工厂元数据。内置短名/完整键、已开放的模组完整键生成明确重载；无工厂只暴露 `createCustom`，无 Builder class 仅降低该 Builder 的推断。将旧导出移除同步到声明、MCP 目录、golden、随包英文 handbook/可执行示例和中英 Wiki；不手改派生文件。

| 验证层级 | 必过项 |
| --- | --- |
| 脚本 / 插件 | `item` 与 `minecraft:item` 同表；非法、缺失和未开放的选择器被拒；作用域仅 STARTUP；默认/命名/缺工厂；supplier 延迟执行及失败隔离；重复项首条保留；配置抛错不入库；旧组不再可导入；其他 keyed 事件不受影响。 |
| Loader / 生命周期 | 原生模组表不会自动开放；显式声明后验证真实提交。NeoForge 覆盖缺 pass，Fabric 覆盖窗口缺表与原生拒绝；两端覆盖内置 item/block、BlockItem 派生、NeoForge 流体派生、ITEM 空 pass、资源生成、重启和 epoch 隔离。替代 sink 不能替代真实注册证据。 |
| 声明 / 文档 | TS/Python 选择器必填、缩写/完整键推断一致、只补全显式开放的目标、缺工厂不暴露 `create`、旧入口不出现在生成目录或推荐示例；golden 只接受经审查的有意变化。 |
| 命令与矩阵 | 从 `ModuleEventExposureTest`、`RegistryStartupMinimalExampleTest`、`StartupRegistryRuntimeEndToEndTest`、`StartupRegistryDefaultDeclarationsTest` 等相关用例开始；然后运行 `:common:check`、`guardLint`、`npm run test:probe-types`，按[仓库验证表](../agents/coding.md#L134-L153)覆盖 NeoForge 1.21.1 / 26.1.2 / 26.2.0 与 Fabric 26.1.2 / 26.2.0 的适用 build/test 和游戏 smoke。记录通过、失败、未跑，最后由 maintainer 实际验收。 |

实施验证：五个节点主源码编译、`guardLint` 和公共模块依赖隔离检查通过。三个 NeoForge 节点的注册定向测试通过（1.21.1：61 个、4 个跳过；26.1.2 / 26.2.0：各 80 个、7 个跳过）；两个 Fabric 节点各 11 个 drain 用例通过。NeoForge 26.1.2 事件门禁及公共模块的必填选择器、随包示例和声明相关定向测试通过。公共模块全量测试仍有失败；首次 TypeScript 全量检查因生成夹具缺失未通过。平台验证在无旧缓存的源码副本运行；未做 Minecraft 实机验收。
