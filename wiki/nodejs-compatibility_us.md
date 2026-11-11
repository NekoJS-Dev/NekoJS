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

`process.versions.node` is shim-provided metadata, not evidence that Node.js itself is running or that a package targeting that Node version is compatible.

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
fs.copyFileSync('nekojs/data/x.txt', 'nekojs/data/x-copy.txt')
fs.renameSync('nekojs/data/x-copy.txt', 'nekojs/data/x-moved.txt')
fs.rmSync('nekojs/data/x-moved.txt')

// promises
await fsp.appendFile('nekojs/data/x.txt', ':more')
const files = await fsp.readdir('nekojs/data')
```

Callback forms (`fs.readFile(path, cb)`) are also supported.

Relative `fs` paths initially resolve from the **game directory**, not from the importing script. `process.chdir()` changes that Context's filesystem working directory within the same sandbox; relative `import`/`require` still resolve from their importing module. The example keeps every write target under `nekojs/`, as required by the default write policy.

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

The positive-delay example is for SERVER/CLIENT/TEST scripts. STARTUP rejects every interval and any timeout with a positive delay; zero-delay/immediate callbacks are allowed. The scheduler only queues ready work: callbacks execute when the owning script environment flushes its queue (including module-await pumping, lifecycle/tick dispatch, or the TEST flow), not on the scheduler thread and not at an exact wall-clock instant. Failed candidates and retired generations cancel their timers; old callbacks must not be treated as persistent state.

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

| Operation | Default boundary |
|---|---|
| Read | Game directory, with canonical-path/symlink escape checks |
| Create/write/delete/rename targets | `nekojs/` only; `allowFsWriteOutsideNekojs=false` by default |
| With `allowFsWriteOutsideNekojs=true` | Writes may extend to the game directory, never outside it |
| Protected configuration | `nekojs/config/` and legacy `config/nekojs-engine.toml` remain unwritable even with the flag enabled |
| Create symbolic links | Not allowed |

The flag is in `nekojs/config/engine.toml`. Filesystem checks and optional watchdog budgets do not make arbitrary Java host calls or untrusted scripts safe. `scriptRunawayTimeoutSeconds` and `scriptStatementLimit` default to 0 (disabled); watchdog isolation requires explicit full reload to recover.

```javascript
import fs from 'node:fs'
fs.writeFileSync('nekojs/data/x.txt', 'ok')      // Allowed: under nekojs/
fs.writeFileSync('/etc/passwd', 'hacked')         // Rejected: outside the sandbox
```

<a id="wiki-section-10"></a>
## npm dependencies

Place **pure JS** npm packages in `nekojs/node_modules/`:

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
### npm dependency limits

Pure JavaScript is necessary, not sufficient. The NekoJS resolver checks explicit files, extension candidates and directory `index` files; it does not implement Node package.json `main`/`exports` resolution. Choose a compatible explicit entry path where needed. Synchronous `require()` rejects not-yet-evaluated ESM with top-level await, including async static descendants; use `import()` for asynchronous loading.

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

[Previous: MixinJS (experimental)](mixinjs_us) · [Contents](Home) · [Next: Plugin development](plugin-development_us)
