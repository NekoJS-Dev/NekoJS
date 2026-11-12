<!-- wiki-page: nodejs-compatibility; locale: us -->

> **English** · [中文](nodejs-compatibility_cn)

<a id="wiki-section-1"></a>
# Node.js compatibility

NekoJS ships a set of common Node.js modules, such as `fs`, `path` and `buffer`. You use them the same way as in Node.js, with `import` or `require`.

NekoJS is not Node.js, though. Only the modules in the table below exist, and some of them cover only the commonly used parts.

<a id="wiki-section-2"></a>
## Available modules

| Module | Use it for |
|---|---|
| `fs`, `fs/promises` | Reading and writing files, in sync, callback and Promise styles |
| `path` | Joining and splitting paths |
| `buffer` | Binary data with `Buffer` |
| `timers`, `timers/promises` | `setTimeout`, `setInterval`, `setImmediate` |
| `events` | `EventEmitter` |
| `util` | Helpers such as `format`, `inspect` and `promisify` |
| `assert`, `assert/strict` | Assertions |
| `test` | Test framework, only in `test_scripts` |
| `crypto` | `randomUUID`, `randomBytes`, `createHash`, `createHmac`, `timingSafeEqual` |
| `os` | Operating system information |
| `process` | Process information such as `process.env` and `process.cwd()` |
| `module` | `createRequire`, `builtinModules`, `isBuiltin` |

The `node:` prefix is optional: `import fs from 'node:fs'` and `import fs from 'fs'` are the same.

`Buffer`, `process`, the timer functions such as `setTimeout`, plus `queueMicrotask`, `atob`, `btoa` and `performance.now()` are globals. You do not need to import them.

<a id="wiki-section-3"></a>
## Examples of common modules

<a id="wiki-section-4"></a>
### node:fs

```javascript
import fs from 'node:fs'
import fsp from 'node:fs/promises'

// Sync style
fs.mkdirSync('nekojs/data', { recursive: true })
fs.writeFileSync('nekojs/data/note.txt', 'hello')
console.info(fs.readFileSync('nekojs/data/note.txt', 'utf8')) // hello
console.info(fs.existsSync('nekojs/data/note.txt'))           // true
console.info(fs.readdirSync('nekojs/data'))                   // [ 'note.txt' ]

// Promise style
await fsp.appendFile('nekojs/data/note.txt', ' world')
```

Other common functions such as `statSync`, `copyFileSync`, `renameSync`, `rmSync` and `unlinkSync` are there too. The callback style, `fs.readFile(path, callback)`, also works.

Relative paths start from **the game folder**, not from the script's folder. That is why the example writes `nekojs/data/...`.

> If you only want to store a little JSON, `JsonIO` is simpler. See [Data and state](data-and-state_us).

<a id="wiki-section-5"></a>
### node:path

```javascript
import path from 'node:path'

path.join('a', 'b', 'c.txt')  // 'a/b/c.txt'
path.extname('a.txt')         // '.txt'
path.basename('/x/y/a.txt')   // 'a.txt'
path.resolve('a', 'b')        // an absolute path
```

<a id="wiki-section-6"></a>
### Timers

```javascript
import { setTimeout as delay } from 'node:timers/promises'

// Global functions, no import needed
const id = setTimeout(() => console.info('one second later'), 1000)
clearTimeout(id)

// Promise style
await delay(1000)
console.info('waited one second')
```

How they differ from Node.js:

- Callbacks run during a game tick, so timing is not exact to the millisecond.
- `setTimeout` returns a number. There is no `.ref()` or `.unref()`. Cancel it with `clearTimeout(id)`.
- **Startup scripts cannot use `setInterval` or a `setTimeout` with a delay above 0**; it throws an error. `setTimeout` with a delay of 0 and `setImmediate` are fine.
- At most 1024 timers can be pending at once. Clear the ones you no longer need.
- When scripts reload, the old scripts' timers are cancelled.

<a id="wiki-section-7"></a>
### node:test and node:assert

Write tests in `nekojs/test_scripts/`:

```javascript
// test_scripts/math_test.js
import test from 'node:test'
import assert from 'node:assert/strict'

test('addition', () => {
  assert.strictEqual(1 + 1, 2)
})

test('async test', async () => {
  const result = await Promise.resolve({ success: true })
  assert.ok(result.success)
})
```

Run them with `/nekojs test`. Results go to `logs/nekojs/test.log`. `test.skip`, `test.only`, `test.todo`, `describe` and `before` / `after` / `beforeEach` / `afterEach` are available too.

`node:test` works only in test scripts. Anywhere else it throws `node:test is only available in test_scripts. Use /nekojs test to run these files.` `node:assert` works everywhere.

<a id="wiki-section-8"></a>
### node:buffer

```javascript
const buf = Buffer.from('hello', 'utf8')
console.info(buf.length)          // 5
console.info(buf.toString('hex')) // 68656c6c6f
```

<a id="wiki-fs-access"></a>
<a id="wiki-section-9"></a>
## File access limits

For safety, scripts can only reach some files:

| Operation | Allowed where |
|---|---|
| Read | Any file inside the game folder |
| Create, write, delete, rename | Only files inside `nekojs/` |
| Write to `nekojs/config/` | Never |
| Create symbolic links | Never |

Anything outside these limits throws an error:

```javascript
import fs from 'node:fs'
fs.writeFileSync('nekojs/data/x.txt', 'ok')  // allowed: inside nekojs/
fs.writeFileSync('options.txt', 'oops')      // error: outside nekojs/
```

If you really need to write outside `nekojs/`, set `allowFsWriteOutsideNekojs = true` in `nekojs/config/engine.toml` and restart the game. Scripts can then write anywhere inside the game folder. Writing outside the game folder or to `nekojs/config/` is still refused. See [Configuration](configuration_us).

<a id="wiki-section-10"></a>
## npm packages

You can use pure-JavaScript npm packages by putting them in `nekojs/node_modules/`. See [Module system](module-system_us) for how and for the limits.

In short:

- The `main` and `exports` fields of `package.json` are not read. If a package's entry is not `index.js`, use the full path.
- Packages with native code (such as `sharp` or `better-sqlite3`) do not work.
- A package that needs Node APIs beyond the list above will not run.

<a id="wiki-section-11"></a>
## Other differences from Node.js

- **`process.exit()` does not quit the game.** It logs a warning and the script keeps running.
- `process.versions.node` is a fixed version string so that some packages recognise the environment. It does not mean Node.js is running.
- `process.chdir()` changes where `fs` relative paths start. It does not affect relative paths in `import` / `require`.
- `process.nextTick()` does not run in exactly the same order as in Node.js. Do not rely on its order relative to Promises.
- There are no `http`, `net`, `child_process` or `worker_threads` modules.

<a id="wiki-section-12"></a>
## Next steps

- [Module system](module-system_us): multiple files, npm packages and `java:` imports.
- [Data and state](data-and-state_us): an easier way to save data.
- [Commands](commands_us): `/nekojs test` and other commands.

<!-- wiki-nav -->

---

[Previous: MixinJS (experimental)](mixinjs_us) · [Contents](Home) · [Next: Plugin development](plugin-development_us)
