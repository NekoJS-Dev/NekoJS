# Parallel implementation verification (2026-10-05)

Branch: `mult`; base: `47dad5ae`. Lead integrates disjoint child implementation/review work; no unrelated AGENTS/CONTEXT/ADR/BlockBroken changes are included.

## Implemented scope

- Compiler: comma declarators, nested initializer fragment traversal, identifier/member diagnostics, source offsets, ASI and compact arrow/function parsing.
- Ticket 39: strict active modification collection, ordinary/nested event mode isolation, dead/closed Context rejection for collection, whole-target Block baseline replacement.
- Ticket 41: actual event-error retention, external Screen replacement/old-root invalidation and conditional Escape smoke.
- Ticket 43: committed common rect/clip/resolved props drive paint/hit tree; staged Inspector publication, layout-only resize, row/design scrolling and retained interaction identity.
- Ticket 44: resource-stack lookup, bounded static PNG decode/native GPU upload, node-specific API implementations, immutable fit/crop/opacity plans, cached text/font scaling, after-publication retirement, failure cleanup/cause preservation.

## Commands and results

- `:common:check :26.1.2:test :26.2.0:test :1.21.1:test :26.1.2-fabric:test :26.2.0-fabric:test guardLint`: PASS after integration fixes.
- `:common:check :26.1.2:test :26.2.0:build :1.21.1:test`: PASS after final root-id envelope change.
- `npm.cmd run test:probe-types`: PASS.
- `:common:test --tests com.tkisor.nekojs.probe.JsxRuntimeProbeDeclarationGoldenTest -Dnekojs.golden.regenerate=true`: explicit regeneration. Diff changes exactly one host signature, adding optional `publish?: boolean`. Ordinary common check and typecheck then pass.
- `:26.1.2:compileJava`: PASS with node upload split. The initial same-class override approach produced duplicate class; implementation now exists separately in both nodes and no shared uploader remains.
- `ServerPackCacheTest`: one full-check run encountered Windows AccessDenied during a physical replacement. Isolated rerun passed, then the subsequent complete common checks passed. No file-restore gate was weakened or source altered.

## Executed focused reports

| Suite | Tests | Skipped | Failures/errors |
|---|---:|---:|---:|
| EventBusJSCollectionDispatchTest | 17 | 0 | 0 |
| ValParserTest | 18 | 0 | 0 |
| GlobalBindingMemberValidatorTest | 19 | 0 | 0 |
| Ticket43HostLayoutProjectionTest | 5 | 0 | 0 |
| Ticket44TextureLoadingTest | 10 | 0 | 0 |
| Ticket44TextureBlitPlanTest | 4 | 0 | 0 |
| Ticket39InitialCollectionAtomicityTest (26.2) | 4 | 2 | 0 |
| Ticket39SameTargetReplacementTest (26.2) | 4 | 4 | 0 |

Vanilla-dependent skips remain skips; they do not prove actual item/block mutation. Dedicated CLIENT visibility and 1.21.1 reflective component smoke remain open.

## Live evidence

- [Lifecycle pack](../2026-10-05-ticket41-lifecycle/README.md): event error followed by healthy callback, oldDisposed/staleDispatch results, Screen replacement and conditional Escape.
- [Profile pack](../2026-10-05-ticket43-profiles/README.md): six public measurements/render unchanged, matching real target clicks and F11 resize.
- [Texture pack](../2026-10-05-ticket44-textures/README.md): actual images/icons/crop/opacity/font hierarchy, missing/corrupt resource diagnostics and healthy callback/close.
- [Modification client readiness](../2026-10-05-ticket39-client/README.md): deployable fixtures and cross-process limitations; not a completed live trial.

## Reviews

Standards and Spec agents independently reviewed the round. Their actionable findings were corrected with follow-up tests: swallowed initializer fragments, ASI/compact arrows, nested event mode/dead Context, scroll axis/design scale, eager texture retirement and cleanup/cause loss. Final review found one GPU Error partial-init cleanup gap; it now closes the allocated texture, suppresses cleanup failure, and rethrows Error unchanged. Exact final node tests are rerun after this correction.

The author-host optional layout publication flag is an intentional compatible addition; existing fake hosts ignore extra optional arguments. Actual NeoForge host separates pending layouts from committed frames. The already-present Java Map viewport conversion and its executable regression are carried forward in the UI commit because Java hosts return Map and first root creation depends on this conversion.

No whole-project release claim, remote upload or human sign-off is authored. Ticket 43 still needs extended responsive golden review; ticket 41 has remaining native editing/narration/generation matrices; ticket 44 has radius/custom-font/repaired-resource reload gaps; ticket 39 client synchronization and AC14 remain open.
