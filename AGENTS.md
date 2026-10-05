# NekoJS

## Working agreement

- Before changing code, read [CONTEXT.md](CONTEXT.md) for vocabulary and [coding conventions](docs/agents/coding.md) for implementation and verification rules.
- Apply these conventions to new and modified code. Keep the change within the requested scope; preserve unrelated behavior and other contributors' work. Keep unrelated translation, formatting, renaming, and cleanup separate.
- Inspect the current diff, the affected flow, its callers, and relevant tests before editing. Reuse existing mechanisms and fix the shared cause of a bug. Add abstractions, dependencies, or configuration only for a concrete current need.
- Follow the user's requirements and existing authorization. Explain material exceptions or contract changes in the change description; an already-authorized change needs no duplicate approval.

## Language

- Write authored source comments, documentation comments, developer-facing log templates, and developer-facing exception messages in English.
- Keep literal fixtures, protocol values, and diagnostic identifiers unchanged when their exact content is required. This exception covers data, not explanatory prose, and does not require copying that data into comments or logs.
- Use the existing localization mechanism for player-facing text; translation values use the target language. Follow the existing language of prose documentation and the user's language in conversation.

## Comments and logs

- Write comments: describe simple behavior briefly; explain the mechanism and essential constraints of complex behavior. Put details beside the code they explain and keep comments focused on current behavior. See [comment conventions](docs/agents/coding.md#comments-and-documentation).
- Lead log messages with the event or failure and its outcome. Include the context needed to locate or diagnose it; choose relevant fields instead of dumping every available detail. A message that reports a problem uses a stable `NEKO-` code. See [logging conventions](docs/agents/coding.md#logging).

## Script API

- Use event groups for subscriptions (`ServerEvents.xxx(event => {})`) and named Bindings for helpers (`Item.of(...)`). For builder APIs, place configuration last (`event.create(arg1, ..., build => { ... })`). Use Bean properties for exposed accessors (`event.xxx` / `event.xxx = value`) according to their read/write support. When changing script-facing APIs or examples, follow the [Script API conventions](docs/agents/coding.md#script-api-conventions).

## Architecture constraints

- `common`, including `com.tkisor.nekojs.api.*`, must have no Minecraft/loader dependencies. Graal is allowed. Keep public contracts in `api.*` and engine-only implementation internal; keep `common-api-processor` independent.
- Put shared Minecraft-facing code in the version tree and genuine node-specific code under `versions/<node>`. New business code uses version facades or existing node splits instead of inline version guards. Prefer facades when logic is shared; node sources contain evaluated code.
- Extend the existing Point / Plugin Hook / Contributor model. Point is the source of truth for a collection channel; keep its facade and pairing checks consistent. Change canonical sources or generators, then regenerate affected derived artifacts through the existing workflow; do not hand-patch generated output.
- Keep `NekoRuntimeRoot` as the single runtime owner. Separate root-owned and generation-owned resources; ordinary reload switches script generations without rebuilding Plugin Runtime. Follow the [resource, thread, and state constraints](docs/agents/coding.md#resources-threads-and-state).
- Preserve public behavior, supported platforms, persistent data, and wire contracts outside the change's explicit migration scope. Temporary migration paths need clear removal conditions; remove them when those conditions are met.

## Verification and delivery

- Select checks by the affected behavior using [the verification table](docs/agents/coding.md#verification). For common changes, `:common:test` alone does not replace `:common:check`, which includes isolation checks.
- Review the final diff for scope, language, architecture, public contracts, and generated files. Explain intentional golden or snapshot changes; never weaken a gate solely to make a failure disappear.
- Deliver four things: what changed, affected behavior/contracts, verification commands and results, and remaining gaps. Distinguish passed, failed, and not run. Human acceptance must record a real maintainer conclusion.

## Collaboration

- Before parallel work, agree on ownership and overlapping files. Use independent branches/worktrees where needed and preserve other contributors' changes.
- Keep internal plans and progress notes in the repository. Create GitHub issues only when explicitly requested by the user; triage of existing community reports remains supported.
- For community triage, use needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix.
