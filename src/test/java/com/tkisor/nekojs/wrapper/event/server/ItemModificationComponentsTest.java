// TODO(fabric): the fabric side has no matching implementation yet; remove this whole-file guard
// once the port is done
//? if neoforge {
//? if >=26 {
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
 * How {@link ItemModificationJS} writes the food, tool, attackDamage and attackSpeed properties into
 * data components. A JS object literal arrives as a {@code Map<String, Object>} under GraalJS's
 * default conversion.
 *
 * <p>The tests drive the <b>production apply path</b>: view, normalised declaration, then the
 * component write of {@code ModificationDomainOwner}, which is the Adapter's real code. Every apply
 * layers onto a builder built from the item's original component base plus {@code addAll(base)},
 * which is the component half of the commit point that restores the baseline first and then applies
 * declarations in order.
 *
 * <p>Bare JUnit has no FML bootstrap, so the block registry is empty. The tool's "applies to all
 * blocks" {@code HolderSet} can only be checked structurally there (rule count and field values);
 * in-game verification covers the catch-all matching every block.
 */
class ItemModificationComponentsTest {

    @BeforeAll
    static void requireVanillaRegistries() {
        // DataComponents/Attributes class initialisation needs BuiltInRegistries, so a bare JUnit run
        // without the FML Loader is skipped
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
        // saturation is a coefficient: absolute saturation = nutrition * saturation * 2
        assertEquals(4 * 0.6F * 2.0F, food.saturation(), 1.0e-6F);
        assertTrue(food.canAlwaysEat());
        // FOOD alone does not trigger a right-click eat, so an item without CONSUMABLE must
        // get one (default 1.6 seconds)
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
        // Potions and the like have CONSUMABLE but no FOOD: removing food must not take the
        // drink action down with it
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

        // The damage entry is replaced; the attack speed and knockback entries stay as they were
        assertEquals(3, out.modifiers().size());
        assertEquals(7.0, out.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND), 1.0e-9);
        assertEquals(-2.4, out.compute(Attributes.ATTACK_SPEED, 0.0, EquipmentSlot.MAINHAND), 1.0e-9);
        assertEquals(1.0, out.compute(Attributes.ATTACK_KNOCKBACK, 0.0, EquipmentSlot.MAINHAND), 1.0e-9);
        // Entry details: base_attack_damage, ADD_VALUE, mainhand, and it applies to the mainhand only
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

        // A single catch-all rule: speed plus drops with the correct tool (with an empty registry
        // the matching scope cannot be asserted, see the class comment)
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

        // Reload: the script changed its mind (only stack size left), so the event returns to the
        // snapshot first and then stacks on top. The first round's food and attackDamage
        // modifications leave nothing behind, and the original components come back as they were
        // (food with nutrition 4, damage 3.0)
        ItemModificationJS second = new ItemModificationJS();
        second.setMaxStackSize(16);
        DataComponentMap afterSecond = apply(second, pristine);

        assertEquals(4, afterSecond.get(DataComponents.FOOD).nutrition());
        assertEquals(Consumable.DEFAULT_CONSUME_SECONDS, afterSecond.get(DataComponents.CONSUMABLE).consumeSeconds());
        assertEquals(3.0, afterSecond.get(DataComponents.ATTRIBUTE_MODIFIERS)
            .compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND), 1.0e-9);
    }

    // -------------------------------------------------------------- helpers

    /**
     * The production application path: view -> normalized declaration -> component writes in
     * {@code ModificationDomainOwner}. It covers the Adapter's real application code directly.
     */
    private static DataComponentMap apply(ItemModificationJS mod, DataComponentMap base) {
        DataComponentMap.Builder builder = DataComponentMap.builder().addAll(base);
        new ModificationDomainOwner().applyItemProperties(builder, base, mod.normalizedProperties());
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

    /**
     * ATTRIBUTE_MODIFIERS in sword form: damage 3.0, attack speed -2.4, plus one unrelated
     * attribute to check preservation.
     */
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
//?}
//?}
