package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 08 regression: a NekoJS plugin can register a node module whose id is namespaced with
 * {@code <modid>:<name>} (see {@code wiki/插件开发.md} and {@code NodeModulesPoint}).
 *
 * <p>Found by dropping a real external addon jar into {@code run/mods}: the addon's
 * {@code registerNodeModules} product was installed into {@code __nekoNodeBuiltins} correctly, but
 * {@code require('nekojs08:hello')} from a script failed because the resolver treated the namespaced
 * id as a relative Windows path and rejected it with {@code Illegal char <:> at index 8}
 * (a {@link java.nio.file.InvalidPathException} raised by {@code Path.of}).
 *
 * <p>A plugin module id is defined as a registry key, never as a filesystem path, so it must be
 * classified as a built-in/special specifier and resolved through {@code __nekoNodeResolve}.
 */
class NekoPluginNodeModuleResolutionTest {

    @TempDir
    Path gameDir;

    private static final String PARENT = "server_scripts/main.js";

    /** The exact id the ticket 08 external addon registers. */
    @Test
    void namespacedPluginModuleIdResolvesAsBuiltinInsteadOfThrowingOnPathParsing() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);

        NekoResolvedModule resolved = resolver.resolve(PARENT, "nekojs08:hello");

        assertTrue(resolved.special(), "a namespaced plugin module id is a registry key, not a file path");
        assertEqualsKindBuiltin(resolved);
    }

    /** {@code require('nekojs08:hello')} must also survive the stricter require.resolve path. */
    @Test
    void namespacedPluginModuleIdSurvivesRequireResolve() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);

        NekoResolvedModule resolved = resolver.resolveForRequire(PARENT, "nekojs08:hello");
        assertTrue(resolved.special());
        assertEqualsKindBuiltin(resolved);
    }

    /** The un-namespaced legacy form used by existing built-ins keeps working. */
    @Test
    void unnamespacedRegistryKeyStillResolves() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);

        assertTrue(resolver.resolve(PARENT, "fs").special());
        assertTrue(resolver.resolve(PARENT, "node:fs").special());
    }

    /**
     * A specifier that really is a relative path must keep its file semantics — the namespaced-id
     * fix must not swallow {@code ./} / {@code ../} / drive-letter specifiers.
     */
    @Test
    void relativeSpecifiersKeepFileSemantics() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);
        Path module = gameDir.resolve("nekojs/server_scripts/lib/helper.js");
        Files.createDirectories(module.getParent());
        Files.writeString(module, "module.exports = 1;");

        NekoResolvedModule resolved = resolver.resolve(PARENT, "./lib/helper.js");
        assertFalse(resolved.special(), "a ./ specifier must stay a file module");
    }

    /**
     * An unregistered namespaced id is <b>classified</b> as a registry key (so the path parser never
     * sees it) and keeps its original specifier; whether the id actually exists is a registry
     * question answered when the module is loaded, not a path question.
     */
    @Test
    void unknownNamespacedIdKeepsItsSpecifierAndIsNeverTreatedAsAPath() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);

        NekoResolvedModule resolved = resolver.resolveForRequire(PARENT, "nosuchmod:missing");
        assertTrue(resolved.special());
        assertEqualsKindBuiltin(resolved);
        org.junit.jupiter.api.Assertions.assertEquals("nosuchmod:missing", resolved.specifier(),
                "the registry key must be preserved verbatim so the registry lookup can report it");
    }

    /**
     * Regression guard for the exact symptom the ticket 08 addon hit: no specifier may escape as a
     * raw {@link java.nio.file.InvalidPathException} ("Illegal char <:>"). Either it resolves or it
     * fails as an IOException naming the module.
     */
    @Test
    void colonBearingSpecifierNeverLeaksInvalidPathException() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);

        for (String specifier : List.of("addon08:not-registered", "nekojs08:hello", "a:b:c")) {
            try {
                resolver.resolveForRequire(PARENT, specifier);
            } catch (IOException expected) {
                assertTrue(expected.getMessage() != null && expected.getMessage().contains(specifier),
                        "an IOException must name the module: " + expected.getMessage());
            } catch (java.nio.file.InvalidPathException leaked) {
                throw new AssertionError("specifier '" + specifier + "' leaked a raw path exception", leaked);
            }
        }
    }

    private static void assertEqualsKindBuiltin(NekoResolvedModule resolved) {
        assertTrue(resolved.kind() == NekoModuleKind.BUILTIN,
                "expected BUILTIN classification but was " + resolved.kind());
    }

    private static NekoModuleResolver resolverFor(Path gameDir) throws Exception {
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        NekoJSPaths paths = constructor.newInstance(gameDir);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        return new NekoModuleResolver(paths, new ScriptFilePolicy(compilers));
    }
}
