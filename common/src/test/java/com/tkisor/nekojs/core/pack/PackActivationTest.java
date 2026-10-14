package com.tkisor.nekojs.core.pack;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.NekoModuleTestFixtures;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Engine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PackActivationTest {
    // Platform log appenders retain a Windows handle until the test JVM exits.
    @TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.NEVER) Path temporary;
    @BeforeAll static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test void isolatedContextsJointReplacementFailureAndDisabledConsumer() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            fixture.pack("economy", "", "\"provides\":[\"wallet\"]", "globalThis.privateSecret=42; Pack.provide('wallet', data => ({balance:42})); Recorder.record('economy');");
            fixture.pack("shop", "\"economy\":\"*\"", "\"requires\":{\"economy\":[\"wallet\"]}",
                    "if(typeof privateSecret !== 'undefined') throw Error('context leaked'); const wallet=Pack.require('economy','wallet'); Recorder.record('shop'+wallet({}).balance);");
            fixture.pack("quests", "\"economy\":\"*\"", "", "Recorder.record('quests');");
            fixture.pack("chat", "", "", "Recorder.record('chat');");
            fixture.root.reload(ScriptType.SERVER);
            assertEquals(1, fixture.recorder.calls("shop42"));
            assertEquals(4, fixture.root.packActivations(ScriptType.SERVER).stream().filter(view -> view.status().equals("ACTIVE")).count());
            Map<String, Long> initial = fixture.activations();
            fixture.script("economy", "Pack.provide('wallet', data => ({balance:43})); Recorder.record('economy');");
            var replacement = fixture.root.reloadPack(ScriptType.SERVER, "economy");
            assertTrue(replacement.success(), replacement.toString());
            assertEquals(1, fixture.recorder.calls("shop43"));
            assertEquals(initial.get("GLOBAL:chat"), fixture.activations().get("GLOBAL:chat"));
            assertNotEquals(initial.get("GLOBAL:economy"), fixture.activations().get("GLOBAL:economy"));
            Map<String, Long> healthy = fixture.activations();
            fixture.script("quests", "throw Error('candidate failure');");
            assertFalse(fixture.root.reloadPack(ScriptType.SERVER, "economy").success());
            assertEquals(healthy, fixture.activations());
            fixture.script("quests", "Recorder.record('quests');");
            assertTrue(ScriptPackRegistry.get().setEnabled("GLOBAL:shop", false).succeeded());
            assertTrue(fixture.root.reloadPack(ScriptType.SERVER, "economy").success());
            assertFalse(fixture.activations().containsKey("GLOBAL:shop"));
            assertTrue(fixture.root.reloadPack(ScriptType.SERVER, "economy").success());
            assertFalse(fixture.activations().containsKey("GLOBAL:shop"));
        }
    }

    @Test void sharedWriteConflictsRejectAllCandidatesAndKeepCommittedState() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            fixture.pack("economy", "", "", "Recorder.record('economy');");
            fixture.pack("shop", "\"economy\":\"*\"", "", "Recorder.record('shop');");
            fixture.root.reload(ScriptType.SERVER);
            fixture.root.globalState().sharedStore().put("balance", 7);
            Map<String, Long> healthy = fixture.activations();
            fixture.script("economy", "shared.balance=8;");
            fixture.script("shop", "shared.balance=9;");
            var result = fixture.root.reloadPack(ScriptType.SERVER, "economy");
            assertFalse(result.success(), result.toString());
            assertTrue(result.failure().contains("pack-batch-write-conflict"), result.failure());
            assertEquals(7, fixture.root.globalState().sharedStore().get("balance"));
            assertEquals(healthy, fixture.activations());
        }
    }

    @Test void missingProviderWaitsAndRestoresAfterAppearance() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            fixture.pack("shop", "\"economy\":\"*\"", "\"requires\":{\"economy\":[\"wallet\"]}", "Pack.require('economy','wallet'); Recorder.record('shop');");
            fixture.root.reload(ScriptType.SERVER);
            assertEquals(0, fixture.recorder.calls("shop"));
            fixture.pack("economy", "", "\"provides\":[\"wallet\"]", "Pack.provide('wallet', data => ({balance:1}));");
            assertTrue(fixture.root.reloadPack(ScriptType.SERVER, "economy").success());
            assertEquals(1, fixture.recorder.calls("shop"));
        }
    }

    @Test void worldSessionsReleasePrivateStateAndOldCapabilityHandles() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            Path worldA = temporary.resolve("world-a"), worldB = temporary.resolve("world-b");
            String code = "global.count=(global.count||0)+1; Recorder.record('world'+global.count); Pack.provide('wallet', data=>({balance:1}));";
            fixture.packAt(worldA.resolve("nekojs_packs"), "economy", "", "\"provides\":[\"wallet\"]", code);
            fixture.packAt(worldB.resolve("nekojs_packs"), "economy", "", "\"provides\":[\"wallet\"]", code);
            fixture.packAt(worldA.resolve("nekojs_packs"), "shop", "\"economy\":\"*\"", "\"requires\":{\"economy\":[\"wallet\"]}",
                    "Recorder.keep('old-wallet', Pack.require('economy','wallet')); ");
            ScriptPackRegistry.get().activateWorldPacks(worldA); fixture.root.reload(ScriptType.SERVER);
            String instanceA = fixture.root.packActivations(ScriptType.SERVER).getFirst().sourceInstance();
            var removed = ScriptPackRegistry.get().deactivateWorldPacks(); fixture.root.clearWorldPackListeners(ScriptType.SERVER, removed);
            assertFalse(fixture.activations().containsKey("WORLD:economy"));
            assertThrows(RuntimeException.class, () -> fixture.recorder.handles.get("old-wallet").execute());
            ScriptPackRegistry.get().activateWorldPacks(worldB); fixture.root.reload(ScriptType.SERVER);
            assertNotEquals(instanceA, fixture.root.packActivations(ScriptType.SERVER).getFirst().sourceInstance());
            assertEquals(2, fixture.recorder.calls("world1"));
            removed = ScriptPackRegistry.get().deactivateWorldPacks(); fixture.root.clearWorldPackListeners(ScriptType.SERVER, removed);
            ScriptPackRegistry.get().activateWorldPacks(worldA); fixture.root.reload(ScriptType.SERVER);
            assertEquals(3, fixture.recorder.calls("world1"));
        }
    }

    @Test void candidateClosePreemptsTheWholeBatchAndReleasesRootResources() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            fixture.pack("economy", "", "", "Recorder.record('healthy');"); fixture.root.reload(ScriptType.SERVER);
            fixture.recorder.closeAction = fixture.root::close;
            fixture.script("economy", "Recorder.closeRoot(); Recorder.record('candidate');");
            assertFalse(fixture.root.reloadPack(ScriptType.SERVER, "economy").success());
            assertTrue(fixture.root.globalState().isClosed());
            assertTrue(fixture.root.packActivations(ScriptType.SERVER).isEmpty());
        }
    }

    @Test void activeEventCloseWaitsForGuestReturnAndStopsSiblingCallbacks() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            fixture.pack("economy", "", "", "Recorder.keep('old',()=>42); FixtureEvents.pulse(e=>{Recorder.closeRoot(); Recorder.record('returned');});");
            fixture.pack("shop", "\"economy\":\"*\"", "", "FixtureEvents.pulse(e=>Recorder.record('sibling')); ");
            fixture.root.reload(ScriptType.SERVER);
            fixture.recorder.closeAction = fixture.root::close;
            fixture.pulse.post(new Object());
            assertEquals(1, fixture.recorder.calls("returned"));
            assertEquals(0, fixture.recorder.calls("sibling"));
            assertTrue(fixture.root.globalState().isClosed());
            assertThrows(IllegalStateException.class, () -> fixture.recorder.handles.get("old").execute());
            assertTrue(fixture.root.packActivations(ScriptType.SERVER).isEmpty());
        }
    }

    @Test void timerWithNestedCapabilityCloseWaitsForTheOutermostGuestReturn() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            fixture.pack("economy", "", "\"provides\":[\"wallet\"]", "Pack.provide('wallet',data=>{Recorder.closeRoot(); Recorder.record('provider-returned'); return {balance:42};});");
            fixture.pack("shop", "\"economy\":\"*\"", "\"requires\":{\"economy\":[\"wallet\"]}",
                    "Recorder.keep('old',()=>42); const wallet=Pack.require('economy','wallet'); setTimeout(()=>Recorder.record('consumer'+wallet({}).balance),0);");
            fixture.root.reload(ScriptType.SERVER);
            fixture.recorder.closeAction = fixture.root::close;
            fixture.awaitTimer("consumer42");
            assertEquals(1, fixture.recorder.calls("provider-returned"));
            assertTrue(fixture.root.globalState().isClosed());
            assertThrows(IllegalStateException.class, () -> fixture.recorder.handles.get("old").execute());
        }
    }

    @Test void packageLibrariesStayLocalAndInvalidDescriptorRetainsHealthyActivation() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            fixture.pack("economy", "", "", "Recorder.record('healthy');"); fixture.root.reload(ScriptType.SERVER);
            Map<String, Long> healthy = fixture.activations();
            Path manifest = fixture.paths.packsRoot().resolve("economy/manifest.json");
            String original = Files.readString(manifest); Files.writeString(manifest, original.replace("\"version\":\"1.0.0\"", "\"version\":\"bad\""));
            assertFalse(fixture.root.reloadPack(ScriptType.SERVER, "economy").success());
            assertEquals(healthy, fixture.activations()); Files.writeString(manifest, original);
            Files.writeString(fixture.paths.packsRoot().resolve("outside.js"), "export const answer=42;");
            fixture.script("economy", "import {answer} from '../../outside.js'; Recorder.record('escaped');");
            assertFalse(fixture.root.reloadPack(ScriptType.SERVER, "economy").success());
            assertEquals(healthy, fixture.activations());
        }
    }

    @Test void providerResourceFailureStopsConsumersAndExplicitRecoveryKeepsDisabledIntent() throws Exception {
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true, 0, 10000, 2);
        try (Fixture fixture = new Fixture(temporary, config)) {
            fixture.pack("economy", "", "\"provides\":[\"wallet\"]", "Pack.provide('wallet', data=>{let count=0; while(true) {count++;}});");
            fixture.pack("shop", "\"economy\":\"*\"", "\"requires\":{\"economy\":[\"wallet\"]}",
                    "Recorder.keep('wallet',Pack.require('economy','wallet')); Recorder.record('shop');");
            fixture.pack("chat", "", "", "Recorder.record('chat');");
            fixture.root.reload(ScriptType.SERVER);
            long chat = fixture.activations().get("GLOBAL:chat");
            assertThrows(RuntimeException.class, () -> fixture.recorder.handles.get("wallet").execute("go"));
            fixture.root.flushReadyNodeTimers(ScriptType.SERVER);
            assertFalse(fixture.activations().containsKey("GLOBAL:economy"));
            assertFalse(fixture.activations().containsKey("GLOBAL:shop"));
            assertEquals(chat, fixture.activations().get("GLOBAL:chat"));
            assertTrue(ScriptPackRegistry.get().setEnabled("shop", false).succeeded());
            fixture.script("economy", "Pack.provide('wallet',data=>({balance:1}));");
            assertTrue(fixture.root.reloadPack(ScriptType.SERVER, "economy").success());
            assertFalse(fixture.activations().containsKey("GLOBAL:shop"));
            assertTrue(ScriptPackRegistry.get().setEnabled("shop", true).succeeded());
            assertTrue(fixture.root.reloadPack(ScriptType.SERVER, "economy").success());
            assertEquals(2, fixture.recorder.calls("shop"));
        }
    }

    @Test void listenersTimersAndPartialInitializationKeepTheirActivationOwner() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            fixture.pack("economy", "", "", "FixtureEvents.pulse(e=>Recorder.record('economy')); setTimeout(()=>Recorder.record('economy-timer'),0);");
            fixture.pack("chat", "", "", "FixtureEvents.pulse(e=>Recorder.record('chat')); setTimeout(()=>Recorder.record('chat-timer'),0);");
            fixture.root.reload(ScriptType.SERVER); fixture.root.flushReadyNodeTimers(ScriptType.SERVER);
            fixture.awaitTimer("economy-timer"); fixture.awaitTimer("chat-timer");
            fixture.pulse.post(new Object());
            assertEquals(1, fixture.recorder.calls("economy")); assertEquals(1, fixture.recorder.calls("chat"));
            assertEquals(1, fixture.recorder.calls("economy-timer")); assertEquals(1, fixture.recorder.calls("chat-timer"));
            fixture.script("economy", "FixtureEvents.pulse(e=>Recorder.record('bad-listener')); setTimeout(()=>Recorder.record('bad-timer'),0); throw Error('partial candidate');");
            var rejected = fixture.root.reloadPack(ScriptType.SERVER, "economy");
            assertFalse(rejected.success()); assertNotNull(rejected.report().sourceLocation());
            fixture.root.flushReadyNodeTimers(ScriptType.SERVER); fixture.pulse.post(new Object());
            assertEquals(2, fixture.recorder.calls("economy")); assertEquals(2, fixture.recorder.calls("chat"));
            assertEquals(0, fixture.recorder.calls("bad-listener")); assertEquals(0, fixture.recorder.calls("bad-timer"));
            fixture.script("economy", "FixtureEvents.pulse(e=>Recorder.record('new-listener')); setTimeout(()=>Recorder.record('new-timer'),0);");
            assertTrue(fixture.root.reloadPack(ScriptType.SERVER, "economy").success());
            fixture.root.flushReadyNodeTimers(ScriptType.SERVER); fixture.pulse.post(new Object());
            assertEquals(2, fixture.recorder.calls("economy")); assertEquals(3, fixture.recorder.calls("chat"));
            fixture.awaitTimer("new-timer");
            assertEquals(1, fixture.recorder.calls("new-listener")); assertEquals(1, fixture.recorder.calls("new-timer"));
            fixture.root.close(); fixture.pulse.post(new Object());
            assertEquals(3, fixture.recorder.calls("chat")); assertEquals(1, fixture.recorder.calls("new-listener"));
        }
    }

    @Test void concurrentCloseInterruptsCandidateAndRejectsQueuedReplacement() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            fixture.pack("economy", "", "", "global.balance=7;"); fixture.root.reload(ScriptType.SERVER);
            fixture.recorder.entered = new java.util.concurrent.CountDownLatch(1);
            fixture.script("economy", "Recorder.signal(); while(true) { global.balance=8; }");
            var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var reload = executor.submit(() -> fixture.root.reloadPack(ScriptType.SERVER, "economy"));
                assertTrue(fixture.recorder.entered.await(10, java.util.concurrent.TimeUnit.SECONDS));
                var close = executor.submit(fixture.root::close);
                assertFalse(reload.get(15, java.util.concurrent.TimeUnit.SECONDS).success());
                close.get(15, java.util.concurrent.TimeUnit.SECONDS);
                assertTrue(fixture.root.globalState().isClosed());
                assertTrue(fixture.root.packActivations(ScriptType.SERVER).isEmpty());
                assertFalse(fixture.root.reloadPack(ScriptType.SERVER, "economy").success());
            } finally { executor.shutdownNow(); }
        }
    }

    @Test void capabilityBridgeRejectsNonJsonValuesAndKeepsPlainDataDetached() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            fixture.pack("economy", "", "\"provides\":[\"wallet\"]", "Pack.provide('wallet',data=>data);");
            fixture.pack("shop", "\"economy\":\"*\"", "\"requires\":{\"economy\":[\"wallet\"]}", """
                    const wallet = Pack.require('economy','wallet');
                    for (const value of [undefined, ()=>1, NaN, Infinity, new Date(), new Map(), new Uint8Array([1]), Recorder]) {
                        let rejected=false;
                        try { wallet(value); } catch(e) { rejected=true; }
                        if(!rejected) throw Error('non-JSON value accepted');
                    }
                    const input={nested:[1,true,null,'value']};
                    const output=wallet(input); output.nested[0]=2;
                    if(input.nested[0]!==1 || output.nested[1]!==true) throw Error('JSON data was not detached');
                    Recorder.record('json-verified');
                    """);
            fixture.root.reload(ScriptType.SERVER);
            assertEquals(1, fixture.recorder.calls("json-verified"));
        }
    }

    @Test void concurrentWorldRevocationCancelsPreparationAndPreservesUnrelatedGlobalActivation() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            fixture.pack("chat", "", "", "Recorder.record('chat');");
            Path world = temporary.resolve("world");
            fixture.packAt(world.resolve("nekojs_packs"), "economy", "", "", "global.balance=7;");
            ScriptPackRegistry.get().activateWorldPacks(world); fixture.root.reload(ScriptType.SERVER);
            long chat = fixture.activations().get("GLOBAL:chat");
            fixture.recorder.entered = new java.util.concurrent.CountDownLatch(1);
            Files.writeString(world.resolve("nekojs_packs/economy/server_scripts/main.js"), "Recorder.signal(); while(true) { global.balance=8; }");
            var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var reload = executor.submit(() -> fixture.root.reloadPack(ScriptType.SERVER, "WORLD:economy"));
                assertTrue(fixture.recorder.entered.await(10, java.util.concurrent.TimeUnit.SECONDS));
                var revoke = executor.submit(() -> {
                    var removed = ScriptPackRegistry.get().deactivateWorldPacks();
                    fixture.root.clearWorldPackListeners(ScriptType.SERVER, removed);
                });
                assertFalse(reload.get(15, java.util.concurrent.TimeUnit.SECONDS).success());
                revoke.get(15, java.util.concurrent.TimeUnit.SECONDS);
                assertFalse(fixture.activations().containsKey("WORLD:economy"));
                assertEquals(chat, fixture.activations().get("GLOBAL:chat"));
                assertTrue(fixture.root.reloadPack(ScriptType.SERVER, "chat").success());
            } finally { executor.shutdownNow(); }
        }
    }

    @Test void worldRemovalReconcilesWholeDomainTablesWithoutReinitializingSurvivors() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            var committed = new java.util.concurrent.atomic.AtomicReference<List<String>>(List.of());
            fixture.root.registerDomainCollector(new com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector() {
                public String domain() { return "fixture-contributions"; }
                public ScriptType scriptType() { return ScriptType.SERVER; }
                public void collect(Handle handle) {
                    var contributions = new java.util.ArrayList<String>();
                    handle.dispatch(fixture.pulse, contributions);
                    handle.registerPlan(new com.tkisor.nekojs.core.state.CandidateStatePlan() {
                        public String domain() { return "fixture-contributions"; }
                        public void preflight() {}
                        public void publish() { committed.set(List.copyOf(contributions)); }
                    });
                }
            });
            fixture.pack("chat", "", "", "Recorder.record('chat'); FixtureEvents.pulse(event=>event.add('chat'));");
            Path world = temporary.resolve("contribution-world");
            fixture.packAt(world.resolve("nekojs_packs"), "economy", "", "",
                    "FixtureEvents.pulse(event=>event.add('economy'));");
            fixture.packAt(world.resolve("nekojs_packs"), "shop", "\"economy\":\"*\"", "",
                    "FixtureEvents.pulse(event=>event.add('shop'));");
            ScriptPackRegistry.get().activateWorldPacks(world); fixture.root.reload(ScriptType.SERVER);
            assertEquals(java.util.Set.of("chat", "economy", "shop"), java.util.Set.copyOf(committed.get()));
            long chat = fixture.activations().get("GLOBAL:chat");
            var removed = ScriptPackRegistry.get().deactivateWorldPacks();
            fixture.root.clearWorldPackListeners(ScriptType.SERVER, removed);
            assertEquals(List.of("chat"), committed.get());
            assertEquals(chat, fixture.activations().get("GLOBAL:chat"));
            assertEquals(1, fixture.recorder.calls("chat"));
        }
    }

    @Test void startupAddedAfterLegacyBootRequiresRestartWithoutEvaluatingItsEntry() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            fixture.root.createScriptManager(ScriptType.STARTUP).discoverScripts();
            fixture.root.load(ScriptType.STARTUP);
            fixture.startupPack();
            var result = fixture.root.reloadPack(ScriptType.STARTUP, "GLOBAL:bootpack");
            assertFalse(result.success(), result.toString());
            assertTrue(result.failure().contains("restart"), result.failure());
            assertEquals(0, fixture.recorder.calls("bootpack"));
        }
    }

    @Test void initialManagedStartupRunsOnceAndSubsequentReplacementRequiresRestart() throws Exception {
        try (Fixture fixture = new Fixture(temporary)) {
            fixture.startupPack();
            fixture.root.createScriptManager(ScriptType.STARTUP).discoverScripts();
            fixture.root.load(ScriptType.STARTUP);
            assertEquals(1, fixture.recorder.calls("bootpack"));
            assertFalse(fixture.root.reloadPack(ScriptType.STARTUP, "GLOBAL:bootpack").success());
            assertEquals(1, fixture.recorder.calls("bootpack"));
        }
    }

    public static class Recorder {
        private final Map<String, Integer> counts = new LinkedHashMap<>();
        private final Map<String, graal.graalvm.polyglot.Value> handles = new LinkedHashMap<>();
        Runnable closeAction;
        java.util.concurrent.CountDownLatch entered;
        public void record(String value) { counts.merge(value, 1, Integer::sum); }
        public void keep(String name, graal.graalvm.polyglot.Value handle) { handles.put(name, handle); }
        public void closeRoot() { closeAction.run(); }
        public void signal() { entered.countDown(); }
        int calls(String key) { return counts.getOrDefault(key, 0); }
    }

    private static final class Fixture implements AutoCloseable {
        final NekoJSPaths previous = NekoJSPaths.get();
        final NekoJSPaths paths;
        final Engine engine = Engine.newBuilder().build();
        final Recorder recorder = new Recorder();
        final com.tkisor.nekojs.api.event.EventGroup group = com.tkisor.nekojs.api.event.EventGroup.of("FixtureEvents");
        final com.tkisor.nekojs.api.event.EventBusJS<Object, Void> pulse = group.server("pulse", Object.class);
        final NekoRuntimeRoot root;
        Fixture(Path directory) throws Exception {
            this(directory, SandboxConfig.defaultConfig());
        }
        Fixture(Path directory, SandboxConfig config) throws Exception {
            paths = NekoJSPaths.fromGameDir(directory);
            Field field = NekoJSPaths.class.getDeclaredField("INSTANCE"); field.setAccessible(true); field.set(null, paths);
            ScriptPackRegistry.get().deactivateWorldPacks(); ScriptPackRegistry.get().refreshGlobalPacks();
            IPluginRuntime plugins = (IPluginRuntime) Proxy.newProxyInstance(IPluginRuntime.class.getClassLoader(), new Class[]{IPluginRuntime.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("bindings")) return Map.of("Recorder", Binding.of("Recorder", recorder));
                        if (method.getName().equals("eventGroups")) return Map.of("FixtureEvents", group);
                        if (method.getReturnType() == Map.class) return Map.of();
                        if (method.getReturnType() == List.class) return List.of();
                        return null;
                    });
            var compiler = ScriptCompilerRegistry.createRuntimeRegistry();
            var tracker = new DefaultErrorTracker(paths, config);
            var core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
            var cache = NekoModuleTestFixtures.newCache(paths, compiler, config);
            var sandbox = new NekoSandboxFactory(core, paths, compiler, plugins, cache);
            var properties = new ScriptPropertyRegistry.Impl();
            properties.register(ScriptProperty.AFTER); properties.register(ScriptProperty.MODLOADED);
            properties.register(ScriptProperty.DISABLE); properties.register(ScriptProperty.PRIORITY); properties.freeze();
            var bridge = new com.tkisor.nekojs.core.DefaultScriptEventBridge(null); bridge.setPluginRuntime(plugins);
            root = new NekoRuntimeRoot(core, plugins, bridge, properties, sandbox, cache);
            root.createScriptManager(ScriptType.SERVER).discoverScripts();
        }
        void pack(String id, String dependencies, String capabilities, String code) throws Exception {
            packAt(paths.packsRoot(), id, dependencies, capabilities, code);
        }
        void packAt(Path packs, String id, String dependencies, String capabilities, String code) throws Exception {
            Path directory = packs.resolve(id); Files.createDirectories(directory.resolve("server_scripts"));
            Files.writeString(directory.resolve("manifest.json"), "{\"formatVersion\":1,\"id\":\"" + id
                    + "\",\"version\":\"1.0.0\",\"entries\":{\"server\":[\"main.js\"]},\"dependencies\":{" + dependencies
                    + "},\"capabilities\":{" + capabilities + "}}"); Files.writeString(directory.resolve("server_scripts/main.js"), code);
        }
        void script(String id, String code) throws Exception { Files.writeString(paths.packsRoot().resolve(id).resolve("server_scripts/main.js"), code); }
        void startupPack() throws Exception {
            Path directory = paths.packsRoot().resolve("bootpack"); Files.createDirectories(directory.resolve("startup_scripts"));
            Files.writeString(directory.resolve("manifest.json"), "{\"formatVersion\":1,\"id\":\"bootpack\",\"version\":\"1.0.0\",\"entries\":{\"startup\":[\"main.js\"]}}");
            Files.writeString(directory.resolve("startup_scripts/main.js"), "Recorder.record('bootpack');");
        }
        void awaitTimer(String marker) throws Exception {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            while (recorder.calls(marker) == 0 && System.nanoTime() < deadline) {
                root.flushReadyNodeTimers(ScriptType.SERVER);
                Thread.sleep(5);
            }
            assertEquals(1, recorder.calls(marker), "scheduled callback must run exactly once: " + marker);
        }
        Map<String, Long> activations() {
            Map<String, Long> result = new LinkedHashMap<>();
            root.packActivations(ScriptType.SERVER).stream().filter(view -> view.status().equals("ACTIVE"))
                    .forEach(view -> result.put(view.selector(), view.activation())); return result;
        }
        public void close() throws Exception {
            root.close(); engine.close();
            ScriptPackRegistry.get().deactivateWorldPacks();
            Field field = NekoJSPaths.class.getDeclaredField("INSTANCE"); field.setAccessible(true); field.set(null, previous);
            ScriptPackRegistry.get().refreshGlobalPacks();
        }
    }
}
