# Bindings and adapters

Bindings are named script values such as `Item`. Resolve the Binding separately from a Java class with the same short name. A Binding may combine helper instance methods with another type's static members.

Use `Item.of("minecraft:iron_sword[damage=2]")` for a component-bearing item string on the supported current nodes. Keep the exact bracket suffix when copying `/nekojs hand` output. The older `{...}` NBT syntax is a different format. Resolving a concrete component value requires the running instance's registries and codecs.

For a complete component-bearing item scenario with its run command and expected log output, see [scripting examples](scripting-examples.md).

Adapters allow specific Java inputs to accept extra script shapes. Check the parameter's emitted input alias and adapter documentation. A string-accepting ItemStack adapter does not make every String parameter an item input, and not every string is a valid item value.

Use readable Bean properties for exposed getters and writable properties for exposed setters. A getter alone does not permit assignment. Parameterized operations remain methods. When a field conflicts with a method, use only the exact selector spelling found in the emitted declaration; some projected methods are deliberately absent.

Entity/player PData on Fabric retains the full `NeoForgeData` persistence container; the platform bridge selects `NekoJSPersistentData` once, matching NeoForge. Other container fields are preserved. Historical Fabric saves may contain a second same-named payload compound. Those bytes remain nested: no automatic flattening or guessed migration occurs because a legitimate user compound is indistinguishable. Back up and inspect affected saves before an intentional migration or a temporary nested read path. Headless bridge/save/sync tests do not replace real-world restart or multiplayer acceptance. ItemStack PData is different: it owns the whole vanilla `CUSTOM_DATA` component, not an entity payload child.

Type IDs in queries identify emitted spellings in a module. This release does not prove assignability or semantic equivalence between different aliases.
