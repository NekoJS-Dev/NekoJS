package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import com.tkisor.nekojs.core.modification.ModificationDeclaration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ticket39UnsupportedBlockTest {
    @Test
    void unsupportedBlockDeclarationIsExplicitlyRejected() {
        ModificationDomainOwner owner = new ModificationDomainOwner();
        try {
            ModificationCandidatePlan plan = new ModificationCandidatePlan(owner);
            plan.add(new ModificationDeclaration("block", "minecraft:stone", Map.of("hardness", 2.0f), null));
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, plan::preflight);
            assertTrue(failure.getMessage().contains("block is 26.x only"));
            assertEquals(ModificationDomainOwner.Outcome.INITIAL, owner.lastDiagnostics().outcome());
        } finally {
            owner.close();
        }
    }
}
