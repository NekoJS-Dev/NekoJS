package dev.latvian.mods.kubejs.item;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import dev.latvian.mods.rhino.type.TypeInfo;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Legacy item selection backed by native stack matching and the current item registry. */
@FunctionalInterface
@RemapByPrefix("kjs$")
public interface ItemPredicate extends Predicate<ItemStack> {
    TypeInfo TYPE_INFO = TypeInfo.of(ItemPredicate.class);
    ItemPredicate NONE = stack -> false;
    ItemPredicate ALL = stack -> true;

    @Override
    boolean test(ItemStack stack);

    default Stream<Item> kjs$getItemStream() {
        return BuiltInRegistries.ITEM.stream().filter(item -> test(item.getDefaultInstance()));
    }
}
