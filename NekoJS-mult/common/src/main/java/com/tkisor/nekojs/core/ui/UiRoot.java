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
import com.tkisor.nekojs.api.ui.ProjectedViewport;
import com.tkisor.nekojs.api.ui.UiLayoutSpec;
import com.tkisor.nekojs.api.ui.UiViewportState;
import com.tkisor.nekojs.api.ui.ViewportContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
public final class UiRoot implements UiRootHandle, UiViewportState {

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
    private final Thread ownerThread;

    private UiHostNode committedRoot;
    private UiVNode committedTree;
    private UiHostInspection inspection = UiHostInspection.empty();
    private UiRootStatus status = UiRootStatus.CREATED;
    private String focusedPath;
    private boolean reconcilePending;
    private UiLayoutEngine.LayoutFacts layoutFacts = UiLayoutEngine.LayoutFacts.empty();
    /**
     * 各次成功 measure/arrange 的解析样式与溢出累积表（path → 值）。
     *
     * <p>纯重排路径会替换整份 {@link UiLayoutEngine.LayoutFacts}，但节点的 props 没有变，
     * 因此累积表与最新一次解析结果一致，且不会因为某个节点被覆盖隐藏而丢失观察面。
     */
    private final Map<String, com.tkisor.nekojs.api.ui.UiLayoutSpec> relayoutFacts = new LinkedHashMap<>();
    private final Map<String, com.tkisor.nekojs.api.ui.UiOverflow> relayoutOverflow = new LinkedHashMap<>();
    private ProjectedViewport projectedViewport;
    private long layoutRevisions;
    /**
     * 是否正处于<b>脚本回调</b>执行期（render 或事件分发）。
     *
     * <p>只有脚本在回调里读视口（{@link #viewport()}）才构成「该 root 的布局依赖视口」；
     * Java 侧与 inspector 的读取（{@link #projectedViewport()} / {@link #viewportState()}）
     * 不会污染这个判断。
     */
    private boolean scriptPhase;
    /** 脚本是否读过视口事实（sticky）：resize 时必须重跑脚本 render 才能正确。 */
    private boolean viewportDependent;

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
        this.ownerThread = this.options.ownerThread() == null
                ? facade.ownerThread() : this.options.ownerThread();
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
        scriptPhase = true;
        try {
            facade.dispatchToBindings(target, resolved);
        } catch (UiFailure failure) {
            record(UiDiagnostics.fromFailure(failure, path, rootId, generation));
        } catch (RuntimeException failure) {
            record(UiDiagnostics.fromThrowable(UiDiagnosticStage.EVENT, "EVENT_FAILURE",
                    failure, path, rootId, generation));
        } finally {
            scriptPhase = false;
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
        paintNode(committedRoot, rootClip(committedRoot), operations, clips, true);
        inspection = new UiHostInspection(committedRoot, operations, clips,
                layoutFacts.resolvedStyles(), layoutFacts.overflow(), projectedViewport, layoutRevisions);
        return inspection;
    }

    /**
     * 遍历已提交树：{@code paint} 为真时调用 host Adapter 绘制并把失败节点记入诊断；
     * 始终产出可观察的绘制顺序与裁剪矩形（裁剪是逐层相交的结果）。
     */
    private void paintNode(UiHostNode node, UiRect clip, List<String> operations,
                           Map<String, UiRect> clips, boolean paint) {
        if (node.disposed()) return;
        UiRect viewport = clip.intersect(node.layout());
        if (paint) {
            try {
                adapter.paint(node, viewport);
            } catch (RuntimeException failure) {
                record(UiDiagnostics.fromThrowable(UiDiagnosticStage.HOST_UPDATE, "PAINT_FAILURE",
                        failure, node.path(), rootId, generation));
                return;
            }
        }
        operations.add(node.path());
        clips.put(node.path(), viewport);
        for (UiHostNode child : node.children()) {
            paintNode(child, viewport, operations, clips, paint);
        }
    }

    /** 只重算裁剪矩形（resize 重排路径），不改动绘制顺序。 */
    private Map<String, UiRect> clipRectsOf(UiHostNode root) {
        Map<String, UiRect> clips = new LinkedHashMap<>();
        paintNode(root, rootClip(root), new ArrayList<>(), clips, false);
        return clips;
    }

    private static UiRect rootClip(UiHostNode root) {
        return new UiRect(0, 0, Math.max(0, root.layout().width()), Math.max(0, root.layout().height()));
    }

    @Override
    public void dispose() {
        if (status == UiRootStatus.DISPOSED) {
            return;
        }
        status = UiRootStatus.DISPOSED;
        reconcilePending = false;
        stateOwner.unregister(this);
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

    /**
     * 响应式视口的可观察事实（票 43 AC1/AC5）。
     *
     * <p>{@link #viewport()} 是脚本可见的 <b>script getter</b>：render 期间读取会被记录为
     * 「该 root 的布局依赖视口」，resize 时因此退回完整 reconcile（正确性优先）。
     * {@link #viewportState()} 是 Java 侧与 inspector 的只读查询，不产生该副作用。
     */
    @Override
    public ProjectedViewport viewport() {
        if (scriptPhase) {
            viewportDependent = true;
        }
        return projectedViewport();
    }

    @Override
    public UiViewportState viewportState() {
        return this;
    }

    @Override
    public java.util.Map<String, UiLayoutSpec> resolvedStyles() {
        return layoutFacts.resolvedStyles();
    }

    /** Java 侧/inspector 的只读视口事实：不记录视口依赖。 */
    public ProjectedViewport projectedViewport() {
        return projectedViewport != null
                ? projectedViewport
                : ProjectedViewport.of(currentViewportContext(), layoutRevisions);
    }

    /**
     * 视口/profile 变化后的布局失效路径（票 43 AC5）。
     *
     * <p>脚本 render <b>没有</b>读取过视口事实时：只对已提交树重新 measure/arrange，
     * 不执行脚本 render，也不经过 reconciler，因此不创建/更新/重排任何 host node。
     *
     * <p>脚本 render 读过视口事实时：结构可能随视口改变，退回
     * {@link #invalidateAndReconcile()}（正确性优先——不能因为"只重排"而让脚本看不到新视口）。
     */
    void invalidateLayout() {
        if (status == UiRootStatus.DISPOSED || status == UiRootStatus.INVALIDATED) {
            return;
        }
        if (viewportDependent || committedRoot == null) {
            // 脚本读过视口（或还没有可重排的树）：结构可能随视口改变，走完整 reconcile。
            // 这条路径同时保证 per-root 覆盖（安全区域 / host 布局能力）被重新解析。
            invalidateAndReconcile();
            return;
        }
        // 纯重排：不跑脚本 render，也不经过 reconciler。per-root 视口事实每次都重新解析，
        // 因此同 facade 下 A/B 两个 root 各自的 options 覆盖都会生效（不会停在旧档位）。
        relayout(committedRoot, currentViewportContext());
        relayoutFacts.putAll(layoutFacts.resolvedStyles());
        relayoutOverflow.putAll(layoutFacts.overflow());
        inspection = inspection.afterRelayout(projectedViewport, layoutRevisions,
                clipRectsOf(committedRoot), unchangedFacts().resolvedStyles(),
                unchangedFacts().overflow());
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
        scriptPhase = true;
        try {
            candidateTree = screen.render();
        } catch (UiFailure failure) {
            record(UiDiagnostics.fromFailure(failure, null, rootId, generation));
            return;
        } catch (RuntimeException failure) {
            record(UiDiagnostics.fromThrowable(UiDiagnosticStage.RENDER, "RENDER_FAILURE",
                    failure, null, rootId, generation));
            return;
        } finally {
            scriptPhase = false;
            stateOwner.endTracking(this);
        }
        if (candidateTree == null) {
            record(UiDiagnostics.of(UiDiagnosticStage.RENDER, UiFailure.INVALID_ROOT,
                    "render function must return a VNode or fragment, got null",
                    null, rootId, generation, null));
            return;
        }

        ViewportContext context = currentViewportContext();
        UiReconciler.Candidate candidate;
        try {
            candidate = reconciler.expand(candidateTree, context);
        } catch (UiFailure failure) {
            record(UiDiagnostics.fromFailure(failure, null, rootId, generation));
            return;
        } catch (RuntimeException failure) {
            record(UiDiagnostics.fromThrowable(UiDiagnosticStage.RENDER, "RENDER_FAILURE",
                    failure, null, rootId, generation));
            return;
        }

        try {
            UiHostNode next = reconciler.commit(committedRoot, candidate);
            commitLayout(next, context);
            committedRoot = next;
            committedTree = candidateTree;
            relayoutFacts.clear();
            relayoutFacts.putAll(layoutFacts.resolvedStyles());
            relayoutOverflow.clear();
            relayoutOverflow.putAll(layoutFacts.overflow());
            inspection = inspection.withCommittedTree(next, layoutFacts.resolvedStyles(),
                    layoutFacts.overflow(), projectedViewport, layoutRevisions);
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

    /**
     * 当前响应式视口事实（票 43 AC1）：逻辑视口来自装配点（resize 更新），安全区域与 host
     * 布局能力可由 Screen 选项在 root 级进一步声明；原生 scale 只作为记录事实，不参与判档。
     */
    private ViewportContext currentViewportContext() {
        ViewportContext base = facade.viewportContext();
        ViewportContext.SafeArea declared = options.safeArea();
        boolean overridesSafeArea = declared.left() > 0 || declared.top() > 0
                || declared.right() > 0 || declared.bottom() > 0;
        if (!overridesSafeArea) {
            return base;
        }
        return new ViewportContext(base.logicalWidth(), base.logicalHeight(),
                declared, base.guiScale(), base.capabilities());
    }

    /**
     * 提交阶段：先完成布局（候选可能在布局阶段失败），再挂载到已提交树。
     *
     * <p>宿主树的节点创建/更新无法回滚，因此当布局或后续步骤失败时，root 会显式把
     * 候选树重新提交回最后一次有效树的 props，并在诊断中标记为「保留最后有效树」。
     */
    private void commitLayout(UiHostNode node, ViewportContext context) {
        try {
            relayout(node, context);
        } catch (UiFailure failure) {
            restoreCommittedTree();
            throw failure;
        } catch (RuntimeException failure) {
            restoreCommittedTree();
            throw new UiFailure(UiDiagnosticStage.LAYOUT, UiFailure.INVALID_LAYOUT,
                    "layout failed for " + node.path() + ": " + failure.getMessage(), node.path(), failure);
        }
    }

    /** 累积解析事实（纯重排路径用；节点 props 未变时与最新解析结果一致）。 */
    private UiLayoutEngine.LayoutFacts unchangedFacts() {
        return new UiLayoutEngine.LayoutFacts(Map.copyOf(relayoutFacts), Map.copyOf(relayoutOverflow));
    }

    /** 只 measure/arrange 并推进布局代数：不执行脚本 render，也不经过 reconciler（AC5）。 */
    private void relayout(UiHostNode node, ViewportContext context) {
        UiLayoutEngine.LayoutFacts facts = layoutEngine.layout(node, context);
        layoutFacts = facts;
        layoutRevisions++;
        projectedViewport = ProjectedViewport.of(context, layoutRevisions);
    }

    /** 把已提交树的 props 重新推回 host Adapter（候选失败时的恢复路径）。 */
    private void restoreCommittedTree() {
        if (committedRoot == null || committedTree == null) {
            return;
        }
        restoreNode(committedRoot, committedTree);
        relayout(committedRoot, currentViewportContext());
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

    private void requireOwner(String operation) {
        if (Thread.currentThread() != ownerThread) {
            UiFailure failure = new UiFailure(UiDiagnosticStage.HOST_UPDATE, UiFailure.OFF_OWNER_THREAD,
                    operation + " must run on the UI owner thread '" + ownerThread.getName()
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