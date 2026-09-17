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
        registerEventListeners(modEventBus);
        // 新一轮启动的注册 epoch：丢弃并诊断上一轮残留（ticket 15 AC2）
        RegistryEventAdapter.beginBoot();
        initializeWorkspace();
        // root 只由本 entry 构造期持有（final local），不落任何 static 字段：
        // 生命周期 handle 经 bind/register 注入各边界（AC3/AC10：无公开 static root）
        NekoRuntimeRoot root = initializeScripts();
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> NekoJSCommands.register(event, root));
        PDataSyncListener.bind(root);
        ServerEventListener.bind(root);
        PackSyncClientConnections.bind(root);
        registerClient(modEventBus, root);
    }

    private static void registerEventListeners(IEventBus modEventBus) {
        modEventBus.addListener(NekoJSMod::onCommonSetup);
        // 通用注册表适配：收集（首 pass 前一次）→ 逐 pass 抽干 → 属性/创造页后置
        modEventBus.addListener(RegistryEventAdapter::onRegister);
        modEventBus.addListener(RegistryEventAdapter::onEntityAttributeCreation);
        modEventBus.addListener(RegistryEventAdapter::onBuildCreativeTabContents);
        modEventBus.addListener(NekoJSMod::onRegisterCapabilities);
        // （命令监听器在构造期 root 就绪后注册，见构造函数）
        // GoalRegistry 钩子已中立化（Entity+Level 签名），这里解包原生事件
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class,
                event -> GoalRegistry.onEntityJoinLevel(event.getEntity(), event.getLevel()));
        // 实体持久化数据存取桥：NeoForge 容器 = Entity#getPersistentData()。
        // 实体引用面直接解引用（调用现场持有实体）——EntityJoinLevelEvent 窗口内实体尚未
        // 进入 level 实体索引，按 id 反查会静默丢弃脚本写入（票 03 §3-3，票 18 修复）；
        // id 面保留 server 反查，供同步/镜像层按 id 记账
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

            @Override
            public net.minecraft.nbt.CompoundTag get(net.minecraft.world.entity.Entity entity, String key) {
//? if >=26 {
                return entity.getPersistentData().getCompound(key).orElseGet(net.minecraft.nbt.CompoundTag::new).copy();
//?} else {
/*                return entity.getPersistentData().getCompound(key).copy();
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

    private NekoRuntimeRoot initializeScripts() {
        NeoForgePluginLoader.loadAnnotatedPlugins();
        // 共享装配序列（两 loader 同构）：插件 bootstrap → 事件面接线 → 引擎上下文/沙盒工厂/
        // 模块管线绑定 → root 构造 → manager 创建+discover → STARTUP load → fireInitStartup。
        // loader 差异（插件发现、接线顺序）留在本类；产物由本 entry 私有持有。
        NekoRuntimeRoot root = NekoRuntimeAssembly.assemble(
                this.scriptEventBridge,
                this.scriptProperties,
                NekoJSBasePluginManager.getOwnedPlugins(),
                pluginRuntime -> {
                    this.scriptEventsRegistrar.bindRuntime(pluginRuntime);
                    ((DefaultScriptEventBridge) this.scriptEventBridge).setPluginRuntime(pluginRuntime);
                }).root();
        // 票 39：Item/Block modification domain owner 注册进 root（进程级基线由 root
        // 生命周期持有；收集/应用挂 reload 的 DOMAIN_PLAN 阶段与服务器启动收集点）。
        root.registerDomainCollector(new com.tkisor.nekojs.wrapper.event.server.ModificationDomainOwner());
        // 票 16：动态注册 facade 的候选域收集器（进程级单例）注册进同一 root 接缝——
        // reload 的 DOMAIN_PLAN 阶段统一收集 inert 定义计划。facade 是 26.x NeoForge 面
        // （fabric 未移植），故整行带版本守卫。
//? if >=26 {
        root.registerDomainCollector(com.tkisor.nekojs.dynamic.DynamicRegistryFacade.runtime());
//?}
        GoalEvents.postRegister();
        return root;
    }

    private static void registerClient(IEventBus modEventBus, NekoRuntimeRoot root) {
        // dist 访问器的版本差异由 McPlatformCompat 门面承载（本文件 neoforge 面，门面可用）
        if (McPlatformCompat.get().isClientDist()) {
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
