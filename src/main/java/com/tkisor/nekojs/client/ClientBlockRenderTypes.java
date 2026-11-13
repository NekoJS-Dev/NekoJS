//? if neoforge {
//? if <26 {
/*package com.tkisor.nekojs.client;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;

/^*
 * Applies client render layers for 1.21.1; this class is not loaded on a dedicated server.
 *
 * <p>In 1.21.1 the chunk render layer comes from the static lookup table in
 * {@link ItemBlockRenderTypes}, and NeoForge patches {@code setRenderLayer}, which must be called
 * while {@code ClientModLoader.isLoading()} is true. RegisterEvent fires during the client mod
 * loading phase, when the blocks already exist and {@code ClientModLoader.isLoading()} is true, so
 * the layer is applied directly in the BLOCK branch.
^/
public final class ClientBlockRenderTypes {
private ClientBlockRenderTypes() {}

/^* Applies a block render layer by name; an unknown name falls back to solid. ^/
@SuppressWarnings("deprecation") // setRenderLayer(Block, ChunkRenderTypeSet) is marked @Deprecated(since=1.19)
// in 1.21.1 with no replacement, and is still the official usage
public static void apply(Block block, String renderType) {
RenderType layer = parse(renderType);
ItemBlockRenderTypes.setRenderLayer(block, ChunkRenderTypeSet.of(layer));
}

/^* Applies a fluid render layer (liquid blocks and the fluid itself default to translucent). ^/
public static void applyFluid(Fluid fluid, String renderType) {
RenderType layer = parse(renderType);
ItemBlockRenderTypes.setRenderLayer(fluid, layer);
}

private static RenderType parse(String renderType) {
return switch (renderType == null ? "solid" : renderType) {
case "cutout" -> RenderType.cutout();
case "cutout_mipped", "cutoutmipped" -> RenderType.cutoutMipped();
case "translucent" -> RenderType.translucent();
default -> RenderType.solid();
};
}
}
 *///?}
//?}
