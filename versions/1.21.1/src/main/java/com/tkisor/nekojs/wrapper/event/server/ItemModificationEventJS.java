// 1.21.1 implementation, paired with the 26.x file of the same name under the version tree's src/.
// The content is that file as evaluated on this node; the real differences here are
// Identifier -> ResourceLocation and bindComponents -> the reflective components field.
// Keep this file in sync when the 26.x side changes behavior.
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.bindings.event.ItemEvents;
import com.tkisor.nekojs.api.annotation.ScriptInternalConstructor;
import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import com.tkisor.nekojs.core.modification.ModificationDeclaration;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import java.lang.reflect.Field;
import java.util.function.Consumer;

/**
 * Server-side item property modification event payload ({@code ItemEvents.modification}).
 * The file of the same name on the 26.x side is the guarded form in the version tree's src/;
 * the collection semantics are documented in its javadoc.
 *
 * <h2>Internal (1.21.1)</h2>
 * {@code Item} still owns its component map itself (26.x delegates to builtInRegistryHolder),
 * so publishing writes the private final {@code Item#components} field through reflection.
 */
public class ItemModificationEventJS {

    private final ModificationCandidatePlan plan;
    private int declaredCount;

    private static final Field COMPONENTS_FIELD = componentsField();

    /** @param plan the candidate plan to collect into (created by the 1.21.1 {@code ModificationDomainOwner}) */
    @ScriptInternalConstructor
    public ItemModificationEventJS(ModificationCandidatePlan plan) {
        this.plan = plan;
    }

    /**
     * Records a modification declaration for the item with the given id. Collection works the same
     * way as on 26.x: callback exceptions propagate, declarations go into an inert plan, and
     * Minecraft is only changed in the Adapter.
     *
     * <p>The callback keeps the functional interface signature
     * ({@code Consumer<ItemModificationJS>}): Java callers pass a lambda directly, and a script
     * function from Graal implements the interface through the sandbox's HostAccess
     * ({@code allowAllImplementations}).
     *
     * @param itemId item id, e.g. {@code 'minecraft:diamond'} (namespace optional)
     * @param modifier property callback (a script function or a Java {@code Consumer})
     */
    public void modify(String itemId, Consumer<ItemModificationJS> modifier) {
        ResourceLocation id = parseItemId(itemId);
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            throw new IllegalArgumentException("Unknown item: " + itemId);
        }
        if (modifier == null) {
            throw new IllegalArgumentException("Modifier must not be null");
        }
        ItemModificationJS view = new ItemModificationJS();
        modifier.accept(view);
        plan.add(new ModificationDeclaration("item", id.toString(), view.normalizedProperties(), null));
        declaredCount++;
    }

    /** Number of declarations recorded so far during this event. */
    public int getModifiedCount() {
        return declaredCount;
    }

    static ResourceLocation parseItemId(String itemId) {
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

    static void applyComponents(Item item, DataComponentMap components) {
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
