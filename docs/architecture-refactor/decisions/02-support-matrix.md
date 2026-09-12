# 哪些版本与加载器组合值得持续维护？

Status: closed
Type: grilling
Mode: HITL
Parent: [NekoJS 架构重构路线图](../../architecture-refactor-map.md)
Assignee: session-0ccab587-1d9c-436f-b53b-a6428bdb1aaf (主 agent，与维护者共同裁决)
Blocked by: none

## Question

哪些版本与加载器组合值得持续维护？

需要形成可由维护者确认的支持矩阵判据，而不是替用户直接删节点。问题维度包括：

- 如何定义长期主线、次要支持、实验性支持和 EOL，并为每一档给出价值/成本、缺陷修复、发布与验收门槛。
- 最新项目版本如何自行选择长期维护的 Minecraft 目标；26.1.2 只能作为候选例子，不是唯一主线，也不是 Mojang 官方 LTS。
- NeoForge 与 Fabric 的能力差异怎样计入支持等级；Fabric 的最低能力和逐步补齐的门槛是什么。
- 现有五个节点在矩阵中的事实位置、保留理由和成本证据是什么；任何节点的 EOL 或删减都必须另行逐项得到人的确认。

证据入口：[build evidence](../evidence/build-and-platforms.md)、[testing evidence](../evidence/testing-and-docs.md)。本票的决策应给出支持等级、升级或降级触发条件，以及每个加载器组合的发布门槛。

## Resolution

### 支持矩阵

维护者已裁定当前五个 Stonecutter node 的支持等级如下：

| node | loader | 支持等级 | 说明 |
|---|---|---|---|
| `26.1.2` | NeoForge | **primary** | 当前主线；缺陷修复、完整验证和发布阻断优先级最高 |
| `26.2.0` | NeoForge | **secondary** | 保持正式构建与发布验证；优先级低于 primary，不能静默退化为未支持 |
| `1.21.1` | NeoForge | **experimental** | 保留实验构建与能力验证，不承诺与 primary 全面等价 |
| `26.1.2-fabric` | Fabric | **experimental** | 保留实验构建；能力差异必须逐项公开和测试 |
| `26.2.0-fabric` | Fabric | **experimental** | 保留实验构建；不得把 source bridge 当作终态能力承诺 |

### 等级门槛

1. **Primary** 必须通过 compile/check、artifact/metadata、contract、data fixture、runtime smoke 和维护者试做；其回归可阻塞发布。
2. **Secondary** 必须保持可构建、可发布验证和公开契约可追踪；版本差异可降低优先级，但必须进入 capability matrix 和 release notes。
3. **Experimental** 至少必须有可重复的构建、产物验证和其已声明能力的 smoke；不自动承诺完整脚本、registry、recipe、client、network 或 probe parity。
4. NeoForge/Fabric 的每项能力必须标为 `supported`、`partial` 或 `unavailable`，并由 03 的 platform/build 方案和 07 的验证账本引用；“Fabric experimental”不是静默漏项许可。
5. 本票不批准删除任何 node。升级、降级或 EOL 必须有新的证据、迁移/发布影响说明和维护者确认，不由一次构建失败自动触发。

该 Resolution 只关闭支持等级与维护承诺的决策；具体源码落点、Stonecutter 终局和每域 capability 仍由 03、04、06、07 处理。
