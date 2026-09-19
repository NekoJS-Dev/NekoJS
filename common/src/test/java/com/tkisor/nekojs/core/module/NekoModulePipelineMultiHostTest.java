package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Regression coverage for multiple loader hosts sharing one runtime-owned preparation cache. */
class NekoModulePipelineMultiHostTest {
    @TempDir
    Path gameDir;

    private NekoJSPaths paths;
    private NekoModulePipelineCache cache;
    private HostFixture first;
    private HostFixture second;

    @BeforeEach
    void setUp() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);
        paths = NekoJSPaths.fromGameDir(gameDir);
        Files.createDirectories(paths.serverScripts().resolve("src"));
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, SandboxConfig.defaultConfig()),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
        first = newHost(compilers);
        second = newHost(compilers);
    }

    @AfterEach
    void tearDown() {
        if (first != null) first.close();
        if (second != null) second.close();
        if (cache != null) cache.clear();
    }

    @Test
    void eachSharedCacheHostInvalidatesItsOwnStaticEsmParent() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Path esmChild = dir.resolve("multi-host-child.mjs");
        Path firstParent = dir.resolve("multi-host-first.mjs");
        Path secondParent = dir.resolve("multi-host-second.mjs");
        Files.writeString(esmChild, "export const value = 'AAAA';\n");
        String parent = "import { value as childValue } from './multi-host-child.mjs';\n"
                + "export const value = childValue;\n";
        Files.writeString(firstParent, parent);
        Files.writeString(secondParent, parent);

        assertEquals("AAAA", value(first.host.loadEntry("./server_scripts/src/multi-host-first.mjs")));
        assertEquals("AAAA", value(second.host.loadEntry("./server_scripts/src/multi-host-second.mjs")));

        Files.writeString(esmChild, "export const value = 'BBBB';\n");

        assertEquals("BBBB", value(first.host.loadEntry("./server_scripts/src/multi-host-first.mjs")),
                "the first host must retain its cache observer after the second host is constructed");
        assertEquals("BBBB", value(second.host.loadEntry("./server_scripts/src/multi-host-second.mjs")));
    }

    @Test
    void eachSharedCacheHostInvalidatesItsOwnJsonParent() throws Exception {
        Path dir = paths.serverScripts().resolve("src");
        Path jsonChild = dir.resolve("multi-host-data.json");
        Path firstParent = dir.resolve("multi-host-json-first.mjs");
        Path secondParent = dir.resolve("multi-host-json-second.mjs");
        Files.writeString(jsonChild, "{\"value\":\"AAAA\"}\n");
        String parent = "import data from './multi-host-data.json';\n"
                + "export const value = data.value;\n";
        Files.writeString(firstParent, parent);
        Files.writeString(secondParent, parent);

        assertEquals("AAAA", value(first.host.loadEntry("./server_scripts/src/multi-host-json-first.mjs")));
        assertEquals("AAAA", value(second.host.loadEntry("./server_scripts/src/multi-host-json-second.mjs")));

        Files.writeString(jsonChild, "{\"value\":\"BBBB\"}\n");

        assertEquals("BBBB", value(first.host.loadEntry("./server_scripts/src/multi-host-json-first.mjs")),
                "the first host must retain its cache observer after the second host is constructed");
        assertEquals("BBBB", value(second.host.loadEntry("./server_scripts/src/multi-host-json-second.mjs")));
    }

    private HostFixture newHost(ScriptCompilerRegistry compilers) throws Exception {
        IOAccess ioAccess = IOAccess.newBuilder()
                .fileSystem(new NekoJSFileSystem(paths.root(), new SandboxPolicy(SandboxConfig.defaultConfig(), paths), paths, cache))
                .build();
        Context context = Context.newBuilder("js").allowAllAccess(true).allowIO(ioAccess).build();
        NekoScriptModuleLoaderHost host = new NekoScriptModuleLoaderHost(
                context, new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(),
                        new ScriptFilePolicy(compilers)), cache);
        context.getBindings("js").putMember("__nekoScriptModuleLoaderHost", host);
        try (var in = getClass().getResourceAsStream("/nekojs/node/internal/script-loader.js")) {
            assertNotNull(in, "script-loader.js must be on the test classpath");
            context.eval(Source.newBuilder("js", new String(in.readAllBytes(), StandardCharsets.UTF_8),
                    "nekojs/node/internal/script-loader.js").build());
        }
        return new HostFixture(context, host);
    }

    private static String value(Object exports) {
        return ((Value) exports).getMember("value").asString();
    }

    private record HostFixture(Context context, NekoScriptModuleLoaderHost host) {
        void close() {
            host.close();
            context.close();
        }
    }
}
