<!-- wiki-page: module-system; locale: us -->

> **English** · [中文](module-system_cn)

<a id="wiki-section-1"></a>
# Module system

Once your scripts grow, you will want to split them into several files. This page shows how to share code between files with `import` / `export` or `require`, how to use npm packages, and how to import Java classes directly.

<a id="wiki-section-2"></a>
## Split code into several files

ES modules (`import` / `export`) are the recommended style. For example, move constants and helpers into a `lib/` folder:

```text
nekojs/server_scripts/
├── lib/
│   ├── constants.js
│   └── recipe_helpers.js
└── main.js
```

```javascript
// lib/constants.js
export const ORES = ['minecraft:iron_ore', 'minecraft:gold_ore']
```

```javascript
// lib/recipe_helpers.js
export function smeltOre(event, ore, output) {
  event.smelting(Item.of(output), Ingredient.of(ore)).id(`nekojs:smelt_${ore.split(':')[1]}`)
}
```

```javascript
// main.js
import { ORES } from './lib/constants.js'
import { smeltOre } from './lib/recipe_helpers.js'

ServerEvents.recipes(event => {
  for (const ore of ORES) {
    smeltOre(event, ore, ore.replace('_ore', '_ingot'))
  }
})
```

Run `/nekojs reload`. A furnace now smelts iron ore and gold ore straight into ingots.

Keep these points in mind:

- Relative paths (`./`, `../`) start from **the folder of the current file**.
- Write the full file name, including the extension, for example `./lib/constants.js`.
- **Every file in a script folder is loaded as a script**, including files in `lib/`. Helper files should only export functions and constants. Do not register events or do one-time work at their top level.

Default exports, `import * as ns`, circular imports, `import.meta`, top-level `await` and dynamic `import()` also work. You can import JSON files too:

```javascript
import settings from './settings.json'
console.info(settings.enabled)
```

<a id="wiki-section-3"></a>
## CommonJS

You can also use `require` and `module.exports`:

```javascript
// utils.cjs
function calculateDamage(base, multiplier) {
  return base * multiplier
}
module.exports = { calculateDamage }
```

```javascript
// main.cjs
const { calculateDamage } = require('./utils.cjs')
console.info(calculateDamage(4, 1.5)) // 6
```

The two styles mix: `import` can load a `.cjs` file, and `require` can load an ES module.

<a id="wiki-section-4"></a>
## The file extension picks the module type

| Extension | Runs as |
|---|---|
| `.mjs` | ES module |
| `.cjs` | CommonJS |
| `.js` | Detected: an ES module if the file uses `import` / `export`, otherwise CommonJS |
| `.ts` / `.jsx` / `.tsx` | Compiled to JavaScript first, then detected like `.js` |

When in doubt, use `.js`. Writing `export` in a `.cjs` file is a syntax error.

`enableEsmAuthoring` in `nekojs/config/engine.toml` defaults to `true`. If you set it to `false`, every file runs as CommonJS and `import` / `export` become errors; they are not converted for you. You normally do not need to change it.

For TypeScript and JSX, see [TypeScript and JSX](typescript-and-jsx_us).

<a id="wiki-section-5"></a>
## Use npm packages

Put a pure-JavaScript package in `nekojs/node_modules/` and refer to it by name:

```javascript
// nekojs/node_modules/greeting/index.js
module.exports = name => `Hello, ${name}!`
```

```javascript
// nekojs/server_scripts/use_greeting.js
const greet = require('greeting')
console.info(greet('NekoJS')) // Hello, NekoJS!
```

NekoJS looks for `greeting` in this order:

1. the file `node_modules/greeting` itself;
2. that name plus an extension, such as `node_modules/greeting.js`;
3. an `index` file in that folder, such as `node_modules/greeting/index.js`.

<a id="wiki-section-6"></a>
### npm package limits

- **The `main` and `exports` fields of `package.json` are not read.** If the entry is not `index.js`, give the full path, for example `require('some-lib/dist/some-lib.js')`.
- **Packages with native code do not work**, for example `sharp`, `better-sqlite3` or `canvas`.
- NekoJS provides only some Node.js core modules; see [Node.js compatibility](nodejs-compatibility_us). A package that needs other Node APIs will not run.
- Package code has the same limits as your scripts. It can only access files inside the game folder.

The quickest way to find out whether a package works is to drop it in and try it.

<a id="wiki-api-modules"></a>
<a id="wiki-section-7"></a>
## Built-in API modules

Most NekoJS features are global variables that you use directly, such as `Item` and `ServerEvents`. Some of them can also be imported from built-in modules:

| Module | Contents |
|---|---|
| `nekojs/api/stable` | Stable APIs such as `ID` and `Native`; client scripts also get `I18n` |
| `nekojs/api/version` | APIs tied to the Minecraft version, such as `Item` and most event groups |

```typescript
import { ID } from 'nekojs/api/stable'
import { Item } from 'nekojs/api/version'

const id = ID.resource('minecraft:stone')
const stack = Item.of('minecraft:stone')
```

```javascript
const { ID } = require('nekojs/api/stable')
```

You do not install these modules. Importing a name that the current script type does not have is an error. For example, `ServerEvents` in `nekojs/api/stable` exists only in server scripts and has just two events, `tickPre` and `tickPost`.

Plugins can publish their own modules at `nekojs/api/stable/<modid>` or `nekojs/api/version/<modid>`. An API published only as a module does not become a global variable.

The code completion generated by `/nekojs probe` shows which names you can import right now. For the full list of globals, see [Global bindings](global-bindings_us).

<a id="wiki-section-8"></a>
## Import Java classes

Use the `java:` prefix with a slash-separated path to import a Java package or class.

**Import a package**, then take classes from it:

```javascript
import { ArrayList } from 'java:java/util'

const list = new ArrayList()
list.add('hello')
console.info(list.size()) // 1
```

When a class name clashes with a built-in JavaScript name (such as `Math`), add a `$` in front:

```javascript
import { $Math, Integer } from 'java:java/lang'
console.info($Math.max(3, 7))   // 7
console.info(Integer.MAX_VALUE) // 2147483647
```

**Import a single class**; the default export is the class itself:

```javascript
import ArrayList from 'java:java/util/ArrayList'
const HashMap = require('java:java/util/HashMap')
```

Path rules:

| Rule | Example |
|---|---|
| Must start with `java:` | `java:java/util` |
| Use slashes, not dots | Write `java:java/util/ArrayList`, not `java:java.util.ArrayList` |
| No `.` or `..` segments | `java:../foo` is an error |

You can also skip modules and write `Java.type('java.util.ArrayList')`.

> Prefer a NekoJS binding when one exists. For example, use `Item.of(...)` instead of importing Minecraft's item classes. Bindings are shorter, have code completion and hold up better across Minecraft versions. Keep `java:` imports for things the bindings do not cover yet.

<a id="wiki-section-9"></a>
## Top-level await and dynamic import

An ES module can use `await` at the top level, and it can load a file only when needed:

```javascript
const config = { enableExtras: true }

if (config.enableExtras) {
  const extras = await import('./lib/extras.js')
  extras.setup()
}
```

An ES module that uses top-level `await` cannot be loaded with `require`. You get `Cannot require async ESM module with top-level await: ... Use import() instead.` Switch to `import()` as the message says.

<a id="wiki-section-10"></a>
## What happens to modules on reload

- Saving a file does not apply it. Run `/nekojs reload` (or the reload command for that script type).
- A reload runs every module again, so module variables start from their initial values.
- To keep data across reloads, use `global`; see [Script basics](script-basics_us). Do not store objects exported by an old module. They are stale after a reload.
- When you reload a single file (for example `/nekojs reload server main.js`), changed modules that it imports are reloaded too.

<a id="wiki-section-11"></a>
## Security limits

Scripts cannot use Java and files freely:

- **Dangerous Java classes cannot be imported**, for example `java.lang.Runtime`, `java.lang.Process`, `java.lang.System`, `java.io`, `java.nio`, `java.net`, reflection and class loaders. Both `java:` and `Java.type` refuse them.
- **Scripts can only access files inside the game folder**, and by default can only write inside `nekojs/`. See [Node.js compatibility](nodejs-compatibility_us).
- Creating threads, reflection and bytecode manipulation are off by default. The switches are `allowThreads`, `allowReflection` and `allowAsm` in `nekojs/config/engine.toml`; see [Configuration](configuration_us).

These limits stop common mistakes, but they **do not make an untrusted script safe**. For example, objects returned by Java methods are not checked against the class filter, and NekoJS does not stop an endless loop. Only run scripts and npm packages that you trust.

<a id="wiki-section-12"></a>
## Next steps

- [TypeScript and JSX](typescript-and-jsx_us): write scripts in `.ts`, `.jsx` and `.tsx`.
- [Node.js compatibility](nodejs-compatibility_us): the Node modules you can use, such as `fs`, `path` and `buffer`.
- [Global bindings](global-bindings_us): tools you can use directly.

<!-- wiki-nav -->

---

[Previous: Registering new content](registering-new-content_us) · [Contents](Home) · [Next: TypeScript and JSX](typescript-and-jsx_us)
