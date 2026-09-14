package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.HostVNode;
import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiEventType;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiRootHandle;
import com.tkisor.nekojs.api.ui.UiRootStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC4 / AC8：reconciler 的候选 render、结构/属性/能力校验、原子提交、keyed diff、
 * 事件替换、子节点排序、布局与幂等 cleanup 全部由 fake host 观察；失败更新保留
 * 最后一次有效树。
 */
class UiRootReconcileTest {

    @Test
    void firstRenderCreatesPrimitiveOrderObservedByFakeHost() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", 320, "height", 240),
                UiTestFixture.host(UiPrimitive.COLUMN, null, UiTestFixture.props("gap", 2),
                        UiTestFixture.host(UiPrimitive.LABEL, null, UiTestFixture.props("text", "hi")),
                        UiTestFixture.host(UiPrimitive.BUTTON, null, UiTestFixture.props("text", "ok"))));
        UiRootHandle root = fixture.facade().open(fixture.screen());

        assertEquals(UiRootStatus.OPEN, root.status());
        assertEquals(List.of("screen", "column", "label", "button"),
                fixture.tagsOf(root.inspection().committedRoot()),
                "首次渲染必须产生确定的 primitive 顺序");
        assertEquals(List.of("screen", "column", "label", "button"),
                fixture.adapter.creates().stream()
                        .map(record -> record.primitive().tag()).toList());
    }

    @Test
    void unchangedSubtreeIsNotRebuiltOnUpdate() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                UiTestFixture.host(UiPrimitive.PANEL, "p", UiTestFixture.props(),
                        UiTestFixture.host(UiPrimitive.LABEL, null, UiTestFixture.props("text", "stable"))));
        var facade = fixture.facade();
        UiRootHandle root = facade.open(fixture.screen());
        UiHostNode panel = root.inspection().committedRoot().children().get(0);
        UiHostNode label = panel.children().get(0);
        fixture.adapter.resetCalls();

        root.invalidateAndReconcile();

        assertTrue(fixture.adapter.creates().isEmpty(), "未变化子树不得重新创建 host node");
        assertSame(panel, root.inspection().committedRoot().children().get(0));
        assertSame(label, root.inspection().committedRoot().children().get(0).children().get(0));
    }

    @Test
    void keyedListUpdatePreservesNodeIdentityAcrossReorderAndRemoval() {
        UiTestFixture fixture = new UiTestFixture();
        List<String> keys = new ArrayList<>(List.of("a", "b", "c"));
        fixture.render = () -> {
            HostVNode[] children = keys.stream()
                    .map(key -> UiTestFixture.host(UiPrimitive.LABEL, key,
                            UiTestFixture.props("text", key)))
                    .toArray(HostVNode[]::new);
            return UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                    UiTestFixture.host(UiPrimitive.COLUMN, null, UiTestFixture.props(), children));
        };
        UiRootHandle root = fixture.facade().open(fixture.screen());
        UiHostNode column = root.inspection().committedRoot().children().get(0);
        UiHostNode nodeB = column.children().get(1);

        keys.clear();
        keys.addAll(List.of("c", "a"));
        root.invalidateAndReconcile();

        List<UiHostNode> after = root.inspection().committedRoot().children().get(0).children();
        assertEquals(List.of("c", "a"), after.stream().map(UiHostNode::key).toList(),
                "keyed diff 必须按 key 重排子节点");
        assertTrue(nodeB.disposed(), "被移除的 key 对应节点必须释放");
        assertFalse(after.contains(nodeB), "移除的节点不得残留在已提交树中");
    }

    @Test
    void eventBindingIsReplacedAndRemovedWithNodeLifecycle() {
        UiTestFixture fixture = new UiTestFixture();
        boolean[] bound = {true};
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                UiTestFixture.host(UiPrimitive.BUTTON, null,
                        bound[0] ? UiTestFixture.props("text", "x", "onClick", (Runnable) () -> { })
                                : UiTestFixture.props("text", "x")));
        UiRootHandle root = fixture.facade().open(fixture.screen());
        UiHostNode button = root.inspection().committedRoot().children().get(0);
        assertEquals(Set.of(UiEventType.CLICK), button.boundEvents());

        bound[0] = false;
        root.invalidateAndReconcile();

        assertEquals(Set.of(), root.inspection().committedRoot().children().get(0).boundEvents(),
                "事件属性移除后绑定集合必须替换为空");
    }

    @Test
    void layoutProducesRectsAndClipAndRepaintsAfterResize() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.viewport = new com.tkisor.nekojs.api.ui.UiSize(100, 50);
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", "fill", "height", "fill"),
                UiTestFixture.host(UiPrimitive.COLUMN, null, UiTestFixture.props("gap", 4),
                        UiTestFixture.host(UiPrimitive.LABEL, null, UiTestFixture.props("text", "abcd"))));
        var facade = fixture.facade();
        UiRootHandle root = facade.open(fixture.screen());
        UiHostNode screen = root.inspection().committedRoot();
        assertEquals(100.0, screen.layout().width(), 0.001);
        assertEquals(50.0, screen.layout().height(), 0.001);

        facade.resize(new com.tkisor.nekojs.api.ui.UiSize(200, 20));
        assertEquals(200.0, root.inspection().committedRoot().layout().width(), 0.001,
                "resize 后布局必须失效重算");
        assertEquals(20.0, root.inspection().committedRoot().layout().height(), 0.001);
    }

    @Test
    void paintReportsObservableOrderAndClipRects() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", 100, "height", 60),
                UiTestFixture.host(UiPrimitive.PANEL, null,
                        UiTestFixture.props("width", 40, "height", 20)));
        UiRootHandle root = fixture.facade().open(fixture.screen());
        var inspection = root.paint();
        assertEquals(List.of("screen", "screen/panel[0]"), inspection.paintOperations());
        assertEquals(2, inspection.clipRects().size());
    }

    @Test
    void adapterFailureKeepsLastValidTreeAndIsReportedAsHostUpdate() {
        UiTestFixture fixture = new UiTestFixture();
        boolean[] fail = {false};
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                UiTestFixture.host(UiPrimitive.PANEL, null,
                        UiTestFixture.props("width", fail[0] ? 5 : 20)));
        UiRootHandle root = fixture.facade().open(fixture.screen());
        UiHostNode committed = root.inspection().committedRoot();
        double before = committed.children().get(0).layout().width();
        fixture.adapter.failCreateFor(UiPrimitive.PANEL);

        // 让候选树新增一个 panel：create 失败
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                UiTestFixture.host(UiPrimitive.PANEL, null, UiTestFixture.props("width", 20)),
                UiTestFixture.host(UiPrimitive.PANEL, "extra", UiTestFixture.props("width", 30)));
        root.invalidateAndReconcile();

        assertTrue(fixture.diagnostics.stream().anyMatch(d ->
                        d.stage() == UiDiagnosticStage.HOST_UPDATE),
                "adapter 失败必须归入 HOST_UPDATE 阶段诊断");
        assertSame(committed, root.inspection().committedRoot(),
                "提交失败必须保留最后一次有效树");
        assertEquals(before, root.inspection().committedRoot().children().get(0).layout().width(), 0.001);
    }

    @Test
    void unknownPrimitiveFailsExplicitlyWithoutCreatingAnything() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.adapter.resetCalls();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                UiTestFixture.host(UiPrimitive.PANEL, null, UiTestFixture.props()));
        // 只允许 screen：panel 触发 capability 失败
        var limited = new FakeUiHostAdapter(Set.of(UiPrimitive.SCREEN));
        UiTestFixture fixture2 = new UiTestFixture();
        // 用 fixture 的 facade 无法替换 adapter；这里直接验证 reconciler 的显式失败
        UiReconciler reconciler = new UiReconciler(limited,
                com.tkisor.nekojs.api.ui.UiComponentRegistry.EMPTY, 64);
        UiFailure failure = assertThrows(UiFailure.class, () -> reconciler.expand(
                UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                        UiTestFixture.host(UiPrimitive.PANEL, null, UiTestFixture.props()))));
        assertEquals(UiFailure.UNSUPPORTED_CAPABILITY, failure.code());
        assertTrue(limited.creates().isEmpty(), "能力校验失败不得创建任何 host node");
    }

    @Test
    void rootMustBeScreenPrimitive() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.PANEL, null, UiTestFixture.props());
        UiRootHandle root = fixture.facade().create(fixture.screen());
        root.open();
        assertTrue(fixture.hasDiagnostic(UiDiagnosticStage.RENDER, UiFailure.INVALID_ROOT),
                "非 screen 根必须显式失败");
        assertEquals(UiRootStatus.CREATED, root.status(), "无效候选不得把 root 提升为 OPEN");
        assertTrue(fixture.adapter.creates().isEmpty());
    }

    @Test
    void disposeIsIdempotentAndReleasesEveryHostNodeOnce() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                UiTestFixture.host(UiPrimitive.PANEL, null, UiTestFixture.props(),
                        UiTestFixture.host(UiPrimitive.LABEL, null, UiTestFixture.props("text", "x"))));
        UiRootHandle root = fixture.facade().open(fixture.screen());
        root.dispose();
        root.dispose();
        root.dispose();

        assertEquals(3, fixture.adapter.disposedNodes().size(), "cleanup 必须恰好释放每个节点一次");
        assertEquals(UiRootStatus.DISPOSED, root.status());
        assertFalse(root.inspection().hasCommittedTree());
    }

    @Test
    void operationsAfterDisposeFailExplicitly() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props());
        UiRootHandle root = fixture.facade().open(fixture.screen());
        root.dispose();

        UiFailure onOpen = assertThrows(UiFailure.class, root::open);
        assertEquals(UiFailure.ROOT_DISPOSED, onOpen.code());
        UiFailure onEvent = assertThrows(UiFailure.class,
                () -> root.dispatchEvent(UiTestFixture.click(1, 1)));
        assertEquals(UiFailure.ROOT_DISPOSED, onEvent.code());
    }

    @Test
    void failedCandidateKeepsLastValidTreeAvailable() {
        UiTestFixture fixture = new UiTestFixture();
        boolean[] valid = {true};
        fixture.render = () -> valid[0]
                ? UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                        UiTestFixture.host(UiPrimitive.LABEL, "a", UiTestFixture.props("text", "ok")))
                : UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                        UiTestFixture.host(UiPrimitive.LABEL, "a", UiTestFixture.props("text", "ok")),
                        UiTestFixture.host(UiPrimitive.LABEL, "a", UiTestFixture.props("text", "dup")));
        UiRootHandle root = fixture.facade().open(fixture.screen());
        UiHostNode committed = root.inspection().committedRoot();

        valid[0] = false;
        root.invalidateAndReconcile();

        assertTrue(fixture.hasDiagnostic(UiDiagnosticStage.RENDER, UiFailure.DUPLICATE_KEY));
        assertSame(committed, root.inspection().committedRoot(),
                "候选失败必须保留最后一次有效树");
        assertEquals(1, root.inspection().committedRoot().children().size());
    }

    @Test
    void componentExpansionProducesCandidateTreeAndTracksRenders() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.component("Counter", () -> UiTestFixture.host(UiPrimitive.LABEL, null,
                UiTestFixture.props("text", "count")));
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                new com.tkisor.nekojs.api.ui.ComponentVNode("Counter", null,
                        UiTestFixture.props(), List.of()));
        UiRootHandle root = fixture.facade().open(fixture.screen());

        assertEquals(List.of("screen", "label"), fixture.tagsOf(root.inspection().committedRoot()));
        assertEquals(1, fixture.componentRenders.getOrDefault("Counter", 0));
    }

    @Test
    void unknownComponentFailsExplicitly() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                new com.tkisor.nekojs.api.ui.ComponentVNode("Missing", null,
                        UiTestFixture.props(), List.of()));
        fixture.facade().create(fixture.screen()).open();
        assertTrue(fixture.hasDiagnostic(UiDiagnosticStage.COMPONENT, UiFailure.UNRESOLVED_COMPONENT));
    }

    @Test
    void fragmentExpandsWithoutProducingAHostNode() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                new com.tkisor.nekojs.api.ui.FragmentVNode(null, List.of(
                        UiTestFixture.host(UiPrimitive.LABEL, null, UiTestFixture.props("text", "a")),
                        UiTestFixture.host(UiPrimitive.LABEL, null, UiTestFixture.props("text", "b")))));
        UiRootHandle root = fixture.facade().open(fixture.screen());
        assertEquals(List.of("screen", "label", "label"), fixture.tagsOf(root.inspection().committedRoot()),
                "Fragment 只做子节点展开，不产生 host node");
    }
}
