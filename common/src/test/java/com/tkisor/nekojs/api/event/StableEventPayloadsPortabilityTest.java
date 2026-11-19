package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.core.api.BindingValueProxy;
import com.tkisor.nekojs.core.api.ModulePublicTypes;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Portability and read-only Bean projection tests for all 19 stable event payload classes.
 */
class StableEventPayloadsPortabilityTest {

    private static final List<Class<?>> ALL_STABLE_EVENT_TYPES = List.of(
            StableServerTickEvent.class,
            StableServerLifecycleEvent.class,
            StableDatapackSyncEvent.class,
            StableTagsUpdatedEvent.class,
            StableLootTableLoadEvent.class,
            StableLevelEvent.class,
            StableExplosionEvent.class,
            StablePlayerEvent.class,
            StablePlayerChatEvent.class,
            StablePlayerDimensionChangeEvent.class,
            StablePlayerAdvancementEvent.class,
            StablePlayerItemEvent.class,
            StableCommandRegistrationEvent.class,
            StableCommandExecuteEvent.class,
            StableBlockEvent.class,
            StableItemEvent.class,
            StableEntityEvent.class,
            StableClientEvent.class,
            StableClientInputEvent.class
    );

    @Test
    void allNineteenStableEventClassesPassPortabilityValidation() {
        assertEquals(19, ALL_STABLE_EVENT_TYPES.size());
        for (Class<?> cls : ALL_STABLE_EVENT_TYPES) {
            assertDoesNotThrow(() -> ModulePublicTypes.validateStable(cls, false),
                    () -> "Expected instance surface of " + cls.getName() + " to be portable");
            assertDoesNotThrow(() -> ModulePublicTypes.validateStable(cls, true),
                    () -> "Expected static surface of " + cls.getName() + " to be portable");
        }
    }

    @Test
    void nullStringsNormalizeToEmptyAndValueEqualityHolds() {
        var lifecycle = new StableServerLifecycleEvent(null, 12);
        assertEquals("", lifecycle.getStage());
        assertEquals(12, lifecycle.getTick());
        assertEquals(new StableServerLifecycleEvent("", 12), lifecycle);
        assertEquals(new StableServerLifecycleEvent("", 12).hashCode(), lifecycle.hashCode());
        assertTrue(lifecycle.toString().contains("StableServerLifecycleEvent"));

        var datapack = new StableDatapackSyncEvent(null, 3, true);
        assertEquals("", datapack.getPlayerName());
        assertEquals(3, datapack.getPlayerCount());
        assertTrue(datapack.isReloadSync());
        assertEquals(new StableDatapackSyncEvent("", 3, true), datapack);

        var tags = new StableTagsUpdatedEvent(null, false);
        assertEquals("", tags.getCause());
        assertFalse(tags.isClientPacket());
        assertEquals(new StableTagsUpdatedEvent("", false), tags);

        var loot = new StableLootTableLoadEvent(null);
        assertEquals("", loot.getTableId());
        assertEquals(new StableLootTableLoadEvent(""), loot);

        var level = new StableLevelEvent(null, false, 1000L, 500L);
        assertEquals("", level.getDimension());
        assertEquals(new StableLevelEvent("", false, 1000L, 500L), level);

        var explosion = new StableExplosionEvent(null, 1.0, 64.0, -2.0, 4.0f, 10, 2);
        assertEquals("", explosion.getDimension());
        assertEquals(new StableExplosionEvent("", 1.0, 64.0, -2.0, 4.0f, 10, 2), explosion);

        var player = new StablePlayerEvent(null, null, null, 0.5, 65.0, 0.5);
        assertEquals("", player.getPlayerName());
        assertEquals("", player.getPlayerUuid());
        assertEquals("", player.getDimension());
        assertEquals(new StablePlayerEvent("", "", "", 0.5, 65.0, 0.5), player);

        var chat = new StablePlayerChatEvent(null, null, null);
        assertEquals("", chat.getPlayerName());
        assertEquals("", chat.getPlayerUuid());
        assertEquals("", chat.getMessage());
        assertEquals(new StablePlayerChatEvent("", "", ""), chat);

        var dimChange = new StablePlayerDimensionChangeEvent(null, null, null, null);
        assertEquals("", dimChange.getPlayerName());
        assertEquals("", dimChange.getPlayerUuid());
        assertEquals("", dimChange.getFromDimension());
        assertEquals("", dimChange.getToDimension());
        assertEquals(new StablePlayerDimensionChangeEvent("", "", "", ""), dimChange);

        var advancement = new StablePlayerAdvancementEvent(null, null, null);
        assertEquals("", advancement.getPlayerName());
        assertEquals("", advancement.getPlayerUuid());
        assertEquals("", advancement.getAdvancementId());
        assertEquals(new StablePlayerAdvancementEvent("", "", ""), advancement);

        var playerItem = new StablePlayerItemEvent(null, null, null, 8);
        assertEquals("", playerItem.getPlayerName());
        assertEquals("", playerItem.getPlayerUuid());
        assertEquals("", playerItem.getItemId());
        assertEquals(8, playerItem.getCount());
        assertEquals(new StablePlayerItemEvent("", "", "", 8), playerItem);

        var cmdReg = new StableCommandRegistrationEvent(null);
        assertEquals("", cmdReg.getEnvironment());
        assertEquals(new StableCommandRegistrationEvent(""), cmdReg);

        var cmdExec = new StableCommandExecuteEvent(null, null);
        assertEquals("", cmdExec.getInput());
        assertEquals("", cmdExec.getSourceName());
        assertEquals(new StableCommandExecuteEvent("", ""), cmdExec);

        var block = new StableBlockEvent(null, 1, 2, 3, null, null);
        assertEquals("", block.getDimension());
        assertEquals("", block.getBlockId());
        assertEquals("", block.getActorName());
        assertEquals(new StableBlockEvent("", 1, 2, 3, "", ""), block);

        var item = new StableItemEvent(null, 16, null, null);
        assertEquals("", item.getItemId());
        assertEquals("", item.getActorName());
        assertEquals("", item.getDimension());
        assertEquals(new StableItemEvent("", 16, "", ""), item);

        var entity = new StableEntityEvent(null, null, null, 2.0, 70.0, -3.0, 5.5f);
        assertEquals("", entity.getEntityTypeId());
        assertEquals("", entity.getEntityUuid());
        assertEquals("", entity.getDimension());
        assertEquals(new StableEntityEvent("", "", "", 2.0, 70.0, -3.0, 5.5f), entity);

        var client = new StableClientEvent(null);
        assertEquals("", client.getStage());
        assertEquals(new StableClientEvent(""), client);

        var clientInput = new StableClientInputEvent(true, false, false, null);
        assertEquals("", clientInput.getHand());
        assertEquals(new StableClientInputEvent(true, false, false, ""), clientInput);
    }

    @Test
    void beanPropertiesAreExposedReadOnlyInGraalJsThroughBindingValueProxy() {
        try (var context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.enter();
            try {
                // 0. StableServerTickEvent
                bind(context, new StableServerTickEvent(42), StableServerTickEvent.class);
                assertEquals(42, context.eval("js", "e.tick").asInt());
                assertReadOnly(context, "tick", "99");

                // 1. StableServerLifecycleEvent
                bind(context, new StableServerLifecycleEvent("starting", 1), StableServerLifecycleEvent.class);
                assertEquals("starting", context.eval("js", "e.stage").asString());
                assertEquals(1, context.eval("js", "e.tick").asInt());
                assertReadOnly(context, "stage", "'stopped'");

                // 2. StableDatapackSyncEvent
                bind(context, new StableDatapackSyncEvent("Alex", 4, true), StableDatapackSyncEvent.class);
                assertEquals("Alex", context.eval("js", "e.playerName").asString());
                assertEquals(4, context.eval("js", "e.playerCount").asInt());
                assertTrue(context.eval("js", "e.reloadSync").asBoolean());
                assertReadOnly(context, "reloadSync", "false");

                // 3. StableTagsUpdatedEvent
                bind(context, new StableTagsUpdatedEvent("server_data_load", false), StableTagsUpdatedEvent.class);
                assertEquals("server_data_load", context.eval("js", "e.cause").asString());
                assertFalse(context.eval("js", "e.clientPacket").asBoolean());
                assertReadOnly(context, "cause", "'other'");

                // 4. StableLootTableLoadEvent
                bind(context, new StableLootTableLoadEvent("minecraft:chests/simple_dungeon"), StableLootTableLoadEvent.class);
                assertEquals("minecraft:chests/simple_dungeon", context.eval("js", "e.tableId").asString());
                assertReadOnly(context, "tableId", "'minecraft:empty'");

                // 5. StableLevelEvent
                bind(context, new StableLevelEvent("minecraft:overworld", false, 24000L, 6000L), StableLevelEvent.class);
                assertEquals("minecraft:overworld", context.eval("js", "e.dimension").asString());
                assertFalse(context.eval("js", "e.clientSide").asBoolean());
                assertEquals(24000L, context.eval("js", "e.gameTime").asLong());
                assertEquals(6000L, context.eval("js", "e.dayTime").asLong());
                assertReadOnly(context, "dimension", "'minecraft:the_nether'");

                // 6. StableExplosionEvent
                bind(context, new StableExplosionEvent("minecraft:the_nether", 10.5, 64.0, -20.25, 4.5f, 18, 3), StableExplosionEvent.class);
                assertEquals("minecraft:the_nether", context.eval("js", "e.dimension").asString());
                assertEquals(10.5, context.eval("js", "e.x").asDouble(), 0.0001);
                assertEquals(64.0, context.eval("js", "e.y").asDouble(), 0.0001);
                assertEquals(-20.25, context.eval("js", "e.z").asDouble(), 0.0001);
                assertEquals(4.5, context.eval("js", "e.radius").asDouble(), 0.0001);
                assertEquals(18, context.eval("js", "e.affectedBlockCount").asInt());
                assertEquals(3, context.eval("js", "e.affectedEntityCount").asInt());
                assertReadOnly(context, "affectedBlockCount", "0");

                // 7. StablePlayerEvent
                bind(context, new StablePlayerEvent("Steve", "00000000-0000-0000-0000-000000000001", "minecraft:overworld", 12.5, 72.0, -4.5), StablePlayerEvent.class);
                assertEquals("Steve", context.eval("js", "e.playerName").asString());
                assertEquals("00000000-0000-0000-0000-000000000001", context.eval("js", "e.playerUuid").asString());
                assertEquals("minecraft:overworld", context.eval("js", "e.dimension").asString());
                assertEquals(12.5, context.eval("js", "e.x").asDouble(), 0.0001);
                assertEquals(72.0, context.eval("js", "e.y").asDouble(), 0.0001);
                assertEquals(-4.5, context.eval("js", "e.z").asDouble(), 0.0001);
                assertReadOnly(context, "playerName", "'Alex'");

                // 8. StablePlayerChatEvent
                bind(context, new StablePlayerChatEvent("Steve", "00000000-0000-0000-0000-000000000001", "hello world"), StablePlayerChatEvent.class);
                assertEquals("Steve", context.eval("js", "e.playerName").asString());
                assertEquals("00000000-0000-0000-0000-000000000001", context.eval("js", "e.playerUuid").asString());
                assertEquals("hello world", context.eval("js", "e.message").asString());
                assertReadOnly(context, "message", "'tampered'");

                // 9. StablePlayerDimensionChangeEvent
                bind(context, new StablePlayerDimensionChangeEvent("Steve", "00000000-0000-0000-0000-000000000001", "minecraft:overworld", "minecraft:the_end"), StablePlayerDimensionChangeEvent.class);
                assertEquals("Steve", context.eval("js", "e.playerName").asString());
                assertEquals("00000000-0000-0000-0000-000000000001", context.eval("js", "e.playerUuid").asString());
                assertEquals("minecraft:overworld", context.eval("js", "e.fromDimension").asString());
                assertEquals("minecraft:the_end", context.eval("js", "e.toDimension").asString());
                assertReadOnly(context, "toDimension", "'minecraft:overworld'");

                // 10. StablePlayerAdvancementEvent
                bind(context, new StablePlayerAdvancementEvent("Steve", "00000000-0000-0000-0000-000000000001", "minecraft:story/mine_stone"), StablePlayerAdvancementEvent.class);
                assertEquals("Steve", context.eval("js", "e.playerName").asString());
                assertEquals("00000000-0000-0000-0000-000000000001", context.eval("js", "e.playerUuid").asString());
                assertEquals("minecraft:story/mine_stone", context.eval("js", "e.advancementId").asString());
                assertReadOnly(context, "advancementId", "'minecraft:story/root'");

                // 11. StablePlayerItemEvent
                bind(context, new StablePlayerItemEvent("Steve", "00000000-0000-0000-0000-000000000001", "minecraft:diamond", 5), StablePlayerItemEvent.class);
                assertEquals("Steve", context.eval("js", "e.playerName").asString());
                assertEquals("00000000-0000-0000-0000-000000000001", context.eval("js", "e.playerUuid").asString());
                assertEquals("minecraft:diamond", context.eval("js", "e.itemId").asString());
                assertEquals(5, context.eval("js", "e.count").asInt());
                assertReadOnly(context, "itemId", "'minecraft:dirt'");

                // 12. StableCommandRegistrationEvent
                bind(context, new StableCommandRegistrationEvent("ALL"), StableCommandRegistrationEvent.class);
                assertEquals("ALL", context.eval("js", "e.environment").asString());
                assertReadOnly(context, "environment", "'DEDICATED'");

                // 13. StableCommandExecuteEvent
                bind(context, new StableCommandExecuteEvent("/say hi", "Server"), StableCommandExecuteEvent.class);
                assertEquals("/say hi", context.eval("js", "e.input").asString());
                assertEquals("Server", context.eval("js", "e.sourceName").asString());
                assertReadOnly(context, "input", "'/stop'");

                // 14. StableBlockEvent
                bind(context, new StableBlockEvent("minecraft:overworld", 100, 65, -30, "minecraft:stone", "Steve"), StableBlockEvent.class);
                assertEquals("minecraft:overworld", context.eval("js", "e.dimension").asString());
                assertEquals(100, context.eval("js", "e.x").asInt());
                assertEquals(65, context.eval("js", "e.y").asInt());
                assertEquals(-30, context.eval("js", "e.z").asInt());
                assertEquals("minecraft:stone", context.eval("js", "e.blockId").asString());
                assertEquals("Steve", context.eval("js", "e.actorName").asString());
                assertReadOnly(context, "blockId", "'minecraft:air'");

                // 15. StableItemEvent
                bind(context, new StableItemEvent("minecraft:apple", 3, "Steve", "minecraft:overworld"), StableItemEvent.class);
                assertEquals("minecraft:apple", context.eval("js", "e.itemId").asString());
                assertEquals(3, context.eval("js", "e.count").asInt());
                assertEquals("Steve", context.eval("js", "e.actorName").asString());
                assertEquals("minecraft:overworld", context.eval("js", "e.dimension").asString());
                assertReadOnly(context, "count", "64");

                // 16. StableEntityEvent
                bind(context, new StableEntityEvent("minecraft:zombie", "00000000-0000-0000-0000-000000000099", "minecraft:overworld", -8.0, 63.0, 14.25, 6.5f), StableEntityEvent.class);
                assertEquals("minecraft:zombie", context.eval("js", "e.entityTypeId").asString());
                assertEquals("00000000-0000-0000-0000-000000000099", context.eval("js", "e.entityUuid").asString());
                assertEquals("minecraft:overworld", context.eval("js", "e.dimension").asString());
                assertEquals(-8.0, context.eval("js", "e.x").asDouble(), 0.0001);
                assertEquals(63.0, context.eval("js", "e.y").asDouble(), 0.0001);
                assertEquals(14.25, context.eval("js", "e.z").asDouble(), 0.0001);
                assertEquals(6.5, context.eval("js", "e.amount").asDouble(), 0.0001);
                assertReadOnly(context, "entityTypeId", "'minecraft:skeleton'");

                // 17. StableClientEvent
                bind(context, new StableClientEvent("tick_pre"), StableClientEvent.class);
                assertEquals("tick_pre", context.eval("js", "e.stage").asString());
                assertReadOnly(context, "stage", "'tick_post'");

                // 18. StableClientInputEvent
                bind(context, new StableClientInputEvent(false, true, false, "MAIN_HAND"), StableClientInputEvent.class);
                assertFalse(context.eval("js", "e.attack").asBoolean());
                assertTrue(context.eval("js", "e.useItem").asBoolean());
                assertFalse(context.eval("js", "e.pickBlock").asBoolean());
                assertEquals("MAIN_HAND", context.eval("js", "e.hand").asString());
                assertReadOnly(context, "hand", "'OFF_HAND'");
            } finally {
                context.leave();
            }
        }
    }

    private static <T> void bind(Context context, T instance, Class<T> publicType) {
        var proxy = new BindingValueProxy(context.asValue(instance), publicType, false);
        context.getBindings("js").putMember("e", proxy);
        assertFalse(context.eval("js", "Object.keys(e).includes('getClass')").asBoolean());
    }

    private static void assertReadOnly(Context context, String property, String jsReplacementLiteral) {
        var before = context.eval("js", "e." + property);
        assertThrows(RuntimeException.class,
                () -> context.eval("js", "'use strict'; e." + property + " = " + jsReplacementLiteral));
        assertEquals(before.toString(), context.eval("js", "e." + property).toString());
    }
}
