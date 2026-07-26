# Current-Surface Audit

> **Status:** Preview current-surface dump (source commit `b0856c7`).  
> This is NOT the API 1.0.0 normative contract. It records what each platform actually exposes to scripts today.

## Platform Files

| Platform | JSON File | Shared Plugin Source |
|----------|-----------|---------------------|
| neoforge-26.1 | [neoforge-26.1.json](current-surface/neoforge-26.1.json) | `platforms/neoforge-26-shared/src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java` |
| neoforge-26.2 | [neoforge-26.2.json](current-surface/neoforge-26.2.json) | `platforms/neoforge-26-shared/src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java` |
| neoforge-1.21.1 | [neoforge-1.21.1.json](current-surface/neoforge-1.21.1.json) | `platforms/neoforge-1.21.1/src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java` |
| cleanroom-1.12.2 | [cleanroom-1.12.2.json](current-surface/cleanroom-1.12.2.json) | `platforms/cleanroom-1.12.2/src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java` |

## Key Observations

### neoforge-26.1 vs neoforge-26.2

These two platforms share the same `neoforge-26-shared` NekoJSCorePlugin. The JSON dumps are intentionally kept separate even though they are currently identical, to track future divergence.

### neoforge-1.21.1 Differences from 26.x

- Uses `ResourceLocation` instead of `Identifier`
- No `TriState` binding
- No `JsonObjectAdapter` in adapters (but has codec adapter for Fireworks)

### cleanroom-1.12.2 Differences

- Uses Forge class names: `EnumFacing` (Direction), `Vec3d` (Vec3), `AxisAlignedBB` (AABB), `EnumDyeColor` (DyeColor), `EnumParticleTypes` (ParticleTypes), `NBTTagCompound` (CompoundTag)
- `Fluids` maps to `net.minecraftforge.fluids.FluidRegistry` (not MC Fluids)
- `FluidIngredientAdapter` intentionally omitted (would hijack GraalJS `Value→List` mapping)
- Only client binding is `Minecraft` (no `Screen`, `Window`, `KeyMapping`, `InputConstants`)
- Registers `Block`, `EntityEntry`, `SoundEvent`, `Potion`, `PotionEffect`, `TextComponent` which are not on 26.x
- Uses `TileEntityAdapter` instead of `BlockEntityTypeAdapter`/`CreativeModeTabAdapter`
- No `NbtIO`, `JsonIO`, or `global` bindings

### Common Patterns Across All Platforms

- `Item` uses `DelegatingBinding` proxy pattern: `of`/`empty` → `ItemJS`, rest → MC `Item` class
- All platforms register `minecraft` recipe namespace with `MinecraftRecipeHandler`
- All platforms have same 4 script properties: `AFTER`, `DISABLE`, `MODLOADED`, `PRIORITY`
- All platforms share the same event groups (Block, Command, Entity, Goal, Item, Level, Network, Player, Registry, Script, Server + Client)

## Schema

Each JSON file has these fields (arrays sorted by name):

| Field | Description |
|-------|-------------|
| `sourceCommit` | Git commit SHA (fixed: `b0856c7`) |
| `platform` | Platform identifier |
| `globals` | Global bindings (sorted) |
| `clientGlobals` | Client-only bindings (sorted) |
| `events` | Event groups (sorted by group name) |
| `clientEvents` | Client event groups (sorted) |
| `adapters` | Type adapters (sorted) |
| `codecAdapters` | Codec-backed adapters |
| `recipeNamespaces` | Recipe namespaces |
| `scriptProperties` | Script properties (sorted) |
| `nativeTypeLeaks` | Platform-specific type leaks |
| `fakeOrPartialBehaviors` | Delegating/partial behaviors |
| `targetTiers` | Target platform tiers |
