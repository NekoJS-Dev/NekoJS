# Source review

Fixed baseline: `2af1bf71dc6a83fc5f17afd1d35c2a43d77af1b0`; reviewed source: `f370c6af2e87975747d2507bd58294e72813bb69`. Command: `git diff 2af1bf71...f370c6af`. One commit: `fix: limit default event builder dependencies to explicit associations`.

Two independent read-only agents reviewed the fixed diff, callers and tests under the code-review skill. Ownership was review-only; neither edited files, ran tests/builds/Minecraft or committed. Their conclusions do not independently certify lead runtime evidence.

## Standards

No demonstrated documented-standard breach. The three-file change stays focused, uses existing catalog facts/renderers, retains complete legacy declarations and adds observable output regressions. No HostAccess, public Java interface or Minecraft dependency expansion.

One nonblocking judgment: possible Duplicated Code. The new explicit association predicate in `TypeScriptProbeBackend` matches the predicate in `EventPayloadDeclarations.builderNames`. The existing resolver could eventually own an associated-entry list for both TS rendering and Python name projection, preventing drift. This is a maintenance suggestion, not a demonstrated behavior failure or hard violation; no broader refactor is added to this narrow repair.

## Spec

Zero findings. The implementation retains complete legacy `@registry-builders`, emits a separate event-only companion from the same explicitly associated facts, references it from events, excludes metadata-free same-name entries, retains manual output, and emits neither companion nor reference when no association exists. The diff matches the three handoff files without public removal or security-policy change.

Full strict TS remains FAIL; Pyright, current-source performance and multiplayer/other-node acceptance remain separate. Summary: Standards0hard breaches/1nonblocking smell; Spec0findings.
