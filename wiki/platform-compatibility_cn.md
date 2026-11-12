<!-- wiki-page: platform-compatibility; locale: cn -->

> **中文** · [English](platform-compatibility_us)

<a id="wiki-section-1"></a>
# 平台与兼容性

NekoJS 支持 NeoForge 和 Fabric 两种加载器。NeoForge 版本功能最完整；Fabric 版本能完成大部分常见工作，但还有一些功能没有移植。

<a id="wiki-section-2"></a>
## 支持的版本

| 加载器 | Minecraft | Java | 说明 |
|---|---|---|---|
| NeoForge | 1.21.1 | 21 | 完整支持 |
| NeoForge | 26.1.2 | 25 | 完整支持，主力版本 |
| NeoForge | 26.2.0 | 25 | 完整支持，主力版本 |
| Fabric | 26.1.2 | 25 | 部分功能缺失，见下表 |
| Fabric | 26.2 | 25 | 部分功能缺失，见下表 |

所有版本都需要安装对应版本的 [Graal](https://www.curseforge.com/minecraft/mc-mods/graal) 前置模组，见 [快速开始](quick-start_cn)。可下载的版本以 [Releases](https://github.com/NekoJS-Dev/NekoJS/releases) 为准。

<a id="wiki-section-3"></a>
## 功能对照

下表只列出各平台有差异或需要特别说明的功能。没有列出的常用功能（脚本加载、reload、模块、Node.js 内置模块、代码补全、网络通信等）在所有版本上都可用。

| 功能 | NeoForge | Fabric |
|---|---|---|
| 修改配方（`ServerEvents.recipes`） | ✅ | ✅ |
| 注册物品、方块、实体 | ✅ | ⚠️ 部分支持 |
| 修改标签（`ServerEvents.tags`） | ✅ | ❌ |
| 修改已有物品、方块属性（`ItemEvents.modification`、`BlockEvents.modification`） | ✅ | ✅ |
| 用脚本生成数据包、资源包（`generateData`、`generateAssets`、`lang`） | ✅ | ❌ |
| 流体相关（`Fluid`、`FluidIngredient`） | ✅ | ❌ |
| HUD 绘制（`ClientEvents.hud`、`screenRender`） | ✅ | ✅ |
| 世界内绘制（`hudRender`、`worldRender`） | ✅ | ❌ |
| 后处理着色器（`PostEffects`） | ✅ | ❌ |
| JSX 界面（`ClientUI`） | ✅ 仅 26.x | ❌ |
| 村民交易（`tradeDeclaration`、`VillagerTrades`） | ✅ | ❌ |
| JEI 集成事件 | ✅（需装 JEI） | ❌ |
| 查询注册表（`Registry`） | ✅ | ❌ 查询结果为空 |
| 能力系统（`Capabilities`） | ✅ | ✅ 实现方式不同，见下文 |
| 错误面板（`/nekojs view_all_errors`） | ✅ 图形界面 | ⚠️ 聊天文本 |
| 动态注册（`/nekojs registry`） | ✅ 仅 26.x，实验性 | ❌ |

<a id="wiki-section-4"></a>
## 需要注意的差异

<a id="wiki-section-5"></a>
### 附魔

在 Minecraft 26.x 上，附魔改成了由数据包定义，不能再用脚本注册。请用数据包添加附魔（文件放在 `nekojs/data/` 下）。NeoForge 1.21.1 仍可以用脚本注册附魔。

<a id="wiki-section-6"></a>
### 能力系统（Capabilities）

脚本里的写法在两个平台上一样，但底层不同：

- NeoForge 使用原生的能力系统，能和其他模组的物品、流体、能量存储互通。
- Fabric 的物品和流体使用 Fabric Transfer API，可以和其他 Fabric 模组互通。**能量**使用 NekoJS 自己的接口，因为 Fabric 没有统一的能量标准，所以无法直接和其他模组的能量系统互通。
- Fabric 上流体数量的单位是 droplet（1000 mB = 81000 droplets）。创建储罐时输入的容量仍然用 mB。

<a id="wiki-section-7"></a>
### 物品拾取事件

`ItemEvents.canPickUp` 在两个平台上的阻止方式不同：Fabric 上回调 `return true` 即可阻止拾取；NeoForge 上 `return true` 没有效果，要调用 `event.setCanPickup(TriState.FALSE)`。详见 [事件参考](event-reference_cn#wiki-item-events)。

<a id="wiki-section-8"></a>
### 原版 /reload 和 F3+T

在 NeoForge 上，原版 `/reload` 会顺带重载服务端脚本，F3+T 会顺带重载客户端脚本。Fabric 上不会，请直接用 `/nekojs reload` 和 `/nekojs reload client`。

<a id="wiki-section-9"></a>
### 其他

- NekuJS（KubeJS 兼容插件）是单独安装的扩展，只支持 NeoForge 1.21.1。
- [MixinJS](mixinjs_cn) 是实验功能，默认关闭，使用前请阅读它的页面。

<a id="wiki-section-10"></a>
## 怎么判断一个功能能不能用

1. 看 [事件参考](event-reference_cn) 和 [全局绑定](global-bindings_cn) 里的平台标注。
2. 在当前游戏里执行 `/nekojs probe`，补全里能看到的才是当前环境实际可用的。
3. 写跨平台脚本时，只依赖本页标为两边都支持的功能。

> 关于 Cleanroom 1.12.2：它由另一个独立分支维护，本 Wiki 的内容不适用于它。

<a id="wiki-section-11"></a>
## 给贡献者

Fabric 移植的详细进度和缺口记录在仓库的 `docs/fabric-port-status.md`。

<!-- wiki-nav -->

---

[上一篇: 首页](home_cn) · [目录](Home) · [下一篇: 快速开始](quick-start_cn)
