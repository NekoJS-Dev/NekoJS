package com.tkisor.nekojs.wrapper.event.server;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B4 regression test: a datapack recipe whose id equals the first id that
 * {@code generateRecipeId("custom")} would produce must not be silently
 * overwritten. The test file lives in the shared 26.x test tree and is also
 * compiled by the 1.21.1 platform, so it deliberately avoids importing the
 * platform-specific id class (Identifier / ResourceLocation) and creates ids
 * through the same public {@code fromNamespaceAndPath} factory both classes
 * expose.
 */
class RecipeEventJSGeneratedIdTest {

    private static String suffixFor(String prefix, int counter) {
        String base = prefix + "_" + counter;
        return UUID.nameUUIDFromBytes(base.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "").substring(0, 8);
    }

    /** Creates a {@code nekojs:<path>} id object for the current platform via reflection. */
    private static Object id(String path) throws Exception {
        RecipeEventJS probe = new RecipeEventJS(new HashMap<>(), null);
        Object sample = probe.generateRecipeId("__probe__");
        return sample.getClass()
                .getMethod("fromNamespaceAndPath", String.class, String.class)
                .invoke(null, "nekojs", path);
    }

    @Test
    void normalRunReturnsDeterministicFirstId() {
        RecipeEventJS event = new RecipeEventJS(new HashMap<>(), null);

        String expected = "nekojs:custom_" + suffixFor("custom", 0);

        assertEquals(expected, event.generateRecipeId("custom").toString());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void generatedIdDoesNotSilentlyOverwriteSeededDatapackId() throws Exception {
        String seededPath = "custom_" + suffixFor("custom", 0);
        Object seededId = id(seededPath);

        Map jsonMap = new HashMap();
        jsonMap.put(seededId, new JsonObject());
        RecipeEventJS event = new RecipeEventJS(jsonMap, null);

        Object generatedId = event.generateRecipeId("custom");

        assertNotEquals(seededId.toString(), generatedId.toString(),
                "generateRecipeId must not return an id already present in the wrapped JSON map");
        assertTrue(event.getFinalJsons().containsKey(seededId),
                "the seeded datapack recipe must remain in the JSON map, not be overwritten");
    }
}
