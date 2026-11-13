package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.NekoJS;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks resources for reliable cleanup. Register an {@link AutoCloseable} resource or a
 * {@link Runnable} cleanup action, and {@link #close()} runs every cleanup in reverse order. One
 * failure does not stop the rest.
 *
 * <p>Log messages and exception messages include the resource name, which makes a leak easier to
 * locate.
 */
public final class ResourceTracker implements AutoCloseable {
    private final List<Entry> entries = new ArrayList<>();
    private boolean closed;

    public synchronized <T extends AutoCloseable> T track(String name, T resource) {
        ensureOpen();
        if (resource == null) {
            return null;
        }
        entries.add(new Entry(name, () -> {
            try {
                resource.close();
            } catch (Exception e) {
                throw new RuntimeException("close failed for '" + name + "'", e);
            }
        }));
        return resource;
    }

    public synchronized void cleanup(String name, Runnable cleanup) {
        ensureOpen();
        if (cleanup == null) {
            return;
        }
        entries.add(new Entry(name, cleanup));
    }

    public synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    public synchronized int size() {
        return entries.size();
    }

    @Override
    public void close() {
        List<Entry> snapshot;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            snapshot = List.copyOf(entries);
            entries.clear();
        }
        // User cleanup actions run outside the lock: they may block or re-enter the tracker's query
        // methods, so the monitor must not be held for long.
        Throwable first = null;
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            Entry entry = snapshot.get(i);
            try {
                entry.cleanup().run();
            } catch (Throwable t) {
                NekoJS.LOGGER.error("Resource cleanup failed for '{}': {}", entry.name(), t.toString(), t);
                if (first == null) {
                    first = t;
                } else {
                    first.addSuppressed(t);
                }
            }
        }
        if (first != null) {
            if (first instanceof Error e) {
                throw e;
            }
            if (first instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Resource cleanup failed for '" + first.getMessage() + "'", first);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("ResourceTracker is already closed");
        }
    }

    private record Entry(String name, Runnable cleanup) {}
}
