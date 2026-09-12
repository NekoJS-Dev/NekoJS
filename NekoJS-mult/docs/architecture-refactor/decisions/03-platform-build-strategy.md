# 版本与加载器差异如何组织，Stonecutter 何去何从？

Status: closed
Type: grilling
Mode: HITL
Parent: [NekoJS 架构重构路线图](../../architecture-refactor-map.md)
Assignee: session-0ccab587-1d9c-436f-b53b-a6428bdb1aaf (主 agent，与维护者共同裁决)
Blocked by: [维护者的最小理解范围与目标模块归属如何确定？](01-maintainer-module-design.md)

## Question

版本与加载器差异如何组织，Stonecutter 何去何从？

在支持矩阵和目标归属明确后，需要裁决差异的承载方式。问题维度包括：

- 是否采用显式 loader/version 源码根、Fabric bridge、active IDE、guards 与 metadata；每种信息的事实源和编译/编辑器可见性如何保持一致。
- Stonecutter 在收紧使用范围、调整版本树和退出使用三种方向上的收益、迁移风险、验证成本与维护者体验如何比较。
- 若考虑退出 Stonecutter，替代方案必须先有可运行、可验证的证据；本票不能未经验证就搭建自研预处理器或把构建风险转给维护者。
- 如何处理共享树、节点目录、版本 facade、replacements 与平台 adapter 的职责，避免逻辑差异藏进机械改名或隐式生成。

证据入口：[build evidence](../evidence/build-and-platforms.md)、[proposal.md](../proposal.md)。[ADR-0007](../../adr/0007-module-boundaries.md) 和 [ADR-0008](../../adr/0008-guard-discipline.md) 是历史约束与候选纪律，需在本票中显式比较并说明是否重评，不能预先视作本次方案已通过。

## Resolution

### 归属与依赖方向

1. `common` 是 MC/loader-free 的跨平台 engine；包括 `api.*` 在内禁止 Minecraft/loader import。GraalJS 可以在 `common` 中使用。
2. 根 `src/` 是允许引用 Minecraft/loader 的共享 MC-facing 树；只有确实共享、且版本/loader 差异可由现有 facade、guard 或 adapter 表达的实现才放这里。
3. `versions/<node>` 承载无法以小型 Adapter/facade 表达、整文件差异明显或高湍流的节点实现。版本 node 不持有第二套 runtime owner、Plugin Runtime 或业务语义。
4. 平台/版本 Adapter 只向 shared Module 提供明确能力、生命周期时机、registry/network/event 接线；common 不反向依赖 platform artifact。两套真实 Adapter 才建立公开 Seam。

### Stonecutter

1. 当前保留 Stonecutter，不批准替换或退出。它负责 variant evaluation、机械 replacements、资源/metadata/mixin 处理、active-source IDE 语义和全节点验证；新业务逻辑不得藏入 replacements。
2. loader/version 业务差异应退出 shared business code，优先进入已有 compat facade、平台 Adapter 或节点 Implementation。Fabric source bridge 可作为当前构建事实，但不是终态 source ownership；每个保留 node 必须有可追溯的唯一源。
3. 退出 Stonecutter 只有在替代方案覆盖五个 node，并证明编译、测试、artifact/metadata/mixin、processor、IDE/source trace、runtime smoke 和一次真实新版本接入均等价或更好后，才可另开决策。当前不启动替代构建。
4. 支持等级沿用 02：NeoForge 26.1.2 primary、26.2.0 secondary，其余三个 node experimental；不同 gate 不得被解释成静默功能 parity。

本票关闭平台差异承载和 Stonecutter 方向，但不批准任何具体源码移动、节点删除、构建替代或业务代码修改；这些进入 07 的实施验收和后续授权。
