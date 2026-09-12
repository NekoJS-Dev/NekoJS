# 怎样以可验证的阶段完成本次重构并作为新标准？

Status: closed
Type: grilling
Mode: HITL
Parent: [NekoJS 架构重构路线图](../../architecture-refactor-map.md)
Assignee: session-0ccab587-1d9c-436f-b53b-a6428bdb1aaf (主 agent，与维护者共同裁决)
Blocked by: [版本与加载器差异如何组织，Stonecutter 何去何从？](03-platform-build-strategy.md)、[脚本表面与插件作者模型如何只有一个事实源？](04-public-contract-and-plugin-model.md)、[自研转译与模块加载应如何拆分且保持语义可控？](06-language-module-pipeline.md)

## Question

怎样以可验证的阶段完成本次重构并作为新标准？

在前置结构、支持矩阵、公开契约、运行时和语言管线问题有了裁决后，需要确定一条可交给实施的验证与迁移路线。问题维度包括：

- 垂直迁移顺序如何选择，怎样让每阶段都能由维护者真实试做并暴露登记、依赖和归属成本，而不是只按目录批量搬运。
- contract、probe、smoke、final jar、data tests 以及真实维护者试做各自验证什么；旧实现的内部 golden 是否可以重建，若可行需要什么明确批准、期限和删除条件。
- breaking 脚本/插件接口的迁移表、持久化数据保护、失败备份/回滚和现有功能逐项确认如何进入阶段验收。
- 兼容桥何时达到删除条件，怎样证明最终只剩职责清晰的单套实现，而不是把过渡层永久留下。
- 本次完成后的版本号如何按新标准 +0.1 发布；不等待 2.x，也不把“两个发布、一次 major breaking”的旧假设当作默认规则。

证据入口：[testing evidence](../evidence/testing-and-docs.md)、[build evidence](../evidence/build-and-platforms.md)、[proposal.md](../proposal.md)。[ADR-0009](../../adr/0009-release-and-acceptance-strategy.md) 是历史发布与验收约定；本 Resolution 以 `1.2.0`、一次 clean cutover 和本票的新门禁取代其中旧的两发布/major 假设，但不改写历史 ADR。
## Resolution

### 目标与阶段顺序

本次重构完成后以 `1.2.0` 作为新标准版本，从当前 `1.1.0-preview3` 完成一次 clean cutover。P0-P4 是同一发布前的内部实施与验收阶段，不分别产生 public breaking，也不授权今后任意 breaking。

| 阶段 | 目的 | 必须留下的证据 | 退出条件 |
|---|---|---|---|
| P0 基线 | 固定五节点、功能覆盖、公开契约、golden、artifact 和数据输入 | test discovered/skip/count、manifest/Probe、jar 清单、runtime 日志、data fixture、coverage ledger | 基线可重复生成，普通测试不写入基线；每项能力有 owner 或明确 deferred |
| P1 runtime owner | 收拢单一 `NekoRuntimeRoot`、共同装配、reload 资源所有权 | NeoForge/Fabric assembly 对照、startup/CLIENT/afterInit、reload/close、错误阶段和资源释放 | 无第二 runtime owner；reload 失败保留 NekoJS 自有 active runtime/state，不双注册平台资源 |
| P2 runtime domains | 垂直迁移 Plugin Runtime、Script Preparation/Resolution/Execution、Managed Surface、Registry Runtime | Point/Hook/Handle、language corpus、source map、contract/golden、registry parity | 调用者只跨小 Interface；无第二语义 pipeline 或第二规范源 |
| P3 feature/platform parity | 逐域迁移事件、recipe、client/UI/render、network/PData、command、diagnostics、pack trust | capability matrix、每域 contract fixture、loader smoke、artifact/source trace、外部 addon fixture | 每个功能域覆盖账本行闭合；unsupported/partial 不被静默伪装成 parity |
| P4 cutover/cleanup | 删除无调用者的重复路径，更新文档、迁移表、支持矩阵和发布产物 | old/new diff、迁移说明、data rollback fixture、全节点 release report、维护者四类试做 | compatibility shim/旧 route 达到删除条件；最终只剩单套标准实现；`1.2.0` release gate 全绿 |

### Contract、golden 与节点门禁

1. 普通测试禁止写 golden、manifest、Probe 基线或其他规范产物。只有显式 regenerate 任务可以更新；更新必须同时提交旧/新 diff、原因、受影响功能、迁移影响和维护者审阅记录。
2. contract 测试验证 managed surface、legacy preview、Plugin Point/Hook/Handle、registry runtime/declaration、packet/diagnostic 字段和 capability matrix；golden 只冻结有意承诺，不冻结 private helper、目录布局或对象身份。
3. `26.1.2` primary 的回归阻塞 release；`26.2.0` secondary 必须通过构建、artifact、契约和已声明能力验证；`1.21.1`、`26.1.2-fabric`、`26.2.0-fabric` experimental 至少通过可重复构建、artifact 和已声明能力 smoke。任何节点的 unsupported/partial 必须显式记录。
4. 四类维护者试做是 release gate：新增事件、新增 Adapter（区分注册类型/Builder 与平台能力）、新增扩展点、新增版本。维护者必须能从入口追到 owner、事实源、依赖方向、受影响节点和测试。

### Public migration、数据保护与回滚

1. Script/Plugin public breaking 通过迁移表发布；不保留长期 compatibility shim、deprecated wrapper 或双运行时路径。功能删除必须逐项记录脚本/插件影响、数据影响、替代路径和维护者确认。
2. 普通 runtime 错误保持普通形式，不携带修复提示。可以提供显式运行的离线 validator/migration report，但它不进入普通执行错误路径，也不成为新的 Script API 事实源。
3. 数据迁移只在格式确有必要变化时引入专用 migration：备份或原子替换、schema/version 标记、旧 fixture 回读、幂等验证、失败恢复和保留原始数据直到验证完成。不得用通用 migration framework 扩大重构范围。
4. release rollback 与 data rollback 分开。旧 artifact 可以回退；`config`、world、实体/玩家 pdata、脚本/pack、trust-store、用户编辑的 workspace/declaration 和历史日志在验证完成前不能被覆盖或删除。reload 失败只保证 NekoJS 所拥有的 runtime 资源回退，不承诺撤销脚本对 Java、网络、世界或其他外部对象造成的副作用。
5. 默认不改变既有路径、key、wire id、格式、默认启用规则、pack manifest 或 sandbox policy。任何例外都必须进入迁移表并有旧数据 fixture。

### Release handoff

`1.2.0` 发布前必须同时存在：五节点 build/check/artifact 报告、capability matrix、managed/legacy/plugin/registry contract 报告、语言 corpus/source-map 报告、runtime smoke、data migration/rollback fixture、维护者试做记录、脚本/插件迁移文档和 README/wiki/ADR 一致性检查。

本票关闭阶段验证、迁移、回滚和版本交接方向；它不批准删除具体源码或执行格式迁移。实施仍须按每个 coverage ledger 行和本 Resolution 的 gate 逐项授权、记录和验收。

### 已确认补充：规划收口中的验收范围

- Fabric `common-api-processor` 接入在 1.2.0 延期；不在 Fabric source root 迁移中顺手启用。W9 负责写清并实现非 processor 的合同/平台能力替代 gate，保留未覆盖说明；延期不免除已声明能力的构建、artifact、contract 与 smoke 验证，也不伪称具有 NeoForge processor 等价性。
- 性能采样是独立 P0 工作，不是 W0 source/artifact manifest 的内容。记录环境、负载、预热、重复次数和统计口径；维护者在取得基线后、P4 前确认是否设置发布阻断阈值。未确认的阈值不能由实现者自行编造，也不能把采样报告省略为“性能不阻塞”。
- 离线 validator/migration report 可选、默认只读，不是 1.2.0 硬 release gate；它不替代必须的公开接口迁移表、必要数据迁移/回滚 fixture 或 contract diff。
- 规划完成与实施验收分开：本票要求的 build、runtime smoke、golden diff、数据回滚和维护者试做是实施/发布证据，不要求在 wayfinder 规划收口前先修改代码或运行迁移。规划阶段必须把 owner、输入、产物、失败诊断与 gate 写清；“已有计划”不能标成“验证已通过”。
