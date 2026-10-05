<!-- wiki-page: home; locale: cn -->

> **中文** · [English](home_us)

<a id="wiki-section-1"></a>
# NekoJS Wiki

NekoJS 是一个基于 GraalJS 的 Minecraft 脚本运行时。你可以用 JavaScript、TypeScript、JSX 或 NekoJS 支持的 Python 子集编写脚本，修改配方、监听事件、注册内容，并在游戏内获得类型提示。

本 Wiki 按使用场景组织：

- **脚本作者**：从 [快速开始](quick-start_cn) 开始，再看 [脚本基础](script-basics_cn)、[全局绑定](global-bindings_cn) 和 [事件参考](event-reference_cn)。
- **整合包作者**：重点看 [配方系统](recipe-system_cn)、[注册新内容](registering-new-content_cn)、[模块系统](module-system_cn) 和 [TypeScript 与 JSX](typescript-and-jsx_cn)。
- **插件开发者**：从 [插件开发](plugin-development_cn) 开始，配合 [类型适配器](type-adapters_cn)、[事件扩展](event-extensions_cn) 和 [注解体系](annotations_cn)。
- **项目贡献者**：阅读 [项目架构](project-architecture_cn)、[Probe 类型生成](probe-type-generation_cn) 和 [构建系统](build-system_cn)。

> **先看平台状态**：如果你使用 Fabric，请先阅读 [平台与兼容性](platform-compatibility_cn)。Fabric 已有 26.x 构建节点，但 API 仍是 NeoForge 的子集，不能默认认为所有事件和绑定都可用。

<a id="wiki-section-2"></a>
## 你能用 NekoJS 做什么

| 能力 | 说明 |
|---|---|
| JavaScript / TypeScript / JSX | `.js`、`.mjs`、`.cjs`、`.ts`、`.jsx`、`.tsx` 可直接放入脚本目录；`.py` 由内置 Python 转译器处理。 |
| JSX 客户端 Screen UI | 仅 NeoForge 26.x；使用 [JSX 客户端 UI](jsx-client-ui_cn) 指南。 |
| ESM 与 CommonJS | 支持 `import` / `export`、动态 `import()`、top-level await 和 `require()`。 |
| 配方与数据 | 用 `ServerEvents.recipes` 修改配方；NeoForge 还提供更完整的数据生成和资源生成事件。 |
| 类型提示 | `/nekojs probe` 默认生成 TypeScript 声明；`/nekojs probe python` 生成 Python stub；`/nekojs probe all` 运行全部 backend。 |
| 事务式 reload | SERVER、CLIENT、TEST 脚本加载失败时，旧环境通常会保留；启动脚本改动仍需重启游戏。 |
| Node.js 核心模块 | 提供受沙盒限制的 `fs`、`path`、`buffer`、`crypto`、`process`、`timers`、`util`、`events`、`assert`、`os`、`test` 等 shim。 |

<a id="wiki-section-3"></a>
## 三十秒上手

1. 安装与 Minecraft/加载器匹配的 NekoJS 和 [Graal](https://www.curseforge.com/minecraft/mc-mods/graal)。Graal 需要 25.1.3.7 或更高版本。
2. 启动游戏，等待根目录生成 `nekojs/` 工作区。
3. 在 `nekojs/server_scripts/hello.js` 写入：

```javascript
console.info('[NekoJS] Server script loaded')
```

4. 进入世界，或执行 `/nekojs reload`。看到日志输出后，继续阅读 [快速开始](quick-start_cn)。

<a id="wiki-section-4"></a>
## 当前平台

| 平台 | 状态 |
|---|---|
| NeoForge 1.21.1 | 当前支持，Java 21 |
| NeoForge 26.1.2 | 当前支持，Java 25 |
| NeoForge 26.2.0 | 当前支持，Java 25 |
| Fabric 26.1.2 | 已有构建和运行冒烟，暂未作为正式制品发布；API 为子集 |
| Fabric 26.2.0 | 已有构建和运行时隔离门禁，暂未作为正式制品发布；API 为子集 |
| Cleanroom 1.12.2 | 独立 legacy 分支，不在当前仓库版本图中 |

详细差异见 [平台与兼容性](platform-compatibility_cn)。发布状态以 [Releases](https://github.com/NekoJS-Dev/NekoJS/releases) 为准。

<a id="wiki-section-5"></a>
## 从哪里继续

- **第一次写脚本**： [快速开始](quick-start_cn)
- **想知道脚本何时运行**： [脚本基础](script-basics_cn)
- **想查绑定和方法**： [全局绑定](global-bindings_cn)
- **想监听或取消事件**： [事件参考](event-reference_cn)
- **想改配方**： [配方系统](recipe-system_cn)
- **想注册物品、方块或实体**： [注册新内容](registering-new-content_cn)
- **想用 JSX 写客户端 Screen**： [JSX 客户端 UI](jsx-client-ui_cn)
- **想拆分模块或使用 npm 包**： [模块系统](module-system_cn)
- **遇到错误**： [常见问题](faq_cn)
- **想写 Java 插件**： [插件开发](plugin-development_cn)

<a id="wiki-section-6"></a>
## 维护说明

Wiki 中仍有部分 Cleanroom legacy 资料和正在翻译的英文页面。涉及平台差异时，以 [平台与兼容性](platform-compatibility_cn)、对应参考页的平台标记和当前 Release 为准。

<!-- wiki-nav -->

---

[目录](Home) · [下一篇: 平台与兼容性](platform-compatibility_cn)
