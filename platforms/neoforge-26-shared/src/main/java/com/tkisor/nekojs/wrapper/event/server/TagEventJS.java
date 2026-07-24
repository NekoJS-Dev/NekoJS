package com.tkisor.nekojs.wrapper.event.server;

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

    public TagEventJS(Identifier registryId, Map<Identifier, List<TagLoader.EntryWithSource>> sourceMap) {
        this.registryId = registryId;
        this.sourceMap = sourceMap;
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

    public void removeAll(String tag) {
        removeAll(Identifier.parse(tag));
    }

    public void removeAll(Identifier tag) {
        sourceMap.remove(tag);
    }

    public List<String> getEntries(String tag) {
        var entries = sourceMap.get(Identifier.parse(tag));
        if (entries == null) return List.of();
        return entries.stream()
                .map(e -> e.entry().toString())
                .toList();
    }

    public void apply() {
        for (var entry : additions.entrySet()) {
            var list = sourceMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
            list.addAll(entry.getValue());
        }
        for (var entry : removals.entrySet()) {
            var list = sourceMap.get(entry.getKey());
            if (list == null) continue;
            var toRemove = entry.getValue().stream()
                    .map(TagLoader.EntryWithSource::entry)
                    .collect(java.util.stream.Collectors.toSet());
            list.removeIf(e -> toRemove.contains(e.entry()));
        }
    }
}
