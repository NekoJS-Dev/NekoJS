package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.catalog.NodeModuleRegister;
import com.tkisor.nekojs.api.catalog.TypeDocsRegister;
import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.probe.ProbeRegistry;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleRegister;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceRegister;
import com.tkisor.nekojs.api.recipe.RecipeSchemaRegister;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.api.lifecycle.PluginLifecycleRegister;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.api.data.AttachedData;

/**
 * NekoJS 插件接口。合并自原 {@code NekoJSBasePlugin}、{@code NekoJSPlugin} 与 {@code RecipeLifecyclePlugin}。
 *
 * <p>所有扩展点都在 common 模块、不直接依赖 Minecraft / NeoForge；平台层通过
 * {@code @RegisterNekoJSPlugin} 自动发现 {@code implements NekoJSPlugin} 的类。
 */
public interface NekoJSPlugin {
    default void registerScriptCompilers(ScriptCompilerRegistry registry) {
    }

    default void registerScriptProperty(ScriptPropertyRegistry registry) {
    }

    default void registerBinding(BindingRegistry registry) {
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
     * 注册自定义探针生成器，替换 NekoJS 内置实现。
     *
     * <p>调用 {@link ProbeRegistry#setGenerator} 即可。替换后，内置的 {@code /nekojs probe} 指令自动使用新实现。
     */
    default void registerProbeGenerator() {
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
}
