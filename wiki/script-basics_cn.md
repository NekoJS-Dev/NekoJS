<!-- wiki-page: script-basics; locale: cn -->

> **中文** · [English](script-basics_us)

<a id="wiki-section-1"></a>
# 脚本基础

本页讲清楚三件事：脚本应该放在哪个目录、改完怎么让它生效、出错了怎么查。

<a id="wiki-section-2"></a>
## 四种脚本

NekoJS 按目录区分脚本。不同目录的脚本在不同时机运行，能用的 API 也不同。

| 目录 | 什么时候运行 | 用来做什么 | 改完怎么生效 |
|---|---|---|---|
| `startup_scripts/` | 游戏启动时，只运行一次 | 注册新物品、方块、实体 | **重启游戏** |
| `server_scripts/` | 进入世界时 | 改配方、监听玩家和世界事件、自定义命令 | `/nekojs reload` |
| `client_scripts/` | 游戏启动时（仅客户端） | HUD、按键、界面、物品提示 | `/nekojs reload client` |
| `test_scripts/` | 只在你手动执行时 | 写测试，检查脚本行为是否正确 | `/nekojs test` |

简单判断：

- 要**添加新东西**（物品、方块） → `startup_scripts`
- 要**改游戏规则或数据**（配方、事件） → `server_scripts`
- 要**改画面或操作** → `client_scripts`

<a id="wiki-section-3"></a>
## 每种脚本能用哪些 API

服务端脚本只能用服务端的 API，客户端脚本只能用客户端的 API。用错会直接报错，例如在 `client_scripts` 里写 `ServerEvents.recipes(...)`。

| 脚本 | 能用的 API |
|---|---|
| 启动脚本 | 服务端和客户端都能用 |
| 服务端脚本 | 只有服务端 |
| 客户端脚本 | 只有客户端 |
| 测试脚本 | 只有服务端 |

每个事件和绑定能在哪种脚本里用，[事件参考](event-reference_cn) 和 [全局绑定](global-bindings_cn) 里都有标注。

<a id="wiki-section-4"></a>
## 文件类型

| 扩展名 | 说明 |
|---|---|
| `.js` | 普通 JavaScript，推荐新手使用 |
| `.mjs` / `.cjs` | 明确指定为 ES 模块 / CommonJS 模块 |
| `.ts` | TypeScript，只去掉类型标注后运行，不做类型检查 |
| `.jsx` / `.tsx` | 带 JSX 语法的 JavaScript / TypeScript |
| `.py` | NekoJS 支持的 Python 子集，不需要安装 Python |

详见 [TypeScript 与 JSX](typescript-and-jsx_cn)、[Python 脚本](python-scripts_cn)。

<a id="wiki-section-5"></a>
## 让改动生效

保存文件**不会**自动生效。改完后按脚本类型执行对应操作：

| 改了什么 | 怎么做 |
|---|---|
| 服务端脚本 | `/nekojs reload`（等同 `/nekojs reload server`） |
| 客户端脚本 | `/nekojs reload client`（NeoForge 上按 F3+T 也会重载） |
| 测试脚本 | `/nekojs test` |
| 启动脚本 | 重启游戏。`/nekojs reload startup` 会被拒绝 |

只想重跑某一个文件时，可以写 `/nekojs reload server recipes.js`（路径相对于 `server_scripts`，按 TAB 可补全）。日常开发建议直接重载整个类型，结果更可靠。

<a id="wiki-section-6"></a>
### reload 失败时会怎样

重载整个类型（例如 `/nekojs reload`）时，如果新脚本里有任何一个报错，**这次重载会整体放弃，游戏继续使用上一次成功加载的脚本**。所以写错了不会让游戏陷入“一半新、一半旧”的状态，修好后再 reload 即可。

但要注意：脚本已经做过的事情不会被撤销。比如脚本在报错之前已经写了文件、给玩家发了物品，这些改动会保留。

<a id="wiki-section-7"></a>
### 配方查看器没刷新

`/nekojs reload` 之后配方会立刻在服务端生效，可以直接合成验证。JEI、REI 等配方查看器有时还显示旧配方，重新进入世界即可。

<a id="wiki-section-8"></a>
## 加载顺序

同一类型的脚本默认按文件路径顺序加载。需要控制顺序时，在文件**最开头**加注释：

```javascript
// priority: 100
```

数字越大越先加载，默认是 0。如果需要“某个文件在另一个文件之后加载”，用 `// after: lib/init.js`。

这些注释必须写在文件开头，写在代码后面不会生效，也不会报错。所有可用的注释见 [脚本属性](script-properties_cn)。

<a id="wiki-section-9"></a>
## 在脚本之间共享数据

每个脚本文件有自己独立的作用域，在一个文件里定义的变量，另一个文件看不到。需要共享时：

- **`global`**：同一类型的脚本之间共享。例如所有服务端脚本共用一个 `global`。
- **`shared`**：所有类型的脚本之间共享。例如服务端脚本写入，客户端脚本读取（仅单人游戏，同一个游戏进程内有效）。

```javascript
// server_scripts/a.js
global.counter = global.counter ?? 0

// server_scripts/b.js
ServerEvents.tickPost(event => {
  global.counter++
})
```

`global` 和 `shared` 的内容在 reload 后保留，但**关闭游戏后就没了**。也不能用它们在服务器和多人游戏的客户端之间传数据。

需要保存到硬盘、同步给客户端，或者存到玩家和物品身上，请看 [数据与状态](data-and-state_cn)。

<a id="wiki-section-10"></a>
## 出错了怎么查

脚本报错不会让游戏崩溃。按这个顺序排查：

1. 执行 `/nekojs error`，看有没有报错。
2. 执行 `/nekojs view_all_errors`，查看完整的错误列表（NeoForge 会打开错误面板）。
3. 打开 `logs/nekojs/<类型>.log`，例如 `logs/nekojs/server.log`。

错误信息会写明出错的文件、行号和列号，并附上出错的那行代码：

```text
> 12 |   event.shapd('minecraft:stone', ...)
     |         ^
```

出错之后会怎样，取决于脚本类型：

- **服务端、客户端、测试脚本**：重载时只要有一个文件报错，这次重载的所有改动都不生效，继续使用上一次成功加载的版本（见上文“reload 失败时会怎样”）。修好错误再 reload 即可。
- **启动脚本**：出错的文件被跳过，其他文件照常运行。
- **事件回调里的报错**（比如 `ServerEvents.recipes(event => { ... })` 里面的代码出错）：只记录这一次错误，脚本和其他事件照常工作。

<a id="wiki-section-11"></a>
### 加载前的拼写检查

NekoJS 在加载脚本前会做一次拼写检查。例如你写了 `event.rec`，它会提示“你是不是想写 `recipes`”。这些只是提示，不会阻止脚本运行。

如果提示太多或有误报，可以在 `nekojs/config/engine.toml` 里设置 `scriptMemberValidation = false` 关闭。

<a id="wiki-section-12"></a>
### 小心死循环

NekoJS 不会强行中断一直运行不结束的脚本。如果脚本里有死循环，或者 `await` 了一个永远不会完成的 Promise，游戏会卡住。在 `tick` 之类频繁触发的事件里，也要避免做太重的工作。

<a id="wiki-section-13"></a>
## 接下来

- [脚本属性](script-properties_cn)：`priority`、`modloaded`、`disable`、`after` 的完整用法。
- [命令](commands_cn)：所有 `/nekojs` 命令。
- [全局绑定](global-bindings_cn)：可以直接使用的全部工具。

<!-- wiki-nav -->

---

[上一篇: 常见问题](faq_cn) · [目录](Home) · [下一篇: 脚本 API 约定](script-api-style_cn)
