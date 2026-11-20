# Events

Import a Java plugin or built-in Event Group before subscribing. NeoForge and Fabric publish the same built-in portable event signatures through `"nekojs:event/stable"`. Stable Event Objects expose portable entity roles. Use a string ID when subscribing to an event that takes a selector. `"nekojs:event/versioned"` exposes each loader's native or wrapper events and dispatch keys. NeoForge also exports `NativeEvents` from `"nekojs/api/version"` in STARTUP scripts. For example, `import { ServerEvents } from "nekojs:event/stable"` selects the portable view, then `ServerEvents.tickPost(event => { ... })` subscribes; plugin event paths append their owner. See [API modules](api-modules.md). These groups are subscription namespaces rather than globals or Binding aggregate exports, and each callback receives an Event Object.

Script-created dynamic groups remain global; import their `ScriptEvents` registrar from `nekojs:event/versioned`.

Use `StartupEvents.registry(selector, callback)` from the versioned module to register content in a STARTUP script. Each callback handles one registry. See [Registry builders](registry-builders.md) for selectors and creation methods.

Before subscribing, resolve the group and event in the current Script Type. Read the final declaration for callback parameters, selectors and event payloads. A same-name event from another mod or scripting engine is not evidence of availability.

Player lifecycle payload names differ by loader: NeoForge's native `PlayerEvents.loggedIn` event exposes `event.entity`; the neutral Fabric payload exposes `event.player`. The shipped login example uses the NeoForge default and documents the single-property Fabric adaptation. Read the actual payload declaration rather than assuming matching event names imply matching members.

Cancellation is specific to the event. For NekoJS cancelable listener contracts, returning `true` cancels; do not invent `event.cancel()`. A cancellable metadata flag does not imply that every event object has that method.

For a player-login handler, recipe-event observation, or a generation-owned timer, follow the file paths, triggers and expected results in [scripting examples](scripting-examples.md).

## Stable player and entity roles

`PlayerEvents` exposes `event.player`. A clone or respawn can also expose `event.originalPlayer` if the loader supplies a distinct previous instance. `PlayerEvents.entityInteract` exposes the interacted entity as `event.target`. Client player events and the interaction key event expose `event.player` when the client has a player.

`EntityEvents` exposes `event.entity`. Damage-related payloads also expose `sourceEntity` and `directSourceEntity` when their native damage source names those entities. Block and item payloads expose `actor`. An item interaction with an entity also exposes `target`. Command execution exposes `sourceEntity`, explosions expose `directSourceEntity` and `indirectSourceEntity`, and a per-player datapack sync exposes `player`. A role is `null` when the event has no native entity for it. Stage change notifications describe stored stage state and identify a player by UUID when applicable.

The portable hierarchy is `StableEntity` → `StableLivingEntity` → `StablePlayer` → `StableServerPlayer`, from general to specific. All entities have `name`, `uuid`, `typeId`, `dimension`, `x`, `y`, `z`, `alive` and `clientSide`. Living entities add `health` and `maxHealth`. Players add `creative`, `spectator` and `stages`. A server player adds `latency` in milliseconds. Use `asLivingEntity()`, `asPlayer()` or `asServerPlayer()` to narrow a role. These methods return `null` if the entity has a different type.

```ts
import { PlayerEvents } from "nekojs:event/stable";

PlayerEvents.chat(event => {
    const player = event.player;
    if (!player) return;
    console.info(player.name, player.health, event.message);
    const serverPlayer = player.asServerPlayer();
    if (serverPlayer) console.info(serverPlayer.latency);
});
```

Scalar event properties such as `event.playerUuid` and `event.x` retain their captured values. Entity properties read live state during the synchronous callback. Copy the values you need before returning if you plan to use them later. For explicit native interoperation, see [Native entity conversion](api-modules.md#native-entity-conversion).
