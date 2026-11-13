package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.wrapper.registry.gen.StartupRegistryRuntime;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Registers a collected startup epoch through Fabric's vanilla registry sinks.
 * Each pass refreshes the pending registries so co-registered entries are included.
 * ITEM runs after the other registries that can produce block items or spawn eggs;
 * each registry still receives only one pass within the runtime epoch.
 */
public final class FabricRegistryAdapter {
    private FabricRegistryAdapter() {}

    private static volatile StartupRegistryRuntime runtime = new StartupRegistryRuntime(nodeLabel());

    /** The runtime for the current epoch. */
    public static StartupRegistryRuntime runtime() {
        return runtime;
    }

    /** Starts a new game boot: switches to a new epoch and, if the previous boot left staging
     * undrained, discards it and logs a diagnostic. */
    public static void beginBoot() {
        StartupRegistryRuntime previous = runtime;
        if (previous != null && !previous.isFullyDrained()) {
            previous.reportUndelivered(message -> NekoJS.LOGGER.error(
                    "[registry-startup] stale staging from a previous boot discarded: {}", message));
        }
        runtime = new StartupRegistryRuntime(nodeLabel());
    }

    /** Called by the mod entry after the STARTUP scripts have loaded; the script-side listeners
     * are already attached by then. */
    public static void onInitialize() {
        StartupRegistryRuntime current = runtime;
        current.collectOnce();
        drainPendingRegistries(current, key -> drainRegistry(current, key));
        // Entity attributes: register the attribute table recorded while EntityTypeBuilder builds.
        // NeoForge consumes the same drainPendingAttributes through EntityAttributeCreationEvent.
        com.tkisor.nekojs.wrapper.registry.gen.EntityTypeBuilder.drainPendingAttributes()
                .forEach(net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry::register);
        // groupTab assignments: group GROUP_ASSIGNMENTS by tab and register a modifyOutput append
        // (NeoForge consumes them through BuildCreativeModeTabContentsEvent). modifyOutput is a
        // lazy callback and the registries are frozen by now, so resolving items inside it is safe.
        java.util.Map<Identifier, java.util.List<Identifier>> byTab = new java.util.HashMap<>();
        com.tkisor.nekojs.wrapper.registry.gen.ItemBuilder.GROUP_ASSIGNMENTS.forEach(
                (itemId, tabId) -> byTab.computeIfAbsent(tabId, k -> new java.util.ArrayList<>()).add(itemId));
        byTab.forEach((tabId, itemIds) ->
                net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(
                                ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, tabId))
                        .register(output -> itemIds.forEach(id ->
                                BuiltInRegistries.ITEM.getOptional(id).ifPresent(item ->
                                        output.accept(new net.minecraft.world.item.ItemStack(item))))));
        // Item fuel (NeoForge uses a getBurnTime override; fabric fills the table through
        // FuelValueEvents.BUILD). This replays on every fuel table build (server start, datapack
        // reload), so it is idempotent.
        if (!com.tkisor.nekojs.wrapper.registry.gen.ItemBuilder.FUEL_ASSIGNMENTS.isEmpty()) {
            net.fabricmc.fabric.api.registry.FuelValueEvents.BUILD.register((builder, ctx) ->
                    com.tkisor.nekojs.wrapper.registry.gen.ItemBuilder.FUEL_ASSIGNMENTS.forEach((id, time) ->
                            BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> builder.add(item, time))));
        }
    }

    static void drainPendingRegistries(StartupRegistryRuntime current,
            java.util.function.Consumer<ResourceKey<? extends Registry<?>>> drain) {
        java.util.Set<ResourceKey<? extends Registry<?>>> visited = new java.util.HashSet<>();
        java.util.Comparator<ResourceKey<? extends Registry<?>>> order = java.util.Comparator
                .comparing((ResourceKey<? extends Registry<?>> key) -> key.equals(Registries.ITEM))
                .thenComparing(key -> key.identifier().toString());
        while (true) {
            java.util.Set<ResourceKey<? extends Registry<?>>> pending = current.snapshotUndrainedRegistries();
            if (pending.isEmpty() && current.itemRegistrationComplete()) return;
            // Even an empty ITEM pass must finish before stale owned assets can be removed.
            ResourceKey<? extends Registry<?>> next = pending.isEmpty()
                    ? Registries.ITEM : pending.stream().min(order).orElseThrow();
            if (!visited.add(next)) {
                throw new IllegalStateException("[NEKO-4028] Registry pass did not drain its pending content: "
                        + next.identifier());
            }
            drain.accept(next);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void drainRegistry(StartupRegistryRuntime current, ResourceKey<? extends Registry<?>> key) {
        // REGISTRY is the root registry (a registry of registries), so its generic capture does not
        // match any concrete key; narrow it with a raw type.
        java.util.Optional<?> resolved = BuiltInRegistries.REGISTRY.get((ResourceKey) key);
        Registry<?> registry = resolved.isPresent() ? (Registry<?>) ((Holder<?>) resolved.get()).value() : null;
        if (registry == null) {
            NekoJS.LOGGER.error("Registry '{}' collected objects but no such vanilla registry exists; content NOT registered", key);
            // The sink throws: each entry lands in DrainResult.errors (source=platform-register, with the
            // definition, registry and node) instead of counting as registered; the entries are still
            // drained, so nothing is left behind.
            current.drainFor(key, (reg, id, supplier) -> {
                throw new IllegalStateException("no such vanilla registry '" + key + "' on fabric; content NOT registered");
            }).errors().forEach(error -> NekoJS.LOGGER.error(
                    "[registry-startup] {} in registry '{}' (node {}, source {}): {}",
                            error.definition(), error.registry() == null ? "?" : error.registry().identifier(),
                            error.node(), error.source(), error.message()));
            return;
        }
        current.drainFor(key, (reg, id, supplier) -> register(registry, id, supplier.get()))
                .errors().forEach(error -> NekoJS.LOGGER.error(
                        "[registry-startup] {} in registry '{}' (node {}, source {}): {}",
                                error.definition(), error.registry() == null ? "?" : error.registry().identifier(),
                                error.node(), error.source(), error.message()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void register(Registry<?> registry, Identifier id, Object value) {
        Registry.register((Registry) registry, id, value);
    }

    private static String nodeLabel() {
        try {
            return com.tkisor.nekojs.platform.Platform.getLoaderId() + ":" + com.tkisor.nekojs.platform.Platform.getMcVersion();
        } catch (IllegalStateException notBootstrapped) {
            return "fabric:?";
        }
    }
}
