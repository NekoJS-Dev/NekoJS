package com.tkisor.nekujs;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventGroupJS;
import com.tkisor.nekojs.api.event.EventSchemaRegistry;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.KubeEvent;
import graal.graalvm.polyglot.Context;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Uses unchanged Additions definitions and the shipping facade, not the historical proof ABI. */
@Tag("fixtures")
class FixtureEventMetadataTest {
    @Test
    void originalPayloadTypesReachTheNativeBusAndGuestCallback() throws Exception {
        Path jar = Path.of(System.getProperty("nekujs.fixtures.dir"))
                .resolve("kubejsadditions-neoforge-1.21.1-6.0.0.jar");
        try (var loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> definitions = Class.forName("pie.ilikepiefoo.events.AdditionalEvents", true, loader);
            assertSame(loader, definitions.getClassLoader());
            assertEquals(jar.toUri().toURL(), definitions.getProtectionDomain().getCodeSource().getLocation());
            EventGroup common = (EventGroup) definitions.getField("GROUP").get(null);
            EventGroup arch = (EventGroup) definitions.getField("ARCH_EVENTS").get(null);
            var registry = new com.tkisor.nekojs.api.event.EventGroupRegistry.Impl();
            common.register(registry, false);
            arch.register(registry, false);
            Class<?> registryPayload = Class.forName("pie.ilikepiefoo.events.custom.ArchEventRegisterEventJS", false, loader);
            Class<?> respawnPayload = Class.forName("pie.ilikepiefoo.events.PlayerRespawnEventJS", false, loader);
            var nativeCommon = registry.view().get("CommonAddedEvents");
            assertSame(registryPayload, EventSchemaRegistry.resolve("ArchEvents", "registry"));
            assertSame(respawnPayload, nativeCommon.getBusHolder("playerRespawn").getBus(ScriptType.SERVER).eventType());
            assertNotNull(registryPayload.getMethod("register", String.class, Class.class, String.class));
            EventHandler respawn = (EventHandler) definitions.getField("PLAYER_RESPAWN").get(null);
            try (Context context = Context.newBuilder("js").allowAllAccess(true)
                    .option("engine.WarnInterpreterOnly", "false").build()) {
                ScriptContextRegistry.bind(context, ScriptType.SERVER);
                try {
                    context.getBindings("js").putMember("CommonAddedEvents", new EventGroupJS(nativeCommon, ScriptType.SERVER));
                    context.eval("js", "globalThis.results = []; CommonAddedEvents.playerRespawn(event => { results.push(event.causedByDeath()); });");
                    KubeEvent payload = (KubeEvent) respawnPayload.getConstructor(ServerPlayer.class, boolean.class).newInstance(null, false);
                    assertTrue(respawn.post(dev.latvian.mods.kubejs.script.ScriptType.SERVER, payload).pass());
                    assertEquals(1, context.eval("js", "results.length").asInt());
                    assertTrue(context.eval("js", "results[0]").asBoolean());
                } finally {
                    nativeCommon.clearListeners(ScriptType.SERVER);
                    ScriptContextRegistry.unbind(context);
                }
            }
            assertFalse(respawn.hasListeners());
        }
    }
}
