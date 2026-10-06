//? if neoforge {
package com.tkisor.nekojs.wrapper.event.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityRegistryEventJSTest {
    private static final AtomicInteger IDS = new AtomicInteger();

    private static Identifier capabilityId() {
        return Identifier.fromNamespaceAndPath("nekojs_test", "capability_" + IDS.incrementAndGet());
    }

    private static final class Fixture {
        final Map<String, Object> targets = new HashMap<>();
        final List<CapabilityRegistrationPlan.Registration> installed = new ArrayList<>();
        final CapabilityRegistrationPlan plan = new CapabilityRegistrationPlan(
                (scope, id) -> targets.get(scope + "/" + id));
        final CapabilityRegistryEventJS event = new CapabilityRegistryEventJS(plan);

        Object add(CapabilityRegistrationPlan.Scope scope, String id) {
            Object target = new Object();
            targets.put(scope + "/" + id, target);
            return target;
        }

        void commit() {
            plan.commit(installed::add);
        }
    }

    @Test
    void collectionIsLazyAndProviderReceivesNativeDirectionOnlyAfterCommit() {
        Fixture fixture = new Fixture();
        fixture.add(CapabilityRegistrationPlan.Scope.BLOCK_ENTITY, "minecraft:chest");
        BlockCapability<String, Direction> capability = BlockCapability.createSided(capabilityId(), String.class);
        AtomicInteger calls = new AtomicInteger();
        fixture.event.registerBlockEntityNative("minecraft:chest", capability, (blockEntity, side) -> {
            calls.incrementAndGet();
            return side == null ? "unsided" : side.getName();
        });
        assertEquals(0, calls.get());
        assertTrue(fixture.installed.isEmpty());
        fixture.commit();
        assertEquals(0, calls.get());
        var installed = fixture.installed.get(0);
        assertSame(capability, installed.capability());
        assertEquals("north", installed.provider().apply(null, Direction.NORTH));
        assertEquals("unsided", installed.provider().apply(null, null));
        assertEquals(2, calls.get());
    }

    @Test
    void customEntityAndItemCapabilitiesPreserveContextAndNullRejection() {
        Fixture fixture = new Fixture();
        fixture.add(CapabilityRegistrationPlan.Scope.ENTITY, "minecraft:player");
        fixture.add(CapabilityRegistrationPlan.Scope.ITEM, "minecraft:stick");
        EntityCapability<String, String> entityCapability = EntityCapability.create(capabilityId(), String.class, String.class);
        ItemCapability<String, String> itemCapability = ItemCapability.create(capabilityId(), String.class, String.class);
        fixture.event.registerEntityNative("minecraft:player", entityCapability,
                (entity, context) -> "entity:" + context);
        fixture.event.registerItemNative("minecraft:stick", itemCapability,
                (stack, context) -> "allowed".equals(context) ? "item" : null);
        fixture.commit();
        assertEquals("entity:custom", fixture.installed.get(0).provider().apply(null, "custom"));
        assertEquals("item", fixture.installed.get(1).provider().apply(null, "allowed"));
        assertNull(fixture.installed.get(1).provider().apply(null, "denied"));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.installed.get(0).provider().apply(null, Direction.NORTH));
    }

    @Test
    void standardRegistrationsSelectNativeScopeSpecificCapabilities() {
        Fixture fixture = new Fixture();
        fixture.add(CapabilityRegistrationPlan.Scope.BLOCK_ENTITY, "minecraft:chest");
        fixture.add(CapabilityRegistrationPlan.Scope.ENTITY, "minecraft:player");
        fixture.add(CapabilityRegistrationPlan.Scope.ITEM, "minecraft:stick");
        fixture.event.registerBlockEntity("minecraft:chest", " ITEM ", () -> null);
        fixture.event.registerEntity("minecraft:player", "item", (entity, context) -> null);
        fixture.event.registerEntity("minecraft:player", "item_automation", (entity, context) -> null);
        fixture.event.registerEntity("minecraft:player", "energy", (entity, context) -> null);
        fixture.event.registerEntity("minecraft:player", "fluid", (entity, context) -> null);
        fixture.event.registerItem("minecraft:stick", "item", (stack, context) -> null);
        fixture.event.registerItem("minecraft:stick", "energy", (stack, context) -> null);
        fixture.event.registerItem("minecraft:stick", "fluid", (stack, context) -> null);
        fixture.commit();
        assertEquals(8, fixture.installed.size());
        assertEquals(void.class, fixture.installed.get(1).capability().contextClass());
        assertEquals(Direction.class, fixture.installed.get(2).capability().contextClass());
        assertEquals(Direction.class, fixture.installed.get(3).capability().contextClass());
        assertEquals(Direction.class, fixture.installed.get(4).capability().contextClass());
        assertTrue(fixture.installed.get(5).capability() instanceof ItemCapability<?, ?>);
        for (var registration : fixture.installed) {
            assertNull(registration.provider().apply(null, null));
        }
    }

    @Test
    void legacySupplierIsInvokedPerQueryAndMayReturnAnOwnerManagedInstance() {
        Fixture fixture = new Fixture();
        fixture.add(CapabilityRegistrationPlan.Scope.BLOCK_ENTITY, "minecraft:chest");
        AtomicInteger calls = new AtomicInteger();
        fixture.event.registerBlockEntity("minecraft:chest", "energy", () -> {
            calls.incrementAndGet();
            return null;
        });
        fixture.commit();
        fixture.installed.get(0).provider().apply(null, null);
        fixture.installed.get(0).provider().apply(null, Direction.SOUTH);
        assertEquals(2, calls.get(), "the legacy provider must not be eagerly cached or globally replaced");
    }

    @Test
    void lookupAndDuplicateFailuresAreRejectedDuringCollection() {
        Fixture fixture = new Fixture();
        fixture.add(CapabilityRegistrationPlan.Scope.ITEM, "minecraft:stick");
        ItemCapability<String, Void> capability = ItemCapability.createVoid(capabilityId(), String.class);
        fixture.event.registerItemNative("stick", capability, (stack, context) -> "value");
        assertThrows(IllegalArgumentException.class,
                () -> fixture.event.registerItemNative("minecraft:stick", capability, (stack, context) -> "other"));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.event.registerItemNative("missing:absent", capability, (stack, context) -> "other"));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.event.registerItemNative("", capability, (stack, context) -> "other"));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.event.registerItemNative("#minecraft:sticks", capability, (stack, context) -> "other"));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.event.registerItemNative("minecraft:stick", null, (stack, context) -> "other"));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.event.registerItemNative("minecraft:stick", capability, null));
        fixture.commit();
        assertEquals(1, fixture.installed.size());
    }

    @Test
    void failureCannotRetryAlreadyInstalledProvidersAndTheirQueriesStayDisabled() {
        Fixture fixture = new Fixture();
        fixture.add(CapabilityRegistrationPlan.Scope.ITEM, "minecraft:stick");
        fixture.add(CapabilityRegistrationPlan.Scope.ENTITY, "minecraft:player");
        fixture.event.registerItemNative("minecraft:stick", ItemCapability.createVoid(capabilityId(), String.class),
                (stack, context) -> "item");
        fixture.event.registerEntityNative("minecraft:player", EntityCapability.createVoid(capabilityId(), String.class),
                (entity, context) -> "entity");
        IllegalStateException cause = new IllegalStateException("native registration failed");
        assertSame(cause, assertThrows(IllegalStateException.class, () -> fixture.plan.commit(registration -> {
            if (!fixture.installed.isEmpty()) {
                throw cause;
            }
            fixture.installed.add(registration);
        })));
        assertEquals(1, fixture.installed.size());
        assertThrows(IllegalStateException.class, fixture::commit);
        assertThrows(IllegalStateException.class,
                () -> fixture.installed.get(0).provider().apply(null, null));
        assertThrows(IllegalStateException.class,
                () -> fixture.event.registerItem("minecraft:stick", "energy", (stack, context) -> null));
    }

    @Test
    void successfulCommitRejectsLateRegistrationAndSecondCommit() {
        Fixture fixture = new Fixture();
        fixture.commit();
        assertThrows(IllegalStateException.class, fixture::commit);
        assertThrows(IllegalStateException.class,
                () -> fixture.event.registerItem("minecraft:stick", "item", (stack, context) -> null));
    }

    @Test
    void providerFailureKeepsCauseAndTypeValidationRunsOnInstalledQuery() {
        Fixture fixture = new Fixture();
        fixture.add(CapabilityRegistrationPlan.Scope.ITEM, "minecraft:stick");
        ItemCapability<String, Void> failureCapability = ItemCapability.createVoid(capabilityId(), String.class);
        RuntimeException cause = new IllegalArgumentException("provider failure");
        fixture.event.registerItemNative("minecraft:stick", failureCapability, (stack, context) -> { throw cause; });
        ItemCapability<String, Void> badResultCapability = ItemCapability.createVoid(capabilityId(), String.class);
        fixture.plan.collect(CapabilityRegistrationPlan.Scope.ITEM, "minecraft:stick", badResultCapability,
                (owner, context) -> Integer.valueOf(42));
        fixture.commit();
        assertSame(cause, assertThrows(IllegalStateException.class,
                () -> fixture.installed.get(0).provider().apply(null, null)).getCause());
        assertTrue(assertThrows(IllegalStateException.class,
                () -> fixture.installed.get(1).provider().apply(null, null)).getMessage().startsWith("[NEKO-4010]"));
    }

    @Test
    void plainBlockProviderReceivesPositionAndNativeContext() {
        Fixture fixture = new Fixture();
        fixture.add(CapabilityRegistrationPlan.Scope.BLOCK, "minecraft:stone");
        BlockCapability<String, Direction> capability = BlockCapability.createSided(capabilityId(), String.class);
        fixture.event.registerBlockNative("minecraft:stone", capability,
                (level, pos, state, blockEntity, side) -> pos.getX() + ":" + (side == null ? "none" : side.getName()));
        fixture.commit();
        var query = new CapabilityRegistrationPlan.BlockQuery(null, new BlockPos(7, 8, 9), null, null);
        assertEquals("7:north", fixture.installed.get(0).provider().apply(query, Direction.NORTH));
        assertEquals("7:none", fixture.installed.get(0).provider().apply(query, null));
    }

    @Test
    void guestScriptRegistersAndQueriesThirdPartyCapabilitiesThroughPublishedMethods() {
        Fixture fixture = new Fixture();
        fixture.add(CapabilityRegistrationPlan.Scope.ITEM, "minecraft:stick");
        ItemCapability<String, String> capability = ItemCapability.create(capabilityId(), String.class, String.class);
        try (Context context = Context.newBuilder("js").allowHostAccess(HostAccess.ALL).build()) {
            context.getBindings("js").putMember("event", fixture.event);
            context.getBindings("js").putMember("nativeCapability", capability);
            context.eval("js", "event.registerItemNative('minecraft:stick', nativeCapability, (stack, query) => query === 'deny' ? null : 'guest:' + query)");
            fixture.commit();
            assertEquals("guest:mod-context", fixture.installed.get(0).provider().apply(null, "mod-context"));
            assertNull(fixture.installed.get(0).provider().apply(null, "deny"));
        }
    }

    @Test
    void differentOwnersAreNotReplacedWithFreshOrWorldSharedStorage() {
        Fixture fixture = new Fixture();
        fixture.add(CapabilityRegistrationPlan.Scope.ITEM, "minecraft:stick");
        ItemCapability<Object, Void> capability = ItemCapability.createVoid(capabilityId(), Object.class);
        Object firstOwner = new Object();
        Object secondOwner = new Object();
        fixture.plan.collect(CapabilityRegistrationPlan.Scope.ITEM, "minecraft:stick", capability,
                (owner, context) -> owner);
        fixture.commit();
        var provider = fixture.installed.get(0).provider();
        assertSame(firstOwner, provider.apply(firstOwner, null));
        assertSame(firstOwner, provider.apply(firstOwner, null));
        assertSame(secondOwner, provider.apply(secondOwner, null));
    }
}
//?}
