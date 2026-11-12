# Default-off MixinJS engine setting

## Contract

The canonical game-directory nekojs/config/engine.toml gains [mixinjs] enabled = false. Existing files without the key are disabled; the normal config writer supplies the default. Only explicit boolean true opts in. The legacy engine-config fallback remains read-only and cannot override an existing canonical file. Missing or invalid early settings fail closed without creating or rewriting files.

The native bootstrap guard captures the setting once at startup, before loader transformations, without constructing a runtime root or loading Minecraft targets. Platform adapters expose that same snapshot through IPlatform.isMixinJsEnabled. Carrier admission, workspace preparation, runtime installation and generated hook capabilities use that snapshot. A disabled feature must not inspect installed plans or managed selection, read target bytecode, prepare libraries, or demand a restart for pending declarations.

Changing the setting requires a full process restart. This is not a live bytecode unload/reload mechanism. Explicit opt-in does not relax existing development-only, exact-profile, local-trust, callback-thread or ABI restrictions. Explicit offline declaration/compiler tools remain usable without enabling automatic runtime activation.

## Verification plan

- Config default persistence, explicit booleans, older constructors, canonical precedence and read-only legacy fallback.
- Early missing/malformed settings fail closed without filesystem mutation.
- Disabled carrier discovery skips every other platform/metadata access; enabled paths retain existing exact-profile and production rejection tests.
- Common isolation, five platform builds, addon regression gates, full Probe TypeScript and wiki checks.

## Results

- Behavior RED: the new disabled-carrier test failed on the original discovery path while the configuration tests passed (19 focused tests, one intentional failure). Earlier test-compilation attempts exposed test-only NightConfig imports; the final tests use the public loader and persisted-file contract instead of adding dependencies.
- Focused GREEN: SandboxConfigLoaderTest and all com.tkisor.nekojs.core.mixin.* tests passed after the discovery gate was added.
- Full unchanged-guard matrix passed in 4m33s: `./gradlew --no-daemon :common:check guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build :addons:nekujs:build :addons:nekujs:fixtureTest :addons:nekujs:expandedFixtureTest :addons:nekujs:compatProofTest --continue --console=plain`.
- Wiki sync/stamp and strict check passed (31 bilingual topics, zero problems); all eight wiki-tool tests passed. Existing wiki anchors were retained.
- Full Probe TypeScript/completion checks passed: `pnpm run test:probe-types`, including installed hook IDs, event fields and void cancellation completions.
- Common report: 2,960 tests, zero failures, four existing skips. No golden expectations or gate strength were weakened.

No new live-client, dedicated-server transformation, or human acceptance was performed for this change. The prior production restriction remains in force. The startup setting does not remove cached libraries or rewrite existing transformations; the process must restart to change activation.
