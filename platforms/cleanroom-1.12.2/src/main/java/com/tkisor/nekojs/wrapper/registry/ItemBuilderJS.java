package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.function.Consumer;

/**
 * 1.12.2 ItemBuilderJS - builds Item instances from script definitions.
 */
@Doc("Builder for registering a new item; obtain it from RegistryEvents.item.create(id).")
public class ItemBuilderJS {
    private final String registryName;
    private String translationKey;
    private CreativeTabs creativeTab;
    private int maxStackSize = 64;
    private int maxDamage = 0;
    private FoodBuilderJS foodBuilder = null;

    /** Creates a builder for the given registry name. */
    public ItemBuilderJS(String registryName) {
        this.registryName = registryName;
    }

    /** Sets an explicit translation key. */
    @Doc("Sets an explicit translation key; defaults to '<namespace>.<path>'.")
    @Param(name = "key", value = "translation key like 'item.mymod.my_item'")
    @Return("this builder, for chaining")
    public ItemBuilderJS translationKey(String key) {
        this.translationKey = key;
        return this;
    }

    /** Sets the creative tab. */
    @Doc("Puts the item into a creative tab.")
    @Param(name = "tab", value = "a CreativeTabs instance, e.g. from CreativeTabRegistryEventJS or CreativeTabs.COMBAT")
    @Return("this builder, for chaining")
    public ItemBuilderJS creativeTab(CreativeTabs tab) {
        this.creativeTab = tab;
        return this;
    }

    /** Sets the max stack size. */
    @Doc("Sets the maximum stack size.")
    @Param(name = "size", value = "stack size from 1 to 64; clamped")
    @Return("this builder, for chaining")
    public ItemBuilderJS maxStackSize(int size) {
        this.maxStackSize = Math.max(1, Math.min(64, size));
        return this;
    }

    /** Sets the max damage (durability). */
    @Doc("Sets the item's max damage (durability); ignored for food items.")
    @Param(name = "damage", value = "max damage; at least 0")
    @Return("this builder, for chaining")
    public ItemBuilderJS maxDamage(int damage) {
        this.maxDamage = Math.max(0, damage);
        return this;
    }

    /**
     * 启用食物模式：用 {@link FoodBuilderJS} 构建 {@link net.minecraft.item.ItemFood}（继承自 Item，
     * 可向上转型）。foodBuilder 非空时 build() 将跳过 setMaxDamage（食物不磨损）。
     */
    @Doc("Makes the item a food item configured via a FoodBuilderJS callback.")
    @Param(name = "config", value = "callback configuring the food properties")
    @Return("this builder, for chaining")
    public ItemBuilderJS food(Consumer<FoodBuilderJS> config) {
        this.foodBuilder = new FoodBuilderJS();
        config.accept(this.foodBuilder);
        return this;
    }

    /**
     * Build and create the item.
     * The item should be registered via RegistryEvent.Register&lt;Item&gt;.
     */
    @Doc("Builds the item instance; does not register it.")
    @Doc("Registration happens automatically when the registry event completes.")
    @Return("the configured item")
    @SuppressWarnings("deprecation")
    public Item build() {
        Item item = foodBuilder != null ? foodBuilder.build() : new Item();
        ResourceLocation rl = new ResourceLocation(registryName);
        item.setRegistryName(rl);
        item.setTranslationKey(translationKey != null ? translationKey : rl.getNamespace() + "." + rl.getPath());
        if (creativeTab != null) {
            item.setCreativeTab(creativeTab);
        }
        item.setMaxStackSize(maxStackSize);
        // 食物不磨损：foodBuilder 模式下跳过 setMaxDamage（ItemFood 不支持 durability 语义）
        if (foodBuilder == null && maxDamage > 0) {
            item.setMaxDamage(maxDamage);
        }
        return item;
    }

    /**
     * Build and register the item to ForgeRegistries.
     */
    @Doc("Builds the item and registers it into the Forge item registry immediately.")
    @Return("the registered item")
    public Item buildAndRegister() {
        Item item = build();
        if (item.getRegistryName() != null) {
            ForgeRegistries.ITEMS.register(item);
        }
        return item;
    }

    /** The registry name given at creation. */
    @Doc("Gets the registry name of the item being built.")
    @Return("the registry name string")
    public String getRegistryName() {
        return registryName;
    }
}
