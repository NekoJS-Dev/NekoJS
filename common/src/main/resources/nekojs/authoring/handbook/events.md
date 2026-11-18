# Events

Import a Java plugin/native Event Group before subscribing. For example, `import { ServerEvents } from "nekojs:event/stable"` selects the portable server tick view, then `ServerEvents.tickPost(event => { ... })` subscribes. Use `nekojs:event/versioned` for the native payload instead; plugin event paths include their owner. See [API modules](api-modules.md). These groups are not globals or Binding aggregate exports. The callback receives an Event Object; the group is a subscription namespace.

Script-created dynamic groups remain global; import their `ScriptEvents` registrar from `nekojs:event/versioned`.

Before subscribing, resolve the group and event in the current Script Type. Read the final declaration for callback parameters, selectors and event payloads. A same-name event from another mod or scripting engine is not evidence of availability.

Player lifecycle payload names differ by loader: NeoForge's native `PlayerEvents.loggedIn` event exposes `event.entity`; the neutral Fabric payload exposes `event.player`. The shipped login example uses the NeoForge default and documents the single-property Fabric adaptation. Read the actual payload declaration rather than assuming matching event names imply matching members.

Cancellation is specific to the event. For NekoJS cancelable listener contracts, returning `true` cancels; do not invent `event.cancel()`. A cancellable metadata flag does not imply that every event object has that method.

For a player-login handler, recipe-event observation, or a generation-owned timer, follow the file paths, triggers and expected results in [scripting examples](scripting-examples.md).

Keep callbacks bounded. Use an existing Binding for general helpers rather than attaching operations to an event object.
