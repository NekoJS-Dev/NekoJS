package com.tkisor.nekojs.core.module;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 08 regression: a NekoJS plugin registers node modules under a namespaced id,
 * {@code <modid>:<name>} (see {@code NodeModulesPoint} and {@code wiki/插件开发.md}), and a script
 * consumes it with {@code require('nekojs08:hello')}. A real external addon jar in {@code run/mods}
 * surfaced this failing with {@code InvalidPathException: Illegal char <:> at index 8}.
 *
 * <p>The fix is deliberately narrow and lives in NekoModuleResolver resolveBareModule: Path.resolve
 * resolves a relative argument lazily, so a colon-bearing bare specifier blew up inside the
 * resolver before it could be classified. It is now answered with the pre-existing SPECIAL tier
 * ("not a file, look it up in the registry").
 *
 * <p><b>What this file must NOT do:</b> it must not weaken the existing contract that an
 * <i>unregistered</i> bare specifier fails under {@code require} semantics.
 * {@code NekoModuleResolverTest#resolveForRequireRejectsUnknownBareSpecifier} pins that, and these
 * tests pin the same rule for the namespaced shape.
 */
class NekoPluginNodeModuleResolutionTest {

    @TempDir
    Path gameDir;

    private static final String PARENT = "server_scripts/main.js";

    /** The exact id the ticket 08 external addon registers, and which scripts require. */
    @Test
    void namespacedPluginModuleIdIsClassifiedSpecialInsteadOfThrowingOnPathParsing() throws Exception {
        NekoModuleResolver resolver = NekoModuleResolverTestSupport.resolverFor(gameDir);

        NekoResolvedModule resolved = resolver.resolve(PARENT, "nekojs08:hello");

        assertTrue(resolved.special(), "a namespaced plugin module id is a registry key, not a file path");
        assertEquals(NekoModuleKind.SPECIAL, resolved.kind(),
                "it must land in the SPECIAL tier so __nekoNodeResolve answers it, not BUILTIN");
        assertEquals("nekojs08:hello", resolved.specifier(),
                "the registry key must be preserved verbatim so the registry lookup can report it");
    }

    /**
     * The load-bearing half of the contract: require of an id the registry does NOT have must still
     * fail as MODULE_NOT_FOUND. Classifying it as BUILTIN would silently pass the resolveForRequire
     * guard and turn a hard error into a confusing later failure.
     */
    @Test
    void unregisteredNamespacedIdStillFailsUnderRequireSemantics() throws Exception {
        NekoModuleResolver resolver = NekoModuleResolverTestSupport.resolverFor(gameDir);

        for (String specifier : List.of("nosuchmod:missing", "addon08:not-registered", "a:b:c")) {
            IOException error = assertThrows(IOException.class,
                    () -> resolver.resolveForRequire(PARENT, specifier),
                    "an unregistered namespaced id must fail, not resolve: " + specifier);
            assertTrue(error.getMessage().contains("Cannot resolve module"),
                    "it must fail as MODULE_NOT_FOUND: " + error.getMessage());
            assertTrue(error.getMessage().contains(specifier),
                    "the failure must name the module: " + error.getMessage());
        }
    }

    /**
     * Regression guard for the exact symptom: no specifier may escape as a raw
     * java.nio.file.InvalidPathException. Either it resolves or it fails as an IOException.
     */
    @Test
    void colonBearingSpecifierNeverLeaksInvalidPathException() throws Exception {
        NekoModuleResolver resolver = NekoModuleResolverTestSupport.resolverFor(gameDir);

        // NOTE: single-letter prefixes (x:y) are deliberately excluded — Windows treats those as
        // drive letters, so they take the file path and fail with a drive error, not a path error.
        for (String specifier : List.of("addon08:not-registered", "nekojs08:hello", "a:b:c", "ab:cd")) {
            try {
                resolver.resolveForRequire(PARENT, specifier);
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains(specifier), expected.getMessage());
            } catch (java.nio.file.InvalidPathException leaked) {
                throw new AssertionError("specifier '" + specifier + "' leaked a raw path exception", leaked);
            }
        }
    }

    /** The un-namespaced legacy form used by existing built-ins keeps working. */
    @Test
    void unnamespacedRegistryKeyStillResolvesAsBuiltin() throws Exception {
        NekoModuleResolver resolver = NekoModuleResolverTestSupport.resolverFor(gameDir);

        assertEquals(NekoModuleKind.BUILTIN, resolver.resolve(PARENT, "fs").kind());
        assertEquals(NekoModuleKind.BUILTIN, resolver.resolve(PARENT, "node:fs").kind());
    }

    /** A specifier that really is a relative path must keep its file semantics. */
    @Test
    void relativeSpecifiersKeepFileSemantics() throws Exception {
        NekoModuleResolver resolver = NekoModuleResolverTestSupport.resolverFor(gameDir);
        Path module = gameDir.resolve("nekojs/server_scripts/lib/helper.js");
        Files.createDirectories(module.getParent());
        Files.writeString(module, "module.exports = 1;");

        NekoResolvedModule resolved = resolver.resolve(PARENT, "./lib/helper.js");
        assertFalse(resolved.special(), "a ./ specifier must stay a file module");
    }

    /**
     * A single-letter prefix is a Windows drive letter, not a registry key: it must keep file
     * semantics (and must not be silently answered from the module registry).
     */
    @Test
    void singleLetterPrefixIsTreatedAsDriveLetterNotRegistryKey() throws Exception {
        NekoModuleResolver resolver = NekoModuleResolverTestSupport.resolverFor(gameDir);

        // It must be routed down the file path (and therefore rejected as outside the game dir),
        // never answered from the module registry.
        IOException error = assertThrows(IOException.class,
                () -> resolver.resolve(PARENT, "C:\\definitely\\not\\here"),
                "a drive-qualified path must stay a file specifier, not a registry key");
        assertTrue(error.getMessage() != null && !error.getMessage().contains("Invalid char"),
                "the drive path must fail for being outside the game dir: " + error.getMessage());
    }

    /** An installed node_modules package still resolves as a file, not as a registry key. */
    @Test
    void installedNodeModulesPackageResolvesAsFile() throws Exception {
        NekoModuleResolver resolver = NekoModuleResolverTestSupport.resolverFor(gameDir);
        Path module = gameDir.resolve("nekojs/node_modules/nekojs/user-lib.js");
        Files.createDirectories(module.getParent());
        Files.writeString(module, "module.exports = 1;");

        NekoResolvedModule resolved = resolver.resolve(PARENT, "nekojs/user-lib");
        assertEquals(NekoModuleKind.SCRIPT, resolved.kind());
        assertEquals(module.toRealPath(), resolved.path());
    }
}
