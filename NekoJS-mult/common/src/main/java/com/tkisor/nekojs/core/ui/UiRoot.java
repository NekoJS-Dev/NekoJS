package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiDiagnostic;
import com.tkisor.nekojs.api.ui.UiDiagnosticSink;
import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiEvent;
import com.tkisor.nekojs.api.ui.UiEventType;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiHostAdapter;
import com.tkisor.nekojs.api.ui.UiHostInspection;
import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProp;
import com.tkisor.nekojs.api.ui.UiRect;
import com.tkisor.nekojs.api.ui.UiRootHandle;
import com.tkisor.nekojs.api.ui.UiRootStatus;
import com.tkisor.nekojs.api.ui.UiScreen;
import com.tkisor.nekojs.api.ui.UiScreenOptions;
import com.tkisor.nekojs.api.ui.UiSize;
import com.tkisor.nekojs.api.ui.UiVNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@link UiRootHandle} 默认实现：root 的依赖订阅、提交队列、generation token、
 * host tree 和 disposed 状态（jsx-ui-spec「root 输入」「generation 生命周期」）。
 *
 * <p>失败语义：render / 候选择失败不触碰已提交树，保留最后一次有效树并把诊断归入对应
 * 阶段；host Adapter 失败归入 HOST_UPDATE。事件回调失败只影响该次分发，其它控件与
 * root 仍可继续分发（AC5）。
 *
 * <p>cleanup 幂等：{@link #dispose()} 重复调用是 no-op；root 释放后所有写操作显式失败
 * （{@link UiFailure#ROOT_DISPOSED}）。
 */
public final class UiRoot implements UiRootHandle {

    private final RootUiFacade facade;
    private final UiHostAdapter adapter;
    private final UiDiagnosticSink diagnostics;
    private final UiScreen screen;
    private final UiScreenOptions options;
    private final DefaultUiStateOwner stateOwner;
    private final UiReconciler reconciler;
    private final UiLayoutEngine layoutEngine;
    private final String rootId;
    private final int generation;
    /** 显式 owner thread 覆盖（{@link UiScreenOptions#ownerThread()}）；null 表示跟随 facade。 */
    private final Thread ownerThreadOverride;

    private UiHostNode committedRoot;
    private UiVNode committedTree;
    private UiHostInspection inspection = UiHostInspection.empty();
    private UiRootStatus status = UiRootStatus.CREATED;
    private String focusedPath;
    private boolean reconcilePending;

    UiRoot(RootUiFacade facade, UiScreen screen, UiScreenOptions options,
           DefaultUiStateOwner stateOwner, String rootId) {
        this.facade = Objects.requireNonNull(facade, "facade");
        this.screen = Objects.requireNonNull(screen, "screen");
        this.options = options == null ? UiScreenOptions.defaults() : options;
        this.stateOwner = Objects.requireNonNull(stateOwner, "stateOwner");
        this.adapter = facade.resources().adapter();
        this.diagnostics = facade.resources().diagnostics();
        this.rootId = Objects.requireNonNull(rootId, "rootId");
        this.generation = facade.generation();
        this.ownerThreadOverride = this.options.ownerThread();
        this.reconciler = new UiReconciler(adapter, facade.resources().components(), 64);
        this.layoutEngine = new UiLayoutEngine(adapter, facade.resources().textMeasurer());
    }

    @Override
    public String rootId() {
        return rootId;
    }

    @Override
    public int generation() {
        return generation;
    }

    @Override
    public UiRootStatus status() {
        return status;
    }

    @Override
    public boolean isActive() {
        return status == UiRootStatus.CREATED || status == UiRootStatus.OPEN
                || status == UiRootStatus.INVALIDATED;
    }

    @Override
    public UiScreenOptions options() {
        return options;
    }

    @Override
    public void open() {
        requireOwner("open");
        requireUsable("open");
        renderAndCommit();
    }

    @Override
    public void invalidate() {
        requireOwner("invalidate");
        requireUsable("invalidate");
        status = UiRootStatus.INVALIDATED;
        reconcilePending = false;
    }

    @Override
    public void invalidateAndReconcile() {
        requireOwner("invalidateAndReconcile");
        if (status == UiRootStatus.DISPOSED) {
            throw disposed("invalidateAndReconcile");
        }
        if (status == UiRootStatus.INVALIDATED) {
            return;
        }
        reconcilePending = true;
        renderAndCommit();
        reconcilePending = false;
    }

    @Override
    public void dispatchEvent(UiEvent event) {
        Objects.requireNonNull(event, "event");
        requireOwner("dispatchEvent");
        requireEventAccepting("dispatchEvent");
        UiHostNode target = hitTest(event);
        String path = target == null ? null : target.path();
        UiEvent resolved = event.withTarget(path, rootId, generation);
        UiEventType type = resolved.type();
        if (type == UiEventType.FOCUS || type == UiEventType.BLUR) {
            focusedPath = type == UiEventType.FOCUS ? path : null;
        }
        if (type == UiEventType.CLOSE && options.closeOnEscape()) {
            facade.onRootCloseRequested(this);
        }
        if (target == null) {
            return;
        }
        try {
            facade.dispatchToBindings(target, resolved);
        } catch (UiFailure failure) {
            record(UiDiagnostics.fromFailure(failure, path, rootId, generation));
        } catch (RuntimeException failure) {
            record(UiDiagnostics.fromThrowable(UiDiagnosticStage.EVENT, "EVENT_FAILURE",
                    failure, path, rootId, generation));
        }
    }

    /**
     * 平台 Screen 的 resize 回调入口（Java 侧，不在脚本契约里）。
     *
     * <p>只让本 root 的布局失效并重算（jsx-ui-spec「布局」：resize 使布局失效，
     * 但不在每个绘制帧执行脚本）。{@code viewport} 由平台传入，避免 facade 级
     * resize 影响其它 root。
     */
    public void resizeViewport(UiSize viewport) {
        requireOwner("Ui.resize");
        if (viewport == null || status == UiRootStatus.DISPOSED) {
            return;
        }
        facade.setViewport(viewport);
        if (status == UiRootStatus.OPEN) {
            invalidateAndReconcile();
        } else {
            facade.resize(viewport);
        }
    }

    @Override
    public UiHostInspection paint() {
        requireOwner("paint");
        if (status == UiRootStatus.DISPOSED) {
            throw disposed("paint");
        }
        if (committedRoot == null) {
            return UiHostInspection.empty();
        }
        List<String> operations = new ArrayList<>();
        Map<String, UiRect> clips = new LinkedHashMap<>();
        paintNode(committedRoot, new UiRect(0, 0,
                Math.max(0, committedRoot.layout().width()),
                Math.max(0, committedRoot.layout().height())), operations, clips);
        inspection = new UiHostInspection(committedRoot, operations, clips);
        return inspection;
    }

    private void paintNode(UiHostNode node, UiRect clip, List<String> operations, Map<String, UiRect> clips) {
        if (node.disposed()) return;
        UiRect viewport = clip.intersect(node.layout());
        try {
            adapter.paint(node, viewport);
        } catch (RuntimeException failure) {
            record(UiDiagnostics.fromThrowable(UiDiagnosticStage.HOST_UPDATE, "PAINT_FAILURE",
                    failure, node.path(), rootId, generation));
            return;
        }
        operations.add(node.path());
        clips.put(node.path(), viewport);
        for (UiHostNode child : node.children()) {
            paintNode(child, viewport, operations, clips);
        }
    }

    @Override
    public void dispose() {
        if (status == UiRootStatus.DISPOSED) {
            return;
        }
        status = UiRootStatus.DISPOSED;
        reconcilePending = false;
        stateOwner.unregister(this);
        // 原生 Screen 与 host node 走同一释放路径（jsx-ui-spec「暂停与替换」）：先关闭
        // Screen 投影，再释放 host node；重复 dispose 是 no-op，因此 cleanup 幂等。
        try {
            facade.closeScreen(this);
        } catch (RuntimeException failure) {
            record(UiDiagnostics.fromThrowable(UiDiagnosticStage.HOST_UPDATE, "SCREEN_CLOSE_FAILURE",
                    failure, null, rootId, generation));
        }
        if (committedRoot != null) {
            reconciler.disposeSubtree(committedRoot);
            committedRoot = null;
        }
        committedTree = null;
        focusedPath = null;
        inspection = UiHostInspection.empty();
        facade.onRootDisposed(this);
    }

    @Override
    public UiHostInspection inspection() {
        return inspection;
    }

    @Override
    public UiVNode committedTree() {
        return committedTree;
    }

    @Override
    public String focusedPath() {
        return focusedPath;
    }

    // ---- 内部 ----

    /** owner tick 请求一次 reconcile（失效合并后的落点）。 */
    void requestReconcile() {
        if (status == UiRootStatus.DISPOSED || status == UiRootStatus.INVALIDATED) {
            return;
        }
        reconcilePending = true;
        renderAndCommit();
        reconcilePending = false;
    }

    private void renderAndCommit() {
        try {
            stateOwner.beginTracking(this);
        } catch (RuntimeException failure) {
            // 跟踪已在进行：并发 render 属于调用方错误；记录后放弃本次提交。
            record(UiDiagnostics.fromThrowable(UiDiagnosticStage.RENDER, "RENDER_REENTRY",
                    failure, null, rootId, generation));
            return;
        }
        UiVNode candidateTree;
        try {
            candidateTree = screen.render();
        } catch (UiFailure failure) {
            stateOwner.endTracking(this);
            record(UiDiagnostics.fromFailure(failure, null, rootId, generation));
            return;
        } catch (RuntimeException failure) {
            stateOwner.endTracking(this);
            record(UiDiagnostics.fromThrowable(UiDiagnosticStage.RENDER, "RENDER_FAILURE",
                    failure, null, rootId, generation));
            return;
        }
        if (candidateTree == null) {
            stateOwner.endTracking(this);
            record(UiDiagnostics.of(UiDiagnosticStage.RENDER, UiFailure.INVALID_ROOT,
                    "render function must return a VNode or fragment, got null",
                    null, rootId, generation, null));
            return;
        }

        UiSize available = availableViewport();
        UiReconciler.Candidate candidate;
        try {
            candidate = reconciler.expand(candidateTree);
        } catch (UiFailure failure) {
            stateOwner.endTracking(this);
            record(UiDiagnostics.fromFailure(failure, null, rootId, generation));
            return;
        } catch (RuntimeException failure) {
            stateOwner.endTracking(this);
            record(UiDiagnostics.fromThrowable(UiDiagnosticStage.RENDER, "RENDER_FAILURE",
                    failure, null, rootId, generation));
            return;
        }
        stateOwner.endTracking(this);

        try {
            UiHostNode next = reconciler.commit(committedRoot, candidate);
            commitLayout(next, available);
            committedRoot = next;
            committedTree = candidateTree;
            inspection = new UiHostInspection(next, inspection.paintOperations(), inspection.clipRects());
            if (status == UiRootStatus.CREATED) {
                status = UiRootStatus.OPEN;
            }
        } catch (UiFailure failure) {
            record(UiDiagnostics.fromFailure(failure, null, rootId, generation));
        } catch (RuntimeException failure) {
            record(UiDiagnostics.fromThrowable(UiDiagnosticStage.HOST_UPDATE, UiFailure.ADAPTER_FAILURE,
                    failure, null, rootId, generation));
        }
    }

    /** 当前逻辑视口（resize 由装配点更新，布局在下一次 reconcile 时失效重算）。 */
    private UiSize availableViewport() {
        return facade.viewport();
    }

    /**
     * 提交阶段：先完成布局（候选可能在布局阶段失败），再挂载到已提交树。
     *
     * <p>宿主树的节点创建/更新无法回滚，因此当布局或后续步骤失败时，root 会显式把
     * 候选树重新提交回最后一次有效树的 props，并在诊断中标记为「保留最后有效树」。
     */
    private void commitLayout(UiHostNode node, UiSize available) {
        try {
            layoutEngine.layout(node, available);
        } catch (UiFailure failure) {
            restoreCommittedTree();
            throw failure;
        } catch (RuntimeException failure) {
            restoreCommittedTree();
            throw new UiFailure(UiDiagnosticStage.LAYOUT, UiFailure.INVALID_LAYOUT,
                    "layout failed for " + node.path() + ": " + failure.getMessage(), node.path(), failure);
        }
    }

    /** 把已提交树的 props 重新推回 host Adapter（候选失败时的恢复路径）。 */
    private void restoreCommittedTree() {
        if (committedRoot == null || committedTree == null) {
            return;
        }
        restoreNode(committedRoot, committedTree);
        layoutEngine.layout(committedRoot, availableViewport());
    }

    /**
     * 尽力恢复：把已提交节点自身与可对应的子节点的 props 推回 host Adapter，并重排布局。
     *
     * <p>函数组件产生的子树在 VNode 层面没有一一对应的还原路径，因此只还原同位置的
     * host 子节点；不承诺逐字段回滚（governance：这不是事务，只是「最后一次有效树
     * 仍在显示」的保证）。
     */
    private void restoreNode(UiHostNode node, com.tkisor.nekojs.api.ui.UiVNode tree) {
        if (tree instanceof com.tkisor.nekojs.api.ui.HostVNode host && !node.props().equals(host.props())) {
            adapter.update(node, host.props());
        }
        List<UiHostNode> children = node.children();
        List<com.tkisor.nekojs.api.ui.UiVNode> expected = new ArrayList<>();
        flattenChildren(tree, expected);
        for (int i = 0; i < children.size() && i < expected.size(); i++) {
            restoreNode(children.get(i), expected.get(i));
        }
    }

    private static void flattenChildren(com.tkisor.nekojs.api.ui.UiVNode tree,
                                        List<com.tkisor.nekojs.api.ui.UiVNode> out) {
        if (tree instanceof com.tkisor.nekojs.api.ui.FragmentVNode fragment) {
            for (com.tkisor.nekojs.api.ui.UiVNode child : fragment.children()) {
                flattenInto(child, out);
            }
        } else if (tree instanceof com.tkisor.nekojs.api.ui.HostVNode host) {
            for (com.tkisor.nekojs.api.ui.UiVNode child : host.children()) {
                flattenInto(child, out);
            }
        }
    }

    private static void flattenInto(com.tkisor.nekojs.api.ui.UiVNode node,
                                    List<com.tkisor.nekojs.api.ui.UiVNode> out) {
        if (node == null) {
            return;
        }
        if (node instanceof com.tkisor.nekojs.api.ui.FragmentVNode fragment) {
            for (com.tkisor.nekojs.api.ui.UiVNode child : fragment.children()) {
                flattenInto(child, out);
            }
            return;
        }
        out.add(node);
    }

    private UiHostNode hitTest(UiEvent event) {
        if (committedRoot == null) return null;
        if (!event.hasPointer()) {
            return focusedNode();
        }
        return hitNode(committedRoot, event.mouseX(), event.mouseY());
    }

    private UiHostNode hitNode(UiHostNode node, double x, double y) {
        if (node.disposed()) return null;
        if (!node.layout().contains(x, y)) return null;
        List<UiHostNode> children = node.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            UiHostNode hit = hitNode(children.get(i), x, y);
            if (hit != null) return hit;
        }
        return node;
    }

    private UiHostNode focusedNode() {
        if (focusedPath == null || committedRoot == null) return null;
        return findByPath(committedRoot, focusedPath);
    }

    private UiHostNode findByPath(UiHostNode node, String path) {
        if (path.equals(node.path())) return node;
        for (UiHostNode child : node.children()) {
            UiHostNode found = findByPath(child, path);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * root 的 client owner thread：显式选项优先，否则每次向 facade 求值。
     *
     * <p>与 {@code RootUiFacade} 同一理由：客户端 owner thread 只在客户端就绪后才存在，
     * 不能在装配期快照（jsx-ui-spec「状态线程」）。
     */
    private Thread ownerThread() {
        return ownerThreadOverride != null ? ownerThreadOverride : facade.ownerThread();
    }

    private void requireOwner(String operation) {
        Thread owner = ownerThread();
        if (Thread.currentThread() != owner) {
            UiFailure failure = new UiFailure(UiDiagnosticStage.HOST_UPDATE, UiFailure.OFF_OWNER_THREAD,
                    operation + " must run on the UI owner thread '" + owner.getName()
                            + "', was '" + Thread.currentThread().getName() + "'", null);
            record(UiDiagnostics.fromFailure(failure, null, rootId, generation));
            throw failure;
        }
    }

    private void requireUsable(String operation) {
        if (status == UiRootStatus.DISPOSED) {
            throw disposed(operation);
        }
    }

    /**
     * 只有 CREATED / OPEN 的 root 接受新事件。
     *
     * <p>{@link UiRootStatus#INVALIDATED} 的 root 已停止接收新事件（javadoc 契约与
     * jsx-ui-spec「generation 生命周期」：generation 失效时停止接受新事件），
     * 但仍保留最后一次有效树供显示；重复失效是幂等的。
     */
    private void requireEventAccepting(String operation) {
        requireUsable(operation);
        if (status == UiRootStatus.INVALIDATED) {
            throw new UiFailure(UiDiagnosticStage.HOST_UPDATE, UiFailure.ROOT_INVALIDATED,
                    "UI root " + rootId + " is invalidated; " + operation + " is not allowed", null);
        }
    }

    private UiFailure disposed(String operation) {
        return new UiFailure(UiDiagnosticStage.HOST_UPDATE, UiFailure.ROOT_DISPOSED,
                "UI root " + rootId + " is disposed; " + operation + " is not allowed", null);
    }

    private void record(UiDiagnostic diagnostic) {
        UiDiagnostics.record(diagnostics, diagnostic);
    }
}