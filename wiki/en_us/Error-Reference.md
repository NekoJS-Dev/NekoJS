# Error Reference

Stable `NEKO-` diagnostic codes. The code is the lookup key: wording may change between versions, a code is never reused for a different failure. Codes are grouped by area:

| Area | Codes |
|---|---|
| Script loading and reload | 1xxx |
| Sandbox and resource limits | 2xxx |
| Script sync limits | 3xxx |
| Registration and bindings | 4xxx |
| Recipes and data | 5xxx |
| JSX UI visual and resource contract | 6xxx |
| JSX UI runtime lifecycle | 7xxx |
| JSX UI inspector | 8xxx |

This page is being backfilled area by area; only areas with assigned codes are listed below.

## 8xxx — JSX UI inspector (ticket 45)

| Code | Meaning |
|---|---|
| NEKO-8001 | A runtime layout snapshot does not match the inspector read contract (runtime/host version skew); the last good frame is kept. |

## 7xxx — JSX UI runtime lifecycle (ticket 42)

| Code | Meaning |
|---|---|
| NEKO-7001 | UI operation rejected: the root's generation is superseded or closed (stale handle). |
| NEKO-7002 | Illegal UI root lifecycle transition (candidate/active/closing/closed). |
| NEKO-7003 | JSX host adapter created outside a managed CLIENT script context. |
| NEKO-7004 | UI mutation attempted off the client owner thread without explicit queueing. |
| NEKO-7005 | UI root release failed during generation teardown; teardown continued. |
| NEKO-7006 | Deferred UI work dropped: its generation never committed or was superseded. |
| NEKO-7007 | JSX UI phase failure (render/layout/event/host) recorded into the diagnostics chain. |

## 6xxx — JSX UI visual and resource contract (ticket 44)

| Code | Meaning |
|---|---|
| NEKO-6001 | A UI color prop is not a controlled color form (ARGB/RGB int, `#RGB`/`#RRGGBB`/`#AARRGGBB`, CSS basic named color). |
| NEKO-6002 | A UI visual prop value is out of range or has the wrong type (`opacity`, `fontSize`, `borderWidth`, `radius`, `fit`, `crop`). |
| NEKO-6003 | A UI resource identifier violates the controlled id grammar (lowercase `namespace:path`, no `..`). |
| NEKO-6004 | A controlled UI resource id does not resolve in the resource roots. |
| NEKO-6005 | Reading or loading a resolved resource failed. Reserved, not yet emitted: the texture load pipeline is not wired; unresolved images draw as placeholder boxes. |
| NEKO-6006 | A UI resource or crop size is not legal (non-positive dimensions). |
| NEKO-6007 | Decoding a resource into a usable image failed. Reserved, not yet emitted: the texture decode pipeline is not wired. |
