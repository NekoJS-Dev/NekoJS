<!-- wiki-page: script-properties; locale: us -->

> **English** · [中文](script-properties_cn)

<a id="wiki-section-1"></a>
# Script properties

Script properties are special comments at the start of a script file. They control how that file loads: its order, whether it loads at all, and which mods it needs.

<a id="wiki-section-2"></a>
## Syntax

Each property takes one line in the form `// name: value`:

```javascript
// priority: 100
// modloaded: create, jei
// after: lib/init.js

console.info('hello')
```

Properties must be at the **very start** of the file. NekoJS reads from the first line down and stops at the first line that is neither a `//` comment nor blank. So:

- Blank lines and ordinary `//` comments may appear between properties.
- Properties after an `import`, a `/* */` block comment or any code are not read.

A property in the wrong place, or a misspelled name such as `priorty`, **produces no error**. The property just has no effect. If the load order is wrong or `disable` does nothing, check these two things first.

After changing properties, reload that script type (for example `/nekojs reload`). Startup scripts need a game restart.

<a id="wiki-section-3"></a>
## All properties

| Property | Value | Default | Effect |
|---|---|---|---|
| `priority` | Integer | `0` | Higher numbers load first |
| `after` | Comma-separated paths | None | Load after the listed files |
| `modloaded` | Comma-separated mod IDs | None | Load only when **all** listed mods are present |
| `disable` | Anything, may be empty | Not disabled | Do not load this file |

<a id="wiki-section-4"></a>
## priority: load order

```javascript
// priority: 1000
```

Higher numbers load first. Negative numbers are allowed. Use it on "library" scripts that set up shared data so they run before everything else.

Startup scripts run in the same order, so registrations in a higher-priority startup script also happen first.

The value must be a single integer. Adding a note after it, as in `// priority: 10 load early`, makes the line invalid; put notes on their own line. An invalid value logs a `NEKO-1025` warning with the file and line number, and that line is ignored.

<a id="wiki-section-5"></a>
## after: load after specific files

```javascript
// after: lib/init.js, lib/constants.js
```

Path forms:

| Form | Meaning |
|---|---|
| `lib/init.js` | Relative to this script type's root, for example `server_scripts/lib/init.js` |
| `./helper.js` | Relative to the current file's folder |
| `lib/*` | Every file in the `lib/` folder |
| `server/lib/init.js` | Prefixed with the script type name (`startup`, `server`, `client`, `test`); same as `lib/init.js` |
| `nekojs:server/lib/init.js` | Same again, with a `nekojs:` prefix |

Separate several paths with commas. Backslashes `\` are treated as `/`.

**`after` only works between files with the same priority.** When two files have different priorities, the higher priority always loads first and `after` is ignored silently, with nothing in the log. So if `after` has no effect and the log is clean, the most likely cause is that the two files have different priorities.

References to a file skipped by `disable` or `modloaded` are also ignored silently.

When a path matches no file, the log shows a `NEKO-1004` warning. When several files `after` each other in a loop, `NEKO-1004` is reported too, and those files fall back to path order.

<a id="wiki-section-6"></a>
## modloaded: load only when mods are present

```javascript
// modloaded: create, jei
```

The file loads only when `create` and `jei` are **both** installed. Use it for integration scripts: without the mod, the script is skipped instead of failing.

Use mod IDs, not display names. You can find a mod's ID in the mod list.

<a id="wiki-section-7"></a>
## disable: turn a file off

```javascript
// disable:
```

If the file starts with a `disable` line, it does not load. The text after the colon does not matter: `// disable: false` **also** disables the file, so you can use it for a reason. To enable the file again, delete the line.

<a id="wiki-section-8"></a>
## How load order is decided

Scripts of one type load in this order:

1. By `priority`, highest first.
2. Among files with the same priority, `after` adjusts the order.
3. Everything else in alphabetical path order.

A simple approach: use `priority` to split files into batches, and `after` to order files inside a batch.

```javascript
// priority: 500
// modloaded: create
// after: lib/init.js

// This file:
// 1. Loads before files with the default priority 0
// 2. Loads only when Create is installed
// 3. Loads after lib/init.js if that file also has priority 500

console.info('Create integration initialized')
```

<a id="wiki-section-9"></a>
## Notes

- **Python scripts do not support script properties.** `# priority:` in a `.py` file is not read. See [Python scripts](python-scripts_us).
- **Script packs have their own dependency order.** A pack's declared prerequisites always load first; a higher priority cannot change that. See [Script packs](script-packs_us).
- Plugins can register new script properties. See [Plugin development](plugin-development_us).

See also: [Script basics](script-basics_us) and [Module system](module-system_us) (splitting code with `require` / `import`).

<!-- wiki-nav -->

---

[Previous: Script API conventions](script-api-style_us) · [Contents](Home) · [Next: Global bindings](global-bindings_us)
