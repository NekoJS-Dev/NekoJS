# 15: 启动期注册、typed Builder 与连带注册垂直收口

**What to build:** STARTUP 脚本通过唯一 RegistryEvents.register 调用者 Interface 声明对象，经 Registry Runtime 收集、显式 setter/JavaBean property 同路径写入、校验、规范化与类型工厂，由各节点 Adapter 在正确注册 pass 创建对象和已裁定连带对象；runtime member、TS/Python declaration、contract/golden、capability 与节点 smoke 从同一条路径可见。该票只处理游戏启动前声明注册，不把服务器运行期 Dynamic Registry 并入同一路径。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)

**Status:** closed

**Assignee:** zcode-agent

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** `none` 只表示实现、测试和证据整理可由 agent 执行；验收条件中要求的维护者删除确认是发布门禁。agent 可以准备替代路径 parity、迁移表和旧 route 无消费者证据，但不得在获得维护者 sign-off 前删除旧公开路径或勾选对应删除验收项，也不因此把本票改判为 `ready-for-human`。

**Work items:**

- W6

## Acceptance criteria

- [x] STARTUP 脚本只从现有 RegistryEvents.register 调用者 Interface 进入；事件在首个注册表 pass 前恰好收集一次，default 类型糖方法、命名类型和 custom/register 输入都能到达对应 Registry Runtime 请求。【生产唯一 post 点收口于 StartupRegistryRuntime（审查全仓 grep 复核恰 1 处）；E2E collectionHappensExactlyOnce + 四入口成功路径；两 Adapter 首 pass 前 collectOnce】
- [x] Registry Runtime 对 duplicate、additional、default、类型冲突和 drain 顺序保留现有已验证语义：同批重复 fail-fast，additional 不与来源对象外的同 id 冲突，每个 registry pass 只 drain 一次且无未交付残留；失败不留下可污染下一轮启动的进程级暂存。【duplicate fail-fast / additional 不与来源外同 id 冲突（含 additional-vs-main 新增 E2E）/每 pass 恰一次 drain 无残留 / epoch 隔离 + 失败负例】
- [x] managed 可写配置 Builder 的显式 setter 与 JavaBean-style property assignment 必须调用同一 setter，并进入同一校验、规范化、definition fingerprint 和注册收集路径；final id、只读成员和未开放 experimental 成员例外。不得依赖未验证的 Graal 天然 Bean 行为，等价性由 runtime contract fixture 固定。【平铺成员真 Graal parity（同 Method/同错误文本/同指纹）；审查 F2 后对象值属性走递归子指纹规范化、合成用例全节点真跑；真实 BlockBuilder 嵌套面 vanilla-gated skip（G1，not-verified 如实标注）】
- [x] setter/property parity 由 GraalJS runtime contract test 固定，TS/Python declaration 呈现一致的成员语义；迁移表说明旧 property 写法继续有效、显式 setter 不引入第二语义，final identity 字段例外可显式记录。【审查 F1 修复后重载双形态（Potion effect 3/5 参）真 Graal 双跑通 + 契约层收集断言；golden 再生成留痕 REPORT §4；final id 例外显式记录于迁移表】
- [x] 启动期既有 `custom(id, type, ...)` 与 `register(registry, id, supplier)` 高级能力保持可用（PR 单注册表 skeleton 的 `register(id, Supplier)` 只是历史形状，不覆盖当前统一事件签名），不被误解为 Dynamic Registry 的通用 `{ type: ... }` catalog；二者必须走唯一启动注册 epoch、重复 ID、类型匹配及各自适用的连带注册 gate；具名类型经既有 Builder 工厂，裸 Supplier 不强制套用具名工厂，规范化 fingerprint 只适用于可表达的启动声明；不对任意 Supplier 代码或其外部副作用承诺可比较指纹/回滚，更不据此开放 Dynamic Registry。Supplier 仍须在合法启动 pass 创建对象并做返回值、实际类型与重复 ID 校验。【validatedSupplier 非空 + 实际类型 + 重复 ID 校验 E2E；Supplier 副作用不承诺指纹/回滚（负例存在）；Dynamic Registry 未混入】
- [x] 随实现交付默认类型糖方法、`custom`、`register(Supplier)`、setter/property parity 和连带注册的最小可运行示例与必要迁移材料；示例只使用已通过 gate 的启动注册能力。【示例与 STARTUP_EXAMPLE 逐行一致、真 Graal 生产序列跑通；真实连带注册与游戏内 probe 输出 not-verified（G1/G2，通道级 E2E + parity 覆盖）】
- [x] typed Builder 的公开成员、校验和错误结果由契约反射生成；Block/Item/Fluid 等已验证启动期类型只按裁定完成约定连带注册，不在本票扩大类型或复制第二套类型 catalog。【成员目录反射生成 / ENGINE_SEAM 排除 / 13 类型不变；连带只保留裁定面；F1 修复后真实重载列表进面】
- [x] MC/loader 对象创建、注册 pass 接线、版本差异和 capability 只放在共享 MC-facing Adapter 或节点 Adapter；common 作者契约不引入 Minecraft/loader 类型，26.1.2、26.2.0、1.21.1 与两个 Fabric 节点的实际差异逐项记录。【对象创建只在两 Adapter；口径更正（审查 F6）：Runtime 住共享 MC-facing 层、持 MC 类型令牌做校验/键表达，不创建对象不进 common；五节点差异表 potion 行勘误为收集伪影】
- [x] runtime member、TS/Python declaration、Probe/manifest 与 contract/golden 均由同一契约输入派生；legacy preview 只作迁移观察，不静默升级为 stable；普通测试不得改写 golden 或声明产物。【派生链同源；确定性由重载稳定排序保证（F1 修复）；golden 走再生成路径并留痕（REPORT §4）】
- [x] 成功、失败、重复、类型冲突、连带注册缺失和 drain 失败测试都从 RegistryEvents 调用者 Interface 贯穿到节点 Adapter 的可观察注册结果，测试输出包含定义、注册表、节点和错误来源，不依赖私有仓库字段。【E2E 9 用例经生产同款 EventGroupJS + 真实 Graal；结果读 DrainResult / 公开 live view，无私有字段】
- [x] 五节点 source trace、artifact 检查和按各节点既定支持等级与声明能力的最小 runtime smoke 证明声明、实现与能力一致；差异显式记录，不自动补 Fabric parity，也不把 experimental 节点当作 primary 回归。【五节点 build 主会话合并门全绿（26.2.0/26.2.0-fabric 补齐）；fabric 无 FluidBuilder 为显式子集（unknown-registry 错误进 DrainResult.errors，审查 F7）；1.21.1 真实差异只剩六 builder 无 tag、painting 无 author/title 两项】
- [x] 本票不把 Dynamic Registry 指向启动期 drain 路径，也不在服务器运行期修改 live registry；两个生命周期在契约、测试和迁移表中保持分离。【dynamic/ 零 diff；契约/测试/迁移表三处分离口径一致】
- [ ] 旧类型化启动入口、手写 declaration、重复 type catalog 或不受测兼容 wrapper 只能在替代路径 parity、旧 route 无消费者、迁移表覆盖所有公开写法并获得维护者删除确认后删除；本票不保留长期双路径。【不勾选：维护者删除确认是门禁（Human input note）；替代 parity + 旧 route 消费者清单（REPORT §6）+ 迁移表已交付，待 sign-off】

- [x] 参照 PR 37 的收集与冻结边界补负例：Builder 配置 callback 抛错不得留可被 drain 的半成品，finish 后底层收集容器的变化不得修改已发布快照；只读 live view 不冒充冻结结果，任意 Supplier 的内部可变状态不被误称为深不可变。【callback 抛错不留半成品（先配置后入库）/快照不可变 + 后到声明进下一轮/live view 防御副本/Supplier 可变状态不称深不可变——4 用例全过】

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md): RegistryEvents 调用者 wrapper、事件成员、dispatch 时机和 catalog/golden 消费由事件面基础提供，不能并行发明第二总线语义。

## Scope and coordination

**Rationale:** 启动期注册是完整的静态声明→drain→节点注册路径，能独立交付并验收；Dynamic Registry 的事务、同步和 stale 语义会超出单一上下文，因此必须分开。

**Coordination:**

- 与 EVENT_SURFACE owner 协调现有 RegistryEvents 总线的注册时机和 catalog 唯一性；事件基础是 blocker，这里只协调具体接入，不因共享事件文件另加串行。
- 与 MANAGED_SURFACE owner 确认 registry Builder 与 declaration 是契约反射输入，避免手写声明成为第二事实源。

## Closure record（2026-09-15）

- 执行者：zcode-agent。实施区间 eba89230..fb86e6fb（实现 3 commits + 双轴审查整改 1 commit fb86e6fb），合并 499fa219。
- 交付物：`StartupRegistryRuntime`（唯一收集 epoch / Supplier 校验 / definition fingerprint / 逐注册表 drain / 连带投递 / DrainResult 可观察结果）、`BuilderSurface` ProxyObject seam（property 与显式 setter 转发同一 Method）、`RegistryBuilderContract`/`RegistryBuilderSurfaces`（真实重载列表 + 确定性排序）、11 个共享树 + 6 个 1.21.1 版本化 builder 的 public field→JavaBean setter 收口、契约条目贯穿 catalog/probe（`RegistryBuilderTsRenderer`/`PyRenderer`）、examples + MIGRATION.md、双版本 golden（26.x 161 行 / 1.21.1 152 行）。
- 双轴审查整改（F1-F8，REPORT §10）：F1 重载收集丢失（`Map<String, List<Method>>` + golden 再生成留痕 §4 + potion「真实差异」勘误为收集伪影 + 双形态 parity 用例）；F2 对象值属性指纹改递归子指纹（合成用例全节点真跑，真实嵌套面 vanilla-gated 归 G1）；F3 恒真断言实质化；F4 golden 字节级 gate；F5 unbind 移 finally；F6 Runtime MC 令牌口径更正；F7 fabric 未知注册表 sink 抛错进 errors；F8 1.21.1 painting javadoc。
- 测试：common 201 suites / 1478 tests / 0 失败；`:26.1.2:build` 45 suites / 209 tests / 36 skip（+3 审查新增）；`:1.21.1:build` 33 / 128 / 0（新用例真跑）；26.2.0 golden 定向 + fabric compileJava/golden 定向绿；五节点全量 build + guardLint（255 块 / 403 文件 / 0 警告）主会话合并门全绿。
- 遗留（REPORT §8/§10）：G1 真实 BlockBuilder 嵌套面 vanilla-gated（ModDev unitTest 后置）与 in-game 连带注册/probe smoke（主会话 minecraft-mod-mcp）；G4 golden 维护者审阅；G7 snapshot 旧形状兼容构造器；AC13 删除门禁待维护者 sign-off。
- **fix-forward `c2348513`（2026-09-17，启动期 P0）**：本票的 `RegistryBuilderSurfaces.register(registry, registryTypes())`（registerTypeDocs 内的急切读取）把"倒序数据依赖"引入插件图——`type_docs` 是内置点、按注册序先于版本树点 `registry_types` 执行，导致 **fml mod 构造期崩溃**（crash-report：`NekoJSMod.initializeScripts → bootstrapOwned → collectPoint → registerTypeDocs:52 → registryTypes:97 → requireResult:102` 抛 "has not finished yet"）。本票实施与双轴审查的五节点 build / 单测均未覆盖（插件图只在真机 boot 执行）。修复 = 引擎补"可选时序依赖"（`dependsOnOptional/Id`，对方缺席不建边）+ `TypeDocsPoint` 声明对 `nekojs:registry_types` 的可选依赖 + 4 条回归钉（含负例对照）；`:26.1.2:runGameTestServer` 由崩溃转 BUILD SUCCESSFUL（bootstrapped once、13 事件组注册、STARTUP 脚本加载）。**教训：插件图/启动面改动必须补一次真机 boot（build+unit 不覆盖）。**

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
