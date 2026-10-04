# 41: NeoForge 26.2 JSX Screen、输入、焦点与滚动 Adapter

**What to build:** 将 40 的 host Adapter contract 接到 NeoForge 26.2 真实客户端：脚本可打开 JSX 描述的独立 Screen，看到 label/button/input/scroll/layout 的首次绘制，并通过稳定事件对象完成点击、文本输入、滚轮、Tab/Shift+Tab、Enter/Space、Escape、焦点、hover、disabled 和基础 narration。正常绘制帧只 paint 已提交 host tree，不重新执行 GraalJS render。

**Blocked by:**
- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)

**Status:** in-review

**Assignee:** 维护者/执行者：sol-ticket41（pixelstarrysky/gpt-6-sol xhigh）

**Claim record:** branch/worktree `ticket-41-jsx-ui-neoforge-screen-adapter` / `D:\mcmodDemo\NekoJS-mult-t41`; baseline `aa30e82f`; expected write set: NeoForge 26.2 JSX Screen/host Adapter, CLIENT entry binding, pure retained transaction tree, focused test/fixture and evidence only.

**Optional:** false

**Selected:** true

**Human input:** none

**Human input note:** 本票发布仅授权规划落盘；源码实施须另行授权。golden 更新仍须旧新 diff 与维护者审阅，人工发布决定不能由 agent 代答。

**协调但不硬阻塞：**
- [26: CLIENT 输入与 HUD callback 生命周期](26-client-input-hud.md)：26 继续拥有 keybind/HUD；41 拥有 JSX Screen 内输入路由。
- [27: CLIENT GUI 与 render Adapter 资源呈现清理](27-client-gui-render.md)：27 继续拥有既有 error GUI/render callback 域；41 与其确认平台 Adapter 和 client owner 线程边界，不把 JSX runtime 塞入 27。
- [30: 错误诊断、telemetry、workspace 与用户报告链路](30-diagnostics.md)：41 先通过公开诊断 seam 报告阶段化错误；与 30 的最终 record 字段对齐在 42 完成整合验收。

## Acceptance criteria

- [ ] NeoForge 26.2 真实客户端能打开、绘制、操作、resize 和关闭 JSX Screen；Screen 选项覆盖标题、关闭行为、暂停策略和外部替换 cleanup。
- [ ] 原生鼠标、滚轮、键盘、文本输入、焦点和捕获被转换为稳定脚本事件；事件对象不暴露原生 Screen、widget、`GuiGraphics`、Java 对象身份或版本特有字段。
- [ ] input 的值、光标、选择、焦点、最大长度和文本事件可用；scroll 正确管理内容范围、裁剪、偏移和滚轮消费。
- [ ] 焦点顺序、Tab/Shift+Tab、Enter/Space、Escape、hover、pressed、disabled、tooltip 和基础 narration 行为与 fake Adapter contract 一致。
- [ ] 首次构建、resize、状态更新和事件回调都不在普通 paint 帧重新执行 JSX render；每帧只绘制已提交 host tree。
- [ ] host Adapter 持有 Minecraft/loader 类型，shared/common author contract 不引入平台类型；脚本侧状态、VNode、host tree 和事件闭包只在 client owner thread 访问。
- [ ] 节点删除后旧事件闭包不再响应；Screen 关闭、外部替换和重复 cleanup 进入同一释放路径。
- [ ] NeoForge 26.2 真实客户端 smoke 使用仓库规定 Minecraft MCP 或等价注册 smoke 通道完成；其它节点只记录 `not verified`，不因 common 编译通过而标记支持。

## Implementation evidence and remaining gaps

**Verified:** the pure retained tree has a runnable transaction/cleanup test for failed commit rollback, stale-handle rejection, and close failure retaining nodes until retry. A source-trace assertion checks that Screen paint walks committed nodes rather than executing a guest render. The integrated main worktree also passed the 26.2 focused transaction test and both 26.2 compile tasks. NeoForge and Fabric 26.2 compile commands passed after forced recompilation: `:26.2.0:compileJava` and `:26.2.0-fabric:compileJava`.

**Live attempt (2026-09-25):** Minecraft MCP returned `pong` and the 26.2 NeoForge client reached a world, but GUI discovery reported `screen: LocalPlayer` with no buttons and `execute_command` returned `no command method found`; the maintainer manually ran `/nekojs reload client`. MCP `launch_minecraft` used `%USERPROFILE%/.minecraft`; CLI `launch ... --java <Zulu 25.0.3>` used `%APPDATA%/.minecraft/mcp_launcher/game`. The CLI initially loaded a stale NekoJS jar (hash `33F0DA8B`), so the isolated fixture failed with `ClientUI is not defined`; the separately existing `t41_smoke.jsx` was reopening a different screen every four seconds, not proving this fixture. With that script temporarily disabled and the freshly built jar (hash `FA2C287A`) installed in the CLI game directory, startup execution failed because `Minecraft.getInstance()` was null in `JsxScreen.<init>`; the later render-thread reload failed in `UI.createRoot` with `Viewport input must be a plain object` (`JsxHostAdapter.viewport()` returns a Java `Map`). The real `ticket41-screen-flow.tsx` Screen never rendered; input/resize/close were not tested. Original script, entry and jar were restored after the test; no AC was marked complete. Details in `docs/research/minecraft-mcp-dsh.md`.

**Follow-up (2026-09-25):** the fake-host Map viewport assertion failed before the change and passed after `jsx-runtime.ts` normalized the Java host's width/height; `:common:check` and `:26.2.0:build` passed. The ticket fixture now creates its host once on `ClientEvents.tickPost`, after Minecraft's client owner thread is ready. With the updated jar (hash `7E9C98D8`) in the CLI game directory and the pre-existing timer script isolated, `screenshots/ticket41-fixed-startup.png` captured the actual Screen with its `OK` button. A maintainer attempted click and Escape; no `[ticket41] clicked` marker was recorded, and the close path reported `ui-host-update` NEKO-7001/7007 against a CLOSING generation. Neither click dispatch nor error-free cleanup, resize, focus, scroll, or reload retention is proven. The world was saved, then the old jar and user scripts were restored exactly. No acceptance checkbox is changed.

**Close-path root cause (2026-09-25, fixed):** the `ui-host-update` NEKO-7001/7007 in the live run was not a click problem. `JsxHostAdapter.teardown` entered `beginClose()` *before* calling the guest `root.close()`, but the common root releases its retained nodes through the same `adapter.begin()` transaction channel as any other update, so every Screen close hit `requireUsable` on a root already in `CLOSING` and reported `[NEKO-7001] Cannot open a host transaction`. Teardown now notifies the guest while the root is still usable, then enters `CLOSING` — with a `tearingDown` re-entry flag replacing the state guard, because `showScreen(null)` fires `Screen.removed()` back into `close()` while the state is still `ACTIVE`. Coverage: `Ticket41JsxHostAdapterTest.screenTeardownNotifiesTheGuestBeforeEnteringClosing` asserts the ordering; `:26.2.0:test` (26 tests) and `:26.2.0:build` pass. The `[ticket41] clicked` marker is still unproven on a real client: the staged fixture was not among the loaded CLIENT scripts in that session, and the screenshot shows the button hovered (pointer already over it), so no real click was dispatched.

**Live verification (2026-10-05):** with the freshly built `:26.2.0` jar (SHA-256 matched the staged game jar), the registered MCP client loaded the authored `ticket41-screen-flow.tsx` fixture from the documented launcher game directory. The Screen opened after CLIENT generation commit, painted its `OK` button, logged `[ticket41] clicked ok`, and returned to the Minecraft title screen after Escape without `NEKO-7001`, `NEKO-7007`, or `ui-host-update`. A manual `/nekojs reload client` produced a new committed generation and reopened the Screen. F11 resize kept the Screen visible and Escape closed it again. The follow-up interaction fixture additionally exercised input focus/blur, controlled text changes, Tab focus traversal, Enter activation, scroll events and clipped content; the user confirmed all were functional after the input/scroll fix. The final rebuilt jar showed the focused input insertion cursor in the real client. Test-only fixture and jar changes were restored after the session. The game log also contained pre-existing offline-auth/Realms errors and a SERVER `Service` unknown-identifier script error; these were outside the CLIENT fixture and are not counted as UI pass evidence. Screenshots and command transcript were captured under the session build output; the source-of-truth result is the game log entries for `ticket41`, CLIENT generation commit, and the absence of JSX lifecycle errors.

**Implementation follow-up (2026-10-05):** the shared NeoForge 26.x Adapter now keeps input cursor/selection state, supports code-point-safe text insertion, Backspace/Delete, Home/End and arrow movement, and paints a focused insertion cursor. Scroll dispatch now resolves the scroll ancestor before dispatching, so child labels cannot swallow wheel input. `Ticket41JsxHostAdapterTest` covers editing, scroll ancestor hit testing, cursor paint presence, retained cleanup, and the guest-free paint path; `:26.2.0:test --tests com.tkisor.nekojs.client.ui.Ticket41JsxHostAdapterTest` passed after the fix.

**Acceptance remains in-review:** the live session proves first open/paint, click, input focus/change/edit/delete, Tab/Enter, scroll, resize, reload reopening, and Escape cleanup on NeoForge 26.2. It does not prove the complete cursor selection UI, disabled/tooltip/narration matrix, external `setScreen` replacement cleanup, every native text-selection gesture, or the full resource/render/error matrix. Those acceptance items remain unchecked pending targeted evidence and maintainer conclusion.

## Dependency rationale

- [40: JSX UI common core、公开契约与 Fake Host Proof](40-jsx-ui-common-core.md)：本票消费该先决票的已验收输出；依赖以 Blocked by 为准，不按编号顺序执行。

## Sources

- [NekoJS JSX UI 规格](../../jsx-ui-spec.md)
- [已批准的 JSX UI 票据整合提案](../jsx-ui-ticket-integration-proposal.md)
- [实现票据索引](README.md)

## Scope and coordination

本票属于新增 JSX UI feature。复用既有 runtime、managed surface、资源和诊断 owner，不自动迁移错误 dashboard，不扩张 HUD/容器 GUI，不新增浏览器兼容层。每个新增公开成员随本票实现同步更新 contract、声明、示例与测试；普通测试只读取 golden。真实客户端证据使用注册的 Minecraft MCP，不硬编码端口。

本票不自动成为 34–37 的 P4/1.2.0 发布 blocker。发布票据不表示已实施或已验收；认领与完成规则见索引。
