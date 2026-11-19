package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingExposure;
import com.tkisor.nekojs.core.api.ModulePublicTypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical loader-neutral specification and validator for all built-in {@code nekojs:event/stable}
 * event groups and events.
 */
public final class CommonStableEvents {
    private CommonStableEvents() {}

    /**
     * Canonical contract for a single built-in portable stable event.
     *
     * @param group                published event group name (e.g. {@code "ServerEvents"})
     * @param event                published event member name (e.g. {@code "tickPre"})
     * @param scriptType           exact {@link ScriptType} where the stable event is exposed
     * @param payloadType          portable payload class exposed to scripts
     * @param selectorType         portable dispatch selector class ({@code String.class}), or {@code null} when non-dispatched
     * @param cancellable          canonical cancellability on loaders with full event cancellation support
     * @param loaderOptionalCancel {@code true} only when a loader platform callback has a documented non-cancellable limitation
     */
    public record Spec(
            String group,
            String event,
            ScriptType scriptType,
            Class<?> payloadType,
            Class<?> selectorType,
            boolean cancellable,
            boolean loaderOptionalCancel
    ) {
        public Spec {
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(event, "event");
            Objects.requireNonNull(scriptType, "scriptType");
            Objects.requireNonNull(payloadType, "payloadType");
        }
    }

    /**
     * Complete immutable list of all 80 canonical {@code nekojs:event/stable} event specifications
     * across the 8 built-in event groups.
     */
    public static final List<Spec> ALL = List.of(
            // ServerEvents (10 SERVER events)
            server("ServerEvents", "tickPre", StableServerTickEvent.class, null, false, false),
            server("ServerEvents", "tickPost", StableServerTickEvent.class, null, false, false),
            server("ServerEvents", "aboutToStart", StableServerLifecycleEvent.class, null, false, false),
            server("ServerEvents", "starting", StableServerLifecycleEvent.class, null, false, false),
            server("ServerEvents", "started", StableServerLifecycleEvent.class, null, false, false),
            server("ServerEvents", "stopping", StableServerLifecycleEvent.class, null, false, false),
            server("ServerEvents", "stopped", StableServerLifecycleEvent.class, null, false, false),
            server("ServerEvents", "datapackSync", StableDatapackSyncEvent.class, null, false, false),
            server("ServerEvents", "tagsUpdated", StableTagsUpdatedEvent.class, null, false, false),
            server("ServerEvents", "lootTableLoad", StableLootTableLoadEvent.class, null, true, false),
            server("ServerEvents", "recipes", com.tkisor.nekojs.api.recipe.StableRecipeEvent.class, null, false, false),

            server("StageEvents", "customize", com.tkisor.nekojs.api.stage.StageCustomizeEvent.class, null, false, false),
            server("StageEvents", "gates", com.tkisor.nekojs.api.stage.StableStageGatesEvent.class, null, false, false),
            server("StageEvents", "added", com.tkisor.nekojs.api.stage.StableStageChangeEvent.class, String.class, false, false),
            server("StageEvents", "removed", com.tkisor.nekojs.api.stage.StableStageChangeEvent.class, String.class, false, false),
            server("StageEvents", "expired", com.tkisor.nekojs.api.stage.StableStageChangeEvent.class, String.class, false, false),
            client("StageEvents", "clientAdded", com.tkisor.nekojs.api.stage.StableStageChangeEvent.class, String.class, false, false),
            client("StageEvents", "clientRemoved", com.tkisor.nekojs.api.stage.StableStageChangeEvent.class, String.class, false, false),

            // LevelEvents (7 SERVER events)
            server("LevelEvents", "loaded", StableLevelEvent.class, null, false, false),
            server("LevelEvents", "unloaded", StableLevelEvent.class, null, false, false),
            server("LevelEvents", "saved", StableLevelEvent.class, null, false, false),
            server("LevelEvents", "tickPre", StableLevelEvent.class, null, false, false),
            server("LevelEvents", "tickPost", StableLevelEvent.class, null, false, false),
            server("LevelEvents", "explosionStart", StableExplosionEvent.class, null, true, false),
            server("LevelEvents", "explosionDetonate", StableExplosionEvent.class, null, false, false),

            // CommandEvents (2 SERVER events)
            server("CommandEvents", "register", StableCommandRegistrationEvent.class, null, false, false),
            server("CommandEvents", "command", StableCommandExecuteEvent.class, null, true, false),

            // PlayerEvents (15 SERVER events)
            server("PlayerEvents", "loggedIn", StablePlayerEvent.class, null, false, false),
            server("PlayerEvents", "loggedOut", StablePlayerEvent.class, null, false, false),
            server("PlayerEvents", "tickPre", StablePlayerEvent.class, null, false, false),
            server("PlayerEvents", "tickPost", StablePlayerEvent.class, null, false, false),
            server("PlayerEvents", "cloned", StablePlayerEvent.class, null, false, false),
            server("PlayerEvents", "respawned", StablePlayerEvent.class, null, false, false),
            server("PlayerEvents", "containerOpened", StablePlayerEvent.class, null, false, false),
            server("PlayerEvents", "containerClosed", StablePlayerEvent.class, null, false, false),
            server("PlayerEvents", "chat", StablePlayerChatEvent.class, null, true, false),
            server("PlayerEvents", "changedDimension", StablePlayerDimensionChangeEvent.class, null, false, false),
            server("PlayerEvents", "advancement", StablePlayerAdvancementEvent.class, null, false, false),
            server("PlayerEvents", "entityInteract", StablePlayerEvent.class, null, true, false),
            server("PlayerEvents", "crafted", StablePlayerItemEvent.class, String.class, false, false),
            server("PlayerEvents", "smelted", StablePlayerItemEvent.class, String.class, false, false),
            server("PlayerEvents", "destroyed", StablePlayerItemEvent.class, String.class, false, false),

            // ItemEvents (7 events: 6 SERVER + 1 CLIENT)
            server("ItemEvents", "rightClicked", StableItemEvent.class, String.class, true, false),
            client("ItemEvents", "tooltip", StableItemEvent.class, String.class, false, false),
            server("ItemEvents", "canPickUp", StableItemEvent.class, String.class, true, false),
            server("ItemEvents", "pickedUp", StableItemEvent.class, String.class, false, false),
            server("ItemEvents", "dropped", StableItemEvent.class, String.class, true, false),
            server("ItemEvents", "entityInteracted", StableItemEvent.class, String.class, true, false),
            server("ItemEvents", "foodEaten", StableItemEvent.class, String.class, false, false),

            // BlockEvents (13 SERVER events)
            server("BlockEvents", "broken", StableBlockEvent.class, String.class, true, false),
            server("BlockEvents", "entityPlaced", StableBlockEvent.class, String.class, true, false),
            server("BlockEvents", "entityMultiPlaced", StableBlockEvent.class, String.class, true, false),
            server("BlockEvents", "neighborNotify", StableBlockEvent.class, String.class, true, false),
            server("BlockEvents", "fluidPlaced", StableBlockEvent.class, String.class, true, false),
            server("BlockEvents", "farmlandTrample", StableBlockEvent.class, String.class, true, false),
            server("BlockEvents", "portalSpawn", StableBlockEvent.class, String.class, true, false),
            server("BlockEvents", "toolModification", StableBlockEvent.class, String.class, true, false),
            server("BlockEvents", "rightClicked", StableBlockEvent.class, String.class, true, false),
            server("BlockEvents", "placed", StableBlockEvent.class, String.class, true, false),
            server("BlockEvents", "leftClicked", StableBlockEvent.class, String.class, true, false),
            server("BlockEvents", "randomTick", StableBlockEvent.class, String.class, false, false),
            server("BlockEvents", "blockEntityTick", StableBlockEvent.class, String.class, false, false),

            // EntityEvents (13 SERVER events)
            server("EntityEvents", "damagePre", StableEntityEvent.class, String.class, true, false),
            server("EntityEvents", "damagePost", StableEntityEvent.class, String.class, false, false),
            server("EntityEvents", "death", StableEntityEvent.class, String.class, true, false),
            server("EntityEvents", "drops", StableEntityEvent.class, String.class, true, false),
            server("EntityEvents", "finalizeSpawn", StableEntityEvent.class, String.class, true, false),
            server("EntityEvents", "tickPre", StableEntityEvent.class, String.class, false, false),
            server("EntityEvents", "tickPost", StableEntityEvent.class, String.class, false, false),
            server("EntityEvents", "joinLevel", StableEntityEvent.class, String.class, true, false),
            server("EntityEvents", "leaveLevel", StableEntityEvent.class, String.class, false, false),
            server("EntityEvents", "useItemStarted", StableEntityEvent.class, String.class, true, false),
            server("EntityEvents", "useItemStopped", StableEntityEvent.class, String.class, true, false),
            server("EntityEvents", "useItemFinished", StableEntityEvent.class, String.class, false, false),
            server("EntityEvents", "useItemTick", StableEntityEvent.class, String.class, true, false),

            // ClientEvents (13 CLIENT events)
            client("ClientEvents", "tickPre", StableClientEvent.class, null, false, false),
            client("ClientEvents", "tickPost", StableClientEvent.class, null, false, false),
            client("ClientEvents", "registerKeyMappings", StableClientEvent.class, null, false, false),
            client("ClientEvents", "registerMenuScreens", StableClientEvent.class, null, false, false),
            client("ClientEvents", "registerRenderers", StableClientEvent.class, null, false, false),
            client("ClientEvents", "registerParticleProviders", StableClientEvent.class, null, false, false),
            client("ClientEvents", "playerTickPre", StablePlayerEvent.class, null, false, false),
            client("ClientEvents", "playerTickPost", StablePlayerEvent.class, null, false, false),
            client("ClientEvents", "loggedIn", StablePlayerEvent.class, null, false, false),
            client("ClientEvents", "loggedOut", StablePlayerEvent.class, null, false, false),
            client("ClientEvents", "cloned", StablePlayerEvent.class, null, false, false),
            client("ClientEvents", "interactionKey", StableClientInputEvent.class, null, true, false),
            client("ClientEvents", "commandRegistry", StableCommandRegistrationEvent.class, null, false, false)
    );

    /**
     * Immutable group-indexed view ({@code group -> (event -> Spec)}) of {@link #ALL}, preserving
     * canonical declaration order.
     */
    public static final Map<String, Map<String, Spec>> BY_GROUP = buildByGroup();

    private static Spec server(
            String group,
            String event,
            Class<?> payloadType,
            Class<?> selectorType,
            boolean cancellable,
            boolean loaderOptionalCancel
    ) {
        return new Spec(group, event, ScriptType.SERVER, payloadType, selectorType, cancellable, loaderOptionalCancel);
    }

    private static Spec client(
            String group,
            String event,
            Class<?> payloadType,
            Class<?> selectorType,
            boolean cancellable,
            boolean loaderOptionalCancel
    ) {
        return new Spec(group, event, ScriptType.CLIENT, payloadType, selectorType, cancellable, loaderOptionalCancel);
    }

    private static Map<String, Map<String, Spec>> buildByGroup() {
        Map<String, Map<String, Spec>> grouped = new LinkedHashMap<>();
        for (Spec spec : ALL) {
            Map<String, Spec> events = grouped.computeIfAbsent(spec.group(), ignored -> new LinkedHashMap<>());
            if (events.put(spec.event(), spec) != null) {
                throw new IllegalStateException("Duplicate canonical stable event spec: " + spec.group() + "." + spec.event());
            }
        }
        Map<String, Map<String, Spec>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Spec>> entry : grouped.entrySet()) {
            immutable.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(immutable);
    }

    /**
     * Validates all {@code nekojs}-owned {@link BindingExposure.Category#STABLE} module event groups
     * against the canonical 8-group, 80-event contract.
     *
     * @param moduleGroups published module event groups to inspect
     * @return immutable list of human-readable contract violation messages (empty when 100% compliant)
     */
    public static List<String> validate(Collection<ModuleEventGroup> moduleGroups) {
        Objects.requireNonNull(moduleGroups, "moduleGroups");
        List<String> violations = new ArrayList<>();

        Map<String, ModuleEventGroup> actualByGroup = new LinkedHashMap<>();
        for (ModuleEventGroup module : moduleGroups) {
            if (module == null) {
                violations.add("Null ModuleEventGroup entry in moduleGroups");
                continue;
            }
            if (module.category() != BindingExposure.Category.STABLE || !"nekojs".equals(module.owner())) {
                continue;
            }
            String groupName = module.group().name();
            if (actualByGroup.putIfAbsent(groupName, module) != null) {
                violations.add("Duplicate stable ModuleEventGroup publication for group: " + groupName);
            }
        }

        for (String expectedGroup : BY_GROUP.keySet()) {
            if (!actualByGroup.containsKey(expectedGroup)) {
                violations.add("Missing stable event group: " + expectedGroup);
            }
        }
        for (String actualGroup : actualByGroup.keySet()) {
            if (!BY_GROUP.containsKey(actualGroup)) {
                violations.add("Unexpected stable event group: " + actualGroup);
            }
        }

        for (Map.Entry<String, Map<String, Spec>> groupEntry : BY_GROUP.entrySet()) {
            String groupName = groupEntry.getKey();
            Map<String, Spec> expectedEvents = groupEntry.getValue();
            ModuleEventGroup module = actualByGroup.get(groupName);
            if (module == null) {
                continue;
            }

            for (String expectedEvent : expectedEvents.keySet()) {
                if (!module.events().contains(expectedEvent)) {
                    violations.add("Missing stable event: " + groupName + "." + expectedEvent);
                }
            }
            for (String actualEvent : module.events()) {
                if (!expectedEvents.containsKey(actualEvent)) {
                    violations.add("Unexpected stable event: " + groupName + "." + actualEvent);
                }
            }

            for (Spec spec : expectedEvents.values()) {
                if (!module.events().contains(spec.event())) {
                    continue;
                }
                validateEventSpec(module, spec, violations);
            }
        }

        return List.copyOf(violations);
    }

    /**
     * Asserts that {@code moduleGroups} satisfies the canonical {@code nekojs:event/stable} contract,
     * throwing {@link IllegalStateException} with all violation messages if any discrepancy exists.
     *
     * @param moduleGroups published module event groups to verify
     */
    public static void assertCompliant(Collection<ModuleEventGroup> moduleGroups) {
        List<String> violations = validate(moduleGroups);
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "CommonStableEvents contract violations (" + violations.size() + "):\n - "
                            + String.join("\n - ", violations));
        }
    }

    private static void validateEventSpec(ModuleEventGroup module, Spec spec, List<String> violations) {
        String qualified = spec.group() + "." + spec.event();

        if (!"nekojs:event/stable".equals(module.moduleId())) {
            violations.add("Wrong moduleId for " + qualified + ": expected nekojs:event/stable, actual " + module.moduleId());
        }

        if (!module.supports(spec.event(), spec.scriptType())) {
            violations.add("Event " + qualified + " must support ScriptType." + spec.scriptType());
        }
        for (ScriptType candidate : ScriptType.values()) {
            if (candidate != spec.scriptType() && module.supports(spec.event(), candidate)) {
                violations.add("Event " + qualified + " must not support ScriptType." + candidate
                        + " (expected only ScriptType." + spec.scriptType() + ")");
            }
        }

        EventGroup.BusHolder holder = module.group().getBusHolder(spec.event());
        if (holder == null) {
            violations.add("Missing BusHolder for " + qualified);
            return;
        }
        EventBusJS<?, ?> bus = resolveBus(holder);
        if (bus == null) {
            violations.add("Missing EventBusJS instance for " + qualified);
            return;
        }

        EventExposure.View view = module.views().get(spec.event());
        Class<?> effectivePayload = view != null ? view.type() : bus.eventType();
        if (!Objects.equals(spec.payloadType(), effectivePayload)) {
            violations.add("Wrong payload type for " + qualified + ": expected " + spec.payloadType().getName()
                    + ", actual " + (effectivePayload == null ? "null" : effectivePayload.getName()));
        }
        if (effectivePayload != null) {
            try {
                ModulePublicTypes.validateStable(effectivePayload, false);
            } catch (RuntimeException ex) {
                violations.add("Non-portable payload type for " + qualified + " ("
                        + effectivePayload.getName() + "): " + ex.getMessage());
            }
        }

        boolean expectedDispatch = spec.selectorType() != null;
        if (bus.canDispatch() != expectedDispatch) {
            violations.add("Wrong dispatch capability for " + qualified + ": expected canDispatch="
                    + expectedDispatch + ", actual " + bus.canDispatch());
        }
        if (expectedDispatch) {
            if (bus.bus() instanceof DispatchEventBus<?, ?> dispatchBus) {
                EventExposure.Selector selector = module.selectors().get(spec.event());
                Class<?> effectiveSelector = selector != null ? selector.type() : dispatchBus.dispatchKey().keyType();
                if (!Objects.equals(spec.selectorType(), effectiveSelector)) {
                    violations.add("Wrong selector type for " + qualified + ": expected "
                            + spec.selectorType().getName() + ", actual "
                            + (effectiveSelector == null ? "null" : effectiveSelector.getName()));
                }
                if (effectiveSelector != null) {
                    try {
                        ModulePublicTypes.validateStable(effectiveSelector, false);
                    } catch (RuntimeException ex) {
                        violations.add("Non-portable selector type for " + qualified + " ("
                                + effectiveSelector.getName() + "): " + ex.getMessage());
                    }
                }
            }
        } else if (module.selectors().containsKey(spec.event())) {
            violations.add("Unexpected selector mapping for non-dispatched event " + qualified);
        }

        if (!spec.loaderOptionalCancel() && bus.canCancel() != spec.cancellable()) {
            violations.add("Wrong cancellability for " + qualified + ": expected canCancel="
                    + spec.cancellable() + ", actual " + bus.canCancel());
        }
    }

    private static EventBusJS<?, ?> resolveBus(EventGroup.BusHolder holder) {
        for (ScriptType scriptType : ScriptType.values()) {
            EventBusJS<?, ?> bus = holder.getBus(scriptType);
            if (bus != null) {
                return bus;
            }
        }
        return null;
    }
}
