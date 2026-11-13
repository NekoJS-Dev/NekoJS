<!-- wiki-page: script-basics; locale: cn -->

> **中文** · [English](script-basics_us)

# 脚本基础

写 NekoJS 脚本前，先搞清三件事：**脚本该放进哪个文件夹、改完怎么让它生效、写错了去哪看报错**。

## 1. 四种脚本目录与分工

NekoJS 根据文件所在的目录决定脚本在什么时机运行、能调用哪些 API：

| 目录 | 运行时机 | 主要用途 | 能用的 API 范围 | 改完怎么生效 |
|---|---|---|---|---|
| `startup_scripts/` | 游戏启动时（仅运行一次） | 注册新物品、方块、实体、能力、生物 AI | 服务端 + 客户端（专用服无客户端 API） | **必须重启游戏** |
| `server_scripts/` | 进入存档或服务端启动时 | 改配方、改标签/掉落表、监听玩家与世界事件、注册命令 | 仅服务端 API | `/nekojs reload` |
| `client_scripts/` | 客户端启动时（专用服不跑） | 画 HUD、写 JSX 界面、自定义按键、改物品悬浮提示 | 仅客户端 API | `/nekojs reload client` |
| `test_scripts/` | 手动执行测试命令时 | 编写自动化断言，验证配方或脚本逻辑是否符合预期 | 仅服务端 API | `/nekojs test` |

**不确定放哪？按这个直觉选：**
- 要**无中生有加新东西**（新物品、新方块） 👉 放 `startup_scripts/`
- 要**改玩法规则或服务端数据**（配方、掉落、右键事件） 👉 放 `server_scripts/`
- 要**改画面显示或键鼠操作**（HUD、弹窗、快捷键） 👉 放 `client_scripts/`

> 跨端调错 API 会直接报错——比如在 `client_scripts/` 里写 `ServerEvents.recipes(...)` 是跑不通的。每个事件和工具属于哪一端，见 [事件参考](event-reference_cn) 和 [全局绑定](global-bindings_cn)。

## 2. 支持的脚本文件后缀

日常写脚本直接用 `.js` 即可；如果你习惯类型提示、JSX 界面或 Python，也可以混用其他后缀：

| 扩展名 | 说明 |
|---|---|
| `.js` | 标准 JavaScript（新手首选） |
| `.mjs` / `.cjs` | 显式声明为 ES Module / CommonJS 模块（详见 [模块系统](module-system_cn)） |
| `.ts` | TypeScript（加载时自动擦除类型标注直接运行，不在游戏内做类型检查） |
| `.jsx` / `.tsx` | 带 JSX 语法的脚本，常用于写客户端界面（详见 [TypeScript 与 JSX](typescript-and-jsx_cn)） |
| `.py` | NekoJS 内置支持的 Python 子集，电脑无需安装 Python（详见 [Python 脚本](python-scripts_cn)） |

## 3. 让修改生效（热重载规则）

在编辑器里保存文件后，游戏**不会**自动刷新，需要手动触发对应重载：

- **服务端脚本**：执行 `/nekojs reload`（等价于 `/nekojs reload server`）。
- **客户端脚本**：执行 `/nekojs reload client`（在 NeoForge 上按原版重载资源的 `F3 + T` 也会顺带重载客户端脚本）。
- **测试脚本**：执行 `/nekojs test`。
- **启动脚本**：**只能重启游戏**（执行 `/nekojs reload startup` 会被直接拒绝）。

> **单文件重载提示**：调试时虽然可以写 `/nekojs reload server recipes.js`（按 Tab 可补全相对路径）只重跑单个文件，但**日常强烈建议直接整类重载**，避免多文件之间的状态不一致。

### 重载报错时会发生什么？

当你整类重载（如 `/nekojs reload`）时，如果新改的脚本里有任意一个文件语法错误或顶层抛错，**本次重载会整体作废，游戏继续保留上一次正常工作的脚本**，不会变成“一半新、一半旧”的残废状态。修好报错再重新 reload 即可。

> 注意：如果脚本在抛错**之前**已经执行了外部副作用（例如调用 `JsonIO` 写了磁盘文件、或已经往玩家背包塞了物品），这些已经发生的操作不会自动撤销。

### 为什么改了配方，JEI / REI 里没变？

执行 `/nekojs reload` 后，新配方在服务端**立刻就能合成生效**。但 JEI、REI 等客户端配方查看器有时不会立刻重刷缓存，退出重进一次存档就能看到最新列表了。

## 4. 控制加载顺序与跨文件传值

### 调整文件加载顺序

同一目录下的脚本默认按文件路径字母顺序加载。如果你想让某个工具库或前置脚本先跑，在文件的**最顶部第一行**加上属性注释：

```javascript
// priority: 100
```

- `priority` 数字越大越先加载（默认为 `0`，支持负数）。
- 如果只需要排在某个特定文件后面，写 `// after: lib/init.js`。
- ⚠️ 这类注释**必须写在文件最开头的代码之前**；写在代码中间不会生效，也不会报错提醒。完整属性列表见 [脚本属性](script-properties_cn)。

### 在不同文件之间共享变量（`global` 与 `shared`）

每个脚本文件都有独立的作用域，顶层 `const` / `let` 定义的变量不会污染其他文件。需要跨文件读写内存数据时：

- **`global`**：在**同一种脚本类型**内部共享（例如所有 `server_scripts/` 共用一个 `global`，所有 `client_scripts/` 共用另一个 `global`，两边互不可见）。
- **`shared`**：在**同一个游戏进程内的所有脚本类型**之间共享（仅单人游戏能让服务端脚本写、客户端脚本读；多人联机时服务端与玩家客户端不在同一台机器进程上，绝不能用 `shared` 传数据）。

```javascript
// server_scripts/a.js
global.counter = global.counter ?? 0

// server_scripts/b.js
ServerEvents.tickPost(event => {
  global.counter++
})
```

> `global` 和 `shared` 里的基础值在 reload 后依然保留，但**关掉游戏就没了**。需要存进存档、写本地文件或联机同步，请看 [数据、状态与持久化](data-and-state_cn)。

## 5. 脚本出错了怎么查？

脚本写错不会直接崩掉整个游戏。遇到“没反应”或报错提示时，按这三步定位：

1. 游戏内执行 `/nekojs error`，快速查看当前有没有报错摘要。
2. 执行 `/nekojs view_all_errors` 查看完整错误堆栈（NeoForge 上会直接弹出可视化错误面板）。
3. 打开游戏目录下的专用日志 `logs/nekojs/<类型>.log`（分为 `startup.log`、`server.log`、`client.log`、`test.log`）。

日志会精确标出出错的文件、行号、列号以及对应的代码行：

```text
> 12 |   event.shapd('minecraft:stone', ...)
     |         ^
```

**不同位置的报错影响范围不同：**
- **服务端 / 客户端 / 测试脚本顶层报错**：整次 reload 放弃，回退到上一次成功的版本。
- **启动脚本（`startup_scripts`）报错**：出错的那个文件被跳过，其余启动脚本继续执行。
- **事件回调内部报错**（例如玩家右键方块时触发的回调里抛错）：只记录当次错误日志，不影响脚本其余部分和后续事件触发。

### 加载前的拼写提示与死循环防范

- **成员拼写预检**：NekoJS 在加载脚本前会扫描常见的 API 调用拼写（比如把 `recipes` 写成 `event.rec` 时会提示“你是不是想写 `recipes`”）。如果遇到误报或觉得提示太吵，可在 `nekojs/config/engine.toml` 中设 `scriptMemberValidation = false` 关闭。
- **严防死循环与阻塞**：NekoJS 不会强行杀死一直不退出的同步死循环。如果你写了 `while (true)` 死循环，或者 `await` 了一个永远不会 resolve 的 Promise，主线程会被直接卡死；在 `tick` 等每秒运行 20 次的高频事件里也请保持逻辑轻量。

## 6. 下一步看什么

- [脚本属性](script-properties_cn)：`priority`、`modloaded`、`disable`、`after` 的全部用法。
- [脚本 API 约定](script-api-style_cn)：事件、绑定、Builder 与读写属性的设计规律。
- [全局绑定](global-bindings_cn)：`Item`、`Text`、`JsonIO` 等开箱即用的全局工具。
- [命令](commands_cn)：所有 `/nekojs` 调试与管理命令。
