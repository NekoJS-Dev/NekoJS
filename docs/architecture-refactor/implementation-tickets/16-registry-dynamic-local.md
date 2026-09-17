# 16: Dynamic Registry inert 定义计划与 typed Builder

**What to build:** SERVER 脚本通过独立的服务器运行期动态注册事件 facade 在 candidate 阶段提交 type-specific callback Builder 定义；本地路径完成事件收集、全规范化 fingerprint、preflight、同 key 冲突、stale/retired 记录和 inert Adapter 请求，并用现有 event Builder 与 Adapter 本地行为测试证明候选不修改 live registry。候选类型只在既有 Item、SoundEvent、MobEffect 范围内选取；完整公开激活由事务/同步 gate 决定，本票不宣称动态热更新已完成。

**Blocked by:** [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md)

**Status:** closed

**Assignee:** zcode-agent

**Optional:** false

**Selected:** true

**Human input:** none
**Human input note:** `none` 只表示实现、测试和证据整理可由 agent 执行；验收条件中涉及的维护者删除确认是后续发布门禁。agent 可以准备替代路径 parity、迁移表和旧 route 无消费者证据，但不得在获得维护者 sign-off 前删除旧公开路径或勾选对应删除验收项，也不因此把本票改判为 `ready-for-human`。

**Work items:**

- W6

## Acceptance criteria

- [x] server registry ready 触发初次候选；script/data reload 在候选阶段重新收集并完成本批 preflight，成功 commit 才发布计划；失败或取消不另起写入，也不在任意脚本线程即时执行 registry mutation。【初次：`ServerEventListener.onServerAboutToStart` → `fireInitialCollection`（`>=26` 守卫与旧动态注册同边界）；候选期：`CandidateDomainCollector` seam（EVENT_PLAN 后 / STATE_PLAN 前）+ `publishJoint` 联合发布；`lastCandidateCollection` 使候选期收集可观察。失败三例（同 key 冲突 / 收集错误 / 毒化）零写入、旧 active 继续服务】
- [x] 脚本作者面使用类型直达入口：已验证类型默认冻结为 `event.item(...)`、`event.soundEvent(...)`、`event.mobEffect(...)` 的 callback Builder 形式；若源码证据迫使不同命名，必须在实施前记录命名决策与理由并进入 contract/golden、declaration 和迁移表。【入口名未偏离；**命名决策**：独立组 `DynamicRegistryEvents` 替代 spec 08 工作名 `ServerEvents.dynamicRegistry`——已进 contract/golden（`nekojs/dynamic/dynamic-registry-events.expected.d.ts`，golden 测试显式断言不出现工作名）、declaration（同 golden + parity 测试）、迁移表（MIGRATION §1）。理由：ServerEvents 是 loader 专属树且 fabric 另有一份同 FQCN 文件，挂进去会把独立生命周期焊死，common 无法承载/测试】
- [x] 事件回调只接收 typed callback Builder；不提供通用 type 对象 catalog，也不把未知 type 字符串转换成注册能力。候选范围只从既有 Item、SoundEvent、MobEffect 中选取，且只有通过目标 Adapter、事务与同步 gate 的类型才可公开；未验证类型记录 not verified 并阻塞开放，不因缺测改写为 unavailable。【成员目录恰好三入口；`typeNamesAndRegistryKeysAreNotInterchangeable`（`minecraft:item`/`block`/`custom`/`register` 等既非入口名也不在目录、`typeof` undefined）；`frozenCandidateSurfaceIsExactlyTheThreeVerifiedTypes`（枚举/registry key/apiName 目录逐字冻结）；能力表按 not verified 口径（REPORT §3）】
- [x] 显式 setter 与 JavaBean-style property 写入调用同一个 setter、校验、规范化和 definition fingerprint 路径；GraalMC 临时 property 实验只作为 characterization，本票验收必须由运行时 contract test 固定，且不得把该实验称为集成通过。【`DynamicBuilderSurfaceParityTest` 真 GraalJS 6 用例：同 id 两写法读数/指纹相同、错误面同源、property 读回与 null≡抑制；`DynamicBuilderContract.Member.setter()` 唯一 Method 为结构保证；示例 harness 跨 reload 换写法 parity（ruby property→setter，指纹不变不冲突）】
- [x] 全规范化 fingerprint 覆盖 Builder 输入和约定连带声明，不依赖对象身份或部分字段；相同定义在重复 reload 中得到相同 fingerprint，字段或连带声明变化能被识别。【sha256(type|id|mode|全量可写属性读数)；`fingerprintCoversEveryBuilderInputTypeAndId`（三类型字段 + mode + 类型 + id 逐个可识别、读数为全量属性逐字断言）、`sameDefinitionAcrossReloadsGetsSameFingerprintAndReclaims`、parity 测试跨实例稳定】
- [x] 同一 key 的定义变化在第一版导致整批冲突失败，旧 active 定义继续服务；remove、replace、modify 和未来覆盖机制不出现在公开 Interface、golden、declaration 或迁移承诺中。【计划层 + reload 层双证据（domain=dynamic-registry-conflict、generation 不推进、旧指纹保留、未过 preflight 禁止 publish）；stale 项 replace 尝试同样冲突；`DynamicAdapterRequest` 构造期校验 action∈{register}；MIGRATION §3.4 明示不在第一版】
- [x] preflight、fingerprint 冲突和 Adapter 请求都是 generation-scoped inert candidate plan；脚本线程或候选失败不得修改 live registry、挂载生产 callback 或提前发布对外 binding，失败时临时计划和资源全部清理。【结构：`DynamicPlanInertnessTest`（计划面零执行通道/零 MC 类型/动作封闭）+ common 零 MC/loader；行为：`poisonedCandidateNeverAttachesItsListenerAndNeverWritesTheLedger`（失败候选后 `DYNAMIC_REGISTRY.hasListeners()` 仍 false）、`collectionErrorFailsTheCandidateWithoutAnyWrite`；失败清理＝计划不可达（记录只留诊断摘要、不持计划引用）且无平台资源】
- [x] 脚本不再声明的已暴露项标记 stale/retired，普通 reload 不物理删除；claim、stale、mode 与后续显式清理语义由 Registry Runtime/Adapter Interface 可观察并测试。【`staleMarkingIsNotAPhysicalDeletionAndKeepsTheConflictLedger`（stale/retired/claim/owner/trackedClaims 查询 + 指纹保留）、`DynamicRegistryReloadPipelineTest`、`undeclaredExposedEntriesBecomeStaleButAreNotPhysicallyDeleted`、`DynamicRegistrationBookkeepingTest` 12 用例；语义表见 REPORT §4；显式清理不在本票】
- [x] 调用者 Interface、Registry Runtime/Adapter 契约、TS/Python declaration、contract/golden 和本地行为测试形成同一条证据链；测试优先穿过事件 facade 与 Adapter Interface，不断言私有 Manager 字段。【单一契约输入 `DynamicBuilderContract` 反射 → runtime member/fingerprint/结构化条目 → 生产渲染器；`DynamicRegistryDeclarationParityTest` + 两份新 golden（`DynamicRegistryEventsDeclarationGoldenTest`）；测试断言面全为 payload/事件组/`DynamicRegistryPlanStore` 公开查询与 reload 失败报告】
- [x] 本票只证明本地 inert 计划行为，不激活多人同步、不宣称动态热更新或任何节点能力已完成；公开激活和能力结论由事务/同步 gate 决定。【计划面无网络/同步通道（通道扫描）；`collectInitial` 日志明示 inert local plan only；MIGRATION §4 不承诺热更新/多人同步；能力表把各方未验证项记 not verified（REPORT §3）】
- [ ] 旧 DynamicRegistry 静态全局入口和直接 registry surgery 路径只能在事件 facade、候选计划、Adapter 请求、声明和迁移路径全部覆盖且无消费者后删除；删除需维护者确认，不保留双写 shim。【**不勾选**：维护者删除确认是发布门禁（Human input note）。替代 parity + 旧 route 消费者清单（REPORT §6）+ 迁移材料（MIGRATION）已交付；本票旧面零删除、零双写】
- [x] 随本票交付隔离测试 harness 可运行的候选计划 fixture，演示类型直达入口、setter/property parity、同 key 冲突与 stale 查询；明确标注仅本地计划、尚未公开激活，不作为生产脚本使用指南。本票不等待票 21 关闭；由票 21 在事务/同步 gate 通过后发布对应生产示例与迁移材料，未开放类型不展示为可用能力。【`FacadeTestHarness` + `DynamicRegistryInertPlanExampleTest`（四场景，示例与 `examples/dynamic-registry-inert-plan.js` 逐行一致）；示例头注/Javadoc 双处标注「仅本地计划、未公开激活、非生产指南」；未开放类型在示例末段与能力表显式说明】

## Sources

- [NekoJS 实现票据拆分草案](../implementation-ticket-breakdown.md)
- [公开契约与插件模型规格](../specs/04-public-contract-and-plugin-model.md)
- [NekoJS 验证与迁移规格](../specs/07-validation-and-migration.md)
- [NekoJS 搬运功能事件面规格](../specs/08-ported-features-event-surface.md)
- [reload 候选状态与线程契约规格](../specs/09-reload-candidate-state-and-thread-contract.md)
- [NekoJS 实施交接单](../implementation-handoff.md)
- [实现票据索引](README.md)

## Dependency rationale

- [14: 事件总线与 Script/Native/Probe 事件声明基础](14-event-surface.md): 动态注册事件 wrapper、成员目录、dispatch 语义和 candidate 事件收集必须消费既有事件面基础，不新增第二 bus。

## Scope and coordination

**Rationale:** 把可本地闭合的 inert 定义、规范化、冲突与 stale 语义先独立交付，可以让 Builder 和事件契约在单上下文内验证；公开类型激活、多人 prepare/ack/commit 与网络/生命周期 gate 另行处理，避免一张过大的 Dynamic Registry 票。

**Coordination:**

- 与 RUNTIME_ROOT/RELOAD_COMMIT owner 对齐 candidate generation token、owner thread 和失败清理接口；RELOAD_COMMIT 是必要 blocker，接口细节仍需协调。
- 与 REGISTRY_STARTUP owner 共享类型事实与 Builder 表达方式，但禁止复用启动期全局 drain 路径；差异写入两张票的测试。
- 与 GLOBAL_STATE owner 只协调同一 candidate 中写集与注册计划的联合失败边界，不把 global 实现作为 Dynamic Registry blocker。
- 与 REGISTRY_STARTUP owner 共享类型事实、setter/property 语义和规范化输入，但 Dynamic Registry 使用自己的 Builder 路径且不得复用启动期全局 drain，因此不是阻塞；若实施时抽取共同 setter/fingerprint 契约，再升级为真实依赖并说明边界。

## Closure record（2026-09-17）

- 执行者：zcode-agent（接管未提交 worktree：认领基线 `230587cc`，接管时改动全部未提交、可编译）。实施/文档提交见 `git log --oneline 230587cc..HEAD`；合并与五节点全量 gate 归主会话。
- 交付物：`common/.../core/dynamic/plan/`（DynamicCandidateRegistryPlan/DynamicDefinition/Builder 三件套/Adapter 请求/PlanStore，零 static 领域状态——仅反射契约 memo 缓存，零 MC import）、`common/.../core/dynamic/facade/`（独立事件组 + payload + Runtime）、`CandidateDomainCollector`（reload 管线 seam）+ `ScriptManager`/`EventBusJS` 两处窄扩展、平台装配（`DynamicRegistryFacade`/`DynamicRegistryPlugin` 事件与声明贡献、`ServerEventListener` 初次候选触发）、9 个测试套件（43 用例，真实 GraalJS + 事务式 reload，另同域 12 用例为旧路径 prior art）、两份声明 golden、`REPORT.md`/`MIGRATION.md`/示例。
- 接管后补齐的缺口：候选期收集可观察记录（AC1）、收集器 catch/上报形态与生产分发对齐（AC7）、`DynamicPlanInertnessTest` 结构性惰性证据（AC7）、触发点 `>=26` 守卫（**因果更正**：1.21.1 的编译单元是 `versions/1.21.1/src` 孪生文件，共享文件不参与其编译，因此不存在即时编译断裂；守卫的作用是让孪生重提取时不会把 26.x 专属符号带进 1.21.1，见 REPORT §10 P1）、fingerprint 字段覆盖与未知类型拒绝用例（AC3/AC5）、示例 parity 场景（AC4/AC12）、contract/golden（AC2/AC9）。
- 双轴审查整改（2026-09-17）：M1 守卫因果更正 + 孪生注释同步（含提取审计，REPORT §10 P1/G10）；M2 撤换失实引文为 spec 08:61/08:63 实际措辞；N1 `DynamicRegistryReloadPipelineTest` 改用唯一化 gameDir（REPORT §11 G9）；N2 数字勘误；N3 收集器 catch 对齐生产；N5 Python 参数下标修复；N6 本文档「零可变 static」措辞；N7 示例/ fixture 人工联动注释；M3 三条声明面偏窄登记且明示非 AC9 证据（G3/G4）。
- 验证（真实运行，REPORT §9 + `evidence/verification-commands.md`）：整改后复跑 `:common:check` + `:common-api-processor:test`、`guardLint`、`:26.1.2:build :1.21.1:build` 全绿（数字见报告与 evidence）。26.2.0 与两个 fabric 节点全量未跑（主会话合并门，未伪报）。
- 遗留（REPORT §11）：AC11 维护者 sign-off（旧面零删除，消费者清单/迁移材料已备）；真机 smoke 与 probe 真机输出（G1/G2）；`$DynamicRegistryEventJS` 声明 import、`fixedRange` null 抑制态、`setMode` 链式返回 any（G3/G4，均非 AC9 证据）；配置门与 facade 关系待票 21（G7）；共享/孪生对维护口径（G10）；五节点全量（G5）。

票据发布不代表已完成验收或本轮授权源码实施；完成条件与认领规则见本目录索引。
