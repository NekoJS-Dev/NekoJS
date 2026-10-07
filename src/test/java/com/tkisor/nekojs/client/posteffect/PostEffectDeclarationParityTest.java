package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.TypeOutputLayout;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.probe.ProbeBackend;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.ProbeCoordinator;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostEffectDeclarationParityTest {
    @TempDir
    Path temporary;

    @Test
    void productionDeclarationsKeepPayloadAndBindingSeparateOnEachNode() throws Exception {
        try {
            Platform.init(new TestPlatform());
        } catch (IllegalStateException initialized) {
            assertNotNull(Platform.instance());
        }
        Class<?> pluginType = optionalClass("com.tkisor.nekojs.client.posteffect.NekoPostEffectPlugin");
        Class<?> coreType = optionalClass("com.tkisor.nekojs.core.NekoJSCorePlugin");
        if (coreType == null) {
            coreType = Class.forName("com.tkisor.nekojs.fabric.FabricCorePlugin");
        }
        EventGroupRegistry.Impl groups = new EventGroupRegistry.Impl();
        coreType.getMethod("registerClientEvents", EventGroupRegistry.class)
                .invoke(coreType.getDeclaredConstructor().newInstance(), groups);
        var clientEvents = groups.view().get("ClientEvents");
        assertNotNull(clientEvents);
        if (pluginType == null) {
            assertNull(optionalClass("com.tkisor.nekojs.client.posteffect.PostEffectsJS"));
            assertNull(optionalClass("com.tkisor.nekojs.client.posteffect.PostEffectEventJS"));
            assertFalse(clientEvents.viewBuses().containsKey("postEffects"));
            try (var context = graal.graalvm.polyglot.Context.newBuilder("js").allowAllAccess(true).build()) {
                context.getBindings("js").putMember("ClientEvents",
                        new com.tkisor.nekojs.api.event.EventGroupJS(clientEvents, ScriptType.CLIENT));
                var rejected = org.junit.jupiter.api.Assertions.assertThrows(
                        graal.graalvm.polyglot.PolyglotException.class,
                        () -> context.eval("js", "ClientEvents.postEffects(event => {})"));
                assertTrue(rejected.getMessage().contains("postEffects"));
            }
            return;
        }
        BindingRegistry.BindingRegistryImpl bindings = new BindingRegistry.BindingRegistryImpl(ScriptType.CLIENT);
        pluginType.getMethod("registerBinding", BindingRegistry.class)
                .invoke(pluginType.getDeclaredConstructor().newInstance(), bindings);
        Binding binding = bindings.viewRegistered().get("PostEffects");
        assertNotNull(binding);
        var bus = clientEvents.getBusHolder("postEffects").getBus(ScriptType.CLIENT);
        assertNotNull(bus);
        var snapshot = new NekoScriptCatalogSnapshot(
                List.of(ScriptType.CLIENT),
                List.of(BindingCatalogEntry.of(binding.name(), ScriptType.CLIENT, binding.valueType(), false)),
                List.of(EventCatalogEntry.of(bus.groupName(), bus.eventName(), bus.scriptType(),
                        bus.eventType(), null, bus.canCancel(), bus.canDispatch())),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new TypeOutputLayout(Path.of("types"), Path.of("snippets")), Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.client.posteffect"), List.of(), List.of(), List.of(), 1, "SMART"));
        var classes = List.copyOf(ProbeCoordinator.collectClasses(snapshot, config));
        assertTrue(classes.contains(binding.valueType()));
        assertTrue(classes.contains(bus.eventType()));
        var reflector = new TypeReflector();
        var declarations = classes.stream().map(reflector::reflect).toList();
        for (ProbeBackend backend : List.of(new TypeScriptProbeBackend(), new PythonProbeBackend())) {
            String language = backend.languageId();
            var context = new ProbeContext.Of(snapshot, classes, config, NekoJSPaths.fromGameDir(temporary),
                    language, temporary.resolve(language), declarations);
            Map<String, String> first = backend.render(context);
            assertEquals(first, backend.render(context), language + " declarations must be deterministic");
            boolean typescript = language.equals("typescript");
            String classFile = typescript ? "@package/com/tkisor/nekojs/client/posteffect/index.d.ts"
                    : "nekojs/_java/com/tkisor/nekojs/client/posteffect/__init__.pyi";
            String content = first.get(classFile);
            assertNotNull(content, language + " payload/binding declaration module is missing");
            String payload = classBody(content, typescript ? "$PostEffectEventJS" : "PostEffectEventJS");
            String runtime = classBody(content, typescript ? "$PostEffectsJS" : "PostEffectsJS");
            for (String method : List.of("register", "unregister")) {
                assertMethod(payload, method, typescript);
                assertNoMethod(runtime, method, typescript);
            }
            for (String method : List.of("set", "clear", "toggle", "current", "hasDefinition", "installed",
                    "activeGeneration", "isAvailable", "presets")) {
                assertMethod(runtime, method, typescript);
                assertNoMethod(payload, method, typescript);
            }
            assertNoMethod(runtime, "has", typescript);
            assertTrue(payload.contains("declaredCount"));
            assertTrue(runtime.contains("active"));
            String eventFile = typescript ? "@side-only/client/events/index.d.ts"
                    : "nekojs/_events/client/__init__.pyi";
            String events = first.get(eventFile);
            assertNotNull(events);
            assertTrue(events.lines().anyMatch(line -> line.contains("postEffects(")
                    && line.contains(typescript ? "$PostEffectEventJS" : "PostEffectEventJS")));
            String globalFile = typescript ? "@side-only/client/bindings/index.d.ts" : "nekojs/__init__.pyi";
            String globals = first.get(globalFile);
            assertNotNull(globals);
            assertTrue(globals.contains(typescript ? "PostEffects: $PostEffectsJS" : "PostEffects: PostEffectsJS"));
        }
    }

    private static String classBody(String content, String name) {
        int start = content.indexOf("class " + name);
        assertTrue(start >= 0, "Missing declaration class " + name);
        var headers = java.util.regex.Pattern.compile("(?m)^\\s*(?:export )?(?:declare )?class\\s+").matcher(content);
        int next = headers.find(start + 1) ? headers.start() : content.length();
        return content.substring(start, next);
    }

    private static void assertMethod(String content, String name, boolean typescript) {
        assertTrue(hasMethod(content, name, typescript), "Missing declaration method " + name);
    }

    private static void assertNoMethod(String content, String name, boolean typescript) {
        assertFalse(hasMethod(content, name, typescript), "Unexpected declaration method " + name);
    }

    private static boolean hasMethod(String content, String name, boolean typescript) {
        return content.lines().map(String::trim)
                .anyMatch(line -> line.startsWith((typescript ? "" : "def ") + name + "("));
    }

    private static Class<?> optionalClass(String name) throws ClassNotFoundException {
        try {
            return Class.forName(name, false, PostEffectDeclarationParityTest.class.getClassLoader());
        } catch (ClassNotFoundException absent) {
            return null;
        }
    }

    private final class TestPlatform implements IPlatform {
        public boolean isClient() { return false; }
        public boolean isDevelopment() { return true; }
        public String getMcVersion() { return "0.0.0"; }
        public Path getGameDir() { return temporary; }
        public Map<String, IModInfo> getMods() { return Map.of(); }
        public IModInfo getInfo(String modId) { return null; }
        public String getLoaderId() { return "test"; }
        public String getLoaderVersion() { return "0.0.0"; }
    }
}
