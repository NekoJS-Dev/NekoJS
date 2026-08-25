package com.tkisor.nekojs.wrapper.event.server;

import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ItemModificationJS} 的 food / tool / attackDamage / attackSpeed 四个属性到
 * 26.x 数据组件的映射实现。属性存取在 {@link ItemModificationJS}，构造、默认值与
 * 校验集中在这里，保持事件类本身只关心写入顺序。
 *
 * <h2>26.x 组件模型（与 1.21.1 的差异）</h2>
 * <ul>
 *   <li>食物 = {@code FOOD}（营养价值，{@link FoodProperties}）+ {@code CONSUMABLE}
 *       （进食动画/时长，{@link Consumable}）。只写 {@code FOOD} 不会让物品可食用：
 *       {@code Item#use} 只认 {@code CONSUMABLE}；进食秒数（KubeJS 的 eatSeconds）
 *       在 26.x 属于 {@code CONSUMABLE#consumeSeconds}，不在 {@code FoodProperties} 上。</li>
 *   <li>攻击属性 = {@code ATTRIBUTE_MODIFIERS} 里的
 *       {@code minecraft:base_attack_damage} / {@code minecraft:base_attack_speed}
 *       修饰符（{@link AttributeModifier.Operation#ADD_VALUE}，主手），语义与
 *       KubeJS 的 attackDamage/attackSpeed 相同：只替换同名属性的条目，其余修饰符原样保留。</li>
 *   <li>工具 = {@code TOOL}（{@link Tool}）。26.x 物品侧没有挖掘等级（等级在方块组件上），
 *       “对全部方块生效”用 direct {@link HolderSet} 枚举方块注册表实现
 *       （vanilla 没有 all-blocks tag；默认组件序列化的是 patch，不会把整个集合写进 NBT）。</li>
 * </ul>
 *
 * <p>JS 侧的对象字面量经 GraalJS 默认转换以 {@code Map<String, Object>} 到达
 * {@code setter(Object)}；解析只接受对象（拒绝裸数字等），未知键直接报错并列出合法键，
 * 与 {@code rarity} 解析的报错风格一致。
 */
public final class ItemModificationComponents {

    private static final Set<String> FOOD_KEYS = Set.of("nutrition", "saturation", "canAlwaysEat", "eatSeconds");
    private static final Set<String> TOOL_KEYS = Set.of("miningSpeed", "damagePerBlock", "canDestroyBlocksInCreative");

    private ItemModificationComponents() {}

    /**
     * 食物配置。{@code saturation} 是 26.x builder 语义的“饱和度系数”
     * （绝对饱和度 = nutrition × saturation × 2），与 KubeJS 的 saturation 字段一致。
     * {@code eatSeconds} 为 {@code null} 表示不改动进食时长。
     */
    public record FoodSpec(int nutrition, float saturation, boolean canAlwaysEat, @Nullable Float eatSeconds) {

        FoodProperties toProperties() {
            FoodProperties.Builder builder = new FoodProperties.Builder()
                .nutrition(this.nutrition)
                .saturationModifier(this.saturation);
            if (this.canAlwaysEat) {
                builder.alwaysEdible();
            }
            return builder.build();
        }
    }

    /** 工具配置：全方块生效的挖掘速度 + 每方块耐久损耗。 */
    public record ToolSpec(float miningSpeed, int damagePerBlock, boolean canDestroyBlocksInCreative) {

        Tool toTool() {
            HolderSet<Block> everyBlock = HolderSet.direct(BuiltInRegistries.BLOCK.listElements().toList());
            return new Tool(
                List.of(Tool.Rule.minesAndDrops(everyBlock, this.miningSpeed)),
                1.0F,
                this.damagePerBlock,
                this.canDestroyBlocksInCreative);
        }
    }

    // ---------------------------------------------------------------- food

    static FoodSpec parseFood(Object value) {
        Map<?, ?> map = requireMap(value, "food", "{ nutrition: 4, saturation: 0.6, canAlwaysEat: true, eatSeconds: 1.6 }");
        rejectUnknownKeys(map, FOOD_KEYS, "food");
        int nutrition = intOption(map, "nutrition", 1);
        float saturation = floatOption(map, "saturation", 0.1F);
        boolean canAlwaysEat = booleanOption(map, "canAlwaysEat", false);
        Float eatSeconds = floatOption(map, "eatSeconds");
        if (nutrition < 0) {
            throw new IllegalArgumentException("Invalid food nutrition " + nutrition + ": must be >= 0");
        }
        if (saturation < 0.0F) {
            throw new IllegalArgumentException("Invalid food saturation " + saturation + ": must be >= 0");
        }
        if (eatSeconds != null && eatSeconds < 0.0F) {
            throw new IllegalArgumentException("Invalid food eatSeconds " + eatSeconds + ": must be >= 0");
        }
        return new FoodSpec(nutrition, saturation, canAlwaysEat, eatSeconds);
    }

    /**
     * 写入 FOOD，并保证物品可食用：显式 eatSeconds 时替换 CONSUMABLE（默认动画/音效）；
     * 否则物品已有 CONSUMABLE 就沿用（保留药水等自定义效果），没有则补一个默认 1.6 秒的。
     */
    static void applyFood(DataComponentMap.Builder builder, DataComponentMap base, FoodSpec food) {
        builder.set(DataComponents.FOOD, food.toProperties());
        if (food.eatSeconds() != null) {
            builder.set(DataComponents.CONSUMABLE, Consumable.builder().consumeSeconds(food.eatSeconds()).build());
        } else if (base.get(DataComponents.CONSUMABLE) == null) {
            builder.set(DataComponents.CONSUMABLE, Consumable.builder().build());
        }
    }

    /**
     * 移除食物：FOOD 总是移除；原本就是食物（base 有 FOOD）的物品连 CONSUMABLE 一起移除，
     * 否则右键仍会播放进食动画并消耗物品。只有 CONSUMABLE 没有 FOOD 的物品（药水）不受影响。
     */
    static void removeFood(DataComponentMap.Builder builder, DataComponentMap base) {
        builder.set(DataComponents.FOOD, null);
        if (base.get(DataComponents.FOOD) != null) {
            builder.set(DataComponents.CONSUMABLE, null);
        }
    }

    // ---------------------------------------------------------------- tool

    static ToolSpec parseTool(Object value) {
        Map<?, ?> map = requireMap(value, "tool", "{ miningSpeed: 6, damagePerBlock: 1, canDestroyBlocksInCreative: true }");
        rejectUnknownKeys(map, TOOL_KEYS, "tool");
        Float miningSpeed = floatOption(map, "miningSpeed");
        if (miningSpeed == null) {
            throw new IllegalArgumentException("Invalid tool: 'miningSpeed' is required (e.g. { miningSpeed: 6 })");
        }
        if (miningSpeed <= 0.0F) {
            throw new IllegalArgumentException("Invalid tool miningSpeed " + miningSpeed + ": must be > 0");
        }
        int damagePerBlock = intOption(map, "damagePerBlock", 1);
        if (damagePerBlock < 0) {
            throw new IllegalArgumentException("Invalid tool damagePerBlock " + damagePerBlock + ": must be >= 0");
        }
        boolean canDestroyBlocksInCreative = booleanOption(map, "canDestroyBlocksInCreative", true);
        return new ToolSpec(miningSpeed, damagePerBlock, canDestroyBlocksInCreative);
    }

    static void applyTool(DataComponentMap.Builder builder, ToolSpec tool) {
        builder.set(DataComponents.TOOL, tool.toTool());
    }

    // ---------------------------------------------------------- attributes

    /**
     * 替换 base_attack_damage / base_attack_speed 修饰符：其余条目（含另一项攻击属性、
     * 护甲等）原样保留，被替换条目的 tooltip display 也保留（首个命中者），与 KubeJS 8 的
     * attackDamage/attackSpeed 语义一致。
     */
    static void applyAttributes(DataComponentMap.Builder builder, DataComponentMap base,
                                @Nullable Double attackDamage, @Nullable Double attackSpeed) {
        ItemAttributeModifiers current = base.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        builder.set(DataComponents.ATTRIBUTE_MODIFIERS, patchAttributeModifiers(current, attackDamage, attackSpeed));
    }

    private static ItemAttributeModifiers patchAttributeModifiers(ItemAttributeModifiers current,
                                                                  @Nullable Double attackDamage, @Nullable Double attackSpeed) {
        List<ItemAttributeModifiers.Entry> kept = new ArrayList<>(current.modifiers().size());
        ItemAttributeModifiers.Display damageDisplay = null;
        ItemAttributeModifiers.Display speedDisplay = null;
        for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
            if (attackDamage != null && entry.attribute().equals(Attributes.ATTACK_DAMAGE)) {
                if (damageDisplay == null) damageDisplay = entry.display();
                continue;
            }
            if (attackSpeed != null && entry.attribute().equals(Attributes.ATTACK_SPEED)) {
                if (speedDisplay == null) speedDisplay = entry.display();
                continue;
            }
            kept.add(entry);
        }
        if (attackDamage != null) {
            kept.add(new ItemAttributeModifiers.Entry(
                Attributes.ATTACK_DAMAGE,
                new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, attackDamage, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND,
                damageDisplay == null ? ItemAttributeModifiers.Display.attributeModifiers() : damageDisplay));
        }
        if (attackSpeed != null) {
            kept.add(new ItemAttributeModifiers.Entry(
                Attributes.ATTACK_SPEED,
                new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeed, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND,
                speedDisplay == null ? ItemAttributeModifiers.Display.attributeModifiers() : speedDisplay));
        }
        return new ItemAttributeModifiers(List.copyOf(kept));
    }

    // -------------------------------------------------------------- parsing

    private static Map<?, ?> requireMap(Object value, String property, String example) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException("Invalid " + property + " '" + value + "': expected an object like " + example);
    }

    private static void rejectUnknownKeys(Map<?, ?> map, Set<String> known, String property) {
        for (Object key : map.keySet()) {
            if (key instanceof String name && !known.contains(name)) {
                throw new IllegalArgumentException("Unknown " + property + " option '" + name + "': expected one of " + String.join(", ", known));
            }
        }
    }

    private static int intOption(Map<?, ?> map, String key, int defaultValue) {
        Object raw = map.get(key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("Invalid value for '" + key + "': expected a number, got '" + raw + "'");
    }

    private static float floatOption(Map<?, ?> map, String key, float defaultValue) {
        Float value = floatOption(map, key);
        return value == null ? defaultValue : value;
    }

    private static @Nullable Float floatOption(Map<?, ?> map, String key) {
        Object raw = map.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.floatValue();
        }
        throw new IllegalArgumentException("Invalid value for '" + key + "': expected a number, got '" + raw + "'");
    }

    private static boolean booleanOption(Map<?, ?> map, String key, boolean defaultValue) {
        Object raw = map.get(key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("Invalid value for '" + key + "': expected a boolean, got '" + raw + "'");
    }
}
