package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.bindings.static_access.ScriptEventsJS;
import com.tkisor.nekojs.command.AddressArgument;
import com.tkisor.nekojs.core.DefaultScriptEventBridge;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeAssembly;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.fabric.event.FabricBlockEventBindings;
import com.tkisor.nekojs.fabric.event.FabricCommandEventBindings;
import com.tkisor.nekojs.fabric.event.FabricEntityEventBindings;
import com.tkisor.nekojs.fabric.event.FabricLevelEventBindings;
import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import com.tkisor.nekojs.fabric.event.FabricServerEventBindings;
import com.tkisor.nekojs.platform.FabricIdCompat;
import com.tkisor.nekojs.platform.FabricPlatform;
import com.tkisor.nekojs.platform.NekoIdCompat;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.script.ScriptBootstrap;
import com.tkisor.nekojs.script.WorkspaceGenerator;
import com.tkisor.nekojs.stage.StageDomainOwner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric entry: brings up the script runtime.
 *
 * <p>The assembly order matches {@code NekoJSMod} in the shared tree: plugin discovery -> V2
 * bootstrap -> engine context / sandbox factory / runtime root -> STARTUP script load ->
 * shared registry collection and drain. Two loader differences: plugin discovery goes through
 * {@link FabricPluginLoader} (built-in list plus entrypoint), and registries have no per-pass
 * event, so {@link FabricRegistryAdapter} registers them directly in one batch.
 *
 * <p>The current Fabric script surface includes neutral bindings, shared registries, recipe
 * scripts (recipes/afterRecipes and ingredient actions), GoalEvents, BlockEvents, ServerEvents
 * lifecycle/tick, PlayerEvents, EntityEvents, ItemEvents, ClientEvents, Stages/StageEvents,
 * entity/player/level/server extensions, packet distribution, ClientData/PData network channels,
 * and ScriptEvents custom events.
 */
public final class NekoJSFabricMod extends NekoJS implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("NekoJS-Fabric");

    /**
     * The runtime root that this entry owns privately; there is no public static root. The
     * Fabric entrypoint is instantiated reflectively by the loader, so it cannot be injected
     * through the constructor; the composition family in the same package reads the root
     * through the package-private {@link #runtimeRootOrNull()}. The root is still assembled
     * only once, in this entry.
     */
    private static volatile NekoRuntimeRoot runtimeRoot;

    static NekoRuntimeRoot runtimeRootOrNull() {
        return runtimeRoot;
    }

    /** Flushes CLIENT-side node timers on the client tick; same role as NekoJSClient on other loaders. */
    public static void flushClientNodeTimers() {
        if (runtimeRoot != null) {
            runtimeRoot.flushReadyNodeTimers(com.tkisor.nekojs.api.ScriptType.CLIENT);
        }
    }

    static {
        Platform.init(new FabricPlatform());
        NekoIdCompat.init(new FabricIdCompat());
        // Platform data source for the probe/workspace catalog (registry types, host extensions,
        // snippets). Runtime member remapping is shared; without this only typings and docs are
        // missing (see docs/fabric-port-status.md).
        com.tkisor.nekojs.api.catalog.NekoScriptCatalog.setPlatformProvider(new FabricCatalogPlatformProvider());
    }

    private final ScriptEventsJS scriptEventsRegistrar;

    public NekoJSFabricMod() {
        this(new ScriptEventsJS());
    }

    private NekoJSFabricMod(ScriptEventsJS scriptEventsRegistrar) {
        super(new DefaultScriptEventBridge(scriptEventsRegistrar));
        this.scriptEventsRegistrar = scriptEventsRegistrar;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("NekoJS fabric entrypoint reached.");
        LOGGER.info("  platform: loader={} v{}, mc={}, dev={}, client={}",
                Platform.instance().getLoaderId(),
                Platform.instance().getLoaderVersion(),
                Platform.instance().getMcVersion(),
                Platform.instance().isDevelopment(),
                Platform.instance().isClient());
        // Registration epoch for a new boot: discard and report anything left over from the previous one.
        FabricRegistryAdapter.beginBoot();

        FabricBlockEventBindings.register();
        FabricEntityEventBindings.register();
        FabricLevelEventBindings.register();
        FabricCommandEventBindings.register();
        // Attaches the inventoryChanged listener through a fabric-api lifecycle callback; no mixin needed.
        FabricPlayerEventBindings.registerLifecycle();
        FabricServerEventBindings.register(() -> {
            if (runtimeRoot != null) {
                runtimeRoot.reload(com.tkisor.nekojs.api.ScriptType.SERVER);
            }
        }, () -> runtimeRoot);
        FabricPackSync.registerServer();
        FabricPlayNetwork.registerServer();
        FabricPDataSync.registerServer();
        // AddressArgument wire registration: vanilla ClientboundCommandsPacket resolves argument
        // infos via ArgumentTypeInfos.BY_CLASS plus a numeric command_argument_type registry id —
        // the same client-sync contract as NekoJSArgumentTypes in the shared tree. An unmapped
        // class throws "Unrecognized argument type" during world entry; Fabric's
        // ArgumentTypeRegistry fills both maps. Runs here so it precedes any server start.
        ArgumentTypeRegistry.registerArgumentType(
                Identifier.fromNamespaceAndPath(NekoJS.MODID, "address"),
                AddressArgument.class,
                SingletonArgumentInfo.contextFree(AddressArgument::address));
        // The /nekojs command tree: FabricNekoJSCommands, the Fabric subset with the same names and
        // behavior as the command surface in the shared tree.
        FabricNekoJSCommands.registerCallback(NekoJSFabricMod::runtimeRootOrNull);
        initializeWorkspace();
        try {
            initializeScripts();
        } catch (com.tkisor.nekojs.core.config.EngineConfigException failure) {
            LOGGER.error("NekoJS script initialization stopped; correct the engine configuration and restart: {}", failure.getMessage());
            return;
        }
        FabricRegistryAdapter.onInitialize();
        com.tkisor.nekojs.bindings.event.CapabilityEvents.postAndApply();
        // The final step once every registration and load is done. On Fabric,
        // FabricRegistryAdapter.onInitialize has already drained the registries, so this sits at the
        // same completion point.
        NekoRuntimeAccess.get().fireAfterInit();
        LOGGER.info("NekoJS {} fabric bootstrap done (startup scripts loaded, registry drained).", NekoJS.MODID);
    }

    /** Same order as {@code NekoJSMod#initializeWorkspace} in the shared tree: create folders -> generate
     * default scripts -> fill in missing folders -> workspace config. */
    private static void initializeWorkspace() {
        NekoJSPaths paths = NekoJSPaths.get();
        paths.initFolders();
        ScriptBootstrap.generateDefaultScripts();
        paths.initFolders();
        WorkspaceGenerator.setupWorkspace();
    }

    /** Builds the engine using the shared {@code NekoRuntimeAssembly} and Fabric plugin discovery. */
    private void initializeScripts() {
        FabricPluginLoader.loadPlugins();
        // The shared assembly sequence. Loader differences stay in this class
        // (FabricPluginLoader discovery, the setPluginRuntime -> bindRuntime wiring order), and
        // the result is held privately by this entry.
        runtimeRoot = NekoRuntimeAssembly.assemble(
                this.scriptEventBridge,
                this.scriptProperties,
                NekoJSBasePluginManager.getOwnedPlugins(),
                pluginRuntime -> {
                    ((DefaultScriptEventBridge) this.scriptEventBridge).setPluginRuntime(pluginRuntime);
                    this.scriptEventsRegistrar.bindRuntime(pluginRuntime);
                }).root();
        com.tkisor.nekojs.mixinjs.MixinPlatform.install(runtimeRoot);
        // Registers the Item/Block modification domain owner into the root, the same way NekoJSMod does.
        runtimeRoot.installAuthoringControlHost(new com.tkisor.nekojs.authoring.MinecraftAuthoringControlHost(
                runtimeRoot, com.tkisor.nekojs.fabric.event.FabricServerEventBindings::currentServer,
                Platform.instance().isClient()
                        ? com.tkisor.nekojs.authoring.AuthoringClientPlatform.create(runtimeRoot) : null));
        runtimeRoot.registerDomainCollector(new com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner());
        StageDomainOwner stageDomainOwner = new StageDomainOwner();
        stageDomainOwner.bindReloadRoot(runtimeRoot);
        runtimeRoot.registerDomainCollector(stageDomainOwner);
        // Fabric has no registry mutation adapter for villager trades, so this registers an explicitly
        // unavailable owner. Collection still goes through the same events, and preflight rejects the
        // whole batch with the unavailable reason; it is not a silent no-op.
        com.tkisor.nekojs.wrapper.event.server.VillagerTradeUnavailableDomainOwner villagerTrades =
                new com.tkisor.nekojs.wrapper.event.server.VillagerTradeUnavailableDomainOwner();
        runtimeRoot.registerDomainCollector(villagerTrades);
        com.tkisor.nekojs.core.villager.VillagerTradesFacade.install(villagerTrades.state());
        // Fires goal registration after the STARTUP scripts load, mirroring NekoJSMod: the script
        // listeners only exist by then. GoalEvents on the node side provides the registration API, and
        // FabricEntityEventBindings already consumes it.
        com.tkisor.nekojs.bindings.event.GoalEvents.postRegister();
    }
}
