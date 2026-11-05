//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.google.gson.Gson;
import graal.graalvm.polyglot.Engine;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.ui.BuiltinUiRuntime;
import com.tkisor.nekojs.network.ErrorSummaryDTO;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NativeBuiltinErrorDashboardTest {
    private static final Gson JSON = new Gson();

    @Test
    void iconStrokesStayVisibleSeparatedAndCenteredInSmallViewports() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture();
             Engine engine = Engine.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build();
             BuiltinUiRuntime runtime = new BuiltinUiRuntime(engine, new NekoSharedHostAccess(List.of()).get());
             var session = runtime.open(Runnable::run)) {
            fixture.prepareBuiltinHost();
            JsxHostAdapter host = JsxHostAdapter.builtin(session, 256, 144, failure -> fail(failure));
            session.beforeRelease(host::dispose);
            Value factory = session.errorDashboardFactory(new com.tkisor.nekojs.bindings.static_access.I18nJS());
            Value view = factory.execute(BuiltinUiHostBridge.host(host), bridge(new AtomicReference<>(List.of()),
                    new AtomicReference<>(), new ArrayList<>()), session.context().eval("js", "[]"));
            host.bindRoot(view.getMember("root"));
            for (int[] size : new int[][] { {256, 144}, {384, 216}, {854, 499}, {1280, 720} }) {
                host.resize(size[0], size[1]);
                var fills = fixture.paintHostFrame(host).fills();
                var strokes = fills.stream().filter(fill -> fill.color() == 0xFF80B9FF).toList();
                assertEquals(2, strokes.size(), "visible icon strokes at " + size[0] + "x" + size[1]);
                var frame = fills.stream().filter(fill -> fill.color() == 0xFF26394F).findFirst().orElseThrow();
                for (var stroke : strokes) {
                    assertTrue(stroke.right() > stroke.left() && stroke.bottom() > stroke.top());
                    assertEquals(frame.left() + frame.right(), stroke.left() + stroke.right(), 1);
                }
                assertTrue(strokes.getFirst().bottom() < strokes.getLast().top(), "stem and dot must remain separated");
                assertEquals(frame.top() + frame.bottom(), strokes.getFirst().top() + strokes.getLast().bottom(), 1);
            }
        }
    }

    @Test
    void realScreenRoutingCopiesOriginalDataAndDoesNotReopenAfterClose() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture();
             Engine engine = Engine.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build();
             BuiltinUiRuntime runtime = new BuiltinUiRuntime(engine, new NekoSharedHostAccess(List.of()).get())) {
            fixture.installDashboardScreens();
            AtomicReference<BuiltinUiRuntime.Session> session = new AtomicReference<>();
            com.tkisor.nekojs.client.gui.ErrorDashboardScreens.install(() -> {
                var created = runtime.open(net.minecraft.client.Minecraft.getInstance()::schedule);
                session.set(created); return created;
            }, fixture.dashboardScreens);
            String raw = "original\r\n\t§a\0literal";
            var errors = List.of(new ErrorSummaryDTO("real", "server_scripts/real.js", 1, 1, "Real error", raw));
            com.tkisor.nekojs.client.gui.ErrorDashboardScreens.showOrUpdate(errors, true);
            assertInstanceOf(BuiltinErrorDashboard.class, fixture.dashboardScreens.currentScreen());
            var screen = (BuiltinErrorDashboard) fixture.dashboardScreens.currentScreen();
            var host = host(screen);
            assertFalse(host.roots().isEmpty());
            AtomicReference<String> copied = new AtomicReference<>();
            screen.clipboard(new JsxHostAdapter.Clipboard() {
                public String read() { return copied.get(); }
                public void write(String value) { copied.set(value); }
            });
            var button = find(host.roots(), "dashboard-copy-full");
            assertTrue(host.dispatchAt(button.x + 2, button.y + 2, "click", 0, 0));
            assertEquals(raw, copied.get());
            screen.clipboard(new JsxHostAdapter.Clipboard() {
                public String read() { return "not confirmed"; }
                public void write(String value) { }
            });
            host.dispatchAt(button.x + 2, button.y + 2, "click", 0, 0);
            assertNotNull(find(host.roots(), "dashboard-dialog"));
            screen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0));
            assertNull(find(host.roots(), "dashboard-dialog"));
            assertSame(screen, fixture.dashboardScreens.currentScreen());
            screen.onClose();
            assertNull(fixture.dashboardScreens.currentScreen());
            assertTrue(session.get().isClosed());
            assertTrue(host.roots().isEmpty());
            com.tkisor.nekojs.client.gui.ErrorDashboardScreens.showOrUpdate(errors, false);
            assertNull(fixture.dashboardScreens.currentScreen());
            fixture.flushClientTasks();
            assertNull(fixture.dashboardScreens.currentScreen());
            com.tkisor.nekojs.client.gui.ErrorDashboardScreens.showOrUpdate(List.of(), false);
            assertNull(fixture.dashboardScreens.currentScreen());
            com.tkisor.nekojs.client.gui.ErrorDashboardScreens.showOrUpdate(List.of(), true);
            var emptyScreen = assertInstanceOf(BuiltinErrorDashboard.class, fixture.dashboardScreens.currentScreen());
            var emptyHost = host(emptyScreen);
            assertNotNull(find(emptyHost.roots(), "dashboard-empty"));
            assertNotNull(find(emptyHost.roots(), "dashboard-search"));
            assertNull(find(emptyHost.roots(), "dashboard-copy-full"));
            emptyScreen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0));
            fixture.flushClientTasks();
            assertNull(fixture.dashboardScreens.currentScreen());
            assertTrue(session.get().isClosed());
        }
    }

    @Test
    void aBrokenGuestFallsBackOnceAndRootShutdownJustClosesTheScreen() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture();
             Engine engine = Engine.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build();
             BuiltinUiRuntime runtime = new BuiltinUiRuntime(engine, new NekoSharedHostAccess(List.of()).get())) {
            fixture.installDashboardScreens();
            AtomicReference<BuiltinUiRuntime.Session> session = new AtomicReference<>();
            com.tkisor.nekojs.client.gui.ErrorDashboardScreens.install(() -> {
                var created = runtime.open(net.minecraft.client.Minecraft.getInstance()::schedule);
                session.set(created); return created;
            }, fixture.dashboardScreens);
            var errors = List.of(new ErrorSummaryDTO("failure", "virtual", -1, 1, "Error", "Original"));
            com.tkisor.nekojs.client.gui.ErrorDashboardScreens.showOrUpdate(errors, true);
            var screen = (BuiltinErrorDashboard) fixture.dashboardScreens.currentScreen();
            var host = host(screen);
            session.get().context().close(true);
            var button = find(host.roots(), "dashboard-copy-full");
            host.dispatchAt(button.x + 2, button.y + 2, "click", 0, 0);
            com.tkisor.nekojs.client.gui.ErrorDashboardScreens.showOrUpdate(
                    List.of(new ErrorSummaryDTO("latest", "virtual", -1, 2, "Latest error", "Latest details")), false);
            fixture.flushClientTasks();
            var fallback = fixture.dashboardScreens.currentScreen();
            assertInstanceOf(com.tkisor.nekojs.client.gui.NekoErrorDashboardScreen.class, fallback);
            var modelField = com.tkisor.nekojs.client.gui.AbstractErrorDashboardScreen.class.getDeclaredField("model");
            modelField.setAccessible(true);
            var fallbackModel = (com.tkisor.nekojs.core.error.ErrorDashboardModel) modelField.get(fallback);
            assertEquals("Latest details", fallbackModel.visibleErrors().getFirst().fullDetails());
            assertTrue(session.get().isClosed());
            assertTrue(host.roots().isEmpty());
            com.tkisor.nekojs.client.gui.ErrorDashboardScreens.showOrUpdate(List.of(), false);
            assertSame(fallback, fixture.dashboardScreens.currentScreen());
            fixture.dashboardScreens.showScreen(null);
            com.tkisor.nekojs.client.gui.ErrorDashboardScreens.showOrUpdate(errors, true);
            var next = (BuiltinErrorDashboard) fixture.dashboardScreens.currentScreen();
            runtime.close();
            next.mouseScrolled(2, 2, 0, -1);
            fixture.flushClientTasks();
            assertNull(fixture.dashboardScreens.currentScreen());
        }
    }

    @Test
    void productionFactoryUsesRealHostAndRetainsSearchSelectionAndScrollOnSnapshotUpdate() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture();
             Engine engine = Engine.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build();
             BuiltinUiRuntime runtime = new BuiltinUiRuntime(engine, new NekoSharedHostAccess(List.of()).get());
             var session = runtime.open(Runnable::run)) {
            fixture.prepareBuiltinHost();
            List<RuntimeException> failures = new ArrayList<>();
            JsxHostAdapter host = JsxHostAdapter.builtin(session, 854, 499, failures::add);
            session.beforeRelease(host::dispose);
            AtomicReference<String> copied = new AtomicReference<>();
            List<String> actions = new ArrayList<>();
            String raw = "Original snapshot\n" + "Raw detail\n".repeat(160) + "    at lastFrame (a.js:42)";
            AtomicReference<List<ErrorSummaryDTO>> errors = new AtomicReference<>(List.of(
                    new ErrorSummaryDTO("a", "server_scripts/a.js", 42, 1, "Error A", raw),
                    new ErrorSummaryDTO("b", "server_scripts/b.js", 3, 2, "Error B", "body B")));
            var factory = session.errorDashboardFactory(new com.tkisor.nekojs.bindings.static_access.I18nJS());
            Value parse = session.context().eval("js", "JSON.parse");
            Value view = factory.execute(BuiltinUiHostBridge.host(host), bridge(errors, copied, actions), parse.execute(JSON.toJson(errors.get())));
            host.bindRoot(view.getMember("root"));
            assertTrue(failures.isEmpty(), failures::toString);
            assertNotNull(find(host.roots(), "dashboard-sidebar"));
            assertNull(find(host.roots(), "dashboard-prototype-controls"));
            assertEquals("Error A", find(host.roots(), "dashboard-message").props.get("text"));
            view.getMember("root").invokeMember("dispatch", "dashboard-search", "change", ProxyObject.fromMap(Map.of("value", "a.js")));
            var rawScroll = find(host.roots(), "dashboard-raw-scroll");
            assertTrue(host.dispatchScroll(rawScroll.x + 2, rawScroll.y + 2, -2));
            double before = ((Number) find(host.roots(), "dashboard-raw-scroll").props.get("scrollOffset")).doubleValue();
            assertTrue(before > 0);
            errors.set(List.of(new ErrorSummaryDTO("a", "server_scripts/a.js", 42, 4, "Updated A", raw)));
            view.invokeMember("update", parse.execute(JSON.toJson(errors.get())));
            assertEquals("a.js", find(host.roots(), "dashboard-search").inputValue);
            assertEquals("Updated A", find(host.roots(), "dashboard-message").props.get("text"));
            assertEquals(before, ((Number) find(host.roots(), "dashboard-raw-scroll").props.get("scrollOffset")).doubleValue(), 0.000001);
            view.getMember("root").invokeMember("dispatch", "dashboard-copy-full", "click", ProxyObject.fromMap(Map.of("button", 0)));
            assertEquals(raw, copied.get());
            view.invokeMember("locationResult", "a", true, "location_ready");
            view.getMember("root").invokeMember("dispatch", "dashboard-locate", "click", ProxyObject.fromMap(Map.of("button", 0)));
            view.getMember("root").invokeMember("dispatch", "dashboard-locate", "click", ProxyObject.fromMap(Map.of("button", 0)));
            assertEquals(1, actions.stream().filter("open:a"::equals).count());
            host.resize(427, 250);
            assertEquals(427, host.roots().getFirst().width);
            assertEquals(111, find(host.roots(), "dashboard-sidebar").width);
            assertEquals("a.js", find(host.roots(), "dashboard-search").inputValue);
            fixture.globals.close();
            assertFalse(session.isClosed(), "user generation cleanup must not own the bundled UI");
            assertTrue(fixture.paintHost(host).stream().anyMatch(draw -> draw.text().contains("Updated A")));
            session.close();
            assertTrue(host.roots().isEmpty());
            assertThrows(IllegalStateException.class, () -> host.dispatchScroll(5, 5, -1));
            assertTrue(failures.isEmpty(), failures::toString);
        }
    }

    private static ProxyObject bridge(AtomicReference<List<ErrorSummaryDTO>> errors,
            AtomicReference<String> copied, List<String> actions) {
        return ProxyObject.fromMap(Map.of(
                "translate", (ProxyExecutable) args -> args[0].asString(),
                "checkLocation", (ProxyExecutable) args -> { actions.add("check:" + args[0]); return null; },
                "openSource", (ProxyExecutable) args -> { actions.add("open:" + args[0].asString()); return null; },
                "copy", (ProxyExecutable) args -> {
                    var error = errors.get().stream().filter(row -> row.id().equals(args[0].asString())).findFirst().orElseThrow();
                    copied.set(args[1].asBoolean() ? error.fullDetails() : error.path()); return true;
                }));
    }

    private static JsxHostTree.Node find(List<JsxHostTree.Node> roots, String id) {
        for (JsxHostTree.Node node : roots) {
            if (id.equals(node.props.get("id"))) return node;
            var found = find(node.children, id);
            if (found != null) return found;
        }
        return null;
    }

    private static JsxHostAdapter host(BuiltinErrorDashboard screen) throws Exception {
        var field = BuiltinErrorDashboard.class.getDeclaredField("host");
        field.setAccessible(true);
        return (JsxHostAdapter) field.get(screen);
    }
}
//?}
