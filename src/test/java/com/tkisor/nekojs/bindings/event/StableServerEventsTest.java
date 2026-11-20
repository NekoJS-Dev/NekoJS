package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingExposure;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.event.ModuleEventGroup;
import com.tkisor.nekojs.api.event.StableServerTickEvent;
import com.tkisor.nekojs.api.recipe.StableRecipeEvent;
import com.tkisor.nekojs.core.api.ModuleEventGroupProxy;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import graal.graalvm.polyglot.Context;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
        var starting = ServerEvents.STARTING;
        var prePayload = new net.neoforged.neoforge.event.tick.ServerTickEvent.Pre(() -> true, server);
        var postPayload = new net.neoforged.neoforge.event.tick.ServerTickEvent.Post(() -> true, server);
//?} else {
        /*new com.tkisor.nekojs.fabric.FabricCorePlugin().registerEvents(registry);
        var pre = com.tkisor.nekojs.fabric.event.FabricServerEventBindings.TICK_PRE;
        var post = com.tkisor.nekojs.fabric.event.FabricServerEventBindings.TICK_POST;
        var starting = com.tkisor.nekojs.fabric.event.FabricServerEventBindings.STARTING;
        var prePayload = new com.tkisor.nekojs.wrapper.event.server.ServerTickEventJS(server);
        var postPayload = new com.tkisor.nekojs.wrapper.event.server.ServerTickEventJS(server);
*///?}
        var publication = registry.moduleGroups().stream()
                .filter(group -> group.category() == BindingExposure.Category.STABLE
                        && group.group().name().equals("ServerEvents"))
                .findFirst().orElseThrow();
        assertEquals("nekojs:event/stable", publication.moduleId());
        var versioned = registry.moduleGroups().stream()
                .filter(group -> group.category() == BindingExposure.Category.VERSION
                        && group.group().name().equals("ServerEvents"))
                .findFirst().orElseThrow();
        assertEquals("nekojs:event/versioned", versioned.moduleId());
        assertEquals(registry.view().get("nekojs:ServerEvents").viewBuses().keySet(), versioned.events());
        assertTrue(versioned.events().containsAll(java.util.Set.of("tickPre", "tickPost", "recipes")));
        for (var source : registry.view().values()) {
            var nativePublication = registry.moduleGroups().stream()
                    .filter(group -> group.category() == BindingExposure.Category.VERSION
                            && group.group().name().equals(source.name()))
                    .findFirst().orElseThrow();
            var stablePublication = registry.moduleGroups().stream()
                    .filter(group -> group.category() == BindingExposure.Category.STABLE
                            && group.group().name().equals(source.name()))
                    .findFirst();
            assertTrue(source.viewBuses().keySet().containsAll(nativePublication.events()), source.name());
            LinkedHashSet<String> publishedEvents = new LinkedHashSet<>(nativePublication.events());
            publishedEvents.addAll(stablePublication.map(ModuleEventGroup::events).orElse(Set.of()));
            assertEquals(source.viewBuses().keySet(), publishedEvents, source.name());
//? if neoforge {
            assertEquals(source.viewBuses().keySet(), nativePublication.events(), source.name());
//?}
        }
        assertEquals(java.util.Set.of(
                "tickPre", "tickPost",
                "aboutToStart", "starting", "started", "stopping", "stopped",
                "datapackSync", "tagsUpdated", "lootTableLoad", "recipes"), publication.events());
        assertTrue(publication.available(ScriptType.SERVER));
        assertFalse(publication.available(ScriptType.CLIENT));
        assertFalse(publication.available(ScriptType.STARTUP));
        assertFalse(publication.available(ScriptType.TEST));
        assertEquals(StableServerTickEvent.class, publication.views().get("tickPre").type());
        assertEquals(StableServerTickEvent.class, publication.views().get("tickPost").type());
        assertEquals(StableRecipeEvent.class, publication.views().get("recipes").type());
        assertFalse(versioned.views().containsKey("recipes"));
        var raw = new ArrayList<Object>();
        try (var context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                context.getBindings("js").putMember("stable", new ModuleEventGroupProxy(context, publication, ScriptType.SERVER));
                context.getBindings("js").putMember("versioned", new ModuleEventGroupProxy(context, versioned, ScriptType.SERVER));
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
                        versioned.tickPre(e => recordRaw(e));
                        """);
                pre.post(prePayload);
//? if fabric {
                /*com.tkisor.nekojs.fabric.event.FabricServerEventBindings.postStableTickPre(server);
*///?}
                post.post(postPayload);
                assertEquals("[37,\"undefined\",\"undefined\",37,37]", context.eval("js", "JSON.stringify(seen)").asString());
                assertEquals(List.of(prePayload), raw);
                assertSame(prePayload, raw.getFirst());
                setNativeTick(server, 91);
                post.post(postPayload);
                assertEquals("[37,\"undefined\",\"undefined\",37,37,91]", context.eval("js", "JSON.stringify(seen)").asString());
                assertEquals(37, context.eval("js", "retained.tick").asInt());
                assertEquals("function", context.eval("js", "typeof stable.starting").asString());
                context.eval("js", "stable.starting(e => seen.push(e.stage, e.tick));");
//? if neoforge {
                starting.post(new net.neoforged.neoforge.event.server.ServerStartingEvent(server));
//?} else {
                /*com.tkisor.nekojs.fabric.event.FabricServerEventBindings.postStableStarting(server);
*///?}
                assertEquals("[37,\"undefined\",\"undefined\",37,37,91,\"starting\",91]",
                        context.eval("js", "JSON.stringify(seen)").asString());
                assertEquals("function", context.eval("js", "typeof stable.recipes").asString());
                assertEquals("function", context.eval("js", "typeof versioned.recipes").asString());
            } finally {
                publication.group().clearListeners(ScriptType.SERVER);
                ScriptContextRegistry.unbind(context);
            }
        }
        assertFalse(pre.hasListeners());
        assertFalse(post.hasListeners());
        assertFalse(starting.hasListeners());
    }

    private static MinecraftServer nativeServer(int tick) throws Exception {
        // The allocated server only needs its tick count and owner thread for event delivery.
        try (var metadata = com.tkisor.nekojs.testfixture.IsolatedNativeLoaderMetadata.open()) {
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var server = (DedicatedServer) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(DedicatedServer.class);
            var thread = MinecraftServer.class.getDeclaredField("serverThread");
            thread.setAccessible(true);
            thread.set(server, Thread.currentThread());
            setNativeTick(server, tick);
            return server;
        }
    }

    private static void setNativeTick(MinecraftServer server, int tick) throws Exception {
        var field = MinecraftServer.class.getDeclaredField("tickCount");
        field.setAccessible(true);
        field.setInt(server, tick);
    }
}
