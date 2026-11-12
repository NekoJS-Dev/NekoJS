<!-- wiki-page: event-extensions; locale: us -->

> **English** · [中文](event-extensions_cn)

<a id="wiki-section-1"></a>
# Event extensions

This page covers how to add new script events, in two ways:

- **Plugin authors** declare an event group in Java, so scripts listen to your events the same way they use `ServerEvents`.
- **Script authors** declare an event group with `ScriptEvents` in a startup script, without Java, and fire it from scripts.

For the overall design of the event system, see [Project architecture](project-architecture_us#wiki-event-system).

<a id="wiki-section-2"></a>
## Plugin event groups: minimal example

These four steps create a server event, `MyEvents.playerGreet`.

**1. The event object.** This is the `event` a script callback receives. It can be a plain class or a record and doesn't need to extend anything:

```java
package com.example.myaddon;

import net.minecraft.server.level.ServerPlayer;

public class PlayerGreetEvent {
    private final ServerPlayer player;
    private final String greeting;

    public PlayerGreetEvent(ServerPlayer player, String greeting) {
        this.player = player;
        this.greeting = greeting;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getGreeting() { return greeting; }
}
```

The two getters become the read-only properties `event.player` and `event.greeting`.

**2. The event group.** Create the group with `EventGroup.of` and declare events with its factory methods:

```java
package com.example.myaddon;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;

public interface MyEvents {
    EventGroup GROUP = EventGroup.of("MyEvents");

    EventBusJS<PlayerGreetEvent, Void> PLAYER_GREET =
        GROUP.server("playerGreet", PlayerGreetEvent.class);
}
```

**3. Register it.** Register the group in your plugin's `registerEvents`:

```java
@Override
public void registerEvents(EventGroupRegistry registry) {
    registry.register(MyEvents.GROUP);
}
```

**4. Fire it.** Call `post` at the right moment, for example while your mod handles a vanilla event:

```java
MyEvents.PLAYER_GREET.post(new PlayerGreetEvent(player, "Hello"));
```

Scripts listen like this:

```javascript
// server_scripts/greet.js
MyEvents.playerGreet(event => {
  console.info(`${event.player.getName().getString()} was greeted: ${event.greeting}`)
})
```

Keeping the group in interface constants is only a convention. `EventGroup` is a plain object, and a static field on a class works just as well. Interface fields are implicitly `public static final` and initialized once when the class loads, which is convenient.

<a id="wiki-section-3"></a>
## Declaring events

<a id="wiki-section-4"></a>
### Factory methods

| Method | Script side | Description |
|---|---|---|
| `GROUP.server(name, eventClass)` | Server | A normal event |
| `GROUP.client(name, eventClass)` | Client | A normal event |
| `GROUP.startup(name, eventClass)` | Startup | A normal event |
| `GROUP.server(name, eventClass, dispatchKey)` | Server | Dispatched by key; `client`/`startup` work the same way |
| `GROUP.add(name, scriptType, bus)` | Your choice | Pass a bus you built yourself, for example to force it cancellable |

The factory methods are shorthand for `add`, which checks two things:

- A duplicate event name within the group throws `IllegalArgumentException`.
- Event groups are frozen after startup; calling `add` afterwards throws `IllegalStateException`.

**A duplicate group name is not an error.** `EventGroupRegistry` merges groups by name, so if two plugins use the same group name, their events end up in one group. This is the proper way to add events to someone else's group. Only a duplicate event name fails.

<a id="wiki-section-5"></a>
### Dispatching by key

A dispatched event lets a script listen for one key only, such as one item or one ID:

```java
EventBusJS<PlayerGreetEvent, Identifier> CUSTOM =
    GROUP.server("custom", PlayerGreetEvent.class, DispatchKey.of(Identifier.class));

// Pass the key when firing
MyEvents.CUSTOM.post(new PlayerGreetEvent(player, "Hello"), someIdentifier);
```

```javascript
MyEvents.custom('mymod:some_key', event => {
  // Runs only when the key is mymod:some_key
})
```

`DispatchKey` has three factories: `of(keyType)`, `of(keyType, event -> key)` (computes the key from the event object) and `string()`. The key a script passes is converted to `keyType`, so type adapters apply here too.

Once you pick a key type, avoid changing it. Changing it breaks the first argument of every existing script.

The Java snippets on this page use 26.x class names. On 1.21.1, replace `Identifier` with `ResourceLocation`.

<a id="wiki-section-6"></a>
### Cancellable events

`post` returns whether the event was cancelled. Only cancellable buses can return `true`. A non-cancellable bus always returns `false`, even if a script does `return true`.

The factory methods decide cancellability from the event class:

- **NeoForge**: the bus is cancellable only if the event class implements `ICancellableEvent`.
- **Fabric**: buses created by the factory methods are never cancellable.

So `PlayerGreetEvent` above is not cancellable. To make your own event cancellable, build the bus with `add` and **replace** the original declaration (don't add a second event with the same name):

```java
EventBusJS<PlayerGreetEvent, Void> PLAYER_GREET =
    MyEvents.GROUP.add("playerGreet", ScriptType.SERVER, EventBusJS.of(PlayerGreetEvent.class, true));

boolean cancelled = PLAYER_GREET.post(new PlayerGreetEvent(player, "Hello"));
```

For a cancellable event with a key, use `EventBusJS.of(eventClass, true, dispatchKey)`.

<a id="wiki-section-7"></a>
### Client events and logical sides

Register client-only event groups with `registerClientEvents(EventGroupRegistry)`. Dedicated servers don't load them.

`.server(...)` and `.client(...)` only decide **which scripts can access** the event. They don't filter logical sides or switch threads for you. In singleplayer, many native events fire once on the client and once on the server. Before posting to a server event, filter out the client one; otherwise the server script is entered from the render thread and GraalJS fails with a multi-threaded access error. NeoForge's `bind(bus, predicate)` does exactly this; see below.

Script access rules:

- Server events can be used from server, startup and test scripts. Client events can be used from client and startup scripts.
- Using an event where it isn't allowed fails with an error like `Event 'X.y' not available in CLIENT`.
- An event name that doesn't exist fails with `No such event bus: X.y`.

<a id="wiki-section-8"></a>
## Bridging native events

<a id="wiki-section-9"></a>
### NeoForge: EventBusForgeBridge

`EventBusForgeBridge` connects NeoForge `IEventBus` events to a NekoJS bus. Once bound, the bridge posts for you; you don't fire the event yourself:

```java
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

EventBusForgeBridge.create(NeoForge.EVENT_BUS)
    .bindTransformed(MyEvents.PLAYER_GREET,
        event -> new PlayerGreetEvent((ServerPlayer) event.getEntity(), "Hello"),
        PlayerEvent.PlayerLoggedInEvent.class);
```

| Method | Use |
|---|---|
| `bind(bus)` / `bind(bus, priority, receiveCancelled)` | The event object is itself a NeoForge `Event`; post it directly |
| `bind(bus, filter)` / `bind(bus, filter, priority, receiveCancelled)` | Post only events that pass `filter`; usually used to filter logical sides |
| `bindTransformed(bus, transformer, eventClass)` | Convert the native event into your event object first, then post |
| `bindCancellable(bus, cancelAction)` | The native event has no `ICancellableEvent` but its own way to "cancel" (such as setting damage to 0) |

When the bus is cancellable and the native event implements `ICancellableEvent`, a script cancel is written back to the native event automatically. `bindCancellable` requires a cancellable bus and fails at bind time otherwise.

For how built-in groups do this, see `FORGE_BRIDGE` at the end of `ServerEvents.java`.

<a id="wiki-section-10"></a>
### Fabric: FabricEventBusBridge

Fabric events are callback interfaces, so there is no event class to attach by name. `FabricEventBusBridge` hands the payload to a NekoJS bus while you register the callback:

```java
FabricEventBusBridge.create().bind(
    CommandEvents.REGISTER,
    listener -> CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
        listener.accept(new CommandRegistryEventJS(dispatcher, registryAccess))));
```

| Method | Use |
|---|---|
| `bind(bus, register)` | The callback payload is already the script event object |
| `bindTransformed(bus, register, transform)` | Convert, then post; skips the conversion when no script is listening |
| `bindCancellable(bus, register)` | Return the script's cancel result to the Fabric callback |
| `bindDispatched` / `bindTransformedDispatched` / `bindCancellableDispatched` | Dispatched-by-key versions of the three above |

Keep the event group, event name, payload and cancel rules the same on both loaders where you can. The real callback signatures differ, so the registration code is written separately. If Fabric has no matching API, implement it with a Mixin or state clearly that the event is unavailable on Fabric; don't pretend it works.

<a id="wiki-section-11"></a>
## Publishing to API modules

Event groups are provided as global names by default. You can also publish the same event in the `nekojs/api/version/<modid>` or `nekojs/api/stable/<modid>` module, so scripts can `import` it (see [Module system](module-system_us#wiki-api-modules)):

```java
EventBusJS<PlayerGreetEvent, Void> PLAYER_GREET = GROUP.server(
        "playerGreet", PlayerGreetEvent.class,
        publication -> publication.versioned().scriptTypes(ScriptType.SERVER));
```

Still register `GROUP` with `registerEvents`. If your mod ID is `myaddon`, server scripts can import `MyEvents` from `nekojs/api/version/myaddon`.

- Publishing reuses the original event; there is no extra bus and no extra native listener. The global form keeps working. A script that subscribes through both forms registers two listeners.
- `publication.stable()` only accepts payloads that pass the stable public-type check. Use `stable(View.class, converter)` to convert, but this conversion is not allowed for cancellable events.
- You can also call `GROUP.expose(...)` for a whole group. A stable group must list the events to publish with `events(...)`.
- Unknown event names, duplicate publications and invalid views fail with `NEKO-4057`.

<a id="wiki-section-12"></a>
## ScriptEvents: defining events in scripts

Script authors can declare their own event groups without Java. Declare them in a startup script, then listen to and fire them from server or client scripts:

```javascript
// startup_scripts/register_events.js
ScriptEvents.server(event => event.register('MyEvents', 'bossKilled'))
ScriptEvents.client(event => event.register('MyClientEvents', 'hudRefresh'))

// The object form also works
// ScriptEvents.server(event => event.register({ group: 'MyEvents', name: 'bossKilled' }))
```

```javascript
// server_scripts/boss.js
MyEvents.bossKilled(payload => {
  console.info(`Killed ${payload.boss}, dropped ${payload.loot}`)
})

// Fire it from any server script
MyEvents.bossKilled.post({ boss: 'ender_dragon', loot: 'dragon_egg' })
```

```javascript
// client_scripts/hud.js
MyClientEvents.hudRefresh(payload => console.info(payload.reason))
MyClientEvents.hudRefresh.post({ reason: 'manual' })
```

- Events declared with `ScriptEvents.server` are used in server scripts; those declared with `ScriptEvents.client` in client scripts.
- The value passed to `post` goes to listeners unchanged and can be any JS value. These events can't be cancelled, and `post` returns nothing.
- Declarations are part of startup scripts, so changing them needs a game restart. `/nekojs reload` only updates listeners.
- Declared events show up in the types generated by `/nekojs probe`.
- Works on both NeoForge and Fabric.

These cases fail with an error:

- The group or event name is not a valid JS identifier (it must match `[A-Za-z_$][A-Za-z0-9_$]*`).
- The group name matches a built-in event group or a built-in binding in any script type: `conflicts with built-in ...`.
- The same group and event name was already declared for that script type: `Script event already registered`.

If a plugin already registers an event group called `MyEvents`, scripts can't use that group name.

<a id="wiki-section-13"></a>
### Migrating from the old form

The old `register` accepted a NeoForge event class as a third argument to attach a native event directly. That can't work on Fabric, so it was removed:

| Old form | Current form |
|---|---|
| `event.register('G', 'n', 'net.neoforged...Event$Post')` | `NativeEvents.onEvent('net.neoforged...Event$Post', e => {})` (NeoForge only) |
| `event.register({ group, name, event, priority, receiveCancelled })` | `NativeEvents.onEvent(priority, receiveCancelled, eventClass, handler)` (NeoForge only) |
| A named event group that works on every loader | `event.register('G', 'n')`, then call `G.n.post(payload)` yourself at the right moment |

<a id="wiki-section-14"></a>
## Design tips

- **Keep event objects simple.** Plain classes and records give the cleanest script members. Adjust members with `@Remap` and `@HideFromJS`, and add probe docs with `@Doc`; see [Annotations](annotations_us).
- **Check for listeners when the payload is expensive.** On hot paths, call `EventBusJS.hasListeners()` first and skip building the event object when no script is listening.
- **Listener exceptions don't reach your code.** `post` catches and logs exceptions thrown by listeners, then returns `false`.

<a id="wiki-section-15"></a>
## Next steps

- [Plugin development](plugin-development_us): `registerEvents` and the other plugin hooks.
- [Annotations](annotations_us): control which members an event object shows to scripts.
- [Event reference](event-reference_us): the list of built-in event groups.

<!-- wiki-nav -->

---

[Previous: Type adapters](type-adapters_us) · [Contents](Home) · [Next: Annotations](annotations_us)
