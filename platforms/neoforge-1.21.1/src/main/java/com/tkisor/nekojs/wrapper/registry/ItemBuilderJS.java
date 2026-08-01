package com.tkisor.nekojs.wrapper.registry;

import lombok.Getter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.function.Consumer;

public class ItemBuilderJS {
    @Getter
    private final ResourceLocation location;

    private int maxStackSize = 64;
    private int maxDamage = 0;
    private boolean fireResistant = false;
    private Rarity rarity = Rarity.COMMON;
    private boolean glowing = false;
    private int burnTime = 0;

    private FoodBuilderJS foodBuilder = null;

    /** 可选：创造标签页 id（如 {@code 'minecraft:building_blocks'} 或自定义 tab id）。null=不分配。 */
    private ResourceLocation groupTab = null;

    public ItemBuilderJS(ResourceLocation location) {
        this.location = location;
    }

    public ItemBuilderJS maxStackSize(int size) { this.maxStackSize = size; return this; }
    public ItemBuilderJS maxDamage(int damage) { this.maxDamage = damage; return this; }
    public ItemBuilderJS fireResistant() { this.fireResistant = true; return this; }

    /**
     * 分配到创造标签页。{@code tabId} 为标签页 id（如 {@code 'minecraft:building_blocks'}
     * 或 {@link com.tkisor.nekojs.wrapper.event.registry.CreativeTabRegistryEventJS} 注册的
     * 自定义 tab id）。在 {@code BuildCreativeModeTabContentsEvent} 时追加。
     */
    public ItemBuilderJS group(String tabId) {
        this.groupTab = tabId == null || tabId.isBlank() ? null : ResourceLocation.parse(tabId);
        return this;
    }

    /** 取目标创造标签页 id（可能为 null）。 */
    public ResourceLocation getGroupTab() { return groupTab; }

    public ItemBuilderJS rarity(String rarityStr) {
        this.rarity = switch (rarityStr.toLowerCase()) {
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
        return this;
    }

    public ItemBuilderJS glowing() { this.glowing = true; return this; }

    /** 燃料燃烧时间（tick）。>0 时物品可作为熔炉/高炉/烟熏炉燃料。 */
    public ItemBuilderJS burnTime(int ticks) { this.burnTime = Math.max(0, ticks); return this; }

    public ItemBuilderJS food(Consumer<FoodBuilderJS> consumer) {
        this.foodBuilder = new FoodBuilderJS();
        consumer.accept(this.foodBuilder);
        return this;
    }

    public Item createItem() {
        Item.Properties props = buildProperties();

        // 仅在需要覆盖方法（发光/燃料）时用匿名子类，否则直接 new Item
        if (!glowing && burnTime <= 0) {
            return new Item(props);
        }

        final boolean foil = glowing;
        final int burn = burnTime;
        return new Item(props) {
            @Override
            public boolean isFoil(ItemStack stack) {
                return foil;
            }

            // 1.21.1: IItemExtension.getBurnTime(ItemStack, RecipeType) —— 无 FuelValues 参数
            @Override
            public int getBurnTime(ItemStack stack, net.minecraft.world.item.crafting.RecipeType<?> type) {
                return burn;
            }
        };
    }

    /**
     * 构建配置好的 {@link Item.Properties}。供 {@link net.minecraft.world.item.BlockItem}
     * 等需要复用属性的场景调用（脚本通过 BlockBuilderJS.item(...) 配置 BlockItem 属性时走这里）。
     * 1.21.1: 直接使用 new Item.Properties()，不需要 setId()。
     */
    public Item.Properties buildProperties() {
        Item.Properties props = new Item.Properties();

        if (maxDamage > 0) {
            props.durability(maxDamage);
        } else {
            props.stacksTo(maxStackSize);
        }

        if (fireResistant) props.fireResistant();
        if (rarity != Rarity.COMMON) props.rarity(rarity);

        if (foodBuilder != null) {
            // 1.21.1 中食物相关属性全部由 food 囊括
            props.food(foodBuilder.buildFood());
        }

        return props;
    }
}