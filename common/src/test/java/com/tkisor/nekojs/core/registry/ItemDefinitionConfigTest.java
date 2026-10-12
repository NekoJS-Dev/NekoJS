package com.tkisor.nekojs.core.registry;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ItemDefinitionConfigTest {
    @Test void componentStateIsDetachedCanonicalAndFrozen() {
        ItemDefinitionConfig config = new ItemDefinitionConfig();
        List<Object> nested = new ArrayList<>(List.of("first"));
        config.component("custom_data", new LinkedHashMap<>(Map.of("z", nested, "a", 2)));
        nested.add("later");
        assertEquals("{minecraft:custom_data={a=2, z=[first]}}", config.components().toString());
        assertThrows(UnsupportedOperationException.class, () -> config.components().put("x", true));
        config.freeze();
        assertThrows(IllegalStateException.class, () -> config.component("rarity", "epic"));
        assertThrows(IllegalStateException.class, () -> config.texture("layer0", "test:item/gem"));
    }

    @Test void bulkReplacementAndNamedComponentConflictsAreAtomic() {
        ItemDefinitionConfig config = new ItemDefinitionConfig();
        config.component("rarity", "epic");
        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("max_stack_size", 16);
        invalid.put("custom_data", null);
        assertThrows(IllegalArgumentException.class, () -> config.components(invalid));
        assertEquals(Map.of("minecraft:rarity", "epic"), config.components());
        config.jukeboxPlayable("minecraft:cat");
        assertThrows(IllegalArgumentException.class, () -> config.component("jukebox_playable", "minecraft:cat"));
        config.components(Map.of("max_stack_size", 16));
        assertFalse(config.components().containsKey("minecraft:rarity"));
        config.jukeboxPlayable(null);
        config.component("jukebox_playable", "minecraft:cat");
        assertThrows(IllegalArgumentException.class, () -> config.jukeboxPlayable("minecraft:cat"));
    }

    @Test void rejectsCyclesHostObjectsNonFiniteNumbersAndUnsafePaths() {
        ItemDefinitionConfig config = new ItemDefinitionConfig();
        List<Object> cycle = new ArrayList<>(); cycle.add(cycle);
        for (Object bad : List.of(cycle, new Object(), Double.NaN)) {
            assertThrows(IllegalArgumentException.class, () -> config.component("custom_data", bad));
        }
        for (String bad : List.of("test:../gem", "test:gem/", "test:a//b", "TEST:gem")) {
            assertThrows(IllegalArgumentException.class, () -> config.parentModel(bad));
        }
    }

    @Test void onlyConfiguredItemsGenerateAndFormatsAreNodeSpecific() {
        ItemDefinitionConfig config = new ItemDefinitionConfig();
        assertNull(config.assets("test:folder/gem"));
        config.parentModel("minecraft:item/generated");
        ItemAssetDefinition asset = config.assets("test:folder/gem");
        assertTrue(asset.files(false).get("test/models/item/folder/gem.json").contains("test:item/folder/gem"));
        assertEquals(1, asset.files(false).size());
        assertEquals("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"test:item/folder/gem\"}}",
                asset.files(true).get("test/items/folder/gem.json"));
        config.texture("layer0", "test:item/first");
        config.textures(Map.of("particle", "test:item/second"));
        assertEquals(Map.of("particle", "test:item/second"), config.textures());
        assertEquals("test:item/folder/gem", asset.textures().get("layer0"));
    }
}
