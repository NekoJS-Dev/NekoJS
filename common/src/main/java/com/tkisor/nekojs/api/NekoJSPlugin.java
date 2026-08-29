package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.data.AttachedData;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.surface.ApiContributionRegistry;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.fs.JSConfigModel;
import com.tkisor.nekojs.core.module.NodeModuleRegister;
import com.tkisor.nekojs.core.plugin.PluginLifecycleRegister;
import com.tkisor.nekojs.core.plugin.RecipeLifecycleRegister;
import com.tkisor.nekojs.core.plugin.RecipeNamespaceRegister;
import com.tkisor.nekojs.core.plugin.RecipeSchemaRegister;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;
import com.tkisor.nekojs.probe.ProbeBackendRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.LangGeneratorJS;

/**
 * NekoJS 插件接口 —— 作者面的<b>唯一入口</b>（双形态模型，ADR-0010）。
 *
 * <p>每条通道的<b>事实源</b>是自包含扩展点（{@code core.plugin} 包的 {@code XxxPoint}：
 * MergePolicy / initializer / finisher / dependsOn 全在点文件内）；本接口的方法是各点的
 * <b>门面投影</b>——覆写基接口钩子（推荐、最简）与 implements {@code XxxPoint.Contributor}
 * （显式形态）由同一个点收集，效果完全等价。配对不变式由
 * {@code PluginHookPairingTest} 守护：<b>在本接口加方法必须配对一个 Point 或进入豁免清单</b>，
 * 防止接口无事实源膨胀。通道速查见 wiki「插件开发通道索引」。
 *
 * <p>平台层通过 {@code @RegisterNekoJSPlugin} 自动发现 {@code implements NekoJSPlugin} 的类。
 * 加载优先级由 {@code @RegisterNekoJSPlugin(priority=...)} 控制（数值大者先加载）；内置
 * CorePlugin 使用 {@link #CORE_PRIORITY} 保证基础设施（adapters/bindings）最先就位。
 * <b>同优先级插件的相对顺序为发现/扫描序，跨平台不稳定——不要依赖同优先级插件间的顺序</b>
 * （需要确定性覆盖时用各 registry 的显式 replace/override 语义，如
 * {@code ScriptCompilerRegistry.replaceLanguage}）。
 *
 * <p>钩子分两族：
 * <ul>
 *   <li><b>注册面（收集型）</b>——bootstrap 期把贡献攒进对应通道的注册表；同名冲突由各点的
 *       MergePolicy 裁决（如事件组 failFast）；</li>
 *   <li><b>回调面（直调型）</b>——事件发生时由平台直接触发（与 {@code registerApiSurface}
 *       同类，不做收集式扩展点），覆写即生效，无需任何注册。</li>
 * </ul>
 *
 * <p>与脚本侧能力的关系（可并用，插件侧优先级更高或更低视扩展点而定）：
 * <ul>
 *   <li>全局绑定 / 输入别名 → {@link #registerBinding} / {@link #registerAdapters}；</li>
 *   <li>配方命名空间（{@code event.recipes.<ns>.<type>(...)}）→ {@link #registerRecipeNamespaces}
 *       （Java handler）/ {@link #registerRecipeSchemas}（schema 定义）/ 脚本侧
 *       {@code event.registerSchema}（运行时注册，优先级最高）；</li>
 *   <li>probe 类型声明 → {@link #registerProbeBackends} / {@link #registerTypeDocs} /
 *       {@link #registerNodeTypeDocs}。</li>
 * </ul>
 */
public interface NekoJSPlugin {
    /** Core builtin plugin priority — guarantees earliest load (adapters/bindings register first). */
    int CORE_PRIORITY = Integer.MAX_VALUE;

    // =====================================================================================
    // 注册面（收集型钩子）—— bootstrap 期收集，冲突由各通道 Point 的 MergePolicy 裁决
    // =====================================================================================

    // ---- 事件组 ----

    /** 注册服务端事件组（{@code ServerEvents.*}/{@code PlayerEvents.*} 等）。同名组 failFast。 */
    default void registerEvents(EventGroupRegistry registry) {
    }

    /** 注册客户端事件组（仅客户端运行时可见，如 {@code ClientEvents.*}）。同名组 failFast。 */
    default void registerClientEvents(EventGroupRegistry registry) {
    }

    // ---- 全局绑定与参数转换 ----

    /**
     * 注册全局绑定（脚本可直接引用的全局名，如 {@code Item}/{@code Ingredient}/{@code RecipeSchema}）。
     * 绑定可以是 Java 类、实例或 {@code Binding.of(...)} 显式声明值类型（供 preflight 校验）。
     */
    default void registerBinding(BindingRegistry registry) {
    }

    /**
     * 注册 JS↔Java 类型适配器（{@code string → ItemStack} 等参数自动转换）。
     * 适配器同时驱动 probe 的输入别名（{@code $ItemStack_}）生成。
     */
    default void registerAdapters(JSTypeAdapterRegistry registry) {
    }

    // ---- 脚本语言与文件属性 ----

    /**
     * 注册脚本编译器（语言插件）。编译器在脚本加载/热重载时被调用。
     * 内置语言经 common 插件注册，与第三方走同一路径。
     */
    default void registerScriptCompilers(ScriptCompilerRegistry registry) {
    }

    /** 注册脚本属性（{@code AFTER}/{@code MODLOADED}/{@code DISABLE}/{@code PRIORITY} 等文件头属性）。 */
    default void registerScriptProperty(ScriptPropertyRegistry registry) {
    }

    // ---- 类型声明与文档（probe / 补全）----

    /** 注册类型文档（为脚本侧类型补充说明/示例，进入 probe 输出与补全）。 */
    default void registerTypeDocs(TypeDocsRegister registry) {
    }

    /**
     * 注册 node 模块的补全声明（{@code declare module 'node:xxx' {...}}）。
     * 与 {@link #registerNodeModules} 配对：前者提供模块实现，本方法提供模块类型声明。
     */
    default void registerNodeTypeDocs(TypeDocsRegister registry) {
    }

    /**
     * 注册插件自定义 JS 模块（CommonJS 风格），脚本可通过 {@code require('moduleId')} 加载。
     * 补全声明需另行经 {@link #registerNodeTypeDocs} 注册 {@code declare module} 声明。
     */
    default void registerNodeModules(NodeModuleRegister registry) {
    }

    /**
     * 注册 probe backend（按 {@code (languageId, name)} 二维登记）。同一
     * {@code (语言, 名字)} 在 bootstrap 结束时报冲突；命令 {@code /nekojs probe [language] [name]}
     * 可指定运行哪些 backend。
     */
    default void registerProbeBackends(ProbeBackendRegistry registry) {
    }

    /** 注册 API surface 贡献（脚本可见的 Java 类型/符号的表面元数据；直调型，非收集式扩展点）。 */
    default void registerApiSurface(ApiContributionRegistry registry) {
    }

    // ---- 配方 ----

    /**
     * 注册配方命名空间 Java handler——{@code event.recipes.<namespace>.<method>(...)} 的方法实现层。
     * handler 的 public 方法即脚本可调用的配方方法（如 {@code shaped/shapeless/smelting}），
     * 方法参数经已注册的 {@code JSTypeAdapter} 自动转换。
     * <pre>{@code
     * registry.register(new RecipeNamespaceEntry(
     *         "mytech",
     *         event -> new MachineRecipeHandler((RecipeEventJS) event),
     *         MachineRecipeHandler.class));
     * }</pre>
     */
    default void registerRecipeNamespaces(RecipeNamespaceRegister registry) {
    }

    /**
     * 注册（或覆盖）配方类型 schema 定义——{@code event.recipes.<ns>.<type>(...)} 的字段与构造描述。
     * 优先级（后者覆盖前者）：脚本侧 {@code event.registerSchema} &gt; data-driven &gt; 本方法（插件）
     * &gt; 平台自动扫描。适用于修正自动扫描的字段误判、为无法自动扫描的类型提供精确 schema。
     */
    default void registerRecipeSchemas(RecipeSchemaRegister registry) {
    }

    /**
     * 注册配方生命周期钩子。默认实现注册 {@link #beforeRecipeLoading} 和 {@link #afterRecipes}，
     * 插件按需覆盖这两个便捷方法即可。
     */
    default void registerRecipeLifecycleHooks(RecipeLifecycleRegister registry) {
        registry.beforeRecipeLoading(this::beforeRecipeLoading);
        registry.afterRecipes(this::afterRecipes);
    }

    // ---- 生命周期注册（便捷默认实现）----

    /**
     * 注册插件生命周期钩子。默认实现注册 {@link #init} / {@link #initStartup} / {@link #afterInit}
     * 以及 {@link #beforeScriptsLoaded} / {@link #afterScriptsLoaded}，插件按需覆盖即可。
     */
    default void registerLifecycleHooks(PluginLifecycleRegister registry) {
        registry.onInit(this::init);
        registry.onInitStartup(this::initStartup);
        registry.onAfterInit(this::afterInit);
        registry.onBeforeScriptsLoaded(this::beforeScriptsLoaded);
        registry.onAfterScriptsLoaded(this::afterScriptsLoaded);
    }

    // =====================================================================================
    // 回调面（直调型钩子）—— 事件发生时由平台触发，覆写即生效，无需注册
    // =====================================================================================

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

    /** 配方加载前触发（datagen 与脚本配方解析开始前）。 */
    default void beforeRecipeLoading(RecipeLifecycleContext context) {
    }

    /** 全部配方注册完成后触发。 */
    default void afterRecipes(RecipeLifecycleContext context) {
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

    /** 向玩家挂载自定义内存数据（进入世界时由平台层触发；纯内存、不持久化）。 */
    default void attachPlayerData(AttachedData<?> data) {
    }

    /**
     * 生成 datapack 数据（写入 {@code <gameDir>/nekojs/data}，随服务器资源 reload 生效）。
     * 在 {@code ServerEvents.generateData} 脚本事件之前触发，与脚本共享同一 generator 实例。
     */
    default void generateData(DataGeneratorJS generator) {
    }

    /**
     * 生成资源包资产（写入 {@code <gameDir>/nekojs/assets}，随客户端资源 reload 生效）。
     * 在 {@code ClientEvents.generateAssets} 脚本事件之前触发，与脚本共享同一 generator 实例。
     */
    default void generateAssets(DataGeneratorJS generator) {
    }

    /** 生成语言条目（按语言代码聚合，合并写入 {@code <gameDir>/nekojs/assets/lang/<lang>.json}）。 */
    default void generateLang(LangGeneratorJS generator) {
    }

    /**
     * jsconfig.json 写盘前修改其模型（paths/include/typeRoots 等）。每个 ScriptType（env）各触发一次，
     * 对应 server/client/startup/test 脚本目录。
     */
    default void modifyWorkspaceConfig(JSConfigModel model, String env) {
    }
}
