//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.CancellableEventBus;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventListenerToken;
import com.tkisor.nekojs.eventbus.CommonPriority;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 24 D4 fixture: script-side cancellation of {@code EntityEvents.damagePre} must be a
 * real lever, not a silent no-op. NeoForge's native {@code LivingDamageEvent.Pre} does not
 * implement {@code ICancellableEvent} (checked on 21.1.227 / 26.1.2.71 / 26.2.0.57), so the
 * family declares the bus explicitly cancellable and the FORGE_BRIDGE maps the bus's cancel
 * result to {@code setNewDamage(0)} — the documented zero-damage lever; the native damage
 * sequence itself still completes (damagePost keeps firing with zero damage).
 *
 * <p>This test pins the bus-side half on the real family bus: the bus stays cancellable under
 * the production predicate, and a cancelling listener short-circuits and is observable from
 * {@code post}. The bridge half (cancel result applied through the adapter-supplied action)
 * lives in {@code EventBusForgeBridgeCancelActionTest}; the real-machine health leg was
 * evidenced by the 2026-09-28 maintainer session (damage still applied after return true).
 */
class Ticket24DamagePreCancelSemanticsTest {

    @BeforeAll
    static void initPlatformStub() {
        try {
            Platform.init(new StubPlatform());
        } catch (IllegalStateException alreadyInitialized) {
            // same-JVM reuse of an already initialized platform stub is fine
        }
    }

    @BeforeEach
    void initCancellabilityPredicate() {
        // production init order: the predicate precedes any family class-init; with the D4
        // fix the damagePre bus is cancellable regardless (explicit EventBusJS.of(type, true))
        EventBusJS.setExternalCancellabilityPredicate(
                net.neoforged.bus.api.ICancellableEvent.class::isAssignableFrom);
    }

    @Test
    void damagePreBusIsCancellableDespiteNonCancellableNativeEvent() {
        assertTrue(EntityEvents.DAMAGE_PRE.canCancel(),
                "damagePre must be cancellable even though LivingDamageEvent.Pre"
                        + " does not implement ICancellableEvent (D4: silent no-op otherwise)");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void damagePreCancellationShortCircuitsAndIsObservableOnPost() {
        List<String> order = new ArrayList<>();
        EventBusJS<Object, Object> bus = (EventBusJS) EntityEvents.DAMAGE_PRE;
        CancellableEventBus<Object> cancellable = (CancellableEventBus<Object>) bus.bus();
        List<EventListenerToken<Object>> tokens = new ArrayList<>();
        try {
            tokens.add(cancellable.listen(CommonPriority.HIGH, event -> {
                order.add("cancel");
                return true;
            }));
            tokens.add(cancellable.listen(event -> {
                order.add("after");
                return false;
            }));

            assertTrue(bus.post(new Object()),
                    "a cancelling damagePre listener must report cancellation from post");
            assertEquals(List.of("cancel"), order,
                    "listeners after the cancelling damagePre one are not run");
        } finally {
            for (EventListenerToken<Object> token : tokens) {
                cancellable.unregister(token);
            }
            EntityEvents.GROUP.clearListeners(ScriptType.SERVER);
        }
    }

    private static final class StubPlatform implements IPlatform {
        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return true; }
        @Override public String getMcVersion() { return "test"; }
        @Override public Path getGameDir() { return com.tkisor.nekojs.TestGameDirs.unique("nekojs-ticket24-damagepre"); }
        @Override public Map<String, IModInfo> getMods() { return Map.of(); }
        @Override public IModInfo getInfo(String modID) { return null; }
        @Override public String getLoaderId() { return "test"; }
        @Override public String getLoaderVersion() { return "0"; }
    }
}
//?}
