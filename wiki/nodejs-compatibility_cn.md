<!-- wiki-page: nodejs-compatibility; locale: cn -->

> **中文** · [English](nodejs-compatibility_us)

<a id="wiki-section-1"></a>
# Node.js 兼容

NekoJS 内置一组 Node.js 核心模块 shim，让你能 `require`/`import` 常用的 Node API。**这是 shim，不是完整 Node.js 运行时**。

<a id="wiki-section-2"></a>
## 可用模块

| 模块 | 说明 |
|---|---|
| `node:buffer` / `buffer` | `Buffer`（通过 `__nekoNodeBuffer` 包装） |
| `node:fs` / `fs` | 同步 + 回调 + promises API |
| `node:path` / `path` | 路径处理 |
| `node:util` / `util` | 工具函数 |
| `node:assert` / `assert` | 断言（`node:assert/strict` 也可） |
| `node:test` / `test` | 测试运行器（`import test from 'node:test'`） |
| `node:timers` / `timers` | `setTimeout`/`setInterval` + `node:timers/promises` |
| `node:process` / `process` | 进程信息 |
| `node:events` / `events` | `EventEmitter` |
| `node:os` / `os` | 操作系统信息 |
| `node:module` / `module` | 模块系统辅助 |
| `node:crypto` / `crypto` | 常用哈希、随机数和基础加密工具（以当前 shim 实际导出为准） |

带 `node:` 前缀和不带前缀都能用（裸名也行）。

`process.versions.node` 是 shim 提供的元数据，不表示当前运行的是 Node.js，也不能据此认定面向该 Node 版本的 npm 包兼容。

<a id="wiki-section-3"></a>
## 常用 API 速查

<a id="wiki-section-4"></a>
### `node:fs`

```javascript
import fs from 'node:fs'
import fsp from 'node:fs/promises'

// 同步
fs.mkdirSync('nekojs/data', { recursive: true })
fs.writeFileSync('nekojs/data/x.txt', 'hello')
console.info(fs.readFileSync('nekojs/data/x.txt', 'utf8'))   // 'hello'
fs.existsSync('nekojs/data/x.txt')                            // true
fs.readdirSync('nekojs/data')                                 // ['x.txt']
fs.statSync('nekojs/data/x.txt').isFile()                     // true
fs.copyFileSync('nekojs/data/x.txt', 'nekojs/data/x-copy.txt')
fs.renameSync('nekojs/data/x-copy.txt', 'nekojs/data/x-moved.txt')
fs.rmSync('nekojs/data/x-moved.txt')

// promises
await fsp.appendFile('nekojs/data/x.txt', ':more')
const files = await fsp.readdir('nekojs/data')
```

回调形式（`fs.readFile(path, cb)`）也支持。

`fs` 的相对路径最初以**游戏目录**为基准，不是以导入它的脚本目录为基准。`process.chdir()` 可在同一沙盒边界内改变该 Context 的文件系统工作目录；相对 `import`/`require` 仍从导入模块的位置解析。示例的所有写入目标均位于 `nekojs/` 下，符合默认写入策略。

<a id="wiki-section-5"></a>
### `node:path`

```javascript
import path from 'node:path'
path.join('a', 'b', 'c.txt')      // 'a/b/c.txt'
path.resolve('a', 'b')            // 绝对路径
path.extname('a.txt')             // '.txt'
path.basename('/x/y/a.txt')       // 'a.txt'
```

<a id="wiki-section-6"></a>
### `node:timers`

```javascript
import { setTimeout as delay } from 'node:timers/promises'

// 全局 setTimeout/setInterval 也可用（无需 import）
setTimeout(() => console.info('1秒后'), 1000)

// promises 形式
await delay(1000)
console.info('等了 1 秒')
```

> 全局 `setTimeout`/`setInterval`/`clearTimeout`/`clearInterval` 在脚本顶层即可用（已 patch 到 `globalThis`）。

上面的正延时示例适用于 SERVER/CLIENT/TEST。STARTUP 拒绝所有 interval 与正延时 timeout，允许零延时或 immediate 回调。调度器只把就绪任务入队：回调在脚本环境归属方冲刷队列时执行（包括等待模块求值期间、生命周期或 tick 分发，或 TEST 流程），不在调度器线程运行，也不保证精确的墙钟触发时刻。失败候选与被替换的代会取消各自的定时器，不应把旧回调当作持久状态。

<a id="wiki-section-7"></a>
### `node:test` + `node:assert`

```javascript
import test from 'node:test'
import assert from 'node:assert/strict'

test('简单的加法', () => {
  assert.strictEqual(1 + 1, 2)
})

test('异步测试', async () => {
  const result = await someAsyncOp()
  assert.ok(result.success)
})
```

在 `test_scripts/` 里写，用 `/nekojs test` 运行。详见 [脚本基础 - TEST 脚本](script-basics_cn)。

<a id="wiki-section-8"></a>
### `node:buffer`

```javascript
import { Buffer } from 'node:buffer'
const buf = Buffer.from('hello', 'utf8')
console.info(buf.length)          // 5
console.info(buf.toString('hex')) // '68656c6c6f'
```

<a id="wiki-section-9"></a>
## 访问范围限制

| 操作 | 默认边界 |
|---|---|
| 读取 | 游戏目录内，并检查规范路径与符号链接逃逸 |
| 新建、写入、删除、改名目标 | 仅 `nekojs/`；`allowFsWriteOutsideNekojs` 默认为 `false` |
| 开启 `allowFsWriteOutsideNekojs=true` | 写入范围可扩大到游戏目录，但不能越出游戏目录 |
| 受保护配置 | 即使开启该选项，`nekojs/config/` 与旧 `config/nekojs-engine.toml` 仍不可写 |
| 创建符号链接 | 不允许 |

该选项位于 `nekojs/config/engine.toml`。文件系统检查与可选 watchdog 预算不能保证任意 Java 宿主调用或不可信脚本安全。`scriptRunawayTimeoutSeconds` 与 `scriptStatementLimit` 默认为 0（禁用），watchdog 隔离后需显式完整重载恢复。

```javascript
import fs from 'node:fs'
fs.writeFileSync('nekojs/data/x.txt', 'ok')      // 允许：位于 nekojs/ 下
fs.writeFileSync('/etc/passwd', 'hacked')         // 拒绝：超出沙盒范围
```

<a id="wiki-section-10"></a>
## npm 依赖

把**纯 JS** 的 npm 包放进 `nekojs/node_modules/`：

```javascript
// nekojs/node_modules/greeting/index.cjs
module.exports = name => `Hello, ${name}!`
```

```javascript
// server_scripts/use_greeting.cjs
const greet = require('greeting/index.cjs')
console.info(greet('NekoJS')) // Hello, NekoJS!
```

<a id="wiki-section-11"></a>
### npm 依赖限制

纯 JavaScript 只是必要条件，不代表包一定兼容。NekoJS 解析器检查显式文件、扩展名候选与目录 `index` 文件，不实现 Node 的 package.json `main` 或 `exports` 解析；必要时指定兼容的明确入口路径。同步 `require()` 会拒绝尚未完成求值且含顶层 await 或异步静态依赖的 ESM，应改用 `import()`。

| 限制 | 说明 |
|---|---|
| 原生 bindings | 不支持含 C/C++ 编译产物的包（`node-sass`、`sharp`、`better-sqlite3`、`canvas` 等） |
| Node 运行时范围 | NekoJS 不是完整 Node 运行时；本表列出的核心模块可用，未实现的 API 不可用 |
| 文件沙盒 | 依赖若访问游戏目录外的文件会被拒绝 |
| 可信代码 | 脚本及其依赖应视为可信代码，尤其是在多人服务器中 |

<a id="wiki-section-12"></a>
## 实战：测试脚本示例

```javascript
// test_scripts/node_fs_test.js
import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import fsp from 'node:fs/promises'

test('fs 同步与 promises 都能用', async () => {
  fs.writeFileSync('nekojs/data/test.txt', 'hello')
  assert.strictEqual(fs.readFileSync('nekojs/data/test.txt', 'utf8'), 'hello')

  await fsp.appendFile('nekojs/data/test.txt', ':promise')
  assert.strictEqual(fs.readFileSync('nekojs/data/test.txt', 'utf8'), 'hello:promise')
})
```

用 `/nekojs test` 运行。

<a id="wiki-section-13"></a>
## 下一步

- [模块系统](module-system_cn) —— `java:` 导入、ESM/CJS 互操作。
- [命令](commands_cn) —— `/nekojs test` 等。
- [常见问题](faq_cn)。

<!-- wiki-nav -->

---

[上一篇: MixinJS（实验性）](mixinjs_cn) · [目录](Home) · [下一篇: 插件开发](plugin-development_cn)
