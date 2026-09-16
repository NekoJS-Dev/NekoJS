# NekoJS

<p align="center">
  <img src="icon.png" width="180" height="180" alt="NekoJS 图标">
  <br>
  <strong>告别繁琐的 Java 模组项目，用现代 JavaScript / TypeScript 轻松魔改 Minecraft</strong>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-LGPL--3.0-blue.svg" alt="License"></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/graal"><img src="https://img.shields.io/badge/Graal-%E2%89%A525.1.3.7-orange.svg" alt="Graal Requirement"></a>
  <a href="https://qm.qq.com/q/rbryak0K6k"><img src="https://img.shields.io/badge/QQ%E7%BE%A4-1158525822-brightgreen.svg" alt="QQ Group"></a>
</p>

---

## 什么是 NekoJS？

**NekoJS** 是一款基于 GraalJS 的高性能 Minecraft 脚本与魔改模组。

无论是整合包作者还是模组开发者，都可以通过编写脚本轻松实现**修改配方、监听与处理事件、注册新物品/方块、定制 GUI 界面**等功能。

如果你曾使用过 KubeJS 或 CraftTweaker，NekoJS 的目标是带来真正**现代化、原生前端水准**的开发体验：
- **真正的现代语法**：全面支持 ES2024、原生 ESM 模块（`import` / `export`）、Top-level await。
- **TypeScript 直接运行**：`.ts` 文件无需手动编译或打包，丢进目录就能跑。
- **开箱即用的类型补全**：无需额外安装任何像 ProbeJS 这样的外部模组，首次启动即自动生成完整的 `.d.ts` 类型声明文件，享受极致的 IDE 代码提示。
- **丝滑的热重载**：在游戏内输入 `/nekojs reload` 即可即时生效服务端与配方逻辑，告别频繁重启游戏的痛苦。

---

## 运行前置与支持版本

1. **必要前置**：必须安装 **[Graal](https://www.curseforge.com/minecraft/mc-mods/graal) 25.1.3.7 或更高版本**（低版本缺少正则引擎，会导致脚本在处理正则表达式时报错）。
2. **支持平台**：
   - **NeoForge**：原生支持 1.21.1 及 26.x（具体兼容版本请参见 Release 发布页面）。
   - **Fabric**：多版本移植正在稳步推进中。

---

## 快速上手

### 1. 安装模组
将 `NekoJS` 以及前置 `Graal` 模组放入游戏 `.minecraft/mods` 目录，启动一次游戏。

### 2. 脚本目录结构
首次启动后，游戏根目录会自动生成 `nekojs` 工作区：

```text
.minecraft/
├── .neko_probe/            # 【自动生成】所有全局变量与事件的类型定义
└── nekojs/
    ├── startup_scripts/    # 启动脚本：注册新物品、方块、流体等（修改后需重启游戏）
    ├── server_scripts/     # 服务端脚本：配方修改、玩家交互、逻辑监听（支持 /nekojs reload 热重载）
    ├── client_scripts/     # 客户端脚本：按键绑定、自定义 GUI、粒子效果
    ├── test_scripts/       # 测试脚本：仅在执行 /nekojs test 命令时运行
    ├── node_modules/       # 纯 JS 的 npm 第三方依赖包
    ├── assets/             # 虚拟资源包目录（可在脚本中动态生成材质、语言文件等）
    ├── data/               # 虚拟数据包目录（可在脚本中动态生成战利品表、进度等）
    └── config/             # 配置文件（如 probe.toml、engine.toml）
```

> **提示**：推荐使用 **VS Code** 直接打开游戏根目录。启动时自动生成的 `tsconfig.json` 会帮你把编辑器和 `.neko_probe/` 关联起来，打开脚本立刻拥有类型推导与自动补全。

### 3. 写下你的第一个脚本

在 `nekojs/server_scripts/` 目录下创建一个 `demo.ts`（或 `demo.js`）：

```typescript
// 1. 监听玩家登录事件
PlayerEvents.loggedIn(event => {
    const { player } = event;
    player.tell(`欢迎来到服务器，${player.name}！祝你玩得愉快！`);
});

// 2. 移除原版某些不合理的配方，并添加自定义配方
ServerEvents.recipes(event => {
    // 移除木剑配方
    event.remove({ output: 'minecraft:wooden_sword' });

    // 添加一个无序合成：9个泥土合成1个钻石（整蛊/测试）
    event.shapeless('minecraft:diamond', [
        '9x minecraft:dirt'
    ]);
});

// 3. 监听每 Tick 运行的逻辑
ServerEvents.tickPre(event => {
    // 处理你的服务端周期逻辑
});
```

保存文件后，在游戏内输入命令：
```text
/nekojs reload
```
配方和事件监听便已瞬间更新完毕，无需退出存档！

---

## 核心亮点与特性

### 1. 现代前端开发体验
- **原生 ESM 支持**：告别魔改脚本里的简陋加载方式，直接使用标准 `import` 与 `export` 拆分模块，支持模块循环依赖与动态 `import()`。
- **TypeScript 直接跑**：支持接口（`interface`）、类型别名（`type`）、泛型、枚举（`enum`）、命名空间（`namespace`）等类型标注，运行时自动擦除，无需额外搭建构建打包工作流。
- **JSX / TSX 支持**：可以直接用 JSX 语法构建 UI 组件。
- **Python 语法支持**：如果你更熟悉 Python，在脚本目录直接写 `.py` 同样可以直接运行！
- **Node.js 核心能力垫片**：内置支持 `fs`、`path`、`buffer`、`process`、`timers`、`util`、`events`、`assert`、`os` 等常用 Node.js 模块。
- **引入 npm 包**：可直接将纯 JS 的 npm 模块放进 `nekojs/node_modules` 中进行引用。

### 2. 告别“猜 API”：极致的类型检查与代码诊断
- **内置类型探测（Probe）**：无需额外安装模组，NekoJS 启动时会自动扫描游戏运行时，生成精确的 TypeScript `.d.ts` 与 Python `.pyi` 声明文件。
- **防手滑拼写纠错**：写错方法名（比如把 `Utils.randomInt` 误写成 `Utils.randmInt`）？加载期会立即弹出友好提示：*“Did you mean 'randomInt'?”*。
- **错误集中面板**：脚本编写有误时不会无声崩溃，游戏内可通过 `/nekojs view_all_errors` 呼出面板统一查看。

### 3. 全面的事件覆盖
NekoJS 提供了涵盖游戏全生命周期的事件总线（详细类型签名请参考 IDE 补全）：

| 事件分类 | 包含常用事件示例 | 触发场景说明 |
| :--- | :--- | :--- |
| **`ServerEvents`** | `recipes`, `tags`, `tickPre`, `tickPost` | 服务器运行、数据加载、配方修改 |
| **`PlayerEvents`** | `loggedIn`, `chat`, `respawned`, `advancement` | 玩家进出、聊天发言、获得成就等 |
| **`EntityEvents`** | `damagePre`, `damagePost`, `death` | 实体受伤预检、结算、死亡处理 |
| **`BlockEvents`** | `broken`, `placed`, `rightClicked` | 方块破坏、放置与交互 |
| **`ItemEvents`** | `rightClicked`, `tooltip`, `crafted` | 物品使用、悬浮提示信息定制 |
| **`RegistryEvents`** | `item`, `block`, `fluid`, `creativeModeTab` | 自定义内容注册（在 `startup_scripts` 中使用） |
| **`ClientEvents`** | `generateAssets`, `lang` | 客户端资源生成与语言包拓展 |

> **支持自定义事件**：可以通过 `ScriptEvents` 自定义你自己的跨脚本、跨端自定义事件系统。

### 4. 轻松注册新物品、方块、流体与创造标签页
在 `startup_scripts/` 中，几行脚本即可添加属于你整合包的专属内容：

```javascript
// 注册自定义流体
RegistryEvents.fluid(event => {
    event.create('nekojs:molten_iron')
        .displayName('熔融铁')
        .density(2000)
        .temperature(1500);
});

// 注册属于你的创造模式物品栏分组
RegistryEvents.creativeModeTab(event => {
    event.create('nekojs:custom_tab')
        .title('我的魔法整合包')
        .icon('minecraft:enchanted_golden_apple')
        .add('minecraft:diamond')
        .add('minecraft:nether_star');
});
```

### 5. 虚拟资源包与数据包生成
告别手动写繁琐的数据包 JSON！通过脚本即可在内存中动态生成：

```javascript
// 动态生成数据包 JSON（如战利品表、配方）
ServerEvents.generateData('after_mods', event => {
    event.json('minecraft:loot_tables/blocks/stone.json', {
        type: 'minecraft:block',
        pools: []
    });
});

// 动态追加本地化文本
ClientEvents.lang('zh_cn', event => {
    event.add('item.nekojs.my_item', '超级猫猫之星');
});
```

### 6. 优雅与 Java 类交互
如果你需要直接调用底层 Java 类或其它模组的公开 API，NekoJS 提供了简洁的 `java:` 模块化导入语法：

```typescript
// 按包导入（懒加载代理）
import { Integer, Math as JavaMath } from 'java:java/lang';

// 直接导入具体的类
import LevelClass from 'java:net/minecraft/world/level/Level';

// CommonJS require 写法同样受支持
const { UUID } = require('java:java/util');
```

### 7. JEI 物品管理器联动
内置对 JEI (Just Enough Items) 的友好集成，支持隐藏配方、屏蔽垃圾物品、补充信息提示等：

```javascript
// 从 JEI 中隐藏某些不可获取的测试物品
RecipeViewerEvents.removeEntries('item', event => {
    event.add('minecraft:barrier');
});

// 为指定物品添加 JEI 说明面板
RecipeViewerEvents.addInformation(event => {
    event.add('minecraft:echo_shard', '§b潜声碎片可以用来制作定位指南针。');
});
```

---

## 安全性与运行须知

NekoJS 的脚本运行在受控的沙盒环境中：
- **文件隔离**：文件操作限定在当前游戏目录内，防止目录逃逸。
- **类访问黑名单**：默认禁止脚本调用线程创建、反射、底层网络与进程管理等高危 Java 接口。
- **防卡死与死循环熔断**：内置语句执行步数限制与求值超时熔断机制，防止死循环卡死服务器主线程。

⚠️ **重要提醒**：由于 Java 运行时对象图的复杂性，**请勿在服务器或整合包中加载来历不明的脚本文件**。请始终确保运行的脚本来自受信任的来源。

---

## 插件开发（面向 Java 模组作者）

如果你是模组作者，希望通过 Java 为 NekoJS 扩展原生功能、绑定 API 或拓展事件，只需实现 `NekoJSPlugin` 接口即可：

```java
@RegisterNekoJSPlugin(priority = 1000)
public final class MyIntegrationPlugin implements NekoJSPlugin {
    @Override
    public void registerBindings(BindingRegistry registry) {
        // 向 JS 脚本中暴露全局对象 MyModAPI
        registry.register("MyModAPI", MyModAPI.class);
    }
}
```

更多进阶内容（如自定义 Probe 类型生成器、配方生命周期拦截等），请参阅 [wiki/插件开发](wiki/插件开发.md)。

---

## 参与贡献与源码构建

欢迎提交 Issue、PR 或加入社区共同完善 NekoJS！

- **源码结构与构建方式**：请参阅 [wiki/构建系统](wiki/构建系统.md)。
- **交流群**：QQ 群 **1158525822** —— [点击加入群聊【NekoJS 交流群】](https://qm.qq.com/q/rbryak0K6k)

## 开源协议

本项目采用 [LGPL-3.0](LICENSE) 协议开源。
