# Bindings and adapters

Bindings are named script values such as `Item`. Resolve the Binding separately from a Java class with the same short name. A Binding may combine helper instance methods with another type's static members.

`I18n.translate(key, ...args)` is a CLIENT-only Binding on all five supported nodes: NeoForge 1.21.1, 26.1.2 and 26.2.0, and Fabric 26.1.2 and 26.2.0. It resolves a full translation key to a plain string using the current Minecraft client language and resource packs. Missing keys remain visible. Format arguments accept strings, numbers, booleans and null; use Minecraft's translation placeholders such as `%s` and `%1$s`. Translated strings are not cached by the Binding. Call it again when rebuilding UI after a language/resource change; stored strings and existing UI state do not update themselves.

```javascript
// CLIENT scripts on every supported node.
const doneLabel = I18n.translate("gui.done");
const countLabel = I18n.translate("example.saved_count", 3);
const hasTitle = I18n.has("example.title");
const safeCount = I18n.translateWithFallback("example.saved_count", "Saved %s times", 3);
const language = I18n.languageCode;
const rightToLeft = I18n.rightToLeft;
```

`I18n.has(key)` checks membership in the active translation table, including Minecraft's language fallbacks; a translation equal to its key still exists. `I18n.translateWithFallback(key, fallback, ...args)` translates an existing key, or formats the literal fallback template when the key is missing. The fallback is not another translation key. It uses `Locale.ROOT` formatting with the same accepted argument types; malformed fallback formats return `Format error: ` followed by the template. For example, a literal percent sign is `%%`.

`I18n.languageCode` is the selected client language code, such as `zh_cn`; `I18n.rightToLeft` reports the active translation table's text direction. Both are read-only and read current client state on each access. Read them again after a language/resource reload completes. They do not change language settings, subscribe to changes, or automatically mirror/rebuild UI. All five members are available to ordinary CLIENT scripts and the built-in diagnostic session; they are not SERVER or STARTUP Bindings.

`I18n` uses the ordinary Java Binding interop path, including Bean properties and Java varargs conversion. The native implementation validates keys, fallback text and format values; it has no separate member proxy. A final argument array can be expanded by Java interop; in typed scripts, prefer `I18n.translate(key, ...values)` to match the declared rest arguments. A single null format argument stays one value. Null keys/fallbacks and object format values are rejected. Wrong arity, incompatible Java parameter types and writes to getter-only properties use the standard interop errors.

Use `Text.translatable(key, ...args)` when sending text from SERVER scripts so the receiving player resolves its language. That produces a text value instead of the plain string returned by `I18n.translate`. The localization Binding does not imply JSX Screen support on every node.

Use `Item.of("minecraft:iron_sword[damage=2]")` for a component-bearing item string on the supported current nodes. Keep the exact bracket suffix when copying `/nekojs hand` output. The older `{...}` NBT syntax is a different format. Resolving a concrete component value requires the running instance's registries and codecs.

For a complete component-bearing item scenario with its run command and expected log output, see [scripting examples](scripting-examples.md).

Adapters allow specific Java inputs to accept extra script shapes. Check the parameter's emitted input alias and adapter documentation. A string-accepting ItemStack adapter does not make every String parameter an item input, and not every string is a valid item value.

Use readable Bean properties for exposed getters and writable properties for exposed setters. A getter alone does not permit assignment. Parameterized operations remain methods. When a field conflicts with a method, use only the exact selector spelling found in the emitted declaration; some projected methods are deliberately absent.

Entity/player PData on Fabric retains the full `NeoForgeData` persistence container; the platform bridge selects `NekoJSPersistentData` once, matching NeoForge. Other container fields are preserved. Historical Fabric saves may contain a second same-named payload compound. Those bytes remain nested: no automatic flattening or guessed migration occurs because a legitimate user compound is indistinguishable. Back up and inspect affected saves before an intentional migration or a temporary nested read path. Headless bridge/save/sync tests do not replace real-world restart or multiplayer acceptance. ItemStack PData is different: it owns the whole vanilla `CUSTOM_DATA` component, not an entity payload child.

Type IDs in queries identify emitted spellings in a module. This release does not prove assignability or semantic equivalence between different aliases.
