package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * 战利品表装载事件（{@code ServerEvents.lootTableLoad}）的**加载器中立**载荷。
 *
 * <p>NeoForge 侧直传原生 {@code LootTableLoadEvent}（getTable/setTable 整表语义，
 * 可取消），不经本类；fabric 侧由 {@code FabricServerEventBindings} 从 fabric
 * loot-api-v3 的 {@code LootTableEvents.MODIFY} 转换。
 *
 * <p><b>语义差异（记录）</b>：fabric 侧 {@code table} 是
 * {@link LootTable.Builder}——脚本用 {@code event.table.addPool(...)} 等 builder
 * 方式修改；NF 侧是 {@code event.getTable()/setTable()} 整表替换。两者均可改表，
 * 但跨平台脚本对表体的操作写法不同。fabric 侧不可取消（NF 可取消——取消后表为空）。
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
