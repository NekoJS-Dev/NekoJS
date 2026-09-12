# PR #37 票据引用审阅

## 取证口径：历史 PR 不等于当前实现或目标契约

PR #37 为 OPEN、未合并；baseRefOid `621f4656dbcbb7e4b656f01aab8835ba39eedcd6`，head 为 `7c2fce53929bc6b6660f404459ebfca5a88c3abd`，本地 `merge-base(origin/master, refs/remotes/pr/37)` 为 `3ae8a116564ec6e245de15a17d1452b3dfc0778f`。完整 patch 为 15 文件、+914/-5、3 commits；已完整读取 head 的 MD 与 patch；通过 gh 读取正文，普通 comments、reviews、逐行 review comments 均为空。正文表达架构维护困难并把待办留在文档，不代表任何 reviewer 批准。PR updatedAt 为 2026-08-27T15:49:20Z（本轮经 GitHub CLI 查询确认），本次审阅日期为 2026-09-09。本次只读源码与文档，未运行构建、测试或 runtime smoke；下述“已落实”是源码结构事实，不是验收通过声明。

## 1. 生命周期：建议合理，当前核心机制已经落实

PR 文档第 65-101 行指出旧扩展点缺少生命周期、依赖和结果持有者，建议 Collector 式三段式。当前源码已有对应机制：`NekoPluginExtensionPoint<P,A,R>` 必须显式提供 initializer、collector、finisher、MergePolicy，并可声明 `dependsOn`；bootstrap 先 `registry.freeze()` 关闭注册图并做 Kahn 拓扑排序，之后才按点执行 initialization → collection → finish/result，并把产物发布到 context、Runtime 结果表和 `NekoPluginExtensionHandle`。Handle 与单次 bootstrap 绑定，finish 前读取失败。PR 伪码中的 merger 是逐插件累积回调，现实现主要对应 collector；MergePolicy 则处理冲突，不能只凭名称把两者等同。此处必须区分三层：freeze 只冻结扩展点注册图；initialization/collection/finish/result 是每轮执行生命周期；`Sealable` 只在 finisher 后密封可选累积器，不等于结果发布，也不是所有 collector 都实现。

`RegistryInfosPoint` 与 `RegistryTypesPoint` 是实际用例：后者 `dependsOn(registry_infos)`，initializer 读取前一点产物；`NekoRegistryPointsPlugin` 注册两点并保存 Handle。普通脚本 reload 走 `NekoRuntimeRoot.reload()` 的 ScriptManager 路径，复用已持有的 Plugin Runtime，不重新 discovery/bootstrap/freeze。因此不照搬 PR patch 的 `onFinish` 全局副作用、静态 `RegistryInfos.INSTANCE`、无语义 Scope，也不在现有实现之外再造第二套 Collector；这不是否定文档本身的三段式建议。票 08 的可执行验收应锁住：真实 addon 依赖/环/未知 id/重复 id/freeze 后注册的错误阶段，Handle 生命周期，普通 reload 前后 bootstrap 与 freeze 只发生一次。

## 2. custom / register：合理，但仅限启动期 Registry Runtime 高级入口

PR 的 `custom(id, type[, callback])` 与 `register(id, Supplier)` 是单注册表启动事件的作者声明口，不是运行期动态注册。当前统一为 `RegistryEvents.register` 一个 STARTUP bus：`event.item(id, callback)` 使用 default 类型，`event.item(id, type, callback)` 使用注册表内命名类型，`custom(id, type, callback)` 解析全局唯一类型名，`register(registry, id, supplier)` 以糖名或完整 key 选择目标注册表。事件在首个注册 pass 前收集一次，builder 与 Supplier 进入 `RegistryRepository`，由 NeoForge/Fabric Adapter 在目标注册表 pass 抽干；该路径有重复检查与未交付诊断；default/命名类型缺失或 `custom` 类型归属歧义会报错。但存在这些分支不等于跨 pass、连带冲突与失败清理已经通过集成验收。

所以不应一概禁止字符串类型名或 Supplier：字符串是启动期命名工厂引用，Supplier 是启动期高级逃逸口。已确认不采用的是 Dynamic Registry 的通用 `{type: ...}` catalog，以及在候选收集期直接修改 live registry；合法提交时的 Adapter mutation 并未被禁止。票 15 应验收唯一入口、一次收集、重复/类型/Supplier 类型匹配、目标 pass drain、无残留和连带注册；票 16 保持 typed callback Builder、inert candidate、不改 live registry；票 21 再验 preflight、prepare/ack、commit 与失败保留旧 active。15 与 16/21 是两种生命周期；16、21 是同一动态能力的本地计划与同步激活两阶段，不是三套注册系统。可共享类型事实与配置表达，不能以复用为名混用启动 drain 和动态提交。

### 历史 head 核验：不是“缺重载/缺构造器”

最终 head 的 `RegistryEventJS.custom` 同时有二参与三参 Consumer 重载；`RegistryObjectBuilder` 也有 `(RegistryInfo, Identifier)` 构造器。因此文档相应调用和 `super(info,id)` 不构成此前怀疑的签名缺失；首个提交不能代表最终 head。文档示例仍需编译和脚本 fixture，不能仅凭签名存在就宣称可运行。[H1][H2]

真实局限是三参 custom 先调用二参版本，把 `builder::build` 放入 providers，再执行 Consumer；Consumer 抛出时此对象已留在该事件的暂存表。`register` 则直接接纳 provider，没有显式空值检查、产物类型校验或收集完成后拒绝写入的门禁。应要求票15证明失败暂存不被消费、下一轮不被污染，Supplier 在合法 pass 执行并校验结果；不要求对任意 Java 副作用通用回滚。这些是 skeleton 待集成的错误边界，并非已证明生产服务器故障。[H1]

## 3. public field：动机合理，方案有条件，不照搬旁路

PR 第 107-124 行的 self-return 子类痛点是真实 DX 问题，但 public field 不是唯一解。后续方向可用私有状态、getter/void setter 与 JavaBean-style property 外观保留 `builder.maxStackSize = 16`，同时让显式 setter 与属性写入调用同一校验、规范化、fingerprint 和收集路径；不要求链式返回。不能声称 Graal 天然支持 JavaBean：Decision 08 明确要求以项目精确 Graal/HostAccess/options runtime contract fixture 固定，必要时用既有 `ProxyObject.putMember` seam 转发同一 setter。

版本区别必须写清：当前 `ItemBuilder` 仍有 public field，ADR-0005 与旧 evidence 也采纳过 public-field 口径；Decision 08 后续对 Dynamic Builder 改为私有状态 + Bean property，Spec 04 与票 09/15/16 再按 managed、启动期、动态期分域落地。本工作区票 15 第 25-28 行已明确 setter/property parity、custom/register 与连带注册验收；因此旧轮“票15缺双写法验收”的结论已不适用于本次落盘时的票面，不应重复补票。不要把 ADR-0005/evidence 冒充最新批准，也不要把该语义扩大到任意 Java 对象；高级 Graal interop 继续按 Decision 04 保留。

### 冻结纠偏：只读入口不是冻结快照

PR 文档§3.3把 `Collections.unmodifiableMap(providers)` 放在“不可变集合”下，但实际是只读 live view：调用者不能借此 put，原表继续收集时，旧 view 仍能看到新条目。复制 Map 也仅隔离容器结构，不能冻结 Supplier 捕获的 Builder，更不能深冻结任意 Java 对象。发布快照应在明确完成边界隔离可变累积器，并规定完成后写入的结果；不能拿不可写外壳冒充稳定产物。[H1][H3]

当前 `registry.freeze()` 关闭的是插件扩展点注册窗口并校验依赖图，不是 Minecraft registry 的冻结，也不是自动深冻结所有 Point 产物。票08已加入“保留旧累积器引用后再写，已发布结果不变”的 fixture 验收自身冻结承诺；目标是稳定的注册定义，不是新增通用深拷贝框架。

## 相邻边界

`RegistryInfo` 承载 key/类型元信息，PR 的 `RegistryObjectType` 承载具名 Builder 工厂，当前 `RegistryTypesPoint` 聚合具名工厂与 default，`RegistryEventJS` 只做作者声明面，分离合理。metadata 被发现不等于能力开放，票 09 应继续由 NormativeApiContract 派生 declaration/Probe/capability，不新增全仓 catalog。连带注册动机合理，但 PR 仅列待办；当前已有 `handleAdditionalObjects` 与目标 pass 投递代码，票 15 只验收既定语义，不另造通用跨注册表拓扑。跨平台也不照抄 PR 的 NeoForge 26.2 物理目录；零 loader import 不等于零 MC 类型，common 禁止 MC/loader，根 `src` 可承载共享 MC-facing 实现，平台差异留给节点 Adapter。

Scope 将同一注册表的类型贡献归在一起，作者体验有价值；但 PR final head 的 `Scope.close()` 是空实现，不能推导退出 scope 即 seal、commit 或 rollback。当前普通注册方法已能表达贡献，不应为了外观再引入资源生命周期。另须区分三类冲突：注册表内命名工厂按已定 merge policy，启动对象重复 ID fail-fast，动态同 key changed definition 按动态契约；PR 中直接 Map.put 的静默覆盖不自动成为这三者的统一规则。

## 转为验收：局部澄清，不新增框架票

- **08：**现有三段式、依赖与 Handle 合理且已实现；顺序应写成注册图 freeze → 初始化/收集 → finisher → 可选累积器 seal → 发布结果。不得禁止 initializer 读取已经完成的依赖产物，只应禁止它偷偷初始化、修改其他点的中间状态。补冻结后的可观察稳定性，不把研究建议贬为“只能评估”。
- **15：**保留 startup 高级接口，但票面已标明 `register(id,Supplier)` 是 PR 单注册表签名；当前统一事件实际为 `register(registry,id,supplier)`。补 callback 异常、Supplier 空值/类型/执行时点、完成后写入与连带冲突 fixture。裸 Supplier 不必先经具名 Builder 工厂，也不能假定任意闭包具有可用于动态热更的稳定指纹。
- **09、15、16：**双写法要求已在当前票面，继续固定精确 Graal 配置、同一 setter 和声明一致性；只对开放的可写配置施加此约束，final identity、只读成员及高级 Java 原始访问不一刀切。
- **16/21：**不因采纳 startup custom/register 就开放动态任意类型或绕过候选/同步门禁。**Decision 00/04**保护现有 Point 和单事实源；**Decision 08**约束动态 Builder。旧 ADR-0005/evidence 的 public-field 结论应标记历史范围并与新规格对齐，不能暗中覆盖后续确认；本报告不修改这些资产。

## 来源表

- PR head 文档：https://github.com/NekoJS-Dev/NekoJS/blob/7c2fce53929bc6b6660f404459ebfca5a88c3abd/docs/MINECRAFT_REGISTRY.md#L65-L101
- `D:\mcmodDemo\NekoJS\NekoJS-mult\common\src\main\java\com\tkisor\nekojs\core\plugin\NekoPluginBootstrap.java:103-159,209-277`
- `D:\mcmodDemo\NekoJS\NekoJS-mult\common\src\main\java\com\tkisor\nekojs\core\plugin\NekoPluginExtensionPoint.java:68-93,167-228`
- `D:\mcmodDemo\NekoJS\NekoJS-mult\common\src\main\java\com\tkisor\nekojs\core\plugin\NekoPluginExtensionHandle.java:3-9,26-48`
- `D:\mcmodDemo\NekoJS\NekoJS-mult\src\main\java\com\tkisor\nekojs\wrapper\registry\gen\RegistryEventJS.java:18-38,130-245`
- `D:\mcmodDemo\NekoJS\NekoJS-mult\src\main\java\com\tkisor\nekojs\wrapper\registry\gen\RegistryRepository.java:23-85`
- `D:\mcmodDemo\NekoJS\NekoJS-mult\docs\architecture-refactor\decisions\08-ported-features-event-surface.md:66-82`
- `D:\mcmodDemo\NekoJS\NekoJS-mult\docs\architecture-refactor\specs\04-public-contract-and-plugin-model.md:41-54`
- `D:\mcmodDemo\NekoJS\NekoJS-mult\docs\architecture-refactor\implementation-tickets\08-plugin-addon.md:19-41`
- `D:\mcmodDemo\NekoJS\NekoJS-mult\docs\architecture-refactor\implementation-tickets\09-managed-surface.md:21-30`
- `D:\mcmodDemo\NekoJS\NekoJS-mult\docs\architecture-refactor\implementation-tickets\15-registry-startup.md:23-35`
- `D:\mcmodDemo\NekoJS\NekoJS-mult\docs\architecture-refactor\implementation-tickets\16-registry-dynamic-local.md:23-34`
- `D:\mcmodDemo\NekoJS\NekoJS-mult\docs\architecture-refactor\implementation-tickets\21-registry-dynamic-sync.md:24-32`
- [H1：PR final-head RegistryEventJS，L30-L57](https://github.com/NekoJS-Dev/NekoJS/blob/7c2fce53929bc6b6660f404459ebfca5a88c3abd/platforms/neoforge-26.2/src/main/java/com/tkisor/nekojs/wrapper/registry/base/RegistryEventJS.java#L30-L57)
- [H2：PR final-head RegistryObjectBuilder，L10-L19](https://github.com/NekoJS-Dev/NekoJS/blob/7c2fce53929bc6b6660f404459ebfca5a88c3abd/platforms/neoforge-26.2/src/main/java/com/tkisor/nekojs/wrapper/registry/base/RegistryObjectBuilder.java#L10-L19)
- [H3：PR final-head 完整 MD](https://github.com/NekoJS-Dev/NekoJS/blob/7c2fce53929bc6b6660f404459ebfca5a88c3abd/docs/MINECRAFT_REGISTRY.md)；[PR 正文与讨论](https://github.com/NekoJS-Dev/NekoJS/pull/37)。
