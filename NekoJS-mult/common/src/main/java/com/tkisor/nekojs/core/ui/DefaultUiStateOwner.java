package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiRootHandle;
import com.tkisor.nekojs.api.ui.UiSignal;
import com.tkisor.nekojs.api.ui.UiStateOwner;
import com.tkisor.nekojs.api.ui.UiStore;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@link UiStateOwner} 默认实现。
 *
 * <p>依赖跟踪：render function 执行期间（{@link #beginTracking}/{@link #endTracking}），
 * 任何 {@code signal.get()} / {@code store.read()} 都把「该依赖 → 当前 root」登记下来。
 * 写入只让登记过该依赖的 root 失效——调用者只从「哪些 root 被重新渲染」观察语义，
 * 不检查私有依赖集合。
 *
 * <p>owner-thread：写入/合并/驱动 tick 只能在 owner thread 上执行；其它线程显式失败
 * （{@link UiFailure#OFF_OWNER_THREAD}），不依赖 GraalJS Context 的隐式跨线程能力。
 *
 * <p>批量失效：同一 tick 内对同一依赖的重复写入合并为一次 root 失效；
 * {@link #runTick()} 把累积失效一次性交给各 root 的 invalidator。root 被释放
 * （disposed）后，其依赖登记随 {@code registered} 句柄注销而移除，写入不再触发
 * 任何 reconcile。
 */
public final class DefaultUiStateOwner implements UiStateOwner {

    private final Thread ownerThread;
    private final Map<UiRootHandle, Invalidator> bindings = new LinkedHashMap<>();
    private final Map<Object, Set<UiRootHandle>> dependents = new IdentityHashMap<>();
    private final Map<UiRootHandle, Set<Object>> dependenciesByRoot = new IdentityHashMap<>();
    private final Set<UiRootHandle> pending = new LinkedHashSet<>();
    private final Set<Object> tracking = new LinkedHashSet<>();
    private UiRootHandle trackingRoot;
    private boolean disposed;
    private int signalIndex;

    /** 用给定 owner thread 构造；{@code null} 表示当前线程。 */
    public DefaultUiStateOwner(Thread ownerThread) {
        this.ownerThread = ownerThread == null ? Thread.currentThread() : ownerThread;
    }

    @Override
    public Thread ownerThread() {
        return ownerThread;
    }

    @Override
    public boolean onOwnerThread() {
        return Thread.currentThread() == ownerThread;
    }

    @Override
    public <T> UiSignal<T> createSignal(T initial) {
        return new DefaultSignal<>(this, "signal" + (++signalIndex), initial);
    }

    @Override
    public UiStore createStore(Map<String, ?> initial) {
        return new DefaultStore(this, initial);
    }

    @Override
    public Runnable register(UiRootHandle root, Invalidator invalidator) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(invalidator, "invalidator");
        bindings.put(root, invalidator);
        return () -> {
            bindings.remove(root);
            Set<Object> deps = dependenciesByRoot.remove(root);
            if (deps != null) {
                for (Object dependency : deps) {
                    Set<UiRootHandle> roots = dependents.get(dependency);
                    if (roots != null) {
                        roots.remove(root);
                    }
                }
            }
            pending.remove(root);
        };
    }

    /** 注销一个 root 的失效绑定与依赖登记（幂等）。 */
    public void unregister(UiRootHandle root) {
        if (root == null) {
            return;
        }
        bindings.remove(root);
        Set<Object> deps = dependenciesByRoot.remove(root);
        if (deps != null) {
            for (Object dependency : deps) {
                Set<UiRootHandle> roots = dependents.get(dependency);
                if (roots != null) {
                    roots.remove(root);
                }
            }
        }
        pending.remove(root);
    }

    /**
     * 开始依赖跟踪：reconciler 在执行 root render function 前调用。
     *
     * @throws IllegalStateException 已有跟踪在进行（不允许嵌套 render）
     */
    public void beginTracking(UiRootHandle root) {
        Objects.requireNonNull(root, "root");
        if (trackingRoot != null) {
            throw new IllegalStateException("dependency tracking is already active for " + trackingRoot.rootId());
        }
        trackingRoot = root;
        tracking.clear();
    }

    /** 结束依赖跟踪，并用本次读取重建该 root 的依赖集合。 */
    public void endTracking(UiRootHandle root) {
        if (trackingRoot == null || !trackingRoot.equals(root)) {
            return;
        }
        Set<Object> previous = dependenciesByRoot.get(root);
        if (previous != null) {
            for (Object dependency : previous) {
                if (!tracking.contains(dependency)) {
                    Set<UiRootHandle> roots = dependents.get(dependency);
                    if (roots != null) {
                        roots.remove(root);
                    }
                }
            }
        }
        Set<Object> snapshot = new LinkedHashSet<>(tracking);
        dependenciesByRoot.put(root, snapshot);
        for (Object dependency : snapshot) {
            dependents.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(root);
        }
        trackingRoot = null;
        tracking.clear();
    }

    /** 依赖读取钩子：仅在跟踪期间生效。 */
    void link(Object dependency) {
        if (trackingRoot != null && dependency != null) {
            tracking.add(dependency);
        }
    }

    /** 当前是否正在跟踪（内部与诊断用）。 */
    public boolean isTracking() {
        return trackingRoot != null;
    }

    /** 判断某依赖当前是否有效（root 仍注册、owner 未释放、依赖未被替换）。 */
    void markDirty(Object dependency) {
        if (disposed) {
            return;
        }
        Set<UiRootHandle> roots = dependents.get(dependency);
        if (roots == null || roots.isEmpty()) {
            return;
        }
        for (UiRootHandle root : roots) {
            if (bindings.containsKey(root)) {
                pending.add(root);
            }
        }
    }

    /** 校验调用线程是 owner thread；否则显式失败。 */
    void requireOwnerThread(String operation) {
        if (Thread.currentThread() != ownerThread) {
            throw new UiFailure(UiDiagnosticStage.HOST_UPDATE, UiFailure.OFF_OWNER_THREAD,
                    operation + " must run on the UI owner thread '" + ownerThread.getName()
                            + "', was '" + Thread.currentThread().getName() + "'", null);
        }
    }

    @Override
    public void runTick() {
        requireOwnerThread("stateOwner.runTick");
        if (disposed || pending.isEmpty()) {
            return;
        }
        List<UiRootHandle> due = new ArrayList<>(pending);
        pending.clear();
        for (UiRootHandle root : due) {
            Invalidator invalidator = bindings.get(root);
            if (invalidator != null) {
                invalidator.requestReconcile();
            }
        }
    }

    @Override
    public boolean hasPendingInvalidation() {
        return !pending.isEmpty();
    }

    @Override
    public void dispose() {
        disposed = true;
        pending.clear();
        bindings.clear();
        dependenciesByRoot.clear();
        dependents.clear();
        tracking.clear();
        trackingRoot = null;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    /** 已注册的 root 数（诊断）。 */
    public int registeredRootCount() {
        return bindings.size();
    }
}
