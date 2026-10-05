<!-- wiki-page: platform-compatibility; locale: cn -->

> **中文** · [English](platform-compatibility_us)

<a id="wiki-section-1"></a>
# 平台与兼容性

NekoJS 当前代码库包含 NeoForge 和 Fabric 两组节点。本文是平台状态的总览；具体 API 仍以各参考页中的“平台”说明为准。

<a id="wiki-section-2"></a>
## 当前节点

| 平台 | Minecraft | 当前状态 | Java | 说明 |
|---|---|---|---|---|
| NeoForge | 1.21.1 | 支持 | 21 | 稳定维护的旧版本节点 |
| NeoForge | 26.1.2 | 支持 | 25 | 主力版本节点 |
| NeoForge | 26.2.0 | 支持 | 25 | 主力版本节点 |
| Fabric | 26.1.2 | 已有构建节点，暂未作为正式制品发布 | 25 | 已通过构建和运行冒烟，API 仍是子集 |
| Fabric | 26.2.0 | 已有构建节点，暂未作为正式制品发布 | 25 | 已有构建和运行时隔离门禁，API 仍是子集 |

Fabric 节点已经覆盖脚本加载、配方、网络、probe、注册表的一部分、常用事件和按键绑定，但不等于与 NeoForge 功能完全相同。发布状态以 [Releases](https://github.com/NekoJS-Dev/NekoJS/releases) 为准。

<a id="wiki-section-3"></a>
## 主要功能差异

| 功能 | NeoForge 1.21.1 / 26.x | Fabric 26.x |
|---|---|---|
| JS/TS/JSX/Python 脚本 | 支持 | 支持 |
| ESM、CommonJS、Node 核心模块 | 支持 | 支持 |
| 配方脚本与 `reload server` | 支持 | 支持；配方 API 仍以当前实现为准 |
| `/nekojs probe` 与 TypeScript/Python 声明 | 支持 | 支持 |
| 网络通道 `Network` / `NetworkEvents` | 支持 | 支持 |
| 基础注册 builder | 支持 | 部分支持 |
| JSX 客户端 Screen UI | NeoForge 26.x | 暂未移植 |
| `ClientEvents` HUD/Screen Painter | 支持 | 支持 `hud` 与 `screenRender`；`hudRender` / `worldRender` 注册式渲染仍未移植 |
| `ServerEvents.tags` | 支持 | 暂未移植 |
| `ServerEvents.generateData` | 支持 | 暂未移植 |
| `ClientEvents.generateAssets` / `lang` | 支持 | 暂未移植 |
| `Fluid`、`FluidIngredient`、`FluidBuilder` | 支持 | 暂未移植 |
| `Capabilities` / `CapabilityEvents` | 普通方块、方块实体、实体、物品的标准与第三方原生 capability；保留各 scope 的 context | 已注册真实 Lookup 接入；物品/流体用 Fabric Transfer，能量用 NekoJS 自有接口，并支持自定义 typed Lookup |
| `VillagerTrades.add` | 支持 | 暂不可用；当前会以 unavailable 原因拒绝 |
| `PostEffects` | 支持 | 暂未移植 |
| JEI 配方查看器事件 | 支持（需要 JEI） | 暂未移植 |
| 26.x 附魔注册 builder | 不可用 | 不可用 |

26.x 的附魔是数据驱动注册表，不能沿用 1.21.1 的运行时注册 builder；请使用数据包方案。源码中保留的 builder 不代表 26.x 注册通道可用。

<a id="wiki-section-3-issue4"></a>
## Issue #4 已实现能力

以下能力已在 NeoForge 1.21.1、NeoForge 26.x 和 Fabric 26.x 的对应实现中补齐；Fabric 仍只承诺本文和参考页明确列出的子集：

| 能力 | 运行时契约 | 详细参考 |
|---|---|---|
| Painter | `ClientEvents.hud` 与 `ClientEvents.screenRender` 提供当帧 `PainterJS`；支持文本、渐变、裁剪、变换、物品、纹理和明确的 UV/尺寸参数 | [事件参考](event-reference_cn#wiki-section-25) |
| Entity / Goal | 注册实体默认有可见 humanoid renderer；支持原生实体类、属性基线、Goal 配置和原生 Goal class/factory | [注册新内容](registering-new-content_cn#wiki-section-7)、[事件参考](event-reference_cn#wiki-section-14) |
| Persistent Data | 服务端实体/玩家 PData 自动 dirty flush、登录/追踪快照、重生/克隆复制；客户端镜像只读；ItemStack 使用原版 `CUSTOM_DATA` 组件 | [全局绑定](global-bindings_cn#wiki-section-4) |
| Capability | NeoForge 使用原生 capability 与事务 handler；Fabric 使用真实 Transfer/Lookup；provider 生命周期是 STARTUP，一次提交，owner 自己负责保存和同步 | [全局绑定](global-bindings_cn#wiki-section-26)、[事件参考](event-reference_cn#wiki-section-15) |

Painter 和客户端 PData mirror 只在对应客户端回调/连接生命周期内有效。Capability provider 不会自动替 owner 保存任意存储；Fabric 的能量接口也不是跨模组标准能量协议。

实体类型 builder 在上述 NeoForge/Fabric 节点使用可见的人形 renderer，默认匹配 64×64 原版僵尸纹理；`renderer`、`texture` 与 `shadowRadius` 可配置。原生实体构造和自定义 Goal factory 必须是独立于 guest Context 的 Java 实现；内置 Goal 配置仍可通过脚本回调使用。

Capability 的同名脚本入口不代表原生类型完全一致：NeoForge 26.x 工厂使用原生事务存储，1.21.1 使用带 `simulate` 的旧接口；Fabric 使用 Transfer 事务。Fabric 没有标准跨模组能量 API，NekoJS 的 `FabricEnergyHandler` 不能自动访问其他能量模组。`fluidTank` 输入容量统一为 mB，但 Fabric 返回对象的数量与容量采用 droplets（1000 mB = 81000 droplets）。存储由具体 owner 保留、持久化与同步，注册 provider 本身不会自动保存数据；详见 [全局绑定](global-bindings_cn#wiki-section-26) 与 [事件参考](event-reference_cn#wiki-section-15)。

<a id="wiki-section-4"></a>
## 命令差异

以下命令在 Fabric 上存在，但部分能力是文本版或子集：

- `/nekojs reload <type> <file>`：Fabric 支持。
- `/nekojs view_all_errors`：Fabric 输出聊天文本；NeoForge 可打开错误 UI。
- `/nekojs probe reset_config`：Fabric 支持。
- `/nekojs editor`：已移除；脚本编辑请使用外部编辑器。
- `/nekojs registry`：Fabric 命令存在，但动态注册快照当前为空；动态注册能力仍不是 Fabric 支持面。
- `/nekojs reload startup`：当前所有平台都拒绝从运行中命令重载 STARTUP。启动脚本改动需要重启游戏。

<a id="wiki-section-5"></a>
## Cleanroom legacy

Cleanroom 1.12.2 不在当前代码库的版本图中，由独立 legacy 分支维护。本文和当前仓库无法验证该分支的实现；Wiki 中保留的 Cleanroom 段落应视为 legacy 资料，不应与当前 NeoForge/Fabric 支持矩阵混读。

<a id="wiki-section-6"></a>
## 如何判断能力

- 需要跨平台脚本时，优先使用本文标为支持的公共 API。
- 使用参考页中的平台标记；没有标记的新增 API 不应默认视为 Fabric 可用。
- 脚本启动后遇到绑定或事件不存在，先运行 `/nekojs probe`，再查看 `/nekojs error` 和平台专属说明。
- 贡献代码时，Fabric 的详细缺口和批次进度见仓库 `docs/fabric-port-status.md`。

<!-- wiki-nav -->

---

[上一篇: 首页](home_cn) · [目录](Home) · [下一篇: 快速开始](quick-start_cn)
