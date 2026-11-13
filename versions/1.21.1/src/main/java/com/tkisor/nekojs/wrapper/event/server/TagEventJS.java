// 1.21.1 implementation, paired with the 26.x file of the same name under the version tree's src/.
// The content is that file as evaluated on this node. Keep this file in sync when the 26.x side
// changes behavior.
package com.tkisor.nekojs.wrapper.event.server;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TagEventJS {

    private static final String SOURCE = "NekoJS";

    private final ResourceLocation registryId;
    private final Map<ResourceLocation, List<TagLoader.EntryWithSource>> sourceMap;
    private final Map<ResourceLocation, List<TagLoader.EntryWithSource>> additions = new HashMap<>();
    private final Map<ResourceLocation, List<TagLoader.EntryWithSource>> removals = new HashMap<>();
    /** Delayed replacement for replaceAll/removeAll: apply() empties the tag source list first and
     *  then writes the new entries. */
    private final Map<ResourceLocation, List<TagLoader.EntryWithSource>> replacements = new HashMap<>();

    public TagEventJS(ResourceLocation registryId, Map<ResourceLocation, List<TagLoader.EntryWithSource>> sourceMap) {
        this.registryId = registryId;
        this.sourceMap = sourceMap;
        // Tags a registration builder queues (.tag(...), see BuilderTags) are injected into this event
        // before the script listeners run, so a later add/remove from the script still wins and
        // apply() writes everything back in one pass. These pending entries are not consumed here:
        // every tag (re)load injects them again, which is why they survive a /reload.
    }

    public ResourceLocation getRegistry() {
        return registryId;
    }

    public void add(String tag, String entry) {
        add(ResourceLocation.parse(tag), ResourceLocation.parse(entry));
    }

    public void add(ResourceLocation tag, ResourceLocation entry) {
        additions.computeIfAbsent(tag, k -> new ArrayList<>())
                .add(new TagLoader.EntryWithSource(TagEntry.element(entry), SOURCE));
    }

    public void remove(String tag, String entry) {
        remove(ResourceLocation.parse(tag), ResourceLocation.parse(entry));
    }

    public void remove(ResourceLocation tag, ResourceLocation entry) {
        removals.computeIfAbsent(tag, k -> new ArrayList<>())
                .add(new TagLoader.EntryWithSource(TagEntry.element(entry), SOURCE, true));
    }

    /** Removes every entry of the tag. Applied later, so it composes correctly with add/remove. */
    public void removeAll(String tag) {
        removeAll(ResourceLocation.parse(tag));
    }

    public void removeAll(ResourceLocation tag) {
        replacements.put(tag, new ArrayList<>());
    }

    /** Replaces the whole content of the tag with the given entries. */
    public void replaceAll(String tag, String... entries) {
        replaceAll(ResourceLocation.parse(tag), entries);
    }

    public void replaceAll(ResourceLocation tag, String... entries) {
        List<TagLoader.EntryWithSource> list = new ArrayList<>();
        for (String entry : entries) {
            list.add(new TagLoader.EntryWithSource(TagEntry.element(ResourceLocation.parse(entry)), SOURCE));
        }
        replacements.put(tag, list);
    }

    public List<String> getEntries(String tag) {
        var entries = sourceMap.get(ResourceLocation.parse(tag));
        if (entries == null) return List.of();
        return entries.stream()
                .map(e -> e.entry().toString())
                .toList();
    }

    public void apply() {
        // Replacements (replaceAll/removeAll) come first: empty the tag's source list, then write the
        // new entries.
        for (var entry : replacements.entrySet()) {
            if (entry.getValue().isEmpty()) {
                sourceMap.remove(entry.getKey());
            } else {
                sourceMap.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        for (var entry : additions.entrySet()) {
            var list = sourceMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
            list.addAll(entry.getValue());
        }
        for (var entry : removals.entrySet()) {
            var list = sourceMap.get(entry.getKey());
            if (list == null) continue;
            // TagEntry has no value equality (identity only), so removal matches on (id, isTag).
            // Otherwise a TagEntry created by remove() would never match an entry with the same id in
            // the source table, including the entries a builder still has pending.
            var toRemove = entry.getValue().stream()
                    .map(TagLoader.EntryWithSource::entry)
                    .map(e -> new RemovalKey(e.getId(), e.isTag()))
                    .collect(java.util.stream.Collectors.toSet());
            list.removeIf(e -> {
                var target = e.entry();
                return toRemove.contains(new RemovalKey(target.getId(), target.isTag()));
            });
        }
    }

    /** Matching key for remove: the entry id plus whether it is a tag reference; the required flag is
     *  ignored. */
    private record RemovalKey(ResourceLocation id, boolean tag) {
    }
}
