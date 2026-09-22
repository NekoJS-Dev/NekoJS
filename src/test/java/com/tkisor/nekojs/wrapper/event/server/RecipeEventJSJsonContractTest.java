// TODO(fabric): deferred to the LoaderBridge fabric port
//? if neoforge {
package com.tkisor.nekojs.wrapper.event.server;

import com.google.gson.JsonObject;
import com.tkisor.nekojs.api.recipe.RecipeJsonBuilder;
import com.tkisor.nekojs.api.recipe.RecipeJsonValue;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 23 AC2 contract fixture (registry-light — the same bare-JUnit shape as
 * {@code RecipeEventJSGeneratedIdTest}): representative script inputs generate or modify the
 * expected recipe JSON through the existing {@code recipes} event object. Value conversion
 * (primitive / object / passthrough JSON), nested setPath, id rebinding and filter-free
 * query/remove semantics are pinned on the JSON map the lifecycle later commits; no private
 * state is asserted.
 *
 * <p>Registry-dependent behaviors (filters applied through {@code Recipe.CODEC}, ingredient
 * serialization, {@code MinecraftRecipeHandler} overloads) are covered by the guarded tests
 * noted in the ticket 23 report.
 */
class RecipeEventJSJsonContractTest {

    private static RecipeEventJS event(Map<String, String> recipes) {
        RecipeEventJS event = new RecipeEventJS(new HashMap<>(), null);
        recipes.forEach(event::setJson);
        return event;
    }

    // ------------------------------------------------------ modify existing JSON

    @Test
    void setJsonReplacesContentAndRemoveDeletesById() {
        RecipeEventJS event = event(Map.of(
                "minecraft:existing", "{\"type\":\"minecraft:smelting\",\"experience\":0.1}"));

        event.setJson("minecraft:existing", "{\"type\":\"minecraft:smelting\",\"experience\":0.7}");
        assertEquals("{\"type\":\"minecraft:smelting\",\"experience\":0.7}", event.getJson("minecraft:existing"));

        event.setJson("nekojs:added", "{\"type\":\"minecraft:crafting_shapeless\"}");
        assertTrue(event.exists("nekojs:added"), "setJson may create ids not present in the datapack");
        assertEquals(2, event.count());

        event.removeById("minecraft:existing");
        assertFalse(event.exists("minecraft:existing"));
        assertNull(event.getJson("minecraft:existing"));
        assertEquals(1, event.count());
    }

    @Test
    void setJsonRejectsInvalidIdsInsteadOfSilentlyDropping() {
        RecipeEventJS event = event(Map.of());
        // colon form: tryParse yields null -> the event's own IllegalArgumentException
        IllegalArgumentException rejected = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> event.setJson("bad mod:path!", "{}"));
        assertTrue(rejected.getMessage().contains("Invalid recipe id"), rejected.getMessage());
        // colon-less form: the platform id parser itself rejects the invalid path loudly
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> event.setJson("not a valid id!", "{}"));
        assertEquals(0, event.count(), "neither rejection may register a recipe");
    }

    // ------------------------------------------------------ generate JSON through builders

    @Test
    void customBuilderStampsTypeAndGeneratedIdLandsInFinalJsons() {
        RecipeEventJS event = event(Map.of());

        JsonObject input = new JsonObject();
        input.addProperty("nekojs:thing", "custom-shape");
        RecipeJsonBuilder builder = event.custom("nekojs:machine", new RecipeJsonValue(input));

        // custom() stamps the schema type and registers under a generated nekojs: id
        assertEquals("nekojs:machine", builder.json().get("type").getAsString());
        assertEquals("custom-shape", builder.json().get("nekojs:thing").getAsString());
        assertEquals(1, event.count());
        assertTrue(event.ids().stream().allMatch(id -> id.startsWith("nekojs:")),
                "generated ids live in the nekojs namespace: " + event.ids());
    }

    @Test
    void customBuilderWithoutTypeIsRejectedNotRegistered() {
        RecipeEventJS event = event(Map.of());
        assertNull(event.custom(new JsonObject()), "missing 'type' cannot become a recipe");
        assertEquals(0, event.count());
    }

    @Test
    void idRebindingMovesTheJsonUnderTheNewKey() {
        RecipeEventJS event = event(Map.of());
        RecipeJsonBuilder builder = event.custom("nekojs:machine", new RecipeJsonValue(new JsonObject()));
        String generated = event.ids().iterator().next();

        builder.id("mymod:machine_a");

        assertFalse(event.exists(generated), "the generated id must be vacated after rebind");
        assertTrue(event.exists("mymod:machine_a"));
        assertEquals("nekojs:machine", event.getJson("mymod:machine_a") == null ? null
                : com.google.gson.JsonParser.parseString(event.getJson("mymod:machine_a"))
                        .getAsJsonObject().get("type").getAsString());
    }

    // ------------------------------------------------------ value conversion through builders

    @Test
    void propertyConvertsPrimitivesAndObjectsToJSONShapes() {
        RecipeEventJS event = event(Map.of());
        RecipeJsonBuilder builder = event.custom("nekojs:machine", new RecipeJsonValue(new JsonObject()));

        builder.property("count", new RecipeJsonValue(4))
                .property("label", new RecipeJsonValue("machine"))
                .property("enabled", new RecipeJsonValue(true))
                .property("nested", new RecipeJsonValue(Map.of("a", 1)));

        JsonObject json = builder.json();
        assertEquals(4, json.get("count").getAsInt());
        assertEquals("machine", json.get("label").getAsString());
        assertTrue(json.get("enabled").getAsBoolean());
        assertEquals(1, json.get("nested").getAsJsonObject().get("a").getAsInt());
    }

    @Test
    void setPathWritesNestedPathsAndRemovePathDeletes() {
        RecipeEventJS event = event(Map.of());
        RecipeJsonBuilder builder = event.custom("nekojs:machine", new RecipeJsonValue(new JsonObject()));

        builder.setPath("result.count", new RecipeJsonValue(4));
        builder.setPath("result.item", new RecipeJsonValue("minecraft:stick"));

        JsonObject json = builder.json();
        assertEquals(4, json.getAsJsonObject("result").get("count").getAsInt());
        assertEquals("minecraft:stick", json.getAsJsonObject("result").get("item").getAsString());

        builder.removePath("result.count");
        assertFalse(json.getAsJsonObject("result").has("count"));
    }

    // ------------------------------------------------------ JSON map query surface

    @Test
    void querySurfaceObservesTheSameJsonTheLifecycleCommits() {
        RecipeEventJS event = event(Map.of(
                "minecraft:a", "{\"type\":\"minecraft:smelting\"}",
                "nekojs:b", "{\"type\":\"nekojs:machine\"}"));

        assertEquals(2, event.count());
        assertTrue(event.exists("minecraft:a"));
        assertFalse(event.exists("minecraft:c"));
        assertEquals(java.util.Set.of("minecraft:a", "nekojs:b"), event.ids());
        // get()/all() hand back live JSON of the same backing map the mixin commits
        assertEquals("minecraft:smelting", event.get("minecraft:a").json().get("type").getAsString());
        assertEquals(2, event.all().size());
    }
}
//?}
