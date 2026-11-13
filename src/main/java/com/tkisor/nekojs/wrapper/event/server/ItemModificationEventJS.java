// 26.x implementation (Holder.Reference#bindComponents is a 26.x API). Keep behavior in sync
// with the matching file under versions/1.21.1/src.
//? if >=26 {
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.bindings.event.ItemEvents;
import com.tkisor.nekojs.api.annotation.ScriptInternalConstructor;
import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import com.tkisor.nekojs.core.modification.ModificationDeclaration;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import java.util.function.Consumer;

/**
 * Event object for {@code ItemEvents.modification} on the server.
 *
 * <h2>Collection behavior</h2>
 * {@code ModificationDomainOwner} dispatches this event at two collection points:
 * <ul>
 *   <li><b>Transactional reload ({@code DOMAIN_PLAN}):</b> dispatched to pending candidate
 *       listeners ({@code CandidateDomainCollector.Handle.dispatch}) into an inert candidate plan,
 *       preflighted during {@code STATE_PLAN}, and applied together with {@code global}/shared
 *       top-level state at commit time.</li>
 *   <li><b>Initial generation (server about-to-start):</b> dispatched to active listeners, then
 *       preflighted and applied by {@code ModificationDomainOwner} (falling back to vanilla
 *       baselines if it fails).</li>
 * </ul>
 * {@link #modify} only collects and normalizes declarations (a later declaration for the same
 * item replaces earlier properties from baseline) without mutating live {@link Item} instances or
 * default components; {@code ModificationDomainOwner} applies the changes.
 *
 * <h2>JS API</h2>
 * <pre>
 * ItemEvents.modification(event {@code ->} {
 *   event.modify('minecraft:diamond', item {@code ->} {
 *     item.maxStackSize = 16;   // uses the same setter as setMaxStackSize (ModificationViewSurface)
 *   });
 * });
 * </pre>
 */
public class ItemModificationEventJS {

    private final ModificationCandidatePlan plan;
    private int declaredCount;

    /** @param plan target candidate plan created by {@code ModificationDomainOwner} */
    @ScriptInternalConstructor
    public ItemModificationEventJS(ModificationCandidatePlan plan) {
        this.plan = plan;
    }

    /**
     * Records a modification declaration for the item with the given id. The callback
     * receives an {@link ItemModificationJS} view; any exception thrown by the callback
     * propagates out so a collection failure aborts the whole batch.
     *
     * <p>The parameter uses {@code Consumer<ItemModificationJS>} so Java callers can pass a
     * lambda directly while script functions implement the interface through sandbox HostAccess
     * ({@code allowAllImplementations}). Because the view implements
     * {@link graal.graalvm.polyglot.proxy.ProxyObject}, {@code item.maxStackSize = 16} and
     * {@code item.setMaxStackSize(16)} invoke the same setter, validation, normalization, and plan
     * recording path (see {@link ModificationViewSurface}).
     *
     * @param itemId item id, e.g. {@code 'minecraft:diamond'} (namespace optional)
     * @param modifier property callback (script function or Java {@code Consumer})
     */
    public void modify(String itemId, Consumer<ItemModificationJS> modifier) {
        Identifier id = parseItemId(itemId);
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

    static Identifier parseItemId(String itemId) {
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

    // builtInRegistryHolder() has no non-deprecated equivalent for rebinding default components.
    @SuppressWarnings("deprecation")
    static void applyComponents(Item item, DataComponentMap components) {
        item.builtInRegistryHolder().bindComponents(components);
    }
}
//?}
