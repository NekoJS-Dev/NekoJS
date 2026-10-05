<!-- wiki-page: script-basics; locale: cn -->

> **中文** · [English](script-basics_us)

<a id="wiki-section-1"></a>
# 脚本基础

NekoJS 把脚本按 **脚本类型（ScriptType）** 分到不同目录，每种类型的加载时机、reload 行为、可用 API 都不同。

<a id="wiki-section-2"></a>
## 脚本类型

| 目录 | ScriptType | 用途 | 自动加载 | 重载行为 |
|---|---|---|---|---|
| `nekojs/startup_scripts/` | STARTUP | 注册物品/方块/实体类型/Goal。游戏启动时跑一次。 | 是 | 不支持命令重载；修改后需重启游戏 |
| `nekojs/server_scripts/` | SERVER | 配方修改、服务端事件监听、与世界状态交互。 | 是 | `/nekojs reload` 热重载 |
| `nekojs/client_scripts/` | CLIENT | 客户端独有：GUI、粒子、按键绑定、客户端事件。 | 是 | `/nekojs reload client` 热重载（仅 integrated client） |
| `nekojs/test_scripts/` | TEST | 显式运行的测试脚本。 | 否 | `/nekojs test` 或 `/nekojs reload test` |

<a id="wiki-section-3"></a>
## API 可见性（侧别门控）

为防止误用，每种脚本类型只能访问对应侧别的 API：

| 脚本类型 | 可用 API |
|---|---|
| STARTUP | 服务端 API + 客户端 API（启动阶段两侧都可见） |
| SERVER | 仅服务端 API |
| CLIENT | 仅客户端 API |
| TEST | 仅服务端 API |

例如在 `client_scripts` 里调用 `ServerEvents.recipes(...)` 会报错。

<a id="wiki-section-4"></a>
## 支持的文件扩展名

`.js`、`.mjs`、`.cjs`、`.ts`（可擦除 TypeScript）、`.jsx`/`.tsx`（classic runtime JSX lowering）和 `.py`（内置 Python 子集转译）。

详见 [TypeScript 与 JSX](typescript-and-jsx_cn)。

<a id="wiki-section-5"></a>
## 加载顺序

脚本按以下规则排序加载：

1. **`priority` 属性**（数字越大越先跑，默认 0）。
2. **`after` 属性**（声明依赖，详见 [脚本属性](script-properties_cn)）。
3. 同优先级按路径名排序。

```javascript
// priority: 100
// 这个脚本会比默认 priority=0 的脚本先跑
```

<a id="wiki-section-6"></a>
## Reload 行为详解

<a id="wiki-section-7"></a>
### 服务端 / 客户端 / 测试

**事务式热重载**：NekoJS 会先把脚本加载进一个候选 Context，全部成功后才替换并关闭旧 Context；如果加载失败，旧 Context 保留，服务器不会进入半坏状态。

- `/nekojs reload`（默认 reload `server`）
- `/nekojs reload server` / `client` / `test`
- `/nekojs reload server path/to/file.js`（只重载单个文件，路径相对该脚本类型根目录，支持 TAB 补全）

> 事务重载保护的是受管脚本 generation 的提交。候选失败会保留当前环境，但脚本对世界、文件或任意 Java 对象的外部副作用不一定可回滚；不要把重载当作游戏状态事务。

<a id="wiki-section-8"></a>
### 启动脚本

启动脚本只在游戏启动阶段执行。修改注册内容后必须重启游戏；当前 `/nekojs reload startup` 会拒绝执行，不能用来从运行中的命令重跑 STARTUP 脚本。

<a id="wiki-section-9"></a>
### 配方热重载

| 平台 | 配方 reload |
|---|---|
| **Cleanroom 1.12.2** | 独立 legacy 分支，当前仓库不验证配方重载与查看器刷新行为。 |
| **Fabric 26.x** | 支持 `/nekojs reload server` 重跑配方脚本；具体配方面以当前节点实现为准。 |
| **NeoForge（26.x / 1.21.1）** | 支持。`/nekojs reload server` 从缓存的 `baseJsons` 重建工作集、重跑配方脚本并替换 `RecipeManager`（服务端即时生效）；JEI/REI 配方面板可能需要客户端刷新。 |

<a id="wiki-section-10"></a>
## 全局变量与作用域

- 每个脚本文件有独立的模块作用域（ESM）或函数作用域（CJS）。
- **`global`** 是当前 `ScriptType` 内共享、并在普通 reload 后保留的状态容器。SERVER 的 `global.counter` 不会自动出现在 CLIENT 的 `global.counter` 中。
- **`shared`** 是同一个 NekoJS runtime 内显式跨脚本类型共享的状态容器。它只表示进程内存共享，不是网络同步，也不是存档持久化。

```javascript
// server_scripts/a.js
global.counter = 0
shared.message = 'server is ready'

// server_scripts/b.js（普通 reload 后仍可读）
ServerEvents.tickPost(event => {
  global.counter = (global.counter || 0) + 1
})

// client_scripts/hud.js（跨类型读取必须使用 shared）
const message = shared.message
```

详见 [全局绑定](global-bindings_cn) 的 `global` 和 `shared` 条目。

<a id="wiki-section-11"></a>
## 错误处理

- 脚本抛出的异常会被 NekoJS 捕获并记录到 `logs/nekojs/<type>.log`，不会崩溃游戏。
- 错误报告带定位：文件、行号、列号，并附上出错位置的代码片段（`>` 标记错误行、`^` 指向错误列）。语法错误（SyntaxError）同样会定位到出错行/列。
- 用 `/nekojs error` 查看当前是否有脚本错误。
- 用 `/nekojs view_all_errors` 把完整错误列表发到聊天栏。
- 出错的脚本会被标记为 disabled，不会反复报错；修好后 reload 会清除错误标记。

<a id="wiki-section-12"></a>
### 加载前静态检查（preflight）

脚本加载/重载时，NekoJS 会对源码做一轮**建议性**静态检查（不阻塞执行），结果同样进入错误面板与日志：

- 全局绑定（`Item`/`Ingredient`/`Utils` 等）与事件回调参数的**成员拼写检查**（如 `event.rec` → 提示 `recipes` 的相近成员建议）；
- 链式调用的类型流转（`Item.of('x').typo()`）、局部变量类型、未知标识符（`Util.x`、调用未声明的函数）。

检查是启发式的：动态构造的成员访问（`Utils[key]`）、`typeof x` 裸标识符等场景不会误报也不会覆盖。误报/嫌吵可在 `nekojs/config/engine.toml` 里设 `scriptMemberValidation = false` 关闭。

<a id="wiki-section-13"></a>
## 工作区与 IDE 配置

首次创建工作区时，NekoJS 会为脚本目录生成 `jsconfig.json`。执行 `/nekojs probe` 后，会生成或更新：

- `.neko_probe/typescript/` 下的 TypeScript `.d.ts`；
- 运行 `/nekojs probe python` 后的 `.neko_probe/python/nekojs/` stub 包；
- VS Code 代码片段和各 backend 管理的编辑器配置。

开服自动运行默认 TS probe 由 `probe.toml` 的 `runAtStartup` 控制，默认关闭。直接用 VS Code 打开对应脚本目录即可获得补全。详见 [Probe 类型生成](probe-type-generation_cn)。

<a id="wiki-section-14"></a>
## 下一步

- [脚本属性](script-properties_cn) —— `priority`/`modloaded`/`disable`/`after` 详解。
- [全局绑定](global-bindings_cn) —— 所有顶层 API。
- [事件参考](event-reference_cn) —— 监听游戏事件。

<!-- wiki-nav -->

---

[上一篇: 常见问题](faq_cn) · [目录](Home) · [下一篇: 脚本属性](script-properties_cn)
