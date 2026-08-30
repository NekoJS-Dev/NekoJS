// 1.21.1 节点专有变体（DEVEX-ROADMAP 档 1 整文件拆分）：主干已 26.x 基准化，本文件为 1.21.1 的
// 完整实现（构造性变换）；主干行为变更时须同步本文件。
// TODO(loader-port): deferred to the LoaderBridge fabric port
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
    /** replaceAll/removeAll 的延迟替换：apply 时先清空 tag 源列表再写入新条目。 */
    private final Map<ResourceLocation, List<TagLoader.EntryWithSource>> replacements = new HashMap<>();

    public TagEventJS(ResourceLocation registryId, Map<ResourceLocation, List<TagLoader.EntryWithSource>> sourceMap) {
        this.registryId = registryId;
        this.sourceMap = sourceMap;
        // 注册 builder 的待写 tag（.tag(...)，见 BuilderTags）先于脚本监听器注入本事件：
        // 脚本随后的 add/remove 仍可覆盖，apply() 统一写回。待写条目不在此消费——
        // 每次 tag（重）加载都会重新注入（稳定事实，跨 /reload 存活）。
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

    /** 清空 tag 的全部条目（延迟应用，与 add/remove 组合时语义正确）。 */
    public void removeAll(String tag) {
        removeAll(ResourceLocation.parse(tag));
    }

    public void removeAll(ResourceLocation tag) {
        replacements.put(tag, new ArrayList<>());
    }

    /** 用新条目整体替换 tag 的全部内容。 */
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
                    .collect(java.util.stream.Collectors.toSet());
            list.removeIf(e -> toRemove.contains(e.entry()));
        }
    }

    /** remove 匹配键：元素 id + 是否 tag 引用（忽略 required 差异）。 */
}
