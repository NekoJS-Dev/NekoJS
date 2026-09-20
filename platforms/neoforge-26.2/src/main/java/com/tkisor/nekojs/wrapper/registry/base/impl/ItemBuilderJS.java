package com.tkisor.nekojs.wrapper.registry.base.impl;

import com.tkisor.nekojs.wrapper.registry.FoodBuilderJS;
import com.tkisor.nekojs.wrapper.registry.base.RegistryInfo;
import com.tkisor.nekojs.wrapper.registry.base.RegistryObjectBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.FuelValues;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

public class ItemBuilderJS extends RegistryObjectBuilder<Item> {

    public int maxStackSize = 64;
    public int maxDamage = 0;
    public boolean fireResistant = false;
    public Rarity rarity = Rarity.COMMON;
    public boolean glowing = false;
    public int burnTime = 0;
    public Identifier groupTab = null;
    public Consumer<FoodBuilderJS> food = null;

    public ItemBuilderJS(RegistryInfo<Item> info, Identifier id) {
        super(info, id);
    }

    @Override
    public Item build() {
        Item.Properties props = buildProperties();

        // 仅在需要覆盖方法（发光/燃料）时用匿名子类，否则直接 new Item
        if (!glowing && burnTime <= 0) {
            return new Item(props);
        }

        final boolean foil = glowing;
        final int burn = burnTime;
        return new Item(props) {
            @Override
            public boolean isFoil(@NonNull ItemStack stack) {
                return foil;
            }

            @Override
            public int getBurnTime(@NonNull ItemStack stack, RecipeType<?> type, @NonNull FuelValues fuelValues) {
                return burn;
            }
        };
    }

    /**
     * 构建配置好的 {@link Item.Properties}（含 id / stackSize / durability / fireResistant /
     * rarity / 食物组件）。供 {@link net.minecraft.world.item.BlockItem} 等需要复用属性的
     * 场景调用（脚本通过 BlockBuilderJS.item(...) 配置 BlockItem 属性时走这里）。
     */
    public Item.Properties buildProperties() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item.Properties props = new Item.Properties().setId(key);

        if (maxDamage > 0) {
            props.durability(maxDamage);
        } else {
            props.stacksTo(maxStackSize);
        }

        if (fireResistant) {
            props.fireResistant();
        }

        if (rarity != Rarity.COMMON) {
            props.rarity(rarity);
        }

        if (food != null) {
            var foodBuilder = new FoodBuilderJS();
            food.accept(foodBuilder);
            props.food(foodBuilder.buildFood());
            props.component(DataComponents.CONSUMABLE, foodBuilder.buildConsumable());
        }

        return props;
    }
}