//? if >=26 {
package com.tkisor.nekojs.api.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of ingredient actions: {@code damageIngredient / keepIngredient / replaceIngredient}
 * register an action on {@link RecipeJsonBuilder}, and the action applies to the crafting
 * remainder of that recipe.
 *
 * <p>Actions are pure script state and are <strong>not written into the recipe JSON</strong>
 * (vanilla deserialization rejects unknown fields). They live only in an in-memory table that maps
 * recipe id to a list of actions. Scripts rerun on every datapack load and server start
 * ({@code nekojs$applyScripts} in RecipeManagerMixin), and the table is cleared before each rerun,
 * so nothing needs persisting: delete an action from a script and it is gone after the next
 * /reload.
 *
 * <p>The consumer is {@code ResultSlotMixin}. After vanilla computes the default remainders, it
 * looks up the recipe id and rewrites the remainders of the slots that match a filter. Slots that
 * match nothing keep the vanilla result, such as an empty bucket or a water bottle.
 */
public final class IngredientActionRegistry {

    /** The kind of an action. */
    public enum Kind {
        /** Takes amount durability damage. Unbreakable items, and items that run out, disappear. */
        DAMAGE,
        /** Keeps the ingredient as it is, using a copy that loses no durability. */
        KEEP,
        /** Replaces the ingredient with the given item. */
        REPLACE
    }

    /**
     * One action: {@code filter} matches ingredient stacks in the crafting grid
     * ({@link Ingredient#test(ItemStack)}), {@code amount} is used by DAMAGE only, and
     * {@code replacement} is used by REPLACE only.
     */
    public record Action(Kind kind, Ingredient filter, int amount, ItemStack replacement) {}

    private static volatile Map<Identifier, List<Action>> actions = new ConcurrentHashMap<>();
    private static final ThreadLocal<Map<Identifier, List<Action>>> DRAFT = new ThreadLocal<>();

    private static Map<Identifier, List<Action>> writableActions() {
        var draft = DRAFT.get();
        return draft == null ? actions : draft;
    }

    public static <T> T withDraft(Map<Identifier, List<Action>> draft, java.util.function.Supplier<T> operation) {
        var previous = DRAFT.get();
        DRAFT.set(draft);
        try {
            return operation.get();
        } finally {
            if (previous == null) {
                DRAFT.remove();
            } else {
                DRAFT.set(previous);
            }
        }
    }

    public static void publishDraft(Map<Identifier, List<Action>> draft) {
        actions = new ConcurrentHashMap<>(draft);
    }

    private IngredientActionRegistry() {}

    /** Clears the whole table before scripts rerun. RecipeManagerMixin calls it before it posts the RECIPES event. */
    public static void clear() {
        writableActions().clear();
    }

    public static boolean isEmpty() {
        return actions.isEmpty();
    }

    public static void record(Identifier recipeId, Action action) {
        if (recipeId == null || action == null) return;
        writableActions().computeIfAbsent(recipeId, k -> new ArrayList<>()).add(action);
    }

    /** Re-keys any actions recorded under {@code oldId} to {@code newId}. */
    public static void rekey(Identifier oldId, Identifier newId) {
        if (oldId == null || newId == null || oldId.equals(newId)) return;
        var current = writableActions();
        List<Action> moved = current.remove(oldId);
        if (moved != null && !moved.isEmpty()) {
            current.computeIfAbsent(newId, k -> new ArrayList<>()).addAll(moved);
        }
    }

    /**
     * Rewrites the default remainder table for one recipe id. For each grid slot that matches an
     * action filter, it replaces the remainder of that slot; other slots stay untouched. Only the
     * first matching action applies to a slot, in script declaration order.
     *
     * @return the passed-in {@code remainders}, modified in place; when nothing matches, the content
     *         equals the vanilla result
     */
    public static NonNullList<ItemStack> transform(Identifier recipeId, CraftingInput input, NonNullList<ItemStack> remainders) {
        List<Action> actions = recipeId == null ? null : IngredientActionRegistry.actions.get(recipeId);
        if (actions == null || actions.isEmpty() || input == null) {
            return remainders;
        }
        for (int slot = 0; slot < remainders.size() && slot < input.size(); slot++) {
            ItemStack gridStack = input.getItem(slot);
            if (gridStack.isEmpty()) continue;
            for (Action action : actions) {
                if (!action.filter().test(gridStack)) continue;
                remainders.set(slot, apply(action, gridStack));
                break;
            }
        }
        return remainders;
    }

    private static ItemStack apply(Action action, ItemStack gridStack) {
        return switch (action.kind()) {
            case KEEP -> gridStack.copyWithCount(1);
            case REPLACE -> action.replacement().copy();
            case DAMAGE -> {
                ItemStack copy = gridStack.copyWithCount(1);
                if (!copy.isDamageableItem()) {
                    // Items that cannot take damage count as kept: damaging an item that has no durability has no defined result.
                    yield copy;
                }
                int newDamage = copy.getDamageValue() + Math.max(1, action.amount());
                if (newDamage >= copy.getMaxDamage()) {
                    yield ItemStack.EMPTY;
                }
                copy.setDamageValue(newDamage);
                yield copy;
            }
        };
    }
}
//?}
