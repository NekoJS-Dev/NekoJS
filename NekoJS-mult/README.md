# NekoJS

<img src="icon.png" width="256" height="256" alt="NekoJS 图标">

**用现代 JavaScript / TypeScript 写 Minecraft 脚本**

NekoJS 是一个跑在 GraalJS 上的 Minecraft 脚本运行时。整合包作者和模组开发者可以用它改配方、加内容、监听事件、写 GUI，而不必开一个 Java 模组项目。它的目标是把现代前端那套开发体验搬进 Minecraft：完整的 ES2024 语法、真正的 ESM 模块、能用的 IDE 补全和类型检查。

**运行前置：[Graal](https://www.curseforge.com/minecraft/mc-mods/graal) 25.1.3.7 或更高。** 更低版本缺少正则语言的注册，脚本里一用正则就会报错。支持的 Minecraft / NeoForge 版本以发布页面标注为准。

（部分代码由 ChatGPT / GLM 生成，看板娘图像由 ChatGPT 生成）

## 它能做什么

**语言与运行时**

- 现代 JavaScript：GraalJS 提供最新 ECMAScript 支持，不是 Rhino / Nashorn 那一套。
- TypeScript 直接跑：`.ts` 文件内置支持可擦除语法——类型标注、`type` / `interface`、泛型、`as` / `satisfies`、`import type`、`enum`、`namespace`、参数属性、类成员修饰符、`?.` / `!`、函数重载等，运行前擦除或降级。**不支持装饰器**，遇到会明确报错。
- JSX / TSX：`.jsx` / `.tsx` 默认降级为 classic runtime，支持 HTML 实体、命名空间标签 `<svg:rect/>`、泛型组件。在 `nekojs/config/engine.toml` 里设 `jsxAutomaticRuntime = true` 可切到标准 automatic runtime。
- Python 子集：`.py` 文件与 JS 从同一批脚本目录自动加载，无需外部运行时。支持 match/case、装饰器、生成器、`**kwargs`、f-string 和 source map。
- 原生 ESM：`import` / `export`、live binding、循环依赖、top-level await、`import.meta`、动态 `import()`，以及 ESM 与 CJS 互操作。
- Node.js 核心模块 shim：`fs`、`path`、`buffer`、`process`、`timers`、`util`、`events`、`assert`、`os`、`test`。
- npm 依赖：把纯 JavaScript 包放进 `nekojs/node_modules` 即可 `require`。不支持含原生 binding 的包——这不是完整的 Node 运行时。

**开发体验**

- 类型声明自动生成：内置 probe 遍历运行时目录生成声明文件，TypeScript 出 `.d.ts`、Python 出 `.pyi`。不需要装 ProbeJS 之类的外部模组就有补全和类型检查。
- 工作区自动就位：首次启动会建好脚本目录、编辑器配置（`tsconfig.json`）和 catalog 元数据。
- 拼写错误提前发现：加载时静态扫描全局绑定和事件回调的成员访问，`Utils.randmInt` 会提示 "Did you mean 'randomInt'?"。可以在 `nekojs/config/engine.toml` 里用 `scriptMemberValidation` 关掉，关掉后零开销。
- 热重载：服务端脚本 `/nekojs reload` 即可重载，配方会重新执行并整体替换配方表。注册类脚本（startup）仍需重启游戏。

**平台**

- 一棵源码树同时支持 NeoForge 26.1 / 26.2 / 1.21.1，Fabric 26.1 正在移植。Cleanroom 1.12.2 在独立的 legacy 分支维护，不在本仓库构建。

---

## 脚本目录

装上 NekoJS 首次启动后，游戏根目录下会生成 `nekojs` 文件夹：

```text
.neko_probe/            # 自动生成的类型声明，按语言分子目录（typescript/、python/）
nekojs/
├── startup_scripts/    # 启动脚本：注册物品、方块等（改动需重启游戏）
│   └── tsconfig.json   #   编辑器配置，自动关联 .neko_probe
├── server_scripts/     # 服务端脚本：配方、事件监听，支持 /nekojs reload
├── client_scripts/     # 客户端脚本：GUI、粒子、按键绑定
├── test_scripts/       # 测试脚本：只在 /nekojs test 时运行
├── node_modules/       # 纯 JS 的 npm 依赖
├── assets/             # 生成的资源包内容
├── data/               # 生成的数据包内容
└── config/             # probe.toml（类型生成配置）
```

引擎配置是 `nekojs/config/engine.toml`，与 `probe.toml` 同目录，首次启动时自动生成。旧位置 `<游戏根目录>/config/nekojs-engine.toml` 仍会被读取，但只作只读回退并打印迁移警告。

前三个目录会自动加载，`test_scripts/` 要用 `/nekojs test` 显式跑。可用的扩展名：`.js`、`.mjs`、`.cjs`、`.ts`、`.jsx`、`.tsx`、`.py`。

脚本首行可以用注释声明加载属性：`// priority: <n>` 和 `// after: <path>`。同 priority 内按 `after:` 的拓扑顺序加载；引用解析不到会告警，成环则回退到原顺序。

## 源码结构

仓库是 stonecutter 多版本多加载器单仓：一棵共享源码树，加上每个节点的参数目录。

```text
common-api/            # 数据契约与 conversion SPI（不依赖 MC / 加载器 / Graal）
common-api-processor/  # 编译期注解处理器，检查 common-api 契约的 spec 覆盖
common/                # 跨平台引擎：Graal Context、ESM/CJS 模块、probe、事件总线、插件系统
buildSrc/              # 节点构建约定（nekojs.neoforge-node / nekojs.fabric-node）
src/                   # 版本共享树，所有节点共用这一份
├── main/java/         #   版本差异用 //? if >=26 守卫和 replacements 表达
├── main/resources*/   #   AT / mixins / interface injection（26.x 与 1.21.1 分层）
├── main/templates/    #   neoforge.mods.toml 模板
└── test/java/         #   共享测试
versions/
├── 1.21.1/            # NeoForge 1.21.1 节点：节点参数 + 该节点专属源码
├── 26.1.2/            # NeoForge 26.1.2 节点
├── 26.2.0/            # NeoForge 26.2.0 节点
└── 26.1.2-fabric/     # Fabric 26.1.2 节点，与上面共用同一棵 src/
```

怎么编译、怎么写守卫、怎么加新 MC 版本，见 [wiki/构建系统](wiki/构建系统.md)。

---

## 快速开始

写一个模块（`server_scripts/utils.ts`）：

```typescript
function calculateDamage(base: number, multiplier: number): number {
    return base * multiplier;
}

const MOD_NAME: string = "NekoJS";

module.exports = { calculateDamage, MOD_NAME };
```

在主脚本里用它（`server_scripts/main.ts`）：

```typescript
const { calculateDamage, MOD_NAME } = require('./utils.ts');

console.log(`[${MOD_NAME}] 正在加载自定义逻辑...`);

ServerEvents.tickPre(event => {
    // 你的 tick 逻辑
});
```

## 编辑器类型检查

probe 生成的声明（`.neko_probe/`）已经把 `ServerEvents`、`BlockEvents` 这些全局对象和它们的事件参数类型完整暴露给编辑器，所以多数错误在事件触发前就能发现：

- 用 `.ts` 写脚本自动获得完整类型检查；已有的 `.js` 脚本在首行加 `// @ts-check` 就能逐文件启用。
- 拼写错误（比如把 `event.recipes` 写成 `event.rec`）会立刻标红，不需要 import 任何类型——`event` 的类型由全局事件对象的签名推断。
- 运行时也会拦这类错误：事件回调里访问不存在的成员或用未定义的变量，会记进错误面板，`/nekojs view_all_errors` 查看。

## Java 模块导入

NekoJS 把 Java 包和类当作 `java:` 前缀的特殊模块。ESM 会把它重写成 synthetic module，CJS 的 `require()` 直接返回 namespace 或 class proxy。

包级模块拿到的是懒加载的 namespace proxy，普通名字按属性查找，`$Class` 形式直接映射到 `Java.type()`：

```ts
import { Integer, $Integer, Math as JavaMath } from 'java:java/lang'
const { Integer, $Integer, Math: JavaMath } = require('java:java/lang')
```

类级模块直接返回 class proxy，另外暴露 `default` 和 `$Class`。只要一个明确的类时用这种写法最直接：

```ts
import IntegerClass, { $Integer } from 'java:java/lang/Integer'
const IntegerClass3 = require('java:java/lang/Integer')
```

两条限制：只接受 `java:` 前缀，只接受斜杠分隔的路径（`java:java/lang`、`java:java/lang/Integer`）。动态 `import()` 得到的是带 `default` / `namespace` 的 synthetic ESM module。类型生成器优先输出包级模块加 `$Class`，再按需补类级模块。

---

## 安全模型

脚本跑在受限的 GraalJS 环境里，但**这不是一个可以安全执行不可信代码的平台**。只运行你信任的脚本，尤其不要在公共服务器上给陌生玩家远程编辑权限。

当前的边界：

- 文件系统访问限制在游戏目录内，并检测已存在路径的符号链接逃逸。
- Java 类查找经过 `ClassFilter` 按名黑名单过滤（拦 `Java.type` 和 `java:` 模块）。默认禁止：线程、反射、ASM、进程、网络、底层 IO、AWT/Swing、RMI/JNDI、JDBC、`java.lang.Module`、Graal/Truffle 内部，以及 NekoJS 自己的 `com.tkisor.nekojs.core`。
- `allowThreads`、`allowReflection`、`allowAsm` 是高危能力开关，默认全关。
- `scriptEvaluationTimeoutSeconds`（默认 30，设 0 或负数为不限）限制脚本入口的求值时长，防止 top-level await 永不完成把服务器线程挂死。
- `scriptStatementLimit`（默认 5000 万，显式设 0 可禁用）限制单个 Context 能执行的语句总数，超限时关闭该 Context，防止死循环耗尽 CPU。
- 游戏内的工作区同步只应给可信管理员用，它本身也限制在脚本目录和脚本扩展名范围内。

**一个必须知道的边界**：按名黑名单只拦类查找。Java 方法返回值的对象图由 Graal 的 `HostAccess` 控制，当前是 `HostAccess.ALL`——也就是说黑名单里的类的实例，仍可能通过某个方法的返回值进入脚本。所以脚本应当视为**半可信代码**。

## 语言前端的定位

NekoJS 的 TypeScript、JSX、source map chain 和诊断都在本体实现，不依赖外部转译模组。方向是继续增强本体前端，而不是把高级语法外包出去。脚本语言插件 registry 仍然保留给第三方语言扩展使用。

`.jsx` / `.tsx` 用 automatic runtime 时，从 `nekojs/jsx-runtime` 导入 `jsx`、`jsxs`、`Fragment`，子节点放在 `props.children`；在 `nekojs/` 工作区里请把 runtime 模块放在裸模块路径 `node_modules/nekojs/jsx-runtime.js`。
---

## 插件开发

外部模组可以用 Java 插件给脚本加能力。插件的写法与生命周期见 [wiki/插件开发](wiki/插件开发.md)，这里只给概览。

插件通过多个 typed hook 注册能力，例如 `registerBindings`、`registerAdapters`、`registerEvents`：

```java
@RegisterNekoJSPlugin
public final class MyPlugin implements NekoJSPlugin {
    @Override
    public void registerBindings(BindingRegistry registry) {
        registry.register("MyApi", MyApi.class);
    }
}
```

`@RegisterNekoJSPlugin` 有三个参数控制加载行为：

- `priority`（默认 1000）：**数值越大越先加载**。内置核心插件用 `NekoJSPlugin.CORE_PRIORITY`（`Integer.MAX_VALUE`），确保适配器和绑定这些基础设施先就位。
- `clientOnly`（默认 false）：只在客户端进程加载，专用服务器跳过。
- `requiredMods`（默认空）：列出的模组全部在场才加载，AND 语义。

```java
@RegisterNekoJSPlugin(priority = 500, clientOnly = true, requiredMods = {"jei", "mekanism"})
public final class MyIntegrationPlugin implements NekoJSPlugin { ... }
```

如果需要**定义新的插件类型**（而不只是实现已有的），实现 `NekoPluginExtensionProvider`，在 bootstrap 第一阶段注册 extension point：

```java
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

之后任何实现 `StartupBindingsPlugin` 的插件都会被这个 extension point 收集。

bootstrap 的顺序是固定的：先扫描并实例化所有 `@RegisterNekoJSPlugin`，再收集 `NekoPluginExtensionProvider` 注册的插件类型，冻结 extension point registry，最后才执行各插件的 typed hook。所有 registry 只在收集阶段可写，bootstrap 结束后延迟注册会 fail-fast。

配方生命周期也是同一套机制：实现 `RecipeLifecyclePlugin`，或在 `registerRecipeLifecycleHooks` 里注册 `beforeRecipeLoading` / `afterRecipes`。这两个 hook 分别跑在服务端配方脚本事件前后，拿到的是受控的 `RecipeLifecycleContext`，不会暴露配方管理器的内部可变 map。

### 可替换的 probe 后端

类型生成是可插拔的：`ProbeCoordinator` 收集一次共享类型，然后派发给各个 `ProbeBackend` 分别渲染（内置 TypeScript 和 Python 两个后端）。第三方插件可以用 `ProbeBackendRegistry.register(backend, source)` 注册自己的后端——注册表在 bootstrap 时锁定，id 冲突会 fail-fast。

插件也可以通过 `probe.assign_type` / `probe.modify_type` / `probe.add_global` / `probe.snippets` 事件定制生成的类型和代码补全，或用 editor-config contributor 合并编辑器配置。

手动触发生成：`/nekojs probe`。无参只跑 TypeScript 后端；子命令有 `all`（跑全部）、`list`（列出后端）、`reload`（重读配置）、`enable` / `disable`（持久化开关）、`<language> [name]`（指定语言或后端）。配置文件是 `nekojs/config/probe.toml`。

---

## 事件系统

以 NeoForge 26.x 为准，一共 16 个事件组。下表只列每组的代表性事件，**权威签名以 probe 生成的 `.neko_probe/@side-only/<type>/events/index.d.ts` 为准**——事件组还在持续扩充。

```text
ServerEvents        约 13 个   tickPre / tickPost / recipes / afterRecipes / tags ...
PlayerEvents        约 17 个   loggedIn / loggedOut / chat / tickPre / tickPost / cloned /
                              respawned / changedDimension / advancement / container* /
                              inventory* / entityInteract / crafted / smelted / destroyed
EntityEvents        约 13 个   damagePre / damagePost / death ...
BlockEvents         约 11 个   broken / rightClicked / placed ...
ItemEvents           约 8 个   rightClicked / tooltip / crafted ...
RegistryEvents      约 12 个   item / block / entityType / fluid / creativeModeTab /
                              soundEvent / mobEffect / potion / particleType /
                              paintingVariant / villagerType / enchantment
CommandEvents        约 2 个   register ...
GoalEvents           约 1 个
LevelEvents         约 10 个   loaded / unloaded / tick ...
NetworkEvents        约 2 个
CapabilityEvents     约 1 个   register
RecipeViewerEvents   约 5 个   addEntries / removeEntries / removeRecipes /
                              removeCategories / addInformation（客户端，需 JEI）
ProbeEvents          约 4 个   modifyType / assignType / addGlobal / snippets
ClientEvents        约 13 个
ScriptEvents / KeyBindEvents
```

事件按运行时机分四类：普通事件（全局监听）、目标事件（带实体/方块/物品目标）、启动时事件（游戏启动触发一次）、服务器事件（存档加载时运行，支持热重载）。

```typescript
ServerEvents.tickPre(event => {
    console.log('服务器 tick 开始');
});

PlayerEvents.loggedIn(event => {
    console.log(`玩家 ${event.player.name} 已登录`);
});

EntityEvents.damagePre(event => {
    console.log(`实体 ${event.entity.type} 即将受到 ${event.damage} 点伤害`);
});
```

### 自定义事件

在 startup 脚本里用 `ScriptEvents` 声明自己的 server / client 事件，载荷由触发方自己传：

```js
// startup_scripts/src/events.js
ScriptEvents.server(event => event.register('MyEvents', 'bossKilled'))
ScriptEvents.client(event => event.register('MyClientEvents', 'hudRefresh'))
// 对象形式：event.register({ group: 'MyEvents', name: 'bossKilled' })
```

然后在对应环境监听和触发：

```js
// server_scripts/src/main.js
MyEvents.bossKilled(payload => console.info(`boss killed: ${payload.boss}`))
MyEvents.bossKilled.post({ boss: 'ender_dragon' })
```

自定义事件不进插件 bootstrap 的静态事件表，但会进 probe 的事件目录和类型生成。startup reload 会刷新事件定义，server / client reload 会清掉对应脚本的 listener，不会重复回调。

要按类名监听 NeoForge 原生事件，用 `NativeEvents.onEvent(...)`。

---

## 生成数据包与资源包

脚本可以在资源 reload 时生成 datapack / 资源包 JSON——战利品表、进度、模型、语言文件等——写进 `nekojs/data` 与 `nekojs/assets`。这两个目录已注册为最高优先级的 datapack / resource pack，内容懒读以保证 reload 时序正确。

```js
ServerEvents.generateData('after_mods', event => {
  event.json('minecraft:loot_tables/blocks/stone.json', { type: 'minecraft:block', pools: [] });
  event.text('minecraft:nekojs/hello.txt', 'content');
});

ClientEvents.generateAssets('after_mods', event => {
  event.json('minecraft:models/block/foo.json', { parent: 'minecraft:block/cube_all' });
});

ClientEvents.lang('en_us', event => {
  event.add('minecraft:item.foo', 'Foo Item');
});
```

- `generateData` / `generateAssets` 按阶段定向，目前只有 `after_mods`。`lang` 按语言代码定向，条目合并写入 `lang/<lang>.json`，已有条目保留。
- 每次服务端 reload 或客户端 F3+T 都会重新触发，**脚本必须幂等**——重复写入会覆盖。
- 外部模组可以用 `NekoJSPlugin.generateData` / `generateAssets` / `generateLang` 钩子生成数据，它们先于脚本事件触发。

## 数据驱动的配方方法

不用写 Java 就能给 `event.recipes.<namespace>.<type>(...)` 加轻量方法定义，放在数据包里：

```text
data/<namespace>/nekojs/recipe_types/<type>.json
```

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

脚本侧就能写：

```js
event.recipes.create.mixing('create:brass_ingot', ['minecraft:copper_ingot', 'create:zinc_ingot'])
```

这只是 JSON 层面的轻量 facade：字段按 JSON path 写入，`kind` 负责把脚本值转成 datapack JSON。没有定义的 namespace / type 仍然可以走 raw JSON 兜底。

## JEI 集成

NeoForge 平台内置 JEI 集成，在客户端脚本里监听（接口向 KubeJS 的 RecipeViewerEvents 对齐，是裁剪版）：

```js
// 从 JEI 隐藏物品（不是真的移除）
RecipeViewerEvents.removeEntries('item', event => event.add('minecraft:stone'));

// 向 JEI 添加条目，比如脚本生成的自定义物品
RecipeViewerEvents.addEntries('item', event => event.add('minecraft:stone'));

// 隐藏配方，可以定向到类别
RecipeViewerEvents.removeRecipes(event => {
  event.remove('minecraft:stone_from_cobblestone');
  event.removeFromCategory('minecraft:crafting', 'minecraft:stick');
});

// 隐藏整个类别
RecipeViewerEvents.removeCategories(event => event.remove('minecraft:crafting'));

// 给物品附加 tooltip（JEI 注册期应用，F3+T 后更新）
RecipeViewerEvents.addInformation(event => event.add('minecraft:stone', '§7This is stone.'));
```

条目事件按类型定向（`'item'` / `'fluid'`），配方和类别按 id 定向。事件在 JEI 每次重建运行时触发，所以脚本要幂等。只在装了 JEI 时生效；REI / EMI 暂不支持。

## 注册流体与创造模式标签页

startup 脚本除了 item / block / entityType，还能注册流体和创造标签页：

```js
RegistryEvents.fluid(event => {
  event.create('nekojs:molten_iron')
    .displayName('Molten Iron')   // 翻译 key，文本用 lang 事件提供
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

- 流体是简单流体：单一源流体，不流动，没有流体方块，桶返回空气。26.x 的渲染由模型驱动，纹理需要资源包提供。
- 标签页的条目在注册时快照，之后新增条目要重新注册或重进存档。

---

## 参与贡献

NekoJS 在活跃开发中。报 Bug、提功能建议、发 Pull Request 都欢迎。

* **QQ 群**：1158525822 —— [点击加入群聊【NekoJS 魔改交流群（？】](https://qm.qq.com/q/rbryak0K6k)

## License

本项目采用 [LGPL-3.0 License](LICENSE) 开源。
