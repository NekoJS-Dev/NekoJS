package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import com.tkisor.nekojs.core.modification.ModificationDeclaration;
import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import graal.graalvm.polyglot.Context;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
//? if >=26 {
import net.minecraft.world.level.block.Blocks;
//?}

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Ticket39SameTargetReplacementTest {
    private final ModificationDomainOwner owner = new ModificationDomainOwner();

    @AfterEach
    void restoreBaselines() {
        owner.close();
    }

    @Test
    void laterItemDeclarationReplacesTheEarlierPropertiesFromBaseline() {
        assumeTrue(VanillaRegistryProbe.available(), "vanilla registries unavailable in this JVM");
        int originalStack = Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
        Rarity originalRarity = Items.DIAMOND.components().getOrDefault(DataComponents.RARITY, Rarity.COMMON);
        ModificationCandidatePlan plan = new ModificationCandidatePlan(owner);
        try (Context context = newContext()) {
            context.eval("js", """
                    event => {
                      event.modify('minecraft:diamond', item => {
                        item.setMaxStackSize(16);
                        item.setRarity('epic');
                      });
                      event.modify('minecraft:diamond', item => item.setMaxStackSize(32));
                    }
                    """).executeVoid(new ItemModificationEventJS(plan));
        }

        plan.preflight();
        plan.publish();

        assertEquals(32, Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1));
        assertEquals(originalRarity, Items.DIAMOND.components().getOrDefault(DataComponents.RARITY, Rarity.COMMON));
        new ModificationCandidatePlan(owner).publish();
        assertEquals(originalStack, Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1));
    }

    //? if >=26 {
    @Test
    void laterBlockDeclarationReplacesAllEarlierPropertiesInAMixedBatch() {
        assumeTrue(VanillaRegistryProbe.available(), "vanilla registries unavailable in this JVM");
        BlockModificationJS baseline = new BlockModificationJS(Blocks.STONE);
        float originalHardness = baseline.getHardness();
        float originalResistance = baseline.getResistance();
        boolean originalRequiresTool = baseline.getRequiresTool();
        float originalFriction = baseline.getFriction();
        float originalJumpFactor = baseline.getJumpFactor();
        int originalLightLevel = baseline.getLightLevel();
        int originalStack = Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
        ModificationCandidatePlan plan = new ModificationCandidatePlan(owner);
        try (Context context = newContext()) {
            context.eval("js", """
                    event => event.modify('minecraft:diamond', item => item.setMaxStackSize(16))
                    """).executeVoid(new ItemModificationEventJS(plan));
            context.eval("js", """
                    event => {
                      event.modify('minecraft:stone', block => {
                        block.setHardness(2);
                        block.setResistance(8);
                        block.setRequiresTool(false);
                        block.setFriction(0.9);
                        block.setJumpFactor(2);
                      });
                      event.modify('minecraft:stone', block => block.setLightLevel(7));
                    }
                    """).executeVoid(new BlockModificationEventJS(plan));
        }

        plan.preflight();
        plan.publish();

        BlockModificationJS applied = new BlockModificationJS(Blocks.STONE);
        assertEquals(originalHardness, applied.getHardness());
        assertEquals(originalHardness, Blocks.STONE.defaultDestroyTime());
        assertEquals(originalResistance, applied.getResistance());
        assertEquals(originalRequiresTool, applied.getRequiresTool());
        assertEquals(originalFriction, applied.getFriction());
        assertEquals(originalJumpFactor, applied.getJumpFactor());
        assertEquals(7, applied.getLightLevel());
        assertEquals(16, Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1));
        new ModificationCandidatePlan(owner).publish();
        assertEquals(originalLightLevel, new BlockModificationJS(Blocks.STONE).getLightLevel());
        assertEquals(originalStack, Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1));
    }

    @Test
    void laterBlockDeclarationRestoresTheOriginalPerStateLightFunction() {
        assumeTrue(VanillaRegistryProbe.available(), "vanilla registries unavailable in this JVM");
        List<Integer> originalLight = lampLightValues();
        ModificationCandidatePlan plan = new ModificationCandidatePlan(owner);
        new BlockModificationEventJS(plan).modify("minecraft:redstone_lamp", block -> block.setLightLevel(7));
        new BlockModificationEventJS(plan).modify("minecraft:redstone_lamp", block -> block.setHardness(2));

        plan.preflight();
        plan.publish();

        assertEquals(originalLight, lampLightValues());
        assertEquals(2.0f, new BlockModificationJS(Blocks.REDSTONE_LAMP).getHardness());
    }

    @Test
    void rejectedMixedBatchLeavesBothPreviouslyAppliedTargetsUnchanged() {
        assumeTrue(VanillaRegistryProbe.available(), "vanilla registries unavailable in this JVM");
        ModificationCandidatePlan active = new ModificationCandidatePlan(owner);
        new ItemModificationEventJS(active).modify("minecraft:diamond", item -> item.setMaxStackSize(16));
        new BlockModificationEventJS(active).modify("minecraft:stone", block -> block.setHardness(3));
        active.preflight();
        active.publish();
        ModificationCandidatePlan rejected = new ModificationCandidatePlan(owner);
        new ItemModificationEventJS(rejected).modify("minecraft:diamond", item -> item.setMaxStackSize(32));
        new BlockModificationEventJS(rejected).modify("minecraft:stone", block -> block.setHardness(8));
        new BlockModificationEventJS(rejected).modify("minecraft:stone", block -> block.setLightLevel(16));

        assertThrows(IllegalArgumentException.class, rejected::preflight);

        assertEquals(16, Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1));
        assertEquals(3.0f, new BlockModificationJS(Blocks.STONE).getHardness());
    }

    private static List<Integer> lampLightValues() {
        return Blocks.REDSTONE_LAMP.getStateDefinition().getPossibleStates().stream()
                .map(state -> state.getLightEmission()).toList();
    }
    //?}

    private static Context newContext() {
        return Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .build();
    }
}
