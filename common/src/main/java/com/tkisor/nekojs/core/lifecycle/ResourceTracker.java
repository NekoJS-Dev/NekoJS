package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.NekoJS;

import java.util.ArrayList;
import java.util.List;

/**
 * 可靠执行命名资源清理的 {@link AutoCloseable} 跟踪器。
 *
 * <p>支持两类清理：
 * <ul>
 *   <li>{@link #track(String, AutoCloseable)}：注册 {@code AutoCloseable} 资源，{@link #close()} 时调用其 {@code close()}。</li>
 *   <li>{@link #cleanup(String, Runnable)}：注册非 closeable 清理动作（例如 listener unregister、timer cancel、
 *       cache invalidate），{@link #close()} 时执行。</li>
 * </ul>
 *
 * <p>清理要求（见 {@code ai_arch/architecture.md} §4.3 与 {@code plan.md} Phase 2.2）：
 * <ul>
 *   <li>逆序执行 cleanup / close（后注册的先清理，符合创建-销毁反向顺序）。</li>
 *   <li>单个清理失败不中断后续清理：异常用 {@link Throwable#addSuppressed(Throwable)} 聚合到首个异常，
 *       全部清理完成后再抛出首个异常。</li>
 *   <li>日志和异常信息包含 resource name，便于定位 reload / shutdown 泄漏。</li>
 *   <li>{@code ResourceTracker} 只负责可靠执行清理动作；reload 失效顺序由 {@code ScriptReloadCoordinator}
 *       决定，module/cache/source-map revision 失效顺序由 {@code ModuleReloadCoordinator} 决定，
 *       runtime shutdown 顺序由 {@code NekoRuntimeRoot} 决定。不把业务失效策略藏进通用 tracker。</li>
 * </ul>
 */
public final class ResourceTracker implements AutoCloseable {
    private final List<Entry> entries = new ArrayList<>();
    private boolean closed;

    public <T extends AutoCloseable> T track(String name, T resource) {
        if (resource == null) {
            return null;
        }
        entries.add(new Entry(name, () -> {
            try {
                resource.close();
            } catch (Exception e) {
                throw new RuntimeException("close failed for '" + name + "'", e);
            }
        }, true));
        return resource;
    }

    public void cleanup(String name, Runnable cleanup) {
        if (cleanup == null) {
            return;
        }
        entries.add(new Entry(name, cleanup, false));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Throwable first = null;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
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
        entries.clear();
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

    private record Entry(String name, Runnable cleanup, boolean closeable) {}
}
