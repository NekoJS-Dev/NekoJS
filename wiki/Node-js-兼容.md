# Node.js 兼容

NekoJS 内置一组 Node.js 核心模块 shim，让你能 `require`/`import` 常用的 Node API。**这是 shim，不是完整 Node.js 运行时**。

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

带 `node:` 前缀和不带前缀都能用（裸名也行）。

## 常用 API 速查

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
fs.rmSync('nekojs/data/x.txt')
fs.renameSync('a.txt', 'b.txt')
fs.copyFileSync('a.txt', 'b.txt')

// promises
await fsp.appendFile('nekojs/data/x.txt', ':more')
const files = await fsp.readdir('nekojs/data')
```

回调形式（`fs.readFile(path, cb)`）也支持。

### `node:path`

```javascript
import path from 'node:path'
path.join('a', 'b', 'c.txt')      // 'a/b/c.txt'
path.resolve('a', 'b')            // 绝对路径
path.extname('a.txt')             // '.txt'
path.basename('/x/y/a.txt')       // 'a.txt'
```

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

在 `test_scripts/` 里写，用 `/nekojs test` 运行。详见 [脚本基础 - TEST 脚本](脚本基础)。

### `node:buffer`

```javascript
import { Buffer } from 'node:buffer'
const buf = Buffer.from('hello', 'utf8')
console.info(buf.length)          // 5
console.info(buf.toString('hex')) // '68656c6c6f'
```

## 访问范围限制

- **文件访问**限制在游戏目录内（沙盒）。试图访问游戏目录外的路径会报错。
- **符号链接逃逸检查**：即使符号链接指向游戏目录外，也会被拒绝。
- **不允许创建符号链接**。

```javascript
import fs from 'node:fs'
fs.writeFileSync('nekojs/data/x.txt', 'ok')      // ✅ 在 nekojs/ 下
fs.writeFileSync('/etc/passwd', 'hacked')         // ❌ 被沙盒拒绝
```

## npm 依赖

把**纯 JS** 的 npm 包放进 `nekojs/node_modules/`：

```javascript
// 假设 nekojs/node_modules/lodash/ 存在
const _ = require('lodash')
console.info(_.chunk([1,2,3,4], 2))   // [[1,2],[3,4]]
```

### ⚠️ npm 依赖限制

| 限制 | 说明 |
|---|---|
| ❌ 原生 bindings | 含 C/C++ 编译产物的包**不能用**（`node-sass`、`sharp`、`better-sqlite3`、`canvas` 等） |
| ⚠️ 不是完整 Node 运行时 | 缺失的 Node API 会缺失；本表列出的核心模块可用 |
| ⚠️ 文件沙盒 | 依赖若访问游戏目录外的文件会被拒 |
| ⚠️ 可信代码 | 脚本（含依赖）应视为可信代码，尤其在多人服务器 |

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

## 下一步

- [模块系统](模块系统) —— `java:` 导入、ESM/CJS 互操作。
- [命令](命令) —— `/nekojs test` 等。
- [常见问题](常见问题)。
