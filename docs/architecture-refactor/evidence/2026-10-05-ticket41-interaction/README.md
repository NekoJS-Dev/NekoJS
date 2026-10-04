# Ticket 41 follow-up: input selection and pointer capture

Execution owner: DSH main session, branch `mult`, follow-up to `8526d5c4`.

## Scope

Implement the reviewed input/scroll gaps without closing the remaining client acceptance matrix. Preserve unrelated working changes and restore the launcher game's original jar and fixture after testing.

## Regression loop

Command: `./gradlew.bat :26.2.0:test --tests com.tkisor.nekojs.client.ui.Ticket41JsxHostAdapterTest --console=plain`.

The added `fullInputRejectsInsertionWithoutDiscardingTheExistingSuffix` and `focusedInputAcceptsControlledValueUpdatesAndPreservesSelectionForUnchangedValues` tests initially failed (9 tests, 2 failures). After the shared state fixes, the focused test task passed, including capture across reconciliation and same-id node replacement.

- Insertion caps only new text by Unicode code points; existing suffix text remains intact.
- Controlled value updates apply while focused; unchanged values preserve cursor/selection.
- Cursor and selection updates belong to the retained input node.
- Capture belongs to the retained tree and uses node identity plus the pressed mouse button. Replacement by another node with the same script id does not inherit capture.
- Nested scroll routing selects the deepest visible scroll container; host scrolling does not depend on a guest callback.
- Focused inputs paint insertion cursor/selection; buttons paint pressed state.
- Omitted `value` preserves uncontrolled text across reconciliation. Explicit controlled values remain authoritative.
- Shortened scroll content clamps the retained offset and translates the already-arranged children back into the valid range.

The last two edge fixes were added after live capture and are covered by node regression tests; the recorded client jar checksum identifies the live-smoke build, not the later edge-fix build.

## Live fixture

Canonical fixture: [`ticket41-interaction-flow.tsx`](../../../../src/test/resources/nekojs/client/ui/ticket41-interaction-flow.tsx). It has controlled input, an activation/release counter, a disabled button and a scroll container with no `onScroll` handler.

Planned observations: input selection/replacement, pressed appearance and release outside the control, scrolling without callbacks, disabled rejection and Escape cleanup. Record only user-confirmed observations and corresponding game logs; rendering and startup alone are not acceptance.

## Environment

Minecraft 26.2; launcher NeoForge 26.2.0.75; compiled against repository NeoForge 26.2.0.57; Zulu Java 25.0.3; MCP mod 0.2.1. The client uses `%APPDATA%/.minecraft/mcp_launcher/game`, separate from `%USERPROFILE%/.minecraft`.

Client jar and staged jar SHA-256 matched before launch. Existing launcher jar and fixture were backed up with `.before-final` suffixes.

## Observed result (2026-10-05)

The user confirmed all requested interactions on the freshly built jar:

- Input selection: `Neko` changed to `NeX`; the filtered log records `change=NeX`.
- Pointer capture: moving outside the pressed button and releasing produced paired `click=1` / `release=1` records; a second normal activation produced `click=2` / `release=2`.
- Uncontrolled scroll survived a later button/state reconcile; the post-operation screenshot starts at `Scroll line 7`, proving the content stayed offset instead of resetting to line 1.
- Disabled button did not activate.
- Escape closed the Screen and returned to the main menu; no JSX `NEKO-700*` or `ui-host-update` records appeared in the filtered log.

The final jar and original launcher fixture were restored after capture. The remaining unrelated game-log noise is offline-auth/Realms failure; it is not a NekoJS CLIENT fixture failure.

## Verification status

- `:common:check :26.1.2:test :26.2.0:test :1.21.1:test guardLint`: PASS.
- `:26.2.0:test --tests com.tkisor.nekojs.client.ui.Ticket41JsxHostAdapterTest`: PASS after the Unicode, controlled-input, scroll-state, capture, selection, and cursor fixes.
- `:26.1.2-fabric:compileJava :26.2.0-fabric:compileJava`: PASS from the same worktree before the final NeoForge-only follow-up; the guarded Adapter is outside Fabric output.
- Final gate: `:common:check :26.2.0:build :26.1.2:test :1.21.1:test :26.1.2-fabric:compileJava :26.2.0-fabric:compileJava guardLint`: PASS. The ticket 41 focused suite has 14 tests, zero failures/errors; guardLint reports zero warnings.
- Test client and user game files: restored.

## Review

Parallel Standards and Spec review found Unicode cursor clamping, uncontrolled-state retention and weak source-token assertions. Follow-up changes fixed the input/scroll regressions, replaced capture routing with retained identity, and removed the newly added selection/capture token assertions. A final scoped review found no remaining actionable defect; duplicate cursor-normalization shape remains an optional design observation. Live evidence and node-only edge checks remain distinguished above.

## Remaining acceptance boundary

This evidence closes the specific live paths above but does not by itself sign the entire ticket: external `setScreen` replacement cleanup, every disabled/tooltip/narration variant, full native mouse text-selection gestures, script/render/resource failure preservation, and owner-thread/generation matrices still require their dedicated evidence or maintainer conclusion.
