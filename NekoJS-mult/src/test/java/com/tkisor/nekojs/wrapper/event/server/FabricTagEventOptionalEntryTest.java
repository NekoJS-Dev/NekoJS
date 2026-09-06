//? if fabric {
package com.tkisor.nekojs.wrapper.event.server;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers Fabric's optional TagEntry form in ServerEvents.tags removal. */
class FabricTagEventOptionalEntryTest {

    @Test
    void removeMatchesOptionalElementEntries() {
        Identifier registry = Identifier.parse("minecraft:item");
        Identifier tag = Identifier.parse("minecraft:planks");
        Identifier entry = Identifier.parse("minecraft:oak_planks");
        Map<Identifier, List<TagLoader.EntryWithSource>> sourceMap = new HashMap<>();
        sourceMap.put(tag, new ArrayList<>(List.of(
                new TagLoader.EntryWithSource(TagEntry.optionalElement(entry), "datapack")
        )));

        TagEventJS event = new TagEventJS(registry, sourceMap);
        event.remove(tag, entry);
        event.apply();

        assertTrue(sourceMap.get(tag).isEmpty(), "required removal must match optional source entries");
    }

}
//?}
