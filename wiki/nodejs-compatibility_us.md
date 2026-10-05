<!-- wiki-page: nodejs-compatibility; locale: us -->

> **English** · [中文](nodejs-compatibility_cn)

<a id="wiki-section-1"></a>
# Node.js compatibility

NekoJS includes a set of Node.js core module shims, so you can `require`/`import` common Node APIs. **These are shims, not a full Node.js runtime**.

<a id="wiki-section-2"></a>
## Available modules

| Module | Description |
|---|---|
| `node:buffer` / `buffer` | `Buffer` (wrapped through `__nekoNodeBuffer`) |
| `node:fs` / `fs` | Synchronous + callback + promises APIs |
| `node:path` / `path` | Path handling |
| `node:util` / `util` | Utility functions |
| `node:assert` / `assert` | Assertions (`node:assert/strict` also works) |
| `node:test` / `test` | Test runner (`import test from 'node:test'`) |
| `node:timers` / `timers` | `setTimeout`/`setInterval` + `node:timers/promises` |
| `node:process` / `process` | Process information |
| `node:events` / `events` | `EventEmitter` |
| `node:os` / `os` | Operating system information |
| `node:module` / `module` | Module system helpers |
| `node:crypto` / `crypto` | Common hashes, random numbers, and basic cryptographic utilities (subject to the current shim's actual exports) |

Both forms work, with or without the `node:` prefix (bare names work too).

<a id="wiki-section-3"></a>
## Common API reference

<a id="wiki-section-4"></a>
### `node:fs`

```javascript
import fs from 'node:fs'
import fsp from 'node:fs/promises'

// Synchronous
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

Callback forms (`fs.readFile(path, cb)`) are also supported.

<a id="wiki-section-5"></a>
### `node:path`

```javascript
import path from 'node:path'
path.join('a', 'b', 'c.txt')      // 'a/b/c.txt'
path.resolve('a', 'b')            // Absolute path
path.extname('a.txt')             // '.txt'
path.basename('/x/y/a.txt')       // 'a.txt'
```

<a id="wiki-section-6"></a>
### `node:timers`

```javascript
import { setTimeout as delay } from 'node:timers/promises'

// Global setTimeout/setInterval also work (no import needed)
setTimeout(() => console.info('1秒后'), 1000)

// promises form
await delay(1000)
console.info('等了 1 秒')
```

> Global `setTimeout`/`setInterval`/`clearTimeout`/`clearInterval` are available at script top level (patched onto `globalThis`).

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

Write these in `test_scripts/` and run them with `/nekojs test`. See [Script basics - TEST scripts](script-basics_us).

<a id="wiki-section-8"></a>
### `node:buffer`

```javascript
import { Buffer } from 'node:buffer'
const buf = Buffer.from('hello', 'utf8')
console.info(buf.length)          // 5
console.info(buf.toString('hex')) // '68656c6c6f'
```

<a id="wiki-section-9"></a>
## Access limits

- **File access** is restricted to the game directory (sandbox). Attempts to access paths outside it fail.
- **Symlink escape checks**: access is rejected even when a symlink points outside the game directory.
- **Creating symlinks is not allowed**.

```javascript
import fs from 'node:fs'
fs.writeFileSync('nekojs/data/x.txt', 'ok')      // Allowed: under nekojs/
fs.writeFileSync('/etc/passwd', 'hacked')         // Rejected: outside the sandbox
```

<a id="wiki-section-10"></a>
## npm dependencies

Place **pure JS** npm packages in `nekojs/node_modules/`:

```javascript
// Assume nekojs/node_modules/lodash/ exists
const _ = require('lodash')
console.info(_.chunk([1,2,3,4], 2))   // [[1,2],[3,4]]
```

<a id="wiki-section-11"></a>
### npm dependency limits

| Limit | Description |
|---|---|
| Native bindings | Packages containing compiled C/C++ code are not supported (`node-sass`, `sharp`, `better-sqlite3`, `canvas`, etc.) |
| Node runtime coverage | NekoJS is not a full Node runtime; the core modules listed here are available, but unimplemented APIs are not |
| File sandbox | Dependencies that access files outside the game directory are rejected |
| Trusted code | Scripts and their dependencies should be treated as trusted code, especially on multiplayer servers |

<a id="wiki-section-12"></a>
## Practical example: test script

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

Run with `/nekojs test`.

<a id="wiki-section-13"></a>
## Next steps

- [Module system](module-system_us) - `java:` imports and ESM/CJS interoperability.
- [Commands](commands_us) - `/nekojs test` and other commands.
- [FAQ](faq_us).

<!-- wiki-nav -->

---

[Previous: JSX client UI](jsx-client-ui_us) · [Contents](Home) · [Next: Plugin development](plugin-development_us)
