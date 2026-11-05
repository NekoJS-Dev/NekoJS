package com.tkisor.nekojs.core.ui;

import graal.graalvm.polyglot.Engine;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinUiRuntimeTest {
    @Test
    void nativeModuleImportsCannotReadUserFiles(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var file = directory.resolve("user-module.mjs");
        java.nio.file.Files.writeString(file, "export default 'user module must not execute';");
        try (var engine = Engine.create("js"); var runtime = new BuiltinUiRuntime(engine, new com.tkisor.nekojs.core.NekoSharedHostAccess(java.util.List.of()).get()); var session = runtime.open(Runnable::run)) {
            session.errorDashboardFactory(new com.tkisor.nekojs.testfixture.I18nTestFacade((key, args) -> key));
            String code = "import value from " + new com.google.gson.Gson().toJson(file.toUri().toString()) + ";export default value;";
            assertThrows(RuntimeException.class, () -> session.context().eval(graal.graalvm.polyglot.Source.newBuilder("js", code,
                    "forbidden-import.mjs").mimeType("application/javascript+module").buildLiteral()));
            assertEquals(42, session.context().eval("js", "21*2").asInt());
        }
    }

    @Test
    void bundledFactoryCompilesWithSharedHostAccessAndWithoutUserFilesOrClassLookup() {
        try (var engine = Engine.create("js"); var runtime = new BuiltinUiRuntime(engine, new com.tkisor.nekojs.core.NekoSharedHostAccess(java.util.List.of()).get()); var session = runtime.open(Runnable::run)) {
            assertTrue(session.errorDashboardFactory(new com.tkisor.nekojs.testfixture.I18nTestFacade((key, args) -> key)).canExecute());
            assertEquals("en_us", session.context().eval("js", "I18n.languageCode").asString());
            assertFalse(session.context().eval("js", "I18n.rightToLeft").asBoolean());
            assertThrows(RuntimeException.class, () -> session.context().eval("js", "I18n.languageCode = 'fr_fr'"));
            assertThrows(RuntimeException.class, () -> session.context().eval("js", "I18n.rightToLeft = true"));
            assertEquals("Fallback 3", session.context().eval("js", "I18n.translateWithFallback('missing', 'Fallback %s', 3)").asString());
            assertEquals("undefined", session.context().eval("js", "typeof globalThis.UI").asString());
            assertEquals("undefined", session.context().eval("js", "typeof globalThis.__nekoJsxFactory").asString());
            assertEquals("undefined", session.context().eval("js", "typeof globalThis.__nekoNodeDefine").asString());
            assertThrows(RuntimeException.class, () -> session.context().eval("js", "Java.type('java.lang.System')"));
        }
    }

    @Test
    void guestCloseUnwindsBeforeReleasingTheContext() {
        Queue<Runnable> queue = new ArrayDeque<>();
        AtomicInteger cleanup = new AtomicInteger();
        try (var engine = Engine.create("js"); var runtime = new BuiltinUiRuntime(engine, new com.tkisor.nekojs.core.NekoSharedHostAccess(java.util.List.of()).get())) {
            var session = runtime.open(queue::add);
            session.beforeRelease(cleanup::incrementAndGet);
            session.context().getBindings("js").putMember("closeUi", (ProxyExecutable) args -> { session.close(); return null; });
            assertEquals(42, session.context().eval("js", "closeUi(); 42").asInt());
            assertTrue(session.isClosed());
            assertEquals(0, cleanup.get());
            queue.remove().run();
            assertEquals(1, cleanup.get());
            assertThrows(RuntimeException.class, () -> session.context().eval("js", "42"));
            session.close();
            assertEquals(1, cleanup.get());
        }
    }

    @Test
    void offThreadShutdownCancelsGuestWorkAndQueuesNativeCleanup() throws Exception {
        Queue<Runnable> queue = new java.util.concurrent.ConcurrentLinkedQueue<>();
        AtomicInteger cleanup = new AtomicInteger();
        var engine = Engine.create("js");
        var runtime = new BuiltinUiRuntime(engine, new com.tkisor.nekojs.core.NekoSharedHostAccess(java.util.List.of()).get());
        var session = runtime.open(queue::add);
        session.beforeRelease(cleanup::incrementAndGet);
        var closer = new Thread(runtime::close);
        closer.start(); closer.join(5000);
        assertFalse(closer.isAlive());
        assertTrue(session.isClosed());
        assertEquals(0, cleanup.get());
        assertThrows(RuntimeException.class, () -> session.context().eval("js", "42"));
        queue.remove().run();
        assertEquals(1, cleanup.get());
        assertThrows(IllegalStateException.class, () -> runtime.open(queue::add));
        engine.close();
    }

    @Test
    void oneSessionFailureDoesNotInvalidateAnotherSession() {
        try (var engine = Engine.create("js"); var runtime = new BuiltinUiRuntime(engine, new com.tkisor.nekojs.core.NekoSharedHostAccess(java.util.List.of()).get());
             var first = runtime.open(Runnable::run); var second = runtime.open(Runnable::run)) {
            assertThrows(RuntimeException.class, () -> first.context().eval("js", "throw new Error('fixture failure')"));
            first.close();
            assertEquals(42, second.context().eval("js", "21*2").asInt());
        }
    }
}
