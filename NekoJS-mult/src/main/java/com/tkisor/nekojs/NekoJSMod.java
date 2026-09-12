//? if neoforge {
package com.tkisor.nekojs;

import com.tkisor.nekojs.bindings.event.CapabilityEvents;
import com.tkisor.nekojs.bindings.event.GoalEvents;
import com.tkisor.nekojs.bindings.static_access.ScriptEventsJS;
import com.tkisor.nekojs.client.NekoJSClient;
import com.tkisor.nekojs.command.NekoJSCommands;
import com.tkisor.nekojs.core.NeoForgePluginLoader;
import com.tkisor.nekojs.core.NeoForgeRuntimeBootstrap;
import com.tkisor.nekojs.core.DefaultScriptEventBridge;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeAssembly;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.listener.RegistryEventAdapter;
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

@Mod(NekoJS.MODID)
public class NekoJSMod extends NekoJS {
    public static IEventBus modEventBus;
    public static NekoRuntimeRoot RUNTIME_ROOT;
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
        registerEventListeners(modEventBus);
        initializeWorkspace();
        initializeScripts();
        registerClient(modEventBus);
    }

    private static void registerEventListeners(IEventBus modEventBus) {
        modEventBus.addListener(NekoJSMod::onCommonSetup);
        // 通用注册表适配：收集（首 pass 前一次）→ 逐 pass 抽干 → 属性/创造页后置
        modEventBus.addListener(RegistryEventAdapter::onRegister);
        modEventBus.addListener(RegistryEventAdapter::onEntityAttributeCreation);
        modEventBus.addListener(RegistryEventAdapter::onBuildCreativeTabContents);
        modEventBus.addListener(NekoJSMod::onRegisterCapabilities);
        NeoForge.EVENT_BUS.addListener(NekoJSCommands::register);
        // GoalRegistry 钩子已中立化（Entity+Level 签名），这里解包原生事件
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class,
                event -> GoalRegistry.onEntityJoinLevel(event.getEntity(), event.getLevel()));
        // 实体持久化数据存取桥：NeoForge 容器 = Entity#getPersistentData()（实体就在调用现场，
        // 此处直接解引用成 id 再交给 store——镜像/同步层同样按 id 记账）
        com.tkisor.nekojs.api.inject.EntityPDataStore.install(new com.tkisor.nekojs.api.inject.EntityPDataStore.Access() {
            @Override
            public net.minecraft.nbt.CompoundTag get(int entityId, String key) {
                var container = pdataContainer(entityId);
                if (container == null) return new net.minecraft.nbt.CompoundTag();
//? if >=26 {
                return container.getCompound(key).orElseGet(net.minecraft.nbt.CompoundTag::new).copy();
//?} else {
/*                return container.getCompound(key).copy();
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
        });
        modEventBus.addListener(NekoJSMod::onLoadComplete);
    }

    /** 能力注册：先跑脚本（startup 脚本在 mod 构造期已加载监听），再应用 pending。 */
    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        CapabilityRegistryEventJS eventJS = new CapabilityRegistryEventJS();
        CapabilityEvents.REGISTER.post(eventJS);
        eventJS.apply(event);
    }

    /** 实体 id → 持久化容器（NeoForge 容器挂实体上；id→实体经全维度查表，仅服务器线程调用）。 */
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

    private void initializeScripts() {
        NeoForgePluginLoader.loadAnnotatedPlugins();
        // 共享装配序列（两 loader 同构）：插件 bootstrap → 事件面接线 → 引擎上下文/沙盒工厂/
        // 模块管线绑定 → root 构造 → manager 创建+discover → STARTUP load → fireInitStartup。
        // loader 差异（插件发现、接线顺序）留在本类；产物由本 entry 私有持有。
        RUNTIME_ROOT = NekoRuntimeAssembly.assemble(
                this.scriptEventBridge,
                this.scriptProperties,
                NekoJSBasePluginManager.getOwnedPlugins(),
                pluginRuntime -> {
                    this.scriptEventsRegistrar.bindRuntime(pluginRuntime);
                    ((DefaultScriptEventBridge) this.scriptEventBridge).setPluginRuntime(pluginRuntime);
                }).root();
        GoalEvents.postRegister();
    }

    private static void registerClient(IEventBus modEventBus) {
        // dist 访问器的版本差异由 McPlatformCompat 门面承载（本文件 neoforge 面，门面可用）
        if (McPlatformCompat.get().isClientDist()) {
            NekoJSClient.register(modEventBus);
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
