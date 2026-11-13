package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

/**
 * Registers all built-in extension points through the same {@link NekoPluginExtensionProvider}
 * path that third-party plugins use. This manifest is the <b>explicit index</b> of built-in
 * points: adding one means a self-contained point file plus one line here.
 *
 * <p><b>Built-ins run first:</b> bootstrap calls this plugin before any third-party provider
 * callback (the engine guarantees this for the roots of the dependency graph, see
 * {@code NekoPluginBootstrap#collect}), so it does not depend on plugin priority and a
 * third-party plugin cannot get in ahead of it. The bootstrap constructs this class directly
 * instead of finding it through the {@code @RegisterNekoJSPlugin} scan.
 *
 * <p>Two points close over per-bootstrap state ({@code script_properties} and
 * {@code bindings}): their values arrive as constructor arguments, and registration builds
 * those points through static factories.
 */
public final class NekoBuiltinPointsPlugin implements NekoJSPlugin, NekoPluginExtensionProvider {

    private final ScriptPropertyRegistry scriptProperties;
    private final boolean client;
    private final java.util.function.Function<NekoJSPlugin, String> owners;

    public NekoBuiltinPointsPlugin(ScriptPropertyRegistry scriptProperties, boolean client) {
        this(scriptProperties, client, plugin -> null);
    }

    NekoBuiltinPointsPlugin(ScriptPropertyRegistry scriptProperties, boolean client,
                           java.util.function.Function<NekoJSPlugin, String> owners) {
        this.scriptProperties = scriptProperties;
        this.client = client;
        this.owners = owners;
    }

    @Override
    public void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry) {
        registry.register(ScriptCompilersPoint.POINT);
        registry.register(ScriptPropertiesPoint.point(scriptProperties));
        registry.register(BindingsPoint.point(client, owners));
        registry.register(AdaptersPoint.POINT);
        registry.register(TypeDocsPoint.POINT);
        registry.register(NodeTypeDocsPoint.POINT);
        registry.register(NodeModulesPoint.POINT);
        registry.register(EventsPoint.point(owners));
        registry.register(ClientEventsPoint.point(owners));
        registry.register(RecipeNamespacesPoint.POINT);
        registry.register(RecipeSchemasPoint.POINT);
        registry.register(RecipeLifecyclePoint.POINT);
        registry.register(LifecyclePoint.POINT);
        registry.register(ProbeBackendsPoint.POINT);
    }
}
