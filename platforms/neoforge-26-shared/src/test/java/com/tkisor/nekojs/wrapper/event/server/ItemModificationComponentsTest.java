package com.tkisor.nekojs.wrapper.event.server;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ItemModificationJS} 新增的 food / tool / attackDamage / attackSpeed 属性经
 * {@code applyTo} 写入数据组件的行为（JS 对象字面量按 GraalJS 默认转换以
 * {@code Map<String, Object>} 形式构造）。
 *
 * <p>与 {@code ItemModificationEventJS.fire} 的快照-恢复模型对齐：每次 apply 都在
 * “物品原始组件 base + addAll(base) 的 builder”上叠加，因此这里的
 * {@link #apply(ItemModificationJS, DataComponentMap)} 就是事件路径去掉 server 绑定
 * 后的最小复刻（fireResistant/tool 之外不需要 server）。
 *
 * <p>裸 JUnit 无 FML bootstrap：方块注册表为空，tool 的“全方块生效”HolderSet 只能做
 * 结构断言（规则数/字段值），catch-all 匹配所有方块的行为由 in-game 验证覆盖。
 */
class ItemModificationComponentsTest {

    @BeforeAll
    static void requireVanillaRegistries() {
        // DataComponents/Attributes 类初始化依赖 BuiltInRegistries；无 FML Loader 的裸 JUnit 跳过
        org.junit.jupiter.api.Assumptions.assumeTrue(
                com.tkisor.nekojs.testfixture.VanillaRegistryProbe.available(),
                "component constants need vanilla registries (no FML loader in bare JUnit)");
    }

    private static final Identifier TEST_KNOCKBACK_ID = Identifier.withDefaultNamespace("nekojs/test_knockback");

    // ------------------------------------------------------------------ food

    @Test
    void foodWritesFoodAndDefaultConsumable() {
        ItemModificationJS mod = new ItemModificationJS();
        mod.setFood(map("nutrition", 4, "saturation", 0.6, "canAlwaysEat", true));

        DataComponentMap result = apply(mod, emptyBase());

        FoodProperties food = result.get(DataComponents.FOOD);
        assertEquals(4, food.nutrition());
        // saturation 是系数语义：绝对饱和度 = nutrition * saturation * 2
        assertEquals(4 * 0.6F * 2.0F, food.saturation(), 1.0e-6F);
        assertTrue(food.canAlwaysEat());
        // FOOD 本身不触发右键进食，没有 CONSUMABLE 的物品必须补一个（默认 1.6 秒）
        Consumable consumable = result.get(DataComponents.CONSUMABLE);
        assertEquals(Consumable.DEFAULT_CONSUME_SECONDS, consumable.consumeSeconds());
    }

    @Test
    void foodDefaultsMatchDocumentedValues() {
        ItemModificationJS mod = new ItemModificationJS();
        mod.setFood(Map.of());

        FoodProperties food = apply(mod, emptyBase()).get(DataComponents.FOOD);

        assertEquals(1, food.nutrition());
        assertEquals(1 * 0.1F * 2.0F, food.saturation(), 1.0e-6F);
        assertFalse(food.canAlwaysEat());
    }

    @Test
    void foodEatSecondsReplacesConsumableDuration() {
        ItemModificationJS mod = new ItemModificationJS();
        mod.setFood(map("nutrition", 2, "eatSeconds", 0.8));

        DataComponentMap result = apply(mod, emptyBase());

        assertEquals(0.8F, result.get(DataComponents.CONSUMABLE).consumeSeconds());
    }

    @Test
    void foodWithoutEatSecondsKeepsExistingConsumable() {
        DataComponentMap base = DataComponentMap.builder()
            .set(DataComponents.CONSUMABLE, Consumable.builder().consumeSeconds(0.8F).build())
            .build();
        ItemModificationJS mod = new ItemModificationJS();
        mod.setFood(Map.of("nutrition", 4));

        DataComponentMap result = apply(mod, base);

        assertEquals(0.8F, result.get(DataComponents.CONSUMABLE).consumeSeconds());
    }

    @Test
    void nullFoodRemovesFoodAndConsumableOfFormerFood() {
        DataComponentMap base = foodBase();
        ItemModificationJS mod = new ItemModificationJS();
        mod.setFood(null);

        DataComponentMap result = apply(mod, base);

        assertNull(result.get(DataComponents.FOOD));
        assertNull(result.get(DataComponents.CONSUMABLE));
    }

    @Test
    void nullFoodKeepsDrinkOnlyConsumable() {
        // 药水一类：有 CONSUMABLE 没有 FOOD——移除食物不能顺带毁掉饮用功能
        DataComponentMap base = DataComponentMap.builder()
            .set(DataComponents.CONSUMABLE, Consumable.builder().build())
            .build();
        ItemModificationJS mod = new ItemModificationJS();
        mod.setFood(null);

        DataComponentMap result = apply(mod, base);

        assertNull(result.get(DataComponents.FOOD));
        assertEquals(Consumable.DEFAULT_CONSUME_SECONDS, result.get(DataComponents.CONSUMABLE).consumeSeconds());
    }

    @Test
    void invalidFoodValuesRejectedWithActionableErrors() {
        ItemModificationJS mod = new ItemModificationJS();

        assertEquals("Invalid food '4': expected an object like { nutrition: 4, saturation: 0.6, canAlwaysEat: true, eatSeconds: 1.6 }",
            assertThrows(IllegalArgumentException.class, () -> mod.setFood(4)).getMessage());
        assertEquals("Unknown food option 'flavor': expected one of nutrition, saturation, canAlwaysEat, eatSeconds",
            assertThrows(IllegalArgumentException.class, () -> mod.setFood(Map.of("flavor", "sweet"))).getMessage());
        assertEquals("Invalid food nutrition -1: must be >= 0",
            assertThrows(IllegalArgumentException.class, () -> mod.setFood(Map.of("nutrition", -1))).getMessage());
        assertEquals("Invalid food saturation -0.5: must be >= 0",
            assertThrows(IllegalArgumentException.class, () -> mod.setFood(Map.of("saturation", -0.5F))).getMessage());
        assertEquals("Invalid food eatSeconds -1.0: must be >= 0",
            assertThrows(IllegalArgumentException.class, () -> mod.setFood(Map.of("eatSeconds", -1.0F))).getMessage());
    }

    // ------------------------------------------------------------- attributes

    @Test
    void attackDamageReplacesOnlyDamageEntry() {
        ItemModificationJS mod = new ItemModificationJS();
        mod.setAttackDamage(7.0);

        ItemAttributeModifiers out = apply(mod, swordBase()).get(DataComponents.ATTRIBUTE_MODIFIERS);

        // 伤害条目被替换，攻速/击退条目原样保留
        assertEquals(3, out.modifiers().size());
        assertEquals(7.0, out.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND), 1.0e-9);
        assertEquals(-2.4, out.compute(Attributes.ATTACK_SPEED, 0.0, EquipmentSlot.MAINHAND), 1.0e-9);
        assertEquals(1.0, out.compute(Attributes.ATTACK_KNOCKBACK, 0.0, EquipmentSlot.MAINHAND), 1.0e-9);
        // 条目细节：base_attack_damage / ADD_VALUE / 主手，且只作用于主手
        ItemAttributeModifiers.Entry damage = entryOf(out, Attributes.ATTACK_DAMAGE);
        assertTrue(damage.modifier().is(Item.BASE_ATTACK_DAMAGE_ID));
        assertEquals(AttributeModifier.Operation.ADD_VALUE, damage.modifier().operation());
        assertEquals(EquipmentSlotGroup.MAINHAND, damage.slot());
        assertEquals(0.0, out.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.OFFHAND), 1.0e-9);
    }

    @Test
    void attackSpeedAloneKeepsDamageEntry() {
        ItemModificationJS mod = new ItemModificationJS();
        mod.setAttackSpeed(-2.9);

        ItemAttributeModifiers out = apply(mod, swordBase()).get(DataComponents.ATTRIBUTE_MODIFIERS);

        assertEquals(3.0, out.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND), 1.0e-9);
        assertEquals(-2.9, out.compute(Attributes.ATTACK_SPEED, 0.0, EquipmentSlot.MAINHAND), 1.0e-9);
    }

    @Test
    void attributesWorkOnItemWithoutComponent() {
        ItemModificationJS mod = new ItemModificationJS();
        mod.setAttackDamage(6.0);
        mod.setAttackSpeed(-2.4);

        ItemAttributeModifiers out = apply(mod, emptyBase()).get(DataComponents.ATTRIBUTE_MODIFIERS);

        assertEquals(2, out.modifiers().size());
        assertEquals(6.0, out.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND), 1.0e-9);
        assertEquals(-2.4, out.compute(Attributes.ATTACK_SPEED, 0.0, EquipmentSlot.MAINHAND), 1.0e-9);
    }

    @Test
    void replacedEntryKeepsTooltipDisplay() {
        ItemAttributeModifiers hiddenDamage = ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 3.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND,
                ItemAttributeModifiers.Display.hidden())
            .build();
        DataComponentMap base = DataComponentMap.builder()
            .set(DataComponents.ATTRIBUTE_MODIFIERS, hiddenDamage)
            .build();
        ItemModificationJS mod = new ItemModificationJS();
        mod.setAttackDamage(7.0);

        ItemAttributeModifiers out = apply(mod, base).get(DataComponents.ATTRIBUTE_MODIFIERS);

        assertEquals(ItemAttributeModifiers.Display.hidden(), entryOf(out, Attributes.ATTACK_DAMAGE).display());
    }

    // ------------------------------------------------------------------ tool

    @Test
    void toolWritesCatchAllRule() {
        ItemModificationJS mod = new ItemModificationJS();
        mod.setTool(map("miningSpeed", 6, "damagePerBlock", 2, "canDestroyBlocksInCreative", false));

        Tool tool = apply(mod, emptyBase()).get(DataComponents.TOOL);

        // 单条 catch-all 规则：速度 + 正确工具掉落（空注册表下无法断言匹配范围，见类注释）
        assertEquals(1, tool.rules().size());
        Tool.Rule rule = tool.rules().getFirst();
        assertEquals(6.0F, rule.speed().orElseThrow().floatValue());
        assertEquals(Boolean.TRUE, rule.correctForDrops().orElseThrow());
        assertEquals(2, tool.damagePerBlock());
        assertFalse(tool.canDestroyBlocksInCreative());
        assertEquals(1.0F, tool.defaultMiningSpeed());
    }

    @Test
    void nullToolRemovesComponent() {
        DataComponentMap base = DataComponentMap.builder()
            .set(DataComponents.TOOL, new Tool(List.of(), 4.0F, 1, true))
            .build();
        ItemModificationJS mod = new ItemModificationJS();
        mod.setTool(null);

        assertNull(apply(mod, base).get(DataComponents.TOOL));
    }

    @Test
    void invalidToolValuesRejectedWithActionableErrors() {
        ItemModificationJS mod = new ItemModificationJS();

        assertEquals("Invalid tool: 'miningSpeed' is required (e.g. { miningSpeed: 6 })",
            assertThrows(IllegalArgumentException.class, () -> mod.setTool(Map.of())).getMessage());
        assertEquals("Invalid tool miningSpeed 0.0: must be > 0",
            assertThrows(IllegalArgumentException.class, () -> mod.setTool(Map.of("miningSpeed", 0.0F))).getMessage());
        assertEquals("Unknown tool option 'tier': expected one of miningSpeed, damagePerBlock, canDestroyBlocksInCreative",
            assertThrows(IllegalArgumentException.class, () -> mod.setTool(Map.of("miningSpeed", 6, "tier", "iron"))).getMessage());
    }

    // ------------------------------------------------- snapshot/restore model

    @Test
    void modificationsComposeOnPristineSnapshotNotOnPatchedMap() {
        DataComponentMap pristine = DataComponentMap.builder().addAll(swordBase())
            .set(DataComponents.FOOD, new FoodProperties(4, 4.8F, false))
            .set(DataComponents.CONSUMABLE, Consumable.builder().build())
            .build();

        ItemModificationJS first = new ItemModificationJS();
        first.setFood(Map.of("nutrition", 8));
        first.setAttackDamage(7.0);
        DataComponentMap afterFirst = apply(first, pristine);
        assertEquals(8, afterFirst.get(DataComponents.FOOD).nutrition());

        // reload：脚本改了主意（只剩 stack size），事件先回到快照再叠加——第一次的
        // food/attackDamage 修改不残留，原始组件（营养 4 的食物、3.0 伤害）原样回来
        ItemModificationJS second = new ItemModificationJS();
        second.setMaxStackSize(16);
        DataComponentMap afterSecond = apply(second, pristine);

        assertEquals(4, afterSecond.get(DataComponents.FOOD).nutrition());
        assertEquals(Consumable.DEFAULT_CONSUME_SECONDS, afterSecond.get(DataComponents.CONSUMABLE).consumeSeconds());
        assertEquals(3.0, afterSecond.get(DataComponents.ATTRIBUTE_MODIFIERS)
            .compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND), 1.0e-9);
    }

    // -------------------------------------------------------------- helpers

    /** 复刻 ItemModificationEventJS#modify 的写法：builder 以 base 全量播种后叠加修改。 */
    private static DataComponentMap apply(ItemModificationJS mod, DataComponentMap base) {
        DataComponentMap.Builder builder = DataComponentMap.builder().addAll(base);
        mod.applyTo(builder, base, null);
        return builder.build();
    }

    private static DataComponentMap emptyBase() {
        return DataComponentMap.builder().build();
    }

    private static DataComponentMap foodBase() {
        return DataComponentMap.builder()
            .set(DataComponents.FOOD, new FoodProperties(4, 4.8F, false))
            .set(DataComponents.CONSUMABLE, Consumable.builder().build())
            .build();
    }

    /** 剑形态的 ATTRIBUTE_MODIFIERS：伤害 3.0、攻速 -2.4，外加一条无关属性验证保留。 */
    private static DataComponentMap swordBase() {
        ItemAttributeModifiers sword = ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 3.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED,
                new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.4, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_KNOCKBACK,
                new AttributeModifier(TEST_KNOCKBACK_ID, 1.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .build();
        return DataComponentMap.builder()
            .set(DataComponents.ATTRIBUTE_MODIFIERS, sword)
            .build();
    }

    private static ItemAttributeModifiers.Entry entryOf(ItemAttributeModifiers modifiers, Holder<Attribute> attribute) {
        return modifiers.modifiers().stream()
            .filter(entry -> entry.attribute().equals(attribute))
            .findFirst()
            .orElseThrow();
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
