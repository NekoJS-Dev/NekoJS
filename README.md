# NekoJS

<img src="icon.png" width="256" height="256" alt="NekoJS 图标">

**现代、极速、优雅的 Minecraft 脚本魔改引擎**

NekoJS 是一个基于 **NeoForge** 和 **GraalVM/GraalJS** 构建的 Minecraft JavaScript 脚本运行时。它面向整合包作者和模组开发者，目标是在 Minecraft 中提供接近现代前端工程化的脚本开发体验。

**前置需要 [Graal](https://www.curseforge.com/minecraft/mc-mods/graal)。请以当前发布页面标注的 Minecraft / NeoForge 版本为准。**

（部分代码使用 Gemini/ChatGPT 生成，看板娘图像由 ChatGPT 生成）

## 核心特性

* **GraalVM 强力驱动**：拥抱最新 ECMAScript 标准，告别老旧的 Rhino/Nashorn，享受现代 JS 语法和 GraalJS 运行时能力。
* **TypeScript & JSX 扩展能力**：NekoJS 本体提供编译器扩展点；`.ts`、`.jsx`、`.tsx` 执行需要安装 NekoSWC 或其他注册到 NekoJS 的脚本编译器。
* **开发者体验优先**：启动后自动生成工作区目录和编辑器配置，配合 NekoProbe 可获得 IDE 智能提示与代码补全。
* **现代模块化与 NPM 生态**：支持基于 `require()` / `module.exports` 的多文件模块化开发，并可在 `nekojs` 目录下引用纯 JavaScript npm 依赖（不支持包含原生 bindings 的包，也不等同于完整 Node.js 运行时）。
* **服务端热重载**：服务端脚本可通过 `/nekojs reload` 重新加载；启动注册类脚本仍需重启游戏。
* **受限安全沙盒**：NekoJS 会限制脚本文件访问范围并过滤高危 Java 类访问。脚本仍应视为可信代码，尤其是在多人服务器中使用远程同步功能时。

---

## 目录结构

首次启动安装了 NekoJS 的游戏后，游戏根目录下会自动生成 `nekojs` 文件夹：

```text
.neko_probe/                # NekoProbe 类型声明库：存放自动生成的 .d.ts 文件（与 nekojs 目录同级）
nekojs/
├── startup_scripts/   # 游戏启动脚本：用于注册物品、方块等核心组件（修改需重启游戏）
│   └── tsconfig.json  # 编辑器配置文件：自动关联根目录 .neko_probe 类型库
├── server_scripts/    # 服务端脚本：负责配方修改、事件监听，支持 /nekojs reload
│   └── tsconfig.json
├── client_scripts/    # 客户端脚本：负责 GUI 渲染、粒子效果、按键绑定等视觉逻辑
│   └── tsconfig.json
├── node_modules/      # 外部库目录：支持原生 Node 模块解析，存放纯 JS 依赖
├── assets/            # 资源目录
├── data/              # 数据包目录
└── config/            # 引擎配置文件，例如安全沙盒相关 engine.toml
```

当前可执行脚本目录为 `startup_scripts/`、`server_scripts/` 和 `client_scripts/`。脚本文件支持 `.js`、`.ts`、`.jsx`、`.tsx`，其中非 `.js` 文件需要可用的编译器扩展（推荐 NekoSWC）。

---

## 快速开始

### 1. 编写模块库 (`utils.ts`)

```typescript
// server_scripts/utils.ts

function calculateDamage(base: number, multiplier: number): number {
    return base * multiplier;
}

const MOD_NAME: string = "NekoJS";

module.exports = {
    calculateDamage,
    MOD_NAME
};
```

### 2. 编写主干逻辑与事件监听 (`main.ts`)

```typescript
// server_scripts/main.ts

const { calculateDamage, MOD_NAME } = require('./utils.ts');

console.log(`[${MOD_NAME}] 正在加载自定义逻辑...`);

ServerEvents.tickPre(event => {
    // 你的 Tick 逻辑
});
```

---

## 安全模型

NekoJS 的脚本运行在受限 GraalJS 环境中，但它不是“不可信代码执行平台”。请只运行你信任的脚本，尤其不要在公共服务器上授予陌生玩家远程编辑权限。

当前安全边界包括：

* 文件系统访问会被限制在游戏目录内，并检测已存在路径的符号链接逃逸。
* Java 类访问经过 `ClassFilter` 过滤，默认禁止线程、反射、ASM、进程、网络、底层 IO 等高危入口。
* `nekojs/config/engine.toml` 中的 `allowThreads`、`allowReflection`、`allowAsm` 是高危能力开关，默认关闭。
* 游戏内工作区同步只应交给可信管理员使用；同步功能会限制在脚本目录和脚本扩展名范围内。

---

## 生态拓展

### NekoSWC

NekoJS 核心主打轻量与稳定。如果你想使用 TypeScript、JSX 或 TSX，请安装官方附属模组 **[NekoSWC](https://github.com/Tki-sor/NekoSWC)**。

NekoSWC 会在底层拦截 NekoJS 的文件读取流，利用 Rust 驱动的 `javet/swc4j` 引擎进行实时转译，并增强工作区配置。

### NekoProbe

NekoProbe 是 NekoJS 的类型生成模组，用于提供 IDE 智能提示与代码补全。

如果需要更方便地编写脚本，建议安装 **[NekoProbe](https://github.com/Tki-sor/NekoProbe)**。

---

## 事件系统

NekoJS 提供事件监听机制，用于响应 Minecraft 游戏中的各种状态变化。

### 已实现事件列表

```text
服务器事件 (ServerEvents)
├── tickPre - 服务器每 tick 开始前触发
├── tickPost - 服务器每 tick 结束后触发
└── recipes - 配方注册事件

玩家事件 (PlayerEvents)
├── loggedIn - 玩家登录游戏时触发
└── chat - 玩家发送聊天消息时触发

实体事件 (EntityEvents)
├── hurtPre - 实体受到伤害前触发（带目标实体）
├── hurtPost - 实体受到伤害后触发（带目标实体）
└── death - 实体死亡时触发（带目标实体）

方块事件 (BlockEvents)
├── broken - 方块被破坏时触发（带目标方块）
├── rightClicked - 方块被右键点击时触发（带目标方块）
└── placed - 方块被放置时触发（带目标方块）

物品事件 (ItemEvents)
├── rightClicked - 物品被右键使用时触发（带目标物品）
├── tooltip - 物品提示信息显示时触发（客户端事件）
└── crafted - 物品被合成时触发

注册事件 (RegistryEvents)
├── item - 物品注册事件（启动时事件）
└── block - 方块注册事件（启动时事件）

命令事件 (CommandEvents)
└── register - 命令注册时触发
```

### 事件类型说明

- **普通事件 (EventHandler)**：适用于全局事件监听。
- **目标事件 (TargetedEventHandler)**：带有特定目标（实体、方块、物品）的事件。
- **启动时事件 (startup)**：仅在游戏启动时触发一次。
- **服务器事件 (server)**：在服务器/存档加载时运行，支持热重载。

### 使用示例

```typescript
ServerEvents.tickPre(event => {
    console.log('服务器 tick 开始');
});

PlayerEvents.loggedIn(event => {
    const player = event.player;
    console.log(`玩家 ${player.name} 已登录`);
});

EntityEvents.hurtPre(event => {
    const entity = event.entity;
    const damage = event.damage;
    console.log(`实体 ${entity.type} 即将受到 ${damage} 点伤害`);
});
```

---

## 路线图

后续规划见 [docs/ROADMAP.md](docs/ROADMAP.md)。

---

## 参与贡献

NekoJS 目前正处于活跃开发阶段。无论是提交 Issue 报告 Bug、提供功能建议，还是提交 Pull Request，我们都非常欢迎。

* **API 文档**：即将到来
* **QQ 群**：1158525822 [点击加入群聊【NekoJS 魔改交流群（？】](https://qm.qq.com/q/rbryak0K6k)

---

## License

本项目采用 [LGPL-3.0 License](LICENSE) 开源。
