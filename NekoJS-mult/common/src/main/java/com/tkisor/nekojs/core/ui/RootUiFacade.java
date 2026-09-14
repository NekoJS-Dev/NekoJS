package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiEvent;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiFacade;
import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiResources;
import com.tkisor.nekojs.api.ui.UiRootHandle;
import com.tkisor.nekojs.api.ui.UiScreen;
import com.tkisor.nekojs.api.ui.UiSize;
import com.tkisor.nekojs.api.ui.UiStateOwner;
import com.tkisor.nekojs.api.ui.ViewportContext;
import com.tkisor.nekojs.api.ui.ViewportProfile;

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
    /** 构造时的 owner thread：仅在 {@link UiResources#ownerThread()} 返回 null 时作为回退。 */
    private final Thread constructionThread;
    private final com.tkisor.nekojs.api.ui.UiScreenHost screenHost;
    private final Map<UiRootHandle, DefaultUiStateOwner> roots = new LinkedHashMap<>();
    private final List<DefaultUiStateOwner> owners = new ArrayList<>();
    private int rootSequence;
    private boolean disposed;
    private ViewportContext viewportContext;

    /** 用宿主资源构造 facade。 */
    public RootUiFacade(UiResources resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.constructionThread = Thread.currentThread();
        this.viewportContext = resources.viewportContext();
        this.screenHost = resources.screenHost() == null
                ? com.tkisor.nekojs.api.ui.UiScreenHost.NONE : resources.screenHost();
    }

    /**
     * 原生 Screen 投影边界（Java 侧装配/诊断；不属于脚本 contract）。
     *
     * <p>票 41：平台 Adapter 经此投影原生 Screen，{@code NONE} 表示纯逻辑路径。
     */
    public com.tkisor.nekojs.api.ui.UiScreenHost screenHost() {
        return screenHost;
    }

    /**
     * 平台 Screen 报告的当前逻辑视口（Java 侧入口，不触发额外 reconcile）。
     *
     * <p>票 41/43 合并：只同步逻辑视口时保留既有安全区域/scale/能力（平台 resize 回调只报尺寸）。
     * 需要整体替换响应式事实请用 {@link #resize(ViewportContext)}。
     */
    void setViewport(com.tkisor.nekojs.api.ui.UiSize next) {
        if (next != null && !next.equals(viewport())) {
            this.viewportContext = viewportContext.withLogical(next.width(), next.height());
        }
    }

    UiResources resources() {
        return resources;
    }

    @Override
    public int generation() {
        return resources.generation();
    }

    /**
     * 本 facade 的 client owner thread。
     *
     * <p><b>每次调用都向 {@link UiResources#ownerThread()} 重新求值</b>：客户端 owner thread
     * 只有在客户端真正就绪后才存在，装配期（mod 构造 / 插件 bootstrap）拿到的是当时恰好执行
     * 构造的线程，不是稳定事实。快照它会把「装配线程 == owner thread」这个错误前提固化，
     * 从而把合法的客户端线程（Render thread）误判成 OFF_OWNER_THREAD。jsx-ui-spec
     * 「状态线程」要求的是 owner thread 归属，不是装配时机。
     */
    public Thread ownerThread() {
        Thread resolved = resources.ownerThread();
        return resolved == null ? constructionThread : resolved;
    }

    /**
     * 当前逻辑视口（Java 侧装配/布局查询）。<b>刻意不进 {@code UiFacade} 脚本契约</b>：
     * 视口由平台 Screen 的 resize 回调驱动，脚本通过布局结果观察，不需要读写契约成员
     * （新增契约成员会改变冻结的 api-manifest，属维护者审阅范围）。
     */
    public UiSize viewport() {
        return new UiSize(viewportContext.logicalWidth(), viewportContext.logicalHeight());
    }

    /** 当前响应式视口事实：逻辑视口、安全区域、原生 scale、host 布局能力（Java 侧装配 API）。 */
    public ViewportContext viewportContext() {
        return viewportContext;
    }

    /** 当前判定出的 Viewport Profile（Java 侧装配/诊断查询）。 */
    public ViewportProfile profile() {
        return viewportContext.profile();
    }

    /**
     * 窗口 resize：只同步逻辑视口（平台 Screen 的 resize 回调入口；保留安全区域/scale/能力）。
     *
     * <p>票 41 引入此便捷重载；语义等价于把 {@link #viewportContext()} 的逻辑尺寸替换为 {@code next}。
     */
    public void resize(UiSize next) {
        if (next == null) {
            return;
        }
        resize(viewportContext.withLogical(next.width(), next.height()));
    }

    /**
     * 窗口 resize：更新响应式视口事实并让全部 root 布局失效（Java 侧装配 API）。
     *
     * <p>视口事实没变时是显式无操作（不触发多余重排）。语义变化才走
     * {@link UiRootHandle#invalidateAndReconcile()}。
     */
    public void resize(ViewportContext next) {
        requireOwner("Ui.resize");
        if (next == null || next.equals(viewportContext)) {
            return;
        }
        // 声明固定视口的 host 不接受逻辑视口变化：显式失败，不静默吞掉一次永不生效的 resize。
        if (viewportContext.has(ViewportContext.Capability.FIXED_VIEWPORT)
                && (next.logicalWidth() != viewportContext.logicalWidth()
                        || next.logicalHeight() != viewportContext.logicalHeight())) {
            throw new UiFailure(UiDiagnosticStage.LAYOUT, UiFailure.INVALID_LAYOUT,
                    "host declares " + ViewportContext.Capability.FIXED_VIEWPORT.code()
                            + "; the logical viewport is fixed at "
                            + (long) viewportContext.logicalWidth() + "x"
                            + (long) viewportContext.logicalHeight()
                            + " and cannot be resized to "
                            + (long) next.logicalWidth() + "x" + (long) next.logicalHeight(), null);
        }
        viewportContext = next;
        for (UiRootHandle root : new ArrayList<>(roots.keySet())) {
            if (root instanceof UiRoot concrete && concrete.isActive()) {
                // 只让布局失效：脚本 render 未读过视口时走纯重排路径（票 43 AC5）。
                concrete.invalidateLayout();
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
        // 首次提交成功后才投影原生 Screen：候选失败不得打开半成品 Screen
        // （jsx-ui-spec「提交语义」「暂停与替换」）。
        if (handle.status() == com.tkisor.nekojs.api.ui.UiRootStatus.OPEN) {
            screenHost.open(handle);
        }
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

    /** root 释放路径上的 Screen 投影关闭；幂等由 {@link com.tkisor.nekojs.api.ui.UiScreenHost} 保证。 */
    void closeScreen(UiRoot root) {
        screenHost.close(root);
    }

    String nextRootId() {
        return "ui-root-" + (++rootSequence);
    }

    private DefaultUiStateOwner newOwner() {
        DefaultUiStateOwner owner = new DefaultUiStateOwner(ownerThread());
        owners.add(owner);
        return owner;
    }

    private void requireOwner(String operation) {
        Thread owner = ownerThread();
        if (Thread.currentThread() != owner) {
            throw new UiFailure(UiDiagnosticStage.HOST_UPDATE, UiFailure.OFF_OWNER_THREAD,
                    operation + " must run on the UI owner thread '" + owner.getName()
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