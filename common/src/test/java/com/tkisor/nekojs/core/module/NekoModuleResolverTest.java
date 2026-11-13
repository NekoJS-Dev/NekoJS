package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NekoModuleResolverTest {

    @TempDir
    Path gameDir;

    @Test
    void resolvesBareUserNodeModuleAsScript() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);
        Path runtime = gameDir.resolve("nekojs/node_modules/nekojs/user-lib.js");
        Files.createDirectories(runtime.getParent());
        Files.writeString(runtime, "export const jsx = () => null;");

        NekoResolvedModule resolved = resolver.resolve("nekojs/server_scripts/main.js", "nekojs/user-lib");

        assertEquals(NekoModuleKind.SCRIPT, resolved.kind());
        assertEquals(runtime.toRealPath(), resolved.path());
    }

    @Test
    void workspaceBuiltinCannotBeShadowedByAUserPackage() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);
        Path shadow = gameDir.resolve("nekojs/node_modules/nekojs/jsx-workspace.js");
        Files.createDirectories(shadow.getParent());
        Files.writeString(shadow, "export const createWorkspace = () => null;");

        assertEquals(NekoModuleKind.BUILTIN,
                resolver.resolve("nekojs/client_scripts/main.tsx", "nekojs/jsx-workspace").kind());
        assertEquals(NekoModuleKind.BUILTIN,
                resolver.resolveForRequire("nekojs/client_scripts/main.tsx", "nekojs/jsx-workspace").kind());
    }

    @Test
    void rejectsBareModuleTraversalOutsideNodeModules() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);
        Path secret = gameDir.resolve("nekojs/server_scripts/secret.js");
        Files.createDirectories(secret.getParent());
        Files.writeString(secret, "export const secret = true;");

        IOException error = assertThrows(IOException.class,
                () -> resolver.resolve("nekojs/server_scripts/main.js", "package/../../server_scripts/secret"));

        assertTrue(error.getMessage().contains("node_modules"), error::getMessage);
    }

    @Test
    void resolvesScopedBareModuleSubpath() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);
        Path module = gameDir.resolve("nekojs/node_modules/@scope/pkg/subpath.js");
        Files.createDirectories(module.getParent());
        Files.writeString(module, "export const value = true;");

        NekoResolvedModule resolved = resolver.resolve("nekojs/server_scripts/main.js", "@scope/pkg/subpath");

        assertEquals(NekoModuleKind.SCRIPT, resolved.kind());
        assertEquals(module.toRealPath(), resolved.path());
    }

    @Test
    void preservesBuiltinJavaAndUnknownBareSpecifierClassification() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);

        assertEquals(NekoModuleKind.BUILTIN, resolver.resolve("nekojs/server_scripts/main.js", "fs").kind());
        assertEquals(NekoModuleKind.BUILTIN, resolver.resolve("nekojs/server_scripts/main.js", "node:fs").kind());
        assertEquals(NekoModuleKind.JAVA_MODULE, resolver.resolve("nekojs/server_scripts/main.js", "java:example/Widget").kind());
        assertEquals(NekoModuleKind.SPECIAL, resolver.resolve("nekojs/server_scripts/main.js", "unknown-package").kind());
    }

    @Test
    void propagatesUnsupportedBareNodeModuleCandidateErrors() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);
        Path unsupported = gameDir.resolve("nekojs/node_modules/nekojs/user-lib.txt");
        Files.createDirectories(unsupported.getParent());
        Files.writeString(unsupported, "not a script");

        IOException error = assertThrows(IOException.class,
                () -> resolver.resolve("nekojs/server_scripts/main.js", "nekojs/user-lib.txt"));

        assertTrue(error.getMessage().startsWith("Unsupported module file type:"), error::getMessage);
    }

    @Test
    void resolveForRequireRejectsUnknownBareSpecifier() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);

        // require.resolve semantics: an unmatched bare specifier throws MODULE_NOT_FOUND instead of falling
        // back to SPECIAL.
        IOException error = assertThrows(IOException.class,
                () -> resolver.resolveForRequire("nekojs/server_scripts/main.js", "unknown-package"));
        assertTrue(error.getMessage().contains("unknown-package"), error::getMessage);
    }

    @Test
    void resolveForRequireKeepsBuiltinJavaAndFileModules() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);
        Path module = gameDir.resolve("nekojs/node_modules/nekojs/user-lib.js");
        Files.createDirectories(module.getParent());
        Files.writeString(module, "export const jsx = () => null;");

        // builtin and java: are valid special specifiers, so require.resolve returns them normally.
        assertEquals("fs", resolver.resolveForRequire("nekojs/server_scripts/main.js", "fs").specifier());
        assertEquals("java:example/Widget",
                resolver.resolveForRequire("nekojs/server_scripts/main.js", "java:example/Widget").specifier());
        // A file module resolves normally, including a hit inside node_modules.
        assertEquals(NekoModuleKind.SCRIPT,
                resolver.resolveForRequire("nekojs/server_scripts/main.js", "nekojs/user-lib").kind());
    }

    @Test
    void preservesRelativeJsonAndScriptCandidateResolution() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);
        Path scripts = gameDir.resolve("nekojs/server_scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("entry.js"), "module.exports = 1;\n");
        Files.writeString(scripts.resolve("data.json"), "{\"value\":1}\n");

        assertEquals(NekoModuleKind.SCRIPT,
                resolver.resolveEntry("./server_scripts/entry").kind());
        assertEquals(NekoModuleKind.JSON,
                resolver.resolve("./server_scripts/entry.js", "./data").kind());
    }

    @Test
    void rejectsSymlinkEscapeFromGameDirectory() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);
        Path outside = gameDir.getParent().resolve("nekojs-resolver-outside.js");
        Path link = gameDir.resolve("nekojs/server_scripts/link.js");
        Files.createDirectories(link.getParent());
        Files.writeString(outside, "module.exports = 1;\n");
        try {
            try {
                Files.createSymbolicLink(link, outside);
            } catch (UnsupportedOperationException | IOException unavailable) {
                return;
            }
            IOException error = assertThrows(IOException.class,
                    () -> resolver.resolve("./server_scripts/main.js", "./link.js"));
            assertTrue(error.getMessage().contains("Symlink escape"), error::getMessage);
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void entryPathFormsRetainCanonicalModuleIdentity() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);
        Path entry = gameDir.resolve("nekojs/server_scripts/entry.js");
        Files.createDirectories(entry.getParent());
        Files.writeString(entry, "export const value = 1;\n");

        NekoResolvedModule relative = resolver.resolveEntry("./server_scripts/entry.js");
        assertEquals(entry.toRealPath(), relative.path());
        assertEquals(relative, resolver.resolveEntry(entry.toString()));
        assertEquals(relative, resolver.resolveEntry(entry.toUri().toString()));
        assertEquals(relative, resolver.resolveEntry("server_scripts/entry"));
    }

    @Test
    void rejectsOutsideEntriesAndParentsEvenWhenTheChildIsInside(@TempDir Path outsideDirectory) throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);
        Path outside = outsideDirectory.resolve("parent.js");
        Files.writeString(outside, "export const value = 1;\n");
        Path inside = gameDir.resolve("nekojs/server_scripts/inside.js");
        Files.createDirectories(inside.getParent());
        Files.writeString(inside, "export const value = 2;\n");
        String specifier = outsideDirectory.relativize(inside).toString().replace('\\', '/');

        assertThrows(IOException.class, () -> resolver.resolveEntry(outside.toString()));
        assertThrows(IOException.class, () -> resolver.resolveEntry(outside.toUri().toString()));
        assertThrows(IOException.class, () -> resolver.resolve(outside.toString(), specifier));
        assertThrows(IOException.class, () -> resolver.resolveForRequire(outside.toString(), specifier));
    }

    @Test
    void rejectsEntrySymlinksEscapingTheGameDirectory(@TempDir Path outsideDirectory) throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);
        Path outside = outsideDirectory.resolve("outside.js");
        Files.writeString(outside, "export const value = 1;\n");
        Path link = gameDir.resolve("nekojs/server_scripts/link.js");
        Files.createDirectories(link.getParent());
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException unavailable) {
            Assumptions.assumeTrue(false, "Symbolic links unavailable: " + unavailable.getClass().getSimpleName());
            return;
        }

        IOException error = assertThrows(IOException.class,
                () -> resolver.resolveEntry("./server_scripts/link.js"));
        assertTrue(error.getMessage().contains("Symlink escape"), error::getMessage);
        assertThrows(IOException.class, () -> resolver.resolveEntry(link.toUri().toString()));
    }

    @Test
    void rechecksRetargetedEntrySymlinksOnTheSameResolver(@TempDir Path outsideDirectory) throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);
        Path scripts = Files.createDirectories(gameDir.resolve("nekojs/server_scripts"));
        Path inside = Files.writeString(scripts.resolve("inside.js"), "module.exports = 1;\n");
        Path outside = Files.writeString(outsideDirectory.resolve("outside.js"), "module.exports = 2;\n");
        Path link = scripts.resolve("entry.js");
        createSymbolicLinkOrSkip(link, inside);
        assertEquals(inside.toRealPath(), resolver.resolveEntry("./server_scripts/entry.js").path());

        Files.delete(link);
        Files.createSymbolicLink(link, outside);

        assertThrows(IOException.class, () -> resolver.resolveEntry("./server_scripts/entry.js"));
        assertThrows(IOException.class, () -> resolver.resolveForRequire(inside.toString(), "./entry.js"));
    }

    @Test
    void rechecksBareModuleContainmentAfterSymlinkRetargeting() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);
        Path modules = Files.createDirectories(gameDir.resolve("nekojs/node_modules"));
        Path inside = Files.writeString(modules.resolve("inside.js"), "module.exports = 1;\n");
        Path scripts = Files.createDirectories(gameDir.resolve("nekojs/server_scripts"));
        Path outside = Files.writeString(scripts.resolve("outside.js"), "module.exports = 2;\n");
        Path link = modules.resolve("entry.js");
        createSymbolicLinkOrSkip(link, inside);
        assertEquals(inside.toRealPath(), resolver.resolveForRequire("./server_scripts/main.js", "entry").path());

        Files.delete(link);
        Files.createSymbolicLink(link, outside);

        IOException error = assertThrows(IOException.class,
                () -> resolver.resolveForRequire("./server_scripts/main.js", "entry"));
        assertTrue(error.getMessage().contains("node_modules"), error::getMessage);
    }

    @Test
    void observesGameRootRetargetingBetweenResolutions(@TempDir Path roots) throws Exception {
        Path first = Files.createDirectories(roots.resolve("first"));
        Path second = Files.createDirectories(roots.resolve("second"));
        Path firstEntry = Files.writeString(Files.createDirectories(first.resolve("nekojs/server_scripts"))
                .resolve("entry.js"), "module.exports = 1;\n");
        Path secondEntry = Files.writeString(Files.createDirectories(second.resolve("nekojs/server_scripts"))
                .resolve("entry.js"), "module.exports = 2;\n");
        Path alias = roots.resolve("game");
        createSymbolicLinkOrSkip(alias, first);
        NekoModuleResolver resolver = resolverFor(alias);
        assertEquals(firstEntry.toRealPath(), resolver.resolveEntry("./server_scripts/entry.js").path());

        Files.delete(alias);
        Files.createSymbolicLink(alias, second);

        assertEquals(secondEntry.toRealPath(), resolver.resolveEntry("./server_scripts/entry.js").path());
    }

    @Test
    void observesMissingModuleRootCreatedAfterAnEarlierResolution() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);
        assertThrows(IOException.class,
                () -> resolver.resolveForRequire("./server_scripts/main.js", "later"));
        Path module = Files.writeString(Files.createDirectories(gameDir.resolve("nekojs/node_modules"))
                .resolve("later.js"), "module.exports = 1;\n");

        assertEquals(module.toRealPath(), resolver.resolveForRequire("./server_scripts/main.js", "later").path());
    }

    @Test
    void rejectsAFileRemovedAfterAnEarlierResolution() throws Exception {
        NekoModuleResolver resolver = resolverFor(gameDir);
        Path scripts = Files.createDirectories(gameDir.resolve("nekojs/server_scripts"));
        Path entry = Files.writeString(scripts.resolve("entry.js"), "module.exports = 1;\n");
        assertEquals(entry.toRealPath(), resolver.resolveEntry("./server_scripts/entry.js").path());

        Files.delete(entry);

        assertThrows(IOException.class, () -> resolver.resolveEntry("./server_scripts/entry.js"));
    }

    private static void createSymbolicLinkOrSkip(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException unavailable) {
            Assumptions.assumeTrue(false, "Symbolic links unavailable: " + unavailable.getClass().getSimpleName());
        }
    }

    private static NekoModuleResolver resolverFor(Path gameDir) throws Exception {
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        Path root = gameDir.resolve("nekojs");
        return new NekoModuleResolver(gameDir, root, root.resolve("node_modules"),
                new ScriptFilePolicy(compilers));
    }
}
