package com.tkisor.nekojs.api.posteffect;

import com.tkisor.nekojs.wrapper.event.client.PostEffectEventJS;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostEffectCatalogTest {
    @Test
    void eventBuildsOrderedImmutableCatalogWithSeparateDesiredState() {
        PostEffectEventJS event = new PostEffectEventJS();
        event.resource("minecraft:invert");
        event.runtime("nekojs:runtime", "{\"targets\":{},\"passes\":[]}",
                Map.of("nekojs:runtime_fragment", "void main() {}"), Map.of());
        event.desiredActive("minecraft:invert");

        PostEffectCatalog catalog = event.catalog();

        assertEquals(List.of("minecraft:invert", "nekojs:runtime"), List.copyOf(catalog.ids()));
        assertEquals("minecraft:invert", catalog.desiredActiveId());
        assertTrue(catalog.definition("nekojs:runtime").isRuntime());
        assertTrue(catalog.definition("minecraft:invert").isResourceBacked());
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.definitions().put("nekojs:late", PostEffectDefinition.resource("nekojs:late")));
        assertThrows(IllegalStateException.class, () -> event.resource("nekojs:late"));
    }

    @Test
    void definitionsValidateBeforeEnteringCatalog() {
        PostEffectEventJS event = new PostEffectEventJS();
        assertThrows(IllegalArgumentException.class, () -> event.resource("not an id"));
        assertThrows(IllegalArgumentException.class, () -> event.runtime("nekojs:broken", "[]"));
        assertTrue(event.snapshot().isEmpty());
    }

    @Test
    void conflictingDefinitionsAndDesiredActiveSelectionsFail() {
        PostEffectEventJS event = new PostEffectEventJS();
        event.resource("nekojs:first");
        event.resource("nekojs:second");
        event.desiredActive("nekojs:first");

        assertThrows(IllegalArgumentException.class, () -> event.resource("nekojs:first"));
        assertThrows(IllegalArgumentException.class, () -> event.desiredActive("nekojs:second"));
        assertEquals("nekojs:first", event.snapshot().desiredActiveId());
    }

    @Test
    void sourceTablesAreDefensiveAndRuntimeJsonIsRetained() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("nekojs:fragment", "void main() {}");
        PostEffectDefinition definition = PostEffectDefinition.runtime(
                "nekojs:runtime", "{\"passes\":[]}", source, Map.of());
        source.put("nekojs:late", "void main() {}");

        assertEquals("{\"passes\":[]}", definition.chainJson());
        assertEquals(List.of("nekojs:fragment"), List.copyOf(definition.fragmentShaders().keySet()));
        assertFalse(definition.fragmentShaders().containsKey("nekojs:late"));
        assertThrows(UnsupportedOperationException.class,
                () -> definition.fragmentShaders().put("nekojs:late", "void main() {}"));
    }
}
