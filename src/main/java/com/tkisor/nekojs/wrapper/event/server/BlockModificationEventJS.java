// 26.x only: BlockBehaviour.Properties modification uses 26.x APIs; 1.21.1 has no BlockEvents.modification event.
//? if >=26 {
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.annotation.ScriptInternalConstructor;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import com.tkisor.nekojs.core.modification.ModificationDeclaration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * Event object for {@code BlockEvents.modification} on the server.
 *
 * <h2>Collection behavior (same as {@link ItemModificationEventJS})</h2>
 * {@code ModificationDomainOwner} dispatches this event at two collection points: to pending
 * candidate listeners during the {@code DOMAIN_PLAN} phase of a transactional reload, and to
 * active listeners for the initial generation. {@link #modify} only collects and normalizes
 * declarations without touching live {@link Block} or {@code BlockState} instances; restoring
 * baselines and applying the full plan happens at commit time in {@code ModificationDomainOwner}.
 *
 * <h2>JS API</h2>
 * <pre>
 * BlockEvents.modification(event {@code ->} {
 *   event.modify('minecraft:stone', block {@code ->} {
 *     block.hardness = 2;              // uses the same setter as block.setHardness(2)
 *     block.resistance = 6;
 *     block.lightLevel = 15;
 *   });
 * });
 * </pre>
 *
 * <h2>Client visibility</h2>
 * The adapter updates server-owned block properties and state copies. No client property
 * synchronization is implemented here. Relogging or chunk resynchronization has not been
 * verified to apply these changes in an independent client process; integrated-server
 * shared state is not evidence of remote-client visibility.
 */
public class BlockModificationEventJS {

    private final ModificationCandidatePlan plan;
    private int declaredCount;

    /** @param plan target candidate plan created by {@code ModificationDomainOwner} */
    @ScriptInternalConstructor
    public BlockModificationEventJS(ModificationCandidatePlan plan) {
        this.plan = plan;
    }

    /**
     * Records a modification declaration for the block with the given id. The callback
     * receives a {@link BlockModificationJS} view; any exception thrown by the callback
     * propagates out so a collection failure aborts the whole batch.
     *
     * <p>The parameter uses {@code Consumer<BlockModificationJS>} so Java callers can pass a
     * lambda directly while script functions implement the interface through sandbox HostAccess
     * ({@code allowAllImplementations}). Because the view implements
     * {@link graal.graalvm.polyglot.proxy.ProxyObject}, {@code block.hardness = 2} and
     * {@code block.setHardness(2)} invoke the same setter, validation, normalization, and plan
     * recording path (see {@link ModificationViewSurface}).
     *
     * @param blockId block id, e.g. {@code 'minecraft:stone'} (namespace optional)
     * @param modifier property callback (script function or Java {@code Consumer})
     */
    @Doc("Records a runtime property modification declaration for one block.")
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
        BlockModificationJS view = new BlockModificationJS(block);
        modifier.accept(view);
        plan.add(new ModificationDeclaration("block", id.toString(), view.normalizedProperties(), null));
        declaredCount++;
    }

    /** Number of declarations recorded so far during this event. */
    @Doc("Number of declarations recorded so far during this event.")
    @Return("how many declarations this event has collected")
    public int getModifiedCount() {
        return declaredCount;
    }

    static Identifier parseBlockId(String blockId) {
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
//?}
