package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.fs.ScriptPathProvider;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression test: {@link ScriptBindingSchema#inferType} must recognize the packed-script layout.
 *
 * <p>Pack scripts are not under any type root: GLOBAL uses
 * {@code <root>/packs/<id>/<type>_scripts/…}, WORLD uses
 * {@code <world>/nekojs_packs/<id>/<type>_scripts/…} and SERVER_CACHE uses
 * {@code …/server_packs/…}. A check that only matches type-root prefixes returns null for
 * these paths; the precheck schema is then empty and member and event callback validation is
 * silently skipped for every pack script.
 */
class ScriptBindingSchemaInferTypeTest {
    @Test
    void flatScriptUnderTypeRootInfersType() {
        assertEquals(ScriptType.SERVER,
                ScriptBindingSchema.inferType(Path.of("any-root", "server_scripts", "a.js")));
        assertEquals(ScriptType.CLIENT,
                ScriptBindingSchema.inferType(Path.of("any-root", "client_scripts", "sub", "b.js")));
    }

    @Test
    void globalPackScriptInfersType() {
        Path packScript = Path.of("arbitrary-root", "packs", "mylib", "server_scripts", "init.js");
        assertEquals(ScriptType.SERVER, ScriptBindingSchema.inferType(packScript));
    }

    @Test
    void worldPackScriptOutsideNekojsRootInfersType() {
        // A WORLD pack lives in <world>/nekojs_packs/<id>/…: the world directory is arbitrary,
        // so the nekojs root prefix does not apply
        Path worldPack = Path.of("some-world")
                .resolve("nekojs_packs")
                .resolve("wp").resolve("client_scripts").resolve("hud.js");
        assertEquals(ScriptType.CLIENT, ScriptBindingSchema.inferType(worldPack));
    }

    @Test
    void unrelatedPathWithoutScriptsSegmentReturnsNull() {
        assertNull(ScriptBindingSchema.inferType(Path.of("some/random/place/file.js")));
        assertNull(ScriptBindingSchema.inferType(null));
    }

    @Test
    void sharedNodeModulesAndMalformedPackLayoutsAreNotScriptRoots() {
        assertNull(ScriptBindingSchema.inferType(Path.of(
                "root", "node_modules", "foo", "server_scripts", "bar.js")));
        assertNull(ScriptBindingSchema.inferType(Path.of(
                "root", "packs", "id", "nested", "server_scripts", "bar.js")));
        assertNull(ScriptBindingSchema.inferType(Path.of(
                "root", "server_packs", "bucket", "id", "nested", "server_scripts", "bar.js")));
    }

    @Test
    void serverCachePackLayoutInfersType() {
        assertEquals(ScriptType.SERVER, ScriptBindingSchema.inferType(Path.of(
                "root", "server_packs", "bucket", "pack-id", "server_scripts", "entry.js")));
    }

    @Test
    void textualTrufflePathKeepsTheScriptRootIdentity() {
        assertEquals("server_scripts/context-callback.js", ScriptPathProvider.authoredPathText(
                "truffle:opaque/server_scripts/context-callback.js", Path.of("").getFileSystem()));
    }
}
