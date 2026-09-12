# 模块边界：三层归属判据

> **本轮规划修订（用户直接澄清）**：`common`（含 `com.tkisor.nekojs.api.*`）允许使用 GraalJS，common 内不设零 Graal import 约束；不为隔离 Graal 而抽 DTO、adapter 或另一个 API jar。`common` 的零 MC/loader 隔离保留。此注记修订当前规划前提；api 的职责、命名、具体签名、生命周期和 artifact 形状已由 04 Resolution 定义，实施映射仍由 07 验证。下方旧的“契约层零 Graal import”只保留为历史决策理由，不再是当前约束。

> **2026-08-30 修订（本文件现状）**：`common-api` 并入 `common`，契约类型改住
> `com.tkisor.nekojs.api.*` 包，边界改由 `guardLint` 的包前缀规则承载。下表已改为三层。
> 修订理由：该模块的边界收益从未兑现——两模块都不发布制品、都被整体嵌进平台 fat jar，
> 不存在"只依赖契约层"的消费者；而代价实在（接口无法自带静态工厂、7 个包拆在两个 jar、
> 40 行只为跨 jar 编译正确性存在的 `$SwitchMap$` 指纹守卫）。完整评估见
> `docs/module-boundary-common-api.md`。
>
> **2026-08-29 修订（ADR-0010 §4）**：`NekoJSPlugin` 经实施裁决移回 `common`——18 个恢复钩子的参数类型全在 common，反向搬 14 个类型会级联拖出内部依赖；FQCN 不变。当时它的边界守护由 `checkCommonIsolation` 与 `PluginHookPairingTest` 承担。
>
> **决策 1（插件入口契约迁入 `common-api`）已终止**：ADR-0010 §4 记录了它撞上什么（钩子
> 参数类型全在引擎侧，迁接口会级联搬实现），本次并入则以"契约层不再独立存在"终结了该
> 路线。若将来确需一个可单独依赖的 API 制品，前置条件是先解决插件钩子参数类型与引擎实现
> 的解耦——重新拆模块不解决任何问题。

`common`（引擎单模块）承载对外契约（`com.tkisor.nekojs.api.*` 包）与跨平台引擎；节点目录几乎全是参数（节点专属源码仅 5 个文件），即几乎所有代码都堆在共享树。本 ADR 把归属规则成文：

## 三层归属判据

| 层 | 装什么 | 硬边界 |
|---|---|---|
| `common` 下的 `com.tkisor.nekojs.api.*` 包 | **对外契约**（数据契约 + 插件契约） | 零 MC / Loader import；允许 Graal（`guardLint` L1，按包前缀取材） |
| `common` 其余包 | **跨平台引擎**（Graal runtime、模块系统、probe、事件总线、V2 扩展点机制） | 可用 Graal，零 MC / Loader import（`guardLint` L2 + `checkCommonIsolation`） |
| `src` 共享树 | 需要触及 MC/loader、且差异**可守卫表达**的代码（wrapper、bindings、client…） | 单副本，守卫表达差异 |
| `versions/<node>` | **节点独有**（差异大到守卫不划算：整文件/整类不同） | 越少越好 |

一句话判据：**能放低层就放低层；守卫表达不了的下放节点目录**。"什么该放 `api.*`"的自查判据：被版本树或插件消费的类型放 `api.*`，只有引擎内部消费的不放。数字阈值（守卫多少条算"不划算"）归 M2 / ADR-0008。

## 决策

1. ~~**插件入口契约迁入 `common-api`**~~ —— **已终止**（2026-08-29 实施裁决推回，2026-08-30 随模块并入正式终结，理由见文件头修订注记）。`NekoJSPlugin` 留在 `common`，钩子-点配对由 `PluginHookPairingTest` 看住。
2. **新代码按同判据落位**：ADR-0004 平台适配层——NeoForge adapter（1.21.1 / 26.x 同 API）→ 共享树守卫内；Fabric adapter → `26.1.2-fabric` 节点目录（整文件差异）。
3. **client/GUI 高守卫区保留共享树**（RE1：client 包 258 条守卫为全树最密）——单副本价值 > 守卫成本，密度治理归 M2，不因密度下放。
4. **边界做编译期检查**：L1/L2 零 MC/loader import、wrapper 层零 loader import（ADR-0004 要求）——**落地于 `guardLint` 任务的 import 扫描**（同为已有机制、单一工具入口，与 ADR-0008 第 5 条一致；核心决策"不引入 ArchUnit 新依赖"不变）。L1 自模块并入起按包前缀（`com/tkisor/nekojs/api/**`）取材，不再依赖模块目录划分。

## Consequences

- 引擎层为两个子项目：`common` 与 `common-api-processor`（后者必须独立，才能作为 annotationProcessor 使用）。`common` 不发布 Maven 制品，产物由各节点内嵌进平台 fat jar。
- 历史上曾由 lint 而非 javac 强制“契约层零 Graal import”：并入后 `api.*` 里的类在编译期引用引擎内部不会报编译错，这一历史理由仍保留但不再构成当前 Graal 约束。当前源码 lint 尚未更新；未来代码实施时移除或更新过时的 Graal 禁令，同时保留 api/common 的 MC/loader 隔离。
- 原 `forge/` 独立分支条款已失效（2026-08-30：1.20.1 移植取消，骨架自仓库移除）。
