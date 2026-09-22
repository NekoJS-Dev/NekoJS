package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.ScriptType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bounded, repeatable {@link JavaClassLoadTelemetrySink} implementation (ticket 30).
 *
 * <p>Privacy and size bounds — what this recorder collects and what it never collects:
 * <ul>
 *   <li><b>Includes</b>: the script type, the script id (engine-internal authored path
 *       identity such as {@code server_scripts/foo.js}, never an absolute filesystem path),
 *       the engine-side Java class name, and whether the class access was allowed;</li>
 *   <li><b>Excludes</b>: script contents, absolute user paths, system properties and any
 *       other environment information;</li>
 *   <li><b>Size bounds</b>: at most {@link #MAX_ENTRIES} distinct entries are kept; once the
 *       bound is reached, the oldest entries are evicted (the collection degrades instead of
 *       growing unbounded). Repeated observations of the same entry are counted, not
 *       re-appended.</li>
 * </ul>
 *
 * <p>Repeatable collection: {@link #snapshot()} returns the currently retained entries in
 * recording order and can be called any number of times with the same result until new
 * observations arrive. Telemetry remains disable-able and degradable: without a sink
 * (see {@link JavaClassLoadTelemetry#setSink}) nothing is recorded at all, and installing this
 * recorder can be undone by re-installing {@code null} at any time.
 *
 * <p>Thread safety: {@code recordAttempt}/{@code recordLoad} may be called from any thread
 * (the telemetry scope is thread-local); {@link #snapshot()} may race with concurrent
 * recording and returns a consistent point-in-time copy.
 */
public final class JavaClassLoadTelemetryRecorder implements JavaClassLoadTelemetrySink {
    /** Upper bound on retained distinct entries; oldest entries are evicted beyond it. */
    public static final int MAX_ENTRIES = 8192;

    /** One retained observation row: type, script id, class name, allowed flag and count. */
    public record Row(ScriptType scriptType, String scriptId, String className, boolean allowed, long count) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s %s %s allowed=%s count=%d",
                    scriptType, scriptId, className, allowed, count);
        }
    }

    private record Key(ScriptType scriptType, String scriptId, String className, boolean allowed) {}

    private final int maxEntries;
    /** Guards the ordered map and the per-entry counters. */
    private final Object lock = new Object();
    private final LinkedHashMap<Key, long[]> orderedEntries;

    public JavaClassLoadTelemetryRecorder() {
        this(MAX_ENTRIES);
    }

    /** Test seam: a smaller bound exercises eviction without producing 8192 entries. */
    JavaClassLoadTelemetryRecorder(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive: " + maxEntries);
        }
        this.maxEntries = maxEntries;
        this.orderedEntries = new LinkedHashMap<>(Math.min(maxEntries, 256) * 2);
    }

    @Override
    public void recordAttempt(ScriptType scriptType, String scriptId, String className, boolean allowed) {
        record(scriptType, scriptId, className, allowed);
    }

    @Override
    public void recordLoad(ScriptType scriptType, String scriptId, String className) {
        record(scriptType, scriptId, className, true);
    }

    private void record(ScriptType scriptType, String scriptId, String className, boolean allowed) {
        if (scriptType == null || scriptId == null || className == null) {
            return;
        }
        Key key = new Key(scriptType, scriptId, className, allowed);
        synchronized (lock) {
            long[] counter = orderedEntries.get(key);
            if (counter != null) {
                counter[0]++;
                return;
            }
            orderedEntries.put(key, new long[] {1L});
            while (orderedEntries.size() > maxEntries) {
                Iterator<Map.Entry<Key, long[]>> eldest = orderedEntries.entrySet().iterator();
                if (eldest.hasNext()) {
                    eldest.next();
                    eldest.remove();
                } else {
                    break;
                }
            }
        }
    }

    /** Retained entries in recording order; a point-in-time copy, safe to iterate freely. */
    public List<Row> snapshot() {
        synchronized (lock) {
            List<Row> rows = new ArrayList<>(orderedEntries.size());
            for (Map.Entry<Key, long[]> entry : orderedEntries.entrySet()) {
                Key key = entry.getKey();
                rows.add(new Row(key.scriptType(), key.scriptId(), key.className(), key.allowed(),
                        entry.getValue()[0]));
            }
            return List.copyOf(rows);
        }
    }

    /** Number of retained distinct entries (bounded by the constructor cap). */
    public int retainedEntries() {
        synchronized (lock) {
            return orderedEntries.size();
        }
    }

    @Override
    public String toString() {
        return "JavaClassLoadTelemetryRecorder[retained=" + retainedEntries() + ", max=" + maxEntries + "]";
    }
}
