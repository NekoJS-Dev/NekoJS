# Generated declaration changes

## 2026-11-16: explicit event modules

Native/plugin subscriptions are no longer globals after the approved breaking event publication migration. Their declarations use custom engine virtual modules under `nekojs:event/{stable,versioned}`; plugin routes append an optional owner, as in `nekojs:event/versioned/myaddon`. The previous event routes have no aliases.

Regenerated with the canonical Gradle task, not hand-written snapshots:

```bash
./gradlew.bat --no-daemon :common:regenerateGoldens --tests '*ProbeEventsSurfaceGoldenTest' --tests '*DynamicRegistryEventsDeclarationGoldenTest' --tests '*LegacyProbeTreeTest' --console=plain
```

The successful regeneration run writes the selected declarations and intentionally marks regeneration tests skipped; normal tests must be rerun without the flag. Reviewed intended changes:

- ProbeEvents and DynamicRegistryEvents expected declarations now contain `nekojs:event/versioned` modules and public structural payloads; plugin event modules use the same category with an owner suffix.
- Legacy fixture native event subscriptions move from global event files into per-environment API module declarations. Obsolete global event files/references are removed by the canonical tree mirror.
- Existing public payload/native class closure remains available. Dynamic script-created globals retain their separate behavior.

The generator also removed the JDK-specific `VarHandle$AccessMode.valueFromOrdinal` Mixin signature. That is an unrelated known baseline drift, not part of this migration. The entire unchanged `legacy-tree/@mixin/index.d.ts` file was restored from HEAD after confirming its sole generated delta. Do not accept that drift or change reflection expectations just to make the legacy golden checks green.

Wiki topic digests use the existing exported `digest` helper in `scripts/wiki-docs.mjs`, scoped to 22 changed topics; human review remains pending. The global stamp CLI is blocked by preexisting structural failures. No checker was weakened. Baseline strict wiki check reports45 findings; the migrated tree currently reports25 existing navigation/structure findings, with no findings in the event namespace pages.

Normal test and consumer checks must be rerun without the regeneration flag. No live-game acceptance or release is implied.
