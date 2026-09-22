package com.example.addon;

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
import com.tkisor.nekojs.core.plugin.Sealable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fixture addon mimicking a future third-party plugin: it is compiled only
 * against the public engine surface ({@code common} main output), carries both
 * loader metadata files in its jar, and exercises the documented addon channels:
 *
 * <ul>
 *   <li>hook projection: {@code registerBinding} contributes the
 *       {@code ExampleAddon} global binding;</li>
 *   <li>provider channel: registers the custom extension point
 *       {@code exampleaddon:greetings}, which depends on {@code nekojs:bindings}
 *       and derives its frozen product from that point's result;</li>
 *   <li>callback face: {@code init()} counts plugin bootstrap executions.</li>
 * </ul>
 */
@RegisterNekoJSPlugin(priority = 1200)
public final class ExampleAddonPlugin implements NekoPluginExtensionProvider, GreetingContributor {

    /** Extension point id owned by this fixture addon (namespaced by its mod id). */
    public static final String POINT_ID = "exampleaddon:greetings";

    /** Global binding name contributed through the hook projection. */
    public static final String BINDING_NAME = "ExampleAddon";

    /** Handle of {@link #POINT_ID}, captured at registration (readable after finish). */
    public static final AtomicReference<NekoPluginExtensionHandle<Greetings>> GREETINGS_HANDLE =
            new AtomicReference<>();

    /** Accumulator handed to the last collection round (frozen-seal observation seam). */
    public static final AtomicReference<GreetingAccumulator> LAST_ACCUMULATOR =
            new AtomicReference<>();

    /** Frozen product of {@code exampleaddon:greetings}: collected greetings plus the bindings count. */
    public record Greetings(List<String> greetings, int serverBindingCount) {
        public Greetings {
            greetings = List.copyOf(greetings);
        }
    }

    /** Sealable accumulator: after the finisher runs, further collection is rejected. */
    public static final class GreetingAccumulator extends ArrayList<String> implements Sealable {
        /** Server binding count observed through the frozen nekojs:bindings product. */
        public final int knownServerBindings;
        private boolean sealed;

        public GreetingAccumulator(int knownServerBindings) {
            this.knownServerBindings = knownServerBindings;
        }

        @Override
        public void seal() {
            sealed = true;
        }

        @Override
        public boolean add(String greeting) {
            if (sealed) {
                throw new IllegalStateException(
                        "exampleaddon:greetings accumulator is sealed after finish");
            }
            return super.add(greeting);
        }
    }

    @Override
    public void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry) {
        ExampleAddonSurface.get().noteRegistration();
        GREETINGS_HANDLE.set(registry.register(
                NekoPluginExtensionPoint.<GreetingContributor, GreetingAccumulator, Greetings>builder(
                        POINT_ID, GreetingContributor.class)
                        .merge(MergePolicy.append())
                        // timing dependency on the built-in bindings point: greetings must finish
                        // after nekojs:bindings so its initializer may read the frozen binding map
                        .dependsOnId("nekojs:bindings")
                        .initializer(ExampleAddonPlugin::newAccumulator)
                        .collector((plugin, accumulator) -> {
                            LAST_ACCUMULATOR.set(accumulator);
                            accumulator.add(plugin.greeting());
                        })
                        .finish(ExampleAddonPlugin::finishGreetings)
                        .build()));
    }

    private static GreetingAccumulator newAccumulator(NekoPluginExtensionContext context) {
        // data dependency on the frozen nekojs:bindings product (ordering declared via dependsOnId)
        Map<ScriptType, Map<String, Binding>> bindings = context.result("nekojs:bindings", mapType());
        int serverBindings = bindings == null ? 0
                : bindings.getOrDefault(ScriptType.SERVER, Map.of()).size();
        return new GreetingAccumulator(serverBindings);
    }

    private static Greetings finishGreetings(GreetingAccumulator accumulator) {
        Greetings greetings = new Greetings(List.copyOf(accumulator), accumulator.knownServerBindings);
        ExampleAddonSurface.get().publishSummary("greetings=" + greetings.greetings()
                + ", serverBindings=" + greetings.serverBindingCount());
        return greetings;
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<ScriptType, Map<String, Binding>>> mapType() {
        return (Class<Map<ScriptType, Map<String, Binding>>>) (Class<?>) Map.class;
    }

    @Override
    public void registerBinding(BindingRegistry registry) {
        // contributes the same frozen surface for the script types visible to the collector
        registry.register(ScriptType.SERVER, BINDING_NAME, ExampleAddonSurface.get());
        registry.register(ScriptType.STARTUP, BINDING_NAME, ExampleAddonSurface.get());
    }

    @Override
    public String greeting() {
        return "primary";
    }

    @Override
    public void init() {
        ExampleAddonSurface.get().noteInit();
    }
}
