<!-- wiki-page: module-system; locale: us -->

> **English** · [中文](module-system_cn)

<a id="wiki-section-1"></a>
# Module system

NekoJS supports both **native ESM** and **CommonJS**, and can also import Java packages/classes as modules. This page explains how to split code across files, use npm dependencies, and import Java classes.

<a id="wiki-section-2"></a>
## ESM (recommended)

Full modern ESM is supported:

```javascript
// math_utils.mjs
export function add(a, b) { return a + b }
export const PI = 3.14
export default function square(x) { return x * x }
```

```javascript
// main.mjs
import square, { add, PI } from './math_utils.mjs'
import { existsSync } from 'node:fs'        // node: modules can also be imported

console.info(square(add(1, 2)))              // 9
```

**Supported features**:
- `import` / `export` (named, default, namespace)
- Live bindings (importers see changes to exported values)
- Circular dependencies
- `import.meta`
- **Top-level await**
- Dynamic `import('...')`
- ESM/CJS interoperability

> `jsconfig.json` defaults to `module: "ESNext"`, which is fully supported by the IDE.

<a id="wiki-section-3"></a>
## CommonJS

```javascript
// utils.cjs
function calculateDamage(base, multiplier) { return base * multiplier }
const MOD_NAME = 'NekoJS'
module.exports = { calculateDamage, MOD_NAME }
```

```javascript
// main.cjs
const { calculateDamage, MOD_NAME } = require('./utils.cjs')

ServerEvents.tickPre(event => {
  // Use calculateDamage
})
```

`require()` uses GraalJS's CommonJS implementation with `js.commonjs-require=true`; the cwd is the `nekojs/` root directory.

<a id="wiki-section-4"></a>
## Extensions and module modes

This table is not naming advice; it describes the engine's dispatch rules (`NekoModuleMode.fromExtension`):

| Extension | Module mode |
|---|---|
| `.mjs` | Forces ESM |
| `.cjs` | Forces CJS |
| `.js` | `AUTO`: determined by content (usually treated as ESM; `enableEsmAuthoring` in `nekojs/config/engine.toml` can disable ESM rewriting, falling back to plain CommonJS) |
| `.ts` | Erasable TypeScript (after erasure, follows the rules above: `AUTO`) |
| `.jsx`/`.tsx` | JSX lowering (classic runtime), with the same module mode as `AUTO` |

This mapping cannot be changed: writing `export` in `.cjs` does not make it ESM because you want it to be. The directory names (`server_scripts` / `client_scripts` / `startup_scripts` / `test_scripts`) and the `nekojs/` root are also hardcoded (`NekoJSPaths`); no configuration option changes them.

See [TypeScript and JSX](typescript-and-jsx_us).

<a id="wiki-section-5"></a>
## Using npm dependencies

Place **pure JS** npm packages in `nekojs/node_modules/`, then `require`/`import` them as you would in Node:

```javascript
// Assume lodash is installed in nekojs/node_modules/lodash/
const _ = require('lodash')
console.info(_.chunk([1,2,3,4], 2))   // [[1,2],[3,4]]
```

<a id="wiki-section-6"></a>
### Limits

- Note: **only pure JS packages are supported**. Packages containing native bindings (compiled C/C++ code) **cannot be used**, such as `node-sass`, `sharp`, and `better-sqlite3`.
- These are shims, **not a full Node.js runtime**. See [Node.js compatibility](nodejs-compatibility_us) for the available core modules.
- File access is limited to the game directory, with symlink escape checks.

<a id="wiki-section-7"></a>
## Importing Java classes (`java:` modules)

Java packages/classes can be imported as special modules. **Only the `java:` prefix and slash-separated paths are accepted**.

<a id="wiki-section-8"></a>
### Package modules (lazy namespace proxy)

```ts
import { Integer, $Integer, Math as JavaMath } from 'java:java/lang'
const { Integer, $Integer, Math: JavaMath } = require('java:java/lang')
```

- Ordinary names (such as `Integer` and `Math`) use property lookup.
- Names with a `$` prefix, such as `$Integer`, map directly to `Java.type('java.lang.Integer')`.
- `Integer`, `$Integer`, and `Math` / `JavaMath` all work.

<a id="wiki-section-9"></a>
### Class modules (return a Java class proxy directly)

```ts
import IntegerClass, { $Integer } from 'java:java/lang/Integer'
const IntegerClass = require('java:java/lang/Integer')
```

- Returns the Java class proxy directly.
- Also exposes `default` and `$Class`.
- This is the most direct form when you only need one specific Java class.

<a id="wiki-section-10"></a>
### Rules

| Rule | Description |
|---|---|
| Only the `java:` prefix is accepted | Prefixes such as `org.example:` are not supported |
| Only slash-separated paths are accepted | `java:java/lang`, `java:java/lang/Integer` |
| `.` / `..` are not supported | — |
| Dynamic `import('java:...')` | Returns a synthetic ESM module with `default` / `namespace` |

<a id="wiki-section-11"></a>
### Practical example: calling arbitrary Java APIs

```javascript
import { $ArrayList } from 'java:java/util'
const ArrayList = $ArrayList          // Equivalent to Java.type('java.util.ArrayList')
const list = new ArrayList()
list.add('hello')
console.info(list.size())             // 1
```

```javascript
// Call standard Java static methods
const JavaMath = Java.type('java.lang.Math')
console.info(JavaMath.max(3, 7))        // 7
console.info(JavaMath.PI)               // 3.141592653589793
```

> **Prefer NekoJS bindings** in recipe/event scripts (`Item.of(...)` rather than `Java.type(...)`): they are simpler and provide type hints. `java:` imports are mainly for cases that bindings do not cover.

<a id="wiki-section-12"></a>
## Security sandbox

NekoJS limits what scripts can access:

- **Java classes**: high-risk classes are filtered (`java.lang.Runtime`, `Process`, `ClassLoader`, `System`, `java.io.*`, `java.nio.*`, reflection, net, lwjgl, polyglot itself, as well as `java.awt`/`javax.swing`/`javax.imageio`, `javax.naming`, `java.rmi`, `java.sql`/`javax.sql`, `java.lang.Module`, `org.graalvm`/`com.oracle.truffle`, and NekoJS internals such as `com.tkisor.nekojs.core`). See `common/.../core/fs/ClassFilter.java` for the specific denylist.
- **File system**: access is limited to the game directory, with symlink escape checks.
- **Configuration**: `nekojs/config/engine.toml` (in the same directory as probe.toml; the old `config/nekojs-engine.toml` is a read-only fallback) can configure `allowThreads`, `allowReflection`, `allowAsm`, `enableEsmAuthoring`, etc.
- **Runaway protection**: `scriptRunawayTimeoutSeconds` (disabled by default at 0; explicitly enabling it, for example at 10, is recommended) aborts and automatically rebuilds the script environment if synchronous script execution exceeds that many seconds without yielding (considered a runaway loop, such as `while(true){}`). Each yield (between events or during a long host call) resets the timer, so a long-lived environment is not terminated merely for executing many statements over time. There is also an optional `scriptStatementLimit` (**disabled by default at 0**): a hard limit on the total statements executed continuously by one script source; enable it only when a hard budget is needed.
- **HostAccess limits**: the name-based denylist only blocks **class lookup** such as `Java.type`; object graphs returned by Java methods are controlled by Graal `HostAccess` (currently `HostAccess.ALL`), so instances of denylisted classes can still reach scripts through method return values.

> Scripts should still be treated as **partially trusted code**: do not run untrusted third-party scripts, especially when using network synchronization on multiplayer servers.

<a id="wiki-section-13"></a>
## Top-level await and dynamic import

```javascript
// The JSON data-store is synchronous; paths are fixed under nekojs/data/
JsonIO.write('profiles/default.json', { enabled: true })
const data = JsonIO.read('profiles/default.json')
console.info(data?.toPrettyString())

// Dynamic import: load on demand
if (someCondition) {
  const mod = await import('./feature.js')
  mod.run()
}
```

<a id="wiki-section-14"></a>
## Practical example: splitting a project across files

```
nekojs/server_scripts/
├── lib/
│   ├── constants.js       // Shared constants
│   └── recipe_helpers.js  // Recipe helpers
├── main.js                // Main entry
└── jsconfig.json
```

```javascript
// lib/constants.js
export const ORES = ['minecraft:iron_ore', 'minecraft:gold_ore', 'minecraft:diamond_ore']
```

```javascript
// lib/recipe_helpers.js
export function dustify(event, ore, output) {
  event.recipes.minecraft.smelting(output, ore).id(`nekojs:dust_${ore.split(':')[1]}`)
}
```

```javascript
// main.js
import { ORES } from './lib/constants.js'
import { dustify } from './lib/recipe_helpers.js'

ServerEvents.recipes(event => {
  for (const ore of ORES) {
    dustify(event, ore, ore.replace('_ore', '_ingot'))
  }
})
```

<a id="wiki-section-15"></a>
## Next steps

- [TypeScript and JSX](typescript-and-jsx_us) - Writing scripts in TS.
- [Node.js compatibility](nodejs-compatibility_us) - Available modules such as `fs`/`path`/`buffer`.
- [Global bindings](global-bindings_us) - Top-level APIs.

<!-- wiki-nav -->

---

[Previous: Registering new content](registering-new-content_us) · [Contents](Home) · [Next: TypeScript and JSX](typescript-and-jsx_us)
