package com.tkisor.nekojs.api;

import com.tkisor.nekojs.core.module.NodeModuleRegister;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.fs.JSConfigModel;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.surface.ApiContributionRegistry;
import com.tkisor.nekojs.probe.ProbeBackendRegistry;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.core.plugin.RecipeLifecycleRegister;
import com.tkisor.nekojs.core.plugin.RecipeNamespaceRegister;
import com.tkisor.nekojs.core.plugin.RecipeSchemaRegister;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.core.plugin.PluginLifecycleRegister;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.AttachedData;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.LangGeneratorJS;

/**
 * NekoJS 插件接口。合并自原 {@code NekoJSBasePlugin}、{@code NekoJSPlugin} 与 {@code RecipeLifecyclePlugin}。
 *
 * <p>所有扩展点都在 common 模块、不直接依赖 Minecraft / NeoForge；平台层通过
 * {@code @RegisterNekoJSPlugin} 自动发现 {@code implements NekoJSPlugin} 的类。
 */
public interface NekoJSPlugin {
    /** Core builtin plugin priority — guarantees earliest load (adapters/bindings register first). */
    int CORE_PRIORITY = Integer.MAX_VALUE;

    default void registerScriptCompilers(ScriptCompilerRegistry registry) {
    }

    default void registerScriptProperty(ScriptPropertyRegistry registry) {
    }

    default void registerBinding(BindingRegistry registry) {
    }

    default void registerApiSurface(ApiContributionRegistry registry) {
    }

    default void registerAdapters(JSTypeAdapterRegistry registry) {
    }

    default void registerTypeDocs(TypeDocsRegister registry) {
    }

    /**
     * 注册 node 模块的补全声明（{@code declare module 'node:xxx' {...}}）。
     *
     * <p>与 {@link #registerNodeModules} 配对：前者提供模块实现，本方法提供模块类型声明。
     * 两者分离，使 node 模块的实现与声明同源 ——
     * 内置实现 {@code NodeModuleTypeDocs} 会扫描 {@code modules.list} 中的
     * {@code .ts}（解析类型注解）/ {@code .js+JSDoc}（扫描注释）模块自动生成声明，
     * 未带类型信息的内置模块则回退到手写声明。
     *
     * <p>插件可覆盖此方法以提供自定义 node 模块声明，或追加额外声明。
     */
    default void registerNodeTypeDocs(TypeDocsRegister registry) {
    }

    /**
     * 注册插件自定义 JS 模块（CommonJS 风格），脚本可通过 {@code require('moduleId')} 加载。
     *
     * <p>补全声明需另行通过 {@link #registerNodeTypeDocs} /
     * {@link TypeDocsRegister#registerManualDeclaration} 注册
     * {@code declare module 'moduleId' {...}}（probe 输出到 {@code @manual/index.d.ts}）。
     */
    default void registerNodeModules(NodeModuleRegister registry) {
    }

    /**
     * 注册 probe backend（按 {@code (languageId, name)} 二维登记）。
     *
     * <p>内置已注册 {@code ("typescript", "builtin")} backend（产出 {@code .d.ts}）。
     * 第三方插件可在此注册其他语言的 backend（如 {@code ("python", "builtin")} 产出 {@code .pyi}），
     * 或为已有语言提供替代 backend。同一 {@code (语言, 名字)} 在 bootstrap 完成时报冲突。
     *
     * <p>命令 {@code /nekojs probe [language] [name]} 可指定运行哪些 backend。
     */
    default void registerProbeBackends(ProbeBackendRegistry registry) {
    }

    default void registerEvents(EventGroupRegistry registry) {
    }

    default void registerClientEvents(EventGroupRegistry registry) {
    }

    default void registerRecipeNamespaces(RecipeNamespaceRegister registry) {
    }

    default void registerRecipeSchemas(RecipeSchemaRegister registry) {
    }

    /**
     * 注册配方生命周期钩子。默认实现注册 {@link #beforeRecipeLoading} 和 {@link #afterRecipes}
     * （原 {@code RecipeLifecyclePlugin} 语义），插件按需覆盖这两个便捷方法即可。
     */
    default void registerRecipeLifecycleHooks(RecipeLifecycleRegister registry) {
        registry.beforeRecipeLoading(this::beforeRecipeLoading);
        registry.afterRecipes(this::afterRecipes);
    }

    default void beforeRecipeLoading(RecipeLifecycleContext context) {
    }

    default void afterRecipes(RecipeLifecycleContext context) {
    }

    /**
     * 注册插件生命周期钩子。默认实现注册 {@link #init} / {@link #initStartup} / {@link #afterInit}
     * 以及 {@link #beforeScriptsLoaded} / {@link #afterScriptsLoaded}，插件按需覆盖对应便捷方法即可。
     */
    default void registerLifecycleHooks(PluginLifecycleRegister registry) {
        registry.onInit(this::init);
        registry.onInitStartup(this::initStartup);
        registry.onAfterInit(this::afterInit);
        registry.onBeforeScriptsLoaded(this::beforeScriptsLoaded);
        registry.onAfterScriptsLoaded(this::afterScriptsLoaded);
    }

    /** 最早触发：plugin runtime bootstrap 完成后、startup 脚本加载前。 */
    default void init() {
    }

    /** startup 脚本加载完成后触发。 */
    default void initStartup() {
    }

    /** 所有 mod 初始化完成（对应 NeoForge FMLLoadCompleteEvent）后触发。 */
    default void afterInit() {
    }

    /** 每次某个类型的脚本加载前触发（含首次加载与完整 reload，不含单文件热重载）。 */
    default void beforeScriptsLoaded(ScriptType type) {
    }

    /** 每次某个类型的脚本加载后触发（含首次加载与完整 reload，不含单文件热重载）。 */
    default void afterScriptsLoaded(ScriptType type) {
    }

    /**
     * 向 {@code MinecraftServer} 挂载自定义内存数据。首次访问 {@code server.data} 时由平台层触发。
     * 纯内存、不持久化；需要持久化请用 {@code pdata}。需要宿主时：{@code (MinecraftServer) data.getParent()}。
     */
    default void attachServerData(AttachedData<?> data) {
    }

    /** 向 {@code Level} 挂载自定义内存数据，首次访问 {@code level.data} 时触发。 */
    default void attachLevelData(AttachedData<?> data) {
    }

    /** 向 {@code Player} 挂载自定义内存数据，首次访问 {@code player.data} 时触发。 */
    default void attachPlayerData(AttachedData<?> data) {
    }

    /**
     * jsconfig.json 写盘前修改其模型（paths/include/typeRoots 等）。每个 ScriptType（env）各触发一次，
     * 对应 server/client/startup/test 脚本目录。默认空实现。
     */
    default void modifyWorkspaceConfig(JSConfigModel model, String env) {
    }

    /**
     * 生成 datapack 数据（写入 {@code <gameDir>/nekojs/data}，随服务器资源 reload 生效）。
     * 在 {@code ServerEvents.generateData} 脚本事件之前触发，与脚本共享同一 generator 实例。
     * 每次服务器资源 reload 都会重新触发。
     */
    default void generateData(DataGeneratorJS generator) {
    }

    /**
     * 生成资源包资产（写入 {@code <gameDir>/nekojs/assets}，随客户端资源 reload 生效）。
     * 在 {@code ClientEvents.generateAssets} 脚本事件之前触发，与脚本共享同一 generator 实例。
     * 每次客户端资源 reload（F3+T）都会重新触发。
     */
    default void generateAssets(DataGeneratorJS generator) {
    }

    /**
     * 生成语言条目（按语言代码聚合，合并写入 {@code <gameDir>/nekojs/assets/lang/<lang>.json}）。
     * 在 {@code ClientEvents.lang} 脚本事件之前触发，与脚本共享同一 generator 实例。
     */
    default void generateLang(LangGeneratorJS generator) {
    }
}
