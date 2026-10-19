package com.tkisor.nekojs.wrapper.event.server;

import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.recipe.RecipeJsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecipeEventJSBaselineIsolationTest {
    @Test void nestedScriptEditsDoNotPersistIntoTheNextApplication() {
        var id = RecipeJsonBuilder.parseId("test:baseline");
        var baseline = JsonParser.parseString("{\"ingredient\":[{\"item\":\"test:original\"}],\"result\":{\"id\":\"test:output\"}}");
        var first = new RecipeEventJS(Map.of(id, baseline), null);
        var edited = first.getFinalJsons().get(id).getAsJsonObject();
        edited.getAsJsonArray("ingredient").get(0).getAsJsonObject().addProperty("item", "test:replacement");
        edited.getAsJsonObject("result").addProperty("count", 4);
        var next = new RecipeEventJS(Map.of(id, baseline), null);
        assertEquals("test:original", baseline.getAsJsonObject().getAsJsonArray("ingredient")
                .get(0).getAsJsonObject().get("item").getAsString());
        assertEquals(baseline, next.getFinalJsons().get(id));
        assertEquals("test:replacement", edited.getAsJsonArray("ingredient")
                .get(0).getAsJsonObject().get("item").getAsString());
    }

    @Test void laterChangesToTheSuppliedBaselineDoNotChangeAnExistingEvent() {
        var id = RecipeJsonBuilder.parseId("test:baseline");
        var baseline = JsonParser.parseString("{\"nested\":{\"values\":[1,2]}}");
        var event = new RecipeEventJS(Map.of(id, baseline), null);
        baseline.getAsJsonObject().getAsJsonObject("nested").getAsJsonArray("values").add(3);
        assertEquals("{\"nested\":{\"values\":[1,2]}}", event.getJson("test:baseline"));
    }
}
