package com.tkisor.nekojs.core.stage;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.StableStageGatesEvent;
import com.tkisor.nekojs.api.stage.StageReadView;
import com.tkisor.nekojs.api.stage.StageScope;
import com.tkisor.nekojs.api.stage.gate.RegionShape;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration;
import com.tkisor.nekojs.core.stage.gate.CompiledStageGateSnapshot;
import com.tkisor.nekojs.core.stage.gate.CompiledStageGateSnapshot.LootDecision;
import com.tkisor.nekojs.core.stage.gate.InlineRecipeStageRegistry;
import com.tkisor.nekojs.core.stage.gate.StageRegistryAccess;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-common unit tests for {@link CompiledStageGateSnapshot}, priority resolution,
 * {@link StageScope} evaluation, whitelisted containers, {@link InlineRecipeStageRegistry},
 * and all 10 declarative gate domains.
 */
class CompiledStageGateSnapshotTest {

    @Test
    void higherPriorityItemRuleOverridesLowerPriorityNamespaceGate() {
        NekoId configurator = NekoId.of("mekanism", "configurator");
        NekoId disassembler = NekoId.of("mekanism", "atomic_disassembler");

        MutableRegistryAccess registries = new MutableRegistryAccess();
        registries.namespaceItems.put("mekanism", Set.of(configurator, disassembler));

        StableStageGatesEvent gates = new StableStageGatesEvent();
        gates.item(NekoId.of("test", "mekanism_namespace"), "industrial_age")
                .namespace("mekanism")
                .priority(0);
        gates.item(NekoId.of("test", "mekanism_configurator"), "copper_age")
                .item(configurator)
                .priority(100);

        CompiledStageGateSnapshot snapshot = CompiledStageGateSnapshot.compile(
                gates.buildDeclarations(),
                gates.whitelistedContainers(),
                Map.of(),
                registries
        );

        StageReadView copperOnlyPlayer = viewOf("copper_age");
        StageReadView emptyServer = viewOf();

        assertTrue(snapshot.canUseItem(configurator, copperOnlyPlayer, emptyServer));
        assertTrue(snapshot.canPickUpItem(configurator, copperOnlyPlayer, emptyServer));
        assertTrue(snapshot.canStoreInInventory(configurator, copperOnlyPlayer, emptyServer));
        assertFalse(snapshot.isItemHiddenInViewer(configurator, copperOnlyPlayer, emptyServer));
        assertNull(snapshot.lockedItemRule(configurator, copperOnlyPlayer, emptyServer));

        assertFalse(snapshot.canUseItem(disassembler, copperOnlyPlayer, emptyServer));
        assertFalse(snapshot.canPickUpItem(disassembler, copperOnlyPlayer, emptyServer));
        assertTrue(snapshot.isItemHiddenInViewer(disassembler, copperOnlyPlayer, emptyServer));
        StageGateDeclaration.ItemGate lockedDisassembler =
                snapshot.lockedItemRule(disassembler, copperOnlyPlayer, emptyServer);
        assertNotNull(lockedDisassembler);
        assertEquals("industrial_age", lockedDisassembler.stage());

        assertEquals(Set.of(disassembler), snapshot.hiddenViewerItems(copperOnlyPlayer, emptyServer));
        assertEquals(List.of("copper_age"), snapshot.requiredStagesForItem(configurator));
        assertEquals(List.of("industrial_age"), snapshot.requiredStagesForItem(disassembler));
    }

    @Test
    void stageScopesEvaluatePlayerServerEitherAndBothCorrectly() {
        NekoId playerItem = NekoId.of("minecraft", "iron_sword");
        NekoId serverItem = NekoId.of("minecraft", "beacon");
        NekoId eitherItem = NekoId.of("minecraft", "ender_pearl");
        NekoId bothItem = NekoId.of("minecraft", "dragon_egg");

        StableStageGatesEvent gates = new StableStageGatesEvent();
        gates.item(NekoId.of("test", "scope_player"), "shared_stage").item(playerItem).scope("player");
        gates.item(NekoId.of("test", "scope_server"), "shared_stage").item(serverItem).scope("server");
        gates.item(NekoId.of("test", "scope_either"), "shared_stage").item(eitherItem).scope("either");
        gates.item(NekoId.of("test", "scope_both"), "shared_stage").item(bothItem).scope(StageScope.BOTH);

        CompiledStageGateSnapshot snapshot = CompiledStageGateSnapshot.compile(
                gates.buildDeclarations(),
                Set.of(),
                Map.of(),
                StageRegistryAccess.PERMISSIVE
        );

        StageReadView none = viewOf();
        StageReadView granted = viewOf("shared_stage");

        // Neither has stage
        assertFalse(snapshot.canUseItem(playerItem, none, none));
        assertFalse(snapshot.canUseItem(serverItem, none, none));
        assertFalse(snapshot.canUseItem(eitherItem, none, none));
        assertFalse(snapshot.canUseItem(bothItem, none, none));

        // Only player has stage
        assertTrue(snapshot.canUseItem(playerItem, granted, none));
        assertFalse(snapshot.canUseItem(serverItem, granted, none));
        assertTrue(snapshot.canUseItem(eitherItem, granted, none));
        assertFalse(snapshot.canUseItem(bothItem, granted, none));

        // Only server has stage
        assertFalse(snapshot.canUseItem(playerItem, none, granted));
        assertTrue(snapshot.canUseItem(serverItem, none, granted));
        assertTrue(snapshot.canUseItem(eitherItem, none, granted));
        assertFalse(snapshot.canUseItem(bothItem, none, granted));

        // Both player and server have stage
        assertTrue(snapshot.canUseItem(playerItem, granted, granted));
        assertTrue(snapshot.canUseItem(serverItem, granted, granted));
        assertTrue(snapshot.canUseItem(eitherItem, granted, granted));
        assertTrue(snapshot.canUseItem(bothItem, granted, granted));
    }

    @Test
    void whitelistedContainersAllowSafeStorageWhileBlockingNonWhitelistedMenus() {
        NekoId restrictedIngot = NekoId.of("minecraft", "netherite_ingot");
        NekoId chestMenu = NekoId.of("minecraft", "generic_9x3");
        NekoId shulkerMenu = NekoId.of("minecraft", "shulker_box");
        NekoId anvilMenu = NekoId.of("minecraft", "anvil");

        StableStageGatesEvent gates = new StableStageGatesEvent();
        gates.whitelistContainer(chestMenu, shulkerMenu);
        gates.item(NekoId.of("test", "netherite_gate"), "netherite_age")
                .item(restrictedIngot)
                .denyContainerStorage();

        CompiledStageGateSnapshot snapshot = CompiledStageGateSnapshot.compile(
                gates.buildDeclarations(),
                gates.whitelistedContainers(),
                Map.of(),
                StageRegistryAccess.PERMISSIVE
        );

        StageReadView locked = viewOf();
        assertEquals(Set.of(chestMenu, shulkerMenu), snapshot.whitelistedContainers());
        assertTrue(snapshot.canStoreInContainer(restrictedIngot, chestMenu, locked, locked));
        assertTrue(snapshot.canStoreInContainer(restrictedIngot, shulkerMenu, locked, locked));
        assertFalse(snapshot.canStoreInContainer(restrictedIngot, anvilMenu, locked, locked));
        assertFalse(snapshot.canStoreInContainer(restrictedIngot, null, locked, locked));
    }

    @Test
    void itemAndBlockGatesIncludingArmorAndCropMinAgeBehaveAsDeclared() {
        NekoId relicSword = NekoId.of("minecraft", "diamond_sword");
        NekoId steelIngot = NekoId.of("modpack", "steel_ingot");
        NekoId diamondChestplate = NekoId.of("minecraft", "diamond_chestplate");
        NekoId obsidian = NekoId.of("minecraft", "obsidian");
        NekoId wheat = NekoId.of("minecraft", "wheat");

        MutableRegistryAccess registries = new MutableRegistryAccess();
        registries.itemTags.put(NekoId.of("c", "ingots/steel"), Set.of(steelIngot));

        StableStageGatesEvent gates = new StableStageGatesEvent();
        gates.item(NekoId.of("test", "relic_sword"), "combat_tier")
                .item(relicSword)
                .tag(NekoId.of("#c", "ingots/steel"))
                .denyDrop()
                .lockedName("Unidentified Relic")
                .lockedMessage("Requires combat_tier");

        gates.armor(NekoId.of("test", "diamond_armor"), "armor_tier")
                .item(diamondChestplate);

        gates.block(NekoId.of("test", "obsidian_gate"), "obsidian_tier")
                .block(obsidian)
                .allowInteract()
                .denyBreak()
                .denyPlace()
                .denyToolModify();

        gates.crop(NekoId.of("test", "mature_wheat"), "master_farmer")
                .block(wheat)
                .minAge(7);

        CompiledStageGateSnapshot snapshot = CompiledStageGateSnapshot.compile(
                gates.buildDeclarations(),
                Set.of(),
                Map.of(),
                registries
        );

        StageReadView locked = viewOf();
        StageReadView unlockedCombat = viewOf("combat_tier");
        StageReadView unlockedFarmer = viewOf("master_farmer");

        // ItemGate checks (explicit item + expanded tag)
        assertFalse(snapshot.canUseItem(relicSword, locked, locked));
        assertFalse(snapshot.canPickUpItem(relicSword, locked, locked));
        assertFalse(snapshot.canDropItem(relicSword, locked, locked));
        assertFalse(snapshot.canEquipItem(relicSword, locked, locked));
        assertFalse(snapshot.canAttackWithItem(relicSword, locked, locked));
        assertFalse(snapshot.canStoreInInventory(relicSword, locked, locked));
        assertTrue(snapshot.isItemHiddenInViewer(relicSword, locked, locked));
        assertTrue(snapshot.isItemHiddenInViewer(steelIngot, locked, locked));
        assertEquals("Unidentified Relic", snapshot.lockedItemRule(relicSword, locked, locked).lockedName());

        assertTrue(snapshot.canUseItem(relicSword, unlockedCombat, locked));
        assertTrue(snapshot.canDropItem(relicSword, unlockedCombat, locked));
        assertFalse(snapshot.isItemHiddenInViewer(steelIngot, unlockedCombat, locked));

        // Armor preset checks: carryable, pickable, visible, attack allowed, but equip & right-click use blocked
        assertTrue(snapshot.canStoreInInventory(diamondChestplate, locked, locked));
        assertTrue(snapshot.canPickUpItem(diamondChestplate, locked, locked));
        assertTrue(snapshot.canAttackWithItem(diamondChestplate, locked, locked));
        assertFalse(snapshot.isItemHiddenInViewer(diamondChestplate, locked, locked));
        assertFalse(snapshot.canEquipItem(diamondChestplate, locked, locked));
        assertFalse(snapshot.canUseItem(diamondChestplate, locked, locked));

        // BlockGate checks (obsidian + crop minAge)
        assertFalse(snapshot.canBreakBlock(obsidian, locked, locked));
        assertFalse(snapshot.canPlaceBlock(obsidian, locked, locked));
        assertTrue(snapshot.canInteractBlock(obsidian, locked, locked));
        assertFalse(snapshot.canToolModifyBlock(obsidian, locked, locked));
        assertNotNull(snapshot.lockedBlockRule(obsidian, null, locked, locked));

        // Crop minAge = 7: young crops (< 7) are allowed; mature crops (>= 7) are blocked until master_farmer
        assertTrue(snapshot.canPlaceBlock(wheat, locked, locked));
        assertTrue(snapshot.canBreakBlock(wheat, 0, locked, locked));
        assertTrue(snapshot.canBreakBlock(wheat, 6, locked, locked));
        assertTrue(snapshot.canInteractBlock(wheat, 6, locked, locked));
        assertNull(snapshot.lockedBlockRule(wheat, 6, locked, locked));

        assertFalse(snapshot.canBreakBlock(wheat, 7, locked, locked));
        assertFalse(snapshot.canInteractBlock(wheat, 7, locked, locked));
        assertNotNull(snapshot.lockedBlockRule(wheat, 7, locked, locked));

        assertTrue(snapshot.canBreakBlock(wheat, 7, unlockedFarmer, locked));
        assertTrue(snapshot.canInteractBlock(wheat, 7, unlockedFarmer, locked));
    }

    @Test
    void recipeGateAndInlineRecipeStageRegistrySupportPutRekeyAndWithInlineRecipes() {
        NekoId shapedBlastFurnace = NekoId.of("minecraft", "blast_furnace");
        NekoId smeltingType = NekoId.of("minecraft", "smelting");
        NekoId steelPlate = NekoId.of("modpack", "steel_plate");
        NekoId autoGeneratedId = NekoId.of("nekojs", "shaped_auto_1");
        NekoId rekeyedId = NekoId.of("modpack", "custom_press");

        StableStageGatesEvent gates = new StableStageGatesEvent();
        gates.recipe(NekoId.of("test", "blast_furnace_recipe"), "metallurgy")
                .recipeId(shapedBlastFurnace)
                .hideInViewer();
        gates.recipe(NekoId.of("test", "smelting_type_gate"), "smelting_unlocked")
                .recipeType(smeltingType)
                .showInViewer();
        gates.recipe(NekoId.of("test", "steel_plate_output"), "steel_processing")
                .outputItem(steelPlate);

        CompiledStageGateSnapshot baseSnapshot = CompiledStageGateSnapshot.compile(
                gates.buildDeclarations(),
                Set.of(),
                Map.of(),
                StageRegistryAccess.PERMISSIVE
        );

        InlineRecipeStageRegistry inlineRegistry = new InlineRecipeStageRegistry();
        AtomicInteger notifications = new AtomicInteger();
        inlineRegistry.onUpdated(notifications::incrementAndGet);

        inlineRegistry.runBatch(() -> {
            inlineRegistry.put(autoGeneratedId, "press_stage", StageScope.EITHER);
            inlineRegistry.rekey(autoGeneratedId, rekeyedId);
        });
        assertEquals(1, notifications.get(), "runBatch must coalesce put + rekey notifications into one update");
        assertNull(inlineRegistry.get(autoGeneratedId));
        InlineRecipeStageRegistry.InlineEntry rekeyedEntry = inlineRegistry.get(rekeyedId);
        assertNotNull(rekeyedEntry);
        assertEquals("press_stage", rekeyedEntry.stage());
        assertEquals(StageScope.EITHER, rekeyedEntry.scope());

        CompiledStageGateSnapshot mergedSnapshot = baseSnapshot.withInlineRecipes(inlineRegistry.snapshot());
        StageReadView locked = viewOf();

        assertFalse(mergedSnapshot.canAssembleRecipe(shapedBlastFurnace, null, null, locked, locked));
        assertFalse(mergedSnapshot.canAssembleRecipe(NekoId.of("minecraft", "iron_ingot"), smeltingType, null, locked, locked));
        assertFalse(mergedSnapshot.canAssembleRecipe(NekoId.of("modpack", "roll_plate"), null, steelPlate, locked, locked));
        assertFalse(mergedSnapshot.canAssembleRecipe(rekeyedId, null, null, locked, locked));

        assertEquals(
                Set.of(shapedBlastFurnace, rekeyedId),
                mergedSnapshot.hiddenRecipeIds(locked, locked),
                "Explicit hidden recipes and inline recipes must appear in hiddenRecipeIds while showInViewer stays visible"
        );

        StageReadView unlockedPlayer = viewOf("metallurgy");
        StageReadView unlockedServer = viewOf("press_stage");
        assertTrue(mergedSnapshot.canAssembleRecipe(shapedBlastFurnace, null, null, unlockedPlayer, unlockedServer));
        assertTrue(mergedSnapshot.canAssembleRecipe(rekeyedId, null, null, unlockedPlayer, unlockedServer));
        assertEquals(Set.of(), mergedSnapshot.hiddenRecipeIds(unlockedPlayer, unlockedServer));
    }

    @Test
    void containerDimensionRegionEntityEffectEnchantAndLootGatesEnforceAllDomains() {
        NekoId enchantingMenu = NekoId.of("minecraft", "enchantment");
        NekoId netherDim = NekoId.of("minecraft", "the_nether");
        NekoId overworldDim = NekoId.of("minecraft", "overworld");
        NekoId horseEntity = NekoId.of("minecraft", "horse");
        NekoId bossEntity = NekoId.of("modpack", "void_guardian");
        NekoId strengthEffect = NekoId.of("minecraft", "strength");
        NekoId sharpnessEnchant = NekoId.of("minecraft", "sharpness");
        NekoId witherSkull = NekoId.of("minecraft", "wither_skeleton_skull");
        NekoId coal = NekoId.of("minecraft", "coal");
        NekoId enderPearl = NekoId.of("minecraft", "ender_pearl");
        NekoId fortressChestTable = NekoId.of("minecraft", "chests/nether_bridge");
        NekoId endermanEntity = NekoId.of("minecraft", "enderman");

        MutableRegistryAccess registries = new MutableRegistryAccess();
        registries.entityTags.put(NekoId.of("c", "bosses"), Set.of(bossEntity));

        StableStageGatesEvent gates = new StableStageGatesEvent();

        // 4. ContainerGate
        gates.container(NekoId.of("test", "enchanting_menu"), "arcane_table")
                .menu(enchantingMenu);

        // 5. DimensionGate
        gates.dimension(NekoId.of("test", "nether_dim"), "nether_portal")
                .dimension(netherDim);

        // 6. RegionGate (RegionShape.box with reversed coordinates + RegionShape.sphere)
        RegionShape citadelBox = RegionShape.box(overworldDim, 20.0, 90.0, 20.0, -20.0, 60.0, -20.0);
        RegionShape sanctuarySphere = RegionShape.sphere(overworldDim, 200.0, 70.0, 200.0, 10.0);
        assertEquals(-20.0, citadelBox.minX());
        assertEquals(20.0, citadelBox.maxX());
        assertTrue(citadelBox.contains(overworldDim, 0.0, 75.0, 0.0));
        assertFalse(citadelBox.contains(netherDim, 0.0, 75.0, 0.0));
        assertTrue(sanctuarySphere.contains(overworldDim, 206.0, 78.0, 200.0)); // 6^2 + 8^2 = 100 <= 10^2
        assertFalse(sanctuarySphere.contains(overworldDim, 208.0, 78.0, 200.0)); // 8^2 + 8^2 = 128 > 10^2

        gates.region(NekoId.of("test", "citadel_region"), "citadel_key")
                .shapes(citadelBox)
                .denyEnter()
                .denyBreak()
                .denyPlace()
                .denyInteract();
        gates.region(NekoId.of("test", "sanctuary_region"), "sanctuary_builder")
                .shape(sanctuarySphere)
                .allowEnter()
                .allowInteract()
                .denyBreak()
                .denyPlace();

        // 7. EntityGate
        gates.entity(NekoId.of("test", "horse_taming"), "equestrian")
                .entity(horseEntity)
                .allowAttack()
                .allowInteract()
                .denyMount();
        gates.entity(NekoId.of("test", "boss_combat"), "boss_slayer")
                .tag(NekoId.of("c", "bosses"))
                .denyAttack()
                .denyInteract()
                .denyMount();

        // 8. EffectGate
        gates.effect(NekoId.of("test", "strength_effect"), "alchemy_tier")
                .effect(strengthEffect);

        // 9. EnchantGate (aboveLevel(2) -> max allowed level without stage is 2, minLevel = 3)
        gates.enchant(NekoId.of("test", "high_sharpness"), "master_enchanter")
                .enchant(sharpnessEnchant)
                .aboveLevel(2);

        // 10. LootGate (replacement item vs outright removal + table/entity scoping)
        gates.loot(NekoId.of("test", "skull_to_coal"), "wither_hunter")
                .item(witherSkull)
                .replaceWith(coal);
        gates.loot(NekoId.of("test", "enderman_pearl_deny"), "void_touched")
                .item(enderPearl)
                .fromEntity(endermanEntity);
        gates.loot(NekoId.of("test", "fortress_chest_lock"), "fortress_raider")
                .fromLootTable(fortressChestTable);

        CompiledStageGateSnapshot snapshot = CompiledStageGateSnapshot.compile(
                gates.buildDeclarations(),
                Set.of(),
                Map.of(),
                registries
        );

        StageReadView locked = viewOf();

        // Container assertions
        assertFalse(snapshot.canOpenContainer(enchantingMenu, locked, locked));
        assertNotNull(snapshot.lockedContainerRule(enchantingMenu, locked, locked));
        assertTrue(snapshot.canOpenContainer(enchantingMenu, viewOf("arcane_table"), locked));

        // Dimension assertions
        assertFalse(snapshot.canEnterDimension(netherDim, locked, locked));
        assertNotNull(snapshot.lockedDimensionRule(netherDim, locked, locked));
        assertTrue(snapshot.canEnterDimension(netherDim, viewOf("nether_portal"), locked));

        // Region assertions (box & sphere)
        assertFalse(snapshot.canEnterRegion(overworldDim, 0.0, 70.0, 0.0, locked, locked));
        assertFalse(snapshot.canBreakInRegion(overworldDim, 0.0, 70.0, 0.0, locked, locked));
        assertNotNull(snapshot.lockedRegionRule(overworldDim, 0.0, 70.0, 0.0, locked, locked));
        assertTrue(snapshot.canEnterRegion(overworldDim, 0.0, 70.0, 0.0, viewOf("citadel_key"), locked));

        assertTrue(snapshot.canEnterRegion(overworldDim, 200.0, 70.0, 205.0, locked, locked));
        assertTrue(snapshot.canInteractInRegion(overworldDim, 200.0, 70.0, 205.0, locked, locked));
        assertFalse(snapshot.canBreakInRegion(overworldDim, 200.0, 70.0, 205.0, locked, locked));
        assertFalse(snapshot.canPlaceInRegion(overworldDim, 200.0, 70.0, 205.0, locked, locked));
        assertTrue(snapshot.canBreakInRegion(overworldDim, 200.0, 70.0, 205.0, viewOf("sanctuary_builder"), locked));

        // Entity assertions
        assertTrue(snapshot.canAttackEntity(horseEntity, locked, locked));
        assertTrue(snapshot.canInteractEntity(horseEntity, locked, locked));
        assertFalse(snapshot.canMountEntity(horseEntity, locked, locked));
        assertTrue(snapshot.canMountEntity(horseEntity, viewOf("equestrian"), locked));

        assertFalse(snapshot.canAttackEntity(bossEntity, locked, locked));
        assertFalse(snapshot.canInteractEntity(bossEntity, locked, locked));
        assertNotNull(snapshot.lockedEntityRule(bossEntity, locked, locked));
        assertTrue(snapshot.canAttackEntity(bossEntity, viewOf("boss_slayer"), locked));

        // Effect assertions
        assertFalse(snapshot.canApplyEffect(strengthEffect, locked, locked));
        assertTrue(snapshot.canApplyEffect(strengthEffect, viewOf("alchemy_tier"), locked));

        // Enchant assertions (levels 1..2 allowed when locked; level >= 3 requires master_enchanter)
        assertTrue(snapshot.canUseEnchantment(sharpnessEnchant, 1, locked, locked));
        assertTrue(snapshot.canUseEnchantment(sharpnessEnchant, 2, locked, locked));
        assertFalse(snapshot.canUseEnchantment(sharpnessEnchant, 3, locked, locked));
        assertFalse(snapshot.canUseEnchantment(sharpnessEnchant, 5, locked, locked));
        assertTrue(snapshot.canUseEnchantment(sharpnessEnchant, 5, viewOf("master_enchanter"), locked));

        // Loot assertions (replacement vs deny without replacement vs origin mismatch allow)
        LootDecision skullLocked = snapshot.filterLootItem(witherSkull, null, null, locked, locked);
        assertEquals(LootDecision.deny(coal), skullLocked);
        assertFalse(skullLocked.allowed());
        assertEquals(coal, skullLocked.replacementItem());
        assertEquals(
                LootDecision.ALLOW,
                snapshot.filterLootItem(witherSkull, null, null, viewOf("wither_hunter"), locked)
        );

        LootDecision pearlFromEnderman = snapshot.filterLootItem(enderPearl, null, endermanEntity, locked, locked);
        assertEquals(LootDecision.deny(null), pearlFromEnderman);
        assertFalse(pearlFromEnderman.allowed());
        assertNull(pearlFromEnderman.replacementItem());

        assertEquals(
                LootDecision.ALLOW,
                snapshot.filterLootItem(enderPearl, null, NekoId.of("minecraft", "piglin"), locked, locked),
                "Entity-scoped loot rule must allow drops from unrelated entities"
        );

        LootDecision fortressDrop = snapshot.filterLootItem(
                NekoId.of("minecraft", "gold_ingot"), fortressChestTable, null, locked, locked);
        assertEquals(LootDecision.deny(null), fortressDrop);
        assertEquals(
                LootDecision.ALLOW,
                snapshot.filterLootItem(
                        NekoId.of("minecraft", "gold_ingot"),
                        fortressChestTable,
                        null,
                        viewOf("fortress_raider"),
                        locked
                )
        );
    }

    private static StageReadView viewOf(String... stages) {
        Set<String> active = new LinkedHashSet<>(List.of(stages));
        return new StageReadView() {
            @Override
            public boolean has(String stage) {
                return stage != null && active.contains(stage.trim());
            }

            @Override
            public List<String> getAll() {
                return List.copyOf(active);
            }

            @Override
            public long remainingTicks(String stage) {
                return has(stage) ? -1L : 0L;
            }
        };
    }

    private static final class MutableRegistryAccess implements StageRegistryAccess {
        final Map<NekoId, Set<NekoId>> itemTags = new HashMap<>();
        final Map<NekoId, Set<NekoId>> blockTags = new HashMap<>();
        final Map<NekoId, Set<NekoId>> entityTags = new HashMap<>();
        final Map<String, Set<NekoId>> namespaceItems = new HashMap<>();
        final Map<String, Set<NekoId>> namespaceBlocks = new HashMap<>();
        final Map<String, Set<NekoId>> namespaceEntities = new HashMap<>();

        @Override
        public boolean itemExists(NekoId id) {
            return true;
        }

        @Override
        public boolean blockExists(NekoId id) {
            return true;
        }

        @Override
        public boolean menuExists(NekoId id) {
            return true;
        }

        @Override
        public boolean entityTypeExists(NekoId id) {
            return true;
        }

        @Override
        public boolean effectExists(NekoId id) {
            return true;
        }

        @Override
        public Set<NekoId> expandItemTag(NekoId tagId) {
            return itemTags.getOrDefault(tagId, Set.of());
        }

        @Override
        public Set<NekoId> expandBlockTag(NekoId tagId) {
            return blockTags.getOrDefault(tagId, Set.of());
        }

        @Override
        public Set<NekoId> expandEntityTypeTag(NekoId tagId) {
            return entityTags.getOrDefault(tagId, Set.of());
        }

        @Override
        public Set<NekoId> itemsInNamespace(String namespace) {
            return namespaceItems.getOrDefault(namespace, Set.of());
        }

        @Override
        public Set<NekoId> blocksInNamespace(String namespace) {
            return namespaceBlocks.getOrDefault(namespace, Set.of());
        }

        @Override
        public Set<NekoId> entitiesInNamespace(String namespace) {
            return namespaceEntities.getOrDefault(namespace, Set.of());
        }
    }
}
