# Probe public contracts and missing optional public signatures

## Two distinct boundaries

The earlier [private-signature repair](<2026-11-09-probe-private-signatures.md>) uses native public-method enumeration before private signatures can poison reflection. The supplied Create 6.0.10 `AbstractComputerBehaviour` is different: its **public** `getPeripheralCapability(): IPeripheral` needs ComputerCraft. Java resolves the owner method set before returning individual Method objects. Per-member catches cannot recover them.

No supported selective-reflection API preserves loaded Method identities and metadata here. Raw JAR resources are not authoritative for transformed/mixin-loaded classes. This change does not derive guessed methods from raw bytes, install a fake optional API, erase signatures to any, or claim complete output.

## Public NekoJS contracts

- Three public `api.villager` interfaces describe declaration, reload and query values. Existing engine classes implement them; native objects, Java signatures, event buses, root lifetime and validation stay unchanged. Query status remains the original enum instance, exposed as `Enum<?>`, not a replacement enum.
- `@ScriptTypeView` chooses an implemented public non-generic interface for **output authoring**. Event catalog payloads and direct returned objects use this view before dependency filtering. Generic arguments and mutable arrays retain their native contracts: rewriting them could widen callback inputs or element writes. Container APIs must explicitly declare their public element contracts. Source Method/Type identities stay native; automatic views do not override authored `@ScriptOutput` expressions or explicit Probe edits.
- `@ScriptInternalConstructor` removes framework-owned constructors from declaration IR and the class collector, preventing candidate-plan implementation types from becoming public dependencies. This is not access control: public Java constructors and native Graal construction remain available. Normal constructors stay declared.
- Public interface declarations must preserve Bean property aliases as well as real getter methods. The TypeScript renderer reuses the existing class instance projection, including property conflicts, hidden members, edited slots and getter overrides. Python shares the existing class property emission while retaining its previously declared interface getter calls and accurate overload imports. No handwritten declarations special-case these views.
- Existing engine-package exclusions remain in force. No special allowlist hole exposes these implementations.

## Missing optional public signature

The shared reflector catches only the method-enumeration `NoClassDefFoundError` boundary. It retains independently reflected public constructors and fields. It omits the **entire public method set and method-bearing heritage**: otherwise inheritance could silently restore methods whose hidden overrides cannot be checked. Enums deliberately retain whole-declaration unavailable handling: their renderer emits a fixed method skeleton, which is incompatible with this partial-method policy. Other failures still use existing unavailable-declaration handling.

TypeScript records retained partial owners in `@special/dependencies.json.incompleteMembers`, separately from wholly unavailable types. Either category keeps `complete=false`. Both languages return an explicit `NEKO-4066` warning. Authored replacements and hidden declarations do not inherit stale partial warnings.

When the optional signature is resolvable, the existing complete native reflection path, Method identities, generic metadata and actual peripheral return type remain intact. This does not modify ComputerCraft or Create behavior.

## Verification and operational scope

Focused checks pass for the unchanged Create owner with ComputerCraft absent, and the same owner with a **test-only signature-resolution interface**. The latter verifies the native dependency-present branch; it is not a real installed ComputerCraft integration/gameplay test and is never shipped. Constructor regressions verify actual Java and Graal construction is unchanged. Shared backend tests require truthful incomplete reports and retained declarations rather than fabricated completeness.

Final full-gate results are recorded in the [implementation plan](<../nekujs-implementation-plan.md>). No existing game output is hand-patched. Restart the client after rebuilding, then regenerate with `/nekojs probe` or `/nekojs probe all`. `/nekojs probe reload` reloads configuration only. Strict checks may still expose unrelated generic/override declaration debt; this scope does not certify all external declarations or full KubeJS compatibility.
