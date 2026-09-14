//? if neoforge {
package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.api.ui.UiEventType;
import com.tkisor.nekojs.api.ui.UiHostAdapter;
import com.tkisor.nekojs.api.ui.UiHostCreate;
import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProp;
import com.tkisor.nekojs.api.ui.UiProps;
import com.tkisor.nekojs.api.ui.UiRect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NekoJS JSX UI 在 NeoForge 26.2 的 host Adapter（jsx-ui-spec「平台边界」）。
 *
 * <p>本类持有全部 Minecraft 类型；common 的 reconciler / layout engine 只通过
 * {@link UiHostAdapter} 的公开契约与它交互（jsx-ui-spec AC6）。
 *
 * <p>节点模型刻意保持「可检查事实」：{@code create/update/reorder/bindEvents/dispose/
 * arrange/paint} 只改变本类记录的已提交状态、不接触任何渲染对象——绘制由
 * {@link Nf262UiScreen} 读取本类的节点矩形后经原生 {@code GuiGraphicsExtractor} 完成。
 * 因此 adapter 层本身可以在无 Minecraft 客户端的单测里被驱动。
 *
 * <p>线程约束：全部方法只在 root 的 client owner thread 上调用。
 */
public final class Nf262UiHostAdapter implements UiHostAdapter {

    /** 26.2 已接线、可真实绘制与交互的 primitive 集合。 */
    private static final Set<UiPrimitive> PRIMITIVES = Collections.unmodifiableSet(EnumSet.of(
            UiPrimitive.SCREEN, UiPrimitive.PANEL, UiPrimitive.ROW, UiPrimitive.COLUMN,
            UiPrimitive.STACK, UiPrimitive.SCROLL, UiPrimitive.LABEL, UiPrimitive.BUTTON,
            UiPrimitive.INPUT, UiPrimitive.SPACER));

    private static final AtomicInteger NODE_SEQUENCE = new AtomicInteger();

    private final List<Nf262UiNode> allNodes = new ArrayList<>();

    @Override
    public String adapterId() {
        return "neoforge-26.2";
    }

    @Override
    public Set<UiPrimitive> primitives() {
        return PRIMITIVES;
    }

    @Override
    public UiHostNode create(UiHostCreate request) {
        Nf262UiNode node = new Nf262UiNode("nf262-" + NODE_SEQUENCE.incrementAndGet(),
                request.primitive(), request.key(), request.props(), request.path());
        allNodes.add(node);
        return node;
    }

    @Override
    public void update(UiHostNode node, UiProps props) {
        cast(node).props = props == null ? UiProps.empty() : props;
    }

    @Override
    public void reorder(UiHostNode parent, List<UiHostNode> children) {
        Nf262UiNode owner = cast(parent);
        List<UiHostNode> next = new ArrayList<>(children);
        owner.children = next;
        for (UiHostNode child : next) {
            cast(child).parent = owner;
        }
    }

    @Override
    public void bindEvents(UiHostNode node, Set<UiEventType> events) {
        cast(node).boundEvents = events == null || events.isEmpty()
                ? EnumSet.noneOf(UiEventType.class) : EnumSet.copyOf(events);
    }

    @Override
    public void dispose(UiHostNode node) {
        Nf262UiNode target = cast(node);
        if (target.disposed) {
            return;
        }
        // 事件闭包随节点一并失效：解绑后旧闭包不再被 root 命中（AC7）。
        target.boundEvents = EnumSet.noneOf(UiEventType.class);
        target.disposed = true;
        for (UiHostNode child : new ArrayList<>(target.children)) {
            dispose(child);
        }
        allNodes.remove(target);
    }

    @Override
    public void arrange(UiHostNode node, UiRect rect) {
        cast(node).layout = rect;
    }

    @Override
    public void paint(UiHostNode node, UiRect clip) {
        Nf262UiNode target = cast(node);
        target.lastClip = clip;
        target.painted = true;
    }

    /** 当前仍存活的节点（绘制顺序遍历用）。 */
    List<Nf262UiNode> liveNodes() {
        return List.copyOf(allNodes);
    }

    /** 清空全部节点（Screen 释放时的兜底；正常路径由 reconciler 逐节点 dispose）。 */
    void clear() {
        allNodes.clear();
    }

    private static Nf262UiNode cast(UiHostNode node) {
        if (!(node instanceof Nf262UiNode nf)) {
            throw new IllegalArgumentException("26.2 adapter received a foreign host node: " + node);
        }
        return nf;
    }

    /** 适配器侧 host node：只有可观察事实，不持有任何渲染/输入对象。 */
    static final class Nf262UiNode implements UiHostNode {
        private final String nodeId;
        private final UiPrimitive primitive;
        private final String key;
        private final String path;
        // package-private：Screen 在事件路径上就地更新 host 侧状态（focused/value），
        // 这些修改不回写 VNode，也不跨 owner thread。
        UiProps props;
        List<UiHostNode> children = new ArrayList<>();
        Set<UiEventType> boundEvents = EnumSet.noneOf(UiEventType.class);
        UiRect layout = UiRect.empty();
        private UiRect lastClip = UiRect.empty();
        private Nf262UiNode parent;
        boolean disposed;
        private boolean painted;

        Nf262UiNode(String nodeId, UiPrimitive primitive, String key, UiProps props, String path) {
            this.nodeId = nodeId;
            this.primitive = primitive;
            this.key = key;
            this.props = props == null ? UiProps.empty() : props;
            this.path = path;
        }

        @Override
        public String nodeId() {
            return nodeId;
        }

        @Override
        public UiPrimitive primitive() {
            return primitive;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public UiRect layout() {
            return layout;
        }

        @Override
        public UiProps props() {
            return props;
        }

        @Override
        public List<UiHostNode> children() {
            return List.copyOf(children);
        }

        @Override
        public Set<UiEventType> boundEvents() {
            return Set.copyOf(boundEvents);
        }

        @Override
        public boolean disposed() {
            return disposed;
        }

        UiRect lastClip() {
            return lastClip;
        }

        boolean painted() {
            return painted;
        }

        void resetPaint() {
            painted = false;
        }

        /** 命中测试：只命中已提交且未释放、且绑定了指针事件的节点（disabled 不参与命中）。 */
        boolean canReceivePointer() {
            if (disposed || !props.bool(UiProp.VISIBLE, true) || props.bool(UiProp.DISABLED, false)) {
                return false;
            }
            return boundEvents.contains(UiEventType.CLICK)
                    || boundEvents.contains(UiEventType.MOUSE_DOWN)
                    || boundEvents.contains(UiEventType.MOUSE_UP)
                    || boundEvents.contains(UiEventType.MOUSE_MOVE)
                    || boundEvents.contains(UiEventType.SCROLL);
        }
    }
}