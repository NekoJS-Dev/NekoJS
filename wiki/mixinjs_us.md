<!-- wiki-page: mixinjs; locale: us -->

> **English** · [中文](mixinjs_cn)

<a id="wiki-section-1"></a>
# MixinJS (experimental)

MixinJS separates native method injection into **startup-loaded declarations** and **callbacks replaced by script reload**. NeoForge 26.x prepares an internal game library automatically; legacy NeoForge and Fabric use dependency-bearing carrier mods. It does not dynamically add Mixins to a running game.

> **Development experiments only.** Nonempty carriers are rejected in production. Build/JVM checks are not gameplay acceptance. A bounded NeoForge 26.1.2 development dedicated-server smoke exercised loader discovery, Sponge transformation and one non-cancellable SERVER callback; that does not establish full native ABI, client, cross-loader or production-mapping acceptance. See the evidence boundary below. This is not a production deployment guarantee.

<a id="wiki-section-2"></a>
## 1. Separate declarations from callbacks

| Content | Location | Activation |
|---|---|---|
| Injection declarations: target class, method descriptor, injection point, Hook ID | `nekojs/mixins/*.mixin.js` or `*.mixin.ts` | NeoForge 26.x: automatic preparation and restart; legacy/Fabric: offline build, install and restart |
| Runtime callbacks: read arguments and replace results | `nekojs/server_scripts/` or `nekojs/client_scripts/` | Load or reload the matching Script Type |

Declarations are not ordinary script entries. Only offline declarations import `nekojs/mixin`; ordinary scripts use the `MixinEvents` Event Group.

<a id="wiki-section-3"></a>
## 2. Write an offline declaration

For example, create `nekojs/mixins/max-health.mixin.js`:

```js
import { Mixin } from "nekojs/mixin";

Mixin.target("net.minecraft.world.entity.LivingEntity", target => {
    target.inject("getMaxHealth()F", "demo:max_health", build => {
        build.at = "RETURN";
        build.cancellable = true;
        build.scriptType = "SERVER";
        build.thread = "SERVER_MAIN";
        build.require = 1;
    });
});
```

The class and method names must match the **exact runtime namespace** of the class bytes supplied to the tool. Do not assume these example names, or development mapping names, apply to every version and loader.

| Field | Supported values | Default |
|---|---|---|
| `at` | `HEAD`, `RETURN` | `HEAD` |
| `cancellable` | Boolean; permits cancellation or result replacement | `false` |
| `scriptType` | `SERVER`, `CLIENT` | `SERVER` |
| `thread` | Matching `SERVER_MAIN` or `CLIENT_MAIN` | `SERVER_MAIN` |
| `require` | Only `1` | `1` |
| `staticMethod` | Whether the target is static; must match its bytecode | `false` |

A selector needs a complete JVM descriptor: `getMaxHealth()F` takes no arguments and returns float; `tick()V` takes no arguments and returns void. Hook IDs use `namespace:path` and must be unique across loaded carriers.

The offline evaluator has no Java lookup, filesystem, network or process API. Apart from `nekojs/mixin`, only confined relative static imports with explicit extensions are allowed; relative modules currently support side effects, not exports. Dynamic imports, top-level await, asynchronous builders, unknown fields and execution beyond the statement budget fail. Declaration callbacks must synchronously return `undefined`.

<a id="wiki-section-4"></a>
## 3. Prepare declarations

**NeoForge 26.1.2 / 26.2.0:** keep declarations in the development instance's `nekojs/mixins/`. NekoJS reads target class resources without initializing targets and automatically prepares `mods/nekojs-mixins-auto-<hash>.jar`. This is a loader game library, not an independent mod-list entry. You do not build or install a carrier manually. First preparation and structural changes stop startup with `NEKO-4090`; fully restart to use the new library. Removing all declarations clears the active selection and requires a restart if it was loaded. Compilation failure preserves the previous selection and stops startup. An atomic selection file under `nekojs/.cache/mixins/` enables only one hash; older immutable libraries remain inert to avoid replacing or deleting loader-locked archives on Windows. They may be removed while the game is stopped.

**Legacy NeoForge / Fabric, or offline validation:** use the tool below. A modern offline build also produces a game library; avoid combining manually installed artifacts with automatic workspace preparation.

The tool reads bytecode directly from a target JAR or class directory without initializing target classes. It requires this repository's build environment; it is not an in-game command.

To avoid PowerShell JSON quoting problems, create `mixin-args.json` at the repository root:

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

Replace `runtime-targets.jar` with your actual target bytecode path. The exact versions and physical side must match the development instance. The `26.2.0` repository node reports Minecraft `26.2` at runtime: use `--minecraft 26.2` with NeoForge `26.2.0.57` for that profile. Automatic preparation reads the native identity. For Fabric, use `--loader fabric` and the actual Fabric Loader version, not the Fabric API version.

After validation, change `validate` to `build`, append `"--output", "build/demo-mixins.jar"` to the argument array, and run the same command. Existing output files are never overwritten; choose a new path or handle the old file yourself.

The current tool uses the `runtime` namespace and explicitly disables remapping in generated Mixins. **There is no general development-to-production mapping conversion.** Missing classes, incorrect descriptors, static/instance mismatches and unsupported methods fail offline validation.

<a id="wiki-section-5"></a>
## 4. Activate and fully restart

For automatic NeoForge 26.x preparation, restart after the preparation notice. The next launch must actually admit the managed library before callbacks can load; merely finding the file on disk is insufficient. Do not edit the owned cache or install extra manual copies.

For legacy NeoForge / Fabric:

1. Place the carrier JAR in the development instance's `mods/` directory, keeping NekoJS and its dependencies installed.
2. Remove the old carrier when replacing it to avoid duplicate Hook IDs.
3. Fully stop and restart the instance so its native loader discovers the carrier during normal startup.
4. Confirm that Minecraft, loader/version, physical side and ABI match the current environment.

Legacy NeoForge and Fabric carriers declare **NekoJS as a mandatory loader dependency**; without it the loader rejects the carrier. All newly generated artifacts also have an early plugin gate: missing NekoJS, a mismatched profile or a production environment disables their injections before linking the runtime bridge. Thus an orphaned modern game library does not inject without NekoJS. This gate does not establish live-loader acceptance.

A declaration change takes effect only after preparation/build and a full restart. Ordinary reload cannot add, remove or reweave native injection structure.

`--side client` selects a physical client profile, which can include SERVER callbacks for an integrated server. `--side server` selects a dedicated-server profile and cannot contain CLIENT callbacks. These profile capabilities do not establish real dedicated-server acceptance.

<a id="wiki-section-6"></a>
## 5. Bind a Hook in an ordinary script

After the loader admits the carrier, create `nekojs/server_scripts/max-health.js`:

```js
MixinEvents.inject("demo:max_health", event => {
    event.returnValue = 40;
});
```

Do not write `event => event.returnValue = 40`: it returns a number and discards the control decision. Callbacks must synchronously return `undefined`; they must not be async functions or return Promises.

- Only matching SERVER or CLIENT scripts can bind; STARTUP and TEST are unsupported.
- Each installed Hook allows one callback. An unknown ID, wrong Script Type or duplicate binding fails candidate loading.
- The entry, callback origin and entire module session must be locally trusted. Remote dependencies, unknown origins and virtually generated callbacks cannot obtain binding authority.
- Script types with installed Hooks use the candidate transaction even on initial load. Successful reload replaces callbacks; failure or duplicate bindings preserve the previous generation.
- World teardown clears SERVER and CLIENT callbacks. Load or reload the matching Script Type to bind them again. Process-lifetime injection structure remains installed.

See [Script basics](script-basics_us) and [Commands](commands_us) for script directories and reload usage.

<a id="wiki-section-7"></a>
## 6. Event object and control rules

| Member | Contract |
|---|---|
| `self` | Native receiver, or null for a static method. The property is read-only; the native object is not necessarily immutable. |
| `args` | Read-only native argument tuple; tuple elements cannot be replaced. |
| `returnValue` | Original result at RETURN; no result yet at HEAD. Writable only for cancellable non-void Hooks. |
| `cancel()` | Available only for cancellable void HEAD Hooks. Non-void HEAD Hooks must supply a return value. |

An unchanged RETURN preserves its original result. An exception, invalid write or non-`undefined` callback result discards all control writes from that invocation. Catching an invalid write does not restore earlier writes. **This does not roll back effects already applied to native objects, the world or other external resources.**

The control object is valid only during its synchronous callback and expires immediately afterward. Do not retain it for timers or asynchronous work. Candidate execution, wrong native threads and same-Hook reentry bypass callbacks and preserve native behavior; native invocations do not queue or wait for a script thread. Different Hooks can nest.

<a id="wiki-section-8"></a>
## 7. Types and unsupported capabilities

Supported values are boolean, one UTF-16 code-unit char, range-checked byte/short/int, finite float/double, assignable native references and native arrays. References accept null, and String results accept JavaScript strings. Ordinary JavaScript objects and arrays cannot impersonate arbitrary native references.

Currently unsupported: long/BigInt, constructors, class initializers, abstract/native/synthetic/bridge methods, injection points other than HEAD/RETURN, runtime classpath injection and dynamic reweaving.

After running [Probe](probe-type-generation_us), TypeScript receives literal Hook ID overloads and self/argument/result types only from **loader-admitted plans**. Pending declarations do not acquire installed-Hook types. If completion is missing, check whether the carrier loaded and its profile matches.

<a id="wiki-section-9"></a>
## 8. Troubleshooting and acceptance boundary

| Diagnostic | Check first |
|---|---|
| `NEKO-4090` | Preparation/restart notice, cache ownership, loader admission, declarations, descriptors, duplicate IDs, exact profile and production rejection. |
| `NEKO-4091` | Locally trusted sources, candidate registration authority and Runtime Root bridge ownership. |
| `NEKO-4092` | Synchronous return requirement, return ABI, expired views, threads or callback bypass. |

See [Error and log reference](error-reference_us) for details. Loader/Mixin transformation failures may be reported by the loader itself rather than these diagnostics.

Common runtime, carrier bytecode, TypeScript and five-node build checks pass. A NeoForge 26.1.2 development headless server also demonstrated automatic preparation, restart, real library discovery, Sponge transformation and one non-cancellable SERVER callback; the 26.2.0 node passed native startup. This is not full client/server, ABI, legacy/Fabric or mapping acceptance. Those checks remain release prerequisites; production admission stays disabled.

<!-- wiki-nav -->

---

[Previous: Client rendering and post effects](client-rendering_us) · [Contents](Home) · [Next: Node.js compatibility](nodejs-compatibility_us)
