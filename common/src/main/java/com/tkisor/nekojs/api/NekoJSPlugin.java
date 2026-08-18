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
 *
 * <p>插件是 NekoJS 的 Java 侧扩展入口：注册脚本编译器/全局绑定/类型适配器/事件组、
 * 配方命名空间与 schema、probe backend、生命周期钩子等。加载优先级由
 * {@code @RegisterNekoJSPlugin(priority=...)} 控制（数值大者先加载）；内置 CorePlugin
 * 使用 {@link #CORE_PRIORITY} 保证基础设施（adapters/bindings）最先就位。
 *
 * <p>与脚本侧能力的关系（可并用，插件侧优先级更高或更低视扩展点而定）：
 * <ul>
 *   <li>全局绑定 / 输入别名 → {@link #registerBinding} / {@link #registerAdapters}；</li>
 *   <li>配方命名空间（{@code event.recipes.<ns>.<type>(...)}）→ {@link #registerRecipeNamespaces}
 *       （Java handler）/ {@link #registerRecipeSchemas}（schema 定义）/ 脚本侧
 *       {@code event.registerSchema}（运行时注册，优先级最高）；</li>
 *   <li>probe 类型声明 → {@link #registerProbeBackends} / {@link #registerTypeDocs} / {@link #registerNodeTypeDocs}。</li>
 * </ul>
 */
public interface NekoJSPlugin {
    /** Core builtin plugin priority — guarantees earliest load (adapters/bindings register first). */
    int CORE_PRIORITY = Integer.MAX_VALUE;

    /** 注册脚本编译器（语言插件：TypeScript/JSX 等）。编译器在脚本加载/热重载时被调用。 */
    default void registerScriptCompilers(ScriptCompilerRegistry registry) {
    }

    /** 注册脚本属性（{@code AFTER}/{@code MODLOADED}/{@code DISABLE}/{@code PRIORITY} 等文件头属性）。 */
    default void registerScriptProperty(ScriptPropertyRegistry registry) {
    }

    /**
     * 注册全局绑定（脚本可直接引用的全局名，如 {@code Item}/{@code Ingredient}/{@code RecipeSchema}）。
     * 绑定可以是 Java 类、实例或 {@code Binding.of(...)} 显式声明值类型（供 preflight 校验）。
     */
    default void registerBinding(BindingRegistry registry) {
    }

    /** 注册 API surface 贡献（脚本可见的 Java 类型/符号的表面元数据）。 */
    default void registerApiSurface(ApiContributionRegistry registry) {
    }

    /**
     * 注册 JS↔Java 类型适配器（{@code string → ItemStack} 等参数自动转换）。
     * 适配器同时驱动 probe 的输入别名（{@code $ItemStack_}）生成。
     */
    default void registerAdapters(JSTypeAdapterRegistry registry) {
    }

    /** 注册类型文档（为脚本侧类型补充说明/示例，进入 probe 输出与补全）。 */
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
     * <p>内置 backend（TypeScript {@code .d.ts} 与 Python {@code .pyi}）由 common 的
     * {@code NekoProbeBuiltinPlugin} 经本方法注册——与第三方走同一路径。
     * 第三方可在此注册其他语言的 backend，或为已有语言提供替代 backend
     * （不同 {@code name}）。同一 {@code (语言, 名字)} 在 bootstrap 结束时报冲突。
     *
     * <p>命令 {@code /nekojs probe [language] [name]} 可指定运行哪些 backend。
     */
    default void registerProbeBackends(ProbeBackendRegistry registry) {
    }

    /** 注册服务端事件组（{@code ServerEvents.*}/{@code PlayerEvents.*} 等）。 */
    default void registerEvents(EventGroupRegistry registry) {
    }

    /** 注册客户端事件组（仅客户端运行时可见，如 {@code ClientEvents.*}）。 */
    default void registerClientEvents(EventGroupRegistry registry) {
    }

    /**
     * 注册配方命名空间 Java handler——{@code event.recipes.<namespace>.<method>(...)} 的方法实现层。
     *
     * <p>每个命名空间一个 handler 类：handler 的 public 方法即脚本可调用的配方方法
     * （如 {@code minecraft} 命名空间的 {@code shaped/shapeless/smelting}），方法参数经已注册的
     * {@code JSTypeAdapter} 自动转换（{@code ItemStack}/{@code Ingredient} 等）。调用解析顺序
     * （{@code RecipeNamespaceProxy}）：handler 方法 &gt; schema 类型 &gt; JSON fallback。
     *
     * <p>示例（注册 {@code event.recipes.mytech.machine(...)}）：
     * <pre>{@code
     * registry.register(new RecipeNamespaceEntry(
     *         "mytech",
     *         event -> new MachineRecipeHandler((RecipeEventJS) event),
     *         MachineRecipeHandler.class));
     * }</pre>
     *
     * <p><b>1.12.2 上这是复杂 mod 配方类的首选取舍。</b>自动扫描（{@code LegacyRecipeSchemaScanner}）
     * 只覆盖「无参构造 + 可注入字段」的值对象配方类；构造语义写在代码里的类型
     * （无无参构造、{@code Object...} 参数、工厂注册）无法自动生成可用 builder，应在本方法
     * 提供 handler，或由整合包作者用脚本侧 {@code event.registerSchema} 声明 schema。
     */
    default void registerRecipeNamespaces(RecipeNamespaceRegister registry) {
    }

    /**
     * 注册（或覆盖）配方类型 schema 定义——{@code event.recipes.<ns>.<type>(...)} 的字段与构造描述。
     *
     * <p>schema 描述字段名、字段 kind（{@code RecipeFieldKind}）与构造方式；脚本侧以
     * {@code event.recipes.<ns>.<type>({...})} 或位置参数调用，平台按 schema 反射构造并注册。
     *
     * <p>优先级（{@code RecipeTypeDefinitionStorage}，后者覆盖前者）：
     * 脚本侧 {@code event.registerSchema} &gt; data-driven &gt; 本方法（插件）&gt; 平台自动扫描。
     * 插件可用于：修正自动扫描的字段误判、为无法自动扫描的类型提供精确 schema、
     * 或直接覆盖内置类型（如为 {@code minecraft} 补充类型）。
     *
     * <p>1.12.2 上本方法与脚本侧 {@code event.registerSchema} 语义等价
     * （前者启动期注册、后者配方事件内注册）；选择取决于 schema 由谁维护——
     * mod/整合包作者写 Java 插件用本方法，终端用户在脚本里声明用 registerSchema。
     */
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
