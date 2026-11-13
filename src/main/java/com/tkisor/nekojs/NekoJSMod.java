//? if neoforge {
package com.tkisor.nekojs;

import com.tkisor.nekojs.bindings.event.CapabilityEvents;
import com.tkisor.nekojs.bindings.event.GoalEvents;
import com.tkisor.nekojs.bindings.static_access.ScriptEventsJS;
import com.tkisor.nekojs.client.NekoJSClient;
import com.tkisor.nekojs.command.NekoJSArgumentTypes;
import com.tkisor.nekojs.command.NekoJSCommands;
import com.tkisor.nekojs.core.NeoForgePluginLoader;
import com.tkisor.nekojs.core.NeoForgeRuntimeBootstrap;
import com.tkisor.nekojs.core.DefaultScriptEventBridge;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeAssembly;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.listener.PDataSyncListener;
import com.tkisor.nekojs.listener.RegistryEventAdapter;
import com.tkisor.nekojs.listener.ServerEventListener;
import com.tkisor.nekojs.network.PackSyncClientConnections;
import com.tkisor.nekojs.platform.NekoIdCompat;
import com.tkisor.nekojs.platform.NeoForgeIdCompat;
import com.tkisor.nekojs.platform.NeoForgePlatform;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.script.ScriptBootstrap;
import com.tkisor.nekojs.script.WorkspaceGenerator;
import com.tkisor.nekojs.wrapper.entity.GoalRegistry;
import com.tkisor.nekojs.wrapper.event.registry.CapabilityRegistryEventJS;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import com.tkisor.nekojs.platform.compat.McPlatformCompat;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(NekoJS.MODID)
public class NekoJSMod extends NekoJS {
    public static IEventBus modEventBus;
    private final ScriptEventsJS scriptEventsRegistrar;

    static {
        Platform.init(new NeoForgePlatform());
        NekoIdCompat.init(new NeoForgeIdCompat());
    }

    public NekoJSMod(IEventBus modEventBus, ModContainer modContainer) {
        this(new ScriptEventsJS(), modEventBus);
    }

    private NekoJSMod(ScriptEventsJS scriptEventsRegistrar, IEventBus modEventBus) {
        super(new DefaultScriptEventBridge(scriptEventsRegistrar));
        this.scriptEventsRegistrar = scriptEventsRegistrar;
        NekoJSMod.modEventBus = modEventBus;

        NeoForgeRuntimeBootstrap.setup();
        // Custom argument types must map to wire infos before any client command sync
        // (AddressArgument in /nekojs trust); console dispatch alone never exercises it.
        NekoJSArgumentTypes.register(modEventBus);
        registerEventListeners(modEventBus);
        // Registration epoch for the new startup: discard and diagnose residue from the previous round
        RegistryEventAdapter.beginBoot();
        initializeWorkspace();
        com.tkisor.nekojs.mixinjs.MixinWorkspace.prepare();
        // Root is held only by this entry's constructor (a final local) and no static fields are set.
        // Lifecycle handles are injected into other code through bind/register; there is no public
        // static root.
        NekoRuntimeRoot root = initializeScripts();
        com.tkisor.nekojs.mixinjs.MixinPlatform.install(root);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> NekoJSCommands.register(event, root));
        PDataSyncListener.bind(root);
        ServerEventListener.bind(root);
        PackSyncClientConnections.bind(root);
        registerClient(modEventBus, root);
    }

    private static void registerEventListeners(IEventBus modEventBus) {
        modEventBus.addListener(NekoJSMod::onCommonSetup);
        // General registry adaptation: Collection (once before first pass) -> Drain per pass -> Property/creative page post-processing
        modEventBus.addListener(RegistryEventAdapter::onRegister);
        modEventBus.addListener(RegistryEventAdapter::onEntityAttributeCreation);
        modEventBus.addListener(RegistryEventAdapter::onBuildCreativeTabContents);
        modEventBus.addListener(NekoJSMod::onRegisterCapabilities);
        // (Command listener registered after root is ready in constructor, see constructor)
        // GoalRegistry hook is neutralized (Entity+Level signature), native events are unwrapped here
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class,
                event -> GoalRegistry.onEntityJoinLevel(event.getEntity(), event.getLevel()));
        // Entity persistent data access bridge: on NeoForge the container is Entity#getPersistentData().
        // Entity references are dereferenced directly (the call site holds the entity). Entities
        // inside the EntityJoinLevelEvent window are not yet in the level entity index, so an id
        // lookup would silently drop script writes. Id lookups from the server are kept for the
        // bookkeeping done by the sync/mirror layer.
        com.tkisor.nekojs.api.inject.EntityPDataStore.install(new com.tkisor.nekojs.api.inject.EntityPDataStore.Access() {
            @Override
            public net.minecraft.nbt.CompoundTag get(int entityId, String key) {
                var container = pdataContainer(entityId);
                if (container == null) return new net.minecraft.nbt.CompoundTag();
//? if >=26 {
                return container.getCompound(key).orElseGet(net.minecraft.nbt.CompoundTag::new).copy();
//?} else {
                /*return container.getCompound(key).copy();
*///?}
            }

            @Override
            public void set(int entityId, String key, net.minecraft.nbt.CompoundTag tag) {
                var container = pdataContainer(entityId);
                if (container == null) return;
                if (tag.isEmpty()) {
                    container.remove(key);
                } else {
                    container.put(key, tag.copy());
                }
            }

            @Override
            public net.minecraft.nbt.CompoundTag get(net.minecraft.world.entity.Entity entity, String key) {
//? if >=26 {
                return entity.getPersistentData().getCompound(key).orElseGet(net.minecraft.nbt.CompoundTag::new).copy();
//?} else {
                /*return entity.getPersistentData().getCompound(key).copy();
*///?}
            }

            @Override
            public void set(net.minecraft.world.entity.Entity entity, String key, net.minecraft.nbt.CompoundTag tag) {
                var container = entity.getPersistentData();
                if (tag.isEmpty()) {
                    container.remove(key);
                } else {
                    container.put(key, tag.copy());
                }
            }
        });
        modEventBus.addListener(NekoJSMod::onLoadComplete);
    }

    /** Capability registration: run the scripts first (startup scripts have already loaded their
     * listeners during mod construction), then apply the pending registrations. */
    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        CapabilityRegistryEventJS eventJS = new CapabilityRegistryEventJS();
        CapabilityEvents.REGISTER.postForCollection(eventJS);
        eventJS.apply(event);
    }

    /** Entity id to persistent container. On NeoForge the container lives on the entity; the id is
     * resolved by scanning all levels. Server thread only. */
    private static net.minecraft.nbt.CompoundTag pdataContainer(int entityId) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        for (var level : server.getAllLevels()) {
            var entity = level.getEntity(entityId);
            if (entity != null) return entity.getPersistentData();
        }
        return null;
    }

    private static void initializeWorkspace() {
        NekoJSPaths paths = NekoJSPaths.get();
        paths.initFolders();
        ScriptBootstrap.generateDefaultScripts();
        paths.initFolders();
        WorkspaceGenerator.setupWorkspace();
    }

    private NekoRuntimeRoot initializeScripts() {
        NeoForgePluginLoader.loadAnnotatedPlugins();
        // Shared assembly sequence, the same on both loaders: plugin bootstrap, event surface wiring,
        // engine context and sandbox factory and module pipeline binding, root construction, manager
        // creation and discover, STARTUP load, fireInitStartup. Loader-specific parts (plugin
        // discovery, wiring order) stay in this class, and the result is held privately by this
        // entry.
        NekoRuntimeRoot root = NekoRuntimeAssembly.assemble(
                this.scriptEventBridge,
                this.scriptProperties,
                NekoJSBasePluginManager.getOwnedPlugins(),
                pluginRuntime -> {
                    this.scriptEventsRegistrar.bindRuntime(pluginRuntime);
                    ((DefaultScriptEventBridge) this.scriptEventBridge).setPluginRuntime(pluginRuntime);
                }).root();
        // Registers the Item/Block modification domain owner with the root. The root lifetime holds the
        // process-level baseline; collection and application hang off the reload DOMAIN_PLAN phase
        // and the server-start collection point.
        root.installAuthoringControlHost(new com.tkisor.nekojs.authoring.MinecraftAuthoringControlHost(
                root, net.neoforged.neoforge.server.ServerLifecycleHooks::getCurrentServer,
                McPlatformCompat.get().isClientDist()
                        ? com.tkisor.nekojs.authoring.AuthoringClientPlatform.create(root) : null));
        root.registerDomainCollector(new com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner());
        // Registers the candidate domain collector of the dynamic registration facade (a process-level
        // singleton) on the same root: the reload DOMAIN_PLAN phase collects the inert definition
        // plans together. The facade is a 26.x NeoForge surface that has not been ported to Fabric,
        // so the line is version-guarded.
        //? if >=26 {
        root.registerDomainCollector(com.tkisor.nekojs.dynamic.DynamicRegistryFacade.runtime());
//?}
// Villager trades domain owner: event collection and the registry mutation at the legal
// commit point both live in the Adapter, and the root lifetime holds the domain ledger. The
// node difference is only in the implementation: 26.x uses the VILLAGER_TRADE / TRADE_SET
// reloadable registries, and the paired owner on 1.21.1 uses the classic static trade pool.
        com.tkisor.nekojs.wrapper.event.server.VillagerTradeDomainOwner villagerTrades =
                new com.tkisor.nekojs.wrapper.event.server.VillagerTradeDomainOwner();
        root.registerDomainCollector(villagerTrades);
        // Process-level lookup for read-only queries (the binding is stateless; install happens once,
        // during assembly)
        com.tkisor.nekojs.core.villager.VillagerTradesFacade.install(villagerTrades.state());
        GoalEvents.postRegister();
        return root;
    }

    private static void registerClient(IEventBus modEventBus, NekoRuntimeRoot root) {
        // McPlatformCompat carries the version differences of the dist accessor; this file is the
        // NeoForge side, where the facade is available
        if (McPlatformCompat.get().isClientDist()) {
            // Registers the post-effect declaration domain owner with the root. It is only registered on the
            // client dist: the collector runs only in the DOMAIN_PLAN phase of the CLIENT candidate
            // and at the client initial-load collection point, and a dedicated server registers no
            // entry and loads no client classes. Runtime set/clear/toggle/current still go through
            // the PostEffects binding, not this owner.
            root.registerDomainCollector(new com.tkisor.nekojs.client.posteffect.PostEffectDomainOwner());
            // Collector for the HUD / world renderer registration domain (likewise client dist only). It
            // also submits an empty batch when this generation registers no renderer, so the commit
            // point retires the previous generation's renderers. Without this collector the empty
            // batch never reaches the commit and the old table is never replaced.
            root.registerDomainCollector(new com.tkisor.nekojs.client.render.ClientRenderDomainOwner());
            // Client wiring for the dynamic registration batch transaction (dropping staged prepare hooks
            // when the connection is lost). The guard reason matches the facade registration line
            // above: the dynamic registration surface is a >=26 NeoForge surface.
            //? if >=26 {
            com.tkisor.nekojs.dynamic.DynamicRegistryClientSync.install();
//?}
            NekoJSClient.register(modEventBus, root);
        }
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(WorkspaceGenerator::createWorkspaceConfigs);
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            RegistryEventAdapter.onLoadComplete();
            NekoRuntimeAccess.get().fireAfterInit();
        });
    }
}
//?}
