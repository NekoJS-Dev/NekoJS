package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiEvent;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiFacade;
import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiResources;
import com.tkisor.nekojs.api.ui.UiRootHandle;
import com.tkisor.nekojs.api.ui.UiScreen;
import com.tkisor.nekojs.api.ui.UiStateOwner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link UiFacade} 默认实现：管理一个 CLIENT generation 内的全部 UI root。
 *
 * <p>每个 root 有独立的依赖订阅与提交队列；owner thread 由 facade 统一收口。
 *
 * <p>lifecycle 语义：{@code closeGeneration}/{@code dispose} 幂等，重复调用是 no-op；
 * root 释放后其依赖订阅一并移除，之后向该 owner 写入不再触发任何 reconcile
 * （缺省状态语义，不是静默 no-op）。
 */
public final class RootUiFacade implements UiFacade {

    private final UiResources resources;
    private final Thread ownerThread;
    private final Map<UiRootHandle, DefaultUiStateOwner> roots = new LinkedHashMap<>();
    private final List<DefaultUiStateOwner> owners = new ArrayList<>();
    private int rootSequence;
    private boolean disposed;
    private com.tkisor.nekojs.api.ui.UiSize viewport;

    /** 用宿主资源构造 facade。 */
    public RootUiFacade(UiResources resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.ownerThread = resources.ownerThread() == null ? Thread.currentThread() : resources.ownerThread();
        this.viewport = resources.viewport();
    }

    UiResources resources() {
        return resources;
    }

    @Override
    public int generation() {
        return resources.generation();
    }

    /** 本 facade 的 client owner thread（Java 侧装配 API）。 */
    public Thread ownerThread() {
        return ownerThread;
    }

    /** 当前逻辑视口（Java 侧装配/布局查询；不属于脚本 contract）。 */
    public com.tkisor.nekojs.api.ui.UiSize viewport() {
        return viewport;
    }

    /** 窗口 resize：更新逻辑视口并让全部 root 布局失效（Java 侧装配 API）。 */
    public void resize(com.tkisor.nekojs.api.ui.UiSize next) {
        requireOwner("Ui.resize");
        if (next == null || next.equals(viewport)) {
            return;
        }
        viewport = next;
        for (UiRootHandle root : new ArrayList<>(roots.keySet())) {
            if (root.isActive()) {
                root.invalidateAndReconcile();
            }
        }
    }

    @Override
    public com.tkisor.nekojs.api.ui.UiStateOwner createStateOwner() {
        return newOwner();
    }

    @Override
    public UiRootHandle create(UiScreen screen) {
        requireOwner("Ui.create");
        requireOpen("Ui.create");
        Objects.requireNonNull(screen, "screen");
        DefaultUiStateOwner owner = newOwner();
        UiRoot root = new UiRoot(this, screen, screen.options(), owner, nextRootId());
        owner.register(root, root::requestReconcile);
        roots.put(root, owner);
        return root;
    }

    @Override
    public UiRootHandle open(UiScreen screen) {
        UiRootHandle handle = create(screen);
        handle.open();
        return handle;
    }

    @Override
    public void invalidateGeneration(int generation) {
        requireOwner("Ui.invalidateGeneration");
        if (generation != this.generation()) {
            return;
        }
        for (UiRootHandle root : new ArrayList<>(roots.keySet())) {
            root.invalidate();
        }
    }

    @Override
    public void closeGeneration(int generation) {
        if (generation != this.generation()) {
            return;
        }
        for (UiRootHandle root : new ArrayList<>(roots.keySet())) {
            root.dispose();
        }
    }

    @Override
    public List<UiRootHandle> roots() {
        return List.copyOf(roots.keySet());
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (UiRootHandle root : new ArrayList<>(roots.keySet())) {
            root.dispose();
        }
        for (DefaultUiStateOwner owner : owners) {
            owner.dispose();
        }
        owners.clear();
        roots.clear();
    }

    // ---- 内部接线 ----

    void dispatchToBindings(UiHostNode target, UiEvent event) {
        UiResources.UiEventSink sink = resources.eventSink();
        if (sink == null) {
            return;
        }
        sink.dispatch(target, event);
    }

    /** root 请求关闭（Escape / 外部替换）时进入同一 cleanup 路径。 */
    void onRootCloseRequested(UiRoot root) {
        root.dispose();
    }

    void onRootDisposed(UiRoot root) {
        roots.remove(root);
    }

    String nextRootId() {
        return "ui-root-" + (++rootSequence);
    }

    private DefaultUiStateOwner newOwner() {
        DefaultUiStateOwner owner = new DefaultUiStateOwner(ownerThread);
        owners.add(owner);
        return owner;
    }

    private void requireOwner(String operation) {
        if (Thread.currentThread() != ownerThread) {
            throw new UiFailure(UiDiagnosticStage.HOST_UPDATE, UiFailure.OFF_OWNER_THREAD,
                    operation + " must run on the UI owner thread '" + ownerThread.getName()
                            + "', was '" + Thread.currentThread().getName() + "'", null);
        }
    }

    private void requireOpen(String operation) {
        if (disposed) {
            throw new UiFailure(UiDiagnosticStage.HOST_UPDATE, UiFailure.ROOT_DISPOSED,
                    "UI facade for generation " + generation() + " is disposed; " + operation
                            + " is not allowed", null);
        }
    }
}
