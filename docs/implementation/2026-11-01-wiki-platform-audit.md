# Wiki platform and authoring audit — 2026-11-01

> Historical audit snapshot: later runtime work supersedes the recorded PData/rendering gaps; see [runtime delivery](2026-11-01-rendering-and-pdata-delivery.md). Source links now open the evolving working tree; obsolete out-of-range renderer anchors were removed without rewriting the original findings.

## Scope and evidence level

Task 26 updated the English/Chinese entry, platform, Python, TS/JSX, JSX UI, Probe, and build pages and added the client-rendering pair. This is a documentation/source audit, not a runtime change, release announcement, or visual acceptance record. No Gradle or Minecraft was run for this task. Existing unrelated wiki changes were preserved; the Lead owns canonical metadata, navigation generation, stamps, and the integrated wiki gate.

## Changed pages

| Topic | English | Chinese | Main correction |
|---|---|---|---|
| Home | [Home](../../wiki/home_us.md) | [首页](../../wiki/home_cn.md) | Current five-node graph versus publication evidence; explicit SERVER reload; style/data/rendering routes |
| Quick start | [Quick start](../../wiki/quick-start_us.md) | [快速开始](../../wiki/quick-start_cn.md) | Observable first log, whole-SERVER recipe follow-up, disposable-world registration and cleanup, independent AI root |
| FAQ | [FAQ](../../wiki/faq_us.md) | [常见问题](../../wiki/faq_cn.md) | Matching reload target, generation replacement, nominal tick rate, no legacy viewer guarantee, live-AI security boundary |
| Platforms | [Platforms](../../wiki/platform-compatibility_us.md) | [平台](../../wiki/platform-compatibility_cn.md) | Loader/API subset, GraalMC prerequisite, rendering feature split and Fabric empty Registry query fallback |
| Python scripts | [Python scripts](../../wiki/python-scripts_us.md) | [Python 脚本](../../wiki/python-scripts_cn.md) | Current builtin/helper behavior and actual Python/JS representation differences |
| Python feature support | [Feature support](../../wiki/python-feature-support_us.md) | [特性支持](../../wiki/python-feature-support_cn.md) | Erased Self annotation versus typing runtime, supported min/max key lowering |
| TypeScript and JSX | [TS/JSX](../../wiki/typescript-and-jsx_us.md) | [TS/JSX](../../wiki/typescript-and-jsx_cn.md) | Attribute entity decoding, automatic-runtime key argument, syntax/host distinction |
| JSX client UI | [JSX UI](../../wiki/jsx-client-ui_us.md) | [JSX UI](../../wiki/jsx-client-ui_cn.md) | Bean properties and source-backed registered-renderer limits/routes |
| Client rendering | [Client rendering](../../wiki/client-rendering_us.md) | [客户端绘制](../../wiki/client-rendering_cn.md) | New platform matrix, complete copy/run/observe/cleanup examples, declaration versus activation |
| Probe | [Probe](../../wiki/probe-type-generation_us.md) | [Probe](../../wiki/probe-type-generation_cn.md) | Actual jsconfig location, in-place output semantics, independent AI snapshot/agent paths |
| Build system | [Build](../../wiki/build-system_us.md) | [构建](../../wiki/build-system_cn.md) | Shared node-source convention, node-local precedence, deleted Fabric bridge, actual-input CI verification |

## Source evidence and retained limits

### Runtime, platform, and first examples

- The five-node graph is declared in [settings](../../settings.gradle.kts#L35-L41). Source/build nodes do not prove that every artifact has been published or visually accepted. Cleanroom is outside this graph; current pages no longer imply that its behavior was checked.
- The GraalMC dependency version is declared in the [version catalog](../../gradle/libs.versions.toml#L23-L28). Python uses the same GraalJS path, not CPython or GraalPy; see the real-context [Python runtime fixtures](../../common/src/test/java/com/tkisor/nekojs/core/module/NekoPythonRuntimeTest.java#L68-L216).
- Whole-type SERVER reload performs recipe follow-up separately from root generation reload in [NekoJSCommands](../../src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java#L387-L410); the quick start does not substitute single-file reload. Example registration omits resources/tab setup deliberately and warns against deleting IDs used by saved worlds.
- Recipe query/action methods reviewed include `ids`, `removeById`, and `get` in [RecipeEventJS](../../src/main/java/com/tkisor/nekojs/wrapper/event/server/RecipeEventJS.java), and explicit stable IDs in [RecipeJsonBuilder](../../src/main/java/com/tkisor/nekojs/api/recipe/RecipeJsonBuilder.java#L71-L86). Generated IDs are not a replacement for stable IDs in reloadable examples.
- Fabric Registry startup support and the runtime `Registry` query Binding are separate surfaces: the latter uses the empty service fallback when the platform supplies no query service. The platform matrix records this gap rather than presenting it as a cross-loader DataMap replacement.

### Client drawing and effects

- [RenderRegistrationBusJS](../../src/main/java/com/tkisor/nekojs/client/render/RenderRegistrationBusJS.java) accepts `(id, callback)` and `(id, options, callback)`. Existing renderer configuration objects are not invented builder overloads. [ClientRenderRegistry](../../src/main/java/com/tkisor/nekojs/client/render/ClientRenderRegistry.java#L266-L317) sorts ascending priority; equal-priority order is not a stable contract. Full candidate publication replaces the renderer batch.
- [ClientRenderEvents](../../src/main/java/com/tkisor/nekojs/client/render/ClientRenderEvents.java#L28-L79) defines the actual HUD/world stage mapping. [WorldRenderContextJS](../../src/main/java/com/tkisor/nekojs/client/render/WorldRenderContextJS.java) explicitly skips immediate line/box drawing on 26.2; callback availability is not drawing completion. The docs retain this implementation gap.
- Fabric Painter versus NeoForge registered-renderer availability is covered by [client GUI surface fixtures](../../src/test/java/com/tkisor/nekojs/client/render/Ticket27ClientGuiRenderSurfaceTest.java#L182-L212). The new page separates Painter, registered HUD/world renderers, retained JSX Screens, and post effects.
- Post-effect collection/commit and runtime activation are distinct in [PostEffectEventJS](../../src/main/java/com/tkisor/nekojs/client/posteffect/PostEffectEventJS.java#L68-L113), [PostEffectDomainOwner](../../src/main/java/com/tkisor/nekojs/client/posteffect/PostEffectDomainOwner.java), and [PostEffectsJS](../../src/main/java/com/tkisor/nekojs/client/posteffect/PostEffectsJS.java#L70-L150). The [1.21.1 event implementation](../../versions/1.21.1/src/main/java/com/tkisor/nekojs/client/posteffect/PostEffectEventJS.java#L44-L76) accepts the legacy `chainJson`/`program` forms, not the modern blur configuration. [Declaration parity fixtures](../../src/test/java/com/tkisor/nekojs/client/posteffect/PostEffectDeclarationParityTest.java#L40-L125) cover the separate declaration/runtime APIs and Fabric absence.
- The G-key effect sample is NeoForge 26.x only because it needs PostEffects. Fabric nevertheless has its own [KeyBindEvents twin](../../src/fabric/java/com/tkisor/nekojs/bindings/event/client/KeyBindEvents.java#L25-L48); the shared NeoForge file guard alone is not proof of Fabric absence. Native class bindings and JSX scope are contributed by [NekoJSCorePlugin](../../src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java#L224-L232).

### Language implementation

- [PythonEmitter](../../common/src/main/java/com/tkisor/nekojs/core/compiler/python/PythonEmitter.java#L101-L213) implements percent formatting, Python-style supported-value string conversion, list addition, and literal/count string replacement. Helper lowering also handles truthiness-based bool/any/all/filter, zero-checked floor division/modulo/divmod, builtin isinstance probes, and split maxsplit. These are not plain JS operator/string-method substitutions.
- [Python compiler tests](../../common/src/test/java/com/tkisor/nekojs/core/compiler/PythonToJsCompilerTest.java) contain list concatenation, percent formatting, str, replace, maxsplit, builtin probes, and min/max key cases. The docs preserve genuine limits: representation-based list/tuple and numeric probes, identity-based container equality, non-CPython arbitrary raise values, JS-coerced container operators, unsupported general Python packages/decorators, and incomplete CPython semantics.
- [PythonParser](../../common/src/main/java/com/tkisor/nekojs/core/compiler/python/PythonParser.java#L459-L465) erases annotations; writing `Self` as an annotation does not provide a typing module at runtime.
- [NekoJsxCompiler](../../common/src/main/java/com/tkisor/nekojs/core/compiler/NekoJsxCompiler.java#L547-L596) decodes quoted attribute entities as well as text entities. [JSX compiler tests](../../common/src/test/java/com/tkisor/nekojs/core/compiler/NekoJsxCompilerTest.java#L63-L104) distinguish automatic third-argument key extraction from classic props. JSX syntax support does not imply arbitrary Screen tags, CSS, React, DOM, or props support.

### Probe and build outputs

- [TypeScriptProbeBackend](../../common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/TypeScriptProbeBackend.java#L375-L433) contributes `.neko_probe/jsconfig.json` plus each script-root jsconfig, not `typescript/jsconfig.json`. Alias and include management remains a merge, with managed entries refreshed. Optional manual/builder declarations are shown without promising that every run emits them.
- [ProbeOutputCommitter](../../common/src/main/java/com/tkisor/nekojs/probe/ProbeOutputCommitter.java#L26-L40) synchronizes files in place. Render/path validation can preserve old output, but an I/O failure during writes can leave partial output; the docs no longer call the operation directory-atomic.
- [AuthoringPaths](../../common/src/main/java/com/tkisor/nekojs/authoring/AuthoringPaths.java) independently selects the hidden AI root; changing Probe output directories does not move it. [TypeScript snapshot publication](../../common/src/main/java/com/tkisor/nekojs/probe/backend/typescript/TypeScriptProbeBackend.java#L84-L105) follows successful declaration generation. Failed generation retains the old pointer; failed publication is a warning, not proof of a fresh snapshot. Selected-root agent roles are not editor autodiscovery; the optional standard bridge defaults off.
- [node-sources convention](../../buildSrc/src/main/kotlin/nekojs.node-sources.gradle.kts#L5-L20) gives node-local source/resource overrides precedence. [Fabric convention](../../buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts#L94-L108) deletes the generated bridge and mounts raw Fabric sources, rather than preprocessing another bridge. [CI](../../.github/workflows/ci-build.yml#L143-L146) runs the regression and [actual source-input verifier](../../tools/verify-node-source-selection.init.gradle#L24-L44); this report does not claim a new build run.

## Verification and handoff

- Passed: source/test inspection and bilingual example/profile comparison; pre-existing numeric section anchors retained in modified topics. The Probe AI-root insertion is a bold label, not a new heading that shifts old anchors.
- Passed: scoped read-tool Markdown audit of 23 files and 367 local links/anchors; balanced fenced blocks and no trailing whitespace. Fixed literal pipe escaping in Python/Probe tables without changing runtime examples. Numeric Java overload syntax in inline code was excluded correctly from link parsing.
- Passed: final table-column audit across all 22 wiki pages (0 problems); `git diff --check -- wiki docs/implementation/2026-11-01-wiki-platform-audit.md` (exit 0).
- Lead-owned: canonical navigation synchronization, topic source-hash stamps, strict wiki checks, and integrated test evidence.
- Not run: Gradle, live Minecraft, shader compilation/visual acceptance, multiplayer client/server acceptance, release artifact installation, and maintainer acceptance. Source/fixture availability is not substituted for these checks.
- Known remaining implementation gaps: NeoForge 26.2 world immediate line/box helpers do not draw; Fabric lacks registered renderers, PostEffects, and retained JSX UI; Fabric Registry queries use the empty fallback. Python remains a JS-backed subset; I/O-failed Probe commits can be partial.
