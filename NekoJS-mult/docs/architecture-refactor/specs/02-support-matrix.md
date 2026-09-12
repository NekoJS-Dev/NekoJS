# 版本与加载器支持矩阵规格

Status: ready-for-agent
Type: spec

## Problem Statement

NekoJS 当前维护五个 Stonecutter 节点，但它们的成本、风险和验证承诺并不相同。如果所有节点都被视为同等支持，维护者会被低价值全量验证拖慢；如果只凭一次构建失败或文档过时就静默删除节点，又会丢失真实用户和平台能力。NeoForge 与 Fabric 的能力也不对称，Fabric 的实验状态不能被当成“所有缺口都可以不记录”的许可。

本规格要建立一个可由维护者确认的支持矩阵：明确每个节点的支持等级、发布门禁、升级或降级触发条件，以及每项 loader 能力如何显式标为 `supported`、`partial`、`unavailable` 或 `not verified`。它既不自动退役节点，也不把尚未验证误写成不可用。

## Solution

保留当前五个节点，并采用三级维护承诺：

| 节点 | 加载器 | 支持等级 | 维护承诺 |
|---|---|---|---|
| `26.1.2` | NeoForge | primary | 当前主线；缺陷修复、完整验证和发布阻断优先级最高 |
| `26.2.0` | NeoForge | secondary | 保持正式构建与发布验证；优先级低于 primary，但不降为未支持 |
| `1.21.1` | NeoForge | experimental | 保留实验构建与已声明能力验证，不承诺与 primary 全面等价 |
| `26.1.2-fabric` | Fabric | experimental | 保留实验构建；能力差异逐项公开并测试 |
| `26.2.0-fabric` | Fabric | experimental | 保留实验构建；source bridge 不作为终态能力承诺 |

支持等级只定义维护和发布承诺，不替代能力矩阵。每个 NeoForge/Fabric 能力必须有显式状态和证据 owner；未验证只能记为 `not verified` 并阻塞对应域验收，不能自动改写为 `unavailable` 或 `partial`。任何节点的 EOL、升级或降级都需要新证据、迁移/发布影响说明和维护者确认，不由单次失败自动触发。

## User Stories

1. 作为维护者，我希望五个节点的支持等级集中可见，以便知道每项工作的验证深度和发布优先级。
2. 作为维护者，我希望 NeoForge `26.1.2` 作为 primary 承担最高优先级和发布阻断，以便主线回归能被及时修复。
3. 作为维护者，我希望 NeoForge `26.2.0` 保持 secondary 的正式构建和发布验证，以便它不会静默退化为未支持。
4. 作为维护者，我希望 NeoForge `1.21.1` 与两个 Fabric 节点保持 experimental，以便实验工作仍有可重复构建和已声明能力 smoke。
5. 作为维护者，我希望所有五个节点继续保留，以便任何 EOL 都必须经过新的证据和人的确认，而不是由一次失败自动发生。
6. 作为发布负责人，我希望 primary 必须通过 compile/check、artifact/metadata、contract、data fixture、runtime smoke 和维护者试做，以便主线发布有完整门禁。
7. 作为发布负责人，我希望 secondary 必须保持可构建、可发布验证和公开契约可追踪，以便版本差异可以降低优先级但不能隐藏。
8. 作为发布负责人，我希望 experimental 至少通过可重复构建、产物验证和已声明能力 smoke，以便实验状态仍有最低可信度。
9. 作为维护者，我希望 NeoForge/Fabric 的每项能力标为 `supported`、`partial` 或 `unavailable`，以便作者知道实际可用范围。
10. 作为维护者，我希望缺少测试或证据的能力标为 `not verified`，以便尚未验证不会被误写成 `unavailable`。
11. 作为 Java 插件作者，我希望支持矩阵明确 loader 能力差异，以便不会在 Fabric 上依赖被静默省略的 NeoForge 行为。
12. 作为脚本作者，我希望发布说明引用 capability matrix，以便升级前能判断脚本和 registry 行为是否受影响。
13. 作为 Fabric 维护者，我希望 experimental 不等于静默漏项许可，以便每项缺失或部分能力都有记录、测试或明确延期说明。
14. 作为 Fabric 维护者，我希望 source bridge 被视为当前构建事实而不是终态能力承诺，以便后续收口不被误解为已经完成 parity。
15. 作为维护者，我希望升级、降级或 EOL 的触发条件包含价值/成本证据、迁移和发布影响以及维护者确认，以便支持范围变化可审计。
16. 作为维护者，我希望一次节点构建失败只产生诊断和修复工作，不自动改变支持等级，以便短暂故障不会造成意外退役。
17. 作为发布负责人，我希望节点集合与 CI 子集按用途保持一致，以便全节点 build、release/publish 和单平台 smoke 不会互相冒充。
18. 作为维护者，我希望能力矩阵引用具体验证证据，以便每个 `supported`、`partial` 或 `unavailable` 判断都可追溯而不是靠声明。

## Implementation Decisions

- 当前五个节点全部保留：NeoForge `26.1.2`、NeoForge `26.2.0`、NeoForge `1.21.1`、Fabric `26.1.2-fabric`、Fabric `26.2.0-fabric`。
- 支持等级固定为 NeoForge `26.1.2` primary、NeoForge `26.2.0` secondary，其余三个节点 experimental。支持等级是维护承诺，不是能力 parity 承诺。
- primary 门禁包括编译与检查、artifact/metadata、contract、data fixture、runtime smoke 和维护者试做；任一必要项回归可以阻塞发布。
- secondary 门禁包括可构建、发布验证和公开契约可追踪。版本差异可以降低执行优先级，但必须进入 capability matrix 和 release notes。
- experimental 门禁至少包括可重复构建、产物验证和已声明能力的 smoke；不自动承诺完整脚本、registry、recipe、client、network 或 probe parity。
- 每项 NeoForge/Fabric 能力使用 `supported`、`partial` 或 `unavailable`。没有测试或证据时使用 `not verified` 并阻塞对应域验收，不得直接改判为不可用或部分可用。
- 能力矩阵必须由 03 的平台/构建方案和 07 的验证账本引用。支持矩阵负责等级，能力矩阵负责逐项状态，两者都不能靠未记录的 skip 或缺失测试自动推导。
- Fabric 的 experimental 状态不构成静默漏项许可。每项能力差异必须有公开状态、验证或明确延期说明。
- Fabric source bridge 是当前构建事实，不是终态 source ownership 或能力承诺；后续收口必须服从 03 的 source trace 与 parity 条件。
- 升级、降级和 EOL 必须由新的证据、迁移/发布影响说明和维护者确认触发。单次构建失败、节点存在感降低或目录整理都不能自动触发等级变化。
- 保留 `26.2.0` 节点身份、`26.2` Minecraft 坐标语义和 Fabric 26.2 制品命名，不在矩阵整理中擅自归一化。
- 支持矩阵和 CI 子集按用途分别维护：全节点构建、NeoForge 专属验证、Fabric artifact/smoke、release/publish 可以是有意子集，但必须说明用途并与节点图一致。

## Testing Decisions

- 测试的最高 Seam 是公开的支持矩阵与 capability matrix，而不是某个 loader 内部类。矩阵声明的等级和状态必须能由节点构建、artifact、contract、smoke 和 source trace 证据支撑。
- primary 的成功断言是编译与检查、artifact/metadata、contract、data fixture、runtime smoke 和维护者试做均存在并通过；失败断言是任一必要门禁缺失、跳过未说明、artifact 不匹配或 runtime smoke 失败并阻塞发布。
- secondary 的成功断言是构建、发布验证和公开契约可追踪；失败断言是版本差异未进入 capability matrix 或 release notes。
- experimental 的成功断言是可重复构建、artifact 验证和每项已声明能力 smoke 通过；失败断言是能力被静默省略、只凭 build 宣称可用或未记录 skip。
- `PlatformCapabilityTest` 是能力枚举和不可变集合的 prior art；它应继续保护稳定枚举，但不能单独证明某个 loader 的 runtime 能力。
- `ApiManifestGoldenTest` 与 `ProbeOutputCompatibilityTest` 是公开契约和派生输出 prior art；成功断言是 capability/契约输出确定且可审阅，失败断言是平台差异被合并进同一个未标注的 golden。
- Fabric artifact verification、NeoForge NBT smoke 和 Fabric development server smoke 是现有节点门禁 prior art。测试应断言每个节点实际发现、执行或明确 skip 的测试和 smoke，而不是只检查任务存在。
- 测试失败不得自动把节点标为 EOL 或降级。测试应产生节点、能力、输入、输出和失败诊断，支持矩阵变化仍由维护者裁决。
- 当前没有完整覆盖五节点全部声明能力的矩阵报告，这是获准实施后的验证缺口。补齐应复用现有 JUnit、artifact gate 和 loader smoke，不新增全仓测试框架。

## Out of Scope

- 删除、退役或新增任何 Stonecutter 节点。
- 改变已裁定的支持等级，或把 experimental 自动提升为完整 parity。
- 决定每个具体功能域的 `supported`、`partial`、`unavailable` 内容；这些由 03、07 和相应功能域负责。
- 用本规格批准 Fabric processor 接入、source bridge 删除、节点坐标改名或制品重命名。
- 创建 GitHub issue、标签镜像或新的测试框架。

## Further Notes

- 本规格派生自 [哪些版本与加载器组合值得持续维护？](../decisions/02-support-matrix.md)。该票的 Resolution 是裁决权威；本文件只是执行视图。
- 平台差异承载和 Stonecutter 门禁见 [版本与加载器差异如何组织，Stonecutter 何去何从？](../decisions/03-platform-build-strategy.md) 及派生规格 [平台构建与 Stonecutter 策略规格](./03-platform-build-strategy.md)。
- 发布验证、能力门禁和迁移条件见 [怎样以可验证的阶段完成本次重构并作为新标准？](../decisions/07-validation-and-migration.md) 及派生规格 [验证与迁移规格](./07-validation-and-migration.md)。
- 节点实施顺序、W8/W9 补充和 release 产物见 [NekoJS 实施交接单](../implementation-handoff.md)。
- `Status: ready-for-agent` 只表示规格可交给代理继续处理，不构成节点增删、源码、测试、构建、迁移或发布授权。
- 获准实施后，本规格范围内的源码重构、验证与验收属于执行内容；本轮只完成规格文档，没有运行构建、测试或运行时验证，`supported`、`partial`、`unavailable` 和 `not verified` 的实际填充必须按证据完成。