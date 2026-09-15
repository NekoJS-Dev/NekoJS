// 26.x 实现，本文件不应再出现版本守卫。1.21.1 的实现是 versions/1.21.1/src 下的同名文件，
// 改本文件行为时须同步它。
package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.wrapper.registry.FoodBuilderJS;
import com.tkisor.nekojs.wrapper.registry.TaggableBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 物品 builder（ticket 15 单一写入语义）：数据属性私有 + JavaBean setter，
 * 脚本端 {@code b.maxStackSize = 16}（经 {@link BuilderSurface} 的 putMember）
 * 与显式 {@code b.setMaxStackSize(16)} 调用<b>同一个 setter</b>，进入同一校验、
 * 规范化、definition fingerprint 与注册收集路径（spec 04/08；ADR-0005 public field
 * 约定已废除，不得保留同名 public field 绕过 setter）。
 * <pre>
 * event.item('mymod:ruby', b =&gt; { b.maxStackSize = 16; b.rarity = 'epic' })
 * </pre>
 * 复合配置（food）保留 void 方法；发光 / 燃料仅在需要覆盖方法时用匿名子类。
 */
public class ItemBuilder extends RegistryObjectBuilder<Item> implements TaggableBuilder<ItemBuilder> {

    /** 已分配创造标签页的物品：物品 id → 标签页 id（BuildCreativeModeTabContents 时消费）。 */
    public static final Map<Identifier, Identifier> GROUP_ASSIGNMENTS = new HashMap<>();

    /** burnTime 记账：物品 id → 燃烧 tick。fabric 消费（FuelValueEvents.BUILD 灌入）；
     * NeoForge 走匿名子类的 getBurnTime override，不读此表。 */
    public static final Map<Identifier, Integer> FUEL_ASSIGNMENTS = new HashMap<>();

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

    public ItemBuilder(Identifier id) {
        super(id);
    }

    /** {@link TaggableBuilder}：物品 tag 归属 ITEM 注册表。 */
    @Override
    public net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>> getTagRegistry() {
        return Registries.ITEM;
    }

    @Override
    public Identifier getLocation() {
        return id;
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

    // ---- 复合配置 ----

    /** 配置食物属性（nutrition/saturation/效果等），复合配置保留方法面。 */
    public void food(Consumer<FoodBuilderJS> consumer) {
        this.foodBuilder = new FoodBuilderJS();
        consumer.accept(this.foodBuilder);
    }

    @Override
    public Item build() {
        Item.Properties props = buildProperties();

        if (groupTab != null) {
            GROUP_ASSIGNMENTS.put(id, Identifier.parse(groupTab));
        }
        if (burnTime > 0) {
            FUEL_ASSIGNMENTS.put(id, burnTime);
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

//? if neoforge {
            // getBurnTime 是 NeoForge 注入 Item 的 patch 方法，fabric 上不存在此 override；
            // fabric 的燃料面（FabricFuelRegistry）随燃料批次接（见 docs/fabric-port-status.md）
            @Override
            public int getBurnTime(ItemStack stack, net.minecraft.world.item.crafting.RecipeType<?> type,
                                    net.minecraft.world.level.block.entity.FuelValues fuelValues) {
                return burn;
            }
//?}
        };
    }

    /**
     * 构建配置好的 {@link Item.Properties}（id / stackSize / durability / fireResistant /
     * rarity / 食物组件）。供 BlockBuilder 等需要复用属性的场景调用。
     */
    public Item.Properties buildProperties() {
        net.minecraft.resources.ResourceKey<Item> key =
                net.minecraft.resources.ResourceKey.create(Registries.ITEM, id);
        Item.Properties props = new Item.Properties().setId(key);

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
            props.component(net.minecraft.core.component.DataComponents.CONSUMABLE, foodBuilder.buildConsumable());
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
