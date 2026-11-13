// 26.x implementation; no version guard should appear in this file again. The 1.21.1 implementation is the
// file of the same name under versions/1.21.1/src, so changing this file's behavior means updating it too.
//? if neoforge {
// TODO(fabric): fabric has no matching implementation yet; drop the whole-file guard once the port is done
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.wrapper.registry.BuilderTags;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TagEventJS {

    private static final String SOURCE = "NekoJS";

    private final Identifier registryId;
    private final Map<Identifier, List<TagLoader.EntryWithSource>> sourceMap;
    private final Map<Identifier, List<TagLoader.EntryWithSource>> additions = new HashMap<>();
    private final Map<Identifier, List<TagLoader.EntryWithSource>> removals = new HashMap<>();
    /** Deferred replacement for replaceAll/removeAll: apply clears the tag source list first, then writes the new entries. */
    private final Map<Identifier, List<TagLoader.EntryWithSource>> replacements = new HashMap<>();

    public TagEventJS(Identifier registryId, Map<Identifier, List<TagLoader.EntryWithSource>> sourceMap) {
        this.registryId = registryId;
        this.sourceMap = sourceMap;
        // Pending tags from registering builders (.tag(...), see BuilderTags) are injected into this event before script
        // listeners run: later script add/remove calls can still override them, and apply() writes everything back.
        // The pending entries are not consumed here; every tag (re)load injects them again (a stable fact that survives /reload).
        BuilderTags.flushInto(registryId, this::add);
    }

    public Identifier getRegistry() {
        return registryId;
    }

    public void add(String tag, String entry) {
        add(Identifier.parse(tag), Identifier.parse(entry));
    }

    public void add(Identifier tag, Identifier entry) {
        additions.computeIfAbsent(tag, k -> new ArrayList<>())
                .add(new TagLoader.EntryWithSource(TagEntry.element(entry), SOURCE));
    }

    public void remove(String tag, String entry) {
        remove(Identifier.parse(tag), Identifier.parse(entry));
    }

    public void remove(Identifier tag, Identifier entry) {
        removals.computeIfAbsent(tag, k -> new ArrayList<>())
                .add(new TagLoader.EntryWithSource(TagEntry.element(entry), SOURCE, true));
    }

    /** Clears every entry of the tag (applied later, so it composes correctly with add/remove). */
    public void removeAll(String tag) {
        removeAll(Identifier.parse(tag));
    }

    public void removeAll(Identifier tag) {
        replacements.put(tag, new ArrayList<>());
    }

    /** Replaces the whole content of the tag with the given entries. */
    public void replaceAll(String tag, String... entries) {
        replaceAll(Identifier.parse(tag), entries);
    }

    public void replaceAll(Identifier tag, String... entries) {
        List<TagLoader.EntryWithSource> list = new ArrayList<>();
        for (String entry : entries) {
            list.add(new TagLoader.EntryWithSource(TagEntry.element(Identifier.parse(entry)), SOURCE));
        }
        replacements.put(tag, list);
    }

    public List<String> getEntries(String tag) {
        var entries = sourceMap.get(Identifier.parse(tag));
        if (entries == null) return List.of();
        return entries.stream()
                .map(e -> e.entry().toString())
                .toList();
    }

    public void apply() {
        // Replacement (replaceAll/removeAll) wins: clear the tag source list, then write the new entries
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
            // TagEntry has no value equality (identity only), so removal matches on (id, isTag); otherwise the TagEntry
            // that remove() creates would never match the same-id entry in the source list (including builder pending entries)
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

    /** remove match key: element id plus whether it is a tag reference (a required difference is ignored). */
    private record RemovalKey(Identifier id, boolean tag) {
    }
}
//?}
