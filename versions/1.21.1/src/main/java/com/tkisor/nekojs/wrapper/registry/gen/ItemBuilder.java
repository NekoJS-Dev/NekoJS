// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.wrapper.registry.FoodBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 物品 builder（ticket 15 单一写入语义：property 赋值与显式 setter 同一写入点）：
 * <pre>
 * event.item('mymod:ruby', b =&gt; { b.maxStackSize = 16; b.rarity = 'epic' })
 * </pre>
 * 复合配置（food）保留 void 方法；发光 / 燃料仅在需要覆盖方法时用匿名子类。
 */
public class ItemBuilder extends RegistryObjectBuilder<Item> {

    /** 已分配创造标签页的物品：物品 id → 标签页 id（BuildCreativeModeTabContents 时消费）。 */
    public static final Map<ResourceLocation, ResourceLocation> GROUP_ASSIGNMENTS = new HashMap<>();

    private int maxStackSize = 64;
    private int maxDamage = 0;
    private boolean fireResistant = false;
    /** 稀有度名：common / uncommon / rare / epic（默认 common；setter 归一化为小写）。 */
    private String rarity = "common";
    private boolean glowing = false;
    /** 燃料燃烧时间（tick）。&gt;0 时物品可作为熔炉/高炉/烟熏炉燃料。 */
    private int burnTime = 0;
    /** 可选：创造标签页 id（如 'minecraft:building_blocks' 或自定义 tab id）。null=不分配。 */
    private String groupTab = null;

    private FoodBuilderJS foodBuilder = null;

    public ItemBuilder(ResourceLocation id) {
        super(id);
    }

    // ---- 数据属性：显式 setter 与 JavaBean property 同一写入点 ----

    public int getMaxStackSize() {
        return maxStackSize;
    }

    /** 最大堆叠（1-99；MC 在注册冻结期对越界值报错，这里在写入期给出带成员名的错误）。 */
    public void setMaxStackSize(int maxStackSize) {
        if (maxStackSize < 1 || maxStackSize > 99) {
            throw new IllegalArgumentException("maxStackSize must be in [1, 99] but got " + maxStackSize);
        }
        this.maxStackSize = maxStackSize;
    }

    public int getMaxDamage() {
        return maxDamage;
    }

    public void setMaxDamage(int maxDamage) {
        if (maxDamage < 0) {
            throw new IllegalArgumentException("maxDamage must be >= 0 but got " + maxDamage);
        }
        this.maxDamage = maxDamage;
    }

    public boolean isFireResistant() {
        return fireResistant;
    }

    public void setFireResistant(boolean fireResistant) {
        this.fireResistant = fireResistant;
    }

    /** 稀有度名（已归一化小写；null 容忍，build 期回退 common——与既有语义一致）。 */
    public String getRarity() {
        return rarity;
    }

    public void setRarity(String rarity) {
        this.rarity = rarity == null ? null : rarity.toLowerCase();
    }

    public boolean isGlowing() {
        return glowing;
    }

    public void setGlowing(boolean glowing) {
        this.glowing = glowing;
    }

    public int getBurnTime() {
        return burnTime;
    }

    public void setBurnTime(int burnTime) {
        if (burnTime < 0) {
            throw new IllegalArgumentException("burnTime must be >= 0 but got " + burnTime);
        }
        this.burnTime = burnTime;
    }

    /** 创造标签页 id（已归一化：空白视为不分配——build 期本来就走同一分支）。 */
    public String getGroupTab() {
        return groupTab;
    }

    public void setGroupTab(String groupTab) {
        this.groupTab = groupTab == null || groupTab.isBlank() ? null : groupTab;
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
            GROUP_ASSIGNMENTS.put(id, ResourceLocation.parse(groupTab));
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
            public int getBurnTime(ItemStack stack, net.minecraft.world.item.crafting.RecipeType<?> type) {
                return burn;
            }
        };
    }

    /**
     * 构建配置好的 {@link Item.Properties}（id / stackSize / durability / fireResistant /
     * rarity / 食物组件）。供 BlockBuilder 等需要复用属性的场景调用。
     */
    public Item.Properties buildProperties() {
        Item.Properties props = new Item.Properties();

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
