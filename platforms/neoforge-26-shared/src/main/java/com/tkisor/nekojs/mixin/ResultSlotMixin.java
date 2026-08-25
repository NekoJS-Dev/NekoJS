package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.api.recipe.IngredientActionRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原料动作消费点（W6）：vanilla 在取出合成产物时经
 * {@code ResultSlot#getRemainingItems(CraftingInput, Level)} 计算默认余量
 * （{@code CraftingRecipe#defaultCraftingReminder}：逐物品 crafting remainder）。
 * 在其返回后按配方 id 查 {@link IngredientActionRegistry} 变换匹配槽位的余量。
 *
 * <p>配方 id 通过与服务端同一查询再次解析（该方法内部已查过一次，但拿不到 id）；
 * 合成取件是玩家 UI 动作而非热路径，二次查询可接受。
 */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {

    @Inject(
            method = "getRemainingItems(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Lnet/minecraft/core/NonNullList;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void nekojs$applyIngredientActions(CraftingInput input, Level level,
                                               CallbackInfoReturnable<NonNullList<ItemStack>> cir) {
        if (IngredientActionRegistry.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.recipeAccess()
                .getRecipeFor(RecipeType.CRAFTING, input, serverLevel)
                .ifPresent(holder -> {
                    // 26.x RecipeHolder#id() 是 ResourceKey<Recipe<?>>，注册名在其 identifier()
                    Identifier recipeId = holder.id().identifier();
                    NonNullList<ItemStack> transformed =
                            IngredientActionRegistry.transform(recipeId, input, cir.getReturnValue());
                    cir.setReturnValue(transformed);
                });
    }
}
