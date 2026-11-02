# Events

Subscribe through an Event Group: `ServerEvents.tickPost(event => { ... })`. The callback receives an Event Object; the group is a subscription namespace.

Before subscribing, resolve the group and event in the current Script Type. Read the final declaration for callback parameters, selectors and event payloads. A same-name event from another mod or scripting engine is not evidence of availability.

Cancellation is specific to the event. For NekoJS cancelable listener contracts, returning `true` cancels; do not invent `event.cancel()`. A cancellable metadata flag does not imply that every event object has that method.

For a player-login handler, recipe-event observation, or a generation-owned timer, follow the file paths, triggers and expected results in [scripting examples](scripting-examples.md).

Keep callbacks bounded. Use an existing Binding for general helpers rather than attaching operations to an event object.
