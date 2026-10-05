# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root, or
- **`CONTEXT-MAP.md`** at the repo root if it exists: it points at one `CONTEXT.md` per context. Read each one relevant to the topic.
- **`docs/adr/`**: read ADRs that touch the area you're about to work in. In multi-context repos, also check `src/<context>/docs/adr/` for context-scoped decisions.

If any of these files don't exist, **proceed silently**. Don't flag their absence; don't suggest creating them upfront. The `/domain-modeling` skill (reached via `/grill-with-docs` and `/improve-codebase-architecture`) creates them lazily when terms or decisions actually get resolved.

## File structure

Single-context repo (most repos):

```
/
├── CONTEXT.md
├── docs/adr/
│   ├── 0001-event-sourced-orders.md
│   └── 0002-postgres-for-write-model.md
└── src/
```

Multi-context repo (presence of `CONTEXT-MAP.md` at the root):

```
/
├── CONTEXT-MAP.md
├── docs/adr/                          ← system-wide decisions
└── src/
    ├── ordering/
    │   ├── CONTEXT.md
    │   └── docs/adr/                  ← context-specific decisions
    └── billing/
        ├── CONTEXT.md
        └── docs/adr/
```

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal: either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Current decisions and historical evidence

- Read explicit revision/supersession notes and the final Resolution of the relevant decision. Follow replacement links for the affected clauses; unaffected clauses remain in force. A newer file does not automatically override an older contract.
- For architecture-refactor work, use the [map](../architecture-refactor-map.md) to reach final decision Resolutions and the [implementation ticket index](../architecture-refactor/implementation-tickets/README.md) to reach execution state. Specs, wiki examples, Question, and historical Evidence do not override an explicitly accepted Resolution.
- A closed decision means the contract was accepted. Implementation status belongs to its implementation ticket; verification requires the recorded commands, results, and applicable runtime evidence. Do not infer completion from a plan or a symbol's existence.
- When maintaining a superseded document, add a concise notice identifying the affected clauses and their replacement. Preserve the original rationale and distinguish planned behavior from verified behavior.

## Flag ADR conflicts

If a proposed change conflicts with a current contract, identify the exact clause and the task's accepted decision, if any. Follow an already-authorized change without asking for duplicate approval. If a genuinely unresolved product or architectural choice remains, record it in the existing task and obtain the missing decision before implementing that choice; continue independent work.

For example: _This dependency would violate ADR-0007's common/loader isolation rule; keep the loader adapter in the version tree._
