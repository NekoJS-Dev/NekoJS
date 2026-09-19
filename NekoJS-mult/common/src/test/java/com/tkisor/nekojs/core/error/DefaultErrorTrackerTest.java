package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoTrustContext;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Source;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.lang.reflect.Modifier;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DefaultErrorTracker#recordCallbackError} 回归测试：
 * 同一回调错误必须复用已存储的 {@link ScriptError} 实例并递增频次（去重先于解析，
 * 避免高频回调反复重建 ScriptError 并重读源码文件），不同签名则更新或新增记录。
 */
class DefaultErrorTrackerTest {

    private DefaultErrorTracker tracker;

    @BeforeAll
    static void bindPaths() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void newTracker() {
        tracker = new DefaultErrorTracker(NekoJSPaths.get(), SandboxConfig.defaultConfig());
    }

    private static NekoEsmLinkException esmError(int line, int column, String message) {
        return new NekoEsmLinkException(new NekoEsmDiagnostic(null, null, line, column, message));
    }

    @Test
    void repeatedCallbackErrorReusesStoredInstanceAndIncrementsCount() {
        tracker.recordCallbackError(ScriptType.SERVER, "timer", esmError(10, 5, "boom"));

        ScriptError first = singleError();

        tracker.recordCallbackError(ScriptType.SERVER, "timer", esmError(10, 5, "boom"));
        tracker.recordCallbackError(ScriptType.SERVER, "timer", esmError(10, 5, "boom"));

        ScriptError stored = singleError();
        assertSame(first, stored, "重复错误必须复用同一实例（不得重新解析异常/读取源码文件）");
        assertEquals(3, stored.getOccurrenceCount(), "每次出现都必须计入频次");
        assertEquals("boom", stored.getErrorMessage());
        assertEquals(10, stored.getLineNumber());
        assertEquals(5, stored.getColumnNumber());
        assertTrue(stored.getLogDetailText(true).contains("连续发生了 3 次"),
                "连续重复计数必须体现在日志明细中");
    }

    @Test
    void differentPositionWithSameMessageUpdatesStoredError() {
        tracker.recordCallbackError(ScriptType.SERVER, "timer", esmError(10, 5, "boom"));
        tracker.recordCallbackError(ScriptType.SERVER, "timer", esmError(11, 5, "boom"));

        ScriptError stored = singleError();
        assertEquals(11, stored.getLineNumber(), "位置变化后应更新同一条记录");
        assertEquals(1, stored.getOccurrenceCount(), "新位置错误是更新而非重复，频次应重置为 1");
    }

    @Test
    void differentMessageCreatesSeparateEntry() {
        tracker.recordCallbackError(ScriptType.SERVER, "timer", esmError(10, 5, "boom"));
        tracker.recordCallbackError(ScriptType.SERVER, "timer", esmError(10, 5, "bang"));

        assertEquals(2, tracker.getErrorCount(), "不同错误信息应各自记录");
    }

    @Test
    void registryObservationsArePackagePrivateReadOnlyViews() throws Exception {
        var sourceMaps = DefaultErrorTracker.class.getDeclaredMethod("sourceMaps");
        var virtualModules = DefaultErrorTracker.class.getDeclaredMethod("virtualModules");

        assertFalse(Modifier.isPublic(sourceMaps.getModifiers()));
        assertFalse(Modifier.isPublic(virtualModules.getModifiers()));
        assertEquals(NekoSourceMapView.class, sourceMaps.getReturnType());
        assertEquals(com.tkisor.nekojs.core.module.NekoVirtualModuleView.class,
                virtualModules.getReturnType());
    }

    @Test
    void moduleViewsAreScopedByTypeAndCandidateCommit() {
        NekoJSPaths paths = NekoJSPaths.get();
        NekoModulePipelineCache server = newCache(paths);
        NekoModulePipelineCache client = newCache(paths);
        NekoModulePipelineCache candidate = newCache(paths);
        try (Context serverContext = Context.newBuilder("js").allowAllAccess(true).build();
             Context clientContext = Context.newBuilder("js").allowAllAccess(true).build();
             Context candidateContext = Context.newBuilder("js").allowAllAccess(true).build()) {
            tracker.activateModuleViews(ScriptType.SERVER, serverContext, server);
            tracker.activateModuleViews(ScriptType.CLIENT, clientContext, client);
            DefaultErrorTracker.ModuleViews serverActive = tracker.moduleViews(ScriptType.SERVER);
            DefaultErrorTracker.ModuleViews clientActive = tracker.moduleViews(ScriptType.CLIENT);

            tracker.activateCandidateModuleViews(ScriptType.SERVER, candidateContext, candidate);
            DefaultErrorTracker.ModuleViews clientContextActive = tracker.moduleViews(clientContext, ScriptType.CLIENT);
            assertNotNull(clientContextActive);
            assertNotSame(clientActive, clientContextActive,
                    "Context-bound view should be distinct from the type fallback object");
            assertSame(clientContextActive, tracker.moduleViews(clientContext, ScriptType.CLIENT),
                    "a SERVER candidate must not replace CLIENT active views");
            assertSame(serverActive, tracker.moduleViews(ScriptType.SERVER),
                    "candidate registration must not replace the SERVER active fallback");
            assertNotSame(serverActive, tracker.moduleViews(candidateContext, ScriptType.SERVER));

            tracker.discardCandidateModuleViews(candidateContext);
            assertSame(serverActive, tracker.moduleViews(ScriptType.SERVER),
                    "discarding a candidate must restore that type's active views");

            tracker.activateCandidateModuleViews(ScriptType.SERVER, candidateContext, candidate);
            tracker.activateModuleViews(ScriptType.SERVER, candidateContext, candidate);
            assertNotSame(serverActive, tracker.moduleViews(ScriptType.SERVER));
            assertSame(clientActive, tracker.moduleViews(ScriptType.CLIENT));
        } finally {
            server.closeOwner();
            client.closeOwner();
            candidate.closeOwner();
        }
    }

    @Test
    void callbackDiagnosticsUseTheOwningContextSessionView() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        SourceMapRegistry activeMaps = new SourceMapRegistry(paths.root());
        SourceMapRegistry candidateMaps = new SourceMapRegistry(paths.root());
        NekoEsmVirtualModuleRegistry activeVirtuals = new NekoEsmVirtualModuleRegistry(paths.root());
        NekoEsmVirtualModuleRegistry candidateVirtuals = new NekoEsmVirtualModuleRegistry(paths.root());
        NekoModulePipelineCache active = newCache(paths, activeMaps, activeVirtuals);
        NekoModulePipelineCache candidate = newCache(paths, candidateMaps, candidateVirtuals);
        try (Context activeContext = Context.newBuilder("js").allowAllAccess(true).build();
             Context candidateContext = Context.newBuilder("js").allowAllAccess(true).build()) {
            activeMaps.register("server_scripts/context-callback.js",
                    sourceMap("server_scripts/active-authored.ts", "active"));
            candidateMaps.register("server_scripts/context-callback.js",
                    sourceMap("server_scripts/candidate-authored.ts", "candidate"));
            tracker.activateModuleViews(ScriptType.SERVER, activeContext, active);
            tracker.activateCandidateModuleViews(ScriptType.SERVER, candidateContext, candidate);

            tracker.recordCallbackError(activeContext, ScriptType.SERVER, "event", throwingPolyglot(
                    activeContext, "server_scripts/context-callback.js"));
            ScriptError activeError = tracker.getAllErrors().iterator().next();
            assertEquals("server_scripts/active-authored.ts", activeError.getDisplayPath(),
                    "an active callback must retain the active session map during candidate execution");

            tracker.clearAll();
            tracker.recordCallbackError(candidateContext, ScriptType.SERVER, "event", throwingPolyglot(
                    candidateContext, "server_scripts/context-callback.js"));
            assertEquals(0, tracker.getErrorCount(), "candidate diagnostics must stay out of the public count");
            assertTrue(tracker.getAllErrors().isEmpty(), "candidate diagnostics must stay out of the public snapshot");

            tracker.publishCandidateErrors(ScriptType.SERVER, candidateContext);
            ScriptError candidateError = tracker.getAllErrors().iterator().next();
            assertEquals("server_scripts/candidate-authored.ts", candidateError.getDisplayPath(),
                    "published candidate diagnostics must use only the candidate Context session map");
        } finally {
            active.closeOwner();
            candidate.closeOwner();
        }
    }

    @Test
    void candidateErrorsRemainHiddenFromPublicDiagnosticsUntilCommit() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        NekoModulePipelineCache candidate = newCache(paths);
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            tracker.activateCandidateModuleViews(ScriptType.SERVER, context, candidate);

            tracker.recordCallbackError(context, ScriptType.SERVER, "candidate", esmError(3, 2, "candidate-only"));

            assertEquals(0, tracker.getErrorCount());
            assertTrue(tracker.getAllErrors().isEmpty());
            assertEquals(0, com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot.ErrorSnapshot.of(tracker).count());

            tracker.publishCandidateErrors(ScriptType.SERVER, context);
            assertEquals(1, tracker.getErrorCount());
            assertEquals("candidate-only", tracker.getAllErrors().iterator().next().getErrorMessage());
        } finally {
            candidate.closeOwner();
        }
    }

    @Test
    void failedCandidateErrorsAreDiscardedAndActiveSnapshotRemainsVisible() throws Exception {
        tracker.recordCallbackError(ScriptType.SERVER, "active", esmError(1, 1, "active-only"));
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            NekoModulePipelineCache candidate = newCache(NekoJSPaths.get());
            try {
                tracker.activateCandidateModuleViews(ScriptType.SERVER, context, candidate);
                tracker.recordCallbackError(context, ScriptType.SERVER, "candidate", esmError(2, 1, "candidate-only"));

                assertEquals(1, tracker.getErrorCount(),
                        "candidate failures must not change the active error count");
                assertEquals("active-only", tracker.getAllErrors().iterator().next().getErrorMessage());

                tracker.discardCandidateModuleViews(context);
                assertEquals(1, tracker.getErrorCount(),
                        "discarding a failed candidate must preserve the active snapshot");
                assertEquals("active-only", tracker.getAllErrors().iterator().next().getErrorMessage());
            } finally {
                candidate.closeOwner();
            }
        }
    }

    @Test
    void freshPolyglotExceptionsWithSameSourcePositionDeduplicate() {
        tracker.recordCallbackError(ScriptType.SERVER, "event", throwingPolyglot());
        tracker.recordCallbackError(ScriptType.SERVER, "event", throwingPolyglot());

        ScriptError stored = singleError();
        assertEquals(2, stored.getOccurrenceCount(), "相同源码位置的重复 Polyglot 异常必须去重并递增频次");
    }

    @Test
    void virtualTruffleUriDoesNotBecomeARealFilePath() throws Exception {
        Source source = Source.newBuilder("js", "throw new Error('x');", "internal/script-loader.js")
                .uri(URI.create("truffle:module/nekojs/node/internal/script-loader.js"))
                .build();

        String path = tracker.extractRelativePath(source);
        assertTrue(path.startsWith("truffle:"), path);
        assertEquals(7, tracker.getRealCodeLine(path, 7));
    }

    /**
     * 票 03 基线发现（归 19/07）：WORLD 包脚本位于存档侧（不在 {@code <gamedir>/nekojs}
     * root 下，Windows 相对 world 路径必现），{@code record} 的 relativize 曾在
     * executeEntry 的 catch 体内抛 IAE——二次异常掩掉原始 kill/执行错误且错误面板丢条目。
     * 票 07 起 record 位于 watchdog 终止的失败可观察路径上：必须回退为原样路径文本
     * 而不是抛出（kill 归因与错误面板条目都以本方法不抛为前提）。
     */
    @Test
    void recordOfWorldPackScriptOutsideRootDoesNotThrow() {
        Path worldPackScript = NekoJSPaths.get().root().resolveSibling(
                "saves/world/nekojs_packs/demo/server_scripts/entry.js");
        com.tkisor.nekojs.script.ScriptContainer worldPackContainer =
                new com.tkisor.nekojs.script.ScriptContainer(
                        com.tkisor.nekojs.api.data.ScriptId.of("nekojs", "server_scripts/worldpacks/demo/entry.js"),
                        ScriptType.SERVER,
                        worldPackScript,
                        new com.tkisor.nekojs.script.prop.ScriptPropertyRegistry.Impl());

        RuntimeException originalError = new RuntimeException("killed by watchdog");
        ScriptError recorded = assertDoesNotThrow(() -> tracker.record(worldPackContainer, originalError),
                "record must not throw for WORLD-pack scripts outside the nekojs root");

        assertNotNull(tracker.get(worldPackContainer.id), "error panel must keep the entry");
        assertEquals(worldPackScript, recorded.getScript().path, "script path must be preserved as-is");
    }

    @Test
    void shouldLogOccurrenceFollowsMilestoneSchedule() {
        assertTrue(DefaultErrorTracker.shouldLogOccurrence(true, 0, 1),
                "首次出现（新建错误）必须记录日志");
        assertTrue(DefaultErrorTracker.shouldLogOccurrence(false, 1, 2),
                "1→2 跨越里程碑 2");
        assertFalse(DefaultErrorTracker.shouldLogOccurrence(false, 2, 3),
                "2→3 未跨越任何里程碑");
        assertTrue(DefaultErrorTracker.shouldLogOccurrence(false, 4, 5),
                "4→5 跨越里程碑 5");
        assertTrue(DefaultErrorTracker.shouldLogOccurrence(false, 9, 10),
                "9→10 跨越里程碑 10");
        assertFalse(DefaultErrorTracker.shouldLogOccurrence(false, 10, 11),
                "10→11 未跨越任何里程碑");
        assertTrue(DefaultErrorTracker.shouldLogOccurrence(false, 24, 25),
                "24→25 跨越里程碑 25");
        assertTrue(DefaultErrorTracker.shouldLogOccurrence(false, 49, 50),
                "49→50 跨越里程碑 50");
        assertFalse(DefaultErrorTracker.shouldLogOccurrence(false, 50, 51),
                "50→51 未跨越里程碑 100");
        assertTrue(DefaultErrorTracker.shouldLogOccurrence(false, 99, 100),
                "99→100 跨越里程碑 100");
        assertFalse(DefaultErrorTracker.shouldLogOccurrence(false, 100, 199),
                "100→199 未跨越里程碑 200");
        assertTrue(DefaultErrorTracker.shouldLogOccurrence(false, 199, 200),
                "199→200 跨越里程碑 200");
        assertTrue(DefaultErrorTracker.shouldLogOccurrence(false, 399, 400),
                "399→400 跨越里程碑 400");
        assertFalse(DefaultErrorTracker.shouldLogOccurrence(false, 400, 799),
                "400→799 未跨越里程碑 800");
        assertTrue(DefaultErrorTracker.shouldLogOccurrence(false, 799, 800),
                "799→800 跨越里程碑 800");
    }

    /** 每次执行都抛出一个全新的 {@link PolyglotException}，模拟 20Hz tick 回调的重复错误。 */
    private PolyglotException throwingPolyglot() {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.eval(Source.newBuilder("js", "function tick() {\n  throw new Error('tick failure');\n}\ntick();\n", "tick.js").build());
            throw new AssertionError("expected PolyglotException");
        } catch (PolyglotException e) {
            return e;
        } catch (Exception e) {
            throw new AssertionError("failed to build throwing source", e);
        }
    }

    private static PolyglotException throwingPolyglot(Context context, String sourceName) throws Exception {
        try {
            context.eval(Source.newBuilder("js", "throw new Error('context failure');", sourceName).build());
            throw new AssertionError("expected PolyglotException");
        } catch (PolyglotException e) {
            return e;
        }
    }

    private static String sourceMap(String sourcePath, String content) {
        return "{\"version\":3,\"sources\":[\"" + sourcePath
                + "\"],\"sourcesContent\":[\"" + content
                + "\"],\"names\":[],\"mappings\":\"AAAA\"}";
    }

    private ScriptError singleError() {
        assertEquals(1, tracker.getErrorCount(), "应只保留一条错误记录");
        return tracker.getAllErrors().iterator().next();
    }

    private static NekoModulePipelineCache newCache(NekoJSPaths paths) {
        return newCache(paths, new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()));
    }

    private static NekoModulePipelineCache newCache(NekoJSPaths paths, SourceMapRegistry sourceMaps,
                                                     NekoEsmVirtualModuleRegistry virtualModules) {
        return new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(),
                        ScriptCompilerRegistry.createRuntimeRegistry(), SandboxConfig.defaultConfig()),
                sourceMaps, virtualModules, NekoTrustContext.local());
    }
}
