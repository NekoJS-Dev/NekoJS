package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.ItemEvents;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import graal.graalvm.polyglot.Context;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
//? if >=26 {
import com.tkisor.nekojs.bindings.event.BlockEvents;
import net.minecraft.world.level.block.Blocks;
//?}

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Ticket39InitialCollectionAtomicityTest {
    private ModificationDomainOwner owner;
    private Context context;

    @BeforeAll
    static void initializePlatform() {
        Ticket39ModificationScriptHarness.ensurePlatformInitialized();
    }

    @BeforeEach
    void setUp() {
        owner = new ModificationDomainOwner();
        context = Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .build();
        ScriptContextRegistry.bind(context, ScriptType.SERVER);
    }

    @AfterEach
    void tearDown() {
        ItemEvents.MODIFICATION.clearTokens(ScriptType.SERVER);
        //? if >=26 {
        BlockEvents.MODIFICATION.clearTokens(ScriptType.SERVER);
        //?}
        try {
            owner.close();
        } finally {
            ScriptContextRegistry.unbind(context);
            context.close();
        }
    }

    @Test
    void activeGuestItemCallbackFailureRejectsStartupCollectionWithoutRegistryAccess() {
        ItemEvents.MODIFICATION.execute(context.eval("js", """
                event => { throw new Error('startup-item-collection-failure'); }
                """));

        owner.applyInitialPlan(null);

        assertRejected("startup-item-collection-failure");
    }

    @Test
    void guestFailureAfterAnItemDeclarationKeepsThePreviouslyAppliedValue() {
        assumeTrue(VanillaRegistryProbe.available(), "vanilla registries unavailable in this JVM");
        ModificationCandidatePlan active = new ModificationCandidatePlan(owner);
        new ItemModificationEventJS(active).modify("minecraft:diamond", item -> item.setMaxStackSize(16));
        active.preflight();
        active.publish();
        ItemEvents.MODIFICATION.execute(context.eval("js", """
                event => {
                  event.modify('minecraft:diamond', item => item.setMaxStackSize(32));
                  throw new Error('startup-partial-item-declaration');
                }
                """));

        owner.applyInitialPlan(null);

        assertRejected("startup-partial-item-declaration");
        assertEquals(16, Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1));
    }

    //? if >=26 {
    @Test
    void activeGuestBlockCallbackFailureRejectsStartupCollectionWithoutRegistryAccess() {
        BlockEvents.MODIFICATION.execute(context.eval("js", """
                event => { throw new Error('startup-block-collection-failure'); }
                """));

        owner.applyInitialPlan(null);

        assertRejected("startup-block-collection-failure");
    }

    @Test
    void blockCallbackFailureRejectsTheAlreadyCollectedItemAndBlockBatch() {
        assumeTrue(VanillaRegistryProbe.available(), "vanilla registries unavailable in this JVM");
        int originalStack = Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
        float originalHardness = Blocks.STONE.defaultBlockState().getDestroySpeed(null, null);
        ItemEvents.MODIFICATION.execute(context.eval("js", """
                event => event.modify('minecraft:diamond', item => item.setMaxStackSize(16))
                """));
        BlockEvents.MODIFICATION.execute(context.eval("js", """
                event => {
                  event.modify('minecraft:stone', block => block.setHardness(8));
                  throw new Error('startup-mixed-batch-failure');
                }
                """));

        owner.applyInitialPlan(null);

        assertRejected("startup-mixed-batch-failure");
        assertEquals(originalStack, Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1));
        assertEquals(originalHardness, Blocks.STONE.defaultBlockState().getDestroySpeed(null, null));
    }
    //?}

    private void assertRejected(String marker) {
        assertEquals(ModificationDomainOwner.Outcome.RECOVERY_FAILED, owner.lastDiagnostics().outcome());
        assertEquals("startup-dispatch", owner.lastDiagnostics().source());
        assertTrue(owner.lastDiagnostics().detail().contains(marker), owner.lastDiagnostics().toString());
    }
}
