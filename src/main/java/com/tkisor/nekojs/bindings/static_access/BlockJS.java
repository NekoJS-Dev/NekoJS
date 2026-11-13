//~ mc_legacy_api
package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.js.type_adapter.ParseIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Script-side helper for blocks, bound as the global {@code Block}.
 *
 * <p>Matches KubeJS semantics: {@code Block.id('minecraft:stone')} looks up a block by string id.
 * A wrapper object is used instead of injecting static methods into Minecraft's {@code Block}
 * class (same reason as in the ItemJS comment).
 */
public class BlockJS {

    /** Looks up a block by string id; returns null when it does not exist. The id defaults to the
     * {@code minecraft:} namespace. */
    public Block id(String id) {
        Identifier location = ParseIds.parseItemOrBlockId(id);
        return BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
    }

    /** Returns the block's registry id (in the form {@code minecraft:stone}). */
    public Identifier idOf(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }
}
