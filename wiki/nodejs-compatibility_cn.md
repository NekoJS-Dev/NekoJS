<!-- wiki-page: nodejs-compatibility; locale: cn -->

> **中文** · [English](nodejs-compatibility_us)

<a id="wiki-section-1"></a>
# Node.js 兼容

NekoJS 自带一部分常用的 Node.js 模块，例如 `fs`、`path`、`buffer`。写法和在 Node.js 里一样，用 `import` 或 `require` 引入即可。

但 NekoJS 不是 Node.js。只有下表里的模块可用，有些模块也只实现了常用部分。

<a id="wiki-section-2"></a>
## 可用模块

| 模块 | 用途 |
|---|---|
| `fs`、`fs/promises` | 读写文件，有同步、回调和 Promise 三种写法 |
| `path` | 拼接和拆分路径 |
| `buffer` | 二进制数据 `Buffer` |
| `timers`、`timers/promises` | `setTimeout`、`setInterval`、`setImmediate` |
| `events` | `EventEmitter` |
| `util` | `format`、`inspect`、`promisify` 等工具函数 |
| `assert`、`assert/strict` | 断言 |
| `test` | 测试框架，只能在 `test_scripts` 里用 |
| `crypto` | `randomUUID`、`randomBytes`、`createHash`、`createHmac`、`timingSafeEqual` |
| `os` | 操作系统信息 |
| `process` | 进程信息，例如 `process.env`、`process.cwd()` |
| `module` | `createRequire`、`builtinModules`、`isBuiltin` |

带不带 `node:` 前缀都可以：`import fs from 'node:fs'` 和 `import fs from 'fs'` 是一样的。

`Buffer`、`process`、`setTimeout` 等定时器函数，以及 `queueMicrotask`、`atob`、`btoa`、`performance.now()` 都是全局可用的，不需要 `import`。

<a id="wiki-section-3"></a>
## 常用模块示例

<a id="wiki-section-4"></a>
### node:fs

```javascript
import fs from 'node:fs'
import fsp from 'node:fs/promises'

// 同步写法
fs.mkdirSync('nekojs/data', { recursive: true })
fs.writeFileSync('nekojs/data/note.txt', 'hello')
console.info(fs.readFileSync('nekojs/data/note.txt', 'utf8')) // hello
console.info(fs.existsSync('nekojs/data/note.txt'))           // true
console.info(fs.readdirSync('nekojs/data'))                   // [ 'note.txt' ]

// Promise 写法
await fsp.appendFile('nekojs/data/note.txt', ' world')
```

还有 `statSync`、`copyFileSync`、`renameSync`、`rmSync`、`unlinkSync` 等常用函数。回调写法 `fs.readFile(path, callback)` 也支持。

相对路径从**游戏目录**算起，不是从脚本所在的目录算起。所以上面写的是 `nekojs/data/...`。

> 只是想存一点 JSON 数据的话，用 `JsonIO` 更简单，见 [数据与状态](data-and-state_cn)。

<a id="wiki-section-5"></a>
### node:path

```javascript
import path from 'node:path'

path.join('a', 'b', 'c.txt')  // 'a/b/c.txt'
path.extname('a.txt')         // '.txt'
path.basename('/x/y/a.txt')   // 'a.txt'
path.resolve('a', 'b')        // 转成绝对路径
```

<a id="wiki-section-6"></a>
### 定时器

```javascript
import { setTimeout as delay } from 'node:timers/promises'

// 全局函数，直接用
const id = setTimeout(() => console.info('1 秒后'), 1000)
clearTimeout(id)

// Promise 写法
await delay(1000)
console.info('等了 1 秒')
```

和 Node.js 的区别：

- 回调在游戏的 tick 里执行，时间不是精确到毫秒的。
- `setTimeout` 返回一个数字 ID，没有 `.ref()`、`.unref()` 方法。用 `clearTimeout(id)` 取消。
- **启动脚本里不能用 `setInterval`，也不能用延时大于 0 的 `setTimeout`**，会直接报错。延时为 0 的 `setTimeout` 和 `setImmediate` 可以用。
- 同一时间最多挂着 1024 个定时器。用完记得清掉。
- 重载脚本时，旧脚本的定时器会被取消。

<a id="wiki-section-7"></a>
### node:test 与 node:assert

在 `nekojs/test_scripts/` 里写测试：

```javascript
// test_scripts/math_test.js
import test from 'node:test'
import assert from 'node:assert/strict'

test('加法', () => {
  assert.strictEqual(1 + 1, 2)
})

test('异步测试', async () => {
  const result = await Promise.resolve({ success: true })
  assert.ok(result.success)
})
```

执行 `/nekojs test` 运行，结果写在 `logs/nekojs/test.log`。`test.skip`、`test.only`、`test.todo`、`describe` 以及 `before` / `after` / `beforeEach` / `afterEach` 也都可以用。

`node:test` 只能在测试脚本里用，在其他目录里调用会报 `node:test is only available in test_scripts. Use /nekojs test to run these files.`。`node:assert` 在哪里都能用。

<a id="wiki-section-8"></a>
### node:buffer

```javascript
const buf = Buffer.from('hello', 'utf8')
console.info(buf.length)          // 5
console.info(buf.toString('hex')) // 68656c6c6f
```

<a id="wiki-fs-access"></a>
<a id="wiki-section-9"></a>
## 文件访问范围

为了安全，脚本能访问的文件有限制：

| 操作 | 允许的范围 |
|---|---|
| 读取 | 游戏目录里的任何文件 |
| 新建、写入、删除、改名 | 只有 `nekojs/` 里的文件 |
| 写 `nekojs/config/` | 不允许 |
| 创建符号链接 | 不允许 |

超出范围的操作会直接报错：

```javascript
import fs from 'node:fs'
fs.writeFileSync('nekojs/data/x.txt', 'ok')  // 可以：在 nekojs/ 里
fs.writeFileSync('options.txt', 'oops')      // 报错：在 nekojs/ 外面
```

如果确实需要写 `nekojs/` 以外的文件，在 `nekojs/config/engine.toml` 里设置 `allowFsWriteOutsideNekojs = true`，然后重启游戏。开了以后，脚本可以写游戏目录里的任何文件，但游戏目录以外和 `nekojs/config/` 仍然不能写。见 [配置参考](configuration_cn)。

<a id="wiki-section-10"></a>
## npm 包

纯 JavaScript 的 npm 包可以放进 `nekojs/node_modules/` 使用。具体做法和限制见 [模块系统](module-system_cn)。

简单说：

- 不读 `package.json` 的 `main` 和 `exports`，入口不是 `index.js` 的包要写完整路径。
- 带原生代码的包（例如 `sharp`、`better-sqlite3`）不能用。
- 用到上面列表以外 Node API 的包跑不起来。

<a id="wiki-section-11"></a>
## 和 Node.js 的其他区别

- **`process.exit()` 不会退出游戏**，只会在日志里打一条警告，脚本继续运行。
- `process.versions.node` 是一个固定的版本号，只为了让某些包能识别环境，不代表真的在运行 Node.js。
- `process.chdir()` 会改变 `fs` 相对路径的起点，但不影响 `import` / `require` 的相对路径。
- `process.nextTick()` 的执行顺序和 Node.js 不完全一样。不要依赖它和 Promise 之间的先后顺序。
- 没有 `http`、`net`、`child_process`、`worker_threads` 等模块。

<a id="wiki-section-12"></a>
## 接下来

- [模块系统](module-system_cn)：多文件、npm 包、`java:` 导入。
- [数据与状态](data-and-state_cn)：保存数据的更简单方式。
- [命令](commands_cn)：`/nekojs test` 等命令。

<!-- wiki-nav -->

---

[上一篇: MixinJS（实验性）](mixinjs_cn) · [目录](Home) · [下一篇: 插件开发](plugin-development_cn)
