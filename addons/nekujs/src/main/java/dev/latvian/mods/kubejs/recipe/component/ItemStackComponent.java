package dev.latvian.mods.kubejs.recipe.component;

import com.mojang.serialization.Codec;
import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.kubejs.recipe.match.ItemMatch;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public record ItemStackComponent(RecipeComponentType<?> type, Codec<ItemStack> codec, boolean allowEmpty, Ingredient filter) implements RecipeComponent<ItemStack> {
    private static final ItemStackAdapter CONVERTER = new ItemStackAdapter();
    public static final RecipeComponentType<ItemStack> ITEM_STACK = RecipeComponentType.unit(ResourceLocation.fromNamespaceAndPath("kubejs", "item_stack"), type -> new ItemStackComponent(type, false, Ingredient.EMPTY));
    public static final RecipeComponentType<ItemStack> OPTIONAL_ITEM_STACK = RecipeComponentType.unit(ResourceLocation.fromNamespaceAndPath("kubejs", "optional_item_stack"), type -> new ItemStackComponent(type, true, Ingredient.EMPTY));
    public ItemStackComponent(RecipeComponentType<?> type, boolean allowEmpty, Ingredient filter) {
        this(type, allowEmpty ? ItemStack.OPTIONAL_CODEC : ItemStack.STRICT_CODEC, allowEmpty, filter);
    }
    @Override public TypeInfo typeInfo() { return TypeInfo.of(ItemStack.class); }
    @Override public ItemStack wrap(RecipeScriptContext cx, Object from) { return CONVERTER.apply(NativeComponentValues.value(from)); }
    @Override public boolean hasPriority(RecipeMatchContext cx, Object from) { return CONVERTER.test(NativeComponentValues.value(from)); }
    @Override public boolean matches(RecipeMatchContext cx, ItemStack value, ReplacementMatchInfo match) {
        return match.match() instanceof ItemMatch item && !value.isEmpty() && item.matches(cx, value, match.exact());
    }
    @Override public boolean isEmpty(ItemStack value) { return value.isEmpty(); }
    @Override public void buildUniqueId(UniqueIdBuilder builder, ItemStack value) {
        if (!value.isEmpty()) builder.append(BuiltInRegistries.ITEM.getKey(value.getItem()));
    }
    @Override public String toString() { return type.toString(); }
}
