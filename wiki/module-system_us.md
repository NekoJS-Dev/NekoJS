<!-- wiki-page: module-system; locale: us -->

> **English** · [中文](module-system_cn)

<a id="wiki-section-1"></a>
# Module system

NekoJS supports both **native ESM** and **CommonJS**, and can also import Java packages/classes as modules. This page explains how to split code across files, use npm dependencies, and import Java classes.

<a id="wiki-section-2"></a>
## ESM (recommended)

NekoJS supplies a native ESM loader. The supported syntax and behavior below do not imply full Node.js or browser compatibility:

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

Script entries and their dependencies use NekoJS's module loader, resolver and generation-owned cache. The Context also enables GraalJS CommonJS support with a `nekojs/` working directory; that option alone does not describe NekoJS dependency identity or reload behavior. Relative imports/requires resolve from the importing module.

<a id="wiki-section-4"></a>
## Extensions and module modes

This table is not naming advice; it describes the engine's dispatch rules (`NekoModuleMode.fromExtension`):

| Extension | Module mode |
|---|---|
| `.mjs` | Requests ESM when `enableEsmAuthoring` is enabled |
| `.cjs` | Forces CJS |
| `.js` | `AUTO`: compiled module syntax selects ESM; a non-module program selects CommonJS |
| `.ts` | Erasable TypeScript (after erasure, follows the rules above: `AUTO`) |
| `.jsx`/`.tsx` | JSX lowering (classic by default; optional automatic runtime), with module mode `AUTO` |

`enableEsmAuthoring=false` selects the CommonJS preparation path even for `.mjs`; it is not an automatic import/export-to-CommonJS compatibility transform. With ESM enabled, writing `export` in `.cjs` does not make it ESM because you want it to be. The directory names (`server_scripts` / `client_scripts` / `startup_scripts` / `test_scripts`) and the `nekojs/` root are also hardcoded (`NekoJSPaths`); no configuration option changes them.

See [TypeScript and JSX](typescript-and-jsx_us).

Module caches and dependency records belong to the script generation. An explicit legacy file reload invalidates the changed module and known affected dependencies/entries, then reruns entries in the active Context; it does not undo side effects if rerunning fails. A full generation reload uses a fresh candidate Context/cache and releases the old generation after commit. Existing namespace/exports references from the old generation must not be retained as durable state. Saving a module alone is not an automatic reload trigger. Helpers under legacy script roots are also discovered as entries: keep helper top-level code free of event registration or other effects that should run only once. Versioned pack files require package/type reload; see [Commands](commands_us) and [Script packs](script-packs_us).

<a id="wiki-section-5"></a>
## Using npm dependencies

Place **pure JS** npm packages in `nekojs/node_modules/`, then `require`/`import` them as you would in Node:

```javascript
// nekojs/node_modules/greeting/index.cjs
module.exports = name => `Hello, ${name}!`
```

```javascript
// server_scripts/use_greeting.cjs
const greet = require('greeting/index.cjs')
console.info(greet('NekoJS')) // Hello, NekoJS!
```

<a id="wiki-section-6"></a>
### Limits

- Note: **only pure JS packages are supported**. Packages containing native bindings (compiled C/C++ code) **cannot be used**, such as `node-sass`, `sharp`, and `better-sqlite3`.
- These are shims, **not a full Node.js runtime**. See [Node.js compatibility](nodejs-compatibility_us) for the available core modules. Pure JS alone does not guarantee compatibility: package.json `main`/`exports` resolution is not implemented, so the example supplies a self-contained module at an explicit entry path. Synchronous `require()` rejects not-yet-evaluated ESM with top-level await or async static descendants; use `import()` instead.
- File access is limited to the game directory, with symlink escape checks.

<a id="wiki-api-modules"></a>
<a id="wiki-section-7"></a>
## Built-in NekoJS API modules

These routes are built into NekoJS; they are not npm packages to install:

| Route | Contract |
|---|---|
| `nekojs/api/stable` | Explicitly published stable views, including `ID` and the `Native` interoperability bridge |
| `nekojs/api/version` | Current-node helpers and published event groups, including `Item` |

`stable` does not mean every export exists on every loader, Minecraft version or Script Type. For example, stable `ServerEvents` exposes SERVER-only `tickPre`/`tickPost`, while `I18n` is CLIENT-only. Inspect the current instance's Probe declarations rather than assuming the native and stable event payloads are interchangeable.


```ts
import { ID } from 'nekojs/api/stable'
import { Item } from 'nekojs/api/version'

const resource = ID.resource('minecraft:stone')
const stack = Item.of('minecraft:stone')
```

CommonJS uses the same Context-local namespace:

```js
const { ID } = require('nekojs/api/stable')
const resource = ID.resource('minecraft:stone')
```

Plugin publications use `nekojs/api/stable/<modid>` or `nekojs/api/version/<modid>`. Importing an unavailable member fails rather than creating a placeholder. Module-only publication does not create a global; existing legacy globals remain supported. These routes are separate from `java:` imports and existing domain modules such as `nekojs/commands`. See [Global bindings](global-bindings_us) and the installed [AI authoring handbook](ai-support_us) for the available surface.

<a id="wiki-section-8"></a>
## Importing Java classes (`java:` modules)

Java packages/classes can be imported as special modules. **Only the `java:` prefix and slash-separated paths are accepted**.

<a id="wiki-section-9"></a>
### Package modules (lazy namespace proxy)

```ts
import { Integer, $Integer, Math as JavaMath } from 'java:java/lang'
const { Integer, $Integer, Math: JavaMath } = require('java:java/lang')
```

- Ordinary names (such as `Integer` and `Math`) use property lookup.
- Names with a `$` prefix, such as `$Integer`, map directly to `Java.type('java.lang.Integer')`.
- `Integer`, `$Integer`, and `Math` / `JavaMath` all work.

<a id="wiki-section-10"></a>
### Class modules (return a Java class proxy directly)

```ts
import IntegerClass, { $Integer } from 'java:java/lang/Integer'
const IntegerClass = require('java:java/lang/Integer')
```

- Returns the Java class proxy directly.
- Also exposes `default` and `$Class`.
- This is the most direct form when you only need one specific Java class.

<a id="wiki-section-11"></a>
### Rules

| Rule | Description |
|---|---|
| Only the `java:` prefix is accepted | Prefixes such as `org.example:` are not supported |
| Only slash-separated paths are accepted | `java:java/lang`, `java:java/lang/Integer` |
| `.` / `..` are not supported | — |
| Dynamic `import('java:...')` | Returns a synthetic ESM module with `default` / `namespace` |

<a id="wiki-section-12"></a>
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

<a id="wiki-section-13"></a>
## Security sandbox

NekoJS limits what scripts can access:

- **Java classes**: high-risk classes are filtered (`java.lang.Runtime`, `Process`, `ClassLoader`, `System`, `java.io.*`, `java.nio.*`, reflection, net, lwjgl, polyglot itself, as well as `java.awt`/`javax.swing`/`javax.imageio`, `javax.naming`, `java.rmi`, `java.sql`/`javax.sql`, `java.lang.Module`, `org.graalvm`/`com.oracle.truffle`, and NekoJS internals such as `com.tkisor.nekojs.core`). See `common/.../core/fs/ClassFilter.java` for the specific denylist.
- **File system**: access is limited to the game directory, with symlink escape checks.
- **Configuration**: `nekojs/config/engine.toml` (in the same directory as probe.toml; the old `config/nekojs-engine.toml` is a read-only fallback) can configure `allowThreads`, `allowReflection`, `allowAsm`, `enableEsmAuthoring`, etc. The three removed execution-budget keys are pruned when a writable current configuration is saved; the legacy fallback remains read-only and is not rewritten. The private offline Mixin declaration sandbox retains its fixed 100,000-statement limit, independently of normal execution.
- **Execution duration**: normal script execution has no module-evaluation deadline, statement budget or runaway watchdog. `scriptEvaluationTimeoutSeconds`, `scriptStatementLimit` and `scriptRunawayTimeoutSeconds` have been removed, with no replacement default timeout. Finite code is not stopped by these budgets; unresolved top-level await can remain pending, and infinite guest/host loops can block the owning thread or process. Lifecycle cancellation, shutdown/preemption and external request deadlines remain separate mechanisms, not guarantees that arbitrary host work can be interrupted.
- **HostAccess limits**: the name-based denylist only blocks **class lookup** such as `Java.type`; object graphs returned by Java methods are controlled by Graal `HostAccess` (currently `HostAccess.ALL`), so instances of denylisted classes can still reach scripts through method return values.

> Treat scripts and their dependencies as **trusted code**. Class-lookup filters and filesystem checks do not make arbitrary Java host calls safe; there is no automatic watchdog to rescue an infinite loop. Do not run untrusted third-party scripts, especially with multiplayer script distribution.

<a id="wiki-section-14"></a>
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

<a id="wiki-section-15"></a>
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
export const ORES = ['minecraft:iron_ore', 'minecraft:gold_ore']
```

```javascript
// lib/recipe_helpers.js
export function dustify(event, ore, output) {
  event.smelting(Item.of(output), Ingredient.of(ore)).id(`nekojs:smelt_${ore.split(':')[1]}`)
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

<a id="wiki-section-16"></a>
## Next steps

- [TypeScript and JSX](typescript-and-jsx_us) - Writing scripts in TS.
- [Node.js compatibility](nodejs-compatibility_us) - Available modules such as `fs`/`path`/`buffer`.
- [Global bindings](global-bindings_us) - Top-level APIs.

<!-- wiki-nav -->

---

[Previous: Registering new content](registering-new-content_us) · [Contents](Home) · [Next: TypeScript and JSX](typescript-and-jsx_us)
