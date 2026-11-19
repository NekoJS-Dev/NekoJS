package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingExposure;
import com.tkisor.nekojs.core.api.ModulePublicTypes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CommonStableEvents} canonical inventory, payload portability, and
 * structural contract validation.
 */
class CommonStableEventsContractTest {

    @Test
    void canonicalInventoryCoversEightyEventsAcrossEightGroupsWithPortableTypes() {
        assertEquals(80, CommonStableEvents.ALL.size());
        assertEquals(8, CommonStableEvents.BY_GROUP.size());

        assertEquals(10, CommonStableEvents.BY_GROUP.get("ServerEvents").size());
        assertEquals(7, CommonStableEvents.BY_GROUP.get("LevelEvents").size());
        assertEquals(2, CommonStableEvents.BY_GROUP.get("CommandEvents").size());
        assertEquals(15, CommonStableEvents.BY_GROUP.get("PlayerEvents").size());
        assertEquals(7, CommonStableEvents.BY_GROUP.get("ItemEvents").size());
        assertEquals(13, CommonStableEvents.BY_GROUP.get("BlockEvents").size());
        assertEquals(13, CommonStableEvents.BY_GROUP.get("EntityEvents").size());
        assertEquals(13, CommonStableEvents.BY_GROUP.get("ClientEvents").size());

        List<String> optionalCancelEvents = CommonStableEvents.ALL.stream()
                .filter(CommonStableEvents.Spec::loaderOptionalCancel)
                .map(spec -> spec.group() + "." + spec.event())
                .toList();
        assertEquals(List.of("ServerEvents.lootTableLoad", "EntityEvents.finalizeSpawn"), optionalCancelEvents);

        for (CommonStableEvents.Spec spec : CommonStableEvents.ALL) {
            assertDoesNotThrow(() -> ModulePublicTypes.validateStable(spec.payloadType(), false),
                    () -> "Expected portable payloadType for " + spec.group() + "." + spec.event());
            if (spec.selectorType() != null) {
                assertEquals(String.class, spec.selectorType(),
                        () -> "Expected String.class selectorType for " + spec.group() + "." + spec.event());
                assertDoesNotThrow(() -> ModulePublicTypes.validateStable(spec.selectorType(), false));
            }
        }
    }

    @Test
    void compliantModulesAndLoaderOptionalCancellationPassValidation() {
        List<ModuleEventGroup> compliant = buildSyntheticModules(null, true);
        assertEquals(List.of(), CommonStableEvents.validate(compliant));
        assertDoesNotThrow(() -> CommonStableEvents.assertCompliant(compliant));

        // Non-cancellable platform callbacks for loaderOptionalCancel specs (e.g. Fabric) also pass.
        List<ModuleEventGroup> fabricStyleOptionalCancel = buildSyntheticModules(null, false);
        assertEquals(List.of(), CommonStableEvents.validate(fabricStyleOptionalCancel));

        // Unrelated VERSION and third-party addon STABLE publications are ignored.
        List<ModuleEventGroup> withExtras = new ArrayList<>(compliant);
        EventGroup addonGroup = EventGroup.of("AddonEvents");
        addonGroup.add("ping", ScriptType.SERVER, EventBusJS.of(StableServerTickEvent.class, false));
        addonGroup.freeze();
        withExtras.add(new ModuleEventGroup(
                "example_addon",
                BindingExposure.Category.STABLE,
                addonGroup,
                Set.of("ping"),
                Map.of(),
                Set.of(ScriptType.SERVER)));
        withExtras.add(new ModuleEventGroup(
                "nekojs",
                BindingExposure.Category.VERSION,
                addonGroup,
                Set.of("ping"),
                Map.of(),
                Set.of(ScriptType.SERVER)));
        assertEquals(List.of(), CommonStableEvents.validate(withExtras));
    }

    @Test
    void validateCatchesMissingAndExtraGroups() {
        List<ModuleEventGroup> missingCommandGroup = buildSyntheticModules(null, true).stream()
                .filter(module -> !module.group().name().equals("CommandEvents"))
                .toList();
        List<String> missingViolations = CommonStableEvents.validate(missingCommandGroup);
        assertFalse(missingViolations.isEmpty());
        assertTrue(missingViolations.stream().anyMatch(msg -> msg.contains("Missing stable event group: CommandEvents")),
                () -> "Expected missing CommandEvents violation, got: " + missingViolations);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> CommonStableEvents.assertCompliant(missingCommandGroup));
        assertTrue(thrown.getMessage().contains("CommandEvents"));

        List<ModuleEventGroup> withUnexpectedGroup = new ArrayList<>(buildSyntheticModules(null, true));
        EventGroup extra = EventGroup.of("ExtraStableEvents");
        extra.add("ping", ScriptType.SERVER, EventBusJS.of(StableServerTickEvent.class, false));
        extra.freeze();
        withUnexpectedGroup.add(new ModuleEventGroup(
                "nekojs",
                BindingExposure.Category.STABLE,
                extra,
                Set.of("ping"),
                Map.of(),
                Set.of(ScriptType.SERVER)));
        List<String> extraViolations = CommonStableEvents.validate(withUnexpectedGroup);
        assertTrue(extraViolations.stream().anyMatch(msg -> msg.contains("Unexpected stable event group: ExtraStableEvents")),
                () -> "Expected unexpected group violation, got: " + extraViolations);
    }

    @Test
    void validateCatchesMissingAndExtraEvents() {
        List<ModuleEventGroup> missingAndExtraEvents = buildSyntheticModules((groupName, builder) -> {
            if ("ServerEvents".equals(groupName)) {
                builder.events.remove("tickPost");
                builder.group.add("unexpectedTick", ScriptType.SERVER, EventBusJS.of(StableServerTickEvent.class, false));
                builder.events.add("unexpectedTick");
                builder.eventScriptTypes.put("unexpectedTick", Set.of(ScriptType.SERVER));
            }
        }, true);

        List<String> violations = CommonStableEvents.validate(missingAndExtraEvents);
        assertTrue(violations.stream().anyMatch(msg -> msg.contains("Missing stable event: ServerEvents.tickPost")),
                () -> "Expected missing ServerEvents.tickPost violation, got: " + violations);
        assertTrue(violations.stream().anyMatch(msg -> msg.contains("Unexpected stable event: ServerEvents.unexpectedTick")),
                () -> "Expected unexpected ServerEvents.unexpectedTick violation, got: " + violations);
    }

    @Test
    void validateCatchesWrongAndNonPortablePayloadTypes() {
        List<ModuleEventGroup> wrongPayloads = buildSyntheticModules((groupName, builder) -> {
            if ("ServerEvents".equals(groupName)) {
                builder.views.put("tickPre", new EventExposure.View(StableClientEvent.class, v -> v));
                builder.views.put("tickPost", new EventExposure.View(net.minecraft.nekojstest.NativeToken.class, v -> v));
            }
        }, true);

        List<String> violations = CommonStableEvents.validate(wrongPayloads);
        assertTrue(violations.stream().anyMatch(msg ->
                        msg.contains("Wrong payload type for ServerEvents.tickPre")
                                && msg.contains(StableClientEvent.class.getName())),
                () -> "Expected wrong payload violation for ServerEvents.tickPre, got: " + violations);
        assertTrue(violations.stream().anyMatch(msg ->
                        msg.contains("Non-portable payload type for ServerEvents.tickPost")),
                () -> "Expected non-portable payload violation for ServerEvents.tickPost, got: " + violations);
    }

    @Test
    void validateCatchesWrongSelectorTypesAndDispatchMismatches() {
        List<ModuleEventGroup> wrongSelectors = buildSyntheticModules((groupName, builder) -> {
            if ("BlockEvents".equals(groupName)) {
                builder.selectors.put("broken", new EventExposure.Selector(Integer.class, v -> v));
            }
            if ("PlayerEvents".equals(groupName)) {
                builder.replaceBus("crafted", ScriptType.SERVER, EventBusJS.of(StablePlayerItemEvent.class, false));
            }
            if ("CommandEvents".equals(groupName)) {
                builder.replaceBus("register", ScriptType.SERVER, EventBusJS.of(
                        StableCommandRegistrationEvent.class,
                        false,
                        DispatchKey.of(String.class, e -> "")));
            }
        }, true);

        List<String> violations = CommonStableEvents.validate(wrongSelectors);
        assertTrue(violations.stream().anyMatch(msg ->
                        msg.contains("Wrong selector type for BlockEvents.broken")
                                && msg.contains(Integer.class.getName())),
                () -> "Expected wrong selector type violation for BlockEvents.broken, got: " + violations);
        assertTrue(violations.stream().anyMatch(msg ->
                        msg.contains("Wrong dispatch capability for PlayerEvents.crafted")),
                () -> "Expected dispatch capability violation for PlayerEvents.crafted, got: " + violations);
        assertTrue(violations.stream().anyMatch(msg ->
                        msg.contains("Wrong dispatch capability for CommandEvents.register")),
                () -> "Expected dispatch capability violation for CommandEvents.register, got: " + violations);
    }

    @Test
    void validateCatchesWrongScriptTypes() {
        List<ModuleEventGroup> wrongScriptTypes = buildSyntheticModules((groupName, builder) -> {
            if ("ServerEvents".equals(groupName)) {
                // Widen tickPre to STARTUP + SERVER (simulating missing .scriptTypes(ScriptType.SERVER)).
                builder.eventScriptTypes.put("tickPre", Set.of(ScriptType.SERVER, ScriptType.STARTUP));
            }
            if ("ItemEvents".equals(groupName)) {
                // Register tooltip on SERVER instead of CLIENT.
                builder.replaceBus("tooltip", ScriptType.SERVER, EventBusJS.of(
                        StableItemEvent.class,
                        false,
                        DispatchKey.of(String.class, StableItemEvent::getItemId)));
                builder.eventScriptTypes.put("tooltip", Set.of(ScriptType.SERVER));
            }
        }, true);

        List<String> violations = CommonStableEvents.validate(wrongScriptTypes);
        assertTrue(violations.stream().anyMatch(msg ->
                        msg.contains("Event ServerEvents.tickPre must not support ScriptType.STARTUP")),
                () -> "Expected STARTUP leak violation for ServerEvents.tickPre, got: " + violations);
        assertTrue(violations.stream().anyMatch(msg ->
                        msg.contains("Event ItemEvents.tooltip must support ScriptType.CLIENT")),
                () -> "Expected missing CLIENT support violation for ItemEvents.tooltip, got: " + violations);
    }

    @Test
    void validateCatchesWrongCancellability() {
        List<ModuleEventGroup> wrongCancellability = buildSyntheticModules((groupName, builder) -> {
            if ("CommandEvents".equals(groupName)) {
                builder.replaceBus("command", ScriptType.SERVER, EventBusJS.of(StableCommandExecuteEvent.class, false));
            }
            if ("LevelEvents".equals(groupName)) {
                builder.replaceBus("loaded", ScriptType.SERVER, EventBusJS.of(StableLevelEvent.class, true));
            }
        }, true);

        List<String> violations = CommonStableEvents.validate(wrongCancellability);
        assertTrue(violations.stream().anyMatch(msg ->
                        msg.contains("Wrong cancellability for CommandEvents.command")
                                && msg.contains("expected canCancel=true, actual false")),
                () -> "Expected cancellability violation for CommandEvents.command, got: " + violations);
        assertTrue(violations.stream().anyMatch(msg ->
                        msg.contains("Wrong cancellability for LevelEvents.loaded")
                                && msg.contains("expected canCancel=false, actual true")),
                () -> "Expected cancellability violation for LevelEvents.loaded, got: " + violations);
    }

    private static List<ModuleEventGroup> buildSyntheticModules(
            BiConsumer<String, GroupFixtureBuilder> customizer,
            boolean optionalCancelEnabled
    ) {
        List<ModuleEventGroup> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, CommonStableEvents.Spec>> entry : CommonStableEvents.BY_GROUP.entrySet()) {
            String groupName = entry.getKey();
            GroupFixtureBuilder builder = new GroupFixtureBuilder(groupName);
            for (CommonStableEvents.Spec spec : entry.getValue().values()) {
                boolean canCancel = spec.loaderOptionalCancel() ? optionalCancelEnabled : spec.cancellable();
                DispatchKey<Object, String> dispatchKey = spec.selectorType() != null
                        ? DispatchKey.of(String.class, ignored -> "")
                        : null;
                @SuppressWarnings("unchecked")
                Class<Object> payloadClass = (Class<Object>) spec.payloadType();
                EventBusJS<Object, String> bus = EventBusJS.of(payloadClass, canCancel, dispatchKey);
                builder.buses.put(spec.event(), new BusEntry(spec.scriptType(), bus));
                builder.events.add(spec.event());
                builder.scriptTypes.add(spec.scriptType());
                builder.eventScriptTypes.put(spec.event(), Set.of(spec.scriptType()));
            }
            if (customizer != null) {
                customizer.accept(groupName, builder);
            }
            result.add(builder.build());
        }
        return result;
    }

    private record BusEntry(ScriptType scriptType, EventBusJS<?, ?> bus) {}

    private static final class GroupFixtureBuilder {
        final String groupName;
        final EventGroup group;
        final Map<String, BusEntry> buses = new LinkedHashMap<>();
        final Set<String> events = new LinkedHashSet<>();
        final Map<String, EventExposure.View> views = new LinkedHashMap<>();
        final Map<String, EventExposure.Selector> selectors = new LinkedHashMap<>();
        final Set<ScriptType> scriptTypes = new LinkedHashSet<>();
        final Map<String, Set<ScriptType>> eventScriptTypes = new LinkedHashMap<>();

        GroupFixtureBuilder(String groupName) {
            this.groupName = groupName;
            this.group = EventGroup.of(groupName);
        }

        void replaceBus(String eventName, ScriptType scriptType, EventBusJS<?, ?> bus) {
            buses.put(eventName, new BusEntry(scriptType, bus));
            scriptTypes.add(scriptType);
        }

        ModuleEventGroup build() {
            for (Map.Entry<String, BusEntry> entry : buses.entrySet()) {
                if (group.getBusHolder(entry.getKey()) == null) {
                    group.add(entry.getKey(), entry.getValue().scriptType(), entry.getValue().bus());
                }
            }
            group.freeze();
            return new ModuleEventGroup(
                    "nekojs",
                    BindingExposure.Category.STABLE,
                    group,
                    events,
                    views,
                    selectors,
                    scriptTypes,
                    eventScriptTypes
            );
        }
    }
}
