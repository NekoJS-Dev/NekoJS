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
    /** replaceAll/removeAll 的延迟替换：apply 时先清空 tag 源列表再写入新条目。 */
    private final Map<Identifier, List<TagLoader.EntryWithSource>> replacements = new HashMap<>();

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

    /** 清空 tag 的全部条目（延迟应用，与 add/remove 组合时语义正确）。 */
    public void removeAll(String tag) {
        removeAll(Identifier.parse(tag));
    }

    public void removeAll(Identifier tag) {
        replacements.put(tag, new ArrayList<>());
    }

    /** 用新条目整体替换 tag 的全部内容。 */
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
        // 替换（replaceAll/removeAll）优先：清空 tag 源列表，再写入新条目
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
            var toRemove = entry.getValue().stream()
                    .map(TagLoader.EntryWithSource::entry)
                    .collect(java.util.stream.Collectors.toSet());
            list.removeIf(e -> toRemove.contains(e.entry()));
        }
    }
}
