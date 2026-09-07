# PR #37 维护者体验与架构证据

## 0. 证据口径

- 目标：调查历史 PR #37，优先回答维护者痛点，再回答 Java 插件作者和脚本作者的设计取舍。
- 本报告只写证据、事实与明确标记的推断；没有修改 Java、Gradle、Stonecutter、脚本或既有文档。
- PR 远程事实来自 GitHub API、commit diff、commit 源码和 PR 正文，不把搜索摘要当作事实源。
- 当前实现事实来自本地 `NekoJS-mult` 版本树、git 历史和 `docs/adr`；它们用于验证哪些方向后来被采用。
- “事实”指 PR/API/源码直接写出的内容；“推断”指根据调用关系归纳的维护成本或架构含义。
- 远程 PR 可完整取证：API compare 返回全部 15 个文件的 patch。普通 PR 页面没有可用的 reviewer 讨论，但这不影响提交 diff 取证。
- PR 的 issue comments、review、review comments API 均为空；不能把后续 ADR 或本地决策冒充 PR reviewer 意见。
- 远程入口：https://github.com/NekoJS-Dev/NekoJS/pull/37
- 完整 compare：https://api.github.com/repos/NekoJS-Dev/NekoJS/compare/621f4656dbcbb7e4b656f01aab8835ba39eedcd6...7c2fce53929bc6b6660f404459ebfca5a88c3abd

## 1. PR 身份与目标

- 标题：`feat: Skeleton for a generic registry system`。
- 状态：open、未 merged；base 是 `master` SHA `621f4656`，head 是 `genericregistry` SHA `7c2fce53`。
- 规模：3 个提交、15 个文件、914 行新增、5 行删除。
- 作者：ZZZank；提交顺序显示设计从 API 骨架到生命周期补丁，再到“i give up”式收尾。
- 提交 1 `1cb9149f`：`generic registry event (actually only API)`，新增最初 6 个通用注册表类；https://github.com/NekoJS-Dev/NekoJS/commit/1cb9149f134cff876c18913f0494ccfd744370c2
- 提交 2 `8b9ba6bd`：`allow callback on extension point finish`，修改扩展点和 bootstrap；https://github.com/NekoJS-Dev/NekoJS/commit/8b9ba6bde35c693874414a688985379dc78afb99
- 提交 3 `7c2fce53`：`i give up`，加入插件接线、Item builder 和设计文档；https://github.com/NekoJS-Dev/NekoJS/commit/7c2fce53929bc6b6660f404459ebfca5a88c3abd
- PR 正文明确说，在“繁复的系统架构”下开发“痛苦万分”，作者把剩余事项记录到 `docs/MINECRAFT_REGISTRY.md`。
- 直接目标是把按注册表逐个手写的事件/构建器路线，抽成“RegistryInfo + 命名对象类型工厂 + 通用 RegistryEventJS”。
- 首个垂直切片只实现 NeoForge `26.2` 的 Item `basic` builder，既不是全量注册能力，也不是 Fabric 实现。
- 文档把跨版本/跨平台、更多 builder、NeoForge `RegisterEvent` 接线和连带注册列为待完成项，见 head 文档第 33-45 行。
- 脚本示例是 `RegistryEvents.register(event => ...)`，支持 `custom(id, type, callback)` 与裸 `register(id, supplier)`，见 head 文档第 149-166 行。

## 1.1 提交演进证据

- commit 1 的 6 个类先建立元信息、builder、类型和事件的静态 API，没有插件注册接线。
- commit 2 只改 common 扩展基础设施，说明 API 骨架很快遇到初始化顺序问题。
- commit 3 同时加入 plugin glue、Item builder、静态结果发布和文档，说明为完成垂直切片付出了外围改动。
- `RegistryObjectType` 从 `createBuilder(info, id)` 改成对象自身携带 info/factory，属于一次局部接口修订。
- `RegistryEventJS` 的 callback overload 也是 commit 3 才加入，最初 API 不满足文档中的 KubeJS 风格。
- commit message `i give up` 是作者状态信号，但不能单独证明某个技术结论。
- 可复现的数量事实来自 compare API：3 commits、15 files、+914/-5。
- 这条演进把“通用 API 很小”与“接线/生命周期很大”区分开。
- 因此维护痛点应看外围编排，而不是只看新增类的行数。

## 2. 改动前：目录与既有接口

- PR base 的 NeoForge 26.2 路径 `platforms/neoforge-26.2/src/main/java/com/tkisor/nekojs` 只有 `platform` 子目录；`wrapper/registry/base` 在 base 上不存在。
- 因此 PR 新增的是一棵平台节点内的 registry skeleton，不是改造一个已经存在的 generic registry 模块。
- base `NekoPluginExtensionPoint` 是四字段记录：`id`、`pluginType`、`enabled`、`collector`，源码第 29-34 行。
- base 扩展点只支持 `.of(...)`、`.clientOnly(...)` 和逐插件 `collect(...)`，源码第 48-78 行。
- base bootstrap 在第 57-78 行硬编码 14 个 `nekojs:*` 内置扩展点，每个点直接把插件 default hook 接到固定 context accessor。
- base bootstrap 第 148-166 行先注册内置点、再让 provider 注册自定义点、freeze，之后按 plugin 外层、point 内层循环收集。
- base 收集流程没有每个点的 initializer、finisher、结果发布或依赖图；全局完成时只有 `freezeState`。
- base `NekoPluginExtensionContext` 第 16-43 行暴露一组固定资源：编译器、绑定、适配器、事件、文档、node module、recipe、lifecycle 和 probe。
- base `NekoJSPlugin` 的注册型 default hook 分散在第 56、60、67、78、82、96、106、119、123、127、151、169、176、191 行。
- 这些是“改动前”维护上下文，不是 PR 声称要删除的 API；PR 本身没有修改 `NekoJSPlugin` 或 `NekoPluginExtensionContext`。
- 远程 base bootstrap：https://github.com/NekoJS-Dev/NekoJS/blob/621f4656dbcbb7e4b656f01aab8835ba39eedcd6/common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginBootstrap.java#L57-L78
- 远程 base collect：https://github.com/NekoJS-Dev/NekoJS/blob/621f4656dbcbb7e4b656f01aab8835ba39eedcd6/common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginBootstrap.java#L148-L166
- 远程 base extension point：https://github.com/NekoJS-Dev/NekoJS/blob/621f4656dbcbb7e4b656f01aab8835ba39eedcd6/common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginExtensionPoint.java#L29-L78
- 远程 base context：https://github.com/NekoJS-Dev/NekoJS/blob/621f4656dbcbb7e4b656f01aab8835ba39eedcd6/common/src/main/java/com/tkisor/nekojs/core/plugin/NekoPluginExtensionContext.java#L16-L43

## 3. 改动后：目录与接口

- 新增 `platforms/neoforge-26.2/.../wrapper/registry/base/`：`RegistryEventJS`、`RegistryInfo`、`RegistryInfos`、`RegistryObjectBuilder`、`RegistryObjectType`、`RegistryObjectTypeRegistry`、`RegistryObjectTypes`。
- 新增 `base/impl/ItemBuilderJS.java`；新增 `base/plugin/RegistrySupportPlugin.java`、`RegistryInfosRegistry.java`、`RegistryInfosPlugin.java`、`BuiltinRegistrySupport.java`。
- 新增 `docs/MINECRAFT_REGISTRY.md`；common 只修改 `NekoPluginExtensionPoint.java` 和 `NekoPluginBootstrap.java`。
- `RegistryInfo<T>` 记录对象基类、原始反射 Type 和 `ResourceKey<Registry<T>>`，另提供 `RegistryAccess` 查询；head 源码第 1-31 行。
- `RegistryInfos` 将扫描根类发现的注册表和手工 `RegistryInfo` 按 Identifier 聚合，并提供 `get`/`view`；head 源码第 41-59、61-114 行。
- 扫描器只接受 public、static、final、`ResourceKey<Registry<...>>` 的特定反射形状；失败或反射异常会 `continue`。
- `RegistryObjectBuilder<T>` 只持有 `RegistryInfo<T>`、id 和抽象 `build()`；设计文档规定属性用 public field，不用 return-this setter。
- `RegistryObjectType<T>` 首版让 `createBuilder(info, id)` 接收 info；最终版把 `registryInfo` 和 `BiFunction` 放进 `Impl`，事件侧只传 id。
- `RegistryObjectTypeRegistry` 提供 `scope(ResourceKey)`、直接 `register(type)` 和 `view()`；`Scope.close()` 没有清理语义。
- `RegistryObjectTypes<T>` 是 finish 后的按 ResourceKey 结果表，但用 `Internal.REGISTERED` 静态可变字段发布，不使用 extension handle。
- `RegistryEventJS<T>` 持有一个 registry 的 info、类型 map 和 providers；`custom` 建 builder 并收集 `builder::build`，`register` 接受裸 Supplier。
- `ItemBuilderJS` 只有 Item 内容实现，字段包括 `maxStackSize`、`maxDamage`、`fireResistant`、`rarity`、`glowing`、`burnTime`、`groupTab`、`food`。
- Item builder 直接导入 NeoForge 26.2 的 Minecraft Item API，并在 `buildProperties` 中 hard-code `Registries.ITEM`；`groupTab` 在该 skeleton 中没有被消费。
- 远程 head RegistryEventJS：https://github.com/NekoJS-Dev/NekoJS/blob/7c2fce53929bc6b6660f404459ebfca5a88c3abd/platforms/neoforge-26.2/src/main/java/com/tkisor/nekojs/wrapper/registry/base/RegistryEventJS.java#L17-L59
- 远程 head RegistryInfo/Infos：https://github.com/NekoJS-Dev/NekoJS/blob/7c2fce53929bc6b6660f404459ebfca5a88c3abd/platforms/neoforge-26.2/src/main/java/com/tkisor/nekojs/wrapper/registry/base/RegistryInfos.java#L14-L58
- 远程 head type registry：https://github.com/NekoJS-Dev/NekoJS/blob/7c2fce53929bc6b6660f404459ebfca5a88c3abd/platforms/neoforge-26.2/src/main/java/com/tkisor/nekojs/wrapper/registry/base/RegistryObjectTypeRegistry.java#L17-L101
- 远程 head plugin glue：https://github.com/NekoJS-Dev/NekoJS/blob/7c2fce53929bc6b6660f404459ebfca5a88c3abd/platforms/neoforge-26.2/src/main/java/com/tkisor/nekojs/wrapper/registry/base/plugin/RegistryInfosPlugin.java#L23-L58
- 远程 head Item builder：https://github.com/NekoJS-Dev/NekoJS/blob/7c2fce53929bc6b6660f404459ebfca5a88c3abd/platforms/neoforge-26.2/src/main/java/com/tkisor/nekojs/wrapper/registry/base/impl/ItemBuilderJS.java#L19-L89

## 4. 为什么需要多处登记

- 事实一：`RegistryInfo` 描述“注册表是什么”，包括 ResourceKey、对象基类和反射类型；它不描述脚本如何构造对象。
- 事实二：`RegistryObjectType` 描述“该注册表支持哪一个命名 builder 类型”，工厂需要拿到目标 registry 的 info。
- 事实三：`RegistryEventJS` 描述“脚本这一轮要注册哪些对象”，它收集 id 到 Supplier，而不是在插件 bootstrap 期间立即创建 Minecraft 对象。
- 事实四：真正的 loader 注册事件还要单独发生；PR 文档把 NeoForge `RegisterEvent` 集成列为待完成，而没有实现 adapter。
- 所以最少有三个时间/维度：发现 registry 元信息、登记 builder 类型、收集脚本对象并在 loader 时机注册。
- 插件系统还增加一层：`RegistryInfosPlugin` 登记两个 custom extension point；`RegistrySupportPlugin` 为两个 point 提供贡献方法；`BuiltinRegistrySupport` 提供原版扫描根和 Item/basic。
- 第一阶段 `registry_infos` 收集 `classesToScan + additionalInfos`，finish 构建 `RegistryInfos`。
- 第二阶段 `registry_object_types` 使用已经构建的 `RegistryInfos`，按 registry scope 登记命名工厂，finish 构建 `RegistryObjectTypes`。
- 这种多处登记本身是合理的维度分离；维护痛点在于调用者必须知道每个阶段何时可用，以及手工把阶段连接起来。
- PR 文档两阶段流程：`docs/MINECRAFT_REGISTRY.md` 第 49-63 行；待办和连带注册：第 33-45 行。

## 3.1 改动面计数

- common 的两个修改点承担了所有扩展点调度语义：`NekoPluginExtensionPoint` 与 `NekoPluginBootstrap`。
- NeoForge 节点新增 7 个 base 类，1 个 Item 实现，4 个 plugin glue 类。
- docs 新增 1 个设计文档，明确记录待完成项和作者对抽象缺口的自我诊断。
- `RegistrySupportPlugin` 让 registry 贡献者不必修改 `NekoJSPlugin`，但仍要理解两个 custom point。
- `RegistryInfosPlugin` 同时是 extension provider 和 plugin，承担阶段桥接与静态发布。
- `BuiltinRegistrySupport` 通过注解进入插件发现，提供原版 `Registries.class` 和 `ITEM/basic`。
- `RegistryEventJS` 没有 loader event 参数，意在把 loader 绑定留到未来 adapter。
- 但该意图没有被目录层兑现：所有新增 registry 源码仍在 NeoForge 26.2 节点。
- 所以 diff 的“接口无 loader”只能作为方向证据，不能作为跨 loader 完成证据。
- 15 文件的新增面显示新增一个 registry 类型仍需要 metadata、type、plugin 和内容类分别落点。

## 5. 暴露的核心抽象问题

- **问题 A：扩展点没有产品生命周期。** 原模型只有 collector，不能表达“先初始化累积器、收集所有插件、finish、发布结果”。
- **问题 B：依赖被伪装成注册顺序。** `registry_object_types` 依赖 `registry_infos`，最终只能在前者注册点的 `registry_infos` finish 回调里创建后者的 registry。
- **问题 C：bootstrap 被迫承担点间编排。** 提交 2 把循环由 plugin×point 改成 point×plugin，并在每个 point 后调用 `onFinish`，形成顺序 barrier。
- **问题 D：`onFinish` 是全局副作用。** 它是 nullable `Runnable`，没有结果类型、依赖声明、finish 状态或 enabled/skip 语义；却可以改变所有扩展点的收集次序。
- **问题 E：万能 context 太浅且耦合。** 内置扩展点的资源通过固定 accessor 进入 `NekoPluginExtensionContext`；新增能力容易牵动 context、bootstrap、runtime 和下游工厂。
- **问题 F：结果访问没有稳定 seam。** `RegistryInfos.INSTANCE` 与 `RegistryObjectTypes.Internal.REGISTERED` 是静态可变全局，初始化前可为 null，reload、测试隔离和并发 bootstrap 没有清晰契约。
- **问题 G：泛型和反射错误容易变成漏注册。** `get` 使用 unchecked cast，扫描异常静默跳过，scope/type map 的重复类型使用 `put` 覆盖，没有统一冲突策略。
- **问题 H：平台 seam 还未真正落下。** EventJS 不导入 loader RegisterEvent 是正确意图，但它位于 NeoForge 版本节点，Item builder 又直接绑定 26.2 内容 API。
- **问题 I：连带注册只有讨论没有协议。** `additionalBuilders()` 和回调式 `handleAdditionalRegistry(...)` 都只是候选方案，目标 pass、Supplier、重复 id 和失败诊断未定。
- **问题 J：脚本易用性和 Java 类型契约有张力。** public field 适合 GraalJS callback，但失去 setter 的校验和稳定方法签名；它应是脚本内容面约定，不应伪装成完整 Java plugin contract。
- **问题 K：PR 是 skeleton 而不是可交付闭环。** 没有 RegisterEvent 接线、Fabric、测试或完整 builder 集合；“generic”在此只代表接口草图。
- 维护者优先的归纳：真正成本不是类数量，而是复杂度泄漏到每个新增点/版本/插件作者，缺少一个有深度的生命周期模块来集中编排、错误和状态。

## 5.1 关键取舍矩阵

- 元信息扫描 vs 手工条目：扫描降低逐类型维护，手工条目覆盖模组自定义 registry；二者应合并为一个贡献面。
- 命名类型 vs 单一 builder：命名 factory 支持扩展和脚本 `custom`，default 类型再提供低认知成本的 sugar。
- 脚本期建对象 vs 延迟 Supplier：延迟创建适配 loader 生命周期，也允许跨 registry 引用；代价是必须有 repository 和未消费诊断。
- public field vs fluent setter：前者让 GraalJS callback 与子类字段直接工作，后者提供校验/链式表达；本项目选择前者并接受 breaking。
- 静态全局 vs handle：静态全局短期接线简单，handle 才能表达 bootstrap 轮次、finish 状态和测试隔离。
- 顺序约定 vs 显式依赖：顺序约定少写代码但把错误隐藏在注册排列，显式依赖增加声明却能拓扑检查和 fail-fast。
- Scope vs 普通注册方法：Scope 让同 registry 的登记集中，但 close 无语义，额外概念不产生足够 leverage。
- common 事件 vs loader adapter：事件面可共享，具体注册调用必须由 NeoForge/Fabric adapter 拥有。
- 这些取舍应以维护者 locality 为第一评判，再以 Java 作者可发现性和 JS 易用性排序。

## 6. 应纳入重构的结论

- 保留“通用注册表”问题切分，但把外部 seam 固定为：loader adapter、平台无关 GraalJS EventJS、builder repository。
- 脚本收集期只攒 builder；对象创建推迟到 NeoForge/Fabric 各自的注册 pass，以 Supplier 处理跨注册表互引。
- `registry_infos` 和 `registry_types` 保留为两个贡献面，但各自成为自包含 Point，拥有 initializer、collector、finisher、merge policy 和产物 handle。
- `registry_types` 对 `registry_infos` 声明显式时序依赖；数据读取经 `context.result(...)`，不再用 onFinish 回调初始化另一个 registry。
- 使用拓扑排序和 cycle fail-fast；同层只按注册序做稳定 tie-breaker，禁止隐式依赖顺序。
- 结果通过 `NekoPluginExtensionHandle` 读取；finish 后产物不可变，累积器密封，bootstrap 每轮重新初始化，支持 reload/test 隔离。
- 内置点和第三方点走同一 provider 路径；内置先行应是 bootstrap 的明确相位，不是 priority 魔法或 context 特例。
- Java 插件作者面应提供一个最小、可发现的 Point/Contributor seam；是否同时保留基接口门面由作者体验决定，但事实源必须只有一个。
- JS/TS 面采用 KubeJS 风格 `RegistryEvents.register`、每 registry default sugar、命名 `custom` 和裸 Supplier escape hatch；高级 Java 访问不被糖方法阻断。
- 用户已确认版本号加 `0.1`、breaking 可接受、最终不留长期兼容桥，因此旧类型化入口和 return-this builder 不应以 deprecated/shim 永久保留。
- public field 作为脚本 builder 数据属性可以保留；动作方法保持动词/void，校验集中在构建或运行时诊断，而不是恢复链式 setter。
- 连带注册采用 builder 自治的回调式 `handleAdditionalObjects(AdditionalObjectRegistry)`，Supplier 懒解析，目标 registry 自己的 pass 投递；after-all 仅用于读取已完成注册表后派生。
- GraalJS 是固定运行时；ProxyObject/Value/Consumer/Supplier 的选择是宿主边界设计，不是替换纯 Java、可控转译器的证据。转译器决策应独立于 PR #37。
- NeoForge+Fabric 是目标双 loader；PR 只覆盖 NeoForge 26.2，跨 loader 结论必须由后续 adapter 和能力矩阵证实。
- Stonecutter 可以重评模块归属：版本差异留在版本树/guard，registry event/repository/Point 契约尽量进入 shared/common；不要照搬 PR 的 NeoForge 目录作为最终边界。

## 7. 仅属 PR 局部的取舍或瑕疵

- Item-only vertical slice、Item 字段默认值、FoodBuilder、`groupTab` 未消费和 26.2 `Registries.ITEM` hard-code 是内容实现问题，不是 generic framework contract。
- `Scope`/try-with-resources 只是 registry 分组语法糖；它的 `close()` 无清理语义，普通 `registerType(registry, name, factory)` 加 default 更浅。
- `Runnable onFinish`、point×plugin 的全局循环改变、静态 registry 单例、finish 后置 null 和 mutable view 是实验骨架的局部 workaround，不应进入标准接口。
- 反射扫描的精确字段修饰符、`rawType` 保存方式、unchecked cast 和静默 continue 需要集中诊断，但不应成为跨版本公开 API。
- PR 文档提出的 `additionalBuilders()` 与 `handleAdditionalRegistry(...)` 尚未验证；后续回调式三件套是重构后的选择，不是 PR 已完成能力。
- EventJS 在 PR 中按一个 registry 持有一个 type map；全局 type 反查、default sugar 动态成员、full registry key 解析属于后续实现。
- PR 没有关于 GraalJS 版本、TypeScript/JSX/Python 转译器、脚本迁移、版本号或兼容桥的事实；这些只能按当前用户约束决策。

## 7.1 证据置信度与边界

- 高置信事实：PR 状态、SHA、统计、文件路径、源码接口和文档待办均来自 GitHub API/commit。
- 高置信事实：PR 没有 comments/reviews，故没有可引用的维护者异议或批准。
- 中置信推断：作者用 onFinish 和循环重排解决依赖，反映的是生命周期抽象缺口，而非特定 registry 的偶发 bug。
- 中置信推断：静态全局会增加 reload/test 风险；PR 没有测试或并发证据证明实际故障。
- 后续 ADR 是本地当前设计决策的证据，不是历史 PR 的原始意图；报告始终分开标注。
- 当前 master 的实现可能仍含迁移期注释或旧入口残留，不应覆盖“最终无 shim”的用户决策。
- PR 分支未合并，不能以 head 的 compile/runtime 行为推断项目已支持 NeoForge 26.2 注册。
- 本票据提交到隔离研究分支；共享工作树只新增指定报告文件。

## 8. 后续本地实现的交叉证据

- 本地 `docs/adr/0001-extension-point-model-v2.md:3-13` 明确把 PR #37 归纳为最大痛点，并选择 Collector 三段式、Point 自包含、handle、merge 和 reload 语义。
- 本地 `docs/adr/0002-extension-point-dependency-semantics.md:3-10,17-21` 把时序依赖定为 `dependsOn` + 拓扑排序，把数据依赖定为 `context.result`。
- 本地 `docs/adr/0003-builtin-extension-point-registration.md:3-19` 选择内置与第三方同 provider、内置点固定先行，并把新增点缩为 Point 文件加清单行。
- 本地 `docs/adr/0004-generic-registry-model.md:3-26` 选择三层解耦、贡献式 RegistryInfo、type/default、先攒后建，明确放弃 PR Scope 和引擎内跨 registry 拓扑。
- 本地 `docs/adr/0005-builder-and-co-registration.md:3-19` 选择 public field、builder 自治和回调式三件套，明确放弃 `additionalBuilders()` 直返集合。
- 本地 `docs/adr/0006-script-registry-api-clean-switch.md:3-18` 选择单入口、KubeJS 风格、一次性 breaking、无长期兼容层。
- 当前 `src/main/java/com/tkisor/nekojs/wrapper/registry/gen/RegistryInfosPoint.java:21-107` 和 `RegistryTypesPoint.java:18-118` 已把两个 PR custom point 改成 Collector Point。
- 当前 `RegistryTypesPoint.java:106-118` 明确 `registry_types` `dependsOn(RegistryInfosPoint.POINT)`，并在 initializer 读取前一点产物。
- 当前 `RegistryEventJS.java:18-38` 是零 loader import 的 GraalJS ProxyObject 事件面；`RegistryRepository.java:14-21,36-86` 集中 builder、Supplier、冲突和未消费诊断。
- 当前 NeoForge adapter `src/main/java/com/tkisor/nekojs/listener/RegistryEventAdapter.java:27-34,46-88` 与 Fabric adapter `versions/26.1.2-fabric/src/main/java/com/tkisor/nekojs/fabric/FabricRegistryAdapter.java:14-20,27-80` 证明平台适配层已经按 loader 分开。
- 本地 git `50029bd2` 的提交说明明确记录 generic registry、single RegistryEvents.register、builder/co-registration、NeoForge adapter 和 Fabric bring-up；这是后续落地事实，不是 PR #37 当时完成事实。

## 9. 结论

- PR #37 的长期价值是准确暴露了“元信息、类型工厂、脚本对象、loader 时机”需要分离；它不是可直接合并的实现。
- 维护者的核心诉求是 locality：新增一个注册表/扩展点应只增加内容定义和一次清单登记，生命周期、依赖、冲突、冻结和诊断由深模块集中承担。
- Java 插件作者需要可理解的贡献面和结果句柄；脚本作者需要单一 KubeJS 风格入口，同时保留裸 Supplier 的高级 Java 逃逸能力。
- 应采纳 Collector/handle/dependency、三层 loader seam、public-field builder、co-registration callback、一次性 breaking；应丢弃 onFinish 顺序补丁、Scope 语法糖、静态全局和 NeoForge 26.2 内容细节。
- 未经 PR 证实的部分：Fabric parity、Stonecutter 最终归属、完整 builder 列表、转译器策略和迁移方案；这些必须在后续架构票中以本地实现/测试证据单独裁决。
