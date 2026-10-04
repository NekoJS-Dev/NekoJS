# Ticket 41 lifecycle client evidence

Branch `mult`, source baseline `47dad5ae`; the jar was built from that working tree before the later 43/44 integration.

## Environment and input

Minecraft 26.2 / NeoForge 26.2.0.75, Zulu Java 25.0.3, MCP mod 0.2.1. Launcher game directory: `%APPDATA%/.minecraft/mcp_launcher/game`.

Live jar SHA-256: `5A75147F77E3258552D8DF4977A9FC4A8E17A52FC99DD7C94C87331552D4DD52`.

Canonical fixture: [`ticket41-lifecycle-flow.tsx`](../../../../src/test/resources/nekojs/client/ui/ticket41-lifecycle-flow.tsx).

## Actual observations

The maintainer selected “全部符合预期，已回到主菜单” after executing the six requested actions.

1. `Throw event error` emitted the intentional `NEKO-7007` event diagnostic for `ticket41-first-root`, generation 2, candidate=false.
2. `Healthy after error` remained usable and logged `healthy=1 frozen=true target=healthy`.
3. `Replace Screen` opened the second root through the existing platform Screen route.
4. Both the immediate and explicit follow-up checks logged `oldDisposed=true staleDispatch=false`.
5. Escape was ignored while `closeOnEscape=false`.
6. After `Allow Escape`, the user confirmed Escape closed the Screen. The saved final screenshot shows the Minecraft main menu.

The intentional event failure was the only JSX lifecycle failure. Startup additionally reported an erroneous binding-preflight `secondHost` unknown identifier from the module's comma-separated declaration. It did not prevent the callbacks from executing. The compiler regression reproducing this false positive failed first and passed after `ValParser` was fixed; this is recorded separately from the live jar's result.

## Artifacts and cleanup

- `client-observations.log`: actual event failure, subsequent healthy callback, replacement cleanup and close-policy marker.
- `startup.png`: first Screen visible.
- `after-close.png`: main menu after the close-policy test.
- Original launcher fixture and jar restored after the user confirmed main menu; test client PID 20884 stopped. Backup copies remain available until final cleanup.

No whole-ticket sign-off is authored. This evidence covers event-error retention, immutable event envelope observation, external Screen replacement, old-root/event invalidation and conditional Escape cleanup; it does not prove dedicated-server class loading, narration variants or every native text-editing gesture.
