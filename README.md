# 🐾 NekoJS 

<img src="icon.png" width="256" height="256" alt="NekoJS 图标">

**现代、极速、优雅的 Minecraft 脚本魔改引擎**

NekoJS 是一个基于 **NeoForge** 和 **GraalVM** 构建的新一代 Minecraft 脚本运行时。它不仅仅是一个执行 JavaScript 的工具，更致力于为整合包作者和模组开发者提供**比肩现代前端工程化**的极致开发体验。

（部分代码使用Gemini/ChatGPT生成，看板娘图像由ChatGPT生成）

## ✨ 核心特性

* 🚀 **GraalVM 强力驱动**: 拥抱最新 ECMAScript 标准，告别老旧的 Rhino/Nashorn，享受极速的执行性能与现代 JS 语法。
* 💎 **开箱即用的 TypeScript & JSX** *(需配合 NekoSWC)*: 底层深度集成 SWC 极速编译引擎。直接编写 `.ts` 和 `.tsx`，无需手动配置繁琐的编译管道，保存即运行！
* 🛠️ **极致的开发者体验 (DX)**:
    * 启动自动生成工作区配置 (`tsconfig.json` / `jsconfig.json`)。
    * 零配置实现完美的 IDE 智能提示与代码补全。
    * 彻底告别“无法重新声明块范围变量”等恼人的全局作用域冲突。
* 📦 **现代模块化与 NPM 生态**:
    * 完美支持基于 `require()` 和 `module.exports` 的多文件模块化开发。
    * **原生支持 Node 模块解析！** 你可以直接在 `nekojs` 目录下使用 npm 安装并引入**纯 JavaScript 编写**的第三方库（注：不支持包含 C/C++ 原生 bindings 的包）。
* 🔄 **丝滑的热重载**: 深度接入数据包生命周期，修改代码后只需执行 `/reload`，瞬间应用更改，拒绝反复重启游戏。
* 🛡️ **严格的安全沙盒**: 底层拦截高危 Java 反射与底层线程操作，并使用自定义文件系统严格限制脚本的 IO 权限（仅限游戏目录），保护玩家的电脑安全。

---

## 📂 目录结构

当你首次启动安装了 NekoJS 的游戏后，游戏根目录下会自动生成 `nekojs` 文件夹，结构如下：
```
nekojs/
├── startup_scripts/   # 游戏启动时加载（用于注册物品、方块等，修改需重启）
├── server_scripts/    # 服务器/存档加载时运行（用于配方、事件监听，支持 /reload 重载）
├── client_scripts/    # 仅客户端运行（用于 GUI 渲染、按键绑定等）
├── common_scripts/    # 双端公共逻辑
├── node_modules/      # 纯 JS 第三方库安装目录
├── probe/             # NekoJS 自动生成的全局类型声明 (.d.ts) 存放处，请勿修改
├── config/            # 脚本引擎配置文件
└── tsconfig.json      # 自动生成的工作区配置，为你提供完美的 IDE 补全体验
```
---

## 💻 快速开始

在 NekoJS 中，你可以使用最符合直觉的 TypeScript 编写代码，并且 IDE 会根据 `probe` 目录下的声明文件为你提供强大的支持。

### 1. 编写模块库 (utils.ts)

```
// server_scripts/utils.ts

// 自由使用 TypeScript 的类型注解！
function calculateDamage(base: number, multiplier: number): number {
return base * multiplier;
}

const MOD_NAME: string = "NekoJS";

// 使用 CommonJS 规范导出
module.exports = {
calculateDamage,
MOD_NAME
};
```

### 2. 编写主干逻辑与事件监听 (main.ts)

```
// server_scripts/main.ts

// 完美导入，IDE 会自动推导出 calculateDamage 和 MOD_NAME 的类型！
const { calculateDamage, MOD_NAME } = require('./utils.ts');

console.log(`[${MOD_NAME}] 正在加载自定义逻辑...`);

// 监听游戏事件
ServerEvent.tick(event => {
// 你的 Tick 逻辑
});
```
---

## 🧩 生态拓展：NekoSWC

NekoJS 核心主打轻量与稳定。如果你想解锁 TypeScript 和 JSX/TSX 的完整魔力，请务必安装官方附属模组 **[NekoSWC](链接预留)**。

NekoSWC 会在底层拦截 NekoJS 的文件读取流，利用底层 Rust 驱动的 `javet/swc4j` 引擎，实现**毫秒级**的实时转译。同时，它会智能拦截并升级你的工作区配置文件，让你立刻获得满血的前端开发体验。

---

## 🎯 事件系统

NekoJS 提供了丰富的事件监听机制，支持监听 Minecraft 游戏中的各种状态变化。

### 📋 已实现事件列表

#### 🎮 服务器事件 (ServerEvents)
- **ServerEvents.tickPre** - 服务器每 tick 开始前触发
- **ServerEvents.tickPost** - 服务器每 tick 结束后触发  
- **ServerEvents.recipes** - 配方注册事件

#### 👤 玩家事件 (PlayerEvents)
- **PlayerEvents.loggedIn** - 玩家登录游戏时触发

#### 🧬 实体事件 (EntityEvents)
- **EntityEvents.hurtPre** - 实体受到伤害前触发（带目标实体）
- **EntityEvents.hurtPost** - 实体受到伤害后触发（带目标实体）
- **EntityEvents.death** - 实体死亡时触发（带目标实体）

#### 🧱 方块事件 (BlockEvents)
- **BlockEvents.broken** - 方块被破坏时触发（带目标方块）
- **BlockEvents.rightClicked** - 方块被右键点击时触发（带目标方块）

#### 🎯 物品事件 (ItemEvents)
- **ItemEvents.rightClicked** - 物品被右键使用时触发（带目标物品）

#### 📚 注册事件 (RegistryEvents)
- **RegistryEvents.item** - 物品注册事件（启动时事件）
- **RegistryEvents.block** - 方块注册事件（启动时事件）

#### 💬 命令事件 (CommandEvents)
- **CommandEvents.register** - 命令注册时触发

### 🔍 事件类型说明

- **普通事件 (EventHandler)**：适用于全局事件监听
- **目标事件 (TargetedEventHandler)**：带有特定目标（实体、方块、物品）的事件
- **启动时事件 (startup)**：仅在游戏启动时触发一次
- **服务器事件 (server)**：在服务器/存档加载时运行，支持热重载

### 💡 使用示例

```typescript
// 监听服务器 tick 事件
ServerEvents.tickPre(event => {
    console.log('服务器 tick 开始');
});

// 监听玩家登录事件
PlayerEvents.loggedIn(event => {
    const player = event.player;
    console.log(`玩家 ${player.name} 已登录`);
});

// 监听实体受伤事件
EntityEvents.hurtPre(event => {
    const entity = event.entity;
    const damage = event.damage;
    console.log(`实体 ${entity.type} 即将受到 ${damage} 点伤害`);
});
```

---
## 🤝 参与贡献

NekoJS 目前正处于活跃开发阶段！无论是提交 Issue 报告 Bug、提供功能建议，还是提交 Pull Request，我们都非常欢迎！

* **API 文档**: [即将到来]
* **QQ 群**: [请在此处填写你的 QQ 群号]

---
### License

本项目采用 [LGPL-3.0 License](LICENSE) 开源。