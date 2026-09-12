# 运行时所有权、reload 与数据保护的契约是什么？

Status: closed
Type: grilling
Mode: HITL
Parent: [NekoJS 架构重构路线图](../../architecture-refactor-map.md)
Assignee: session-0ccab587-1d9c-436f-b53b-a6428bdb1aaf (主 agent，与维护者共同裁决)
Blocked by: [维护者的最小理解范围与目标模块归属如何确定？](01-maintainer-module-design.md)

## Question

运行时所有权、reload 与数据保护的契约是什么？

需要把生命周期和持久化保护写成可验证的契约，而不是由某个实现阶段自行扩大或缩小数据窗口。问题维度包括：

- Engine、Context、session、timer、listener、source map 的所有权、创建与释放顺序、跨 reload 可见性和错误归属。
- NeoForge 与 Fabric 各自的 composition root 如何组装同一套跨平台运行时契约，平台 adapter 不能偷偷成为第二套生命周期。
- 本地可信脚本与远端脚本的显式受限授权如何进入生命周期；能力拒绝、降级和审计需要由哪个契约负责，且不承诺对任意恶意代码强隔离。
- reload 失败时是否保留 old runtime/old state、如何报告新旧切换边界、timer/listener/source map 如何避免泄漏或双重注册。
- data inventory 如何覆盖 config、world、pdata、packs、cache 等格式；迁移成功、失败、备份、回滚和版本标记的契约是什么，哪些数据绝不丢失；这些数据窗口不能由本票之外的实现者自行定义。

证据入口：[runtime evidence](../evidence/runtime-and-modules.md)、[proposal.md](../proposal.md)。本票应给出可由 contract、probe、smoke 和 data tests 检查的所有权与保护边界，但不提前规定未被证据支持的格式或迁移时间窗。

## Resolution

### Runtime 与 reload

1. `NekoRuntimeRoot` 是唯一 runtime owner；NeoForge 与 Fabric 的 composition root 只负责原生生命周期、事件、网络和 registry 时机，不能各自持有第二套 runtime lifecycle。
2. 普通 reload 的切换单位是脚本环境与模块 session。它可以重建 prepared module、Graal Context、bindings、timers 和 script listeners，但不重新 bootstrap/freeze Plugin Runtime，不重复注册平台事件、registry 或 network。
3. reload 先构造候选环境；准备、执行或绑定失败时，候选资源全部关闭，当前 active runtime/state 保持可用，并报告带 source location、阶段和错误原因的结果。候选完整通过后才切换，旧环境再按 timer/listener/context 所有权顺序释放。这里的保留只覆盖 NekoJS 所拥有的 runtime 资源，不承诺撤销脚本已经通过 Java、网络、世界或其他外部对象造成的副作用。
4. 插件实现、平台注册或 capability 发生变化，需要显式的进程/loader 重启或另行批准的重新装配契约；不能借普通脚本 reload 隐式改变。
5. 本地可信脚本与远端脚本继续使用显式受限授权；拒绝、降级和审计必须出现在执行/pack trust 结果中，不宣称对任意恶意 guest 强隔离。

### 持久化数据与迁移

1. 普通 Module 重排默认不改变 config、world、实体/玩家 pdata、脚本与 pack 路径、格式、key、读写语义、默认启用规则或远端 trust-store 决策。
2. 不可再生或用户编辑的数据必须保留；probe 输出和 module cache 只有在来源可重建且有证据时才可重建，workspace config/declaration 不得因“生成物”标签被盲目覆盖。
3. 只有确有必要时才引入格式专用 migration；必须先备份或原子替换，写入 schema/version 标记，验证旧 fixture 可读，失败可回滚，并保留旧数据直到验证完成。不得引入没有数据收益的通用 migration framework。
4. `PersistentDataJS` 的 key 与跨 loader pdata 语义属于保护清单；network sync 的 wire id/格式默认保持，任何改变必须由 07 的迁移表和 fixture gate 单独批准。

### 票范围

本票不拆成独立 runtime 与 data 票。两者在 reload cutover、失败回滚和持久化保护上共享同一条风险路径；Resolution 用上述两个子契约保持范围可审查，避免新增票制造重复状态。具体语言管线由 06，阶段/发布/迁移证据由 07 继续裁定。

### 已确认补充：Fabric WORLD pack

维护者在规划收口中确认：1.2.0 保持当前 Fabric WORLD pack 的行为，公开其已知生命周期差异；完整 parity 不是本次发布的硬要求。能力表按具体行为及证据标 `partial` 或 `unavailable`，延期是工作状态而非第四种 capability 值；不以 experimental 身份隐藏差异，不改变本地 pack 的路径、默认启用或信任规则。
