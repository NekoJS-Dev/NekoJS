package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Loader-neutral event object for {@code ServerEvents.lootTableLoad}.
 *
 * <p>NeoForge passes its native {@code LootTableLoadEvent} directly (whole-table
 * {@code getTable}/{@code setTable} and cancellable) and does not use this class; on Fabric,
 * {@code FabricServerEventBindings} adapts {@code LootTableEvents.MODIFY} from Fabric Loot API v3.
 *
 * <p><b>Platform differences:</b> On Fabric, {@code table} is a {@link LootTable.Builder} modified
 * in place (for example {@code event.table.addPool(...)}) and the event cannot be cancelled; on
 * NeoForge, scripts replace the table via {@code event.getTable()}/{@code setTable()} and cancelling
 * yields an empty table.
 */
@Doc("Fired when a loot table is loaded (ServerEvents.lootTableLoad).")
@Doc("Modify via event.table (builder on fabric, full table on NeoForge).")
@Getter
public class LootTableLoadEventJS {

    @Doc("The loot table's id, e.g. 'minecraft:entities/zombie'.")
    private final Identifier id;

    @Doc("The table (builder on fabric, modify it in place).")
    private final LootTable.Builder table;

    @Doc("The registries access for building loot entries.")
    private final HolderLookup.Provider registries;

    public LootTableLoadEventJS(Identifier id, LootTable.Builder table,
                                HolderLookup.Provider registries) {
        this.id = id;
        this.table = table;
        this.registries = registries;
    }
}
