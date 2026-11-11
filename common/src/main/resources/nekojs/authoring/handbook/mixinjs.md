# Experimental MixinJS

MixinJS separates startup/offline native injection preparation from reloadable callbacks. It is development-only: production admission is rejected until full loader and production mapping acceptance is completed. One NeoForge development dedicated-server hook is verified; do not generalize that smoke result into complete platform or production support.

## Declare native hooks

Place declarations under `nekojs/mixins`, outside ordinary script directories. The isolated declaration evaluator accepts `.mixin.js` and `.mixin.ts`, static `nekojs/mixin` imports and confined relative side-effect imports. It has no Java lookup, filesystem, process or network API. Exports, dynamic imports, top-level await and asynchronous builders are rejected.

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

On NeoForge 26.1.2 and 26.2.0, NekoJS automatically compiles this directory during startup into the owned `mods/nekojs-mixins-auto-<hash>.jar` game library. It is not an independent mod-list entry and needs no manual carrier build or installation. First preparation, source/profile changes and removal of a loaded library stop startup with NEKO-4090 and require a full restart. Only actual loader admission permits runtime hooks. An atomic hash selection under `nekojs/.cache/mixins/` enables one immutable library; removing declarations disables that selection. Old libraries remain inert and may be removed while the game is stopped, avoiding Windows loader archive locks. Failed compilation leaves the previous selection intact and stops startup; an unowned cache is never overwritten or deleted. Remove manual artifacts before enabling automatic preparation. Target resources are read without class initialization; exact runtime names remain required.

On legacy NeoForge and Fabric, use a standard carrier mod. It declares NekoJS as a mandatory loader dependency, so the loader rejects it when NekoJS is absent. Every newly generated artifact also has an early metadata-only plugin gate: missing NekoJS, a mismatched physical profile or production disables injection before linking the runtime bridge. This also protects orphaned modern game libraries. Live-loader acceptance remains incomplete: NeoForge 26.1.2 development preparation/restart and one dedicated-server callback are verified, but legacy/Fabric dependencies, orphan libraries without NekoJS, clients and the full ABI/control matrix still need native acceptance.

For legacy/Fabric builds or offline validation, use the offline pack tool `mixins validate` or `mixins build` with `--source`, `--classes`, `--minecraft`, `--loader`, `--loader-version`, `--side`, and (for build) `--output`. Pass tool arguments through the existing `:common:packTool` task and its JSON-array `packArgs` property. `--classes` is an exact runtime-namespace target JAR or class directory, not an arbitrary development mappings JAR. The tool reads class bytes without initializing targets, checks the exact descriptor and static/instance form, and creates a modern NeoForge game library or a legacy/Fabric carrier mod. Existing output files are never overwritten.

Injection targets are exact method implementations, not virtual-method families. A subclass override that does not call the targeted superclass implementation bypasses that injection. Check the actual receiver class and override chain before choosing targets, especially when sharing hooks between integrated and dedicated servers. Probe completion and `require = 1` confirm the declared target, not that every virtual call reaches it.

For manual builds, install the artifact in the native loader mod directory and fully restart. Structural changes, hook IDs and descriptors require preparation/rebuilding and restarting; script reload does not reweave classes. The admitted carrier must match Minecraft, loader/version, physical side and ABI exactly. The repository node named 26.2.0 reports Minecraft 26.2 at runtime; manual builds must use --minecraft 26.2 with NeoForge 26.2.0.57, not infer the Minecraft identity from the node directory. A client profile can carry SERVER callbacks for an integrated server; a dedicated-server profile cannot carry CLIENT callbacks.

## Bind an installed hook

Only locally trusted SERVER or CLIENT candidates can bind an installed hook of the matching type. Unknown/virtual callback origins and sessions that loaded remote sources are rejected. Ordinary scripts do not import `nekojs/mixin`.

```js
MixinEvents.inject("demo:max_health", event => {
    event.returnValue = 40;
});
```

One callback is allowed per installed hook. When a script type has installed hooks, initial loading also uses the existing candidate transaction rather than activating callbacks incrementally. World teardown revokes SERVER and CLIENT hook callbacks; reload the corresponding script type to bind them again. Structural slots remain installed until the process ends. Candidate registration, duplicate preflight, commit, rollback, script/pack cleanup and root close use the existing generation lifecycle. The bridge bypasses callbacks during candidate execution, on the wrong native thread, or when the same slot reenters. It does not queue or block a native invocation. Probe emits literal hook overloads only from loader-admitted plans, never pending declaration files.

## Probe completion

Run the usual Probe generation after entering the matching development runtime. Probe writes an isolated `mixins/jsconfig.json` project and `typescript/@mixin/index.d.ts`. Declaration files import `Mixin` from `nekojs/mixin`: completion offers collected runtime class names, exact declared method selectors, hook ID shape and builder fields. Known classes reject unsupported selectors; unscanned classes accept a descriptor-shaped string, with exact validation still performed by the compiler. Constructors, inherited-only methods, abstract/native/synthetic/bridge methods and long-valued signatures are not suggested. Reflection does not initialize target classes. Set `staticMethod = true` for a static target; match `scriptType` and `thread` explicitly when using CLIENT.

Ordinary SERVER/CLIENT projects separately receive `MixinEvents.inject` literal IDs and typed `self`, readonly `args`, return replacement and void cancellation controls from loader-admitted plans. Pending declarations never create callback overloads. Re-run Probe after structural preparation and restart; callback-only reload does not require regeneration. The declaration project does not include ordinary script globals, Java imports or runtime subscriptions, and the declaration-only module is not exposed to ordinary scripts. Editor completion describes this exact runtime namespace, not cross-version or production mapping support.

## Control and ABI

Callbacks must synchronously return `undefined`; returning a Promise or another value discards the decision. `self` and the argument tuple are read-only. Cancellable non-void hooks replace `returnValue`; a cancellable void HEAD hook supports `cancel()`. Unchanged RETURN hooks preserve the native result. A callback exception or invalid write discards all writes from that callback, even if the script catches the invalid write. Retained event controls expire immediately after dispatch.

Allowed primitives are boolean, one UTF-16 code-unit char, range-checked byte/short/int, finite float/double, assignable host references and native arrays. Null is allowed for references; String accepts a JavaScript string. Constructors, class initializers, long/BigInt, abstract/native/synthetic/bridge methods and injection points other than HEAD/RETURN are rejected. There is no runtime classpath injection or dynamic reweaving.

## Acceptance boundary

Classfile and common runtime tests do not prove native loader discovery, real Mixin transformation, mapping correctness, dedicated-server isolation or production execution. Those checks remain release blockers. Current carriers explicitly use validated runtime names with remapping disabled; this is not a general development-to-production remapping solution.
