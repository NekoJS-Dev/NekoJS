package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.ComponentVNode;
import com.tkisor.nekojs.api.ui.FragmentVNode;
import com.tkisor.nekojs.api.ui.HostVNode;
import com.tkisor.nekojs.api.ui.UiComponentRegistry;
import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiEventType;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiHostAdapter;
import com.tkisor.nekojs.api.ui.UiHostCreate;
import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProp;
import com.tkisor.nekojs.api.ui.UiProps;
import com.tkisor.nekojs.api.ui.UiVNode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * retained tree reconciler（jsx-ui-spec「reconciler」「组件模型」「提交语义」）。
 *
 * <p>提交分两个阶段，保证「提交前 active host tree 不变」：
 * <ol>
 *   <li><b>候选择</b>（{@link #expand}）：展开函数组件与 Fragment、展平数组与空值，
 *       校验结构（根必须是 screen）、属性（登记表）、事件值形状、布局取值、
 *       受控资源标识、重复 key 与 host capability。此阶段不触碰 host tree。</li>
 *   <li><b>提交</b>（{@link #commit}）：按 type + key 做 keyed diff——复用、创建、
 *       更新 props、替换事件绑定、重排子节点、释放消失的子树。</li>
 * </ol>
 *
 * <p>候选择阶段抛出 {@link UiFailure} 时 host tree 完全未被触碰；提交阶段 Adapter 抛错
 * 时以 {@link UiDiagnosticStage#HOST_UPDATE} 上报，root 保留最后一次有效树引用，
 * 不采纳半成品候选。cleanup 幂等：已释放节点再次 {@code dispose} 是 no-op。
 */
final class UiReconciler {

    /** 展开后的候选 host 节点（尚未创建 host node）。 */
    static final class Candidate {
        final UiPrimitive primitive;
        final String key;
        final UiProps props;
        final String path;
        final List<Candidate> children = new ArrayList<>();
        int order;
        UiHostNode committed;

        Candidate(UiPrimitive primitive, String key, UiProps props, String path) {
            this.primitive = primitive;
            this.key = key;
            this.props = props == null ? UiProps.empty() : props;
            this.path = path;
        }

        /** 同一父节点下的匹配身份：keyed 用 key，keyless 用出现序号。 */
        String identity() {
            return primitive.tag() + "#" + (key == null ? "@" + order : key);
        }
    }

    private final UiHostAdapter adapter;
    private final UiComponentRegistry components;
    private final int maxComponentDepth;

    UiReconciler(UiHostAdapter adapter, UiComponentRegistry components, int maxComponentDepth) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.components = components == null ? UiComponentRegistry.EMPTY : components;
        this.maxComponentDepth = maxComponentDepth <= 0 ? 64 : maxComponentDepth;
    }

    UiHostAdapter adapter() {
        return adapter;
    }

    /** 候选择：展开候选 VNode 为候选 host 树（纯函数，不触碰 host）。 */
    Candidate expand(UiVNode root) {
        Map<String, Integer> keylessCounters = new HashMap<>();
        Set<String> seenKeys = new HashSet<>();
        List<Candidate> roots = new ArrayList<>();
        collect(java.util.Collections.singletonList(root), null, roots, keylessCounters, seenKeys, 0);
        if (roots.size() != 1) {
            throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.INVALID_ROOT,
                    "render function must produce exactly one root <screen>, got " + roots.size(), "screen");
        }
        Candidate screen = roots.getFirst();
        if (screen.primitive != UiPrimitive.SCREEN) {
            throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.INVALID_ROOT,
                    "candidate tree root must be <screen>, got <" + screen.primitive.tag() + ">",
                    screen.path);
        }
        return screen;
    }

    /** 提交：把候选树与已提交树做 keyed diff；{@code committed} 为 null 时创建全新树。 */
    UiHostNode commit(UiHostNode committed, Candidate candidate) {
        if (committed == null) {
            createSubtree(candidate);
            return candidate.committed;
        }
        candidate.committed = committed;
        applyNode(committed, candidate);
        return committed;
    }

    // ---- 候选择 ----

    private void collect(List<UiVNode> nodes, String parentPath, List<Candidate> out,
                         Map<String, Integer> keylessCounters, Set<String> seenKeys, int depth) {
        if (depth > maxComponentDepth) {
            throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.INVALID_CHILD,
                    "component/fragment nesting exceeds " + maxComponentDepth + " levels", parentPath);
        }
        if (nodes == null) {
            return;
        }
        for (UiVNode node : nodes) {
            // 条件渲染的 null/undefined 分支不产生节点（契约：空值安全丢弃），
            // 其它非法值仍由 collectOne 显式失败。
            if (node == null) {
                continue;
            }
            collectOne(node, parentPath, out, keylessCounters, seenKeys, depth);
        }
    }

    private void collectOne(UiVNode node, String parentPath, List<Candidate> out,
                            Map<String, Integer> keylessCounters, Set<String> seenKeys, int depth) {
        if (node == null) {
            // 条件渲染的 false/null 分支：按契约产生「无节点」，不报错。
            return;
        }
        if (node instanceof HostVNode host) {
            UiPrimitive primitive = host.primitive();
            int slot = keylessCounters.getOrDefault(primitive.tag(), 0);
            if (host.key() == null) {
                keylessCounters.put(primitive.tag(), slot + 1);
            } else if (!seenKeys.add(primitive.tag() + "#" + host.key())) {
                throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.DUPLICATE_KEY,
                        "duplicate key '" + host.key() + "' for <" + primitive.tag() + "> children of "
                                + (parentPath == null ? "<root>" : parentPath), parentPath);
            }
            String path = UiNodePaths.childPath(parentPath, primitive, slot, host.key());
            if (!adapter.primitives().contains(primitive)) {
                throw new UiFailure(UiDiagnosticStage.HOST_UPDATE, UiFailure.UNSUPPORTED_CAPABILITY,
                        "host adapter '" + adapter.adapterId() + "' does not support <"
                                + primitive.tag() + ">", path);
            }
            UiPrimitiveRegistry.validateProps(primitive, host.props(), path);
            UiPrimitiveRegistry.validateLayoutValues(host.props(), path);
            UiPrimitiveRegistry.validateLayoutSpec(host.props(), path);
            if (primitive == UiPrimitive.IMAGE) {
                UiPrimitiveRegistry.validateResourceId(host.props().string(UiProp.SOURCE.jsName()), path);
            }
            Candidate candidate = new Candidate(primitive, host.key(), host.props(), path);
            collect(host.children(), path, candidate.children, new HashMap<>(), new HashSet<>(), depth + 1);
            out.add(candidate);
            return;
        }
        if (node instanceof FragmentVNode fragment) {
            // Fragment 只做子节点展开，自身不产生 host node，也不占用 key 槽位。
            if (fragment.key() != null && !seenKeys.add("Fragment#" + fragment.key())) {
                throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.DUPLICATE_KEY,
                        "duplicate key '" + fragment.key() + "' for Fragment children of "
                                + (parentPath == null ? "<root>" : parentPath), parentPath);
            }
            collect(fragment.children(), parentPath, out, keylessCounters, seenKeys, depth + 1);
            return;
        }
        if (node instanceof ComponentVNode component) {
            String path = parentPath + "/" + component.component()
                    + (component.key() == null ? "" : "#" + component.key());
            UiComponentRegistry.UiRenderer renderer = components.resolve(component.component());
            if (renderer == null) {
                throw new UiFailure(UiDiagnosticStage.COMPONENT, UiFailure.UNRESOLVED_COMPONENT,
                        "unknown component <" + component.component() + ">", path);
            }
            UiVNode produced;
            try {
                produced = renderer.render(component.props(), component.children());
            } catch (UiFailure failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw new UiFailure(UiDiagnosticStage.COMPONENT, UiFailure.INVALID_COMPONENT_RESULT,
                        "component <" + component.component() + "> failed: " + failure.getMessage(),
                        path, failure);
            }
            if (produced == null) {
                return;
            }
            collectOne(produced, parentPath, out, keylessCounters, seenKeys, depth + 1);
            return;
        }
        throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.INVALID_CHILD,
                "unsupported VNode kind: " + node.getClass().getName(), parentPath);
    }

    // ---- 提交 ----

    private void createSubtree(Candidate candidate) {
        UiHostCreate request = new UiHostCreate(candidate.primitive, candidate.key,
                candidate.props, candidate.path);
        UiHostNode node = adapter.create(request);
        candidate.committed = node;
        for (Candidate child : candidate.children) {
            createSubtree(child);
        }
        List<UiHostNode> childNodes = new ArrayList<>();
        for (Candidate child : candidate.children) {
            childNodes.add(child.committed);
        }
        adapter.reorder(node, childNodes);
        adapter.bindEvents(node, boundEvents(candidate));
    }

    /** 对已提交节点应用候选：更新 props、事件绑定与子节点 keyed diff。 */
    private void applyNode(UiHostNode node, Candidate candidate) {
        applyProps(node, candidate);
        diffChildren(node, candidate.children);
        Set<UiEventType> events = boundEvents(candidate);
        if (!events.equals(node.boundEvents())) {
            adapter.bindEvents(node, events);
        }
    }

    private void applyProps(UiHostNode node, Candidate candidate) {
        if (!candidate.props.equals(node.props())) {
            adapter.update(node, candidate.props);
        }
    }

    private void diffChildren(UiHostNode parent, List<Candidate> candidates) {
        List<UiHostNode> current = new ArrayList<>(parent.children());
        Map<String, UiHostNode> byIdentity = new LinkedHashMap<>();
        int keylessSlot = 0;
        for (UiHostNode child : current) {
            String identity = child.primitive().tag() + "#"
                    + (child.key() == null ? "@" + keylessSlot++ : child.key());
            byIdentity.put(identity, child);
        }

        List<UiHostNode> next = new ArrayList<>();
        Set<UiHostNode> reused = new HashSet<>();
        for (Candidate candidate : candidates) {
            UiHostNode existing = byIdentity.get(candidate.identity());
            if (existing != null && existing.primitive() == candidate.primitive && !existing.disposed()) {
                candidate.committed = existing;
                reused.add(existing);
                applyNode(existing, candidate);
                next.add(existing);
            } else {
                createSubtree(candidate);
                next.add(candidate.committed);
            }
        }

        // 释放候选树中消失的节点
        for (UiHostNode child : current) {
            if (!reused.contains(child) && !next.contains(child)) {
                disposeSubtree(child);
            }
        }

        if (!sameOrder(current, next)) {
            adapter.reorder(parent, next);
        }
    }

    private static boolean sameOrder(List<UiHostNode> current, List<UiHostNode> next) {
        if (current.size() != next.size()) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i) != next.get(i)) {
                return false;
            }
        }
        return true;
    }

    private Set<UiEventType> boundEvents(Candidate candidate) {
        Set<UiEventType> events = EnumSet.noneOf(UiEventType.class);
        for (UiProp prop : UiProp.values()) {
            if (!prop.isEvent()) continue;
            if (!candidate.props.has(prop.jsName())) continue;
            if (candidate.props.raw(prop.jsName()) == null) continue;
            UiEventType type = eventTypeOf(prop);
            if (type != null) events.add(type);
        }
        return events;
    }

    private static UiEventType eventTypeOf(UiProp prop) {
        for (UiEventType type : UiEventType.values()) {
            if (type.prop() == prop) return type;
        }
        return null;
    }

    /** 释放子树；已释放节点是 no-op（幂等 cleanup）。 */
    void disposeSubtree(UiHostNode node) {
        if (node == null || node.disposed()) {
            return;
        }
        for (UiHostNode child : new ArrayList<>(node.children())) {
            disposeSubtree(child);
        }
        adapter.dispose(node);
    }
}