package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.bindings.static_access.ScriptEventsJS;
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
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric 入口：脚本运行时 bring-up。
 *
 * <p>装配序与 NeoForge 侧 {@code NekoJSMod} 一致：插件发现 → V2 bootstrap →
 * 引擎上下文 / 沙盒工厂 / 运行时根 → STARTUP 脚本加载 → 通用注册表收集+抽干。
 * 两处 loader 差异：插件发现走 {@link FabricPluginLoader}（内置清单 + entrypoint）；
 * 注册表无逐 pass 事件，改 {@link FabricRegistryAdapter} 单批直注。
 *
 * <p>尚未接（随后续批次，缺口全录见 {@code docs/fabric-port-status.md}）：JEI 配方查看器、
 * 工作区编辑器 GUI 与其网络包、Modification 重放、网络自定义通道。
 * 当前 fabric 脚本面 = 中性绑定 + 通用注册表 + 配方脚本（recipes/afterRecipes + 原料动作）
 * + GoalEvents + BlockEvents.broken + ServerEvents 生命周期/tick + PlayerEvents 进出服/chat
 * + EntityEvents joinLevel/death/damagePre/damagePost + ItemEvents.rightClicked
 * + ClientEvents tick（CLIENT 脚本）+ Level/Player/Server/MutableComponent 实体扩展
 * + 包分发与 ClientData/PData 网络通道 + ScriptEvents 自定义事件。
 */
public final class NekoJSFabricMod extends NekoJS implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("NekoJS-Fabric");

    public static NekoRuntimeRoot RUNTIME_ROOT;

    /**
     * loader-entry 私有 seam：同包（{@code com.tkisor.nekojs.fabric}）的 composition 家族
     * （NekoJSFabricClient / FabricPackSync / FabricNekoJSCommands）经此处读取 root，
     * Fabric entrypoint 由 loader 反射实例化无法构造注入。root 仍只在本 entry 装配一次
     * （总账 A2：Phase 5 后字段转 private，仅存本 accessor 一个读点）。
     */
    static NekoRuntimeRoot runtimeRootOrNull() {
        return RUNTIME_ROOT;
    }

    /** 客户端 tick 冲刷 CLIENT 侧 node timers（与 NeoForge 侧 NekoJSClient 同职责）。 */
    public static void flushClientNodeTimers() {
        if (RUNTIME_ROOT != null) {
            RUNTIME_ROOT.scriptManagerOf(com.tkisor.nekojs.api.ScriptType.CLIENT).flushReadyNodeTimers();
        }
    }

    static {
        Platform.init(new FabricPlatform());
        NekoIdCompat.init(new FabricIdCompat());
        // probe/workspace 目录的平台数据源（注册表类型、host 扩展、snippets）——运行时
        // 成员重映射是共享的，不装这个只缺 typings/文档面（见 docs/fabric-port-status.md）
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

        FabricBlockEventBindings.register();
        FabricEntityEventBindings.register();
        FabricLevelEventBindings.register();
        FabricCommandEventBindings.register();
        // inventoryChanged 的监听器挂载（fabric-api 生命周期回调，零 mixin）
        FabricPlayerEventBindings.registerLifecycle();
        FabricServerEventBindings.register(() -> {
            if (RUNTIME_ROOT != null) {
                RUNTIME_ROOT.reload(com.tkisor.nekojs.api.ScriptType.SERVER);
            }
        });
        FabricPackSync.registerServer();
        FabricPlayNetwork.registerServer();
        FabricPDataSync.registerServer();
        // /nekojs 指令树（FabricNekoJSCommands，与共享树 NeoForge 版同名同语义的 fabric 子集）
        FabricNekoJSCommands.registerCallback();
        initializeWorkspace();
        initializeScripts();
        FabricRegistryAdapter.onInitialize();
        // 全部注册/装载完成后收尾（NeoForge 侧在 FMLLoadComplete 的 RegistryEventAdapter.onLoadComplete
        // 之后 fire；fabric 侧 FabricRegistryAdapter.onInitialize 即注册抽干完成，同位次）
        NekoRuntimeAccess.get().fireAfterInit();
        LOGGER.info("NekoJS {} fabric bootstrap done (startup scripts loaded, registry drained).", NekoJS.MODID);
    }

    /** 与 NeoForge 侧 {@code NekoJSMod#initializeWorkspace} 同序：建目录 → 生成默认脚本 → 补目录 → 工作区配置。 */
    private static void initializeWorkspace() {
        NekoJSPaths paths = NekoJSPaths.get();
        paths.initFolders();
        ScriptBootstrap.generateDefaultScripts();
        paths.initFolders();
        WorkspaceGenerator.setupWorkspace();
    }

    /** 引擎装配：与 {@code NekoJSMod#initializeScripts} 同构（共享 {@code NekoRuntimeAssembly}），替换插件发现、去掉 neoforge 专属钩子。 */
    private void initializeScripts() {
        FabricPluginLoader.loadPlugins();
        // 共享装配序列（与 NeoForge 侧同构）：loader 差异（FabricPluginLoader 发现、接线顺序
        // setPluginRuntime → bindRuntime）留在本类；产物由本 entry 私有持有。
        RUNTIME_ROOT = NekoRuntimeAssembly.assemble(
                this.scriptEventBridge,
                this.scriptProperties,
                NekoJSBasePluginManager.getOwnedPlugins(),
                pluginRuntime -> {
                    ((DefaultScriptEventBridge) this.scriptEventBridge).setPluginRuntime(pluginRuntime);
                    this.scriptEventsRegistrar.bindRuntime(pluginRuntime);
                }).root();
        // STARTUP 脚本加载后触发 goal 注册（镜像 NekoJSMod：脚本监听器此时才挂上；
        // 注册面由节点孪生 GoalEvents 提供，消费端 FabricEntityEventBindings 已在跑）
        com.tkisor.nekojs.bindings.event.GoalEvents.postRegister();
    }
}
