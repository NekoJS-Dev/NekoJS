//? if >=26 {
package com.tkisor.nekojs.wrapper.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Pending tag set for registering builders ({@code .tag(...)}).
 *
 * <p>Registration events fire during STARTUP, while tags only get their bindings when the data pack loads, with the
 * whole startup flow in between. {@code .tag(...)} therefore only records a (registry key, tag id, target id) triple
 * on the builder ({@link #record}) and stores it in this class's static pending set; later, when
 * {@link com.tkisor.nekojs.wrapper.event.server.TagEventJS} is constructed (the {@code ServerEvents.tags} mechanism,
 * in the {@code TagLoader} build phase), {@link #flushInto} feeds them into the event per registry and the event's
 * {@code apply()} writes them back into the tag sources.
 *
 * <p>Pending entries are a stable fact about the registered objects: {@link #flushInto} does not consume the set, so
 * every tag (re)load feeds them in again (they still take effect after {@code /reload}). Registration events fire
 * only once per JVM lifetime, so at runtime no path re-registers a builder and invalidates old entries --
 * {@link #clear()} exists for a full STARTUP reload and for tests to wipe everything (called before builders
 * re-register, dropping the old entries with them).
 */
public final class BuilderTags {

    private BuilderTags() {
    }

    /** One pending tag: adds {@code targetId} to {@code tagId} of the {@code registryKey} registry. */
    public record PendingTag(ResourceKey<? extends Registry<?>> registryKey, Identifier tagId, Identifier targetId) {
    }

    private static final List<PendingTag> PENDING = new ArrayList<>();

    /**
     * Records a pending tag. A duplicate triple is recorded once (a builder calling {@code .tag(...)} twice has no
     * extra effect); a {@code null} argument is silently ignored (a misspelled script argument must not break the
     * registration flow).
     */
    public static void record(ResourceKey<? extends Registry<?>> registryKey, Identifier tagId, Identifier targetId) {
        if (registryKey == null || tagId == null || targetId == null) {
            return;
        }
        PendingTag pending = new PendingTag(registryKey, tagId, targetId);
        synchronized (BuilderTags.PENDING) {
            if (!PENDING.contains(pending)) {
                PENDING.add(pending);
            }
        }
    }

    /**
     * Feeds the pending entries whose registry id matches ({@code registryKey.identifier().equals(registryId)}) to
     * {@code adder}, the {@code add(tagId, targetId)} semantics exposed by
     * {@link com.tkisor.nekojs.wrapper.event.server.TagEventJS}. The set is not consumed: every tag (re)load feeds
     * them again, so pending entries survive a reload.
     */
    public static void flushInto(Identifier registryId, BiConsumer<Identifier, Identifier> adder) {
        List<PendingTag> snapshot;
        synchronized (BuilderTags.PENDING) {
            snapshot = List.copyOf(PENDING);
        }
        for (PendingTag pending : snapshot) {
            if (pending.registryKey().identifier().equals(registryId)) {
                adder.accept(pending.tagId(), pending.targetId());
            }
        }
    }

    /** Clears every pending entry (for a full STARTUP reload / tests). */
    public static void clear() {
        synchronized (BuilderTags.PENDING) {
            PENDING.clear();
        }
    }

    /** Current number of pending entries (for diagnostics / tests). */
    public static int size() {
        synchronized (BuilderTags.PENDING) {
            return PENDING.size();
        }
    }
}
//?}
