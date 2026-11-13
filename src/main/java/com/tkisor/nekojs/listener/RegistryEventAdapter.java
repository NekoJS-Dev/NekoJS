//? if neoforge {
//~ mc_legacy_api
package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.wrapper.registry.gen.EntityTypeBuilder;
import com.tkisor.nekojs.wrapper.registry.gen.ItemBuilder;
import com.tkisor.nekojs.wrapper.registry.gen.StartupRegistryRuntime;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.function.Supplier;

/**
 * NeoForge adapter for the general-purpose registries. It is a thin wiring layer over
 * {@link StartupRegistryRuntime}: collection, validation, fingerprinting, draining and the delivery
 * of dependent entries all happen in the runtime that the version trees share. The runtime holds MC
 * type tokens to validate and key entries but creates no MC objects; MC and loader objects are only
 * created here and in the Fabric counterpart. This class connects each pass of {@link RegisterEvent}
 * to {@code runtime.drainFor}; objects are registered as Suppliers and built by the platform before
 * registration is frozen.
 *
 * <p>Epoch: each game start (mod construction) swaps in a fresh runtime through {@link #beginBoot()}.
 * Leftovers from the previous run are dropped and reported, so a failure never leaves process-wide
 * staging behind that could poison the next start.
 *
 * <p>Also handles two later platform events on the registry side: the entity attribute table,
 * recorded while the builder is built, and creative tab contents, the {@code ItemBuilder} groupTab
 * assignment.
 */
public final class RegistryEventAdapter {
    private RegistryEventAdapter() {}

    private static volatile StartupRegistryRuntime runtime = new StartupRegistryRuntime(nodeLabel());

    /** The runtime for the current epoch, used when wiring the registration passes. */
    public static StartupRegistryRuntime runtime() {
        return runtime;
    }

    /** Starts a new game boot and installs a fresh epoch. If the previous runtime still holds
     * undrained staging, that staging is discarded and reported first. */
    public static void beginBoot() {
        StartupRegistryRuntime previous = runtime;
        if (previous != null && !previous.isFullyDrained()) {
            previous.reportUndelivered(message -> NekoJS.LOGGER.error(
                    "[registry-startup] stale staging from a previous boot discarded: {}", message));
        }
        runtime = new StartupRegistryRuntime(nodeLabel());
    }

    public static void onRegister(RegisterEvent event) {
        ResourceKey<? extends Registry<?>> key = event.getRegistryKey();
        StartupRegistryRuntime current = runtime;
        current.collectOnce();
        current.drainFor(key, (registry, id, supplier) -> registerInto(event, registry, id, supplier))
                .errors().forEach(error -> NekoJS.LOGGER.error(
                        "[registry-startup] {} in registry '{}' (node {}, source {}): {}",
                                error.definition(), error.registry() == null ? "?" : error.registry().identifier(),
                                error.node(), error.source(), error.message()));
    }

    /** Load-complete diagnostics: entries that were collected but no pass consumed, because a registry
     * name is misspelled or the target pass runs before the source. */
    public static void onLoadComplete() {
        runtime.reportUndelivered(message -> NekoJS.LOGGER.error("{}", message));
    }

    /** Entity attribute table: recorded while the builder is built and attached here in one go, after
     * registration has completed. */
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        EntityTypeBuilder.drainPendingAttributes().forEach(event::put);
    }

    /**
     * Creative tab contents: appends the items that {@link ItemBuilder#GROUP_ASSIGNMENTS} assigns to
     * the current tab; scripts assign them with {@code b.groupTab = 'tab_id'}. The event fires after
     * all registration is done, so items can safely be resolved from {@link BuiltInRegistries#ITEM}.
     */
    public static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        // 26.x: ResourceKey.identifier() (the type was renamed to Identifier)
        //? if >=26 {
        var tabId = event.getTabKey().identifier();
//?} else {
        /*var tabId = event.getTabKey().identifier();
*///?}
        ItemBuilder.GROUP_ASSIGNMENTS.entrySet().stream()
                .filter(entry -> entry.getValue().equals(tabId))
                .forEach(entry -> acceptCreativeTabEntry(event, entry.getKey()));
    }

    private static void acceptCreativeTabEntry(BuildCreativeModeTabContentsEvent event, Identifier itemId) {
        // 26.x: Registry.get(Identifier) returns Optional<Reference<Item>>, so take .value()
        //? if >=26 {
        Item item = BuiltInRegistries.ITEM.get(itemId).map(Holder::value).orElse(null);
//?} else {
        /*Item item = BuiltInRegistries.ITEM.get(itemId);
*///?}
        if (item != null && item != Items.AIR) {
            event.accept(new net.minecraft.world.item.ItemStack(item),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    private static String nodeLabel() {
        try {
            return com.tkisor.nekojs.platform.Platform.getLoaderId() + ":" + com.tkisor.nekojs.platform.Platform.getMcVersion();
        } catch (IllegalStateException notBootstrapped) {
            return "neoforge:?";
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerInto(
            RegisterEvent event, ResourceKey<? extends Registry<?>> registry, Identifier id, Supplier<?> supplier) {
        event.register((ResourceKey) registry, id, (Supplier) supplier);
    }
}
//?}
