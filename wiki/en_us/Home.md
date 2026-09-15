<!--
  Translated page. Keep the metadata block below up to date.
  See docs/translation-guide.md for conventions and the term glossary.
-->

> **English** · [中文](../Home)
>
> | | |
> |---|---|
> | Source page | [`wiki/Home.md`](../Home) |
> | Source version | commit `29541c2c`, 2026-09-12 |
> | Translation updated | 2026-09-12 |
>
> If the Chinese page has changed since that commit, the Chinese page is correct and this one may be out of date.
>
> Links below marked 中文 point to pages that have not been translated yet.

# Welcome to NekoJS

> **A modern, fast, and elegant Minecraft scripting engine**

NekoJS is a Minecraft JavaScript scripting runtime built on **NeoForge** and **GraalVM/GraalJS**. It is aimed at modpack authors and mod developers. The goal is to provide a scripting experience inside Minecraft that is close to modern frontend engineering practice.

This wiki is organised by reader role:

- **Script authors**: start with [Quick start 中文](../快速开始), then read [Script basics 中文](../脚本基础), [Global bindings 中文](../全局绑定), and [Event reference 中文](../事件参考).
- **Modpack authors**: focus on [Recipe system 中文](../配方系统), [Registering new content 中文](../注册新内容), [Module system 中文](../模块系统), and [TypeScript and JSX 中文](../TypeScript-与-JSX).
- **Plugin developers**: start with [Plugin development 中文](../插件开发), together with [Type adapters 中文](../类型适配器), [Event extensions 中文](../事件扩展), and [Annotations 中文](../注解体系).
- **Project contributors**: read [Project architecture 中文](../项目架构), [Probe type generation 中文](../Probe-类型生成), and [Build system 中文](../构建系统).

---

## Why choose NekoJS?

| Feature | Description |
|---|---|
| **Powered by GraalVM** | Supports the latest ECMAScript standard. It replaces the older Rhino and Nashorn engines, so you get modern JavaScript syntax and the capabilities of the GraalJS runtime. |
| **Built-in TypeScript and JSX support** | Includes an erasable TypeScript frontend for `.ts` and lightweight classic runtime lowering for `.jsx` and `.tsx`. No extra build step is required. |
| **Native ESM runtime** | Supports `import` and `export`, live bindings, circular dependencies, top-level await, `import.meta`, dynamic `import()`, and ESM/CJS interoperability. |
| **Node.js compatible API** | Includes shims for the core modules `fs`, `path`, `buffer`, `process`, `timers`, `util`, `events`, `assert`, `os`, and `test`. |
| **Editor code completion** | `/nekojs probe` generates TypeScript and Python type declarations into `.neko_probe/` with a single command. You do not need the external ProbeJS mod to get completion. |
| **Server-side hot reload** | `/nekojs reload` hot reloads server scripts. Both NeoForge and Cleanroom support recipe hot reload. For details, see [Commands 中文](../命令). |
| **Multi-platform** | Supports NeoForge 26.1, 26.2, and 1.21.1, as well as Cleanroom 1.12.2 (Forge). All platforms share common infrastructure. |

---

## Get started in thirty seconds

1. Install the required dependency mod [Graal](https://www.curseforge.com/minecraft/mc-mods/graal) and NekoJS, then start the game.
2. A `nekojs/` folder is created automatically in the game root directory.
3. Create `hello.js` inside `nekojs/server_scripts/`:

```javascript
// server_scripts/hello.js
ServerEvents.started(event => {
  console.info('NekoJS is ready!')
})
```

4. Enter a world, or run `/nekojs reload`. You are finished once the message appears in the log.

For the full walkthrough, see **[Quick start 中文](../快速开始)**.

---

## Which pages should I read?

- **I want to write scripts to modify a modpack**: start with [Quick start 中文](../快速开始), then read [Global bindings 中文](../全局绑定) and [Event reference 中文](../事件参考).
- **I want to change recipes**: see [Recipe system 中文](../配方系统).
- **I want to register new items, blocks, or entities**: see [Registering new content 中文](../注册新内容).
- **I want to use TypeScript or split my code into several module files**: see [TypeScript and JSX 中文](../TypeScript-与-JSX) and [Module system 中文](../模块系统).
- **I hit an error**: see [Frequently asked questions 中文](../常见问题).
- **I want to write a plugin mod for NekoJS**: see [Plugin development 中文](../插件开发).
- **I want to understand the internal architecture**: see [Project architecture 中文](../项目架构).

---

## Supported Minecraft versions

| Platform | Minecraft version | Loader | Java |
|---|---|---|---|
| NeoForge 26.1 | 26.1.2 | NeoForge 26.1.2-beta | 25 |
| NeoForge 26.2 | 26.2.0 | NeoForge 26.2.0-beta | 25 |
| NeoForge 1.21.1 | 1.21.1 | NeoForge 21.1.x | 21 |
| Cleanroom 1.12.2 | 1.12.2 | Cleanroom (Forge) 0.5.14-alpha | 25 |

> Almost all script APIs behave the same on every platform. A small number differ on 1.12.2 because of API differences, and those cases are marked in the text.

---

## Additional resources

- `docs/ROADMAP.md` is the design roadmap for the project.
