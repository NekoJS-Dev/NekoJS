# NekoJS

<img src="icon.png" width="256" height="256" alt="NekoJS 图标">

**现代、极速、优雅的 Minecraft 脚本魔改引擎**

NekoJS 是一个基于 **NeoForge** 和 **GraalVM/GraalJS** 构建的 Minecraft JavaScript 脚本运行时。它面向整合包作者和模组开发者，目标是在 Minecraft 中提供接近现代前端工程化的脚本开发体验。

**前置需要 [Graal](https://www.curseforge.com/minecraft/mc-mods/graal)。请以当前发布页面标注的 Minecraft / NeoForge 版本为准。**

（部分代码使用 ChatGPT/GLM5.2 生成，看板娘图像由 ChatGPT 生成）

## 核心特性

* **GraalVM 强力驱动**：拥抱最新 ECMAScript 标准，告别老旧的 Rhino/Nashorn，享受现代 JS 语法和 GraalJS 运行时能力。
* **TypeScript & JSX 本体支持**：NekoJS 本体内置 `.ts` erasable TypeScript 前端和轻量 `.jsx/.tsx` classic runtime lowering；后续高级 TS/TSX/JSX 语法也优先在本体语言前端中补齐。
* **原生 ESM 运行时**：支持 `import`/`export`、live binding、循环依赖、top-level await、`import.meta`、dynamic `import()` 和 ESM/CJS 互操作。
* **Node.js 兼容 API**：内置 `fs`、`path`、`buffer`、`process`、`timers`、`util`、`events`、`assert`、`os`、`test` 等核心模块 shim。
* **开发者体验优先**：启动后自动生成工作区目录、编辑器配置和可供外部工具消费的 catalog 元数据；NekoProbe 作为独立项目消费这些信息提供 IDE 智能提示与代码补全。
* **现代模块化与 NPM 生态**：支持基于 `require()` / `module.exports` 的多文件模块化开发，并可在 `nekojs` 目录下引用纯 JavaScript npm 依赖（不支持包含原生 bindings 的包，也不等同于完整 Node.js 运行时）。
* **服务端热重载**：服务端脚本可通过 `/nekojs reload` 重新加载；启动注册类脚本仍需重启游戏。
* **受限安全沙盒**：NekoJS 会限制脚本文件访问范围并过滤高危 Java 类访问。脚本仍应视为可信代码，尤其是在多人服务器中使用远程同步功能时。
* **多平台支持**：同时支持 NeoForge 26.1 26.2 1.21.1，共享 common 基础设施。
* **内置Probe补全**: 无需安装 ProbeJS 这类 Mod 即可使用补全，并且实现可被其他更完善的 Probe 替换。

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

当前自动加载脚本目录为 `startup_scripts/`、`server_scripts/` 和 `client_scripts/`；`test_scripts/` 是通过 `/nekojs test` 显式运行的测试环境。脚本文件支持 `.js`、`.mjs`、`.cjs`、内置 erasable `.ts`，以及轻量 `.jsx/.tsx` classic runtime lowering；更复杂的 TS/TSX 语法会逐步收敛到 NekoJS 本体语言前端。

## 源码结构

```text
common/                          # 跨平台通用代码
└── src/main/java/com/tkisor/nekojs/
    ├── core/                    # 核心运行时：Graal Context/Engine、ClassFilter、VFS
    ├── script/                  # 脚本管理：NekoJSScriptManager、ScriptType、reload
    ├── api/                     # 公开 API：NekoJSPlugin、JSTypeAdapter、事件声明、catalog
    ├── bindings/                # JS 全局绑定
    ├── plugin/                  # 插件系统：extension point、bootstrap snapshot
    ├── network/                 # 网络同步
    └── wrapper/                 # 脚本友好 wrapper

platforms/
├── neoforge-26.1/               # NeoForge 26.1
│   └── src/main/java/.../neoforge-26.1/...
├── neoforge-26.2/               # NeoForge 26.2
│   └── src/main/java/.../neoforge-26.2/...
└── neoforge-1.21.1/             # NeoForge 1.21.1
    └── src/main/java/...
```

---

## Java 模块导入

NekoJS 把 Java 包/类当成 `java:` 特殊模块处理。ESM 会把 Java 导入重写成 synthetic module；CJS 的 `require()` 直接返回 Java namespace / class proxy。

### 包级模块

```ts
import { Integer, $Integer, Math as JavaMath } from 'java:java/lang'
const { Integer, $Integer, Math: JavaMath } = require('java:java/lang')
```

- 包级模块是懒加载 namespace proxy：普通名字按属性查找，`$Class` 会直接映射到 `Java.type('java.lang.Class')`。
- 因此 `Integer`、`$Integer`、`Math` / `JavaMath` 这类写法都能用。

### 类级模块

```ts
import IntegerClass, { $Integer } from 'java:java/lang/Integer'
const IntegerClass3 = require('java:java/lang/Integer')
```

- 类级模块会直接返回 Java class proxy，并额外暴露 `default` / `$Class`。
- 如果只想拿一个明确的 Java 类，这种写法最直接。

### 兼容边界

- 现在只接受 `java:` 前缀。
- 现在只接受斜杠分隔路径：`java:java/lang`、`java:java/lang/Integer`。
- `import('java:java/lang')` / `import('java:java/lang/Integer')` 会得到带 `default` / `namespace` 的 synthetic ESM module。
- ESM static import / dynamic import 推荐优先使用 `java:` 斜杠形式。
- 类型生成器优先输出 `java:package/path` + `$Class`，再按需补 `java:package/path/Class` 的类级模块。

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

## 编辑器类型检查

NekoJS 生成的类型声明（`.neko_probe/`）已经把 `ServerEvents`、`BlockEvents` 等全局对象及其事件参数类型完整暴露给编辑器，因此绝大多数错误可以在**事件触发前**就被发现：

* 用 `.ts` 编写脚本可获得完整的编辑器类型检查；现有的 `.js` 脚本只需在文件首行加上 `// @ts-check` 即可逐文件启用检查。
* 拼写错误（如把 `event.recipes` 写成 `event.rec`）会立即被标红，无需 `import` 任何类型 —— 全局事件对象的签名会自动推断 `event` 的类型。
* 运行时（游戏内）同样会拦截这类错误：事件回调里访问不存在的成员、或使用未定义的变量，会被记录到错误面板，用 `/nekojs view_all_errors` 查看。

> 注意：NekoJS 内置的是 erasable TypeScript 前端（类型标注会在运行前擦除），暂不支持 `enum` / `namespace` / `module` 等 TS 语法；这类语法会在加载时报错，请在脚本中避免。

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

### 语言前端

NekoJS 核心主打轻量与稳定，内置 `.ts` 的 erasable TypeScript 支持：类型标注、`type` / `interface`、`import type` / `export type` 等会在 Java 前端中擦除，之后继续走 NekoJS 自有 ESM/CJS pipeline。NekoJS 也内置轻量 `.jsx/.tsx` classic runtime lowering，会把 JSX 元素降到 `globalThis.__nekoJsxFactory(...)` / `globalThis.__nekoJsxFragment(...)`。

后续方向是继续增强 NekoJS 本体语言前端，而不是依赖外部 NekoSWC 模组来承担高级 TS/TSX/JSX 转换。脚本语言插件 registry 仍保留给第三方语言扩展使用，但 NekoJS 自身的 TypeScript、JSX、sourcemap chain 和 diagnostics 会优先在本体实现。

### 插件扩展点示例

NekoJS 插件默认通过多入口 typed hooks 注册能力，例如 `registerBindings`、`registerAdapters`、`registerEvents`。如果外部 mod 需要定义新的插件类型，可以通过 `NekoPluginExtensionProvider` 在 bootstrap 的第一阶段注册 extension point descriptor；所有插件类型注册完成后，bootstrap 才会进入第二阶段收集具体贡献。

例如某个 mod 想提供 startup-only bindings 插件类型，可以先定义一个新的 typed plugin interface：

```java
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.data.BindingRegistry;

public interface StartupBindingsPlugin extends NekoJSPlugin {
    void registerStartupBinding(BindingRegistry registry);
}
```

然后用一个被 NekoJS 发现的插件注册这个新 extension point：

```java
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.plugin.NekoPluginExtensionPoint;
import com.tkisor.nekojs.api.plugin.NekoPluginExtensionProvider;
import com.tkisor.nekojs.api.plugin.NekoPluginExtensionRegistry;
import com.tkisor.nekojs.script.ScriptType;

@RegisterNekoJSPlugin
public final class MyExtensionPointPlugin implements NekoJSPlugin, NekoPluginExtensionProvider {
    @Override
    public void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry) {
        registry.register(NekoPluginExtensionPoint.of(
                "mymod:startup_bindings",
                StartupBindingsPlugin.class,
                (plugin, context) -> plugin.registerStartupBinding(context.bindings().at(ScriptType.STARTUP))
        ));
    }
}
```

之后其他插件只要实现这个接口，就会被同一个 extension point 收集：

```java
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.BindingRegistry;

@RegisterNekoJSPlugin
public final class MyStartupApiPlugin implements StartupBindingsPlugin {
    @Override
    public void registerStartupBinding(BindingRegistry registry) {
        registry.register("MyStartupApi", MyStartupApi.class);
        if (registry.scriptType() != ScriptType.STARTUP) { // always false, see extension point registry
            registry.register("NotStartup", new NotStartupValue());
        }
    }
}
```

这个流程的生命周期是固定的：先扫描并实例化所有 `@RegisterNekoJSPlugin`，再收集所有 `NekoPluginExtensionProvider` 注册的插件类型，冻结 extension point registry 后才执行各插件的 typed hooks。extension point 的 collector 只能访问受限的 `NekoPluginExtensionContext` registry，不会拿到 `NekoPluginRuntime` 内部集合。所有 registry 都只允许在 bootstrap 收集阶段写入，bootstrap 完成后会 fail-fast 拒绝延迟注册。

Recipe lifecycle 也是同一套 typed hook：外部插件可以实现 `RecipeLifecyclePlugin`，或在 `registerRecipeLifecycleHooks` 中注册 `beforeRecipeLoading` / `afterRecipes`。这两个 hook 分别运行在 server recipe 脚本事件前后，操作的是受控 `RecipeLifecycleContext`，不会暴露 recipe manager 的内部 mutable map。

### 数据驱动配方方法

NekoJS 支持用数据包资源给 `event.recipes.<namespace>.<type>(...)` 增加轻量方法定义，路径为：

```text
data/<namespace>/nekojs/recipe_types/<type>.json
```

例如：

```json
{
  "type": "create:mixing",
  "constructors": [["result", "ingredients"]],
  "fields": {
    "result": { "path": "results", "kind": "item_stack", "array": true },
    "ingredients": { "path": "ingredients", "kind": "ingredient", "array": true }
  }
}
```

脚本侧即可写：

```js
event.recipes.create.mixing('create:brass_ingot', [
  'minecraft:copper_ingot',
  'create:zinc_ingot'
])
```

这只是 JSON-first 的轻量 facade：字段通过 JSON path 写入，`kind` 负责把脚本值转成 datapack JSON；未知 namespace/type 仍可使用 raw JSON fallback。

### NekoProbe

NekoProbe 是独立于本项目的类型生成/编辑器体验项目。NekoJS 本体负责提供稳定的 `NekoScriptCatalog` 元数据、workspace layout 和 snippets 数据，NekoProbe 消费这些信息生成更完整的 IDE 智能提示与代码补全。

---

## 事件系统

NekoJS 提供事件监听机制，用于响应 Minecraft 游戏中的各种状态变化。

### 已实现事件列表

```text
服务器事件 (ServerEvents)
├── tickPre - 服务器每 tick 开始前触发
├── tickPost - 服务器每 tick 结束后触发
├── recipes - 配方注册事件
└── afterRecipes - 配方脚本执行后、最终解析前触发

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

### Startup 自定义事件方法

`startup_scripts` 可以用 `ScriptEvents` 把 NeoForge 原生事件注册成更友好的 server/client 事件方法：

```js
// startup_scripts/src/events.js
ScriptEvents.server(event => event.register('CustomServerEvents', 'playerTick', 'net.neoforged.neoforge.event.tick.PlayerTickEvent.Post'))
ScriptEvents.client(event => event.register('CustomClientEvents', 'screenOpening', 'net.neoforged.neoforge.client.event.ScreenEvent.Opening'))
```

随后在对应环境监听：

```js
// server_scripts/src/main.js
CustomServerEvents.playerTick(event => {
  console.info(`player tick: ${event.getEntity().getName().getString()}`)
})
```

对象形式可设置优先级和是否接收已取消事件：

```js
ScriptEvents.server(event => event.register({
  group: 'CustomServerEvents',
  name: 'rightClickBlock',
  event: 'net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock',
  priority: 'normal',
  receiveCancelled: false
}))
```

自定义事件不会写入插件 bootstrap 的静态事件表；startup reload 会刷新事件定义，server/client reload 会清理对应脚本 listener，避免重复回调。

---

## 路线图

后续规划见 [docs/ROADMAP.md](docs/ROADMAP.md)。

---

## 参与贡献

NekoJS 目前正处于活跃开发阶段。无论是提交 Issue 报告 Bug、提供功能建议，还是提交 Pull Request，我们都非常欢迎。

* **QQ 群**：1158525822 [点击加入群聊【NekoJS 魔改交流群（？】](https://qm.qq.com/q/rbryak0K6k)

---

## License

本项目采用 [LGPL-3.0 License](LICENSE) 开源。
