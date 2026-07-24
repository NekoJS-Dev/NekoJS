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

    public TagEventJS(ResourceLocation registryId, Map<ResourceLocation, List<TagLoader.EntryWithSource>> sourceMap) {
        this.registryId = registryId;
        this.sourceMap = sourceMap;
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

    public void removeAll(String tag) {
        removeAll(ResourceLocation.parse(tag));
    }

    public void removeAll(ResourceLocation tag) {
        sourceMap.remove(tag);
    }

    public List<String> getEntries(String tag) {
        var entries = sourceMap.get(ResourceLocation.parse(tag));
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
