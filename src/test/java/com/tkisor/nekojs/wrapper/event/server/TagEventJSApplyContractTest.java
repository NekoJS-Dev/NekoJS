// 26.x/1.21.1 共享测试树：本文件零版本守卫（TagEventJS 两侧同包同名，构造输入只用字符串）。
//? if neoforge {
package com.tkisor.nekojs.wrapper.event.server;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 23 AC8 external-behavior fixtures for {@code ServerEvents.tags} (dispatch key =
 * registry id): add/remove/replaceAll/removeAll are collected per event instance and applied
 * to the tag loader's source map in one {@code apply()} — the exact map the vanilla tag build
 * then consumes. Covers: modification, conflicts (add+remove of the same entry, replaceAll vs
 * later add), deferred deletion semantics of removeAll, and reload re-assertion (a new event
 * instance starts from the post-apply source state, per-load re-declaration).
 *
 * <p>Registry-light: entry construction and matching happen through the public TagEventJS
 * surface only; no registry is consulted.
 */
class TagEventJSApplyContractTest {

    private static Identifier id(String text) {
        return Identifier.parse(text);
    }

    private static Map<Identifier, List<TagLoader.EntryWithSource>> sourceMap() {
        Map<Identifier, List<TagLoader.EntryWithSource>> map = new HashMap<>();
        List<TagLoader.EntryWithSource> entries = new ArrayList<>();
        entries.add(new TagLoader.EntryWithSource(
                net.minecraft.tags.TagEntry.element(id("minecraft:stone")), "minecraft"));
        entries.add(new TagLoader.EntryWithSource(
                net.minecraft.tags.TagEntry.element(id("minecraft:dirt")), "minecraft"));
        map.put(id("nekojs:existing"), entries);
        return map;
    }

    private static List<String> entryIds(Map<Identifier, List<TagLoader.EntryWithSource>> map, String tag) {
        List<String> ids = new ArrayList<>();
        for (TagLoader.EntryWithSource entry : map.get(id(tag))) {
            ids.add(entry.entry().getId().toString());
        }
        return ids;
    }

    @Test
    void addAppendsAndRemoveDeletesByIdIgnoringSource() {
        Map<Identifier, List<TagLoader.EntryWithSource>> sourceMap = sourceMap();
        TagEventJS event = new TagEventJS(id("minecraft:item"), sourceMap);

        event.add("nekojs:existing", "nekojs:added");
        event.remove("nekojs:existing", "minecraft:stone");
        event.apply();

        assertEquals(List.of("minecraft:dirt", "nekojs:added"), entryIds(sourceMap, "nekojs:existing"),
                "remove deletes the matching source entry (source label ignored); add appends after it");
    }

    @Test
    void addCreatesTagsThatDidNotExistAndRemoveAllDeletesWholeTags() {
        Map<Identifier, List<TagLoader.EntryWithSource>> sourceMap = sourceMap();
        TagEventJS event = new TagEventJS(id("minecraft:item"), sourceMap);

        event.add("nekojs:brand_new", "minecraft:stone");
        event.apply();
        assertEquals(List.of("minecraft:stone"), entryIds(sourceMap, "nekojs:brand_new"),
                "adding to an unknown tag creates it in the loader map");

        TagEventJS second = new TagEventJS(id("minecraft:item"), sourceMap);
        second.removeAll("nekojs:brand_new");
        second.apply();
        assertFalse(sourceMap.containsKey(id("nekojs:brand_new")),
                "removeAll deletes the tag from the loader map (deferred to apply)");
    }

    @Test
    void replaceAllWinsOverPriorContentButLaterAddsStillAppend() {
        Map<Identifier, List<TagLoader.EntryWithSource>> sourceMap = sourceMap();
        TagEventJS event = new TagEventJS(id("minecraft:item"), sourceMap);

        event.replaceAll("nekojs:existing", "minecraft:oak_log", "minecraft:birch_log");
        event.add("nekojs:existing", "nekojs:extra");
        event.apply();

        assertEquals(List.of("minecraft:oak_log", "minecraft:birch_log", "nekojs:extra"),
                entryIds(sourceMap, "nekojs:existing"),
                "replaceAll replaces the whole tag content; additions declared afterwards still append");
    }

    @Test
    void reloadInstancesRestartDeclarationFromTheAppliedSourceState() {
        Map<Identifier, List<TagLoader.EntryWithSource>> sourceMap = sourceMap();
        TagEventJS first = new TagEventJS(id("minecraft:item"), sourceMap);
        first.add("nekojs:existing", "nekojs:first_pass");
        first.apply();

        // The next (re)load constructs a fresh event over the post-apply map: undeclared
        // modifications do not reappear, and the observed entries include the applied ones.
        TagEventJS second = new TagEventJS(id("minecraft:item"), sourceMap);
        assertEquals(List.of("minecraft:stone", "minecraft:dirt", "nekojs:first_pass"),
                second.getEntries("nekojs:existing"),
                "the reload pass observes exactly the previously applied state");
        second.add("nekojs:existing", "nekojs:second_pass");
        second.apply();
        assertEquals(List.of("minecraft:stone", "minecraft:dirt", "nekojs:first_pass", "nekojs:second_pass"),
                entryIds(sourceMap, "nekojs:existing"),
                "each load re-asserts its own declarations on top of the applied state");
    }

    @Test
    void getEntriesReadsTheCurrentSourceWithoutMutation() {
        Map<Identifier, List<TagLoader.EntryWithSource>> sourceMap = sourceMap();
        TagEventJS event = new TagEventJS(id("minecraft:item"), sourceMap);

        assertEquals(List.of("minecraft:stone", "minecraft:dirt"), event.getEntries("nekojs:existing"));
        assertEquals(List.of(), event.getEntries("nekojs:unknown"));
        assertEquals(List.of("minecraft:stone", "minecraft:dirt"), event.getEntries("nekojs:existing"),
                "querying does not mutate the source map");
    }
}
//?}
