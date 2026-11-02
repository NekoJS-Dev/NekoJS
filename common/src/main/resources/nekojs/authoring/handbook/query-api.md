# Read-only authoring queries

## Prepare a matching snapshot

Read `context`, `capabilities` and `schema` first. Context reports `environmentId`, `snapshotId` and `indexState`. Index states include `not-ready`, `ready`, `disabled` and `unavailable`. Exporting the handbook does not build the API index. Run a successful **TypeScript Probe** in the game (use the installed `/nekojs probe` help); ordinary script reload does not regenerate it.

The default pointer is `.github/neko_ai/snapshots/current.json` relative to the game directory. `.github/neko_ai/environment.json` records `apiSnapshotDirectory` for the selected AI output directory. Probe publishes an immutable digest-named JSON file, then atomically switches its pointer. The runtime or standalone stdio helper verifies the hash, snapshot ID and environment before loading it on a context/API request. The helper can read the last successful export while the game is stopped; it does not run Probe or inspect live game state. A failed publication does not replace the previous verified snapshot. Do not edit index files manually.

Every request scope needs the discovered `environmentId`, an uppercase `scriptType` (`STARTUP`, `SERVER`, `CLIENT` or `TEST`) and `language: "typescript"`. Only `memberView: "declared"` is supported. Pin `snapshotId` explicitly once discovered; it may be omitted only to select the current snapshot. Replace angle-bracket placeholders below before sending requests. The names shown are examples, not promises that every instance exports them.

## Resolve, then inspect members

POST to `resolve` (or call `nekojs_resolve`):

```json
{
  "scope": {"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared"},
  "name":"Item", "kind":"binding", "match":"exact",
  "select":["symbolId","name","kind","coverage","source"], "page":{"size":20}
}
```

Inspect all candidates: class and Binding names can be ambiguous. Copy the chosen result's `symbolId` into `scope.ownerSymbolId` for `members`. Do not substitute a name for an ID. Exact matching is the default; `prefix` is an explicit option. POST to `members`:

```json
{
  "scope": {"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared","ownerSymbolId":"<ownerSymbolId>"},
  "name":"of", "kind":"method", "arity":{"min":1,"max":1},
  "select":["symbolId","name","parameters","returnType","callArity","coverage"], "page":{"size":20}
}
```

Arity filters the overlap of accepted argument-count intervals, including optional/varargs behavior; it is not a raw parameter-count filter. Every callable row is one emitted overload, never a merged method group.

## Correlate parameters in one overload

POST to `query`. This asks for an `of` overload whose **same first parameter** has display type `string`. It never combines facts from different parameters or overloads:

```json
{
  "scope": {"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared","ownerSymbolId":"<ownerSymbolId>"},
  "from":"members",
  "where":{"all":[
    {"field":"name","op":"eq","value":"of"},
    {"some":{"path":"parameters","where":{"all":[
      {"field":"position","op":"eq","value":0},
      {"field":"type.display","op":"eq","value":"string"}
    ]}}}
  ]},
  "select":["symbolId","name","parameters","returnType","coverage"], "page":{"size":20}
}
```

Resources are `symbols`, `members`, `events`, `inputAdapters` and `declarations`. Boolean groups are `all`, `any` and `not`; correlated arrays use `some`. Discover resource-specific fields, operators, selectable fields and structural request schemas instead of guessing. For declarations, select `path`, `sha256` and `text`; read the generated local declaration when text exceeds the transport budget. Type IDs/display strings are conservative, not an assignability oracle across modules or aliases.

## Read coverage and continue safely

Inspect `items`, `coverage.state`, `coverage.scope`, `coverage.unresolvedRecords`, `warnings` and `page`. Missing metadata remains unknown even under `not`. Exact filtering never silently becomes fuzzy. Incomplete coverage and an empty last page do **not** prove an API absent.

Declared records include materialized emission projections, not a flattened inherited generic view. Authored declarations, literal overloads and capture views may exist only as declaration text. Binding inheritance/collision resolution, event callback signatures and adapter aliases are not fully indexed: follow their declarations. Python members, `effective` views and `assignableTo` are unsupported.

For another page, set `page.cursor` to the returned `page.nextCursor`, preserving operation, scope, filter, selection and ordering. Page size may change. Stop only after inspecting `page.endOfResults` and `page.stopReason`. The runtime retains the last three loaded snapshots; cursors expire after 15 minutes or shutdown. After `SNAPSHOT_EXPIRED`, refresh context and restart the query, rather than appending results from a different snapshot.

## Batch and budgets

POST to `batch`. Children are query objects, not arbitrary operations; they share the outer scope and pinned snapshot and cannot override scope:

```json
{
  "scope": {"environmentId":"<environmentId>","snapshotId":"<snapshotId>","scriptType":"SERVER","language":"typescript","memberView":"declared"},
  "queries":[
    {"from":"symbols","where":{"field":"name","op":"eq","value":"Item"},"select":["symbolId","name","coverage"]},
    {"from":"events","where":{"field":"group","op":"eq","value":"ServerEvents"},"select":["symbolId","name","group","coverage"]}
  ]
}
```

Inspect each `results` entry's `status` and `result` or `error` independently. Limits: batch size 1–10, page size default 20/max 100, 32 selected fields, three ordering keys, 32 predicate nodes, depth 4, 100 values per `in`/`notIn` and 64 KiB HTTP bodies/responses. Stdio also bounds each JSON-RPC input/output frame to 64 KiB; reduce selections/pages to leave room for the MCP envelope. Unknown fields, wrong types and unsupported operators are errors, not ignored options.

`INDEX_NOT_READY` requires a successful TypeScript Probe; `INDEX_DISABLED` requires enabling the API and restarting the game to export the changed state; `INDEX_UNAVAILABLE` requires checking local snapshot/configuration and logs. `SCOPE_CONFLICT` means rediscover this instance's environment. `RESPONSE_TOO_LARGE` means reduce fields/page size or read local files. Never work around an error by assuming signatures. Use [in-mod MCP](connections.md) while the game runs, or the [standalone stdio helper](stdio.md) for offline exported snapshots. Both expose the same query arguments; ordinary JSON HTTP is an additional advanced option. These read-only query operations cannot execute JavaScript, reflect arbitrary Java, write scripts or reload a generation. In-mod MCP has separate default-off [live controls](live-controls.md); they are not query or batch operations.
