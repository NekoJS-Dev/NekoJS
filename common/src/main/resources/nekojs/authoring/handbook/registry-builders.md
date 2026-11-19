# Registry builders

Import `StartupEvents` from `nekojs:event/versioned` in a STARTUP script. Pass one required selector to `StartupEvents.registry(selector, callback)`. Use the original Minecraft resource path, such as `item`, `block`, or `entity_type`. `item` and `minecraft:item` select the same registry. Use the full key for a modded registry, such as `example:machines`. The entry ID has its own namespace.

- `event.create(id, build)` uses the registry’s default builder.
- `event.create(id, typeName, build)` selects a named builder for the registry. Supply the type name if there is no default.
- `event.createCustom(id, supplier)` queues a supplier the loader calls when committing the native entry. It works for an open registry without a builder. The supplier must return a valid native entry and satisfy script access and loader constraints.

Keep the configuration callback last. Assign supported Bean properties and use methods for actions and nested configuration. See the [item and block examples](scripting-examples.md#register-an-item-and-a-block-at-process-startup).

NekoJS supports only static registries explicitly opened for the current Minecraft version and loader. The host mod creates its native static registry through the loader and a NekoJS plugin declares the full key and supported versions and loaders. Plugins can add named builders to an already open registry. Native registries are never opened through autodiscovery. Enchantments and other data-pack registry entries load through data packs.

`RegistryEvents` was removed without an alias. Split multi-registry listeners by selector, replace registry-specific builder calls with `event.create(...)`, and replace the old supplier registration with `event.createCustom(...)`. Keep the original entry IDs.

Registration changes require a full process restart. Check the current version and loader’s declarations. The `fluid` selector is NeoForge-only.
