// TODO(fabric): deferred to the LoaderBridge fabric port
//? if neoforge {
package com.tkisor.nekojs.wrapper.event.server;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 23 AC8 external-behavior fixtures for the {@code ServerEvents.lootTables} JSON
 * surface: modification (setJson/create/modify), conflicts between setJson and remove on the
 * same id, "delete" semantics (empty-table replacement recorded as a pending removal), and the
 * post-reload stale state (pending declarations survive reload event instances because scripts
 * re-declare every reload — documented behavior, pinned here as characterization).
 *
 * <p>Registry-light: everything below observes the pending/loaded JSON surface only; the
 * {@code LootTableLoadEvent} application path itself needs a live reload pipeline (recorded as
 * not run in the ticket 23 report). Each test uses unique table ids so the process-wide
 * pending maps are never asserted globally.
 */
class LootTableEventJSJsonSurfaceTest {

    private static String fresh(String prefix) {
        return prefix + "_" + System.nanoTime();
    }

    @Test
    void setJsonCreatesModifiesAndReadsBackThroughThePendingSurface() {
        String id = "nekojs:" + fresh("chest");

        LootTableEventJS event = new LootTableEventJS();
        assertFalse(event.getIds().contains(id));
        assertNullJson(event, id);

        event.setJson(id, "{\"type\":\"minecraft:chest\"}");
        assertTrue(event.getIds().contains(id), "a declared table id is queryable immediately");
        assertEquals("minecraft:chest", event.getJson(id).getAsJsonObject().get("type").getAsString());

        // A second reload event instance (scripts re-declare) observes the same pending state.
        LootTableEventJS nextReload = new LootTableEventJS();
        assertTrue(nextReload.getIds().contains(id));
        assertEquals("minecraft:chest",
                nextReload.getJson(id).getAsJsonObject().get("type").getAsString());
    }

    @Test
    void modifyBuildsOnExistingJsonAndEmptyIdsStartFromScratch() {
        String id = "nekojs:" + fresh("modify");

        LootTableEventJS event = new LootTableEventJS();
        event.setJson(id, "{\"type\":\"minecraft:chest\",\"pools\":[]}");

        event.modify(id, table -> {
            table.setType("minecraft:block");
            table.addPool("{\"rolls\":2}");
        });

        JsonObject json = event.getJson(id).getAsJsonObject();
        assertEquals("minecraft:block", json.get("type").getAsString());
        assertEquals(1, json.getAsJsonArray("pools").size(), "addPool appends to the emptied pools array");
        assertEquals(2, json.getAsJsonArray("pools").get(0).getAsJsonObject().get("rolls").getAsInt());

        // modify() of an unknown id starts from an empty object (documented convenience).
        String freshId = "nekojs:" + fresh("scratch");
        event.modify(freshId, table -> table.setType("minecraft:chest"));
        assertEquals("minecraft:chest", event.getJson(freshId).getAsJsonObject().get("type").getAsString());
    }

    @Test
    void conflictingDeclarationsOnTheSameIdResolveLastCallWins() {
        String id = "nekojs:" + fresh("conflict");

        LootTableEventJS event = new LootTableEventJS();
        event.setJson(id, "{\"type\":\"minecraft:chest\"}");
        event.remove(id);
        // remove() declared after setJson(): the table is scheduled for deletion, not creation.
        assertFalse(event.getIds().contains(id), "remove must retract the pending creation");
        assertNullJson(event, id);

        // Re-declaring after remove() flips it back to a pending replacement.
        event.create(id, "{\"type\":\"minecraft:block\"}");
        assertTrue(event.getIds().contains(id));
        assertEquals("minecraft:block", event.getJson(id).getAsJsonObject().get("type").getAsString());
    }

    @Test
    void pendingDeclarationsSurviveReloadInstancesAsStaleStateUntilRedeclared() {
        // Documented semantics ("pending 修改跨 reload 保留（每次 reload 脚本重新声明）"):
        // a table declared by an OLD script generation stays pending when the next reload's
        // scripts stop declaring it. Pinned so a future cleanup policy change is a conscious
        // contract change, not an accident.
        String staleId = "nekojs:" + fresh("stale");
        new LootTableEventJS().setJson(staleId, "{\"type\":\"minecraft:chest\"}");

        LootTableEventJS laterReload = new LootTableEventJS();
        assertTrue(laterReload.getIds().contains(staleId),
                "stale pending state is observable after the declaring reload is gone");

        // The next declaration for the same id replaces the stale content wholesale.
        laterReload.setJson(staleId, "{\"type\":\"minecraft:block\"}");
        assertEquals("minecraft:block",
                laterReload.getJson(staleId).getAsJsonObject().get("type").getAsString());
    }

    @Test
    void blockLootShorthandTargetsTheBlocksNamespacePath() {
        String block = fresh("myore");

        LootTableEventJS event = new LootTableEventJS();
        event.modifyBlockLoot("nekojs:" + block, table -> table.setType("minecraft:block"));

        String tableId = "nekojs:blocks/" + block;
        assertTrue(event.getIds().contains(tableId),
                "modifyBlockLoot maps a block id to its <ns>:blocks/<path> table id");
        assertEquals("minecraft:block",
                event.getJson(tableId).getAsJsonObject().get("type").getAsString());

        List<String> ids = event.getIds();
        assertTrue(ids.stream().noneMatch(candidate -> candidate.equals("nekojs:" + block)),
                "the raw block id is not itself a table id");
    }

    private static void assertNullJson(LootTableEventJS event, String id) {
        assertTrue(event.getJson(id) == null, "no pending JSON expected for " + id);
    }
}
//?}
