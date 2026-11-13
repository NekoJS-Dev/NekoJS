<!-- wiki-page: home; locale: cn -->

> **中文** · [English](home_us)

<a id="wiki-section-1"></a>
# NekoJS Wiki

NekoJS 是一个 Minecraft 脚本模组：用 JavaScript（也可以用 TypeScript 或 Python）写脚本，就能修改配方、监听游戏事件、添加新物品和方块，不需要写 Java，也不需要重新编译模组。

如果你用过 KubeJS，会觉得很熟悉：脚本放在游戏目录里，改完执行一条命令就能生效。

<a id="wiki-section-2"></a>
## 我该从哪里开始？

| 你是… | 先读 | 然后 |
|---|---|---|
| 第一次接触 NekoJS | [快速开始](quick-start_cn) | [脚本基础](script-basics_cn) |
| 想改配方、加物品的整合包作者 | [配方系统](recipe-system_cn)、[注册新内容](registering-new-content_cn) | [事件参考](event-reference_cn)、[全局绑定](global-bindings_cn) |
| 想用 TypeScript 或拆分多个文件 | [TypeScript 与 JSX](typescript-and-jsx_cn) | [模块系统](module-system_cn) |
| 想用 Python 写脚本 | [Python 脚本](python-scripts_cn) | [Python 特性清单](python-feature-support_cn) |
| 想写 Java 插件扩展 NekoJS | [插件开发](plugin-development_cn) | [类型适配器](type-adapters_cn)、[事件扩展](event-extensions_cn) |
| 想参与 NekoJS 本身的开发 | [项目架构](project-architecture_cn) | [构建系统](build-system_cn) |

遇到问题先看 [常见问题](faq_cn)；看到 `NEKO-xxxx` 错误码就去 [错误与日志参考](error-reference_cn) 查。

<a id="wiki-section-3"></a>
## 一分钟体验

1. 把 NekoJS 和前置模组 [Graal](https://www.curseforge.com/minecraft/mc-mods/graal)（[按加载器选择所需版本](quick-start_cn#wiki-section-2)）放进 `mods/`，启动游戏。
2. 游戏目录下会出现 `nekojs/` 文件夹。新建 `nekojs/server_scripts/hello.js`：

```javascript
console.info('Hello, NekoJS!')
```

3. 进入世界，执行 `/nekojs reload`，然后打开 `logs/nekojs/server.log`，就能看到这行输出。

完整步骤（包括改配方、加物品、开启代码补全）见 [快速开始](quick-start_cn)。

<a id="wiki-section-4"></a>
## NekoJS 能做什么

| 想做的事 | 用什么 | 详细说明 |
|---|---|---|
| 添加、删除、修改配方 | `ServerEvents.recipes` | [配方系统](recipe-system_cn) |
| 添加新物品、方块、实体 | `RegistryEvents.register`（启动脚本） | [注册新内容](registering-new-content_cn) |
| 在玩家行为、方块破坏等时机执行代码 | `PlayerEvents`、`BlockEvents` 等事件 | [事件参考](event-reference_cn) |
| 添加自定义命令 | `CommandEvents.register` | [事件参考](event-reference_cn#wiki-command-events) |
| 在屏幕上画 HUD、做界面 | `ClientEvents`、JSX 界面 | [客户端绘制](client-rendering_cn)、[JSX 客户端 UI](jsx-client-ui_cn) |
| 在编辑器里获得代码补全 | `/nekojs probe` 生成类型声明 | [Probe 类型生成](probe-type-generation_cn) |
| 引用 npm 上的纯 JS 库 | `import` / `require` | [模块系统](module-system_cn) |

<a id="wiki-section-5"></a>
## 支持的版本

| 加载器 | Minecraft | Java |
|---|---|---|
| NeoForge | 1.21.1 | 21 |
| NeoForge | 26.1.2、26.2.0 | 25 |
| Fabric | 26.1.2、26.2 | 25 |

Fabric 版本能运行脚本、改配方、用大部分常用事件，但还缺少一些 NeoForge 上才有的功能。用 Fabric 前请看 [平台与兼容性](platform-compatibility_cn)。可下载的版本以 [Releases](https://github.com/NekoJS-Dev/NekoJS/releases) 为准。

<!-- wiki-nav -->

---

[目录](Home) · [下一篇: 平台与兼容性](platform-compatibility_cn)
