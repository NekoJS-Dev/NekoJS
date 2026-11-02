# Render registration and post-effect authoring interfaces

## Scope and contracts

Task 29 closes runtime/Probe interface gaps without editing world-draw implementations, Fabric persistent data, wiki pages or the bundled handbook. No Gradle or Stonecutter commands were run by this worker; the Lead owns integrated verification and generated golden updates.

### HUD and world rendering

The production registration bus retains these intentional forms:

	type RenderRegistration = (id, callback) | (id, options, callback)

- HUD options: `layer?: "background" | "normal" | "foreground"` and `priority?: number`.
- World options: `layer?: "early" | "normal" | "late"` and `priority?: number`.
- Bare layer strings and bare integer priorities remain accepted. Existing case/whitespace normalization of layer strings remains intentional compatibility; generated literal completions use canonical lowercase names.
- Omitted fields retain NORMAL/0 defaults. Priorities must be finite signed 32-bit integers; unknown keys, nulls, arrays, callable options, wrong member types, fractions and overflow now fail before registration rather than being ignored/truncated.
- Layer names are derived from the actual registry enums, not a second handwritten Probe list.
- HUD callbacks receive the actual HudRenderContextJS and version-specific raw graphics type resolved from graphics(); world callbacks receive WorldRenderContextJS. Registrations return true, reflected as boolean.

ScriptOptionSchema is the single runtime validator and structured AdapterInputShape provider. EventBusJS.callSignatures supplies opt-in callback-last registration metadata, copied through both catalog snapshot variants. Ordinary event subscriptions keep their previous emitter path. Probe reuses its recursive shape renderer and reference/import collection instead of switching on event names or adding cosmetic declaration strings.

### Post-effect declarations

Both NeoForge node implementations now support real callback-last builders:

	ClientEvents.postEffects(event => {
	  event.register("nekojs:example", builder => {
	    builder.chainJson = "{... version-appropriate chain JSON ...}";
	  });
	});

Chain JSON still has to pass the existing platform preflight before any active generation changes.

The prior register(id, optionsObject) overload remains, now with strict schema validation. @ScriptInput names the same provider used by runtime validation, allowing Probe to replace broad Map input declarations with exact optional fields. Consumer<PostEffectBuilder> retains real callback type information.

Actual Graal integration exposed an overload ambiguity: a guest function selected the Java Map overload as an empty object, returning false without entering the callback. The final event payload uses a finite ProxyObject dispatcher to check executable inputs before object conversion. Functions invoke the real Consumer overload; objects pass through the canonical schema into the real Map overload. Both typed Java overloads remain the source of Probe declarations and direct Java compatibility; no broad Value overload or handwritten TypeScript signature was added. The dispatcher preserves register, unregister, getDeclaredCount and the declaredCount Bean property. Invalid arity, ids, option kinds, writes and deletions poison the candidate rather than silently succeeding.

| Node | Supported builder/legacy object fields |
| --- | --- |
| 1.21.1 | chainJson, program |
| 26.x | chainJson, fragmentShader, fragmentShaderId, fragmentShaders, vertexShaders, uniformsJson, blurRadius, blurRounds |

All text fields require strings. Shader-source tables require string keys and string values, are detached/immutable, and every shader identifier is validated rather than silently filtered. 26.x blurRadius is finite in [0,64]; blurRounds is an integer in [1,8]. These explicit validation limits replace the prior generator's silent clamping/coercion. The existing precedence remains chainJson, then fragmentShader, then blurRadius (legacy chainJson, then program).

Builders provide typed Bean properties plus callable getter/setter methods. Unset reads return nonnull typed defaults (empty text/maps, blurRadius 0, blurRounds 1) without adding explicit declaration fields; an empty builder still fails collection. Their ProxyObject boundary validates guest writes without permissive host numeric coercion. All five proxy infrastructure methods, including the inherited default removeMember hook, have hidden overrides on the builder and event. An invalid assignment, deletion or accessor argument count taints the builder even if JavaScript catches the immediate exception. Stable diagnostics are NEKO-4060 (strict options), NEKO-4061 (builder lifecycle/validity), and NEKO-4062 (render registration contract/metadata). Each callback runs once and its builder is sealed on every exit path, so captured references cannot mutate collected declarations. Map validation failures and builder/callback failures poison the existing whole-candidate plan; no partial declaration becomes live. The pre-existing plan preflight/publish boundary remains the only live resource transition.

### Hidden implementation-interface seam

The Lead-owned TypeReflector correction prevents hidden methods from reappearing through TypeScript heritage: an interface edge is omitted only when it has a nonempty set of public instance methods and every implementation resolved on the concrete class has no script-visible name. It reuses existing visibility/remapping semantics rather than blacklisting ProxyObject. Marker and static-only interfaces remain, as do partially visible interfaces and their visible default methods. Abstract owners and child interfaces also retain abstract contracts: a hidden abstract declaration is not a concrete implementation that can erase heritage. Inherited and generic-erasure implementations are covered. Actual node reflection and rendering additionally verify that both payloads expose their authored contracts without ProxyObject heritage or infrastructure methods.

## Regression sources and counts

- RenderOptionsTest (3 tests): strict Java/guest fields; accepted canonicalization and integer extremes; rejected keys/types/nulls/nonfinite/fractional/overflow values; detached source maps.
- RenderRegistrationContractTest (2 tests): actual Graal bus invocation for all intentional forms; invalid options rejected without installed entries; actual callback and schema metadata.
- PostEffectBuilderTest (7 tests): actual finite Graal dispatch for callbacks and legacy objects; Bean properties/accessors; sealed captured builders; caught-invalid/deletion poisoning and callback failures; strict numeric limits; inert detached collection; count/unregister/enumeration and malformed invocation compatibility; and actual TypeReflector/TypeScriptClassRenderer surface assertions. Every negative callback case asserts that the callback was entered, preventing the previous overload ambiguity from producing false-positive poison tests. The positive callback assertion also reports collectionError and callback-entry evidence when rejected.
- ScriptInputShapeDeclarationTest (5 tests) with ScriptInputFixtures: catalog copies, callback-last multi-input/return metadata, actual host callback types/import closure, @ScriptInput methods/constructors/setters/inherited members, strict provider construction, registry imports and exact ordinary-event parity. Generated TypeScript fixtures target common/build/probe-ts-script-input-shapes/{callable,annotated}.

- TypeReflectorHiddenInterfaceTest (7 tests, Lead-owned): wholly hidden contracts, inherited resolution, visible default methods, marker/static-only contracts, generic erasure, abstract owners and child-interface hidden overrides. The Lead reports the two added compatibility cases first failed as expected, then all 7 passed after abstract resolved methods were retained.

## Verification and remaining acceptance

Scoped git diff --check passed during implementation and after the five-file Graal integration fix. No tests, Gradle, Stonecutter, renderer/gameplay acceptance, or declaration regeneration were executed by this worker.

The Lead reports the final focused node run passed with exit 0: 7 PostEffectBuilderTest and 2 RenderRegistrationContractTest tests on each of :1.21.1 and :26.2.0, plus 5 WorldRenderPipelineTest tests on :26.2.0. That is 23 passing tests, with 0 errors and 0 skips. These are host/Graal and extracted-pipeline checks, not visual game/GPU acceptance.

At this report update, the final full common checks and three NeoForge declared-event-only golden regenerations are running under the Lead; full five-node gates and final golden review remain Lead-owned. Their results are not claimed here. Fabric remains outside the post-effect implementation guard; this change does not claim Fabric post-effect/rendering availability. Python callable-registration rendering is not implemented by this task; it must not be presented as equivalent to the corrected TypeScript declarations.
