# Python Bean/member collision review

Baseline9b2c5d29; owned WIP changes only PythonClassRenderer plus its new regression fixture. No generated golden was changed. This review is performed by the responsible agent and is not independent maintainer acceptance.

## Standards

Manual review found no unresolved documented-standard breach in this narrow change. Common remains Minecraft/loader-free. Original accessor names are retained, English comments explain the representation constraint, and no public/runtime ownership or thread behavior changes. The conflict set uses the same ordinary-member predicate as overload detection; invalid Bean names already emitted as methods are also considered. Hidden and renamed methods/fields are covered, and setter suffix casing usesLocale.ROOT as the existing shared reflector does.

An extra review boundary was addressed before final verification: falling back to real overloaded setter methods must also cause the backend to import typing.overload. The fixture now includes String/int setters, real HostAccess calls to both, overload markers and the same hasOverloads predicate consumed by the backend. All previous Python renderer/integration cases remain selected for the focused check.

## Spec

The renderer preserves real callable getter/setter names when a synthesized Bean alias would shadow a real method or visible field. Noncolliding properties retain their previous declaration shape. The real Graal/Nashorn-compatible HostAccess fixture verifies the original accessor and method calls, field access, and static factory access. No missing Java method is invented, no manual producer or security gate is removed, and the shared IR/TypeScript renderer is unchanged.

This addresses one diagnostic family only. Java static/instance overload inconsistency, simple-name type imports, ordinary field/method collisions and inheritance ordering remain separate; no full IDE acceptance is inferred. Any actual fresh Pyright count must come from exact installed new-JAR generation, not fixture assertions or edits to generated files.

## Independent review availability

The code-review skill's Standards and Spec agents were dispatched with read-only ownership. Both jobs failed before returning a review because the service reported an account usage limit. They produced no findings or pass conclusion. The earlier independent reviews apply to the earlier Probe linkage repair, not this new Python change. Independent review remains NOT COMPLETED; the manual checks and actual verification above are separately identified.
