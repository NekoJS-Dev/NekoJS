package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiEventType;
import com.tkisor.nekojs.api.ui.UiHostAdapter;
import com.tkisor.nekojs.api.ui.UiHostCreate;
import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProp;
import com.tkisor.nekojs.api.ui.UiProps;
import com.tkisor.nekojs.api.ui.UiRect;
import com.tkisor.nekojs.api.ui.UiSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * fake host Adapter（jsx-ui-spec Testing Decisions）：在 common 内完整实现
 * {@link UiHostAdapter}，让 VNode 展开、keyed diff、布局、事件替换、提交与清理
 * 都能在不启动 Minecraft 的情况下被观察。
 *
 * <p>fake 节点只记录可观察事实：创建顺序、props、最终矩形、子节点顺序、事件绑定集合、
 * 释放状态与 paint 调用记录。不含绘制、纹理或 GL 语义。
 */
public final class FakeUiHostAdapter implements UiHostAdapter {

    /** 单次 create 调用的记录。 */
    public record CreateRecord(String nodeId, UiPrimitive primitive, String key, UiProps props, String path) {
    }

    /** 单次 update 调用的记录。 */
    public record UpdateRecord(String nodeId, UiProps props) {
    }

    /** 单次 reorder 调用的记录。 */
    public record ReorderRecord(String nodeId, List<String> children) {
    }

    /** 单次 bindEvents 调用的记录。 */
    public record BindRecord(String nodeId, Set<UiEventType> events) {
    }

    private final Set<UiPrimitive> primitives;
    private final List<CreateRecord> creates = new ArrayList<>();
    private final List<UpdateRecord> updates = new ArrayList<>();
    private final List<ReorderRecord> reorders = new ArrayList<>();
    private final List<BindRecord> binds = new ArrayList<>();
    private final List<String> disposed = new ArrayList<>();
    private final List<String> painted = new ArrayList<>();
    private final Set<String> failOnCreate = new HashSet<>();
    private int sequence;
    private Runnable paintingHook;

    /** 默认支持全部登记 primitive。 */
    public FakeUiHostAdapter() {
        this(EnumSet.allOf(UiPrimitive.class));
    }

    /** 只支持给定 primitive（用于能力校验测试）。 */
    public FakeUiHostAdapter(Set<UiPrimitive> primitives) {
        this.primitives = Collections.unmodifiableSet(EnumSet.copyOf(primitives));
    }

    @Override
    public String adapterId() {
        return "fake";
    }

    @Override
    public Set<UiPrimitive> primitives() {
        return primitives;
    }

    @Override
    public UiHostNode create(UiHostCreate request) {
        String nodeId = "fake-" + (++sequence);
        if (failOnCreate.contains(request.primitive().tag())) {
            throw new IllegalStateException("fake adapter is configured to fail creating "
                    + request.primitive().tag());
        }
        FakeUiHostNode node = new FakeUiHostNode(nodeId, request.primitive(), request.key(),
                request.props(), request.path());
        creates.add(new CreateRecord(nodeId, request.primitive(), request.key(), request.props(), request.path()));
        return node;
    }

    @Override
    public void update(UiHostNode node, UiProps props) {
        FakeUiHostNode fake = cast(node);
        fake.props = props;
        updates.add(new UpdateRecord(fake.nodeId(), props));
    }

    @Override
    public void reorder(UiHostNode parent, List<UiHostNode> children) {
        FakeUiHostNode fake = cast(parent);
        fake.children = new ArrayList<>(children);
        List<String> ids = new ArrayList<>();
        for (UiHostNode child : children) {
            ids.add(child.nodeId());
        }
        reorders.add(new ReorderRecord(fake.nodeId(), List.copyOf(ids)));
    }

    @Override
    public void bindEvents(UiHostNode node, Set<UiEventType> events) {
        FakeUiHostNode fake = cast(node);
        fake.boundEvents = events.isEmpty()
                ? EnumSet.noneOf(UiEventType.class) : EnumSet.copyOf(events);
        binds.add(new BindRecord(fake.nodeId(), Set.copyOf(events)));
    }

    @Override
    public void dispose(UiHostNode node) {
        FakeUiHostNode fake = cast(node);
        if (fake.disposed) {
            return;
        }
        fake.disposed = true;
        for (UiHostNode child : new ArrayList<>(fake.children)) {
            dispose(child);
        }
        disposed.add(fake.nodeId());
    }

    @Override
    public void arrange(UiHostNode node, UiRect rect) {
        cast(node).layout = rect;
    }

    @Override
    public void paint(UiHostNode node, UiRect clip) {
        if (paintingHook != null) {
            paintingHook.run();
        }
        painted.add(cast(node).nodeId());
    }

    /** 全部 create 调用记录。 */
    public List<CreateRecord> creates() {
        return List.copyOf(creates);
    }

    /** 全部 update 调用记录。 */
    public List<UpdateRecord> updates() {
        return List.copyOf(updates);
    }

    /** 全部 reorder 调用记录。 */
    public List<ReorderRecord> reorders() {
        return List.copyOf(reorders);
    }

    /** 全部 bindEvents 调用记录。 */
    public List<BindRecord> binds() {
        return List.copyOf(binds);
    }

    /** 释放顺序（node id）。 */
    public List<String> disposedNodes() {
        return List.copyOf(disposed);
    }

    /** paint 调用顺序（node id）。 */
    public List<String> paintedNodes() {
        return List.copyOf(painted);
    }

    /** 让某个 primitive 的 create 失败。 */
    public void failCreateFor(UiPrimitive primitive) {
        failOnCreate.add(primitive.tag());
    }

    /** 在 paint 期间执行副作用（用于验证异常路径）。 */
    public void onPaint(Runnable hook) {
        this.paintingHook = hook;
    }

    /** 重置调用记录（不重置已创建节点）。 */
    public void resetCalls() {
        creates.clear();
        updates.clear();
        reorders.clear();
        binds.clear();
        disposed.clear();
        painted.clear();
    }

    private static FakeUiHostNode cast(UiHostNode node) {
        if (!(node instanceof FakeUiHostNode fake)) {
            throw new IllegalArgumentException("fake adapter received a foreign host node: " + node);
        }
        return fake;
    }
    /** fake host node：只记录可观察事实。 */
    public static final class FakeUiHostNode implements UiHostNode {
        private final String nodeId;
        private final UiPrimitive primitive;
        private final String key;
        private final String path;
        private UiProps props;
        private List<UiHostNode> children = new ArrayList<>();
        private Set<UiEventType> boundEvents = EnumSet.noneOf(UiEventType.class);
        private UiRect layout = UiRect.empty();
        private boolean disposed;

        FakeUiHostNode(String nodeId, UiPrimitive primitive, String key, UiProps props, String path) {
            this.nodeId = nodeId;
            this.primitive = primitive;
            this.key = key;
            this.props = props;
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

        /** 由 props 的 width/height 得到确定性内容尺寸。 */
        UiSize contentSize(UiSize available) {
            return new UiSize(declared(props, UiProp.WIDTH, available.width()),
                    declared(props, UiProp.HEIGHT, available.height()));
        }

        private static double declared(UiProps props, UiProp prop, double fallback) {
            if (!props.has(prop)) {
                return fallback;
            }
            Object raw = props.raw(prop);
            if (raw instanceof Number number) {
                return number.doubleValue();
            }
            if (raw instanceof String text) {
                try {
                    return com.tkisor.nekojs.api.ui.UiDimension.parse(text).value();
                } catch (RuntimeException ignored) {
                    return fallback;
                }
            }
            return fallback;
        }

        @Override
        public String toString() {
            return "<" + primitive.tag() + " " + nodeId + (key == null ? "" : " key=" + key) + ">";
        }
    }
}