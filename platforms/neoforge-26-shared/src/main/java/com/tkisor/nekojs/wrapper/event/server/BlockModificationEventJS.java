package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Server-side block property modification event ({@code BlockEvents.modification}),
 * fired once per server startup (about-to-start, after datapack load) and re-fired
 * by {@code /nekojs reload server}.
 *
 * <h2>JS API</h2>
 * <pre>
 * BlockEvents.modification(event {@code ->} {
 *   event.modify('minecraft:stone', block {@code ->} {
 *     block.hardness = 2;
 *     block.resistance = 6;
 *     block.lightLevel = 15;
 *     block.requiresTool = false;
 *     block.friction = 0.8;
 *   });
 * });
 * </pre>
 *
 * <h2>Snapshot-restore model</h2>
 * Before modifying a block, its pristine property values are snapshotted into a
 * static map keyed by block id (the original light-emission {@code ToIntFunction}
 * is kept, so per-state light functions restore exactly). Every fire first
 * restores <b>all</b> previously modified blocks from their snapshots, then posts
 * the event so scripts re-apply their modifications on top of the original values
 * — a modification removed from a script disappears on the next reload.
 * Snapshots live for the whole process lifetime; block singletons and their
 * {@code BlockBehaviour.Properties} never change underneath us.
 *
 * <h2>Visibility note</h2>
 * Writes update the {@code Properties} fields plus the copies held by the
 * {@link Block} and every {@link BlockState} (see {@link BlockModificationJS}),
 * so all existing states pick up the change immediately on the server. Clients
 * are NOT resynced: players need to relog (or receive a chunk resync) to observe
 * visual-only effects such as light emission.
 */
public class BlockModificationEventJS {

    /** 每个方块的原始属性快照（跨脚本 reload 保留，restore 路径依据）。 */
    private static final Map<Identifier, BlockModificationJS.PropertySnapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private int modifiedCount;

    /** Creates and posts the event; returns how many blocks were modified. */
    public static int fire() {
        // restore-all-first：先恢复上一轮全部修改，再 post；被删掉的 modify 在本次 reload 即消失
        restoreAll();
        BlockModificationEventJS event = new BlockModificationEventJS();
        BlockEvents.MODIFICATION.post(event);
        if (event.modifiedCount > 0) {
            NekoJS.LOGGER.info("NekoJS block modifications applied to {} block(s)", event.modifiedCount);
        }
        return event.modifiedCount;
    }

    private static void restoreAll() {
        for (Map.Entry<Identifier, BlockModificationJS.PropertySnapshot> entry : SNAPSHOTS.entrySet()) {
            BuiltInRegistries.BLOCK.getOptional(entry.getKey())
                    .ifPresent(block -> entry.getValue().applyTo(block));
        }
    }

    /**
     * Modifies the runtime properties of the block with the given id. The callback
     * receives a {@link BlockModificationJS} view; properties are applied to the
     * block (and all its states) after the callback returns.
     *
     * @param blockId block id, e.g. {@code 'minecraft:stone'} (namespace optional)
     * @param modifier property callback
     */
    @Doc("Modifies the runtime properties of one block.")
    @Param(name = "blockId", value = "block id like 'minecraft:stone' (the 'minecraft:' prefix is optional)")
    @Param(name = "modifier", value = "callback receiving a block property view; assign block.hardness / block.resistance / block.lightLevel / block.requiresTool / block.friction / block.jumpFactor")
    public void modify(String blockId, Consumer<BlockModificationJS> modifier) {
        Identifier id = parseBlockId(blockId);
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) {
            throw new IllegalArgumentException("Unknown block: " + blockId);
        }
        if (modifier == null) {
            throw new IllegalArgumentException("Modifier must not be null");
        }

        // restore-before-modify：已修改过的方块先回到快照，保证修改始终叠加在原始属性上
        BlockModificationJS.PropertySnapshot base = SNAPSHOTS.get(id);
        if (base == null) {
            SNAPSHOTS.put(id, BlockModificationJS.PropertySnapshot.capture(block));
        } else {
            base.applyTo(block);
        }

        BlockModificationJS modification = new BlockModificationJS(block);
        modifier.accept(modification);
        modification.applyTo(block);
        modifiedCount++;
    }

    /** Number of blocks modified so far during this event. */
    @Doc("Number of blocks modified so far during this event.")
    @Return("how many blocks this event has modified")
    public int getModifiedCount() {
        return modifiedCount;
    }

    private static Identifier parseBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            throw new IllegalArgumentException("Block id must not be empty");
        }
        String id = blockId.trim();
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        Identifier location = Identifier.tryParse(id);
        if (location == null) {
            throw new IllegalArgumentException("Invalid block id: " + blockId);
        }
        return location;
    }
}
