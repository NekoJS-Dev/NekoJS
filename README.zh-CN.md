<p align="center">
  <img src="icon.png" width="180" height="180" alt="NekoJS 图标">
  <br>
  <strong>告别繁琐的 Java 模组项目，用现代 JavaScript / TypeScript 轻松魔改 Minecraft</strong>
</p>

<div align="center">

[![License: LGPL-3.0](https://img.shields.io/badge/license-LGPL--3.0-blue.svg)](LICENSE)
[![CI](https://github.com/NekoJS-Dev/NekoJS/actions/workflows/ci-build.yml/badge.svg)](https://github.com/NekoJS-Dev/NekoJS/actions/workflows/ci-build.yml)
[![Release](https://img.shields.io/github/v/release/NekoJS-Dev/NekoJS?include_prereleases&label=release)](https://github.com/NekoJS-Dev/NekoJS/releases)
[![Graal](https://img.shields.io/badge/Graal-required-orange.svg)](https://www.curseforge.com/minecraft/mc-mods/graal)
[![QQ 群](https://img.shields.io/badge/QQ%E7%BE%A4-1158525822-brightgreen.svg)](https://qm.qq.com/q/rbryak0K6k)

[文档](wiki/home_cn.md) · [快速开始](wiki/quick-start_cn.md) · [下载](https://github.com/NekoJS-Dev/NekoJS/releases) · [问题反馈](https://github.com/NekoJS-Dev/NekoJS/issues) · [English docs](wiki/home_us.md)

[English](README.md) | 简体中文

</div>

---

## 为什么选 NekoJS

如果你用过 KubeJS 或 CraftTweaker，下面这些可能是你想要的：

- **现代 JavaScript：** 运行在 GraalJS 上，支持最新的 ECMAScript 语法、`import` / `export`、顶层 `await` 和动态 `import()`。
- **TypeScript 直接运行：** `.ts`、`.tsx` 放进脚本目录就能跑，不用自己编译打包。
- **自带代码补全：** 执行 `/nekojs probe`，NekoJS 会按你当前装的模组生成类型声明，VS Code 里能直接看到有哪些事件、方法和物品 ID。不需要另装 ProbeJS 之类的模组。
- **能用 npm 包：** 纯 JavaScript 的 npm 包放进 `nekojs/node_modules` 就能 `import`，常用的 Node.js 内置模块（`fs`、`path`、`events` 等）也可以用。

## 能做什么

| 想做的事 | 写在哪里 | 文档 |
|---|---|---|
| 添加、删除、替换配方 | `server_scripts` | [配方系统](wiki/recipe-system_cn.md) |
| 添加物品、方块、流体、实体、创造标签页 | `startup_scripts` | [注册新内容](wiki/registering-new-content_cn.md) |
| 在玩家登录、方块破坏、实体受伤等时机运行代码 | `server_scripts` | [事件参考](wiki/event-reference_cn.md) |
| 添加自定义命令 | `server_scripts` | [事件参考](wiki/event-reference_cn.md) |
| 画 HUD、做界面（JSX） | `client_scripts` | [客户端绘制](wiki/client-rendering_cn.md)、[JSX 界面](wiki/jsx-client-ui_cn.md) |
| 修改 JEI 里显示的物品和说明 | `client_scripts` | [事件参考](wiki/event-reference_cn.md) |
| 保存数据、同步给客户端 | 任意 | [数据与状态](wiki/data-and-state_cn.md) |
| 调用 Java 类和其他模组的 API | 任意 | [模块系统](wiki/module-system_cn.md) |

## 支持的版本

| 加载器 | Minecraft | Java | 状态 |
|---|---|---|---|
| NeoForge | 1.21.1 | 21 | 支持 |
| NeoForge | 26.1.2、26.2.0 | 25 | 支持，功能最全 |
| Fabric | 26.1.2、26.2 | 25 | 支持，部分功能还没移植 |

所有版本都需要前置模组 [Graal](https://www.curseforge.com/minecraft/mc-mods/graal)：**Fabric 和 NeoForge 均要求 GraalMC 25.5.0 或更高**，请使用[对应加载器的下载](wiki/quick-start_cn.md#wiki-section-2)。各平台的具体差异见 [平台与兼容性](wiki/platform-compatibility_cn.md)。

## 快速开始

**1. 安装。** 从 [Releases](https://github.com/NekoJS-Dev/NekoJS/releases) 下载和你的 Minecraft 版本、加载器对应的 NekoJS，连同 Graal 一起放进 `mods/`，启动游戏。

> 同一 Minecraft 版本和加载器的客户端、服务端使用同一个 NekoJS jar，不再区分 `-server` 产物。NekoJS 不内置 ICU4J；两个加载器的 GraalMC 25.5.0 均包含其使用的重定位 ICU。

**2. 找到脚本目录。** 第一次启动后，游戏目录下会出现 `nekojs/`：

```text
nekojs/
├── startup_scripts/   注册物品、方块等。改了要重启游戏
├── server_scripts/    配方、事件、命令。改完执行 /nekojs reload
├── client_scripts/    HUD、按键、界面。改完执行 /nekojs reload client
├── test_scripts/      测试。执行 /nekojs test 时才运行
├── assets/  data/     资源包和数据包内容
└── config/            配置文件
```

**3. 写一个脚本。** 新建 `nekojs/server_scripts/welcome.js`：

```javascript
PlayerEvents.loggedIn(event => {
  // NeoForge 上玩家是 event.entity，Fabric 上是 event.player
  const player = event.entity ?? event.player
  player.displayClientMessage(Text.of('欢迎来到服务器！').gold(), false)
})
```

执行 `/nekojs reload`，重新进入世界就能看到欢迎消息。

**4. 打开代码补全。** 在游戏里执行 `/nekojs probe`，然后用 VS Code 打开 `nekojs/server_scripts` 文件夹。

完整教程见 [快速开始](wiki/quick-start_cn.md)。遇到问题先看 [常见问题](wiki/faq_cn.md)。

## 示例

<details>
<summary><b>注册一个物品和创造标签页</b></summary>

```javascript
// nekojs/startup_scripts/items.js
RegistryEvents.register(event => {
  event.item('mymod:cool_gem', item => {
    item.maxStackSize = 16
    item.rarity = 'rare'
  })

  event.creativeModeTab('mymod:main', tab => {
    tab.title = '我的整合包'
    tab.icon = 'mymod:cool_gem'
    tab.add('mymod:cool_gem')
  })
})
```

重启游戏后生效。材质放在 `nekojs/assets/mymod/textures/item/cool_gem.png`。

</details>

<details>
<summary><b>用 TypeScript 拆分模块</b></summary>

脚本目录里的每个文件都会被加载，所以 `lib/` 里只放导出的函数和常量。


```typescript
// nekojs/server_scripts/lib/ores.ts
export const ORES: string[] = ['minecraft:iron_ore', 'minecraft:gold_ore']

// nekojs/server_scripts/recipes.ts
import { ORES } from './lib/ores.ts'

ServerEvents.recipes(event => {
  for (const ore of ORES) {
    event.remove({ input: ore, type: 'minecraft:smelting' })
  }
})
```

</details>

<details>
<summary><b>调用 Java 类</b></summary>

```javascript
import { ArrayList } from 'java:java/util'

const list = new ArrayList()
list.add('hello')
console.info(list.size())
```

部分高风险的 Java 类默认禁止访问，见 [模块系统](wiki/module-system_cn.md)。

</details>

<details>
<summary><b>用 Python 写配方</b></summary>

```python
# nekojs/server_scripts/recipes.py
from nekojs import *

def on_recipes(event):
    event.remove({'output': 'minecraft:stick'})

ServerEvents.recipes(on_recipes)
```

`from nekojs import *` 只是给编辑器用的，执行 `/nekojs probe python` 后就有补全。见 [Python 脚本](wiki/python-scripts_cn.md)。

</details>

<details>
<summary><b>修改 JEI 显示的内容</b></summary>

```javascript
// nekojs/client_scripts/jei.js（需要安装 JEI，仅 NeoForge）
RecipeViewerEvents.removeEntries('item', event => {
  event.add('minecraft:debug_stick')
})

RecipeViewerEvents.addInformation(event => {
  event.add('minecraft:echo_shard', '可以用来合成追溯指针。')
})
```

</details>

## 安全须知

脚本的权限很大，能访问游戏的大部分内部功能。**只运行你信任的人写的脚本。**

NekoJS 默认会限制一些危险操作：脚本只能读游戏目录里的文件，只能在 `nekojs/` 里写文件，不能创建线程、使用反射或访问网络、进程等高风险的 Java 类。但这些限制不能保证挡住恶意脚本。另外，NekoJS 不会强行中断死循环，写错的循环会让游戏卡住。

## 从源码构建

需要 JDK 17 或更高（推荐 25）。各 Minecraft 版本需要的 JDK 由 Gradle 自动下载。

```bash
git clone https://github.com/NekoJS-Dev/NekoJS.git
cd NekoJS
./gradlew :26.1.2:build        # 构建一个版本
./gradlew sandboxCheck         # 提交前的完整检查
```

构建结果在 `versions/<版本>/build/libs/`。项目结构和其他命令见 [构建系统](wiki/build-system_cn.md) 和 [项目架构](wiki/project-architecture_cn.md)。

## 参与贡献

欢迎提交 Issue 和 Pull Request。

- 报告问题时，请附上 `logs/nekojs/` 里的日志、出问题的脚本，以及 Minecraft、加载器和 NekoJS 的版本。
- 提交 PR 前请看 [PR 检查清单](.github/PULL_REQUEST_TEMPLATE.md) 和 [代码约定](docs/agents/coding.md)。
- 交流群：QQ **1158525822**（[点击加入](https://qm.qq.com/q/rbryak0K6k)）

## 许可证

[LGPL-3.0](LICENSE) © Tki_sor
