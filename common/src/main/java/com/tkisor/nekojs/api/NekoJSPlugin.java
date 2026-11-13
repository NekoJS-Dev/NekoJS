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

import java.util.Set;

/**
 * NekoJS plugin interface—the sole entry point for authors (dual-form model, ADR-0010).
 *
 * <p>The source of truth for each channel is a self-contained extension point ({@code XxxPoint} in the {@code core.plugin} package:
 * MergePolicy / initializer / finisher / dependsOn are all within the point file). Methods in this interface are
 * facade projections of these points—overriding base interface hooks (recommended, simplest) and implementing {@code XxxPoint.Contributor}
 * (explicit form) are collected by the same point, with equivalent effects. Pairing invariants are guaranteed by
 * {@code PluginHookPairingTest}: <b>Methods added to this interface must be paired with a Point or included in the exemption list</b>,
 * to prevent interface bloating without a source of truth. See the "Plugin Development Channel Index" wiki for quick lookups.
 *
 * <p>The platform automatically discovers classes that {@code implements NekoJSPlugin} via {@code @RegisterNekoJSPlugin}.
 * Load priority is controlled by {@code @RegisterNekoJSPlugin(priority=...)} (higher values load first); the built-in
 * CorePlugin uses {@link #CORE_PRIORITY} to ensure infrastructure (adapters/bindings) is ready first.
 * <b>Relative order of plugins with the same priority depends on discovery/scanning order and is unstable across platforms—do not rely on order among plugins of the same priority</b>
 * (use explicit replace/override semantics of registries for deterministic overrides, e.g.,
 * {@code ScriptCompilerRegistry.replaceLanguage}).
 *
 * <p>Hooks are divided into two categories:
 * <ul>
 *   <li><b>Registration side (collection-based)</b>—contributions are collected into the corresponding channel registry during bootstrap; conflicts with the same name are resolved by the point's
 *       MergePolicy (e.g., fail-fast for event groups).</li>
 *   <li><b>Callback side (direct-call)</b>—triggered directly by the platform when an event occurs (similar to {@code registerApiSurface},
 *       not a collection-based extension point); overrides take effect immediately without registration.</li>
 * </ul>
 *
 * <p>Relationship with script-side capabilities (can be used together; plugin priority may be higher or lower depending on the extension point):
 * <ul>
 *   <li>Global bindings / input aliases → {@link #registerBinding} / {@link #registerAdapters};</li>
 *   <li>Recipe namespaces ({@code event.recipes.<ns>.<type>(...)}) → {@link #registerRecipeNamespaces}
 *       (Java handler) / {@link #registerRecipeSchemas} (schema definition) / script-side
 *       {@code event.registerSchema} (runtime registration, highest priority);</li>
 *   <li>Probe type declarations → {@link #registerProbeBackends} / {@link #registerTypeDocs} /
 *       {@link #registerNodeTypeDocs}。</li>
 * </ul>
 */
public interface NekoJSPlugin {
    /** Core builtin plugin priority — guarantees earliest load (adapters/bindings register first). */
    int CORE_PRIORITY = Integer.MAX_VALUE;

    // =====================================================================================
    // Registration side (collection-based hooks): collected during bootstrap; conflicts are resolved by each channel's Point MergePolicy.
    // =====================================================================================

    // ---- Event Groups ----

    /** Registers server event groups (e.g., {@code ServerEvents.*}/{@code PlayerEvents.*}). Groups with the same name fail fast. */
    default void registerEvents(EventGroupRegistry registry) {
    }

    /** Registers client event groups (visible only in client runtime, e.g., {@code ClientEvents.*}). Fail-fast on same-name groups. */
    default void registerClientEvents(EventGroupRegistry registry) {
    }

    // ---- Global Bindings and Parameter Conversion ----

    /**
     * Registers global bindings (global names directly referenced by scripts, e.g., {@code Item}, {@code Ingredient}, {@code RecipeSchema}).
     * Bindings can be Java classes, instances, or explicitly declared value types via {@code Binding.of(...)} (for preflight validation).
     */
    default void registerBinding(BindingRegistry registry) {
    }

    /**
     * Registers JS↔Java type adapters (automatic parameter conversion, e.g., {@code string → ItemStack}).
     * Adapters also drive the generation of input aliases for the probe (e.g., {@code $ItemStack_}).
     */
    default void registerAdapters(JSTypeAdapterRegistry registry) {
    }

    // ---- Script Languages and File Attributes ----

    /**
     * Registers script compilers (language plugins). Compilers are invoked during script loading/hot-reloading.
     * Built-in languages are registered via the common plugin and follow the same path as third-party ones.
     */
    default void registerScriptCompilers(ScriptCompilerRegistry registry) {
    }

    /** Registers script attributes (e.g., {@code AFTER}, {@code MODLOADED}, {@code DISABLE}, {@code PRIORITY} file header attributes). */
    default void registerScriptProperty(ScriptPropertyRegistry registry) {
    }

    // ---- Type Declarations and Documentation (probe / completion) ----

    /** Registers type documentation (supplements explanations/examples for script-side types, included in probe output and completion). */
    default void registerTypeDocs(TypeDocsRegister registry) {
    }

    /**
     * Registers completion declarations for node modules (e.g., {@code declare module 'node:xxx' {...}}).
     * Paired with {@link #registerNodeModules}: the former provides the module implementation, this method provides the module type declaration.
     */
    default void registerNodeTypeDocs(TypeDocsRegister registry) {
    }

    /**
     * Registers plugin-custom JS modules (CommonJS style), loadable via {@code require('moduleId')} in scripts.
     * Completion declarations must be registered separately via {@link #registerNodeTypeDocs} with {@code declare module} declarations.
     */
    default void registerNodeModules(NodeModuleRegister registry) {
    }

    /**
     * Registers probe backends (registered by {@code (languageId, name)}). The same
     * {@code (language, name)} pair conflicts at the end of bootstrap; the command {@code /nekojs probe [language] [name]}
     * can be used to specify which backends to run.
     */
    default void registerProbeBackends(ProbeBackendRegistry registry) {
    }

    /** Registers API surface contributions (surface metadata for script-visible Java types/symbols; direct-call, not a collection-based extension point). */
    default void registerApiSurface(ApiContributionRegistry registry) {
    }

    // ---- Recipes ----

    /**
     * Registers recipe namespace Java handlers—the method implementation layer for {@code event.recipes.<namespace>.<method>(...)}.
     * Public handler methods are script-callable recipe methods (e.g., {@code shaped}, {@code shapeless}, {@code smelting}),
     * with method arguments automatically converted via registered {@code JSTypeAdapter}s.
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
     * Registers (or overrides) recipe type schema definitions—field and constructor descriptions for {@code event.recipes.<ns>.<type>(...)}.
     * Priority (the latter overrides the former): script-side {@code event.registerSchema} > data-driven > this method (plugin)
     * > platform auto-scanning. Useful for fixing field misidentifications during auto-scanning or providing precise schemas for types that cannot be auto-scanned.
     */
    default void registerRecipeSchemas(RecipeSchemaRegister registry) {
    }

    /**
     * Registers recipe lifecycle hooks. Default implementation registers {@link #beforeRecipeLoading} and {@link #afterRecipes};
     * plugins can override these convenient methods as needed.
     */
    default void registerRecipeLifecycleHooks(RecipeLifecycleRegister registry) {
        registry.beforeRecipeLoading(this::beforeRecipeLoading);
        registry.afterRecipes(this::afterRecipes);
    }

    // ---- Lifecycle Registration (Convenient Default Implementation) ----

    /**
     * Registers plugin lifecycle hooks. Default implementation registers {@link #init} / {@link #initStartup} / {@link #afterInit}
     * and {@link #beforeScriptsLoaded} / {@link #afterScriptsLoaded}; plugins can override as needed.
     */
    default void registerLifecycleHooks(PluginLifecycleRegister registry) {
        registry.onInit(this::init);
        registry.onInitStartup(this::initStartup);
        registry.onAfterInit(this::afterInit);
        registry.onBeforeScriptsLoaded(this::beforeScriptsLoaded);
        registry.onAfterScriptsLoaded(this::afterScriptsLoaded);
    }

    // =====================================================================================
    // Callback side (direct-call hooks)—triggered by the platform when an event occurs, override to take effect immediately, no registration required
    // =====================================================================================

    /** Triggered earliest: after plugin runtime bootstrap, before startup script loading. */
    default void init() {
    }

    /** Triggered after startup script loading completes. */
    default void initStartup() {
    }

    /** Triggered after all mod initialization completes (corresponds to NeoForge FMLLoadCompleteEvent). */
    default void afterInit() {
    }

    /** Triggered before scripts of a certain type load (includes initial load and full reload, excludes single-file hot-reload). */
    default void beforeScriptsLoaded(ScriptType type) {
    }

    /** Triggered after scripts of a certain type load (includes initial load and full reload, excludes single-file hot-reload). */
    default void afterScriptsLoaded(ScriptType type) {
    }

    /** Triggered before recipe loading (before datagen and script recipe parsing begin). */
    default void beforeRecipeLoading(RecipeLifecycleContext context) {
    }

    /** Triggered after all recipe registration completes. */
    default void afterRecipes(RecipeLifecycleContext context) {
    }

    /**
     * Mounts custom memory data to {@code MinecraftServer}. Triggered by the platform on the first access to {@code server.data}.
     * Purely in-memory, not persisted; use {@code pdata} for persistence. Use {@code (MinecraftServer) data.getParent()} when the host is needed.
     */
    default void attachServerData(AttachedData<?> data) {
    }

    /** Mounts custom memory data to {@code Level}. Triggered by the platform on the first access to {@code level.data}. */
    default void attachLevelData(AttachedData<?> data) {
    }

    /** Mounts custom memory data to player (triggered by platform upon entering world; purely in-memory, not persisted). */
    default void attachPlayerData(AttachedData<?> data) {
    }

    /**
     * Generates datapack data (written to {@code <gameDir>/nekojs/data}, takes effect on server resource reload).
     * Triggered before {@code ServerEvents.generateData} script events, sharing the same generator instance with scripts.
     */
    default void generateData(DataGeneratorJS generator) {
    }

    /**
     * Generates resource pack assets (written to {@code <gameDir>/nekojs/assets}, takes effect on client resource reload).
     * Triggered before {@code ClientEvents.generateAssets} script events, sharing the same generator instance with scripts.
     */
    default void generateAssets(DataGeneratorJS generator) {
    }

    /**
     * Generates language entries (aggregated by language code, merged and written to {@code <gameDir>/nekojs/assets/lang/<lang>.json}).
     * The platform triggers this once per language, for the union of languages in {@link #generatedLangs()} and this method's call.
     */
    default void generateLang(LangGeneratorJS generator) {
    }

    /**
     * Declares language codes for which this plugin's {@link #generateLang} should be called (e.g., {@code en_us}).
     *
     * <p>Direct-call hook (callback side), similar to {@code generateLang}—override to take effect, <b>no pairing</b> extension point required,
     * and it is <b>not</b> a general-purpose resource declaration system.
     *
     * <p>Default {@code Set.of("en_us")}: even if no script registers a {@code ClientEvents.lang} listener,
     * plugin callbacks will not be silently skipped. Explicit declaration is required for languages other than en_us; the platform unions
     * this declaration with languages from script-keyed listeners and triggers for each.
     * Return value must be a valid language code ({@code [A-Za-z0-9_]{1,64}}); invalid values are rejected before writing any language file.
     *
     * <p><b>{@link #generateLang} is triggered per language for the union</b>: once multiple languages appear in a round (including others' declarations
     * or script listener languages), this plugin's {@code generateLang} will be called once for <b>each</b> language.
     * Plugins producing only a single language must filter by {@link LangGeneratorJS#getLang()} in the callback, e.g.,
     * {@code if (!"ja_jp".equals(generator.getLang())) return;}; otherwise, its entries will be written into all language files.
     */
    default Set<String> generatedLangs() {
        return Set.of("en_us");
    }

    /**
     * Modifies the jsconfig.json model before writing to disk (e.g., paths/include/typeRoots). Triggered once per ScriptType (env),
     * corresponding to server/client/startup/test script directories.
     */
    default void modifyWorkspaceConfig(JSConfigModel model, String env) {
    }
}
