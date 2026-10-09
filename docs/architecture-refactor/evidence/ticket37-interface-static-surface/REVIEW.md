# Interface static surface review

Baseline `77aa54b00b3d22aa7fade97eef5ee635a2dabbf9`. Both independent reviewers examined the shared renderer, existing renderer stability check, new real reflection/HostAccess fixture and seven regenerated legacy files. Reviews were read-only and did not run Gradle or edit files.

## Standards

`sam_linkage_standards`: no documented violations or actionable smells. The shared renderer keeps edited/hidden names on the existing path; constants use readonly object members. Overloads receive actual formatting context, preserving class static modifiers. The short constant formatter repeats basic operations but extraction adds little value given its distinct modifier contract. The declaration-ending assertion accepts the additional valid `};` form while retaining deterministic output and export-prefix checks. Seven golden changes consistently relocate interface members without unrelated signature edits. Tests exercise actual reflection and production HostAccess/ClassFilter; authored explanatory text is English.

## Spec

`sam_linkage_spec`: no findings against the requirement to preserve names, fields, overloads, generic inference and separate instance/static accessibility. Interface instances retain ordinary methods; actual static factories/constants occupy the same-name const value. No runtime alias is added, hidden members do not leave an unnecessary value surface, and ordinary class static methods/overloads keep their modifier. Golden differences relocate members and correct constant modifiers without deleting names/signatures. Strict callers and Graal calls verify the matching contracts.

Findings: Standards0; Spec0. Full matrix, installed artifacts, whole TypeScript/Pyright and actual maintainer acceptance remain separate evidence. No reviewer conclusion certifies overall tickets or publication.

Targeted Spec follow-up on the two new NeoForge Codec TS2430 diagnostic identities: no evidence that the patch removed a real instance contract. MapCodec is byte-identical, surviving MapDecoder/MapEncoder instance signatures are unchanged, and the old class already omitted inherited methods such as decode/decoder/encode/encoder. This supports a pre-existing declaration gap; it does not establish the complete diagnostic causal chain. A modifier-only in-memory control failed its expected two-diagnostic prediction, so the earlier invalid-static-poisoning hypothesis is unconfirmed. Retain both new errors and the failed control; whole strict TypeScript remains FAIL.
