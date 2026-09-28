//? if neoforge {
package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code bindCancellable} fixture (ticket 24 D4): a cancellable family bus whose native
 * event does not implement {@code ICancellableEvent} must route the script-side cancel
 * result through the adapter-supplied cancel action. Production user:
 * {@code EntityEvents.DAMAGE_PRE}, where cancelling maps to
 * {@code LivingDamageEvent.Pre#setNewDamage(0)} — the zero-damage lever, with the native
 * damage sequence still completing.
 */
class EventBusForgeBridgeCancelActionTest {

    /** Stand-in for a native event with a non-ICancellableEvent cancel lever (damage-like). */
    public static class DamageLikeEvent extends Event {
        float newDamage = 5.0f;

        void setNewDamage(float newDamage) {
            this.newDamage = newDamage;
        }
    }

    @BeforeAll
    static void initPlatformStub() {
        try {
            Platform.init(new StubPlatform());
        } catch (IllegalStateException alreadyInitialized) {
            // same-JVM reuse of an already initialized platform stub is fine
        }
    }

    @Test
    void scriptCancellationIsAppliedThroughTheCancelAction() {
        IEventBus forgeBus = BusBuilder.builder().build();
        EventBusJS<DamageLikeEvent, Object> busJS = EventBusJS.of(DamageLikeEvent.class, true);
        ((CancellableEventBus<DamageLikeEvent>) busJS.bus())
                .listen((Predicate<DamageLikeEvent>) event -> true);

        EventBusForgeBridge.create(forgeBus).bindCancellable(busJS, event -> event.setNewDamage(0));

        DamageLikeEvent nativeEvent = new DamageLikeEvent();
        forgeBus.post(nativeEvent);

        assertEquals(0.0f, nativeEvent.newDamage,
                "a cancelled script bus must reach the native zero-damage lever (D4: silent no-op otherwise)");
    }

    @Test
    void cancelActionDoesNotRunWhenScriptsDoNotCancel() {
        IEventBus forgeBus = BusBuilder.builder().build();
        EventBusJS<DamageLikeEvent, Object> busJS = EventBusJS.of(DamageLikeEvent.class, true);
        ((CancellableEventBus<DamageLikeEvent>) busJS.bus())
                .listen((Predicate<DamageLikeEvent>) event -> false);

        EventBusForgeBridge.create(forgeBus).bindCancellable(busJS, event -> event.setNewDamage(0));

        DamageLikeEvent nativeEvent = new DamageLikeEvent();
        forgeBus.post(nativeEvent);

        assertEquals(5.0f, nativeEvent.newDamage,
                "without script cancellation the native damage value passes through unchanged");
    }

    @Test
    void bindingANonCancellableBusFailsFastInsteadOfSilentlyIgnoringCancels() {
        IEventBus forgeBus = BusBuilder.builder().build();
        EventBusJS<DamageLikeEvent, Object> plainBus = EventBusJS.of(DamageLikeEvent.class, false);

        assertThrows(IllegalArgumentException.class,
                () -> EventBusForgeBridge.create(forgeBus).bindCancellable(plainBus, event -> event.setNewDamage(0)),
                "a non-cancellable bus can never report cancellation; the binding must fail at setup");
    }

    private static final class StubPlatform implements IPlatform {
        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return true; }
        @Override public String getMcVersion() { return "test"; }
        @Override public Path getGameDir() { return com.tkisor.nekojs.TestGameDirs.unique("nekojs-bridge-cancel"); }
        @Override public Map<String, IModInfo> getMods() { return Map.of(); }
        @Override public IModInfo getInfo(String modID) { return null; }
        @Override public String getLoaderId() { return "test"; }
        @Override public String getLoaderVersion() { return "0"; }
    }
}
//?}
