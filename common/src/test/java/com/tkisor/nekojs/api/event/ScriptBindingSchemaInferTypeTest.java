package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * W4/A4 回归：{@link ScriptBindingSchema#inferType} 必须识别脚本包布局。
 *
 * <p>包脚本（GLOBAL {@code <root>/packs/<id>/<type>_scripts/…}、WORLD
 * {@code <world>/nekojs_packs/<id>/<type>_scripts/…}、SERVER_CACHE
 * {@code …/server_packs/…}）不在任何类型根之下。旧实现只做类型根前缀判定，
 * 包路径恒返回 null → 预检 schema 为空 → 成员/事件回调校验对包脚本整体静默失效。
 */
class ScriptBindingSchemaInferTypeTest {

    @BeforeAll
    static void init() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void flatScriptUnderTypeRootInfersType() {
        assertEquals(ScriptType.SERVER,
                ScriptBindingSchema.inferType(NekoJSPaths.get().serverScripts().resolve("a.js")));
        assertEquals(ScriptType.CLIENT,
                ScriptBindingSchema.inferType(NekoJSPaths.get().clientScripts().resolve("sub/b.js")));
    }

    @Test
    void globalPackScriptInfersType() {
        Path packScript = NekoJSPaths.get().root()
                .resolve("packs").resolve("mylib").resolve("server_scripts").resolve("init.js");
        assertEquals(ScriptType.SERVER, ScriptBindingSchema.inferType(packScript));
    }

    @Test
    void worldPackScriptOutsideNekojsRootInfersType() {
        // WORLD 包在 <world>/nekojs_packs/<id>/… —— 任意世界目录，nekojs root 前缀不可用
        Path worldPack = Path.of(System.getProperty("java.io.tmpdir"))
                .resolve("some-world").resolve("nekojs_packs")
                .resolve("wp").resolve("client_scripts").resolve("hud.js");
        assertEquals(ScriptType.CLIENT, ScriptBindingSchema.inferType(worldPack));
    }

    @Test
    void unrelatedPathWithoutScriptsSegmentReturnsNull() {
        assertNull(ScriptBindingSchema.inferType(Path.of("some/random/place/file.js")));
        assertNull(ScriptBindingSchema.inferType(null));
    }
}
