package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.surface.ApiContributionRegistry;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.AttachedData;

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
 * <b>同优先级插件的相对顺序为发现/扫描序，跨平台不稳定——不要依赖同优先级插件间的顺序</b>
 * （需要确定性覆盖时用各 registry 的显式 replace/override 语义，如
 * {@code ScriptCompilerRegistry.replaceLanguage}）。
 *
 * <p>与脚本侧能力的关系（可并用，插件侧优先级更高或更低视扩展点而定）：
 * <ul>
 *   <li>全局绑定 / 输入别名 → {@link #registerBinding} / {@link #registerAdapters}；</li>
 *   <li>配方命名空间（{@code event.recipes.<ns>.<type>(...)}）→ {@link #registerRecipeNamespaces}
 *       （Java handler）/ {@link #registerRecipeSchemas}（schema 定义）/ 脚本侧
 *       {@code event.registerSchema}（运行时注册，优先级最高）；</li>
 *   <li>probe 类型声明 → {@code probe_backends} / {@code type_docs} / {@code node_type_docs} 扩展点的 Contributor 方法。</li>
 * </ul>
 */
public interface NekoJSPlugin {
    /** Core builtin plugin priority — guarantees earliest load (adapters/bindings register first). */
    int CORE_PRIORITY = Integer.MAX_VALUE;

    /** 注册 API surface 贡献（脚本可见的 Java 类型/符号的表面元数据）。 */
    default void registerApiSurface(ApiContributionRegistry registry) {
    }


    default void beforeRecipeLoading(RecipeLifecycleContext context) {
    }

    default void afterRecipes(RecipeLifecycleContext context) {
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


    /**
     * 向玩家挂载自定义内存数据（进入世界时由平台层触发；纯内存、不持久化）。
     */
    default void attachPlayerData(AttachedData<?> data) {
    }
}
