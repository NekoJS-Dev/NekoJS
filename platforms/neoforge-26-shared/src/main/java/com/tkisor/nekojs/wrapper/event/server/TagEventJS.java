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
    /** replaceAll/removeAll 的延迟替换：apply 时先清空 tag 源列表再写入新条目。 */
    private final Map<Identifier, List<TagLoader.EntryWithSource>> replacements = new HashMap<>();

    public TagEventJS(Identifier registryId, Map<Identifier, List<TagLoader.EntryWithSource>> sourceMap) {
        this.registryId = registryId;
        this.sourceMap = sourceMap;
        // 注册 builder 的待写 tag（.tag(...)，见 BuilderTags）先于脚本监听器注入本事件：
        // 脚本随后的 add/remove 仍可覆盖，apply() 统一写回。待写条目不在此消费——
        // 每次 tag（重）加载都会重新注入（稳定事实，跨 /reload 存活）。
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
            // TagEntry 无值相等（identity），按 (id, isTag) 匹配移除——否则 remove() 新建的
            // TagEntry 永远匹配不上源表里的同 id 条目（含 builder 待写条目）
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

    /** remove 匹配键：元素 id + 是否 tag 引用（忽略 required 差异）。 */
    private record RemovalKey(Identifier id, boolean tag) {
    }
}
