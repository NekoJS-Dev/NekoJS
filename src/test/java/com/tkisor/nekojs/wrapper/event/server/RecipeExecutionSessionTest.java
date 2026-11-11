package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.recipe.RecipeJsonBuilder;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises borrowed native builder identity and final encode preparation. */
class RecipeExecutionSessionTest {
    @Test
    void sessionFollowsRenamesSnapshotsAreDetachedAndPreparationRunsOnceBeforeFinalDecode() {
        var event = new RecipeEventJS(Map.of(), null);
        var builder = new RecipeJsonBuilder(event, "test:process", "process");
        var session = builder.session();
        builder.json().addProperty("count", 1);
        JsonObject snapshot = session.snapshot();
        snapshot.addProperty("count", 99);
        assertEquals(1, builder.json().get("count").getAsInt());
        AtomicInteger preparations = new AtomicInteger();
        session.beforeEncode(() -> {
            preparations.incrementAndGet();
            assertEquals("test:renamed", session.currentId());
            builder.json().addProperty("prepared", true);
        });
        builder.id("test:renamed");
        assertFalse(builder.json().has("prepared"));
        event.prepareRecipesForDecode();
        event.prepareRecipesForDecode();
        assertEquals(1, preparations.get());
        assertTrue(builder.json().get("prepared").getAsBoolean());
        assertSame(builder.json(), event.getFinalJsons().get(RecipeJsonBuilder.parseId("test:renamed")));
        assertEquals(99, snapshot.get("count").getAsInt());
    }

    @Test
    void removedOrReplacedBuildersAreSkippedDuringFinalPreparation() {
        var event = new RecipeEventJS(Map.of(), null);
        var removed = new RecipeJsonBuilder(event, "test:process", "process").id("test:removed");
        var replaced = new RecipeJsonBuilder(event, "test:process", "process").id("test:replaced");
        removed.session().beforeEncode(() -> fail("Removed recipe was prepared"));
        replaced.session().beforeEncode(() -> fail("Replaced recipe was prepared"));
        event.removeById("test:removed");
        event.setJson("test:replaced", "{\"type\":\"test:replacement\"}");
        event.prepareRecipesForDecode();
        assertEquals(1, event.getFinalJsons().size());
    }

    @Test
    void preparationFailuresPropagateInsteadOfReportingSuccessfulCompletion() {
        var event = new RecipeEventJS(Map.of(), null);
        var builder = new RecipeJsonBuilder(event, "test:process", "process");
        builder.session().beforeEncode(() -> { throw new IllegalStateException("invalid custom recipe"); });
        IllegalStateException failure = assertThrows(IllegalStateException.class, event::prepareRecipesForDecode);
        assertEquals("invalid custom recipe", failure.getMessage());
    }

    @Test
    void exposesEventOwnedCompletionAndNativeBuilderSession() throws Exception {
        assertEquals(void.class, RecipeEventJS.class.getMethod("prepareRecipesForDecode").getReturnType());
        assertEquals("com.tkisor.nekojs.api.recipe.RecipeBuilderSession",
                RecipeJsonBuilder.class.getMethod("session").getReturnType().getName());
    }
}
