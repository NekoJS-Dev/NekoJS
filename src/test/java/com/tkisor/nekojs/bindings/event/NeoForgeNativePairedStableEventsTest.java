//? if neoforge {
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
import com.tkisor.nekojs.core.NekoJSCorePlugin;
import com.tkisor.nekojs.core.api.ModulePublicTypes;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
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
 * End-to-end verification for paired {@code nekojs:event/versioned} and {@code nekojs:event/stable}
 * publications across all built-in NeoForge native event families.
 */
class NeoForgeNativePairedStableEventsTest {

    private static final Set<String> PAIRED_NATIVE_FAMILIES = Set.of(
            "ServerEvents",
            "LevelEvents",
            "PlayerEvents",
            "CommandEvents",
            "BlockEvents",
            "ItemEvents",
            "EntityEvents",
            "ClientEvents"
    );

    private static final Set<String> EXPECTED_CLIENT_STABLE_EVENTS = Set.of(
            "tickPre",
            "tickPost",
            "playerTickPre",
            "playerTickPost",
            "interactionKey",
            "loggedIn",
            "loggedOut",
            "cloned",
            "commandRegistry",
            "registerKeyMappings",
            "registerMenuScreens",
            "registerRenderers",
            "registerParticleProviders"
    );

    private static EventGroupRegistry buildRegistry() {
        EventBusJS.setExternalCancellabilityPredicate(
                net.neoforged.bus.api.ICancellableEvent.class::isAssignableFrom);
        try {
            Platform.init(new IPlatform() {
                @Override public boolean isClient() { return false; }
                @Override public boolean isDevelopment() { return true; }
                @Override public String getMcVersion() { return "test"; }
                @Override public Path getGameDir() {
                    return TestGameDirs.unique("nekojs-neoforge-paired-stable-events");
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
        NekoJSCorePlugin plugin = new NekoJSCorePlugin();
        plugin.registerEvents(registry);
        plugin.registerClientEvents(registry);
        return registry;
    }

    @Test
    void matchesCanonicalCommonStableEventsContract() {
        List<String> violations = CommonStableEvents.validate(buildRegistry().moduleGroups());
        assertEquals(List.of(), violations);
    }

    @Test
    void allEightNativeFamiliesPublishBothVersionedAndStableModuleGroups() {
        EventGroupRegistry registry = buildRegistry();
        List<ModuleEventGroup> modules = registry.moduleGroups();

        for (String family : PAIRED_NATIVE_FAMILIES) {
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
        assertTrue(stableFamilies.containsAll(PAIRED_NATIVE_FAMILIES),
                "All 8 NeoForge native event families must publish STABLE module groups");

        ModuleEventGroup clientStable = findPublication(modules, BindingExposure.Category.STABLE, "ClientEvents");
        ModuleEventGroup clientVersioned = findPublication(modules, BindingExposure.Category.VERSION, "ClientEvents");
        assertEquals(EXPECTED_CLIENT_STABLE_EVENTS, clientStable.events(),
                "ClientEvents stable surface must expose exactly the 13 non-deprecated native client events");
        assertFalse(clientStable.events().contains("tick"));
        assertFalse(clientStable.events().contains("registerEntityRenderers"));
        assertFalse(clientStable.events().contains("registerBlockEntityRenderers"));
        assertTrue(clientVersioned.events().containsAll(Set.of(
                "tick", "registerEntityRenderers", "registerBlockEntityRenderers", "hud", "generateAssets")));
    }

    @Test
    void everyStableEventHasAPairedVersionedSiblingSharingTheSameUnderlyingBus() {
        EventGroupRegistry registry = buildRegistry();
        List<ModuleEventGroup> modules = registry.moduleGroups();
        List<ModuleEventGroup> stableGroups = modules.stream()
                .filter(module -> module.category() == BindingExposure.Category.STABLE)
                .toList();
        assertFalse(stableGroups.isEmpty(), "Expected non-empty STABLE module groups");

        for (ModuleEventGroup stable : stableGroups) {
            String family = stable.group().name();
            ModuleEventGroup versioned = findPublication(modules, BindingExposure.Category.VERSION, family);
            assertNotNull(versioned, () -> "Every STABLE group must have a paired VERSION group: " + family);
            assertTrue(versioned.events().containsAll(stable.events()),
                    () -> "STABLE events must be strictly contained in VERSION events for " + family);

            EventGroup sourceGroup = registry.view().get(stable.owner() + ":" + family);
            assertNotNull(sourceGroup, () -> "Missing source EventGroup for " + family);

            for (String eventName : stable.events()) {
                EventBusJS<?, ?> stableBus = resolveBus(stable.group(), eventName);
                EventBusJS<?, ?> versionedBus = resolveBus(versioned.group(), eventName);
                EventBusJS<?, ?> sourceBus = resolveBus(sourceGroup, eventName);
                assertSame(versionedBus, stableBus,
                        () -> "STABLE and VERSION publications must share the exact same EventBusJS for "
                                + family + "." + eventName);
                assertSame(sourceBus, stableBus,
                        () -> "Published EventBusJS must be identical to the registered source bus for "
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
                        () -> "Native MC/NeoForge type leaked in stable view of " + family + ": " + view.type().getName());
                validatedViews++;
            }

            for (EventExposure.Selector selector : stable.selectors().values()) {
                assertNotNull(selector.type(), () -> "Null selector type in " + family);
                assertDoesNotThrow(() -> ModulePublicTypes.validateStable(selector.type(), false),
                        () -> "Native MC/NeoForge type leaked in stable selector of " + family + ": " + selector.type().getName());
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
//?}
