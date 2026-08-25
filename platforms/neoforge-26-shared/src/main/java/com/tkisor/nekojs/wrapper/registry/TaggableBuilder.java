package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.List;

/**
 * 可打 tag 的注册 builder 契约：实现方给出 tag 归属的注册表 key（{@link #getTagRegistry()}）
 * 与被打对象 id（{@link #getLocation()}），默认 {@link #tag(String...)} 把条目记入
 * {@link BuilderTags}，等 tag 加载阶段（{@code ServerEvents.tags} 机制）落到实际 tag 上。
 *
 * <p>实现方只需 1-3 行：实现本接口（自类型作泛型参数）并让 {@link #getTagRegistry()}
 * 返回注册表 key（如 {@code return Registries.ITEM;}）。一个 builder 注册多个对象时
 * （如流体的 source/flowing）覆盖 {@link #getTagTargets()} 追加目标。
 */
public interface TaggableBuilder<Self extends TaggableBuilder<Self>> {

    /** tag 归属的注册表 key（如 {@link Registries#ITEM} / {@link Registries#BLOCK}）。 */
    ResourceKey<? extends Registry<?>> getTagRegistry();

    /** 被打 tag 的对象 id（builder 的注册 id）。 */
    Identifier getLocation();

    /**
     * tag 落录的默认目标只有注册 id 本身；一个 builder 注册多个对象时覆盖
     * （如 {@link FluidBuilderJS} 的 source + flowing 两个流体，对标原版 water tag
     * 同时含 {@code water} 与 {@code flowing_water}）。
     */
    default List<Identifier> getTagTargets() {
        return List.of(getLocation());
    }

    /**
     * 给注册对象打 tag：记入 {@link BuilderTags} 待写集合，tag 加载阶段统一落盘。
     *
     * <p>接受 {@code 'namespace:name'} 与裸 {@code 'name'}（→ {@code minecraft:name}）；
     * 允许带 {@code '#'} 前缀（从文档复制 {@code '#minecraft:mineable/pickaxe'} 时友好）。
     * 可链式多次调用；空白参数被忽略。
     */
    @Doc("Adds this object to one or more registry tags, e.g. tag('c:tools/pickaxe') or tag('minecraft:mineable/pickaxe', 'c:my_tag').")
    @Doc("Entries are pending facts about the registered object: they are applied every time tags load (server start and /reload) before ServerEvents.tags listeners run, so scripts can still add or remove entries on top.")
    @Param(name = "tags", value = "tag id(s): 'namespace:name', or plain 'name' for 'minecraft:name'; a leading '#' is tolerated")
    @Return("this builder, for chaining")
    @SuppressWarnings("unchecked")
    default Self tag(String... tags) {
        if (tags == null) {
            return (Self) this;
        }
        for (String raw : tags) {
            Identifier tagId = parseTag(raw);
            if (tagId == null) {
                continue;
            }
            for (Identifier target : getTagTargets()) {
                BuilderTags.record(getTagRegistry(), tagId, target);
            }
        }
        return (Self) this;
    }

    /** 去掉可选 {@code '#'} 前缀；空白返回 {@code null}（跳过）。 */
    private static Identifier parseTag(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.startsWith("#") ? raw.substring(1) : raw;
        if (normalized.isBlank()) {
            return null;
        }
        return Identifier.parse(normalized);
    }
}
