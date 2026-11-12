<!-- wiki-page: script-api-style; locale: us -->

> **English** · [中文](script-api-style_cn)

<a id="wiki-section-1"></a>
# Script API conventions and object parameters

NekoJS favors **event groups, named Bindings, operations on event objects, configuration callbacks and Bean properties**. Reusing another project's implementation mechanism does not require copying its script-facing shape. This page separates callable interfaces from proposed cleanup; it does not teach unimplemented overloads.

<a id="wiki-section-2"></a>
## Event groups subscribe

An event is a member of its group. Required filters/selectors, where present, precede the callback. A configuration object does not create the group, and assigning group members does not define events.


```js
// server_scripts/welcome.js (NeoForge)
PlayerEvents.loggedIn(event => {
    event.entity.sendMessage("Welcome to NekoJS!");
});
```

This login example targets NeoForge: the native PlayerEvent exposes `getEntity()` as `event.entity`. Fabric’s neutral login payload uses `event.player`; the same Script Event name does not guarantee identical payload members.

Read callback data and event operations from event; use Bindings for general helpers. Consult the [event reference](event-reference_us) for timing, cancellation, Script Types and platform support instead of inferring them from names.

<a id="wiki-section-3"></a>
## Bindings and configuration callbacks

Item.of, for example, is a method on a named Binding. Configurable definitions put identifying/required arguments first and the configuration callback last. This real registration entry belongs in startup_scripts. Static registration normally requires a process restart; dynamic registration is a separate constrained capability described under [registering content](registering-new-content_us).

```js
// startup_scripts/content.js
RegistryEvents.register(event => {
    event.item("nekojs_example:ruby", build => {
        build.maxStackSize = 16;
    });
});
```

The event coordinates creation; build configures the definition. They are distinct objects. No extra build() is needed. Chain calls only when those methods actually return the builder.

<a id="wiki-section-4"></a>
## Bean properties are not arbitrary fields

Published JavaBean read/write capabilities normally appear as script properties, subject to the actual wrapper and generated declaration:

- Read-only properties cannot be assigned; write-only properties cannot be read.
- Ordinary parameterized or operational methods do not become properties merely because their names contain get/set.
- Use actually exposed properties such as event.player or the registration builder's maxStackSize; do not invent members from Java class names.
- Repair runtime/declaration drift at its shared source rather than teaching users to bypass validation.

<a id="wiki-section-5"></a>
## When {} carries legitimate data

| Shape | Meaning | Automatically replace with a builder? |
|---|---|---|
| event.remove({ id: "minecraft:stick" }) | Recipe filter data | No; this is the current filter contract |
| Custom recipe JSON, components and shader JSON | Data/resource formats | No; preserve their structure and validation |
| JSX props and UI.createRoot property/host options | UI property data and runtime interfaces | No; braces alone do not establish a problem |
| MCP/HTTP request bodies | External protocol messages | No; these are not game-script event interfaces |
| Many optional settings used to create a behavioral definition | Configurable-definition interface | Review whether a configuration callback and named operations fit better |

<a id="wiki-section-6"></a>
## Explicit data options and real builders

These implemented interfaces serve different responsibilities:

- ClientEvents.hudRender(id, options, callback) / worldRender: options is explicit scheduling data { layer, priority }. Layer-string and integer-priority shorthands and (id, callback) remain supported. Runtime validation and Probe share fields and layer literals; unknown fields, invalid layers, and non-int32 priorities fail. This is not a definition builder.
- ClientEvents.postEffects(event => event.register(id, build => { ... })): register provides a real node-specific post-effect builder with configuration last. On 26.x, example properties are build.blurRadius = 4 and build.blurRounds = 1; 1.21.1 has its own chainJson/program fields. The builder seals after the callback and requires no build() call. The legacy object/Map overload remains, with the same strict field/value validation.

See [client rendering and post effects](client-rendering_us) for complete examples, lifecycle and profiles. An installed post-effect definition is not necessarily an activatable resource.

Verify runtime behavior, Probe output, and failure retention together. Braces alone do not decide whether an API follows the conventions: shader JSON, recipe filters, and render scheduling data retain appropriate data structures.

<a id="wiki-section-7"></a>
## Reviewing a borrowed implementation

Check the event group and Script Type, callback-last ordering, creation/configuration separation, property/method accuracy, candidate failure retention and declaration/runtime parity. Source comments identify some mechanisms borrowed from Katton; they do not prove every object parameter came from Katton or every port violates NekoJS conventions.

For an unlisted interface, inspect the current instance's Probe declarations and [precise AI queries](ai-support_us), then report missing or inconsistent coverage. Do not infer NekoJS members directly from another project's examples.

<!-- wiki-nav -->

---

[Previous: Script basics](script-basics_us) · [Contents](Home) · [Next: Script properties](script-properties_us)
