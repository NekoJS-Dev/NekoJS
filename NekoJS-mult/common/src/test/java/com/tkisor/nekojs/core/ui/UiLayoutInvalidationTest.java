package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiRect;
import com.tkisor.nekojs.api.ui.UiRootHandle;
import com.tkisor.nekojs.api.ui.UiSize;
import com.tkisor.nekojs.api.ui.ViewportContext;
import com.tkisor.nekojs.api.ui.ViewportProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC5：resize / profile 切换只让布局失效并重新 measure/arrange，<b>不</b>重新执行脚本 render，
 * 也<b>不</b>重建全部 host node；稳定输出包含最终矩形、裁剪与溢出诊断。
 */
class UiLayoutInvalidationTest {

    @Test
    void resizeRelayoutsWithoutReRunningTheScriptRender() {
        UiTestFixture fixture = new UiTestFixture();
        int[] renders = {0};
        fixture.viewport = new UiSize(700, 400);
        fixture.render = () -> {
            renders[0]++;
            return UiTestFixture.host(UiPrimitive.SCREEN, null,
                    UiTestFixture.props("width", "fill", "height", "fill", "padding", 4),
                    UiTestFixture.host(UiPrimitive.PANEL, null,
                            UiTestFixture.props("width", 40, "height", 20)));
        };
        var facade = fixture.facade();
        UiRootHandle root = facade.open(fixture.screen());
        assertEquals(1, renders[0]);

        int createsBefore = fixture.adapter.creates().size();
        int updatesBefore = fixture.adapter.updates().size();
        int reordersBefore = fixture.adapter.reorders().size();
        List<UiHostNode> nodesBefore = flatten(root.inspection().committedRoot());

        facade.resize(new UiSize(200, 150));

        assertEquals(1, renders[0], "resize 不得重新执行脚本 render");
        assertEquals(createsBefore, fixture.adapter.creates().size(),
                "resize 不得创建新的 host node");
        assertEquals(updatesBefore, fixture.adapter.updates().size(),
                "resize 不得更新 host node 属性");
        assertEquals(reordersBefore, fixture.adapter.reorders().size(),
                "resize 不得重排子节点");

        List<UiHostNode> nodesAfter = flatten(root.inspection().committedRoot());
        assertEquals(nodesBefore.size(), nodesAfter.size());
        for (int i = 0; i < nodesBefore.size(); i++) {
            assertSame(nodesBefore.get(i), nodesAfter.get(i),
                    "resize 必须复用同一批 host node 对象（不重建全部节点）");
        }

        // 布局确实重算了：screen 跟随新视口，panel 的绝对位置随之变化（padding 4）。
        UiHostNode screen = nodesAfter.get(0);
        UiHostNode panel = nodesAfter.get(1);
        assertEquals(200.0, screen.layout().width(), 0.0001);
        assertEquals(150.0, screen.layout().height(), 0.0001);
        assertEquals(4.0, panel.layout().x(), 0.0001);
        assertEquals(4.0, panel.layout().y(), 0.0001);
    }

    @Test
    void profileChangeIsObservableAndLayoutRevisionsAdvancePerRelayout() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.viewport = new UiSize(700, 400);
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", "fill", "height", "fill"));
        var facade = fixture.facade();
        UiRootHandle root = facade.open(fixture.screen());

        assertTrue(root.viewport().profile() == ViewportProfile.DESKTOP_WIDE);
        long firstRevision = root.inspection().layoutRevisions();
        assertEquals(ViewportProfile.DESKTOP_WIDE, root.inspection().profile());

        facade.resize(new UiSize(300, 200));
        assertEquals(ViewportProfile.HANDHELD, root.viewport().profile(), "profile 必须随视口变化");
        assertEquals(ViewportProfile.HANDHELD, root.inspection().profile(),
                "inspector 必须报告当前 profile");
        assertEquals(firstRevision + 1, root.inspection().layoutRevisions(),
                "每次重排推进一次布局代数");

        // 相同视口重复 resize 是显式无操作：不推进代数。
        facade.resize(new UiSize(300, 200));
        assertEquals(firstRevision + 1, root.inspection().layoutRevisions());

        // 安全区域变化也算视口事实变化：复用同一逻辑视口但可用区变小。
        fixture.safeArea = ViewportContext.SafeArea.of(60);
        facade.resize(new ViewportContext(300, 200, ViewportContext.SafeArea.of(60), 3,
                java.util.Set.of()));
        assertEquals(firstRevision + 2, root.inspection().layoutRevisions());
        assertEquals(180.0, root.viewport().contentWidth(), 0.0001);
        assertEquals(3, root.viewport().guiScale(), "原生 scale 作为事实被记录，但不改变 profile 语义");
    }

    @Test
    void viewportReadingRenderFallsBackToFullReconcileForCorrectness() {
        UiTestFixture fixture = new UiTestFixture();
        int[] renders = {0};
        fixture.viewport = new UiSize(700, 400);
        var facade = fixture.facade();
        UiRootHandle root = facade.create(fixture.screen());
        // 脚本 render 读取 root.viewport()：该 root 的布局依赖视口，resize 必须重跑脚本。
        fixture.render = () -> {
            renders[0]++;
            double width = root.viewport().contentWidth();
            return UiTestFixture.host(UiPrimitive.SCREEN, null,
                    UiTestFixture.props("width", width, "height", "fill"));
        };
        root.open();
        assertEquals(1, renders[0]);
        assertEquals(700.0, root.inspection().committedRoot().layout().width(), 0.0001);

        facade.resize(new UiSize(300, 200));
        assertEquals(2, renders[0],
                "render 读过视口事实时必须重跑脚本，否则脚本看不到新视口（结构可能已变）");
        assertEquals(300.0, root.inspection().committedRoot().layout().width(), 0.0001);
    }

    @Test
    void overflowIsReportedForNodesLargerThanTheirContainer() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.viewport = new UiSize(640, 400);
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", "fill", "height", "fill"),
                UiTestFixture.host(UiPrimitive.SCROLL, null,
                        UiTestFixture.props("width", 100, "height", 60),
                        UiTestFixture.host(UiPrimitive.PANEL, null,
                                UiTestFixture.props("width", 400, "height", 20))));
        UiRootHandle root = fixture.facade().open(fixture.screen());

        var inspection = root.inspection();
        assertTrue(inspection.hasOverflow(), "子节点宽于容器时必须报告溢出: " + inspection.overflow());
        var panelOverflow = inspection.overflow().get("screen/scroll[0]/panel[0]");
        assertEquals(300.0, panelOverflow.horizontal(), 0.0001, "400 宽的子节点放进 100 宽的容器");
        assertEquals(0.0, panelOverflow.vertical(), 0.0001);
        assertFalse(inspection.overflow().containsKey("screen/scroll[0]"),
                "未溢出的容器本身不应被报告");
    }

    @Test
    void inlineNodeOverflowIsReportedInBothAxes() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.viewport = new UiSize(640, 400);
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", "fill", "height", "fill"),
                UiTestFixture.host(UiPrimitive.PANEL, null,
                        UiTestFixture.props("width", 50, "height", 30),
                        UiTestFixture.host(UiPrimitive.ROW, null,
                                UiTestFixture.props(),
                                UiTestFixture.host(UiPrimitive.LABEL, null,
                                        UiTestFixture.props("text", "aaaaa", "width", 80, "height", 90)))));
        UiRootHandle root = fixture.facade().open(fixture.screen());
        var labelOverflow = root.inspection().overflow().get("screen/panel[0]/row[0]/label[0]");
        assertEquals(30.0, labelOverflow.horizontal(), 0.0001, "80 宽的子节点超出 50 宽的容器");
        assertEquals(60.0, labelOverflow.vertical(), 0.0001, "90 高的子节点超出 30 高的容器");
        assertTrue(labelOverflow.area() > 0);

        // 同步断言最终矩形：溢出诊断只报告事实，矩形本身按裁剪规则产出。
        UiHostNode label = root.inspection().committedRoot().children().get(0)
                .children().get(0).children().get(0);
        assertEquals(80.0, label.layout().width(), 0.0001);
        assertEquals(90.0, label.layout().height(), 0.0001);
    }

    @Test
    void resizeKeepsPaintContinuityAndClipRects() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.viewport = new UiSize(400, 300);
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", "fill", "height", "fill"),
                UiTestFixture.host(UiPrimitive.PANEL, null,
                        UiTestFixture.props("width", 40, "height", 20)));
        var facade = fixture.facade();
        UiRootHandle root = facade.open(fixture.screen());
        var before = root.paint();
        assertEquals(List.of("screen", "screen/panel[0]"), before.paintOperations());

        facade.resize(new UiSize(120, 90));
        var after = root.inspection();
        assertEquals(List.of("screen", "screen/panel[0]"), after.paintOperations(),
                "resize 不得打断绘制顺序契约");
        assertEquals(2, after.clipRects().size());
        assertEquals(new UiRect(0, 0, 120, 90), after.clipRects().get("screen"));
    }

    private static List<UiHostNode> flatten(UiHostNode node) {
        List<UiHostNode> all = new java.util.ArrayList<>();
        collect(node, all);
        return all;
    }

    private static void collect(UiHostNode node, List<UiHostNode> out) {
        out.add(node);
        for (UiHostNode child : node.children()) {
            collect(child, out);
        }
    }
}
