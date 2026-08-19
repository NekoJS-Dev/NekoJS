package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.bindings.event.ItemEvents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

import java.lang.reflect.Field;
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
 * <h2>Internal (1.21.1)</h2>
 * {@code Item} owns its component map in the private {@code components} field, so the
 * final map is written back reflectively — the public {@code modifyDefaultComponentsFrom}
 * only works during the registration window, far before this event fires.
 */
public class ItemModificationEventJS {

    /** 每个物品的原始组件快照（跨脚本 reload 保留，restore 路径依据）。 */
    private static final Map<ResourceLocation, DataComponentMap> SNAPSHOTS = new ConcurrentHashMap<>();

    /** 1.21.1 Item#components 为私有字段，反射写入（无 AT，保持影响面最小）。 */
    private static final Field COMPONENTS_FIELD = componentsField();

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
        ResourceLocation id = parseItemId(itemId);
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
        modification.applyTo(builder, base);
        applyComponents(item, builder.build());
        modifiedCount++;
    }

    /** Number of items modified so far during this event. */
    public int getModifiedCount() {
        return modifiedCount;
    }

    private static ResourceLocation parseItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("Item id must not be empty");
        }
        String id = itemId.trim();
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            throw new IllegalArgumentException("Invalid item id: " + itemId);
        }
        return location;
    }

    private static void applyComponents(Item item, DataComponentMap components) {
        try {
            COMPONENTS_FIELD.set(item, components);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to write item components of " + BuiltInRegistries.ITEM.getKey(item), e);
        }
    }

    private static Field componentsField() {
        try {
            Field field = Item.class.getDeclaredField("components");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
