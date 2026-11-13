//? if >=26 {
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
 * Contract for a registry builder whose objects can carry tags: the implementation supplies the registry key the
 * tags belong to ({@link #getTagRegistry()}) and the id of the tagged object ({@link #getLocation()}); by default
 * {@link #tag(String...)} records entries into {@link BuilderTags}, which land on the real tags during the tag
 * loading phase (the {@code ServerEvents.tags} mechanism).
 *
 * <p>An implementation needs 1-3 lines: implement this interface (with the self type as the generic parameter) and
 * have {@link #getTagRegistry()} return the registry key (such as {@code return Registries.ITEM;}). When one builder
 * registers several objects (a fluid's source/flowing, say), override {@link #getTagTargets()} to add targets.
 */
public interface TaggableBuilder<Self extends TaggableBuilder<Self>> {

    /** The registry key the tags belong to (such as {@link Registries#ITEM} / {@link Registries#BLOCK}). */
    ResourceKey<? extends Registry<?>> getTagRegistry();

    /** The id of the object being tagged (the builder's registration id). */
    Identifier getLocation();

    /**
     * The default target is the registration id alone; override when one builder registers several objects
     * (such as {@link FluidBuilder}'s source + flowing fluids, matching the vanilla water tag, which holds both
     * {@code water} and {@code flowing_water}).
     */
    default List<Identifier> getTagTargets() {
        return List.of(getLocation());
    }

    /**
     * Tags the registered object: records it into the pending set of {@link BuilderTags} and lands it during the tag
     * loading phase.
     *
     * <p>Accepts {@code 'namespace:name'} and a bare {@code 'name'} (-> {@code minecraft:name}); a {@code '#'} prefix is
     * allowed (handy when copying {@code '#minecraft:mineable/pickaxe'} from documentation). Calls can be chained;
     * blank arguments are ignored.
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

    /** Strips an optional {@code '#'} prefix; returns {@code null} for blank input (skipped). */
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
//?}
