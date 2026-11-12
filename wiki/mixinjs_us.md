<!-- wiki-page: mixinjs; locale: us -->

> **English** · [中文](mixinjs_cn)

<a id="wiki-section-1"></a>
# MixinJS (experimental)

MixinJS lets a script run code at the start or the return of a Minecraft Java method. It can read the arguments, change the return value or cancel the call. It has two parts:

1. An **injection declaration** that says which method to inject into. The loader applies it at game startup, so changing it needs a game restart.
2. A **callback** in an ordinary script that decides what happens at the injection point. You can change callbacks at any time with `/nekojs reload`.

> **This is an experimental feature for development environments only.** In a normal game (production environment), NekoJS refuses to load injections. So far only simple cases have been verified, on a NeoForge 26.1.2 development dedicated server. Other versions, loaders and client setups have not been fully tested. Do not use it in a released modpack or on a real server.

<a id="wiki-section-2"></a>
## Before you start: turn MixinJS on

MixinJS is off by default. Add this to `nekojs/config/engine.toml`:

```toml
[mixinjs]
enabled = true
```

Then **fully restart the game**. Every later change to this switch, including turning it off, needs a full restart. `/nekojs reload` and `/nekojs probe` do not reread it.

- If the config file is missing or malformed, MixinJS stays off.
- MixinJS does not need `allowAsm` or `allowReflection`, and turning it on does not turn those on.
- While it is off, code completion for declaration files and the offline tool still work. Injections just do not take effect.

For other settings, see [Configuration](configuration_us).

<a id="wiki-section-3"></a>
## How it works

| Part | Where it goes | How it takes effect |
|---|---|---|
| Injection declaration: target class, method, injection point, Hook ID | `*.mixin.js` or `*.mixin.ts` under `nekojs/mixins/` | Generate the injection file, then fully restart the game |
| Callback: read arguments, change the return value | `nekojs/server_scripts/` or `nekojs/client_scripts/` | After the game loads the injection, load or reload it like any script |

The two parts are linked by a **Hook ID** such as `demo:max_health`. Declaration files use `import { Mixin } from "nekojs/mixin"`. Ordinary scripts use the global `MixinEvents`.

<a id="wiki-section-4"></a>
## 1. Write an injection declaration

This example sets every mob's maximum health to 40. Create `nekojs/mixins/max-health.mixin.js`:

```javascript
import { Mixin } from "nekojs/mixin";

Mixin.target("net.minecraft.world.entity.LivingEntity", target => {
    target.inject("getMaxHealth()F", "demo:max_health", build => {
        build.at = "RETURN";
        build.cancellable = true;
        build.scriptType = "SERVER";
        build.thread = "SERVER_MAIN";
    });
});
```

- The first argument is the **method name plus its full JVM descriptor**: `getMaxHealth()F` means no arguments, returns float; `tick()V` means no arguments, returns void.
- The second argument is the Hook ID, in the form `namespace:path`. It may only contain lowercase letters, digits, `_`, `.`, `-` and `/`. It must be unique across all loaded declarations.
- Class and method names must be the **names the running game actually uses**. They can differ between Minecraft versions and loaders. NekoJS does not translate mappings for you.

Fields you can set:

| Field | Values | Default |
|---|---|---|
| `at` | `HEAD` (start of the method), `RETURN` (when it returns) | `HEAD` |
| `cancellable` | Must be `true` to cancel the call or change the return value | `false` |
| `scriptType` | `SERVER`, `CLIENT` | `SERVER` |
| `thread` | Must match `scriptType`: `SERVER_MAIN` or `CLIENT_MAIN` | `SERVER_MAIN` |
| `require` | Only `1` | `1` |
| `staticMethod` | `true` when the target is a static method; must match the real method | `false` |

Note that the injection only applies to the method in the class you name. If a subclass overrides the method and does not call the parent version, calls on that subclass skip your injection.

Declaration files run in a separate, restricted environment:

- No access to Java classes, files or the network.
- They can import only `nekojs/mixin` and relative files with a `.js` or `.ts` extension. Imported files cannot export anything.
- No default imports, dynamic `import()` or top-level `await`.
- `build => { ... }` must finish synchronously and return nothing. Setting an unknown field is an error.
- One run may execute at most 100,000 statements.

<a id="wiki-section-5"></a>
## 2. Generate and load the injection

**NeoForge 26.1.2 and 26.2.0: generated for you.** Put your declarations in `nekojs/mixins/` and start the game. NekoJS generates `mods/nekojs-mixins-auto-<hash>.jar`, then stops this launch with `NEKO-4090` and asks you to restart. After the next start, the injection is active.

- The jar does not appear in the mod list, and you do not install it yourself.
- Every time you change a declaration, it is regenerated and you need to restart once more.
- After you delete all declarations, the next launch no longer loads the injection. If the injection was loaded at that point, you need one more restart.
- Old jars are not deleted, but they are not loaded either. Delete them by hand while the game is closed.
- Do not also install an injection jar by hand; that causes an error.

**Older NeoForge and Fabric: build it yourself.** This needs this repository's development setup. You run it with Gradle from the repository root; it is not an in-game command. First create `mixin-args.json` (a separate argument file avoids PowerShell quoting problems):

```json
[
  "mixins", "validate",
  "--source", "nekojs/mixins",
  "--classes", "runtime-targets.jar",
  "--minecraft", "26.1.2",
  "--loader", "neoforge",
  "--loader-version", "26.1.2.71",
  "--side", "client"
]
```

```powershell
./gradlew.bat :common:packTool -PpackArgsFile=mixin-args.json
```

- Replace `--classes` with a jar or folder that contains the target classes' bytecode.
- Versions must match the running environment exactly. On a 26.2.0 environment, the Minecraft version is `26.2`. For Fabric, use `--loader fabric` and set `--loader-version` to the Fabric Loader version, not the Fabric API version.
- `--side client` is for the client (including a single-player world's built-in server) and may contain both SERVER and CLIENT hooks. `--side server` is for a dedicated server and cannot contain CLIENT hooks.

Once validation passes, change `validate` to `build`, add `"--output", "build/demo-mixins.jar"` and run the same command. An existing output file is never overwritten, so pick a new path.

Put the generated jar in the development instance's `mods/`, remove any older copy, and fully restart the game. The jar depends on NekoJS; the loader rejects it if NekoJS is missing.

Either way, **after changing a declaration you must regenerate and fully restart**. `/nekojs reload` cannot add, remove or change injections.

<a id="wiki-section-6"></a>
## 3. Write the callback in a script

Once the injection is loaded, create `nekojs/server_scripts/max-health.js`:

```javascript
MixinEvents.inject("demo:max_health", event => {
    event.returnValue = 40;
});
```

Run `/nekojs reload`. Every mob's maximum health is now 40.

**The callback must not return a value.** Do not write `event => event.returnValue = 40`: that returns 40, and the change for that call is thrown away. The callback cannot be an `async` function or return a Promise either.

- Put the callback in the folder that matches the declaration's `scriptType`: `SERVER` in `server_scripts`, `CLIENT` in `client_scripts`. Startup and test scripts cannot use it.
- Each Hook takes exactly one callback. An unknown Hook ID, the wrong script type or a second registration makes that load fail, and the game keeps the last scripts that loaded successfully.
- Callbacks must come from local script files. Script packs synced from a server cannot register callbacks.
- A successful reload replaces the old callback with the new one.
- Leaving the world clears callbacks. They are registered again the next time the scripts load.

<a id="wiki-section-7"></a>
## 4. The event object

| Member | Meaning |
|---|---|
| `self` | The object the method was called on; `null` for a static method |
| `args` | The method's arguments, read-only |
| `returnValue` | At `RETURN`, the original return value; at `HEAD`, nothing yet. You can only change it when `cancellable = true` and the method returns a value |
| `cancel()` | Cancels the call. Only for a method with `cancellable = true` and `at = "HEAD"` that returns void |

- If you leave `returnValue` alone, the method returns its original result.
- If the callback throws, writes a value that is not allowed, or returns a value, **every change for that call is discarded** and the method runs as normal. Anything else the callback already did (such as changing a block in the world) is not undone.
- `event` is only valid while the callback runs. Do not keep it for a timer or async code.

The callback is skipped, and the method runs as normal, when:

- scripts are in the middle of a reload;
- the method is called on the wrong thread (for example a SERVER hook off the server main thread);
- the callback triggers the same Hook again while it runs.

A wrong-thread call or a failed callback logs a `NEKO-4092` warning once.

<a id="wiki-section-8"></a>
## 5. Supported types and limits

Arguments and return values can be: boolean, char (a single character), byte / short / int (range-checked), float / double (must be finite), and Java objects and arrays (the type must match; `null` is allowed). A method that returns String accepts a JavaScript string. A plain JavaScript object or array cannot stand in for a Java object.

Not supported yet:

- methods with `long` (including `long[]`) in their arguments or return value;
- constructors and static initializers;
- abstract, native, synthetic and bridge methods;
- injection points other than `HEAD` and `RETURN`;
- adding or changing injections while the game is running.

**Code completion:** declaration files always get completion for `nekojs/mixin`. When MixinJS is on and the injection is loaded, running [`/nekojs probe`](probe-type-generation_us) makes `MixinEvents.inject` suggest the types of `self`, the arguments and the return value for each Hook ID. If there are no suggestions, first check that the switch is on, then check that the injection really loaded.

<a id="wiki-mixinjs-troubleshooting"></a>
<a id="wiki-section-9"></a>
## Troubleshooting

| Error code | Check first |
|---|---|
| `NEKO-4090` | Whether you need to restart as prompted; whether `[mixinjs] enabled` is on; whether this is a development environment; whether the declaration's class name, descriptor and fields are right; duplicate Hook IDs; whether the build's version, loader and `--side` match the running game; whether you mixed a generated jar with a hand-installed one |
| `NEKO-4091` | Whether the callback comes from a local script file rather than a synced script pack |
| `NEKO-4092` | Whether the callback returns a value or is async; whether the return value has the right type and range; whether `event` is used after the callback ended; whether the Hook ran on the wrong thread |

For more error codes, see [Error and log reference](error-reference_us). Mixin errors reported by the loader itself do not carry these codes; check the game log.

<!-- wiki-nav -->

---

[Previous: Client rendering and post effects](client-rendering_us) · [Contents](Home) · [Next: Node.js compatibility](nodejs-compatibility_us)
