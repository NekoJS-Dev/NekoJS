<!-- wiki-page: script-properties; locale: us -->

> **English** · [中文](script-properties_cn)

<a id="wiki-section-1"></a>
# Script properties

Script properties are directives written as comments in script files that control how scripts load.

<a id="wiki-section-2"></a>
## Syntax

All properties are single-line comments in the form `// key: value`:

```javascript
// priority: 100
// modloaded: create, jei
// after: ./lib/init.js
// disable:

console.info('hello')
```

**Placement matters**: the scanner (`ScriptContainer.preload`) reads downward from the first line of the file and stops with `break` at the first line that is neither empty nor starts with `//`. Properties must therefore be in the **opening comment block**. Empty lines and other `//` comments may appear between them, but once an `import`, a `/* */` block comment, or any code appears, later `// priority:` lines are no longer read. Incorrect placement produces **no error or log message**; the property simply uses its default, causing symptoms such as an unexpected load order or an ineffective `disable`.

Unknown keys are also silently ignored (property names come from a registry; plugins can add them through `registerScriptProperty`, see [Plugin development](plugin-development_us)). Misspelling `priorty:` therefore fails silently too. Failed value parsing produces a warning.

<a id="wiki-section-3"></a>
## Property overview

| Property | Type | Default | Purpose |
|---|---|---|---|
| `priority` | Integer | `0` | Higher numbers load first |
| `modloaded` | Comma-separated list of mod ids | — | Runs only when **all** listed mods are present (AND semantics) |
| `disable` | Presence disables, including an empty value | Enabled | Disables the script |
| `after` | Comma-separated list of paths | — | Loads after the listed files |

<a id="wiki-section-4"></a>
## priority: control load order

```javascript
// priority: 1000   // Runs first
```

Higher numbers run first. This is often used for library scripts that initialize shared data or register global events, ensuring they execute before application scripts.

> The priority of STARTUP scripts also affects registration order (higher numbers register first).

<a id="wiki-section-5"></a>
## modloaded: conditional loading

```javascript
// modloaded: create, jei
```

This script runs only when **both** `create` **and** `jei` are loaded. It is commonly used for mod integration scripts to avoid errors when a mod is missing.

You can also add `graal` (the Graal dependency mod) to ensure the runtime is present.

<a id="wiki-section-6"></a>
## disable: temporarily disable a script

```javascript
// disable:
// disable: true
// disable: 暂时注释掉，等修好
```

An opening `// disable:` directive disables the script even when its value is empty. `// disable: false` also disables it; remove the directive to enable loading again.

<a id="wiki-section-7"></a>
## after: explicitly declare dependencies

Load the current script after specified scripts to establish explicit loading dependencies.

```javascript
// after: lib/init.js, lib/constants.js
```

Path formats:

| Form | Meaning |
|---|---|
| `aaa/bbb.js` | Path relative to the current script type's root directory |
| `./ccc.js` | Relative to the current file's directory |
| `nekojs/aaa/bbb.js` | Explicit `nekojs/` prefix, relative to the script type's root directory (equivalent to `aaa/bbb.js`) |
| `nekojs:<type>/bbb.js` | `nekojs:` prefix + script type root directory (for example `nekojs:server/lib.js`) |
| `<type>/bbb.js` | Starts with the script type directory name (for example `server/lib.js`) |
| `aaa/*` | All files in the `aaa/` folder |
| `aaa/bbb.js, ccc.js` | Multiple paths separated by commas |

Backslashes `\` in paths are always treated as `/`.

Dependency edges only apply between scripts **in the same priority group that both execute** (`shouldRun`). This creates two types of ignored dependencies with different symptoms:

| Situation | Handling |
|---|---|
| The referenced path does not exist anywhere in this batch | Ignored with a warning (`after 依赖排序存在问题：...`) |
| The reference matches a script, but it belongs to another priority group / is excluded by `disable` or `modloaded` / is the current script itself | **Silently** ignored, with no log message |

The most common reason for an ineffective `after` with no log messages is that the two files have different priorities.

<a id="wiki-section-8"></a>
## Complete example

```javascript
// priority: 500
// modloaded: create
// after: lib/init.js

// This script:
// 1. Has priority=500 and runs before default scripts
// 2. Runs only when Create is installed
// 3. Runs after lib/init.js
// 4. Loads Create integration recipes

ServerEvents.recipes(event => {
  event.recipes.create.mixing(
    'create:brass_ingot',
    ['minecraft:copper_ingot', 'create:zinc_ingot']
  )
})
```

<a id="wiki-section-9"></a>
## Relationship with priority

`priority` and `after` jointly determine load order (`ScriptLoadOrderSorter`; enforced by the engine during loading, not merely advisory):

1. First, perform a stable sort by descending `priority`.
2. Within each priority group, perform a stable topological sort (Kahn) using dependencies declared by `after`.
3. Where the order remains undecided, preserve discovery order. File discovery uses `Files.walk(...).sorted()`, which gives lexicographic path order.
4. If a cycle occurs within a group, **the entire group falls back** to the order from steps 1/3, with a warning listing the scripts involved in the cycle.

The recommendation to use `priority` for broad batches and `after` for precise ordering within a batch follows from these rules: `after` cannot cross priority groups.

<a id="wiki-section-10"></a>
## Next steps

- [Script basics](script-basics_us): script types and reload.
- [Module system](module-system_us): split files with `require`/`import`.

<!-- wiki-nav -->

---

[Previous: Script basics](script-basics_us) · [Contents](Home) · [Next: Global bindings](global-bindings_us)
