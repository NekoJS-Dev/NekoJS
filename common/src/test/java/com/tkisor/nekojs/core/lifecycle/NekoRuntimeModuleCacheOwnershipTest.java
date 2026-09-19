package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.NekoSharedEngine;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.module.NekoTrustContext;
import com.tkisor.nekojs.core.module.NekoModuleResolver;
import com.tkisor.nekojs.core.module.NekoScriptModuleLoaderHost;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import graal.graalvm.polyglot.Context;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 票据 11：模块 cache/session 生命周期由 runtime owner 持有。
 *
 * <p>root 构造时接收唯一的 prepared 缓存实例并经 {@link NekoRuntimeRoot#preparationCache()}
 * 暴露；独立 root 互不可见；root close 全清释放。消费 05 的单 owner 成果，不重做 05。
 */
class NekoRuntimeModuleCacheOwnershipTest {

    @BeforeAll
    static void bindPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void rootOwnsSharedCacheIsolatedRootsDoNotShareAndCloseReleases() throws Exception {
        NekoModulePipelineCache firstCache = newCache();
        NekoModulePipelineCache secondCache = newCache();
        NekoRuntimeRoot first = rootWith(firstCache);
        NekoRuntimeRoot second = rootWith(secondCache);
        try {
            assertSame(firstCache, first.preparationCache(), "root 必须持有装配传入的同一实例");

            Path script = NekoJSPaths.get().testScripts().resolve("ownership_probe.cjs");
            Files.createDirectories(script.getParent());
            Files.writeString(script, "module.exports = 1;\n");
            try {
                first.preparationCache().prepare(script);
                assertEquals(1, first.preparedModuleCountForDiagnostics(), "准备条目落在 owner 实例");
                assertEquals(0, second.preparedModuleCountForDiagnostics(), "独立 root 互不可见");
                NekoModulePipelineCache child = firstCache.openSession();
                child.prepare(script);
                assertEquals(2, first.preparedModuleCountForDiagnostics(),
                        "generation child entries remain under the same root owner");
            } finally {
                Files.deleteIfExists(script);
            }

            first.closeSilently();
            assertEquals(0, first.preparedModuleCountForDiagnostics(), "root close 释放其持有的全部 prepared 条目");
            assertEquals(0, second.preparedModuleCountForDiagnostics());
        } finally {
            second.closeSilently();
        }
    }

    @Test
    void factoryRootManagerAndHostAllUseTheSameCacheIdentity() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = SandboxConfig.defaultConfig();
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        NekoModulePipelineCache cache = newCache(compilers);
        NekoCoreContext core = new NekoCoreContext(
                NekoSharedEngine.get(), config, ClassFilter.INSTANCE, new DefaultErrorTracker(paths, config));
        IPluginRuntime plugins = (IPluginRuntime) Proxy.newProxyInstance(
                NekoRuntimeModuleCacheOwnershipTest.class.getClassLoader(),
                new Class<?>[]{IPluginRuntime.class}, new NullPluginRuntime());
        NekoSandboxFactory factory = new NekoSandboxFactory(core, paths, compilers, plugins, cache);
        NekoRuntimeRoot root = new NekoRuntimeRoot(core, plugins, ScriptEventBridge.EMPTY,
                new ScriptPropertyRegistry.Impl(), factory, cache);
        try {
            assertSame(cache, field(factory, "preparationCache"));
            assertSame(cache, root.preparationCache());
            var manager = root.createScriptManager(com.tkisor.nekojs.api.ScriptType.TEST);
            assertSame(cache, field(manager, "preparationCache"));
            try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
                NekoScriptModuleLoaderHost host = new NekoScriptModuleLoaderHost(context,
                        new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(),
                                ScriptFilePolicy.legacyRuntime()), cache);
                try {
                    assertSame(cache, field(host, "preparationCache"));
                } finally {
                    host.close();
                }
            }

            NekoModulePipelineCache otherCache = newCache();
            assertThrows(IllegalArgumentException.class,
                    () -> new NekoRuntimeRoot(core, plugins, ScriptEventBridge.EMPTY,
                            new ScriptPropertyRegistry.Impl(), factory, otherCache),
                    "root must reject a cache that is not owned by its sandbox factory");
        } finally {
            root.closeSilently();
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static NekoRuntimeRoot rootWith(NekoModulePipelineCache cache) {
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = SandboxConfig.defaultConfig();
        NekoCoreContext core = new NekoCoreContext(
                NekoSharedEngine.get(), config, ClassFilter.INSTANCE, new DefaultErrorTracker(paths, config));
        IPluginRuntime plugins = (IPluginRuntime) Proxy.newProxyInstance(
                NekoRuntimeModuleCacheOwnershipTest.class.getClassLoader(),
                new Class<?>[]{IPluginRuntime.class},
                new NullPluginRuntime());
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(
                core, paths, compilers, plugins, cache);
        return new NekoRuntimeRoot(core, plugins, ScriptEventBridge.EMPTY,
                new ScriptPropertyRegistry.Impl(), sandboxFactory, cache);
    }

    private static NekoModulePipelineCache newCache() {
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = SandboxConfig.defaultConfig();
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        return newCache(paths, config, compilers);
    }

    private static NekoModulePipelineCache newCache(ScriptCompilerRegistry compilers) {
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = SandboxConfig.defaultConfig();
        return newCache(paths, config, compilers);
    }

    private static NekoModulePipelineCache newCache(NekoJSPaths paths, SandboxConfig config,
                                                     ScriptCompilerRegistry compilers) {
        return new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
    }

    /** 零行为插件桩：本测试不经过装配/执行，任何调用返回空值/零值/空集合。 */
    private static final class NullPluginRuntime implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            Class<?> type = method.getReturnType();
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            if (type == void.class) {
                return null;
            }
            if (type == java.util.Map.class) {
                return java.util.Map.of();
            }
            if (type == java.util.List.class) {
                return java.util.List.of();
            }
            if (type == java.util.Set.class) {
                return java.util.Set.of();
            }
            if (type == java.util.Collection.class) {
                return java.util.List.of();
            }
            if (type == java.util.Optional.class) {
                return java.util.Optional.empty();
            }
            return null;
        }
    }
}
