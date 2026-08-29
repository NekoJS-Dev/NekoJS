// TODO(loader-port): deferred to the LoaderBridge fabric port
//? if neoforge {
package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.wrapper.registry.FoodBuilderJS;
//? if >=26 {
import com.tkisor.nekojs.wrapper.registry.TaggableBuilder;
//?}
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 物品 builder（ADR-0005 public field 约定）：
 * <pre>
 * event.item('mymod:ruby', b =&gt; { b.maxStackSize = 16; b.rarity = 'epic' })
 * </pre>
 * 复合配置（food）保留 void 方法；发光 / 燃料仅在需要覆盖方法时用匿名子类。
 */
//? if >=26 {
public class ItemBuilder extends RegistryObjectBuilder<Item> implements TaggableBuilder<ItemBuilder> {
//?} else {
/*public class ItemBuilder extends RegistryObjectBuilder<Item> {
*///?}

    /** 已分配创造标签页的物品：物品 id → 标签页 id（BuildCreativeModeTabContents 时消费）。 */
    public static final Map<Identifier, Identifier> GROUP_ASSIGNMENTS = new HashMap<>();

    public int maxStackSize = 64;
    public int maxDamage = 0;
    public boolean fireResistant = false;
    /** 稀有度名：common / uncommon / rare / epic（默认 common）。 */
    public String rarity = "common";
    public boolean glowing = false;
    /** 燃料燃烧时间（tick）。&gt;0 时物品可作为熔炉/高炉/烟熏炉燃料。 */
    public int burnTime = 0;
    /** 可选：创造标签页 id（如 'minecraft:building_blocks' 或自定义 tab id）。null=不分配。 */
    public String groupTab = null;

    private FoodBuilderJS foodBuilder = null;

    public ItemBuilder(Identifier id) {
        super(id);
//? if >=26 {
    }

    /** {@link TaggableBuilder}：物品 tag 归属 ITEM 注册表。 */
    @Override
    public net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>> getTagRegistry() {
        return Registries.ITEM;
    }

    @Override
    public Identifier getLocation() {
        return id;
//?}
    }

    /** 配置食物属性（nutrition/saturation/效果等），复合配置保留方法面。 */
    public void food(Consumer<FoodBuilderJS> consumer) {
        this.foodBuilder = new FoodBuilderJS();
        consumer.accept(this.foodBuilder);
    }

    @Override
    public Item build() {
        Item.Properties props = buildProperties();

        if (groupTab != null && !groupTab.isBlank()) {
            GROUP_ASSIGNMENTS.put(id, Identifier.parse(groupTab));
        }

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

            @Override
//? if >=26 {
            public int getBurnTime(ItemStack stack, net.minecraft.world.item.crafting.RecipeType<?> type,
                                    net.minecraft.world.level.block.entity.FuelValues fuelValues) {
//?} else {
/*            public int getBurnTime(ItemStack stack, net.minecraft.world.item.crafting.RecipeType<?> type) {
*///?}
                return burn;
            }
        };
    }

    /**
     * 构建配置好的 {@link Item.Properties}（id / stackSize / durability / fireResistant /
     * rarity / 食物组件）。供 BlockBuilder 等需要复用属性的场景调用。
     */
    public Item.Properties buildProperties() {
//? if >=26 {
        net.minecraft.resources.ResourceKey<Item> key =
                net.minecraft.resources.ResourceKey.create(Registries.ITEM, id);
        Item.Properties props = new Item.Properties().setId(key);
//?} else {
/*        Item.Properties props = new Item.Properties();
*///?}

        if (maxDamage > 0) {
            props.durability(maxDamage);
        } else {
            props.stacksTo(maxStackSize);
        }

        if (fireResistant) props.fireResistant();
        Rarity resolved = resolveRarity(rarity);
        if (resolved != Rarity.COMMON) props.rarity(resolved);

        if (foodBuilder != null) {
            props.food(foodBuilder.buildFood());
//? if >=26 {
            props.component(net.minecraft.core.component.DataComponents.CONSUMABLE, foodBuilder.buildConsumable());
//?}
        }

        return props;
    }

    private static Rarity resolveRarity(String name) {
        return switch (name == null ? "common" : name.toLowerCase()) {
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }
}
//?}
