package dev.latvian.mods.kubejs.plugin.builtin.wrapper;

import dev.latvian.mods.kubejs.util.ID;
import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import dev.latvian.mods.kubejs.recipe.component.NativeComponentValues;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Wrapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public interface ItemWrapper {
    dev.latvian.mods.rhino.type.TypeInfo TYPE_INFO = dev.latvian.mods.rhino.type.TypeInfo.of(ItemStack.class);
    static boolean isItemStackLike(Object value) {
        value = Wrapper.unwrapped(value);
        return value == null || value instanceof ItemStack || value instanceof ItemLike
                || value instanceof ResourceLocation || value instanceof ResourceKey<?> || value instanceof CharSequence;
    }
    static ItemStack wrap(Context context, Object value) {
        value = Wrapper.unwrapped(value);
        if (value == null) return ItemStack.EMPTY;
        if (value instanceof ItemStack stack) return stack;
        if (value instanceof ItemLike item) return new ItemStack(item);
        if (value instanceof ResourceLocation || value instanceof ResourceKey<?>) value = ID.mc(value).toString();
        if (value instanceof CharSequence text) value = text.toString();
        return new ItemStackAdapter().apply(NativeComponentValues.value(value));
    }
}
