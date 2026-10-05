# NekoJS

A Minecraft scripting engine built on GraalVM/GraalJS. Modpack authors use modern JavaScript and TypeScript to customize startup, server, and client behavior across Minecraft versions and loaders.

## Language

### Script interface

**Script API**:
The public JavaScript interface used by script and modpack authors. Its primary concern is convenient, readable script authoring.
_Avoid_: User API, frontend interface

**Event Group**:
A named namespace of related Script Events, such as `ServerEvents`. In `ServerEvents.xxx(...)`, `ServerEvents` is the group and `xxx` identifies one event.
_Avoid_: Event bus, individual event

**Script Event**:
A named callback registration entry within an Event Group. Scripts subscribe through calls such as `ServerEvents.xxx(event => {})`; the event supplies the callback's Event Object.
_Avoid_: Plugin Hook, Extension Point

**Event Object**:
The object passed to a Script Event callback, conventionally named `event`. It exposes the data and operations available during that event.
_Avoid_: Event Group, listener

**Binding**:
A named value or callable exposed to a script environment, such as `Item` in `Item.of(...)`. A Binding provides access to its public operations and properties; it is not itself an event subscription.
_Avoid_: Dependency injection binding, Plugin Hook

**Builder**:
An object used to configure a definition being created or registered. In callback-based creation, it is passed as `build` to the final callback, as in `event.create(id, build => {})`.
_Avoid_: Finished registered object, Event Object

**Bean Property**:
A script-facing property backed by exposed Java accessors, such as `event.xxx` for `getXxx()` and assignment for `setXxx(value)`. Reading and writing depend on which accessors the object exposes.
_Avoid_: Java field, unconditional writable property

### Java plugin interface

**Plugin API**:
The public Java interface and extension contracts used by plugin authors.
_Avoid_: Developer API

**Extension Point**:
A named place where plugins contribute one kind of engine capability, such as registry metadata or registry object types.
_Avoid_: Hook, instrumentation point

**Contributor**:
The contribution interface exposed by an Extension Point. Implementing it lets a plugin participate in that point's collection.
_Avoid_: Hook interface, SPI

**Plugin Hook**:
An author-facing method on `NekoJSPlugin`. Collection hooks are facades for paired Extension Points; direct callback hooks are invoked at the relevant lifecycle or platform event.
_Avoid_: Using hook to mean an Extension Point, Contributor, or Script Event

**Extension Handle**:
The handle returned when an Extension Point is registered, providing access to that point's result after bootstrap completes.
_Avoid_: Result reference, provider

### Registration

**Generic Registry**:
The registration system driven by registry metadata and object-type factories, in place of separate handwritten registration wrappers for each type.
_Avoid_: Generic registration, registry center

**Co-registration**:
Related registration performed when another object is registered, such as a Block's BlockItem or a Fluid's block and bucket.
_Avoid_: Automatic registration, implicit registration

### Execution and platforms

**Script Type**:
The execution category of a script: `STARTUP`, `SERVER`, `CLIENT`, or `TEST`. The category determines which script-facing capabilities and lifecycle apply.
_Avoid_: File extension, programming language

**Type-local shared state (`global`)**:
Runtime memory shared by scripts of the same Script Type.
_Avoid_: JavaScript global object, world persistence

**Explicit cross-type shared state**:
Runtime memory accessed through an explicit shared entry by different Script Types within the same NekoJS runtime.
_Avoid_: Client/server network synchronization, cross-process persistence

**Version tree**:
The Stonecutter-managed source tree shared across Minecraft versions and loaders, with version facades and node-specific implementations for platform differences.
_Avoid_: Shared tree, trunk
