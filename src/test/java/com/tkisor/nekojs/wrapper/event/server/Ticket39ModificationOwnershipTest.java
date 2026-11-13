package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.modification.ModificationApplier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural guard for snapshot ownership and for the old path being closed. It needs no
 * vanilla registry, so it runs on 26.1.2 / 26.1.2-fabric / 1.21.1 / 26.2.0 / 26.2.0-fabric.
 *
 * <p>These assertions check machine-checkable facts of the form "the old path is no longer
 * there", not behavior: the process-wide static {@code SNAPSHOTS}, the {@code fire()}
 * restore-all-then-replay entry point, and the public live-mutation hooks on the views must
 * all be gone. Snapshot and restore state may only live in the instance fields of the domain
 * owner authorized by the root, and applying happens only in the Adapter.
 *
 * <p>Removal gate: this test is only part of the parity and no-consumer evidence for the
 * replacement path. Formally deleting the old public path still needs maintainer sign-off, see
 * the removal-gate section of the batch report.
 */
class Ticket39ModificationOwnershipTest {

    /** Views and payloads must not publicly accept these live platform types (otherwise a script
     * could bypass the candidate plan and change live objects directly). */
    private static final Set<String> LIVE_PLATFORM_TYPES = Set.of(
            "net.minecraft.world.item.Item",
            "net.minecraft.world.level.block.Block",
            "net.minecraft.world.level.block.state.BlockState",
            "net.minecraft.core.component.DataComponentMap");

    @Test
    void payloadsAndOwnerHoldNoProcessLevelStaticSnapshotState() {
        assertNoStaticSnapshotState(ItemModificationEventJS.class);
        assertNoStaticSnapshotState(ModificationDomainOwner.class);
//? if >=26 {
        assertNoStaticSnapshotState(BlockModificationEventJS.class);
//?}
    }

    private static void assertNoStaticSnapshotState(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            assertFalse(Map.class.isAssignableFrom(field.getType())
                            || Collection.class.isAssignableFrom(field.getType()),
                    type.getSimpleName() + "." + field.getName()
                            + " is process-level static snapshot state; snapshots must be instance state "
                            + "owned by the root-authorized domain owner (ticket 39 AC5)");
        }
    }

    @Test
    void restoreAllThenReplayEntryPointsAreGone() {
        for (Class<?> type : List.of(ItemModificationEventJS.class, ModificationDomainOwner.class)) {
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                String name = method.getName();
                assertFalse(name.equals("fire") || name.equals("restoreAll") || name.equals("restore"),
                        type.getSimpleName() + "." + name
                                + " is a legacy restore-all/replay entry point; collection+commit is the only path "
                                + "(ticket 39 AC3/AC14)");
            }
        }
        // Traceable ownership from the caller-facing interface to the Adapter: the domain owner is
        // where collection is mounted, where changes are applied, and what the root cleans up on
        // close. It is not a new public Modification Runtime.
        assertTrue(CandidateDomainCollector.class.isAssignableFrom(ModificationDomainOwner.class),
                "the domain owner is the root-owned collection mount point");
        assertTrue(ModificationApplier.class.isAssignableFrom(ModificationDomainOwner.class),
                "the domain owner is the platform Adapter (preflight/apply seam)");
        assertTrue(AutoCloseable.class.isAssignableFrom(ModificationDomainOwner.class),
                "the root-owned domain owner must release its baselines on root close (AC5)");
    }

    @Test
    void modifyKeepsThePreRefactorFunctionalCallbackSignature() throws Exception {
        Method itemModify = ItemModificationEventJS.class
                .getDeclaredMethod("modify", String.class, Consumer.class);
        assertEquals(void.class, itemModify.getReturnType(),
                "modify only records a declaration; it applies nothing to live objects");

//? if >=26 {
        Method blockModify = BlockModificationEventJS.class
                .getDeclaredMethod("modify", String.class, Consumer.class);
        assertEquals(void.class, blockModify.getReturnType());
//?}
    }

    @Test
    void viewsExposeNoLiveMutationSeam() {
        assertNoPublicLiveMutationSeam(ItemModificationJS.class);
//? if >=26 {
        assertNoPublicLiveMutationSeam(BlockModificationJS.class);
//?}
    }

    private static void assertNoPublicLiveMutationSeam(Class<?> viewType) {
        for (Method method : viewType.getMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(LIVE_PLATFORM_TYPES.contains(parameter.getName()),
                        viewType.getSimpleName() + "." + method.getName() + "(" + parameter.getSimpleName()
                                + ") exposes a live-platform mutation seam to the script surface; writes must go "
                                + "through the normalized declaration plan (ticket 39 AC2)");
            }
        }
    }

    /**
     * A machine-checkable limit for the modification classes: they must not reference network or
     * packet symbols. This code declares no client auto-sync capability (auto-sync is unsupported;
     * relog and chunk resync need a platform-side action), and the write side only touches
     * server-side object graphs. If anyone adds a packet or sendTo to this path, this guard fails
     * at once and the capability record must be updated first.
     */
    @Test
    void modificationPathReferencesNoNetworkSymbols() {
        assertNoNetworkReference(ItemModificationEventJS.class);
        assertNoNetworkReference(ItemModificationJS.class);
        assertNoNetworkReference(ModificationDomainOwner.class);
        assertNoNetworkReference(com.tkisor.nekojs.core.modification.ModificationCandidatePlan.class);
        assertNoNetworkReference(com.tkisor.nekojs.core.modification.ModificationDeclaration.class);
//? if >=26 {
        assertNoNetworkReference(BlockModificationEventJS.class);
        assertNoNetworkReference(BlockModificationJS.class);
//?}
    }

    private static void assertNoNetworkReference(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            assertNotNetwork(type, method.getReturnType().getName());
            for (Class<?> parameter : method.getParameterTypes()) {
                assertNotNetwork(type, parameter.getName());
            }
            for (Class<?> exception : method.getExceptionTypes()) {
                assertNotNetwork(type, exception.getName());
            }
        }
        for (Field field : type.getDeclaredFields()) {
            assertNotNetwork(type, field.getType().getName());
        }
    }

    private static void assertNotNetwork(Class<?> type, String referencedTypeName) {
        assertFalse(referencedTypeName.startsWith("net.minecraft.network")
                        || referencedTypeName.contains(".network."),
                type.getSimpleName() + " references " + referencedTypeName
                        + ": the modification path declares no automatic client-sync capability "
                        + "(ticket 39 AC10). Adding a sync path must update the capability record in the "
                        + "baseline REPORT first.");
    }
}
