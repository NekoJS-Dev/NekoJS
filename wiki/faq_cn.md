<!-- wiki-page: faq; locale: cn -->

> **中文** · [English](faq_us)

<a id="wiki-section-1"></a>
# 常见问题

<a id="wiki-section-2"></a>
## 安装与启动

<a id="wiki-section-3"></a>
### Q: 启动游戏崩溃，提示找不到 GraalJS / polyglot

A: 没装或装错版本的 **Graal** 前置 mod。NekoJS 依赖 Graal 提供 GraalJS 运行时。去 [Graal 的 CurseForge 页面](https://www.curseforge.com/minecraft/mc-mods/graal) 下载与你 MC / 加载器版本**完全匹配**的版本。至少要 Graal 25.1.3.7。

更低的版本还有一个不太明显的坑：它没有注册 TRegex 正则语言，脚本里一用正则（`/…/`、`String.replace(/…/)`、`RegExp`）就会抛 `No language for id regex found`。如果你看到这条报错，先确认 Graal 版本。

<a id="wiki-section-4"></a>
### Q: `nekojs/` 文件夹没生成

A: 首次启动后会在游戏根目录生成。如果没生成：
1. 确认 NekoJS 和 Graal 都正确加载了（看 `mods/` 和启动日志）。
2. 检查游戏目录权限。
3. 试试手动运行 `/nekojs probe`，它会触发工作区生成。

<a id="wiki-section-5"></a>
### Q: 支持哪些 MC 版本？

A: 见 [平台与兼容性](platform-compatibility_cn)。当前仓库包含 NeoForge 1.21.1、26.1.2、26.2.0，以及 Fabric 26.1.2、26.2.0 的构建节点；Fabric API 是子集，Cleanroom 属于独立 legacy 分支。

<a id="wiki-section-6"></a>
## 脚本运行

<a id="wiki-section-7"></a>
### Q: 我的脚本没生效

A: 逐项排查：
1. 文件放对目录了么？（`startup_scripts` / `server_scripts` / `client_scripts` / `test_scripts`）。见 [脚本基础](script-basics_cn)。
2. 扩展名对么？（`.js`/`.mjs`/`.cjs`/`.ts`/`.jsx`/`.tsx`/`.py`）。
3. 脚本被 `// disable:` 禁用了么？见 [脚本属性](script-properties_cn)。
4. `// modloaded:` 列的 mod 都装了么？
5. 服务端/客户端脚本改了有没有 `/nekojs reload`？
6. 启动脚本注册的内容（新物品/方块）改了**重启游戏**了么？
7. 跑 `/nekojs error` 看有没有错误。

<a id="wiki-section-8"></a>
### Q: 报错 `事件X is not defined` / `XXX is not a function`

A: 多半是脚本类型不对或事件名拼错：
- 服务端事件（如 `ServerEvents.recipes`）必须在 `server_scripts/` 里写。
- 客户端事件（如 `ClientEvents.tick`）必须在 `client_scripts/`。
- 事件名查 [事件参考](event-reference_cn)，绑定名查 [全局绑定](global-bindings_cn)。

<a id="wiki-section-9"></a>
### Q: reload 后旧代码还在跑 / 行为没变

A:
- **共享状态**：`global` 只在当前脚本类型内共享，普通 reload 后仍保留；跨 SERVER/CLIENT 等脚本类型共享请使用 `shared`。两者都只存在于同一 NekoJS runtime 进程内，不是网络同步。
- **事件监听器**：reload 会清除旧监听器，但如果之前注册时出了问题，监听器可能没清干净。再 reload 一次。
- **配方**：NeoForge 和 Fabric 26.x 都支持 `/nekojs reload server` 处理配方脚本；NeoForge 的 JEI/REI 配方面板可能需要客户端刷新。Cleanroom 的行为属于独立 legacy 分支，当前仓库不验证。详见 [命令](commands_cn)。
- **注册表（startup）**：启动脚本注册的内容 reload 不会真正加进游戏，需重启。

<a id="wiki-section-10"></a>
### Q: 顶层 await / dynamic import 报错

A: 确认你的文件被当 ESM 处理：
- 用 `.mjs` 扩展名最稳妥。
- 或确认 `nekojs/config/engine.toml` 里 `enableEsmAuthoring = true`（默认开）。
- 顶层 await 只能在 ESM 模块里用，不能在 CJS（`.cjs`）里用。

<a id="wiki-section-11"></a>
## TypeScript / JSX

<a id="wiki-section-12"></a>
### Q: 我的 `enum` / `namespace` 报错

A: NekoJS 的 TS 前端**已支持** `enum`/`const enum`（编译为运行时对象）和 `namespace`/`module`（编译为 IIFE）。如果遇到具体语法不支持，请提交 issue。

<a id="wiki-section-13"></a>
### Q: `.ts` 文件没有类型检查

A: TS 前端只擦除不检查。检查靠 IDE（VS Code）配合 `.neko_probe` 类型声明。跑 `/nekojs probe` 刷新类型声明。`.js` 文件可在顶部加 `// @ts-check` 开启检查。

<a id="wiki-section-14"></a>
### Q: 如何使用 automatic JSX runtime？

A: 在 `nekojs/config/engine.toml` 设置 `jsxAutomaticRuntime = true`。NekoJS 会在生成工作区和运行 probe 时，把各脚本目录的 `jsconfig.json` 更新为 `"jsx": "react-jsx"` 与 `"jsxImportSource": "nekojs"`，并移除 classic factory 配置。runtime `nekojs/jsx-runtime` 已内置，不需要创建 `node_modules/nekojs/jsx-runtime.js`。语法和配置详见 [TypeScript 与 JSX](typescript-and-jsx_cn)；要创建 Minecraft Screen，详见 [JSX 客户端 UI](jsx-client-ui_cn)。

配置由 NekoJS 的 workspace/probe 流程维护。手动修改 JSX 相关键后，下次生成或 probe 可能会恢复为与 `engine.toml` 一致的值；不要同时保留 `jsxFactory` / `jsxFragmentFactory` 和 automatic runtime 配置。

<a id="wiki-section-15"></a>
## Python

<a id="wiki-section-16"></a>
### Q: Python 脚本能运行，为什么没有 NekoJS 类型提示？

A: 运行时全局绑定与编辑器类型分析是两回事。先执行 `/nekojs probe python`，再在使用 NekoJS API 的文件顶部写：

```python
from nekojs import *
```

也可以写具名导入，例如 `from nekojs import Item, ServerEvents`。这些导入由转译器剥离，只用于让编辑器解析 Python stub。无参 `/nekojs probe` 不生成 Python stub。

如果仍然没有提示，确认 VS Code 已启用 Pylance、以游戏目录或脚本目录为工作区打开，以及配置中的 `extraPaths` 指向 `.neko_probe/python`。不需要 pip 安装 NekoJS，也不要用 `import nekojs` 代替这行特殊导入。详见 [Python 脚本](python-scripts_cn) 与 [Probe 类型生成](probe-type-generation_cn)。

<a id="wiki-section-17"></a>
## 配方

<a id="wiki-section-18"></a>
### Q: 我加了配方但配方面板（JEI/HEI）里没有

A:
- **NeoForge**：`/nekojs reload` 会重跑配方脚本并从缓存重建配方（服务端合成逻辑即时生效）；JEI/REI 配方面板可能需要客户端刷新（如重进世界或面板刷新）。详见 [命令](commands_cn)。
- **Cleanroom 1.12.2**：支持完整热重载，reload 后会自动刷新 HEI/JEI。如果没刷新，确认你装的是 HEI（`had-enough-items`）而不是别的。

<a id="wiki-section-19"></a>
### Q: `event.recipes.create.mixing(...)` 报错

A: 这个类型没有 handler 也没有 schema。要么：
1. 用原始 JSON：`event.custom({ type: 'create:mixing', ... })`。
2. 给它写一个数据驱动 schema JSON（见 [配方系统 - 数据驱动 Schema](recipe-system_cn#wiki-section-13)），之后就能用位置参数了。

<a id="wiki-section-20"></a>
### Q: 配方 id 是什么？怎么指定？

A: 每个配方有唯一 id（如 `minecraft:stone`）。`.id('yournamespace:name')` 指定；不指定则自动生成 `nekojs:<prefix>_<hash>`。用 `event.ids()` 列所有 id，用 `event.get(id)` 按 id 取，用 `event.removeById(id)` 按 id 删。

<a id="wiki-section-21"></a>
## 注册内容

<a id="wiki-section-22"></a>
### Q: 我注册的物品没有材质 / 显示紫黑方块

A: 缺资源文件。注册的物品默认按原版资源路径解析：
- 材质：`nekojs/assets/<mod>/textures/item/<name>.png`
- 模型：`nekojs/assets/<mod>/models/item/<name>.json`

在 `nekojs/assets/` 下放对应文件即可。详见 [注册新内容 - 资源与本地化](registering-new-content_cn#wiki-section-14)。

<a id="wiki-section-23"></a>
### Q: 注册的物品名字显示为 `item.mymod.xxx`

A: 缺语言文件。在 `nekojs/assets/<mod>/lang/zh_cn.json` 加：
```json
{ "item.mymod.xxx": "中文名" }
```

<a id="wiki-section-24"></a>
### Q: reload startup 后我加的物品没出现

A: 启动脚本在游戏启动阶段执行。`/nekojs reload startup` 当前会拒绝执行；修改物品、方块等注册内容后必须重启游戏。详见 [脚本基础](script-basics_cn)。

<a id="wiki-section-25"></a>
## 模块 / npm

<a id="wiki-section-26"></a>
### Q: `require('xxx')` 报错 `module not found`

A:
1. 包放进 `nekojs/node_modules/<包名>/` 了吗？
2. 包是**纯 JS** 的吗？原生 bindings（C/C++ 编译）**不支持**。
3. 路径对吗？`require('./xxx')` 相对当前文件；`require('xxx')` 走 node_modules。

<a id="wiki-section-27"></a>
### Q: 我想用某个 npm 包但它是原生 bindings

A: 不支持。换纯 JS 的替代品，或自己用 `java:` 导入 Java 等价物实现。

<a id="wiki-section-28"></a>
### Q: `java:java/lang/Integer` 这种导入语法对吗？

A: 对。规则：`java:` 前缀 + 斜杠分隔路径。详见 [模块系统 - 导入 Java 类](module-system_cn)。

<a id="wiki-section-29"></a>
## IDE / 类型提示

<a id="wiki-section-30"></a>
### Q: VS Code 没有补全

A:
1. 跑 `/nekojs probe` 生成 `.neko_probe/`。
2. 用 VS Code **打开 `nekojs/server_scripts/`（或对应目录）作为根**，而不是整个游戏目录。这样 `jsconfig.json` 才能正确关联 `.neko_probe`。
3. 确认 `.neko_probe/` 在游戏根目录（与 `nekojs/` 同级）。

<a id="wiki-section-31"></a>
### Q: 补全的类型和实际运行不一致

A: 跑 `/nekojs probe` 重新生成。如果你是新加了 mod 或注册了新内容，类型声明会过期。这是已知限制（probe 是手动触发的）。

<a id="wiki-section-32"></a>
## 性能

<a id="wiki-section-33"></a>
### Q: 我的 tick 事件很卡

A: `ServerEvents.tickPre`/`tickPost` 每 tick（50ms）都跑。脚本里别做重活（大量文件 IO、复杂循环）。耗时操作考虑缓存、节流、或用 `setTimeout`/计数器降频。

<a id="wiki-section-34"></a>
### Q: 脚本加载很慢

A: NekoJS 用 GraalJS，首次加载有预热成本。运行起来后很快。如果持续慢，检查是不是在加载大量 npm 依赖或在顶层做了重 IO。

<a id="wiki-section-35"></a>
## 安全

<a id="wiki-section-36"></a>
### Q: 我的脚本访问文件被拒

A: 沙盒限制——只能访问游戏目录内。详见 [Node.js 兼容 - 访问范围限制](nodejs-compatibility_cn#wiki-section-9)。如需放宽（仅单人/可信环境），看 `nekojs/config/engine.toml`。

<a id="wiki-section-37"></a>
### Q: 多人服务器用 NekoJS 安全吗？

A: 脚本应视为**半可信代码**——只运行你信任的脚本。沙盒限制了 Java 类访问和文件范围，但按名黑名单只拦类查找：Java 方法返回值的对象图由 Graal 的 `HostAccess` 控制（当前是 `HostAccess.ALL`），黑名单里的类的实例仍可能通过某个方法的返回值进入脚本。内置编辑器的客户端脚本上传已移除；不要把只读错误 UI 或脚本网络事件当作远程编辑服务。服务器脚本仍须由可信维护者部署。

<a id="wiki-section-38"></a>
## 还没解决？

- 查 [事件参考](event-reference_cn)、[全局绑定](global-bindings_cn)、[配方系统](recipe-system_cn) 确认 API 用法。
- 提 issue（见仓库 README 链接）。

<!-- wiki-nav -->

---

[上一篇: 快速开始](quick-start_cn) · [目录](Home) · [下一篇: 脚本基础](script-basics_cn)
