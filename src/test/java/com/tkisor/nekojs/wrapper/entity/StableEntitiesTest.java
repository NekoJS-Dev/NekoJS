package com.tkisor.nekojs.wrapper.entity;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.entity.StableServerPlayer;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.event.StablePlayerChatEvent;
import com.tkisor.nekojs.bindings.static_access.NativeBridgeJS;
import com.tkisor.nekojs.core.api.BindingValueProxy;
import com.tkisor.nekojs.core.api.ModuleEventGroupProxy;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.testfixture.IsolatedNativeLoaderMetadata;
import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("platform-gate")
class StableEntitiesTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        try (var metadata = IsolatedNativeLoaderMetadata.open()) {
            if (!VanillaRegistryProbe.registryMetadataAvailable()) {
                fail("Native entity fixtures require registry metadata", VanillaRegistryProbe.metadataFailure());
            }
        }
    }

    @Test
    void wrapsTheMostSpecificKindAndPreservesTheExactNativeInstance() throws Exception {
        var item = allocate(ItemEntity.class);
        var mob = allocate(ArmorStand.class);
        var clientPlayer = allocate(RemotePlayer.class);
        var serverPlayer = allocate(ServerPlayer.class);
        var ordinary = StableEntities.wrap((Entity) item);
        var living = StableEntities.wrap((Entity) mob);
        var client = StableEntities.wrap((Entity) clientPlayer);
        var server = StableEntities.wrap((Entity) serverPlayer);

        assertNull(StableEntities.wrap((Entity) null));
        assertNull(ordinary.asLivingEntity());
        assertNull(living.asPlayer());
        assertNull(client.asServerPlayer());
        assertInstanceOf(StableServerPlayer.class, server);
        assertSame(item, StableEntities.unwrap(ordinary));
        assertSame(mob, StableEntities.unwrap(living.asLivingEntity()));
        assertSame(clientPlayer, StableEntities.unwrap(client.asPlayer()));
        assertSame(serverPlayer, StableEntities.unwrap(server.asServerPlayer()));
        assertSame(server, server.asPlayer());
        assertSame(server, server.asLivingEntity());

        UUID uuid = UUID.randomUUID();
        serverPlayer.setUUID(uuid);
        assertEquals(uuid.toString(), server.getUuid());
        var replacement = allocate(ServerPlayer.class);
        replacement.setUUID(uuid);
        assertNotSame(StableEntities.unwrap(server), StableEntities.unwrap(StableEntities.wrap(replacement)));
        assertThrows(IllegalArgumentException.class, () -> StableEntities.unwrap((com.tkisor.nekojs.api.entity.StableEntity) null));
    }

    @Test
    void stableEventKeepsItsSnapshotButScopesLiveViewsAndNativeInterop() throws Exception {
        ServerPlayer player = allocate(ServerPlayer.class);
        player.setUUID(UUID.randomUUID());
        var group = EventGroup.of("StableEntityFixtures");
        var chat = group.server("chat", StablePlayerChatEvent.class, api -> api.stable());
        var registry = new EventGroupRegistry.Impl().scopedTo("fixture");
        registry.register(group);
        var publication = registry.moduleGroups().getFirst();
        var seen = new ArrayList<Object>();
        try (var context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                ValueBindings.install(context, publication, seen);
                context.eval("js", """
                        globalThis.retainedEvent = null;
                        globalThis.retainedPlayer = null;
                        globalThis.checked = [];
                        events.chat(event => {
                            if (retainedEvent) {
                                for (const player of [retainedEvent.player, retainedPlayer]) {
                                    try { void player.uuid; } catch (_) { checked.push('expired'); }
                                }
                            }
                            const server = event.player.asServerPlayer();
                            if (typeof event.player.connection !== 'undefined' || server.latency !== 0) {
                                throw new Error('Unexpected stable player surface');
                            }
                            const native = Native.entities.toNativeServerPlayer(server);
                            const returned = Native.entities.fromNative(native);
                            recordNative(native);
                            recordNative(Native.entities.toNative(returned));
                            if (returned.asServerPlayer() === null || returned.uuid !== event.playerUuid) {
                                throw new Error('Entity round trip changed subtype or UUID');
                            }
                            try { Native.entities.toNative({uuid: event.playerUuid}); }
                            catch (_) { checked.push('lookalike rejected'); }
                            retainedEvent = event;
                            retainedPlayer = event.player;
                        });
                        """);
                chat.post(new StablePlayerChatEvent("fixture", player.getStringUUID(), "first", StableEntities.wrap(player)));
                chat.post(new StablePlayerChatEvent("fixture", player.getStringUUID(), "second", StableEntities.wrap(player)));
                assertEquals(4, seen.size());
                seen.forEach(nativeEntity -> assertSame(player, nativeEntity));
                assertEquals("lookalike rejected,expired,expired,lookalike rejected",
                        context.eval("js", "checked.join(',')").asString());
                assertEquals("second", context.eval("js", "retainedEvent.message").asString());
                assertThrows(PolyglotException.class, () -> context.eval("js", "retainedPlayer.uuid"));
                assertThrows(PolyglotException.class, () -> context.eval("js", "Native.entities.toNative(retainedPlayer)"));
            } finally {
                group.clearListeners(ScriptType.SERVER);
                ScriptContextRegistry.unbind(context);
            }
        }
    }

    @SuppressWarnings("removal")
    private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        // Only identity, UUID and type matter here. No level, connection or entity tick is needed.
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class ValueBindings {
        private static void install(Context context, com.tkisor.nekojs.api.event.ModuleEventGroup publication, List<Object> seen) {
            var bindings = context.getBindings("js");
            bindings.putMember("events", new ModuleEventGroupProxy(context, publication, ScriptType.SERVER));
            bindings.putMember("Native", new BindingValueProxy(context.asValue(new NativeBridgeJS()), NativeBridgeJS.class, false));
            bindings.putMember("recordNative", (ProxyExecutable) args -> {
                seen.add(args[0].asHostObject());
                return null;
            });
        }
    }
}
