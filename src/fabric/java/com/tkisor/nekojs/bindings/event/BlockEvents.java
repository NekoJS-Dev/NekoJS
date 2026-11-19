package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StableBlockEvent;
import com.tkisor.nekojs.fabric.event.FabricBlockEventBindingsV2;
import com.tkisor.nekojs.fabric.event.FabricStableEventProjections;
import com.tkisor.nekojs.wrapper.event.block.BlockBrokenEventJS;
import com.tkisor.nekojs.wrapper.event.server.BlockModificationEventJS;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import java.util.function.Function;

/**
 * Block event group for server scripts (Fabric).
 *
 * <p>Provides block break notifications and block property modification hooks.
 */
@Doc("Block event group for server scripts: block breaking and block property modification.")
public final class BlockEvents {
    private BlockEvents() {}

    /** Event group published as {@code BlockEvents}. */
    public static final EventGroup GROUP = EventGroup.of("BlockEvents");

    /** Fired on the server when a player breaks a block; dispatched by block. */
    @Doc("Fired on the server when a player breaks a block; dispatched by block.")
    @Doc("Cancellable: return true to cancel the block break. Only visible in server_scripts.")
    public static final EventBusJS<BlockBrokenEventJS, Block> BROKEN =
            GROUP.add("broken", ScriptType.SERVER, EventBusJS.of(
                    BlockBrokenEventJS.class, true, dispatchByBlock(BlockBrokenEventJS::getBlock)), api -> {
                api.versioned();
                api.stable()
                        .when(event -> event.getLevel() instanceof ServerLevel level && level.getServer().isSameThread())
                        .view(StableBlockEvent.class, FabricStableEventProjections::blockBroken)
                        .selector(String.class, FabricStableEventProjections::resolveBlockSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired during server startup and reload to modify block properties. */
    @Doc("Fired during server startup and reload to modify registered block properties.")
    @Doc("Only visible in server_scripts.")
    public static final EventBusJS<BlockModificationEventJS, Void> MODIFICATION =
            GROUP.server("modification", BlockModificationEventJS.class, api -> api.versioned());

    public static boolean hasStablePlacementListeners() {
        return FabricBlockEventBindingsV2.hasStablePlacementListeners();
    }

    public static boolean postStablePlacement(StableBlockPlacement.Attempt attempt) {
        return FabricBlockEventBindingsV2.postStablePlacement(attempt);
    }

    static <T> DispatchKey<T, Block> dispatchByBlock(Function<T, Block> toKey) {
        return DispatchKey.of(Block.class, toKey);
    }
}
