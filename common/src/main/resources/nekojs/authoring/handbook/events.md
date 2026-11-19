# Events

Import a Java plugin or built-in Event Group before subscribing. Across both NeoForge and Fabric (and across supported Minecraft versions), all 8 built-in event families (`ServerEvents`, `LevelEvents`, `PlayerEvents`, `CommandEvents`, `BlockEvents`, `ItemEvents`, `EntityEvents`, and `ClientEvents`) publish the identical 80-event portable `"nekojs:event/stable"` contract (`CommonStableEvents`, with immutable `Stable*Event` snapshot views and string ID selectors), while `"nekojs:event/versioned"` exposes each loader's native or wrapper events and dispatch keys where natively supported by the loader API (and NeoForge also exports the raw bus bridge `NativeEvents` from `"nekojs/api/version"` in `STARTUP` scripts). For example, `import { ServerEvents } from "nekojs:event/stable"` selects the portable view, then `ServerEvents.tickPost(event => { ... })` subscribes; plugin event paths append their owner. See [API modules](api-modules.md). These groups are subscription namespaces rather than globals or Binding aggregate exports, and each callback receives an Event Object.

Script-created dynamic groups remain global; import their `ScriptEvents` registrar from `nekojs:event/versioned`.

Use `StartupEvents.registry(selector, callback)` from the versioned module to register content in a STARTUP script. Each callback handles one registry. See [Registry builders](registry-builders.md) for selectors and creation methods.

Before subscribing, resolve the group and event in the current Script Type. Read the final declaration for callback parameters, selectors and event payloads. A same-name event from another mod or scripting engine is not evidence of availability.

Player lifecycle payload names differ by loader: NeoForge's native `PlayerEvents.loggedIn` event exposes `event.entity`; the neutral Fabric payload exposes `event.player`. The shipped login example uses the NeoForge default and documents the single-property Fabric adaptation. Read the actual payload declaration rather than assuming matching event names imply matching members.

Cancellation is specific to the event. For NekoJS cancelable listener contracts, returning `true` cancels; do not invent `event.cancel()`. A cancellable metadata flag does not imply that every event object has that method.

For a player-login handler, recipe-event observation, or a generation-owned timer, follow the file paths, triggers and expected results in [scripting examples](scripting-examples.md).

Keep callbacks bounded. Use an existing Binding for general helpers rather than attaching operations to an event object.
