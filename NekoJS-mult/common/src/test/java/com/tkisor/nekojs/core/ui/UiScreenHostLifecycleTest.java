package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiResources;
import com.tkisor.nekojs.api.ui.UiRootHandle;
import com.tkisor.nekojs.api.ui.UiRootStatus;
import com.tkisor.nekojs.api.ui.UiScreenHost;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC7 的 common 侧证据：Screen 投影与 host node 走同一条释放路径，且 cleanup 幂等。
 *
 * <p>这些断言只用 {@link UiScreenHost} 这一公开 seam 的可观察调用顺序，不依赖任何
 * Minecraft 类型——真实原生 Screen 的 in-game 证据在票 41 的 MCP smoke 里。
 */
class UiScreenHostLifecycleTest {

    /** 记录 open/close 调用顺序的 fake screen host。 */
    private static final class RecordingScreenHost implements UiScreenHost {
        final List<String> calls = new ArrayList<>();
        final List<UiRootHandle> opened = new ArrayList<>();
        final List<UiRootHandle> closed = new ArrayList<>();
        boolean alive;

        @Override
        public void open(UiRootHandle root) {
            calls.add("open");
            opened.add(root);
            alive = true;
        }

        @Override
        public void close(UiRootHandle root) {
            calls.add("close");
            closed.add(root);
            alive = false;
        }

        @Override
        public boolean isAlive(UiRootHandle root) {
            return alive && opened.contains(root);
        }
    }

    private UiTestFixture fixtureWith(RecordingScreenHost host) {
        UiTestFixture fixture = new UiTestFixture();
        UiResources base = fixture.resources();
        fixture.hostResources = new UiResources() {
            @Override public com.tkisor.nekojs.api.ui.UiHostAdapter adapter() { return base.adapter(); }
            @Override public com.tkisor.nekojs.api.ui.UiTextMeasurer textMeasurer() { return base.textMeasurer(); }
            @Override public com.tkisor.nekojs.api.ui.UiComponentRegistry components() { return base.components(); }
            @Override public int generation() { return base.generation(); }
            @Override public com.tkisor.nekojs.api.ui.UiDiagnosticSink diagnostics() { return base.diagnostics(); }
            @Override public Thread ownerThread() { return base.ownerThread(); }
            @Override public Invalidator invalidator() { return base.invalidator(); }
            @Override public UiEventSink eventSink() { return base.eventSink(); }
            @Override public com.tkisor.nekojs.api.ui.UiSize viewport() { return base.viewport(); }
            @Override public UiScreenHost screenHost() { return host; }
        };
        return fixture;
    }

    @Test
    void openProjectsTheScreenOnlyAfterFirstSuccessfulCommit() {
        RecordingScreenHost host = new RecordingScreenHost();
        UiTestFixture fixture = fixtureWith(host);
        fixture.render = () -> null; // 首次渲染失败：不得打开半成品 Screen

        RootUiFacade facade = new RootUiFacade(fixture.hostResources);
        UiRootHandle failed = facade.open(fixture.screen());
        assertEquals(UiRootStatus.CREATED, failed.status());
        assertTrue(host.opened.isEmpty(), "候选失败不得投影原生 Screen");

        fixture.render = () -> UiTestFixture.host(com.tkisor.nekojs.api.ui.UiPrimitive.SCREEN,
                null, UiTestFixture.props("width", 100.0, "height", 50.0));
        UiRootHandle opened = facade.open(fixture.screen());
        assertEquals(UiRootStatus.OPEN, opened.status());
        assertEquals(List.of("open"), host.calls);
        assertTrue(host.isAlive(opened));
    }

    @Test
    void disposeClosesTheScreenExactlyOnceAndIsIdempotent() {
        RecordingScreenHost host = new RecordingScreenHost();
        UiTestFixture fixture = fixtureWith(host);
        fixture.render = () -> UiTestFixture.host(com.tkisor.nekojs.api.ui.UiPrimitive.SCREEN,
                null, UiTestFixture.props("width", 100.0, "height", 50.0));
        RootUiFacade facade = new RootUiFacade(fixture.hostResources);
        UiRootHandle root = facade.open(fixture.screen());

        root.dispose();
        root.dispose();
        root.dispose();

        assertEquals(List.of("open", "close"), host.calls, "重复 dispose 不得重复关闭 Screen");
        assertFalse(host.isAlive(root));
        assertEquals(UiRootStatus.DISPOSED, root.status());
        // host node 与 Screen 释放同一次：每个已创建节点恰好释放一次
        assertEquals(fixture.adapter.creates().size(), fixture.adapter.disposedNodes().size());
    }

    @Test
    void closeGenerationClosesEveryScreenThroughTheSamePath() {
        RecordingScreenHost host = new RecordingScreenHost();
        UiTestFixture fixture = fixtureWith(host);
        fixture.render = () -> UiTestFixture.host(com.tkisor.nekojs.api.ui.UiPrimitive.SCREEN,
                null, UiTestFixture.props("width", 100.0, "height", 50.0));
        RootUiFacade facade = new RootUiFacade(fixture.hostResources);
        UiRootHandle first = facade.open(fixture.screen());
        UiRootHandle second = facade.open(fixture.screen());

        facade.closeGeneration(fixture.generation);
        facade.closeGeneration(fixture.generation);

        assertEquals(List.of("open", "open", "close", "close"), host.calls,
                "generation 关闭必须释放每个 Screen，且重复关闭是 no-op");
        assertFalse(host.isAlive(first));
        assertFalse(host.isAlive(second));
    }

    @Test
    void facadeDisposeClosesScreensAndIsIdempotent() {
        RecordingScreenHost host = new RecordingScreenHost();
        UiTestFixture fixture = fixtureWith(host);
        fixture.render = () -> UiTestFixture.host(com.tkisor.nekojs.api.ui.UiPrimitive.SCREEN,
                null, UiTestFixture.props("width", 100.0, "height", 50.0));
        RootUiFacade facade = new RootUiFacade(fixture.hostResources);
        facade.open(fixture.screen());

        facade.dispose();
        facade.dispose();

        assertEquals(List.of("open", "close"), host.calls);
        assertTrue(facade.roots().isEmpty());
    }

    @Test
    void absentScreenHostIsNotAnErrorForLogicOnlyEnvironments() {
        // 默认 UiResources.screenHost() == null：fake/纯逻辑环境不应因此失败。
        UiTestFixture fixture = new UiTestFixture();
        fixture.render = () -> UiTestFixture.host(com.tkisor.nekojs.api.ui.UiPrimitive.SCREEN,
                null, UiTestFixture.props("width", 10.0, "height", 10.0));
        RootUiFacade facade = new RootUiFacade(fixture.resources());
        UiRootHandle root = facade.open(fixture.screen());

        assertEquals(UiRootStatus.OPEN, root.status());
        root.dispose();
        assertEquals(UiRootStatus.DISPOSED, root.status());
    }
}
