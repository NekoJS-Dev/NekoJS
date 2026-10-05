# Ticket 44 font and resource-reload follow-up

Read-only audit against the built NeoForge 26.1.2.71 and 26.2.0.57 Minecraft artifacts. Native signatures and reload bytecode were checked with `javap`; this audit does not establish live font behavior.

## Font selection still missing

The canonical label props, primitive whitelist, `VisualSpec`, and `VisualStyleResolver` have no `font` member. The host measureText contract takes only text, size, and width; both measurement and drawing always use the default font. `resolveFont` is an unused lookup contract rather than an authorable selection feature.

Both nodes support `FontDescription.Resource(Identifier)`, `Style.EMPTY.withFont(FontDescription)`, `Font.width(FormattedText)`, and `GuiGraphicsExtractor.text(Font, Component, int, int, int, boolean)`. A controlled label font id could use one selected Style for both measurement and painted cached lines, with an optional fourth measureText parameter preserving the three-argument host contract. Missing/invalid ids need a locatable diagnostic and the same explicit fallback on both paths. Such a contract change requires canonical-source declaration regeneration and Probe type checks.

Native FontManager uses `FileToIdConverter.json("font")` on both nodes. The current resolver's acceptance of `fonts/` and the common resolver documentation's supposed directory rename do not match native loading. A resource existing in `fonts/` can report RESOLVED without becoming a usable native font. JSON presence alone also does not prove native provider decoding/loading; malformed definitions may fall back to missing glyphs. Implementation and acceptance must distinguish these facts.

## Resource revision and UI ownership

The production revision is `resources.listPacks().toList()`, compared through List.equals. It observes pack instance equality, not file contents or enabled pack names. Minecraft resource reload opens selected packs again; `NekoJSPackLoader.openFull` delegates to `openPrimary`, which constructs a new `NekoJSPathPackResources`. These pack classes do not override equality. An ordinary F3+T therefore changes the usual revision even when pack names stay the same. A disk edit alone, or a caller reusing equal pack objects, does not change it.

The existing `NekoJSClient` F3+T listener reloads CLIENT scripts and publishes the new generation. A successful reload closes the previous generation's Screen/root. A repaired image visible afterward is proof of new-generation resource readback, not old-root preservation.

The current Screen paint path only consumes retained plans. `inspect` explicitly refreshes resources and reprojects the stored layout basis; successful publication precedes old slot retirement. This can prove an image repair when a usable root, changed revision, RESOLVED status, and subsequent painted pixels are observed. It does not prove automatic refresh without that trigger.

Font resource changes would additionally require canonical common measure/arrange, such as `root.resize(viewport())`, without guest render. Rewrapping text against the stored layout basis would leave Inspector rectangles stale.
