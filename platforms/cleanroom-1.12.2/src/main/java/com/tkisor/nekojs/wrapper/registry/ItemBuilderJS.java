package com.tkisor.nekojs.wrapper.registry;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.function.Consumer;

/**
 * 1.12.2 ItemBuilderJS - builds Item instances from script definitions.
 */
public class ItemBuilderJS {
    private final String registryName;
    private String translationKey;
    private CreativeTabs creativeTab;
    private int maxStackSize = 64;
    private int maxDamage = 0;
    private boolean containerItem = false;
    private FoodBuilderJS foodBuilder = null;

    public ItemBuilderJS(String registryName) {
        this.registryName = registryName;
    }

    public ItemBuilderJS translationKey(String key) {
        this.translationKey = key;
        return this;
    }

    public ItemBuilderJS creativeTab(CreativeTabs tab) {
        this.creativeTab = tab;
        return this;
    }

    public ItemBuilderJS maxStackSize(int size) {
        this.maxStackSize = Math.max(1, Math.min(64, size));
        return this;
    }

    public ItemBuilderJS maxDamage(int damage) {
        this.maxDamage = Math.max(0, damage);
        return this;
    }

    /**
     * 启用食物模式：用 {@link FoodBuilderJS} 构建 {@link net.minecraft.item.ItemFood}（继承自 Item，
     * 可向上转型）。foodBuilder 非空时 build() 将跳过 setMaxDamage（食物不磨损）。
     */
    public ItemBuilderJS food(Consumer<FoodBuilderJS> config) {
        this.foodBuilder = new FoodBuilderJS();
        config.accept(this.foodBuilder);
        return this;
    }

    /**
     * Build and create the item.
     * The item should be registered via RegistryEvent.Register&lt;Item&gt;.
     */
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
    public Item buildAndRegister() {
        Item item = build();
        if (item.getRegistryName() != null) {
            ForgeRegistries.ITEMS.register(item);
        }
        return item;
    }

    public String getRegistryName() {
        return registryName;
    }
}
