# Validation

1. Check the environment, Script Type and source of every newly used API. Completion: symbols resolve in current declarations or are explicitly reported unconfirmed.
2. Run the workspace's type checker if available. Completion: record the command and exit status; transpilation alone is not strict type checking.
3. Run the smallest applicable NekoJS check: server/client reload, explicit test scripts, or a process restart for startup registration. Completion: inspect execution and assertion outcomes separately, plus the original error output. While connected to the live root, use [structured runtime results](query-api.md#live-runtime-results-in-mod-only); after a control timeout, query the existing operation ID before considering another action.
4. Validate game-specific effects in the actual world/client when needed. Completion: record what behavior was observed, on which node, and any missing acceptance.
5. Deliver the changed scripts, affected behavior, passed/failed/not-run checks, and remaining gaps.

Do not weaken strict checks, invent successful game acceptance, or treat successful generation of `.d.ts` files as a clean type-check result. Bundled example metadata states the evidence level; isolated mocks do not prove Minecraft execution.
