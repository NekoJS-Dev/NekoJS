package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.core.dynamic.DynamicRegistrationBookkeeping;

import java.util.ArrayList;
import java.util.List;

/**
 * Debug view over {@link DynamicRegistries}, consumed by the
 * {@code /nekojs registry [stale]} subcommand (command wiring itself is owned
 * by {@code NekoJSCommands} and added in a separate batch).
 *
 * <pre>{@code
 * // /nekojs registry
 * for (DynamicRegistryDebug.RegistrySnapshot snapshot : DynamicRegistryDebug.snapshot()) {
 *     source.sendSystemMessage(Component.literal(snapshot.summary()));
 * }
 * // /nekojs registry stale
 * DynamicRegistryDebug.snapshot().stream()
 *         .flatMap(s -> java.util.stream.Stream.of(s.prettyStaleIds()))
 *         ...
 * }</pre>
 */
public final class DynamicRegistryDebug {

    /** Per-registry debug data: tracked claims and which went stale after the last reload. */
    public record RegistrySnapshot(String registry, int count, List<String> staleIds) {

        /** One-line summary, e.g. {@code sound_event: 3 registered, 1 stale}. */
        public String summary() {
            return registry + ": " + count + " registered, " + staleIds.size() + " stale";
        }

        /** Multi-line listing of the stale ids ("registry id (owner)"), empty when none are stale. */
        public List<String> prettyStaleIds() {
            List<String> lines = new ArrayList<>();
            for (String id : staleIds) {
                lines.add(registry + " " + id);
            }
            return lines;
        }
    }

    private DynamicRegistryDebug() {
    }

    /** Snapshot of every runtime registry: {count, staleIds} per registry. */
    public static List<RegistrySnapshot> snapshot() {
        List<RegistrySnapshot> snapshots = new ArrayList<>();
        for (DynamicRegistrationBookkeeping bookkeeping : DynamicRegistries.bookkeepings()) {
            snapshots.add(new RegistrySnapshot(
                    bookkeeping.registry(),
                    bookkeeping.count(),
                    bookkeeping.staleIds()));
        }
        return snapshots;
    }

    /** Flat summary lines of {@link #snapshot()}. */
    public static List<String> summaryLines() {
        List<String> lines = new ArrayList<>();
        for (RegistrySnapshot snapshot : snapshot()) {
            lines.add(snapshot.summary());
            lines.addAll(snapshot.prettyStaleIds());
        }
        return lines;
    }
}
