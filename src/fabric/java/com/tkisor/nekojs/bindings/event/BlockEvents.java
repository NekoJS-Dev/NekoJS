package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.block.BlockBrokenEventJS;
import com.tkisor.nekojs.wrapper.event.server.BlockModificationEventJS;
import net.minecraft.world.level.block.Block;

import java.util.function.Function;

/** Declares Fabric block event sources extended by the Fabric callback bindings. */
public final class BlockEvents {
    private BlockEvents() {}

    public static final EventGroup GROUP = EventGroup.of("BlockEvents");

    public static final EventBusJS<BlockBrokenEventJS, Block> BROKEN =
            GROUP.add("broken", ScriptType.SERVER, EventBusJS.of(
                    BlockBrokenEventJS.class, true, dispatchByBlock(BlockBrokenEventJS::getBlock)));

    /** Posted by the modification domain owner, not a Fabric callback. */
    public static final EventBusJS<BlockModificationEventJS, Void> MODIFICATION =
            GROUP.server("modification", BlockModificationEventJS.class);

    static <T> DispatchKey<T, Block> dispatchByBlock(Function<T, Block> toKey) {
        return DispatchKey.of(Block.class, toKey);
    }
}
