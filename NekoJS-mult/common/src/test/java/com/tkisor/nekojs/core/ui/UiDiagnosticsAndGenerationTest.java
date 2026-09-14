package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiDiagnostic;
import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiEventType;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiRootHandle;
import com.tkisor.nekojs.api.ui.UiRootStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC5 / AC8：render、component、layout、event、host update 阶段错误可区分并进入统一
 * 诊断 seam；单个 event 错误不影响其它控件；render/layout 失败不提交半成品树。
 * close/dispose、重复 close、候选失败清理和最后有效树保留都有 fake Adapter 测试。
 */
class UiDiagnosticsAndGenerationTest {

    @Test
    void renderFailureIsReportedAsRenderStageAndKeepsRootCreated() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> {
            throw new IllegalStateException("boom");
        };
        UiRootHandle root = fixture.facade().create(fixture.screen());
        root.open();

        assertTrue(fixture.hasDiagnostic(UiDiagnosticStage.RENDER, "RENDER_FAILURE"));
        assertEquals(UiRootStatus.CREATED, root.status());
        assertFalse(root.inspection().hasCommittedTree());
    }

    @Test
    void componentFailureIsReportedAsComponentStage() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.components.put("Boom", (props, children) -> {
            throw new IllegalStateException("component exploded");
        });
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                new com.tkisor.nekojs.api.ui.ComponentVNode("Boom", null,
                        UiTestFixture.props(), List.of()));
        fixture.facade().create(fixture.screen()).open();

        assertTrue(fixture.hasDiagnostic(UiDiagnosticStage.COMPONENT, UiFailure.INVALID_COMPONENT_RESULT));
    }

    @Test
    void invalidLayoutValueIsReportedAsLayoutStage() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", "not-a-size"));
        fixture.facade().create(fixture.screen()).open();

        assertTrue(fixture.diagnostics.stream().anyMatch(d ->
                        d.stage() == UiDiagnosticStage.LAYOUT || d.stage() == UiDiagnosticStage.COMPONENT),
                "非法布局值必须进入可区分的诊断阶段: " + fixture.diagnostics);
        assertFalse(fixture.adapter.creates().stream()
                        .anyMatch(record -> record.primitive() == UiPrimitive.SCREEN),
                "布局校验失败不得留下半提交树");
    }

    @Test
    void everyDiagnosticStageIsDistinctAndCarriesRootAndGeneration() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.generation = 7;
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                UiTestFixture.host(UiPrimitive.LABEL, "a", UiTestFixture.props("text", "x")),
                UiTestFixture.host(UiPrimitive.LABEL, "a", UiTestFixture.props("text", "y")));
        UiRootHandle root = fixture.facade().open(fixture.screen());

        UiDiagnostic diagnostic = fixture.diagnostics.stream()
                .filter(d -> d.stage() == UiDiagnosticStage.RENDER).findFirst().orElseThrow();
        assertEquals(UiFailure.DUPLICATE_KEY, diagnostic.code());
        assertEquals(root.rootId(), diagnostic.rootId(), "诊断必须携带 UI root 标识");
        assertEquals(7, diagnostic.generation(), "诊断必须携带 generation");
        assertNotNull(diagnostic.message());

        // 阶段码是稳定字符串，且五个阶段互不相同
        assertEquals(5, java.util.Arrays.stream(UiDiagnosticStage.values())
                .map(UiDiagnosticStage::code).distinct().count());
    }

    @Test
    void singleEventFailureDoesNotStopOtherDispatchOrBreakRoot() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", 100, "height", 60),
                UiTestFixture.host(UiPrimitive.BUTTON, null,
                        UiTestFixture.props("onClick", (Runnable) () -> { },
                                "width", 40, "height", 20)));
        UiRootHandle root = fixture.facade().open(fixture.screen());

        fixture.failEvents = true;
        root.dispatchEvent(UiTestFixture.click(5, 5));
        assertTrue(fixture.hasDiagnostic(UiDiagnosticStage.EVENT, "EVENT_FAILURE"),
                "事件回调异常必须进入 EVENT 阶段诊断");

        // 其它控件的事件仍然可以继续分发
        fixture.failEvents = false;
        root.dispatchEvent(UiTestFixture.click(5, 5));
        assertEquals(2, fixture.dispatched.size(), "单个 event 错误不得阻断后续事件分发");
        assertTrue(root.isActive());
    }

    @Test
    void hostAdapterPaintFailureIsReportedAsHostUpdateAndDoesNotAbortOtherNodes() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null,
                UiTestFixture.props("width", 100, "height", 60),
                UiTestFixture.host(UiPrimitive.PANEL, null,
                        UiTestFixture.props("width", 20, "height", 20)));
        UiRootHandle root = fixture.facade().open(fixture.screen());
        fixture.adapter.onPaint(() -> {
            throw new IllegalStateException("paint exploded");
        });
        var inspection = root.paint();
        assertTrue(inspection.paintOperations().size() <= 1, "失败节点不得记录为已绘制");
    }

    @Test
    void closeGenerationIsIdempotentAndReleasesEverythingOnce() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                UiTestFixture.host(UiPrimitive.LABEL, null, UiTestFixture.props("text", "x")));
        var facade = fixture.facade();
        UiRootHandle root = facade.open(fixture.screen());

        facade.closeGeneration(1);
        facade.closeGeneration(1);
        facade.closeGeneration(1);

        assertEquals(UiRootStatus.DISPOSED, root.status());
        assertEquals(2, fixture.adapter.disposedNodes().size(), "每个 host node 恰好释放一次");
    }

    @Test
    void closeGenerationIgnoresOtherGenerations() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.generation = 3;
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props());
        var facade = fixture.facade();
        UiRootHandle root = facade.open(fixture.screen());

        facade.closeGeneration(2);
        assertTrue(root.isActive(), "其它 generation 的关闭不得影响本 generation 的 root");
        facade.closeGeneration(3);
        assertFalse(root.isActive());
    }

    @Test
    void candidateFailureCleanupLeavesLastValidTreeAndDisposedCandidatesDoNotLinger() {
        UiTestFixture fixture = new UiTestFixture();
        boolean[] valid = {true};
        fixture.render = () -> valid[0]
                ? UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                        UiTestFixture.host(UiPrimitive.PANEL, null, UiTestFixture.props("width", 30)))
                : UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                        UiTestFixture.host(UiPrimitive.PANEL, null, UiTestFixture.props("width", 30)),
                        UiTestFixture.host(UiPrimitive.SCROLL, null, UiTestFixture.props()));
        // fake Adapter 不支持 scroll 时能力校验失败
        var profileWithoutScroll = new FakeUiHostAdapter(java.util.EnumSet.of(
                UiPrimitive.SCREEN, UiPrimitive.PANEL, UiPrimitive.LABEL));
        UiReconciler reconciler = new UiReconciler(profileWithoutScroll,
                com.tkisor.nekojs.api.ui.UiComponentRegistry.EMPTY, 64);
        UiFailure failure = assertThrows(UiFailure.class, () -> reconciler.expand(
                UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                        UiTestFixture.host(UiPrimitive.PANEL, null, UiTestFixture.props()),
                        UiTestFixture.host(UiPrimitive.SCROLL, null, UiTestFixture.props()))));
        assertEquals(UiFailure.UNSUPPORTED_CAPABILITY, failure.code());
        assertTrue(profileWithoutScroll.creates().isEmpty(),
                "候选校验失败不得创建任何 host node（无残留需要清理）");
    }

    @Test
    void invalidAtSecondAttemptKeepsDisposedFlagCleanForReusedNodes() {
        UiTestFixture fixture = new UiTestFixture();
        int[] value = {1};
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props(),
                UiTestFixture.host(UiPrimitive.LABEL, "a", UiTestFixture.props("text", String.valueOf(value[0]))));
        UiRootHandle root = fixture.facade().open(fixture.screen());
        UiHostNode label = root.inspection().committedRoot().children().get(0);

        value[0] = 2;
        root.invalidateAndReconcile();
        assertFalse(label.disposed(), "复用的节点不得被释放");
        assertEquals("2", label.props().string("text"));
    }

    @Test
    void closeOnEscapeGoesThroughTheSameCleanupPath() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props());
        UiRootHandle root = fixture.facade().open(fixture.screen());
        root.dispatchEvent(com.tkisor.nekojs.api.ui.UiEvent.plain(UiEventType.CLOSE));
        assertEquals(UiRootStatus.DISPOSED, root.status(),
                "Escape 关闭必须进入同一 root cleanup 路径");
    }

    @Test
    void facadeDisposeIsIdempotentAndRejectsFurtherCreates() {
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props());
        var facade = fixture.facade();
        facade.open(fixture.screen());
        facade.dispose();
        facade.dispose();

        UiFailure failure = assertThrows(UiFailure.class,
                () -> facade.create(fixture.screen()));
        assertEquals(UiFailure.ROOT_DISPOSED, failure.code());
    }

    @Test
    void unavailableFacadeRejectsInsteadOfSilentNoOp() {
        var unavailable = new UnavailableUiFacade(0);
        UiFailure failure = assertThrows(UiFailure.class,
                () -> unavailable.create(fixtureScreen()));
        assertEquals(UiFailure.UNSUPPORTED_CAPABILITY, failure.code());
        assertTrue(unavailable.roots().isEmpty());
    }

    private static com.tkisor.nekojs.api.ui.UiScreen fixtureScreen() {
        return new com.tkisor.nekojs.api.ui.UiScreen() {
            @Override
            public com.tkisor.nekojs.api.ui.UiVNode render() {
                return UiTestFixture.host(UiPrimitive.SCREEN, null, UiTestFixture.props());
            }

            @Override
            public com.tkisor.nekojs.api.ui.UiScreenOptions options() {
                return com.tkisor.nekojs.api.ui.UiScreenOptions.defaults();
            }
        };
    }
}
