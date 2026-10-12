# Issue #36：物品注册与资源生成实施计划

> 本文件保留审批时的计划与当时验证状态；实施结果、实际契约和验证限制见 [REPORT.md](REPORT.md)。

日期：2026-10-12。目标分支：`stonecutter`，调查基线：`c6455e0477b2f1336a72fbc717d0d5371ae4709f`。

当前状态：供用户审阅的实施计划；本轮只编写本文和三份源码调查笔记，未实施功能、未运行构建或游戏验收。

需求来源：[NekoJS Issue #36](https://github.com/NekoJS-Dev/NekoJS/issues/36)。参考源码由用户指定为 `D:/kubejs-2601`。

## 1. 建议加入什么

本次围绕“在注册声明里完成物品默认组件和资源配置”扩展已有 ItemBuilder，分成组件与资源两部分。

| 拟新增能力 | 脚本写法 | 首期语义 |
| --- | --- | --- |
| 单个数据组件 | `build.component(id, value)` | 组件 ID 与 JSON 形状的值；写入该物品的默认组件，重复设置同一 ID 时替换之前的显式值。 |
| 批量数据组件 | `build.components = { ... }` | Bean 属性；替换当前显式组件集合。单个方法与批量属性使用同一份规范化状态。 |
| 唱片播放 | `build.jukeboxPlayable = 'minecraft:13'` | 引用歌曲注册键，使用原生 Item.Properties 路径，不在 STARTUP callback 中提前查找数据包歌曲。 |
| 提供锻造材料 | `build.trimMaterial = 'minecraft:quartz'` | 26.x 的“物品提供某种 trim material”语义；不等同于给物品附加已有 ArmorTrim。1.21.1 不提供这个现代原生属性。 |
| 模型父级 | `build.parentModel = 'minecraft:item/handheld'` | Bean 属性；为自动生成的物品模型指定 parent。 |
| 单个纹理槽 | `build.texture(textureId)` / `build.texture(slot, textureId)` | 前者设置 `layer0`，后者设置指定槽，例如 `layer1` 或自定义父模型的命名槽。 |
| 批量纹理槽 | `build.textures = { layer0: 'example:item/base', ... }` | Bean 属性；替换当前纹理表，与 texture 方法共用状态。 |
| BlockItem 配置 | `build.item(item => { ... })` 内使用以上配置 | 沿现有嵌套 ItemBuilder 路径生效，不另建 BlockItem API。 |

保留当前 `RegistryEvents.register(event => ...)` 和 callback-last 写法。上述标量配置提供 getter/setter，属性赋值与显式 setter 使用同一校验路径；多参数操作保留方法。这里的 API 是拟议契约，示例尚不是已可运行的现有功能。

```js
RegistryEvents.register(event => {
  event.item('example:gem', build => {
    build.maxStackSize = 16;
    build.components = {
      'minecraft:custom_data': { category: 'gem' }
    };
    build.component('minecraft:enchantment_glint_override', true);
    build.parentModel = 'minecraft:item/generated';
    build.texture('example:item/gem');
    build.texture('layer1', 'example:item/gem_overlay');
  });

  event.item('example:disc', build => {
    build.maxStackSize = 1;
    build.jukeboxPlayable = 'minecraft:13';
    build.parentModel = 'minecraft:item/generated';
    build.texture('example:item/disc');
  });
});
```

`texture` 配置的是对已有 PNG 资源的引用，不绘制或下载图片。只有显式设置 parentModel、texture 或 textures 才启用自动物品资源生成；未使用新配置的注册声明保持原行为。启用后，缺省 parent 为 `minecraft:item/generated`，缺省 layer0 为 `<item namespace>:item/<item path>`。

## 2. 现状与参考源码结论

| 核对项 | 当前事实 | 对计划的影响 |
| --- | --- | --- |
| NekoJS ItemBuilder | 两个版本源都没有通用组件、唱片、trim 或模型/纹理配置；已有堆叠、耐久、稀有度、食物等配置。 | 新增上述成员；不重做已有基础属性。 |
| 注册时机 | callback 成功后才入 RegistryRepository；对象由平台注册 pass 消费 supplier 创建。 | 配置阶段只记录意图，不写资源、不提前注册组件或物品。 |
| 嵌套 BlockItem | BlockBuilder 使用子 ItemBuilder 的 buildProperties，绕过 ItemBuilder.build。 | 组件放在共享 properties 路径；资源收集必须覆盖子 builder，不能只挂 build 方法。 |
| 既有组件适配 | ItemStackAdapter 已有 JSON patch 路径，但 DataComponentsAdapter 使用普通 JsonOps。 | 复用转换与错误机制；它不能直接证明 STARTUP 的动态注册表组件可解码。 |
| 既有 Assets | NeoForge 有生成器和已挂载磁盘资源包；itemModel 只写 models/item JSON。 | 复用安全写入和资源根，补自动物品的版本化资源编排。 |
| 资源生成时机 | 当前客户端在 resource reload listener 中执行生成；文档说明资源在下一次 reload 生效。 | 必须核实首次加载时序，避免把“写出文件”当作“已加载模型”。 |
| Fabric | 当前没有 Assets 绑定、生成事件桥或对应资源包挂载。 | 首期不承诺自动模型；仅写文件不足以支持它。 |
| KubeJS | 指定目录是 8.0.6／MC 26.1.2 源码快照，无 Git 元数据；有 component、jukeboxPlayable、模型和纹理配置，没有 trimMaterial helper。 | 参考 API 行为和资源格式，不照搬其 Rhino 转换、虚拟资源包及生命周期。 |

主要依据：[26.x ItemBuilder](D:/mcmodDemo/NekoJS-mult/src/main/java/com/tkisor/nekojs/wrapper/registry/gen/ItemBuilder.java:36)、[1.21.1 ItemBuilder](D:/mcmodDemo/NekoJS-mult/versions/1.21.1/src/main/java/com/tkisor/nekojs/wrapper/registry/gen/ItemBuilder.java:27)、[注册 callback](D:/mcmodDemo/NekoJS-mult/src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryEventJS.java:209)、[BlockItem 路径](D:/mcmodDemo/NekoJS-mult/src/main/java/com/tkisor/nekojs/wrapper/registry/gen/BlockBuilder.java:194)、[当前组件适配](D:/mcmodDemo/NekoJS-mult/src/main/java/com/tkisor/nekojs/js/type_adapter/DataComponentsAdapter.java:1)、[资源写入](D:/mcmodDemo/NekoJS-mult/common/src/main/java/com/tkisor/nekojs/wrapper/AssetGeneratorJS.java:89)、[客户端生成时机](D:/mcmodDemo/NekoJS-mult/src/main/java/com/tkisor/nekojs/client/NekoJSClient.java:83)、[KubeJS 物品资源格式](D:/kubejs-2601/src/main/java/dev/latvian/mods/kubejs/generator/KubeAssetGenerator.java:51)。详细路径、映射依赖检查及边界见文末三份笔记。

## 3. 首期平台范围

“计划支持”不表示已经验证。最终必须按此表完成实现与验收，或在交付时逐项报告未完成。

| 节点 | 通用组件与唱片 | trimMaterial | 自动模型/纹理引用 |
| --- | --- | --- | --- |
| NeoForge 26.1.2 | 计划支持 | 计划支持 | 计划支持，主要验收节点 |
| NeoForge 26.2.0 | 计划支持 | 计划支持 | 计划支持，单独验证客户端格式 |
| NeoForge 1.21.1 | 计划支持，按旧版组件 codec | 不提供现代原生属性 | 计划支持旧版 model 格式，不新增 Assets 全局绑定 |
| Fabric 26.1.2／26.2.0 | 计划支持，验证真实注册与延迟组件初始化 | 计划支持 | 首期不支持；使用新资源配置时明确报告节点不支持，不能静默写未挂载文件 |

Fabric 自动模型作为后续独立阶段：仅补物品生成资源的客户端挂载与加载时序。是否进一步开放完整 Assets 绑定/事件另定，不与本次核心组件实现捆绑。

1.21.1 的 TRIM 是 ArmorTrim，而 26.x 的 PROVIDES_TRIM_MATERIAL 表示物品可作为锻造材料，不能互相替代。trimMaterial 只在真实支持的版本源和派生声明里出现，不通过虚假别名掩盖差异。

## 4. 组件怎么实现

### 4.1 输入、顺序与冻结

1. 使用 namespaced 字符串 ID 和 JSON 形状的值。查组件类型、转换和 codec 校验在 Minecraft-facing 边界完成；common 不引入 DataComponentType、Item.Properties 或 loader 类型。
2. 以同一份 canonical 组件表承载 component 方法和 components 属性。批量赋值先完整转换/校验，再替换，失败不留下半份表；读取返回受保护的视图或副本。首期不把 null 暗中解释为“删除默认组件”。
3. 普通 Builder 属性先形成原生默认值，显式组件覆盖可覆盖的默认值；重复显式 ID 最后一次设置生效。显式组件与 jukeboxPlayable／trimMaterial 同时配置同一组件时拒绝冲突，避免依赖未验证的立即/延迟 initializer 合并顺序。
4. 新组件、parent 与 textures 均进入现有 normalizedPropertyReadings／定义指纹；稳定排序和深复制 JSON，使声明等价时指纹一致。单个方法修改不能躲开指纹。冻结后的组件快照必须同时用于 properties 构建和登记，不在两处读取不同的可变 builder 状态。
5. unknown ID、非法值、当前版本不存在的类型、无法满足的 registry lookup 和不合法的堆叠/耐久组合，均报告物品 ID、组件 ID、节点和原失败原因。使用稳定 NEKO 诊断码，实施时检查现有编号并同步中英文错误索引。

### 4.2 动态注册表与便捷属性

jukeboxPlayable 和 trimMaterial 记录 ResourceKey，调用各版本原生 Properties 方法；不在配置 callback 中要求歌曲/材料 holder 已加载。它们引用现有或数据包提供的条目，不顺带新建歌曲、声音、锻造材料注册系统。

通用 JSON 组件分为可立即解码的值与需要 registry provider 的值。第一阶段必须验证原生 delayedComponent／initializer 的可用接缝，以及旧版可表达范围；若不能为某类值建立可靠的延迟解码路径，就明确拒绝该类输入并列入支持表，不能宣称“所有组件都支持”或吞掉 codec 错误。便捷属性的原生 key 路径独立覆盖唱片和现代 trim 需求。

配置冻结不意味着 Minecraft 注册批次可以回滚。结构与可立即解码的值在注册前预检；依赖 provider 的解析可能晚于物品登记，失败必须在实际加载阶段保留原因并中止相应加载，不承诺撤销已注册的原生对象。测试需要区分 callback／supplier 失败与晚初始化失败。

现有 ItemStackAdapter 的已发布输入行为保持不变。若抽共享解码器，需要同时跑 stack 与注册回归；ItemEvents.modification 的 reload、snapshot 和同步机制不改。

## 5. 模型与资源怎么实现

### 5.1 注册与客户端之间的桥接

推荐流程：

```text
RegistryEvents.register callback
  -> 规范化并冻结 ItemBuilder／嵌套 BlockItem 配置
  -> 原有 RegistryRepository 与平台注册 pass
  -> 注册成功后保留不可变的物品资源定义
  -> 客户端资源准备阶段按版本输出模型与物品定义
  -> 现有 NekoJS 资源包挂载、读取与 reload
```

资源定义由既有 startup runtime／root 授权的注册域持有，属于本次游戏启动，不属于普通脚本 reload 的 generation。失败的 callback、重复 ID、supplier 创建失败、noItem 或未真正注册的子物品，不发布资源定义。

配置 setter 和 ItemBuilder.build 均不直接写磁盘。新增的是一段消费注册快照的物品资源编排，复用 AssetGeneratorJS／DataGeneratorJS 和同一资源根，不新增事件、Plugin Point、通用资源框架或运行时所有者。

自动生成放在现有 plugin/script 显式资源贡献之前，但必须早于客户端实际读取对应模型。先核实当前 reload listener 顺序；若 postClientGeneration 已太晚，只把自动物品输出前移到合法的资源准备时机，不无条件触发循环 reload。验收目标是首次加载可见、F3+T 后一致，不能要求用户手动多重载一次来掩盖时序问题。

### 5.2 版本化输出

| 版本 | 自动输出 |
| --- | --- |
| 1.21.1 | `assets/<ns>/models/item/<path>.json`，包含 parent 与 textures。 |
| 26.1.2／26.2.0 | model JSON 加 `assets/<ns>/items/<path>.json`；客户端定义指向 `<ns>:item/<path>`。26.2.0 必须实测确认，不仅套用 KubeJS 26.1.2 样本。 |

26.x 基本客户端定义目标形状：

```json
{
  "model": {
    "type": "minecraft:model",
    "model": "example:item/gem"
  }
}
```

模型 parent、纹理槽和引用共享规范化规则，保留物品 namespace 与子目录。版本选择在 facade／既有节点源完成；common 中只有不依赖 MC 的输入和 JSON 输出规则。既有 Assets.itemModel 的“单独写模型”行为不因本次自动生成而悄悄扩写，自动流程负责组合需要的两个文件。

### 5.3 文件冲突与更新

- 本机制尚未拥有的同路径文件视为手写/外部文件，自动输出不覆盖；给出可定位的跳过原因。
- 自动输出只更新自己拥有、且仍与上次输出一致的文件；显式脚本、插件或用户修改后，该文件不再按自动文件覆盖或清理。
- 在既有资源写入基础上，为自动物品输出增加有限的来源与内容指纹记录，持久化位置在实施阶段确定；记录仅管理本机制输出。失效定义只清理仍被本机制拥有的旧文件，不删除用户资源或纹理。
- model 与 client definition 是一组：先完整校验两份内容，再准备并提交；失败不能把新旧两代文件拼在一起。复用已有原子写入，补这组输出的失败保留检查。
- 显式 Assets/plugin 输出继续按既有语义工作，并可在自动输出后覆盖默认值；不把 non-Assets DataGenerationBatch 的保护规则误称为现有 Assets 行为。
- 现有资源包默认 TOP 和外部资源包排序不改；实际跨包覆盖以客户端验收为准。

## 6. 预计修改哪些文件

这是实施文件地图，不表示所有列出的文件都必须修改。若现有机制足够，优先复用；最终 diff 必须与实际需要对应。

| 文件／模块 | 类型 | 拟修改内容 |
| --- | --- | --- |
| [共享 ItemBuilder](D:/mcmodDemo/NekoJS-mult/src/main/java/com/tkisor/nekojs/wrapper/registry/gen/ItemBuilder.java:36) | 修改 | 组件表、component 方法、歌曲/trim 属性、parent/textures 配置；buildProperties 应用同一快照。 |
| [1.21.1 ItemBuilder](D:/mcmodDemo/NekoJS-mult/versions/1.21.1/src/main/java/com/tkisor/nekojs/wrapper/registry/gen/ItemBuilder.java:27) | 修改 | 通用组件、歌曲和模型配置；保留旧版原生组件/食物差异，不添加现代 trimMaterial。 |
| [BlockBuilder](D:/mcmodDemo/NekoJS-mult/src/main/java/com/tkisor/nekojs/wrapper/registry/gen/BlockBuilder.java:126) 与旧版对应源 | 复用／必要时修改 | 子 ItemBuilder 快照与成功注册的资源贡献，保留 noItem 与原 co-registration。 |
| [DataComponentsAdapter](D:/mcmodDemo/NekoJS-mult/src/main/java/com/tkisor/nekojs/js/type_adapter/DataComponentsAdapter.java:1) 与版本 facade | 复用／扩展 | 抽最小共享转换规则，建立需要 provider 时的延迟解码边界；不改变既有 stack 默认行为。 |
| [RegistryEventJS](D:/mcmodDemo/NekoJS-mult/src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryEventJS.java:209)、[StartupRegistryRuntime](D:/mcmodDemo/NekoJS-mult/src/main/java/com/tkisor/nekojs/wrapper/registry/gen/StartupRegistryRuntime.java:142) | 必要时修改 | 新状态冻结与仅成功注册后的资源定义收集；不重写注册 pass。 |
| [RegistryBuilderContract](D:/mcmodDemo/NekoJS-mult/src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryBuilderContract.java:86)／BuilderSurface／RegistryBuilderSurfaces | 复用／缺口修补 | 确保 method 更新的 canonical 属性进入指纹、Bean parity 和派生声明；无需改动时保持不变。 |
| common 中物品资源定义／JSON 编排辅助 | 按需新增少量内部类型 | 只承载 ID、parent、texture 表、格式和输出，处理确定性与自动文件归属；不包含 MC/loader 对象。类型名称在实现时确定。 |
| [AssetGeneratorJS](D:/mcmodDemo/NekoJS-mult/common/src/main/java/com/tkisor/nekojs/wrapper/AssetGeneratorJS.java:89)／[DataGeneratorJS](D:/mcmodDemo/NekoJS-mult/common/src/main/java/com/tkisor/nekojs/wrapper/DataGeneratorJS.java:149) | 复用／必要时扩展 | 用现有安全路径、配额与原子写入处理自动输出及成组提交，保留现有显式资产 API。 |
| [NekoJSClient](D:/mcmodDemo/NekoJS-mult/src/main/java/com/tkisor/nekojs/client/NekoJSClient.java:83) 与 1.21.1 client 源 | 修改 | 接入自动物品生成及首次资源加载时序；专用服不访问客户端类。 |
| [NekoJSPackLoader](D:/mcmodDemo/NekoJS-mult/src/main/java/com/tkisor/nekojs/resource/NekoJSPackLoader.java:24)／各 NekoJSPathPackResources | 验证为主 | 复用挂载、确认读回与排序；只有加载时序接缝确实需要时修改。 |
| [NekoRegistryDeclarations](D:/mcmodDemo/NekoJS-mult/src/main/java/com/tkisor/nekojs/wrapper/registry/gen/NekoRegistryDeclarations.java:46)、契约 golden、Probe fixtures | 修改／再生成 | 同步存量手写入口与自动 TS/Python Builder 声明；不手补生成文件。 |
| `wiki/registering-new-content_{cn,us}.md`、`wiki/error-reference_{cn,us}.md`、示例与 capability 说明 | 修改 | 新 API、codec 输入、节点范围、冲突规则、资源位置和需重启的边界。 |

首期不新增依赖，不修改 KubeJS 源码，不引入 Minecraft/loader 到 common，不增加新的注册表类型、动态注册 API、运行期 Item modification 或通用资源事件。KubeJS 仅作为参考源码，其 LGPL 实现不直接复制到本项目。

## 7. 实施顺序与检查点

| 阶段 | 交付 | 进入下一阶段的条件 |
| --- | --- | --- |
| P0：可行性与现状固定 | 组件初始化/provider 的小验证、属性覆盖顺序、资源加载时序 trace；记录真实版本差异。 | 能解释动态引用在哪里解析，以及自动 JSON 如何在首次模型加载前可见；不以普通 JsonOps 或文件存在冒充证明。 |
| P1：默认组件 | 单个/批量组件、jukeboxPlayable、26.x trimMaterial；同一快照、指纹、错误与版本行为。 | Graal 配置 parity、注册默认值、晚初始化引用、失败不发布和 stack 非回归通过。 |
| P2：资源声明 | parentModel、texture/textures、普通物品与 BlockItem 快照收集。 | callback/supplier 失败无资源贡献；noItem 无输出；资源字段变化进入指纹。 |
| P3：NeoForge 生成与加载 | 旧版模型、26.x 成对文件、归属记录、冲突/更新/清理、正确加载时机。 | 输出回读与失败保留通过；三个 NeoForge 客户端模型实际可见，专用服安全。 |
| P4：声明与交付 | TS/Python、golden 再生成、文档、全部适用门禁和本地 commit。 | 最终 diff 已审查，未降低门禁；逐项记录 passed/failed/not run 与实际人工验收结论。 |
| 后续可选：Fabric 自动资源 | 窄客户端资源包挂载与对应 smoke。 | 单独实现与验收；首期能力表仍明确不支持自动模型。 |

完成计划阶段后先交给用户审阅；本文不是已经实施或通过验收的证明。

## 8. 怎么验证

| 行为 | 验证目标 |
| --- | --- |
| 默认组件 | 注册后的 Item 默认组件与新 ItemStack 可观察值正确；不能仅断言 builder map。 |
| 批量原子性 | unknown ID／坏值不留下半份组件；已有字段组合是否合法由原生约束验证。 |
| 延迟引用 | 真实 loader/注册表环境中的歌曲、现代 trim 引用及缺失条目失败；验证 provider 时机。 |
| 状态与属性 | component 方法与批量属性进入同一指纹；setter/property parity；JSON 深复制；冻结后别名修改不影响发布状态。 |
| 注册路径 | 一次收集、重复 ID、callback 和 supplier 失败、嵌套 BlockItem、noItem；没有未注册物品的资源输出。 |
| 资源内容 | 缺省/自定义 parent、多个 texture 槽、namespace/子目录、1.21 与 26 格式、确定性字节与回读。 |
| 文件生命周期 | 首次生成、下次启动变更、删除声明、手改自动文件、已存在手写文件、显式覆盖、成对写入中途失败。 |
| 客户端 | 初次启动没有缺失模型；背包/手持/掉落显示，F3+T 后仍正确；三个 NeoForge 节点分别验收。 |
| 平台与隔离 | 五节点组件行为及实际成员面一致；Fabric 模型配置明确拒绝；专用服无客户端类链接/资源生成副作用。 |
| 声明 | TS/Python 与 runtime 同源，1.21 不发射 trimMaterial；无宽泛 any/no-op 掩盖平台差异。 |

扩展现有 BuilderSetterPropertyParityTest、StartupRegistryRuntimeEndToEndTest、RegistryBuilderSurfaceGoldenTest、StartupRegistryDefaultDeclarationsTest、AssetGeneratorJSTest 和 Ticket29AssetsReadbackFixtureTest；必要时新增注册组件、自动物品资源的集成 fixture。需要 vanilla/loader 的断言放到实际可执行的 loader-backed 验证中，不能把 assumption skip 算作覆盖。

实施后计划执行的基本门禁：

```powershell
./gradlew.bat :common:check :common-api-processor:test guardLint --console=plain
./gradlew.bat :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm.cmd run test:probe-types
./gradlew.bat :26.1.2:runGameTestServer --console=plain
```

另按受影响的实际 task/fixture 运行节点定向测试、capability 门禁和新 STARTUP 声明严格类型检查。已有 npm fixture 通过不能替代新 Builder 声明验收；GameTestServer 不能证明客户端模型已加载。

本轮验证状态：源码与本地映射依赖只读调查完成；Markdown 路径与格式检查完成；上述构建、测试和游戏验收均未运行。当前没有维护者对新功能的人工验收结论，也没有修改或关闭 GitHub Issue。

## 9. 明确不在首期加入的内容

- 自定义 DataComponentType 注册和 codec 插件框架；随意 Java 对象、函数值作为 JSON 组件输入；通用组件移除语法。
- 1.21.1 现代 trim-material 的语义模拟，或把 ArmorTrim 冒充提供锻造材料。
- 完整 KubeJS modelGenerator、模型 elements/动画/tints/条件模型、PNG 编辑器；这些高级模型仍可使用已有显式资源路径。
- 唱片歌曲/声音或 trim 材料的数据包注册 DSL；本次 helper 只引用这些条目。
- 在普通 reload 中重新注册 Item 或热更默认组件；STARTUP 注册变更仍需重启，资源 reload 只重新物化已冻结的资源声明。
- 重新设计 ItemEvents.modification、全部 Fabric Assets parity、删除旧公开接口、放宽资源安全/配额和改写包排序。

## 10. 调查附件

三个 GPT-6 Luna xhigh 子代理只读调查生产源码和本地映射依赖，各自仅写一个调查笔记；计划正文由主代理整理。没有子代理实施功能。

- [NekoJS 物品注册与组件](D:/mcmodDemo/NekoJS-mult/docs/architecture-refactor/evidence/2026-10-12-issue36-plan/neko-item-research.md)。
- [KubeJS 参考实现](D:/mcmodDemo/NekoJS-mult/docs/architecture-refactor/evidence/2026-10-12-issue36-plan/kubejs-research.md)。
- [NekoJS 资源生成与版本](D:/mcmodDemo/NekoJS-mult/docs/architecture-refactor/evidence/2026-10-12-issue36-plan/neko-assets-research.md)。

历史票据与 ADR 用于理解既有约束；当旧文档与当前实现/编码约定不同，以本次源码证据和现行约定为准。本计划不假定历史票据勾选意味着新功能已通过客户端或跨节点验收。
