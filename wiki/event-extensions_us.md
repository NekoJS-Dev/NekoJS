<!-- wiki-page: event-extensions; locale: us -->

> **English** · [中文](event-extensions_cn)

<a id="wiki-section-1"></a>
# Event extensions

> This page covers two topics: (1) how plugins register **custom event groups** so scripts can use them like `ServerEvents`; (2) how users use `ScriptEvents` in **startup scripts** to bridge vanilla events into new event groups.

<a id="wiki-section-2"></a>
## Concepts recap

NekoJS's event system, described in [Project architecture: event system](project-architecture_us#wiki-section-10), consists of:

- **`EventGroup`**: a named collection of buses. Factory methods such as `group.server("name", EventClass)` declare an event.
- **`EventBus` / `CancellableEventBus` / `DispatchEventBus`**: three bus forms. The dispatch form routes by key, for example an item/block id.
- **`EventBusJS`**: a GraalJS-facing `ProxyExecutable` allowing scripts to call `EventGroup.eventName(cb)`.
- **`EventBusForgeBridge`**: bridges platform events into neutral buses.

Built-in event groups live under `bindings/event/` and are declared as interfaces with `static final` fields, for example:

```java
public interface ServerEvents {
    EventGroup GROUP = EventGroup.of("ServerEvents");

    EventBusJS<RecipeEventJS, Void> RECIPES = GROUP.server("recipes", RecipeEventJS.class);
    EventBusJS<TagEventJS, Identifier> TAGS =
        GROUP.server("tags", TagEventJS.class, TAG_REGISTRY_KEY);   // Dispatch bus
}
```

This organization is a **convention**. `EventGroup` is an ordinary object; it can be a static field in a class, an enum member, or even a field in a plugin instance. Interface constants are implicitly `public static final` and initialize once when the class loads. That suits the pattern of calling `registry.register(GROUP)` in `registerEvents` and `XXX.post(...)` elsewhere.

<a id="wiki-section-3"></a>
## Register custom event groups in a plugin

<a id="wiki-section-4"></a>
### Step 1: define the event object class

Your event object is the `event` received by script callbacks. **There are no requirements on the class structure**: an ordinary class, a record, or even an existing Minecraft class can be used directly. No base class or interface is required:

```java
package com.example.myaddon;

import net.minecraft.server.level.ServerPlayer;

public class PlayerGreetEvent {
    private final ServerPlayer player;
    private final String greeting;
    public PlayerGreetEvent(ServerPlayer player, String greeting) {
        this.player = player; this.greeting = greeting;
    }
    public ServerPlayer getPlayer() { return player; }
    public String getGreeting() { return greeting; }
}
```

The event class is inspected in only two ways: script-visible members are determined by reflection and `@Remap` / `@HideFromJS` (see [Annotations](annotations_us)), and cancellation support is determined by whether it implements the platform's cancellable-event interface (see below). Getter naming only affects how convenient it is to use from scripts.

> To add JSDoc to the event object, annotate its methods/parameters with `@Doc` / `@Param` / `@Return`, which probe includes in JSDoc, or use programmatic `registerTypeDocs` (`TypeDocsRegister.register(...)`; see [Plugin development](plugin-development_us)).

<a id="wiki-section-5"></a>
### Step 2: declare the event group

```java
package com.example.myaddon;

import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventBusJS;
import net.minecraft.resources.Identifier;

public interface MyEvents {
    EventGroup GROUP = EventGroup.of("MyEvents");

    // Ordinary server event
    EventBusJS<PlayerGreetEvent, Void> PLAYER_GREET =
        GROUP.server("playerGreet", PlayerGreetEvent.class);

    // Dispatch event keyed by Identifier
    EventBusJS<MyCustomEvent, Identifier> CUSTOM =
        GROUP.server("custom", MyCustomEvent.class, MY_DISPATCH_KEY);
}
```

`EventGroup` factory methods:

| Method | Bus type | Purpose |
|---|---|---|
| `GROUP.server(name, eventClass)` | `EventBus` | Always uses `EventBusJS.of(type)`; cancellation is detected from the event class (`EventBusJS.eventCancellability(type)`), not the factory variant or callback form |
| `GROUP.server(name, eventClass, dispatchKey)` | `DispatchEventBus` | Dispatch by key |
| `GROUP.client(...)` / `GROUP.startup(...)` | Same as above | Client/startup side |

These three factory groups are conveniences for `GROUP.add(name, ScriptType.X, bus)`. `add` is public; use it directly when constructing your own bus, for example to force cancellation support as shown below.

`add` enforces two rules: a duplicate event name within a group throws `IllegalArgumentException`; once bootstrap finishes, `EventGroup` is frozen with `freeze()`, and subsequent `add` calls throw `IllegalStateException`. **Duplicate group names are not errors**: `EventGroupRegistry` merges by name, so two plugins using the same group name contribute events to the same group. This is also the supported way to extend another group's events. Only duplicate event names cause an error.

<a id="wiki-section-6"></a>
### Step 3: register the event group in your plugin

```java
@Override
public void registerEvents(EventGroupRegistry registry) {
    registry.register(MyEvents.GROUP);
}
```

<a id="wiki-section-7"></a>
### Step 4: dispatch the event

Post your event at the appropriate point, for example when your mod receives a vanilla event:

```java
import com.tkisor.nekojs.api.event.EventBusJS;

// Ordinary event
MyEvents.PLAYER_GREET.post(new PlayerGreetEvent(player, "Hello"));

// Dispatch event, with a key
MyEvents.CUSTOM.post(someIdentifier, new MyCustomEvent(...));
```

> `post(...)` returns a boolean indicating cancellation. **A non-cancellable bus always returns false**, even if a script returns true. `GROUP.server(name, eventClass)` uses `EventBusJS.of(type)`, whose `eventCancellability(type)` check on NeoForge asks whether the event class `implements ICancellableEvent` (`NeoForgeRuntimeBootstrap`). A POJO such as the `PlayerGreetEvent` above is therefore non-cancellable.
>
> To make a POJO event cancellable, bypass the convenience factory and construct the bus yourself:
>
> ```java
> EventBusJS<PlayerGreetEvent, Void> PLAYER_GREET =
>     MyEvents.GROUP.add("playerGreet", ScriptType.SERVER, EventBusJS.of(PlayerGreetEvent.class, true));
> ```
>
> Only then is `boolean cancelled = PLAYER_GREET.post(...)` meaningful.

<a id="wiki-section-8"></a>
### Script-side usage

Once registered, your events work like built-in events:

```javascript
MyEvents.playerGreet(event => {
  console.info(`${event.getPlayer().getName().getString()} was greeted: ${event.getGreeting()}`)
})

MyEvents.custom('mymod:some_key', event => {
  // Dispatch event: the first argument is the key
})
```

<a id="wiki-section-9"></a>
## ScriptEvents: define events from scripts

NekoJS also provides a mechanism **for script authors**: declare a custom event group in `startup_scripts/`, then listen to it and trigger it from `server_scripts` / `client_scripts` just like a built-in event.
The payload is the value supplied by the caller; NekoJS does not wrap it.

<a id="wiki-section-10"></a>
### Declaration

```javascript
// startup_scripts/register_events.js
ScriptEvents.server(event => event.register(
  'MyEvents',    // Event group name, which becomes a global binding name
  'bossKilled'   // Event name
))

ScriptEvents.client(event => event.register('MyClientEvents', 'hudRefresh'))
```

<a id="wiki-section-11"></a>
### Object form

```javascript
ScriptEvents.server(event => event.register({ group: 'MyEvents', name: 'bossKilled' }))
```

<a id="wiki-section-12"></a>
### Listen and trigger

```javascript
// server_scripts/use_custom.js
MyEvents.bossKilled(payload => {
  console.info(`Killed ${payload.boss}; dropped ${payload.loot}`)
})

// Trigger from any server script
MyEvents.bossKilled.post({ boss: 'ender_dragon', loot: 'dragon_egg' })
```

```javascript
// client_scripts/use_custom_client.js
MyClientEvents.hudRefresh(payload => console.info(payload.reason))
MyClientEvents.hudRefresh.post({ reason: 'manual' })
```

<a id="wiki-section-13"></a>
### Rules

Three rules actually throw exceptions (`ScriptEventsJS` / `ScriptEventRegistry`):

- `group` and `name` must match `[A-Za-z_$][A-Za-z0-9_$]*`, otherwise `register` throws `IllegalArgumentException`.
- A group name must not collide with a built-in event group or a built-in global binding under **any** ScriptType. A collision throws an exception containing `conflicts with built-in ...`.
- Each `(ScriptType, group, name)` can be registered only once, **regardless of the source script**. An existing key throws; the same source does not permit replacement. Commands do not currently reload STARTUP, so changing custom event definitions requires a game restart.

The other rules describe behavior:

- Events declared with `ScriptEvents.server(...)` are available in `server_scripts`; those declared with `.client(...)` are available in `client_scripts`.
- Listen with `Group.name(callback)` and trigger with `Group.name.post(payload)`; payload can be any JS value.
- **Probe coverage**: dynamically declared groups/events enter the event catalog and probe generation (`.d.ts` / `.pyi`).
- Changing startup definitions requires a restart; **server/client reload** updates listeners without declaring definitions again.
- **Platform support**: available consistently on NeoForge (26.x / 1.21.1) and Fabric.

<a id="wiki-section-14"></a>
### Migration: the old native-event-class bridge form

The old third parameter was a NeoForge event class, either its FQN or class object, with additional `priority` / `receiveCancelled` options.
That model cannot be shared across loaders: Fabric events are callback interfaces, not event classes that can be registered by name. It has been removed:

| Old form | Current form |
| --- | --- |
| `event.register('G', 'n', 'net.neoforged...Event$Post')` | `NativeEvents.onEvent('net.neoforged...Event$Post', e => {})` (NeoForge surface) |
| `event.register({ group, name, event, priority, receiveCancelled })` | `NativeEvents.onEvent(priority, receiveCancelled, eventClass, handler)` (NeoForge surface) |
| Named event groups across loaders | `event.register('G', 'n')`, then call `G.n.post(payload)` yourself at the appropriate point |

<a id="wiki-section-15"></a>
## EventBusForgeBridge

Use `EventBusForgeBridge` when your group needs to bridge **native platform events** (NeoForge `IEventBus` / Forge `EVENT_BUS`):

```java
EventBusForgeBridge.create(NeoForge.EVENT_BUS)
    .bind(MyEvents.PLAYER_GREET)        // Bind each event to a native event
    .bind(MyEvents.CUSTOM);
```

`bind(...)` subscribes to native events, forwards them to the neutral bus, and propagates cancellation back to the original `ICancellableEvent`. See built-in groups, such as `FORGE_BRIDGE` at the end of `ServerEvents.java`.

<a id="wiki-section-16"></a>
## Design considerations

The first two items are recommendations without code enforcement; the last three describe mechanisms:

1. **Keep event objects simple** (recommendation): POJOs and records produce the clearest reflected script interface. Complex objects also work, but expose a correspondingly more complex member set.
2. **Keep dispatch keys stable** (recommendation): changing a dispatch key type (`Identifier` / `String` / `Class`) breaks the first argument of existing scripts. This is an API stability commitment you choose to make.
3. **Cancellation comes from the event class** (mechanism), not from choosing a factory method. See the `post` discussion above.
4. **Cross-side access throws** (mechanism): accessing an event declared with `.server(...)` from `client_scripts` makes `EventGroupJS.getMember` throw `Event 'X.y' not available in CLIENT`; an unknown event name throws `No such event bus`. Note that ScriptType predicates overlap: SERVER events are also available in STARTUP/TEST, and CLIENT events are also available in STARTUP.
5. **Short-circuit when there are no listeners** (recommendation, but important on hot paths): expensive emitters such as probe should check `EventBusJS.hasListeners()` first and skip constructing the event object when no listeners exist.

<a id="wiki-section-17"></a>
## Next steps

- [Plugin development](plugin-development_us): the `registerEvents` hook.
- [Annotations](annotations_us): control the event object's interface with `@Remap` / `@HideFromJS` / `@PlatformAvailability`.
- [Event reference](event-reference_us): built-in event groups.

<!-- wiki-nav -->

---

[Previous: Type adapters](type-adapters_us) · [Contents](Home) · [Next: Annotations](annotations_us)
