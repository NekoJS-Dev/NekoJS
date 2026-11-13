// fabric has no matching implementation yet; remove this whole-file guard once the port lands
//? if neoforge {
package com.tkisor.nekojs.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for nekojs.mixins.json: the JSON parses, required=true is set, and every mixin class
 * loads. It catches drift between the mixin config and the compiled output. Each platform reads
 * the same-named config from its own classpath (1.21.1 and 26.x have different content).
 */
class MixinConfigIntegrityTest {

    @Test
    void mixinConfigParsesAndEveryListedClassLoads() throws Exception {
        verifyConfig("nekojs.mixins.json");
        if (getClass().getResource("/nekojs-dynamic.mixins.json") != null) {
            verifyConfig("nekojs-dynamic.mixins.json");
        }
    }

    private void verifyConfig(String resource) throws Exception {
        InputStream in = getClass().getResourceAsStream("/" + resource);
        assertNotNull(in, resource + " resource must be present on the test classpath");

        JsonObject root;
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        JsonElement required = root.get("required");
        assertNotNull(required, "mixin config missing 'required'");
        assertTrue(required.getAsBoolean(), "mixin config must set required=true");

        JsonElement packageEl = root.get("package");
        assertNotNull(packageEl, "mixin config missing 'package'");
        String pkg = packageEl.getAsString();
        assertFalse(pkg.isEmpty(), "mixin config 'package' must not be empty");

        // Check all three sections (an older version only checked "mixins", so a typo in a client/server entry went unnoticed)
        Set<String> seen = new HashSet<>();
        ClassLoader loader = getClass().getClassLoader();
        for (String section : List.of("mixins", "client", "server")) {
            JsonElement sectionEl = root.get(section);
            if (sectionEl == null) continue;
            JsonArray sectionMixins = sectionEl.getAsJsonArray();
            for (JsonElement element : sectionMixins) {
                String simpleName = element.getAsString();
                assertTrue(seen.add(section + ":" + simpleName), "duplicate mixin entry: " + simpleName);

                String fqcn = pkg + "." + simpleName;
                // initialize=false: only prove the class exists and loads; do not run mixin static initializers without a game context.
                assertDoesNotThrow(() -> Class.forName(fqcn, false, loader),
                        "mixin class not found on classpath: " + fqcn);
            }
        }
        assertFalse(seen.isEmpty(), "mixin config lists no mixin classes at all");
    }
}
//?}
