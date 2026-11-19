package com.tkisor.nekojs.stage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.recipe.RecipeJsonBuilder;
import com.tkisor.nekojs.api.recipe.RecipeJsonValue;
import com.tkisor.nekojs.api.stage.StageScope;
import com.tkisor.nekojs.core.stage.gate.InlineRecipeStageRegistry;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests verifying {@link RecipeJsonBuilder} inline stage gating with
 * {@link InlineRecipeStageRegistry#SHARED} across auto-generated IDs, custom ID rekeying,
 * recipe JSON cleanliness, and batched update notifications.
 */
class RecipeInlineStageIntegrationTest {

    @BeforeEach
    void setUp() {
        InlineRecipeStageRegistry.SHARED.clear();
    }

    @AfterEach
    void tearDown() {
        InlineRecipeStageRegistry.SHARED.clear();
    }

    @Test
    void stageRegistersDefaultPlayerScopeEntryWithoutPollutingRecipeJson() {
        RecipeEventJS event = new RecipeEventJS(new HashMap<>(), null);
        JsonObject payload = new JsonObject();
        payload.addProperty("category", "misc");

        RecipeJsonBuilder builder = event.custom("minecraft:crafting_shapeless", new RecipeJsonValue(payload));
        assertEquals(1, event.ids().size(), "Expected exactly one auto-generated recipe ID");
        NekoId defaultId = NekoId.resource(event.ids().iterator().next());

        RecipeJsonBuilder returned = builder.stage("iron_age");
        assertEquals(builder, returned, "stage() must return the same RecipeJsonBuilder instance for fluent chaining");

        InlineRecipeStageRegistry.InlineEntry entry = InlineRecipeStageRegistry.SHARED.get(defaultId);
        assertNotNull(entry, "InlineRecipeStageRegistry.SHARED must record an entry under the default recipe ID");
        assertEquals(defaultId, entry.recipeId(), "Recorded entry must preserve the default recipe ID");
        assertEquals("iron_age", entry.stage(), "Recorded entry must store the requested stage name");
        assertEquals(StageScope.PLAYER, entry.scope(), "Single-argument stage() must default to StageScope.PLAYER");
        assertEquals(1, InlineRecipeStageRegistry.SHARED.size(), "Registry must hold exactly one gated recipe");

        JsonObject serialized = builder.json();
        assertFalse(serialized.has("stage"),
                "Recipe JsonObject must not contain a 'stage' property that could break RecipeSerializer decoding");
        assertFalse(serialized.has("nekojs:stage"),
                "Recipe JsonObject must not contain a 'nekojs:stage' property");
        assertEquals(Set.of("type", "category"), serialized.keySet(),
                "Recipe JsonObject keys must remain strictly vanilla recipe properties");

        String committedJsonText = event.getJson(defaultId.toString());
        assertNotNull(committedJsonText, "Committed recipe JSON must remain registered under defaultId");
        JsonObject committedJson = JsonParser.parseString(committedJsonText).getAsJsonObject();
        assertFalse(committedJson.has("stage"), "Committed recipe JSON must not contain 'stage'");
        assertFalse(committedJson.has("nekojs:stage"), "Committed recipe JSON must not contain 'nekojs:stage'");
    }

    @Test
    void chainingStageBeforeCustomIdRekeysEntryAndLeavesZeroStaleEntries() {
        RecipeEventJS event = new RecipeEventJS(new HashMap<>(), null);
        RecipeJsonBuilder builder = event.builder("minecraft:smelting");

        NekoId temporaryAutoId = NekoId.resource(event.ids().iterator().next());
        String customIdString = "mymod:industrial_ingot";
        NekoId customId = NekoId.resource("mymod", "industrial_ingot");
        assertNotEquals(temporaryAutoId, customId, "Auto-generated ID must differ from explicit custom ID");

        builder.stage("industrial_age", StageScope.SERVER.id()).id(customIdString);

        assertNull(InlineRecipeStageRegistry.SHARED.get(temporaryAutoId),
                "Temporary auto-generated recipe ID must be removed from InlineRecipeStageRegistry.SHARED after id()");
        assertFalse(InlineRecipeStageRegistry.SHARED.snapshot().containsKey(temporaryAutoId),
                "Snapshot must leave zero stale entries under the old auto-generated recipe ID");

        InlineRecipeStageRegistry.InlineEntry rekeyed = InlineRecipeStageRegistry.SHARED.get(customId);
        assertNotNull(rekeyed, "Inline stage gate must be rekeyed under the custom recipe ID");
        assertEquals(new InlineRecipeStageRegistry.InlineEntry(customId, "industrial_age", StageScope.SERVER), rekeyed,
                "Rekeyed entry must match (customId, 'industrial_age', StageScope.SERVER)");
        assertEquals(1, InlineRecipeStageRegistry.SHARED.size(),
                "InlineRecipeStageRegistry.SHARED must contain only the rekeyed entry");

        assertFalse(builder.json().has("stage"),
                "Rekeyed recipe JsonObject must not contain a 'stage' property");
        assertFalse(builder.json().has("nekojs:stage"),
                "Rekeyed recipe JsonObject must not contain a 'nekojs:stage' property");
    }

    @Test
    void customIdBeforeStageAndRemoveByIdKeepRegistryClean() {
        RecipeEventJS event = new RecipeEventJS(new HashMap<>(), null);
        NekoId customId = NekoId.resource("nekojs", "alloy_kiln");

        RecipeJsonBuilder builder = event.builder("minecraft:blasting")
                .id("alloy_kiln")
                .stage("bronze_age", StageScope.BOTH.id());

        InlineRecipeStageRegistry.InlineEntry entry = InlineRecipeStageRegistry.SHARED.get(customId);
        assertEquals(new InlineRecipeStageRegistry.InlineEntry(customId, "bronze_age", StageScope.BOTH), entry);
        assertFalse(builder.json().has("stage"));
        assertFalse(builder.json().has("nekojs:stage"));

        event.removeById("nekojs:alloy_kiln");
        assertNull(InlineRecipeStageRegistry.SHARED.get(customId),
                "Removing a recipe via RecipeEventJS.removeById must purge its inline stage gate");
        assertTrue(InlineRecipeStageRegistry.SHARED.isEmpty(),
                "InlineRecipeStageRegistry.SHARED must be empty after removing the only gated recipe");
    }

    @Test
    void runBatchCoalescesPutRekeyAndRemoveIntoSingleOnUpdatedCallback() {
        AtomicInteger callbackInvocations = new AtomicInteger();
        Runnable listener = callbackInvocations::incrementAndGet;
        InlineRecipeStageRegistry.SHARED.onUpdated(listener);
        try {
            NekoId tempId = NekoId.resource("nekojs", "temp_generated_0");
            NekoId finalId = NekoId.resource("mymod", "steel_gear");
            NekoId transientId = NekoId.resource("nekojs", "transient_recipe");
            NekoId keptId = NekoId.resource("mymod", "steam_boiler");

            InlineRecipeStageRegistry.SHARED.runBatch(() -> {
                InlineRecipeStageRegistry.SHARED.put(tempId, "steel_age", StageScope.PLAYER);
                InlineRecipeStageRegistry.SHARED.put(transientId, "obsolete_stage", StageScope.EITHER);
                InlineRecipeStageRegistry.SHARED.put(keptId, "steam_age", StageScope.SERVER);
                InlineRecipeStageRegistry.SHARED.rekey(tempId, finalId);
                InlineRecipeStageRegistry.SHARED.remove(transientId);
                assertEquals(0, callbackInvocations.get(),
                        "onUpdated listener must not fire until the outermost runBatch completes");
            });

            assertEquals(1, callbackInvocations.get(),
                    "Multiple put/rekey/remove mutations inside runBatch must coalesce into exactly one callback");
            assertEquals(2, InlineRecipeStageRegistry.SHARED.size(),
                    "Expected two surviving entries after put/rekey/remove batch");
            assertNull(InlineRecipeStageRegistry.SHARED.get(tempId),
                    "Old ID must not remain after batched rekey");
            assertNull(InlineRecipeStageRegistry.SHARED.get(transientId),
                    "Removed ID must not remain after batched remove");
            assertEquals(new InlineRecipeStageRegistry.InlineEntry(finalId, "steel_age", StageScope.PLAYER),
                    InlineRecipeStageRegistry.SHARED.get(finalId));
            assertEquals(new InlineRecipeStageRegistry.InlineEntry(keptId, "steam_age", StageScope.SERVER),
                    InlineRecipeStageRegistry.SHARED.get(keptId));

            InlineRecipeStageRegistry.SHARED.runBatch(() -> {
                // No mutations inside batch should not trigger an update notification.
            });
            assertEquals(1, callbackInvocations.get(),
                    "No-op runBatch must not invoke onUpdated listeners");
        } finally {
            InlineRecipeStageRegistry.SHARED.removeListener(listener);
        }
    }
}
