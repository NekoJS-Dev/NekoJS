package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingExposure;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.event.StableServerTickEvent;
import com.tkisor.nekojs.core.api.ModuleEventGroupProxy;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import graal.graalvm.polyglot.Context;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

// Native server initialization must not share the ordinary suite's partial vanilla bootstrap.
@Tag("platform-gate")
class StableServerEventsTest {
    @Test
    void nativeTickCountProjectsDirectlyWithoutPhaseStateOrNativePayloadLeaks() throws Exception {
        var registry = new EventGroupRegistry.Impl().scopedTo("nekojs");
        var server = nativeServer(37);
//? if neoforge {
        new com.tkisor.nekojs.core.NekoJSCorePlugin().registerEvents(registry);
        var pre = ServerEvents.TICK_PRE;
        var post = ServerEvents.TICK_POST;
        var prePayload = new net.neoforged.neoforge.event.tick.ServerTickEvent.Pre(() -> true, server);
        var postPayload = new net.neoforged.neoforge.event.tick.ServerTickEvent.Post(() -> true, server);
//?} else {
        /*new com.tkisor.nekojs.fabric.FabricCorePlugin().registerEvents(registry);
        var pre = com.tkisor.nekojs.fabric.event.FabricServerEventBindings.TICK_PRE;
        var post = com.tkisor.nekojs.fabric.event.FabricServerEventBindings.TICK_POST;
        var prePayload = new com.tkisor.nekojs.wrapper.event.server.ServerTickEventJS(server);
        var postPayload = new com.tkisor.nekojs.wrapper.event.server.ServerTickEventJS(server);
*///?}
        var publication = registry.moduleGroups().stream()
                .filter(group -> group.category() == BindingExposure.Category.STABLE
                        && group.group().name().equals("ServerEvents"))
                .findFirst().orElseThrow();
        assertEquals("nekojs/api/stable", publication.moduleId());
        assertEquals(java.util.Set.of("tickPre", "tickPost"), publication.events());
        assertFalse(publication.available(ScriptType.CLIENT));
        assertFalse(publication.available(ScriptType.STARTUP));
        assertEquals(StableServerTickEvent.class, publication.views().get("tickPre").type());
        var raw = new ArrayList<Object>();
        try (var context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                context.getBindings("js").putMember("stable", new ModuleEventGroupProxy(context, publication, ScriptType.SERVER));
                context.getBindings("js").putMember("rawPre", pre);
                context.getBindings("js").putMember("recordRaw", (graal.graalvm.polyglot.proxy.ProxyExecutable) values -> {
                    raw.add(values[0].asHostObject());
                    return null;
                });
                context.eval("js", """
                        globalThis.seen = [];
                        stable.tickPre(e => {
                            globalThis.retained = e;
                            seen.push(e.tick, typeof e.server, typeof e.getServer);
                            try { e.tick = 123; } catch (_) {}
                            seen.push(e.tick);
                        });
                        stable.tickPost(e => seen.push(e.tick));
                        rawPre(e => recordRaw(e));
                        """);
                pre.post(prePayload);
                post.post(postPayload);
                assertEquals("[37,\"undefined\",\"undefined\",37,37]", context.eval("js", "JSON.stringify(seen)").asString());
                assertEquals(List.of(prePayload), raw);
                assertSame(prePayload, raw.getFirst());
                setNativeTick(server, 91);
                post.post(postPayload);
                assertEquals("[37,\"undefined\",\"undefined\",37,37,91]", context.eval("js", "JSON.stringify(seen)").asString());
                assertEquals(37, context.eval("js", "retained.tick").asInt());
                assertEquals("undefined", context.eval("js", "typeof stable.starting").asString());
            } finally {
                publication.group().clearListeners(ScriptType.SERVER);
                ScriptContextRegistry.unbind(context);
            }
        }
        assertFalse(pre.hasListeners());
        assertFalse(post.hasListeners());
    }

    private static MinecraftServer nativeServer(int tick) throws Exception {
        // Only getTickCount is exercised; no server thread or world is needed.
        try (var metadata = nativeLoaderMetadata()) {
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var server = (DedicatedServer) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(DedicatedServer.class);
            setNativeTick(server, tick);
            return server;
        }
    }

    private static AutoCloseable nativeLoaderMetadata() throws Exception {
        // NeoForge feature flags need an empty mod list before server class initialization.
//? if neoforge {
//? if <=1.21.1 {
        /*if (net.neoforged.fml.loading.LoadingModList.get() != null) return () -> {};
        var instance = net.neoforged.fml.loading.LoadingModList.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        return () -> instance.set(null, null);
*///?} else {
        if (net.neoforged.fml.loading.FMLLoader.getCurrentOrNull() != null) return () -> {};
        var constructor = net.neoforged.fml.loading.FMLLoader.class.getDeclaredConstructor(
                ClassLoader.class, String[].class, net.neoforged.api.distmarker.Dist.class, boolean.class, java.nio.file.Path.class);
        constructor.setAccessible(true);
        var loader = constructor.newInstance(StableServerEventsTest.class.getClassLoader(), new String[0],
                net.neoforged.api.distmarker.Dist.DEDICATED_SERVER, false, java.nio.file.Path.of("."));
        try {
            var modList = net.neoforged.fml.loading.FMLLoader.class.getDeclaredField("loadingModList");
            modList.setAccessible(true);
            modList.set(loader, net.neoforged.fml.loading.LoadingModList.of(
                    List.of(), List.of(), List.of(), List.of(), List.of(), java.util.Map.of()));
            return loader;
        } catch (Exception failure) {
            loader.close();
            throw failure;
        }
//?}
//?} else {
        /*return () -> {};
*///?}
    }

    private static void setNativeTick(MinecraftServer server, int tick) throws Exception {
        var field = MinecraftServer.class.getDeclaredField("tickCount");
        field.setAccessible(true);
        field.setInt(server, tick);
    }
}
