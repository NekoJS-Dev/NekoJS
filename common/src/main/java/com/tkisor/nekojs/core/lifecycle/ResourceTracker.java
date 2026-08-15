package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.NekoJS;

import java.util.ArrayList;
import java.util.List;

/**
 * 可靠的资源清理追踪器：注册 {@link AutoCloseable} 资源或 {@link Runnable} 清理动作，
 * {@link #close()} 时逆序执行所有清理，单个失败不中断后续。
 *
 * <p>日志和异常信息包含 resource name，便于定位泄漏。
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
        // 用户清理动作在锁外执行：它们可能阻塞或重入 tracker 的查询方法，不应长期持有监视器。
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
