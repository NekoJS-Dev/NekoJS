package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;

/**
 * Loader-neutral payload for the portal spawn event ({@code BlockEvents.portalSpawn}).
 *
 * <p>The member names follow NeoForge 26.x {@code BlockEvent.PortalSpawnEvent}:
 * {@code event.level} (LevelAccessor) / {@code event.pos} / {@code event.state} /
 * {@code event.portalSize} (NeoForge's getLevel/getPos/getState/getPortalSize, where
 * {@link PortalShape} is a vanilla class and loader-independent). Dispatched by
 * {@code state.getBlock()}, the same as {@code dispatchByBlock} on the NeoForge side, with the
 * key {@code minecraft:nether_portal}.
 *
 * <p>Cancellable: when a listener returns {@code true}, the portal blocks are not created (the
 * ICancellableEvent semantics of NeoForge {@code PortalSpawnEvent}). On fabric,
 * {@code MixinPortalShape} posts at the {@code PortalShape#createPortalBlocks} entry and
 * cancels there.
 *
 * <p>fabric coverage: in 26.x the whole jar calls
 * {@code PortalShape#createPortalBlocks} from one place only, {@code BaseFireBlock#onPlace}
 * (a player lighting an obsidian frame, javap-verified). {@code PortalForcer#createPortal}
 * (programmatic frame plus portal creation when an entity travels dimensions without a
 * portal) calls {@code setBlock} directly and bypasses this class, so it is outside this
 * event.
 */
@Doc("Fired when a nether portal is about to be created (BlockEvents.portalSpawn).")
@Doc("Return true to cancel the portal creation.")
@Getter
public class BlockPortalSpawnEventJS {

    @Doc("The level the portal is being created in.")
    private final LevelAccessor level;

    @Doc("Position of the portal (bottom-left corner of the shape).")
    private final BlockPos pos;

    @Doc("Block state of the nether portal blocks about to be placed.")
    private final BlockState state;

    @Doc("The portal shape (vanilla PortalShape; axis, width, height, frame info).")
    private final PortalShape portalSize;

    public BlockPortalSpawnEventJS(LevelAccessor level, BlockPos pos, BlockState state, PortalShape portalSize) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.portalSize = portalSize;
    }

    @Doc("The portal block (minecraft:nether_portal).")
    public Block getBlock() {
        return state.getBlock();
    }
}
