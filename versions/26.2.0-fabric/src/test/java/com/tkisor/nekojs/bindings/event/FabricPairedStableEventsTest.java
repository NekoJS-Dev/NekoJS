// Fabric node-local paired stable/versioned event verification. Both Fabric nodes carry a copy of
// this test in their node-local test tree so it runs exclusively against the Fabric classpath.
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.TestGameDirs;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingExposure;
import com.tkisor.nekojs.api.event.CommonStableEvents;
import com.tkisor.nekojs.api.event.DispatchEventBus;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventExposure;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.event.ModuleEventGroup;
import com.tkisor.nekojs.core.api.ModulePublicTypes;
import com.tkisor.nekojs.fabric.FabricCorePlugin;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end verification for {@code nekojs:event/stable} and {@code nekojs:event/versioned}
 * publications across all 8 built-in event families on Fabric.
 */
class FabricPairedStableEventsTest {

    private static final Set<String> PAIRED_FAMILIES = Set.of(
            "ServerEvents",
            "LevelEvents",
            "PlayerEvents",
            "CommandEvents",
            "BlockEvents",
            "ItemEvents",
            "EntityEvents",
            "ClientEvents"
    );

    private static final Set<String> NATIVE_FABRIC_CLIENT_EVENTS = Set.of(
            "tickPre",
            "tickPost",
            "loggedIn",
            "loggedOut",
            "commandRegistry"
    );

    private static final Map<String, Set<String>> STABLE_ONLY_MIXIN_FALLBACKS = Map.of(
            "CommandEvents", Set.of("command"),
            "BlockEvents", Set.of("entityMultiPlaced", "toolModification"),
            "EntityEvents", Set.of(
                    "useItemStarted",
                    "useItemStopped",
                    "useItemFinished",
                    "useItemTick"),
            "ClientEvents", Set.of(
                    "playerTickPre",
                    "playerTickPost",
                    "interactionKey",
                    "cloned",
                    "registerKeyMappings",
                    "registerMenuScreens",
                    "registerRenderers",
                    "registerParticleProviders")
    );

    private static EventGroupRegistry buildRegistry() {
        try {
            Platform.init(new IPlatform() {
                @Override public boolean isClient() { return false; }
                @Override public boolean isDevelopment() { return true; }
                @Override public String getMcVersion() { return "test"; }
                @Override public Path getGameDir() {
                    return TestGameDirs.unique("nekojs-fabric-paired-stable-events");
                }
                @Override public Map<String, IModInfo> getMods() { return Map.of(); }
                @Override public IModInfo getInfo(String modID) { return null; }
                @Override public String getLoaderId() { return "test"; }
                @Override public String getLoaderVersion() { return "0"; }
            });
        } catch (IllegalStateException alreadyInitialized) {
            // Reuse an already-initialized platform in the shared test JVM.
        }
        EventGroupRegistry registry = new EventGroupRegistry.Impl().scopedTo("nekojs");
        FabricCorePlugin plugin = new FabricCorePlugin();
        plugin.registerEvents(registry);
        plugin.registerClientEvents(registry);
        return registry;
    }

    @Test
    void matchesCanonicalCommonStableEventsContract() {
        EventGroupRegistry registry = buildRegistry();
        assertEquals(List.of(), CommonStableEvents.validate(registry.moduleGroups()));
    }

    @Test
    void nativeFabricEventsPublishVersionedWhileMixinFallbacksAreStableOnly() {
        EventGroupRegistry registry = buildRegistry();
        List<ModuleEventGroup> modules = registry.moduleGroups();

        for (String family : PAIRED_FAMILIES) {
            ModuleEventGroup versioned = findPublication(modules, BindingExposure.Category.VERSION, family);
            assertNotNull(versioned, () -> "Missing VERSION publication for " + family);
            assertEquals("nekojs:event/versioned", versioned.moduleId());
            assertFalse(versioned.events().isEmpty(), () -> "Empty VERSION events for " + family);

            ModuleEventGroup stable = findPublication(modules, BindingExposure.Category.STABLE, family);
            assertNotNull(stable, () -> "Missing STABLE publication for " + family);
            assertEquals("nekojs:event/stable", stable.moduleId());
            assertFalse(stable.events().isEmpty(), () -> "Empty STABLE events for " + family);
        }

        Set<String> stableFamilies = modules.stream()
                .filter(module -> module.category() == BindingExposure.Category.STABLE)
                .map(module -> module.group().name())
                .collect(Collectors.toSet());
        assertEquals(PAIRED_FAMILIES, stableFamilies,
                "Fabric must publish exactly the 8 canonical STABLE event families");

        ModuleEventGroup clientVersioned = findPublication(modules, BindingExposure.Category.VERSION, "ClientEvents");
        ModuleEventGroup clientStable = findPublication(modules, BindingExposure.Category.STABLE, "ClientEvents");
        assertTrue(clientVersioned.events().containsAll(NATIVE_FABRIC_CLIENT_EVENTS),
                "Fabric-API-native client events must be published in ClientEvents VERSION");
        assertTrue(clientStable.events().containsAll(NATIVE_FABRIC_CLIENT_EVENTS),
                "Fabric-API-native client events must be published in ClientEvents STABLE");

        for (String family : PAIRED_FAMILIES) {
            ModuleEventGroup versioned = findPublication(modules, BindingExposure.Category.VERSION, family);
            ModuleEventGroup stable = findPublication(modules, BindingExposure.Category.STABLE, family);
            Set<String> expectedStableOnly = STABLE_ONLY_MIXIN_FALLBACKS.getOrDefault(family, Set.of());

            assertTrue(stable.events().containsAll(expectedStableOnly),
                    () -> "Missing expected STABLE fallback events in " + family + ": " + expectedStableOnly);
            for (String fallbackEvent : expectedStableOnly) {
                assertFalse(versioned.events().contains(fallbackEvent),
                        () -> "Mixin/fallback event " + family + "." + fallbackEvent
                                + " has no native Fabric API callback and must be absent from VERSION");
            }

            Set<String> actualStableOnly = new LinkedHashSet<>(stable.events());
            actualStableOnly.removeAll(versioned.events());
            assertEquals(expectedStableOnly, actualStableOnly,
                    () -> "Unexpected STABLE-only event set for " + family);

            EventGroup sourceGroup = registry.view().get(stable.owner() + ":" + family);
            assertNotNull(sourceGroup, () -> "Missing registered source EventGroup for " + family);

            Set<String> sharedEvents = new LinkedHashSet<>(stable.events());
            sharedEvents.retainAll(versioned.events());
            assertFalse(sharedEvents.isEmpty(),
                    () -> "Expected non-empty VERSION ∩ STABLE intersection for " + family);

            for (String eventName : sharedEvents) {
                EventBusJS<?, ?> stableBus = resolveBus(stable.group(), eventName);
                EventBusJS<?, ?> versionedBus = resolveBus(versioned.group(), eventName);
                EventBusJS<?, ?> sourceBus = resolveBus(sourceGroup, eventName);
                assertSame(versionedBus, stableBus,
                        () -> "VERSION and STABLE publications must share the exact same EventBusJS for "
                                + family + "." + eventName);
                assertSame(sourceBus, stableBus,
                        () -> "Published EventBusJS must be identical to the registered source bus for "
                                + family + "." + eventName);
            }

            for (String eventName : stable.events()) {
                EventBusJS<?, ?> stableBus = resolveBus(stable.group(), eventName);
                EventBusJS<?, ?> sourceBus = resolveBus(sourceGroup, eventName);
                assertSame(sourceBus, stableBus,
                        () -> "Every STABLE event must share the exact same EventBusJS with the source group for "
                                + family + "." + eventName);
            }
        }
    }

    @Test
    void allStableEventViewsAndSelectorsHaveZeroNativeTypeLeaks() {
        EventGroupRegistry registry = buildRegistry();
        List<ModuleEventGroup> stableGroups = registry.moduleGroups().stream()
                .filter(module -> module.category() == BindingExposure.Category.STABLE)
                .toList();
        assertFalse(stableGroups.isEmpty(), "Expected non-empty STABLE module groups");

        int validatedViews = 0;
        int validatedSelectors = 0;
        for (ModuleEventGroup stable : stableGroups) {
            String family = stable.group().name();

            for (EventExposure.View view : stable.views().values()) {
                assertNotNull(view.type(), () -> "Null view type in " + family);
                assertDoesNotThrow(() -> ModulePublicTypes.validateStable(view.type(), false),
                        () -> "Native MC/Fabric type leaked in stable view of " + family + ": " + view.type().getName());
                validatedViews++;
            }

            for (EventExposure.Selector selector : stable.selectors().values()) {
                assertNotNull(selector.type(), () -> "Null selector type in " + family);
                assertDoesNotThrow(() -> ModulePublicTypes.validateStable(selector.type(), false),
                        () -> "Native MC/Fabric type leaked in stable selector of " + family + ": " + selector.type().getName());
                validatedSelectors++;
            }

            for (String eventName : stable.events()) {
                EventBusJS<?, ?> bus = resolveBus(stable.group(), eventName);
                EventExposure.View view = stable.views().get(eventName);
                Class<?> effectivePayload = view != null ? view.type() : bus.eventType();
                if (effectivePayload != null) {
                    assertDoesNotThrow(() -> ModulePublicTypes.validateStable(effectivePayload, false),
                            () -> "Native payload type leaked in " + family + "." + eventName + ": " + effectivePayload.getName());
                }
                if (bus.bus() instanceof DispatchEventBus<?, ?> dispatch) {
                    EventExposure.Selector selector = stable.selectors().get(eventName);
                    Class<?> effectiveSelector = selector != null ? selector.type() : dispatch.dispatchKey().keyType();
                    assertDoesNotThrow(() -> ModulePublicTypes.validateStable(effectiveSelector, false),
                            () -> "Native selector type leaked in " + family + "." + eventName + ": " + effectiveSelector.getName());
                }
            }
        }

        assertTrue(validatedViews > 0, "Expected projected stable event views to be validated");
        assertTrue(validatedSelectors > 0, "Expected projected stable event selectors to be validated");
    }

    private static ModuleEventGroup findPublication(
            List<ModuleEventGroup> modules, BindingExposure.Category category, String groupName) {
        return modules.stream()
                .filter(module -> module.category() == category && module.group().name().equals(groupName))
                .findFirst()
                .orElse(null);
    }

    private static EventBusJS<?, ?> resolveBus(EventGroup group, String eventName) {
        EventGroup.BusHolder holder = group.getBusHolder(eventName);
        assertNotNull(holder, () -> "Missing BusHolder for " + group.name() + "." + eventName);
        for (ScriptType scriptType : ScriptType.all()) {
            EventBusJS<?, ?> bus = holder.getBus(scriptType);
            if (bus != null) {
                return bus;
            }
        }
        throw new AssertionError("No live EventBusJS instance for " + group.name() + "." + eventName);
    }
}
