# 模块边界：四层归属判据

`common-api`（93 文件）已有部分契约包，但插件入口 `NekoJSPlugin` 仍在 `common`（README 注明的过渡状态）；节点目录几乎全是参数（节点专属源码仅 5 个文件），即几乎所有代码都堆在共享树。本 ADR 把归属规则成文：

## 四层归属判据

| 层 | 装什么 | 硬边界 |
|---|---|---|
| `common-api` | **对外契约**（数据契约 + 插件契约） | 零 MC / Loader / Graal import |
| `common` | **跨平台引擎**（Graal runtime、模块系统、probe、事件总线、V2 扩展点机制） | 可用 Graal，零 MC / Loader import |
| `src` 共享树 | 需要触及 MC/loader、且差异**可守卫表达**的代码（wrapper、bindings、client…） | 单副本，守卫表达差异 |
| `versions/<node>` | **节点独有**（差异大到守卫不划算：整文件/整类不同） | 越少越好 |

一句话判据：**能放低层就放低层；守卫表达不了的下放节点目录**。数字阈值（守卫多少条算"不划算"）归 M2 / ADR-0008。

## 决策

1. **插件入口契约迁入 `common-api`**：`NekoJSPlugin`、`@RegisterNekoJSPlugin`、`NekoPluginExtensionProvider`、扩展点/句柄公共类型——纯 Java 接口，零依赖即满足 L1 边界；`common-api` 从"数据契约孵化层"升级为对外契约层；`common-api-processor` 的契约检查机制看住它们。
2. **新代码按同判据落位**：ADR-0004 平台适配层——NeoForge adapter（1.21.1 / 26.x 同 API）→ 共享树守卫内；Fabric adapter → `26.1.2-fabric` 节点目录（整文件差异）。
3. **client/GUI 高守卫区保留共享树**（RE1：client 包 258 条守卫为全树最密）——单副本价值 > 守卫成本，密度治理归 M2，不因密度下放。
4. **边界做编译期检查**：L1/L2 零 MC/loader import、wrapper 层零 loader import（ADR-0004 要求）——**落地于 `guardLint` 任务的 import 扫描**（同为已有机制、单一工具入口，与 ADR-0008 第 5 条一致；核心决策"不引入 ArchUnit 新依赖"不变）；是否 CI 强制归 G1 裁定（ADR-0009 已裁定挂 CI）。

## Consequences

- 现状 `common/src/main/java/com/tkisor/nekojs/api/` 下的插件入口类型迁移到 `common-api`（包名不变、模块归属变化），插件作者 import 路径不变。
- `forge/` 独立分支（B4 端口）并入根分支时按同判据落位。
- 边界检查的处理器扩展工作量列入 G1 编排。
