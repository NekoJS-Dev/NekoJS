package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.bindings.event.ItemEvents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Server-side item property modification event ({@code ItemEvents.modification}),
 * fired once per server startup (about-to-start, after datapack load) and re-fired
 * by {@code /nekojs reload server}.
 *
 * <h2>JS API</h2>
 * <pre>
 * ItemEvents.modification(event {@code ->} {
 *   event.modify('minecraft:diamond', item {@code ->} {
 *     item.maxStackSize = 16;
 *     item.rarity = 'epic';
 *     item.fireResistant = true;
 *   });
 * });
 * </pre>
 *
 * <h2>Snapshot-restore model</h2>
 * Before modifying an item, its pristine {@link DataComponentMap} is snapshotted
 * into a static map keyed by item id. On every re-fire (script reload), a previously
 * modified item is first restored from its snapshot, so modifications always compose
 * on the original components and a modification removed from a script disappears on
 * reload. Snapshots live for the whole process lifetime (item default components are
 * frozen at registry time, so they never change underneath us).
 *
 * <h2>Internal (26.x)</h2>
 * Unlike 1.21.1, {@code Item} no longer holds the component map itself: it delegates
 * to {@code builtInRegistryHolder()}, and the final map is published through the
 * public {@code Holder.Reference#bindComponents(DataComponentMap)} — no reflection
 * required on this platform.
 */
public class ItemModificationEventJS {

    /** 每个物品的原始组件快照（跨脚本 reload 保留，restore 路径依据）。 */
    private static final Map<Identifier, DataComponentMap> SNAPSHOTS = new ConcurrentHashMap<>();

    private final MinecraftServer server;
    private int modifiedCount;

    public ItemModificationEventJS(MinecraftServer server) {
        this.server = server;
    }

    /** Creates and posts the event; returns how many items were modified. */
    public static int fire(MinecraftServer server) {
        if (server == null) return 0;
        ItemModificationEventJS event = new ItemModificationEventJS(server);
        ItemEvents.MODIFICATION.post(event);
        if (event.modifiedCount > 0) {
            NekoJS.LOGGER.info("NekoJS item modifications applied to {} item(s)", event.modifiedCount);
        }
        return event.modifiedCount;
    }

    /**
     * Modifies the default properties of the item with the given id. The callback
     * receives an {@link ItemModificationJS} view whose properties are applied to
     * the item after the callback returns.
     *
     * @param itemId item id, e.g. {@code 'minecraft:diamond'} (namespace optional)
     * @param modifier property callback
     */
    public void modify(String itemId, Consumer<ItemModificationJS> modifier) {
        Identifier id = parseItemId(itemId);
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            throw new IllegalArgumentException("Unknown item: " + itemId);
        }

        // restore-before-modify：已修改过的物品先回到快照，保证修改始终叠加在原始组件上
        DataComponentMap base = SNAPSHOTS.get(id);
        if (base == null) {
            base = item.components();
            SNAPSHOTS.put(id, base);
        } else {
            applyComponents(item, base);
        }

        ItemModificationJS modification = new ItemModificationJS();
        modifier.accept(modification);

        DataComponentMap.Builder builder = DataComponentMap.builder().addAll(base);
        modification.applyTo(builder, base, server);
        applyComponents(item, builder.build());
        modifiedCount++;
    }

    /** Number of items modified so far during this event. */
    public int getModifiedCount() {
        return modifiedCount;
    }

    private static Identifier parseItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("Item id must not be empty");
        }
        String id = itemId.trim();
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        Identifier location = Identifier.tryParse(id);
        if (location == null) {
            throw new IllegalArgumentException("Invalid item id: " + itemId);
        }
        return location;
    }

    // builtInRegistryHolder() 无非废弃等价 API（components() 委托它），保守保留
    @SuppressWarnings("deprecation")
    private static void applyComponents(Item item, DataComponentMap components) {
        item.builtInRegistryHolder().bindComponents(components);
    }
}
