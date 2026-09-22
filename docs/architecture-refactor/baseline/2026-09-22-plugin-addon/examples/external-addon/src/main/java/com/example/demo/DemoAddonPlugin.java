package com.example.demo;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.core.plugin.MergePolicy;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionContext;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionHandle;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionPoint;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionProvider;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal runnable external NekoJS addon (ticket 08 example).
 *
 * <p>Compile against the published nekojs fat jar only — the classes referenced
 * here (NekoJSPlugin, RegisterNekoJSPlugin, the binding API and the extension
 * point builder) are the public plugin surface. Nothing else is needed: the
 * loaders discover the jar through its metadata files and hand the plugin class
 * to the NekoJS bootstrap.
 */
@RegisterNekoJSPlugin(priority = 1000)
public final class DemoAddonPlugin implements NekoPluginExtensionProvider, Greeter {

    /** Custom extension point id — always namespace it with your mod id. */
    public static final String POINT_ID = "demomod:greetings";

    /** Global binding name this addon contributes to scripts. */
    public static final String BINDING_NAME = "DemoAddon";

    /** Handle captured at registration; readable once the point has finished. */
    public static final AtomicReference<NekoPluginExtensionHandle<Greetings>> HANDLE =
            new AtomicReference<>();

    /** Frozen product of the custom point. */
    public record Greetings(List<String> greetings) {
        public Greetings {
            greetings = List.copyOf(greetings);
        }
    }

    @Override
    public void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry) {
        HANDLE.set(registry.register(NekoPluginExtensionPoint
                .<Greeter, List<String>, Greetings>builder(POINT_ID, Greeter.class)
                .merge(MergePolicy.append())
                // run after the built-in bindings point so the initializer may read
                // the frozen binding map (data dependency via context.result)
                .dependsOnId("nekojs:bindings")
                .initializer(DemoAddonPlugin::readFrozenBindings)
                .collector((plugin, acc) -> acc.add(plugin.greeting()))
                .finish(Greetings::new)
                .build()));
    }

    private static List<String> readFrozenBindings(NekoPluginExtensionContext context) {
        Map<ScriptType, Map<String, Binding>> bindings =
                context.result("nekojs:bindings", castMapType());
        int count = bindings == null ? 0 : bindings.getOrDefault(ScriptType.SERVER, Map.of()).size();
        List<String> accumulator = new ArrayList<>();
        accumulator.add("bindings=" + count);
        return accumulator;
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<ScriptType, Map<String, Binding>>> castMapType() {
        return (Class<Map<ScriptType, Map<String, Binding>>>) (Class<?>) Map.class;
    }

    @Override
    public void registerBinding(BindingRegistry registry) {
        // script-visible surface: scripts of every generation read the SAME frozen
        // instance — an ordinary /nekojs reload never rebuilds it
        registry.register(ScriptType.SERVER, BINDING_NAME, new DemoAddonSurface());
    }

    @Override
    public String greeting() {
        return "demo";
    }

    /** What scripts see as the {@code DemoAddon} global. */
    public static final class DemoAddonSurface {
        public String marker() {
            return "demomod-frozen-product";
        }
    }
}
