# Closeout review

Baseline: `d5484f9d71fa5fd5b297ffa14d1cad864b594a56`; internal work-in-progress review, not a fabricated release commit. Standards and Spec were reviewed independently in parallel; final follow-up reviews were read-only. No reviewer edited source or changed acceptance on the maintainer's behalf.

## Standards

Sources: root instructions, `CONTEXT.md`, `docs/agents/coding.md`; review includes the changed production comments/TypeDoc, canonical Fabric test guard, new production declaration fixture, and owned native/rollback evidence scripts.

One documented-rule finding (external input/path validation, coding.md:73): optional/blank or substring `OwnedGameDir` could select an unrelated Java window. The first regex correction still mistook a quoted JVM-property fragment for a real gameDir flag. Both are fixed with shared Windows `CommandLineToArgvW` parsing, exactly one genuine flag, an absolute existing directory, resolved equality, and validation before focus/capture/right-click. Four genuine argv forms and nine rejection cases passed without window input; a final sequential check again accepted a genuine flag and rejected the quoted-property bypass after all edits.

Two minor heuristic smells remain non-blocking: `tree` in the bounded rehearsal means a hash manifest; two simple explicit fixture-copy loops repeat traversal. These do not add a production abstraction or alter runtime behavior. No unrelated rename/framework extraction was made solely for the review.

## Spec

Sources: tickets15/16/22/28/37/38 and validation/data-protection specs. One initial finding: inert staging was described as failure-before-publish proof without actually injecting failure. Corrected by raising/catching an actual bounded `OSError` cancellation after stage hash validation, verifying live/damaged-original preservation, then retrying the independent restore. The fresh [result](rollback-result.json) records that injection, exact byte restoration and idempotence. It does not claim mid-copy/power-loss recovery or production Minecraft parsing.

Final Spec follow-up reported no unsound changed checkbox, wrong implementation or scope expansion. Only22 unavailable,28 production declaration/Fabric failure,37 inventory/optional read-only tool and38 focused CLI acceptance are newly closed. Domain deletion, public sign-off, first-frame/all-node visual parity, current candidate full runtime/performance, final version and release/cutover policy stay open.15/16/22/28 status headers were reopened rather than treated as complete.

Summary: Standards—1 hard finding corrected,2 bounded heuristic notes; Spec—1 evidence finding corrected,0 unresolved changed-acceptance defects. Neither axis substitutes for the explicit remaining technical/human gates.
