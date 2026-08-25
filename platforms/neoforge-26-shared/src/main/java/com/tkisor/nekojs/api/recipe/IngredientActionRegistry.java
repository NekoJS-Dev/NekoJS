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
 * 原料动作注册表（W6）：{@code damageIngredient / keepIngredient / replaceIngredient}
 * 在 {@link RecipeJsonBuilder} 上登记，作用于对应配方的合成余量（crafting remainder）。
 *
 * <p>动作是纯脚本状态：<strong>不写进配方 JSON</strong>（vanilla 反序列化会拒绝未知字段），
 * 只存内存表，配方 id → 动作列表。脚本在每次数据包加载 / 服务器启动时重跑
 * （RecipeManagerMixin 的 {@code nekojs$applyScripts}），重跑前整表清空——因此无需持久化，
 * 脚本里删掉一行动作，下次 /reload 后动作即消失。
 *
 * <p>消费点：{@code ResultSlotMixin} 在 vanilla 计算完默认余量后，按配方 id 查表变换
 * （匹配过滤器的槽位替换其余量；不匹配的槽位保留 vanilla 语义，如空桶/水瓶）。
 */
public final class IngredientActionRegistry {

    /** 动作种类。 */
    public enum Kind {
        /** 耐久 +amount（不可破坏或耗尽的物品直接消失）。 */
        DAMAGE,
        /** 原样保留（复制一份不损耗的原料）。 */
        KEEP,
        /** 替换为指定物品。 */
        REPLACE
    }

    /**
     * 单条动作：{@code filter} 匹配网格中的原料栈（{@link Ingredient#test(ItemStack)}），
     * {@code amount} 仅 DAMAGE 使用，{@code replacement} 仅 REPLACE 使用。
     */
    public record Action(Kind kind, Ingredient filter, int amount, ItemStack replacement) {}

    private static final Map<Identifier, List<Action>> ACTIONS = new ConcurrentHashMap<>();

    private IngredientActionRegistry() {}

    /** 脚本重跑前整表清空（RecipeManagerMixin 在 post RECIPES 事件前调用）。 */
    public static void clear() {
        ACTIONS.clear();
    }

    public static boolean isEmpty() {
        return ACTIONS.isEmpty();
    }

    public static void record(Identifier recipeId, Action action) {
        if (recipeId == null || action == null) return;
        ACTIONS.computeIfAbsent(recipeId, k -> new ArrayList<>()).add(action);
    }

    /**
     * 按配方 id 变换默认余量表：对每个匹配某条动作过滤器的网格槽位，替换其余量；
     * 其余槽位不动。同一槽位只应用第一条匹配的动作（脚本声明顺序）。
     *
     * @return 传入的 {@code remainders}（原地修改；无任何匹配时内容与 vanilla 结果一致）
     */
    public static NonNullList<ItemStack> transform(Identifier recipeId, CraftingInput input, NonNullList<ItemStack> remainders) {
        List<Action> actions = recipeId == null ? null : ACTIONS.get(recipeId);
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
                    // 不可损耗物品按保留处理：damage 一个无耐久的物品没有可定义的结果
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
