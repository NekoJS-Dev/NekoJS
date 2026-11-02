# Registry builders

Register content in STARTUP scripts through the instance's published registration Event Group and Builder declarations. Required identifying arguments precede the configuration callback: `event.create(arg1, build => { ... })` is a shape example, not a promised registry entry.

Use the actual convenience entry or generic registry contract exposed in the current declarations. Builder configuration goes last. Assign supported Bean properties; use methods for actions and compound configuration.

For a complete item/block registration file and disposable-world restart workflow, see [scripting examples](scripting-examples.md).

Registration changes require a full process restart. Registry entries, builder types, assets and co-registration behavior differ by platform. Do not infer that a NeoForge fluid builder or a 1.21.1 enchantment builder exists on Fabric or 26.x.

Avoid creating a second identifier to bypass a duplicate registration error. Diagnose the original definition and load order.
