# 欢迎使用 NekoJS

> **现代、极速、优雅的 Minecraft 脚本魔改引擎**

NekoJS 是一个基于 **NeoForge** 和 **GraalVM/GraalJS** 构建的 Minecraft JavaScript 脚本运行时。它面向整合包作者和模组开发者，目标是在 Minecraft 中提供接近现代前端工程化的脚本开发体验。

本 Wiki 按读者角色组织：

- **脚本作者**：从 [快速开始](快速开始) 入门，继续阅读 [脚本基础](脚本基础)、[全局绑定](全局绑定) 和 [事件参考](事件参考)。
- **整合包作者**：重点阅读 [配方系统](配方系统)、[注册新内容](注册新内容)、[模块系统](模块系统) 和 [TypeScript 与 JSX](TypeScript-与-JSX)。
- **插件开发者**：从 [插件开发](插件开发) 开始，配合 [类型适配器](类型适配器)、[事件扩展](事件扩展) 和 [注解体系](注解体系)。
- **项目贡献者**：阅读 [项目架构](项目架构)、[Probe 类型生成](Probe-类型生成) 和 [构建系统](构建系统)。

---

## 为什么选 NekoJS?

| 特性 | 说明 |
|---|---|
| **GraalVM 强力驱动** | 拥抱最新 ECMAScript 标准，告别老旧的 Rhino/Nashorn，享受现代 JS 语法和 GraalJS 运行时能力。 |
| **TypeScript & JSX 本体支持** | 内置 `.ts` 可擦除 TypeScript 前端和轻量 `.jsx/.tsx` classic runtime lowering，无需额外编译步骤。 |
| **原生 ESM 运行时** | 支持 `import`/`export`、live binding、循环依赖、top-level await、`import.meta`、动态 `import()`、ESM/CJS 互操作。 |
| **Node.js 兼容 API** | 内置 `fs`、`path`、`buffer`、`process`、`timers`、`util`、`events`、`assert`、`os`、`test` 等核心模块 shim。 |
| **IDE 智能提示** | 启动后自动生成 `.d.ts` 类型声明（`.neko_probe/`），无需外部 ProbeJS mod 即可获得补全。 |
| **服务端热重载** | `/nekojs reload` 热重载服务端脚本；Cleanroom 1.12.2 还支持配方热重载。 |
| **多平台** | 同时支持 NeoForge 26.1 / 26.2 / 1.21.1 与 Cleanroom 1.12.2（Forge），共享通用基础设施。 |

---

## 三十秒上手

1. 安装前置 mod [Graal](https://www.curseforge.com/minecraft/mc-mods/graal) 和 NekoJS，启动游戏。
2. 在游戏根目录下会自动生成 `nekojs/` 文件夹。
3. 在 `nekojs/server_scripts/` 里新建 `hello.js`：

```javascript
// server_scripts/hello.js
ServerEvents.started(event => {
  console.info('NekoJS 已就绪！')
})
```

4. 进入世界，或执行 `/nekojs reload`，看到日志输出即可。

完整流程见 **[快速开始](快速开始)**。

---

## 我该看哪部分?

- **我想写脚本魔改整合包**：从 [快速开始](快速开始) 开始，然后翻 [全局绑定](全局绑定) 和 [事件参考](事件参考)。
- **我想改配方**：参见 [配方系统](配方系统)。
- **我想注册新物品/方块/实体**：参见 [注册新内容](注册新内容)。
- **我想用 TS 或拆分多文件模块**：参见 [TypeScript 与 JSX](TypeScript-与-JSX)、[模块系统](模块系统)。
- **我遇到报错了**：参见 [常见问题](常见问题)。
- **我想给 NekoJS 写插件 mod**：参见 [插件开发](插件开发)。
- **我想了解内部架构**：参见 [项目架构](项目架构)。

---

## 支持的 Minecraft 版本

| 平台 | MC 版本 | 加载器 | Java |
|---|---|---|---|
| NeoForge 26.1 | 26.1.2 | NeoForge 26.1.2-beta | 25 |
| NeoForge 26.2 | 26.2.0 | NeoForge 26.2.0-beta | 25 |
| NeoForge 1.21.1 | 1.21.1 | NeoForge 21.1.x | 21 |
| Cleanroom 1.12.2 | 1.12.2 | Cleanroom（Forge）0.5.14-alpha | 25 |

> 绝大多数脚本 API 跨平台一致；少数 1.12.2 因 API 差异略有不同，文中会标注。

---

## 额外资源

- `docs/ROADMAP.md` 是项目的设计路线图。
