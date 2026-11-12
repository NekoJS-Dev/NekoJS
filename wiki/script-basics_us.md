<!-- wiki-page: script-basics; locale: us -->

> **English** · [中文](script-basics_cn)

<a id="wiki-section-1"></a>
# Script basics

This page covers three things: which folder a script belongs in, how to apply your changes, and how to find errors.

<a id="wiki-section-2"></a>
## The four script types

NekoJS tells scripts apart by folder. Scripts in different folders run at different times and can use different APIs.

| Folder | When it runs | What it is for | How to apply changes |
|---|---|---|---|
| `startup_scripts/` | Once, while the game starts | Registering new items, blocks and entities | **Restart the game** |
| `server_scripts/` | When a world loads | Recipes, player and world events, custom commands | `/nekojs reload` |
| `client_scripts/` | While the game starts (client only) | HUD, key bindings, screens, item tooltips | `/nekojs reload client` |
| `test_scripts/` | Only when you run it | Tests that check your scripts behave correctly | `/nekojs test` |

A quick rule:

- **Adding new things** (items, blocks) → `startup_scripts`
- **Changing game rules or data** (recipes, events) → `server_scripts`
- **Changing what the player sees or presses** → `client_scripts`

<a id="wiki-section-3"></a>
## Which APIs each type can use

Server scripts can only use server APIs, and client scripts can only use client APIs. Using the wrong one fails immediately — for example, calling `ServerEvents.recipes(...)` in `client_scripts`.

| Script | Available APIs |
|---|---|
| Startup | Both server and client |
| Server | Server only |
| Client | Client only |
| Test | Server only |

The [Event reference](event-reference_us) and [Global bindings](global-bindings_us) note which script types can use each event and binding.

<a id="wiki-section-4"></a>
## File types

| Extension | Description |
|---|---|
| `.js` | Plain JavaScript; recommended for beginners |
| `.mjs` / `.cjs` | Explicitly an ES module / a CommonJS module |
| `.ts` | TypeScript; type annotations are removed before running, and types are not checked |
| `.jsx` / `.tsx` | JavaScript / TypeScript with JSX syntax |
| `.py` | The Python subset NekoJS supports; no Python installation needed |

See [TypeScript and JSX](typescript-and-jsx_us) and [Python scripts](python-scripts_us).

<a id="wiki-section-5"></a>
## Applying changes

Saving a file does **not** apply it. After a change, do the following for its script type:

| You changed | Do this |
|---|---|
| A server script | `/nekojs reload` (same as `/nekojs reload server`) |
| A client script | `/nekojs reload client` (on NeoForge, F3+T also reloads them) |
| A test script | `/nekojs test` |
| A startup script | Restart the game. `/nekojs reload startup` is rejected |

To rerun a single file, use `/nekojs reload server recipes.js` (the path is relative to `server_scripts`; TAB completes it). For everyday work, reloading the whole type is more reliable.

<a id="wiki-section-6"></a>
### What happens when a reload fails

When you reload a whole type (for example with `/nekojs reload`) and any one of the new scripts throws an error, **the whole reload is discarded and the game keeps using the last scripts that loaded successfully**. A mistake never leaves the game half old, half new. Fix the error and reload again.

Note that things a script already did are not undone. If a script wrote a file or gave a player an item before it failed, those changes stay.

<a id="wiki-section-7"></a>
### The recipe viewer did not refresh

After `/nekojs reload`, recipes work on the server immediately, so you can test them by crafting. Recipe viewers such as JEI or REI sometimes keep showing old recipes; re-enter the world to refresh them.

<a id="wiki-section-8"></a>
## Load order

Scripts of the same type load in file path order by default. To control the order, add a comment at the **very top** of the file:

```javascript
// priority: 100
```

Higher numbers load first; the default is 0. To make one file load after another, use `// after: lib/init.js`.

These comments must be at the start of the file. If they come after code, they are ignored without any error. See [Script properties](script-properties_us) for every available comment.

<a id="wiki-section-9"></a>
## Sharing data between scripts

Each script file has its own scope: a variable defined in one file is not visible in another. To share data:

- **`global`**: shared by scripts of the same type. For example, all server scripts share one `global`.
- **`shared`**: shared by scripts of all types. For example, a server script writes and a client script reads (single player only, within the same game process).

```javascript
// server_scripts/a.js
global.counter = global.counter ?? 0

// server_scripts/b.js
ServerEvents.tickPost(event => {
  global.counter++
})
```

`global` and `shared` keep their contents across reloads, but **are gone when the game closes**. They also cannot carry data between a server and a client in multiplayer.

To save data to disk, sync it to clients, or store it on players and items, see [Data and state](data-and-state_us).

<a id="wiki-section-10"></a>
## Finding errors

Script errors do not crash the game. Check in this order:

1. Run `/nekojs error` to see whether there are errors.
2. Run `/nekojs view_all_errors` to see the full list (on NeoForge it opens an error panel).
3. Open `logs/nekojs/<type>.log`, for example `logs/nekojs/server.log`.

An error message shows the file, line and column, and the line of code that failed:

```text
> 12 |   event.shapd('minecraft:stone', ...)
     |         ^
```

What happens next depends on the script type:

- **Server, client and test scripts**: if any file fails during a reload, none of that reload's changes apply and the last working version stays in use (see "What happens when a reload fails" above). Fix the error and reload.
- **Startup scripts**: the failing file is skipped and the others still run.
- **Errors inside event callbacks** (for example, code inside `ServerEvents.recipes(event => { ... })`): only that one error is recorded; the script and other events keep working.

<a id="wiki-section-11"></a>
### Spelling checks before loading

Before loading scripts, NekoJS checks spelling. If you write `event.rec`, it suggests "did you mean `recipes`?". These are hints only and never stop a script from running.

If there are too many hints or false alarms, set `scriptMemberValidation = false` in `nekojs/config/engine.toml`.

<a id="wiki-section-12"></a>
### Watch out for infinite loops

NekoJS does not forcibly stop a script that never finishes. An infinite loop, or an `await` on a Promise that never completes, freezes the game. Also keep work light in events that fire often, such as `tick`.

<a id="wiki-section-13"></a>
## Next steps

- [Script properties](script-properties_us): full usage of `priority`, `modloaded`, `disable` and `after`.
- [Commands](commands_us): every `/nekojs` command.
- [Global bindings](global-bindings_us): every ready-to-use helper.

<!-- wiki-nav -->

---

[Previous: FAQ](faq_us) · [Contents](Home) · [Next: Script API conventions](script-api-style_us)
