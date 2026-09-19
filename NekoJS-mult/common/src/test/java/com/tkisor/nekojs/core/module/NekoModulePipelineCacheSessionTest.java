package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Candidate module sessions must be disposable without changing the active session. */
class NekoModulePipelineCacheSessionTest {
    @TempDir
    Path gameDir;

    @Test
    void discardedCandidateKeepsActivePreparedMapAndVirtualSource() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);
        NekoJSPaths paths = NekoJSPaths.fromGameDir(gameDir);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        NekoModulePipelineCache owner = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, SandboxConfig.defaultConfig()),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
        Path module = paths.serverScripts().resolve("session-probe.mjs");
        Files.createDirectories(module.getParent());
        Files.writeString(module, "export const value = 'old';\n");

        NekoModulePipelineCache active = owner.openSession();
        active.prepare(module);
        Path virtual = Path.of(active.virtualModules().register(
                "server_scripts/session-probe.mjs", "export const value = 'old-virtual';\n"));
        active.sourceMaps().register("server_scripts/session-probe.mjs",
                sourceMap("server_scripts/session-probe.mjs", "old-map"));

        Files.writeString(module, "export const value = 'new';\n");
        NekoModulePipelineCache candidate = owner.openSession();
        try {
            assertTrue(candidate.prepare(module).code().contains("new"));
            Path candidateVirtual = Path.of(candidate.virtualModules().register(
                    "server_scripts/session-probe.mjs", "export const value = 'new-virtual';\n"));
            candidate.sourceMaps().register("server_scripts/session-probe.mjs",
                    sourceMap("server_scripts/session-probe.mjs", "new-map"));

            assertTrue(candidate.virtualModules().source(candidateVirtual).contains("new-virtual"));
            assertEquals("new-map", candidate.sourceMaps()
                    .getMappedPosition("server_scripts/session-probe.mjs", 1, 1).sourceContent);
        } finally {
            candidate.closeSession();
        }

        assertEquals("export const value = 'old-virtual';\n", active.virtualModules().source(virtual));
        assertEquals("old-map", active.sourceMaps()
                .getMappedPosition("server_scripts/session-probe.mjs", 1, 1).sourceContent);

        // Restore the file so the active cache hit is observable independently of the candidate stamp.
        Files.writeString(module, "export const value = 'old';\n");
        assertTrue(active.prepare(module).code().contains("old"));
        assertEquals(1, owner.preparedEntryCount(), "closing a candidate removes only its entries");

        owner.clear();
        assertEquals(0, owner.preparedEntryCount(), "root close/clear releases child sessions");
        NekoModulePipelineCache afterClear = owner.openSession();
        afterClear.closeSession();

        owner.close();
        assertThrows(IllegalStateException.class, owner::openSession,
                "a closed root must reject new generation sessions");
    }

    @Test
    void sessionsKeepExplicitSchemaViewsAndChildrenCannotCreateOwners() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);
        NekoJSPaths paths = NekoJSPaths.fromGameDir(gameDir);
        NekoModulePipelineCache owner = newCache(paths);
        owner.installActiveBindingSchema(ScriptType.SERVER,
                Map.of("ActiveBinding", new ScriptBindingSchema.BindingMembers(Set.of("active"))), Set.of());
        NekoModulePipelineCache unbound = owner.openSession();
        NekoModulePipelineCache active = owner.openSession(owner.activeBindingSchemaView(ScriptType.SERVER));
        AtomicInteger diagnostics = new AtomicInteger();
        ScriptBindingSchema.View candidateView = new ScriptBindingSchema.View(
                Map.of("CandidateBinding", new ScriptBindingSchema.BindingMembers(Set.of("candidate"))),
                Set.of("candidateGlobal"), diagnostic -> diagnostics.incrementAndGet());
        NekoModulePipelineCache candidate = owner.openSession(ScriptBindingSchema.emptyView());
        try {
            assertTrue(unbound.bindingSchemaView().lookup().isEmpty(),
                    "a session without an explicit generation view must start empty");
            assertTrue(active.bindingSchemaView().lookup().containsKey("ActiveBinding"));
            assertTrue(candidate.bindingSchemaView().lookup().isEmpty(),
                    "candidate preparation before binding installation must not read active schema");

            candidate.installBindingSchemaView(candidateView);
            assertEquals(candidateView, candidate.bindingSchemaView());
            assertThrows(IllegalStateException.class, candidate::openSession,
                    "a child session must not create another runtime owner");
            assertFalse(Modifier.isPublic(NekoModulePipelineCache.class
                    .getDeclaredMethod("closeOwner").getModifiers()),
                    "owner close must not be a public child-cache API");
        } finally {
            unbound.close();
            active.close();
            candidate.close();
            owner.close();
        }
    }

    private static String sourceMap(String source, String content) {
        return "{\"version\":3,\"sources\":[\"" + source + "\"],"
                + "\"sourcesContent\":[\"" + content + "\"],\"names\":[],\"mappings\":\"AAAA\"}";
    }

    private static NekoModulePipelineCache newCache(NekoJSPaths paths) {
        return new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(),
                        ScriptCompilerRegistry.createRuntimeRegistry(), SandboxConfig.defaultConfig()),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
    }
}
