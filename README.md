# NekoJS

<img src="icon.png" width="256" height="256" alt="NekoJS 图标">

**现代、极速、优雅的 Minecraft 脚本魔改引擎**

NekoJS 是一个基于 **NeoForge** 和 **GraalVM/GraalJS** 构建的 Minecraft JavaScript 脚本运行时。它面向整合包作者和模组开发者，目标是在 Minecraft 中提供接近现代前端工程化的脚本开发体验。

**前置需要 [Graal](https://www.curseforge.com/minecraft/mc-mods/graal)（25.1.3.6+）。请以当前发布页面标注的 Minecraft / NeoForge 版本为准。**

（部分代码使用 ChatGPT/GLM5.2 生成，看板娘图像由 ChatGPT 生成）

## 核心特性

* **GraalVM 强力驱动**：拥抱最新 ECMAScript 标准，告别老旧的 Rhino/Nashorn，享受现代 JS 语法和 GraalJS 运行时能力。
* **TypeScript & JSX 本体支持**：NekoJS 本体内置 `.ts` erasable TypeScript 前端和轻量 `.jsx/.tsx` classic runtime lowering；后续高级 TS/TSX/JSX 语法也优先在本体语言前端中补齐。同时内置 Python 子集转译前端（`.py` 自动加载，无需外部运行时，probe 提供 `.pyi` stub）。
* **原生 ESM 运行时**：支持 `import`/`export`、live binding、循环依赖、top-level await、`import.meta`、dynamic `import()` 和 ESM/CJS 互操作。
* **Node.js 兼容 API**：内置 `fs`、`path`、`buffer`、`process`、`timers`、`util`、`events`、`assert`、`os`、`test` 等核心模块 shim。
* **开发者体验优先**：启动后自动生成工作区目录、编辑器配置（jsconfig.json）和可供外部工具消费的 catalog 元数据；内置 probe 直接遍历 catalog 生成声明文件（多后端：TypeScript `.d.ts` 与 Python `.pyi` stub，通过 `/nekojs probe` 子命令与 `probe.toml` 的 `languages.typescript` / `languages.python` 选择），无需安装 ProbeJS 这类外部 mod 即可获得 IDE 智能提示与代码补全。
* **现代模块化与 NPM 生态**：支持基于 `require()` / `module.exports` 的多文件模块化开发，并可在 `nekojs` 目录下引用纯 JavaScript npm 依赖（不支持包含原生 bindings 的包，也不等同于完整 Node.js 运行时）。
* **服务端热重载**：服务端脚本可通过 `/nekojs reload` 重新加载；启动注册类脚本仍需重启游戏。
* **配方热重载**：Cleanroom 1.12.2 上 `/nekojs reload server` 会解冻注册表 → 移除旧 nekojs 配方 → 重跑配方脚本 → 重新冻结，并通过 mixin 自动刷新 HEI/JEI 配方面板。NeoForge 平台（26.x / 1.21.1）同样支持热重载：reload 会重新执行配方脚本并整体替换 `RecipeManager.recipes`（mixin `RecipeManagerMixin#nekojs$applyScripts`，从 prepare 阶段永久缓存的基础配方 JSON 重建工作集）。
* **受限安全沙盒**：NekoJS 会限制脚本文件访问范围并过滤高危 Java 类访问。脚本仍应视为可信代码，尤其是在多人服务器中使用远程同步功能时。
* **多平台支持**：同时支持 NeoForge 26.1 / 26.2 / 1.21.1 与 Cleanroom 1.12.2（Forge），共享 common 基础设施。
* **脚本方法校验**：加载时静态扫描全局绑定和事件回调的成员访问，拼写错误即时提示（如 `Utils.randmInt` → "Did you mean 'randomInt'?"）。可通过 `engine.toml` 中 `scriptMemberValidation` 选项关闭，关闭后零性能开销。
* **可替换的 probe 实现**：内置 probe 由 `ProbeCoordinator` 统一收集类型并派发给可插拔的 `ProbeBackend` 后端；第三方插件可通过 `ProbeBackendRegistry.register(backend, source)` 注册自定义后端（注册表在 bootstrap 时锁定，冲突会 fail-fast 报错）。

---

## 目录结构

首次启动安装了 NekoJS 的游戏后，游戏根目录下会自动生成 `nekojs` 文件夹：

```text
.neko_probe/                # NekoProbe 类型声明库：按语言分子目录存放自动生成的声明（默认 typescript/ + python/，可用 probe.toml 的 [languages.<id>].outputDir 配置；与 nekojs 目录同级）
nekojs/
├── startup_scripts/   # 游戏启动脚本：用于注册物品、方块等核心组件（修改需重启游戏）
│   └── tsconfig.json  # 编辑器配置文件：自动关联根目录 .neko_probe 类型库
├── server_scripts/    # 服务端脚本：负责配方修改、事件监听，支持 /nekojs reload
│   └── tsconfig.json
├── client_scripts/    # 客户端脚本：负责 GUI 渲染、粒子效果、按键绑定等视觉逻辑
│   └── tsconfig.json
├── test_scripts/     # 测试脚本：通过 /nekojs test 显式运行
│   └── tsconfig.json
├── node_modules/      # 外部库目录：支持原生 Node 模块解析，存放纯 JS 依赖
├── assets/            # 资源目录
├── data/              # 数据包目录
└── config/            # 引擎配置文件：engine.toml（安全沙盒等）与 probe.toml（类型生成）
```

当前自动加载脚本目录为 `startup_scripts/`、`server_scripts/` 和 `client_scripts/`；`test_scripts/` 是通过 `/nekojs test` 显式运行的测试环境。脚本文件支持 `.js`、`.mjs`、`.cjs`、内置 erasable `.ts`、轻量 `.jsx/.tsx` classic runtime lowering，以及 `.py`（Python 子集，同一套脚本目录自动加载）；更复杂的 TS/TSX 语法会逐步收敛到 NekoJS 本体语言前端。脚本文件可用首行注释声明属性：`// priority: <n>` 与 `// after: <path>`（`after:` 已强制生效：同 priority 内按拓扑顺序加载，未解析的引用会告警，成环时回退到原顺序）。

## 源码结构

```text
common/                          # 跨平台通用代码
└── src/main/java/com/tkisor/nekojs/
    ├── core/                    # 核心运行时：Graal Context/Engine、ClassFilter、VFS
    ├── script/                  # 脚本管理：NekoJSScriptManager、ScriptType、reload
    ├── api/                     # 公开 API：NekoJSPlugin、JSTypeAdapter、事件声明、catalog
    ├── bindings/                # JS 全局绑定
    ├── probe/                   # 类型声明生成（.d.ts / .pyi 多后端）
    ├── eventbus/                # 事件总线实现
    ├── plugin/                  # 插件系统：extension point、bootstrap snapshot
    ├── network/                 # 网络同步
    └── wrapper/                 # 脚本友好 wrapper

platforms/
├── cleanroom-1.12.2/            # Cleanroom 1.12.2（Forge）
│   └── src/main/java/...
├── neoforge-26.1/               # NeoForge 26.1
│   └── src/main/java/...
├── neoforge-26.2/               # NeoForge 26.2
│   └── src/main/java/...
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

> 注意：NekoJS 内置的 TypeScript 前端支持「可擦除」语法（类型标注、`type`/`interface`、泛型、`as`/`satisfies`、`import type`/`export type`、`declare`、参数属性 `constructor(public x)`、`enum`/`const enum`、`namespace`/`module`、类成员修饰符、`?.`/`!`、函数重载签名等）——这些会在运行前擦除或降级为运行时 IIFE/赋值。**不支持** 装饰器（`@Decorator`）—— NekoJS 是脚本引擎非 TS 框架，遇到装饰器会清晰报错，请改用普通函数包装。

---

## 安全模型

NekoJS 的脚本运行在受限 GraalJS 环境中，但它不是“不可信代码执行平台”。请只运行你信任的脚本，尤其不要在公共服务器上授予陌生玩家远程编辑权限。

当前安全边界包括：

* 文件系统访问会被限制在游戏目录内，并检测已存在路径的符号链接逃逸。
* Java 类访问经过 `ClassFilter` 过滤，默认禁止线程、反射、ASM、进程、网络、底层 IO 等高危入口。
* `nekojs/config/engine.toml` 中的 `allowThreads`、`allowReflection`、`allowAsm` 是高危能力开关，默认关闭。
* `scriptMemberValidation`（默认开启）在脚本加载时静态扫描全局绑定和事件回调的成员访问，拼写错误会即时提示。关闭可跳过 AST 解析开销。推荐开发时开启，整合包分发时可关闭。
* `scriptEvaluationTimeoutSeconds`（默认 30；0 或负数表示不限制）限制脚本入口求值时长：顶层 await / 模块加载永不完成时按超时报错终止，防止挂死服务器线程。
* 游戏内工作区同步只应交给可信管理员使用；同步功能会限制在脚本目录和脚本扩展名范围内。

---

## 生态拓展

### 语言前端

NekoJS 核心主打轻量与稳定，内置 `.ts` 的 TypeScript 支持：类型标注、`type` / `interface`、`import type` / `export type`、泛型（含泛型箭头 `<T>(x: T) => T`）、`as` / `satisfies`、内联 `import { x, type T }`、参数属性、`enum` / `namespace`、类成员修饰符等会在 Java 前端中擦除或降级，之后继续走 NekoJS 自有 ESM/CJS pipeline。NekoJS 也内置 `.jsx/.tsx` lowering：默认 classic runtime（`globalThis.__nekoJsxFactory(...)` / `globalThis.__nekoJsxFragment(...)`），支持 HTML 实体解码、命名空间标签（`<svg:rect/>`）、泛型组件（`<Foo<T>/>`）；在 `nekojs/config/engine.toml` 里设 `jsxAutomaticRuntime = true` 可切换到标准 automatic runtime：从 `nekojs/jsx-runtime` 导入 `jsx`、`jsxs` 和 `Fragment`，子节点放在 `props.children`。在 `nekojs/` 工作区内，请将 runtime 模块放在裸模块路径 `node_modules/nekojs/jsx-runtime.js`。

后续方向是继续增强 NekoJS 本体语言前端，而不是依赖外部 NekoSWC 模组来承担高级 TS/TSX/JSX 转换。脚本语言插件 registry 仍保留给第三方语言扩展使用，但 NekoJS 自身的 TypeScript、JSX、sourcemap chain 和 diagnostics 会优先在本体实现。

NekoJS 同样内置 Python 子集转译前端（无外部运行时）：支持 match/case、@staticmethod/@classmethod/@property、生成器、for/else、**kwargs、f-string 与 source map；`.py` 文件与 `.js`/`.ts` 一样从同一套脚本目录自动加载；probe 的 `PythonProbeBackend` 会为 Python 脚本生成 `.pyi` stub。

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
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionPoint;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionProvider;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionRegistry;
import com.tkisor.nekojs.api.ScriptType;

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

### 插件加载顺序与条件

`@RegisterNekoJSPlugin` 支持三个参数控制加载行为：

- `priority`（int，默认 1000）：**数值越大越先加载**。内置核心插件用 `NekoJSPlugin.CORE_PRIORITY`（`Integer.MAX_VALUE`）确保最先注册 adapter/binding 等基础设施。
- `clientOnly`（boolean，默认 false）：仅客户端进程加载，dedicated server 跳过该插件。
- `requiredMods`（String[]，默认空）：列出的 mod 全部在场才加载（AND 语义）。

```java
@RegisterNekoJSPlugin(priority = 500, clientOnly = true, requiredMods = {"jei", "mekanism"})
public final class MyIntegrationPlugin implements NekoJSPlugin { ... }
```

过滤与排序在 common 的 `NekoJSBasePluginManager.registerClass` 中统一完成，4 平台 PluginLoader 只负责发现被注解的类。

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

类型生成已经内置在本仓库中：`ProbeCoordinator` 完成一次共享类收集后，把共享 IR 派发给内置或第三方注册的 `ProbeBackend` 各自渲染（内置 TypeScript `.d.ts` 与 Python `.pyi` 后端）。第三方插件可通过 `probe.assign_type` / `probe.modify_type` / `probe.add_global` / `probe.snippets` 事件定制类型与代码补全，并可通过 editor-config contributor 合并编辑器配置；`NekoScriptCatalog` 元数据与 workspace layout 仍由 NekoJS 本体稳定提供。

可用 `/nekojs probe` 手动触发生成：无参默认只跑 TypeScript 内置后端，支持 `all`（跑全部已注册后端）、`list`（列出后端）、`reload`（重读配置）、`enable` / `disable`（持久化开关）与 `<language> [name]`（指定语言/后端）子命令；配置文件为 `<game>/nekojs/config/probe.toml`。

---

## 事件系统

NekoJS 提供事件监听机制，用于响应 Minecraft 游戏中的各种状态变化。

### 已实现事件列表

NekoJS 提供 13 个事件组（以 NeoForge 26.x 为准，平台支持范围以 probe 为准）。下列仅展示每组常用事件：

```text
服务器事件 (ServerEvents)        约 13 个  tickPre / tickPost / recipes / afterRecipes / tags ...
玩家事件 (PlayerEvents)          约 17 个  loggedIn / loggedOut / chat / tickPre / tickPost /
                                              cloned / respawned / changedDimension / advancement /
                                              container* / inventory* / entityInteract /
                                              crafted / smelted / destroyed
实体事件 (EntityEvents)          约 13 个  hurtPre / hurtPost / death ...
方块事件 (BlockEvents)           约 11 个  broken / rightClicked / placed ...
物品事件 (ItemEvents)             约 8 个  rightClicked / tooltip / crafted ...
注册事件 (RegistryEvents)        约 12 个  item / block / entityType / fluid / creativeModeTab / soundEvent / mobEffect / potion / particleType / paintingVariant / villagerType / enchantment（启动时事件）
命令事件 (CommandEvents)          约 2 个  register ...
目标事件 (GoalEvents)             约 1 个
关卡事件 (LevelEvents)           约 10 个  loaded / unloaded / tick ...
网络事件 (NetworkEvents)          约 2 个
能力事件 (CapabilityEvents)        约 1 个  register（启动时事件）
配方查看器事件 (RecipeViewerEvents) 约 5 个  addEntries / removeEntries / removeRecipes / removeCategories / addInformation（客户端，需 JEI）
客户端事件 (ClientEvents)        约 13 个
```

> 完整、权威的事件签名以 probe 生成的 `.neko_probe/@side-only/<type>/events/index.d.ts` 为准；事件组持续扩充，本表只列代表性事件。

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

## 数据与资产生成

脚本可以在资源 reload 时生成 datapack / 资源包 JSON（战利品表、进度、模型、lang 等），写入 `<gameDir>/nekojs/data` 与 `<gameDir>/nekojs/assets`（已注册为 TOP 位置的 datapack / resource pack，懒读保证 reload 时序正确）：

```js
ServerEvents.generateData('after_mods', event => {
  event.json('minecraft:loot_tables/blocks/stone.json', {
    type: 'minecraft:block',
    pools: []
  });
  event.text('minecraft:nekojs/hello.txt', 'content');
});

ClientEvents.generateAssets('after_mods', event => {
  event.json('minecraft:models/block/foo.json', { parent: 'minecraft:block/cube_all' });
});

ClientEvents.lang('en_us', event => {
  event.add('minecraft:item.foo', 'Foo Item');
});
```

- `generateData` / `generateAssets` 按阶段定向（当前支持 `after_mods`）；`lang` 按语言代码定向（`en_us` 等），条目合并写入 `lang/<lang>.json`（保留已有条目）。
- 每次服务器 / 客户端（F3+T）资源 reload 都会重新触发，脚本需保持幂等（重复写入会覆盖）。
- 外部 mod 可通过 `NekoJSPlugin.generateData/generateAssets/generateLang` 钩子生成数据（先于脚本事件触发）。

Cleanroom 1.12.2 平台的差异：

- `generateData` 写入 `<worldDir>/data`（loot tables / advancements / functions），由 `/nekojs reload server` 触发并调用 `server.reload()`（vanilla /reload 等价物）使内容生效。
- `generateAssets` 写入 `<gameDir>/nekojs/assets`，由 `MinecraftMixin` 注册为 `FolderResourcePack`，每次 F3+T（或首次进入游戏）生效；由于 `LanguageManager` 是第一个资源 reload listener，生成先于模型/纹理加载。
- `lang` 为 `.lang` 文本格式，不写 JSON 文件：条目经 `LanguageManagerMixin` 直接注入当前语言的 `Locale`（mixin 注入，无反射），并 `LanguageMap.replaceWith` 同步到 I18n。

## 配方查看器集成（JEI）

NeoForge 平台（1.21.1 / 26.1 / 26.2）内置 JEI 集成，脚本在 CLIENT 脚本中监听（与 KubeJS 的 RecipeViewerEvents 对齐，裁剪版）：

```js
// 从 JEI 隐藏指定物品（不彻底移除）
RecipeViewerEvents.removeEntries('item', event => {
  event.add('minecraft:stone');
});

// 向 JEI 添加条目（如脚本生成的自定义物品）
RecipeViewerEvents.addEntries('item', event => {
  event.add('minecraft:stone');
});

// 隐藏指定配方（可定向类别）
RecipeViewerEvents.removeRecipes(event => {
  event.remove('minecraft:stone_from_cobblestone');
  event.removeFromCategory('minecraft:crafting', 'minecraft:stick');
});

// 隐藏整个查看器类别
RecipeViewerEvents.removeCategories(event => {
  event.remove('minecraft:crafting');
});

// 为物品附加 tooltip（JEI 注册期应用，F3+T 后更新）
RecipeViewerEvents.addInformation(event => {
  event.add('minecraft:stone', '§7This is stone.');
});
```

- 条目事件按类型定向（`'item'` / `'fluid'`，可传物品/流体 id 或对象）；配方/类别按 id 定向。
- 事件在 JEI 运行时重建（每次资源 reload）时触发，脚本需保持幂等。
- 仅在安装 JEI 时生效；REI / EMI 集成不在本次范围内。

## 内容注册（Fluid / CreativeTab）

除已有的 item / block / entityType 外，startup 脚本可注册流体与创造模式标签页（NeoForge 1.21.1 / 26.1 / 26.2）：

```js
RegistryEvents.fluid(event => {
  event.create('nekojs:molten_iron')
    .displayName('Molten Iron')   // 翻译 key，配合 lang 事件提供文本
    .density(2000)
    .viscosity(2000)
    .temperature(1500)
    .lightLevel(12);
});

RegistryEvents.creativeModeTab(event => {
  event.create('nekojs:custom')
    .title('Custom Tab')
    .icon('minecraft:iron_ingot')
    .add('minecraft:stone')
    .add('minecraft:diamond');
});
```

- 流体实现为简单流体（单一源流体、无流动、无流体方块、桶返回空气）；26.x 渲染为模型驱动，纹理通过资源包模型提供；1.21.1 的纹理亦留给资源包 / 后续扩展。
- `FluidRegistryEventJS` 用 PENDING Map 处理 `FLUID_TYPES` / `FLUID` 双 registry 分支（按 id 去重，幂等）。
- 标签页条目在注册时快照（注册后新增条目需重新注册 / 重进存档）。

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
