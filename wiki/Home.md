# 欢迎使用 NekoJS

> **现代、极速、优雅的 Minecraft 脚本魔改引擎**

NekoJS 是一个基于 **NeoForge** 和 **GraalVM/GraalJS** 构建的 Minecraft JavaScript 脚本运行时。它面向整合包作者和模组开发者，目标是在 Minecraft 中提供接近现代前端工程化的脚本开发体验。

本 Wiki 分为两部分：

- **[📒 用户文档](快速开始)** —— 写脚本的人看这里。从快速开始到完整的 API 参考。
- **[🛠 开发者文档](项目架构)** —— 给 NekoJS 写插件、扩展事件、贡献代码的人看这里。

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

→ 完整流程见 **[快速开始](快速开始)**。

---

## 我该看哪部分?

- **我想写脚本魔改整合包** → 从 [快速开始](快速开始) 开始，然后翻 [全局绑定](全局绑定) 和 [事件参考](事件参考)。
- **我想改配方** → [配方系统](配方系统)。
- **我想注册新物品/方块/实体** → [注册新内容](注册新内容)。
- **我想用 TS 或拆分多文件模块** → [TypeScript 与 JSX](TypeScript-与-JSX)、[模块系统](模块系统)。
- **我遇到报错了** → [常见问题](常见问题)。
- **我想给 NekoJS 写插件 mod** → [插件开发](插件开发)。
- **我想了解内部架构** → [项目架构](项目架构)。

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
