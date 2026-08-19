package com.tkisor.nekojs.core.pack.sync;

import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 包哈希聚焦测试：文件创建顺序无关的稳定性、内容/manifest 变化敏感、
 * 内容目录范围（脚本/assets/data 参与哈希，状态文件不参与）。
 */
class PackHasherTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @TempDir
    Path packRoot;

    @Test
    void hashIsStableAcrossFileCreationOrder() throws Exception {
        Path a = buildPack("pack_a", true); // 先写 one.js 再写 two.js
        Path b = buildPack("pack_b", false); // 先写 two.js 再写 one.js（内容相同）

        assertEquals(PackHasher.hashPackDir(a), PackHasher.hashPackDir(b));
    }

    @Test
    void hashChangesOnContentChange() throws Exception {
        Path base = buildPack("base", true);
        String before = PackHasher.hashPackDir(base);

        Files.writeString(base.resolve("server_scripts").resolve("one.js"), "helloModified");
        assertNotEquals(before, PackHasher.hashPackDir(base));
    }

    @Test
    void hashChangesOnManifestChange() throws Exception {
        Path base = buildPack("base_m", true);
        String before = PackHasher.hashPackDir(base);

        Files.writeString(base.resolve("manifest.json"), "{\"id\": \"base_m\", \"version\": \"2.0.0\"}");
        assertNotEquals(before, PackHasher.hashPackDir(base));
    }

    @Test
    void contentDirsAndStateFiles() throws Exception {
        Path pack = packRoot.resolve("scope_pack");
        Files.createDirectories(pack.resolve("client_scripts"));
        Files.createDirectories(pack.resolve("assets/nekojs/textures"));
        Files.createDirectories(pack.resolve("data/nekojs/recipes"));
        Files.createDirectories(pack.resolve("ignored_dir"));
        Files.writeString(pack.resolve("manifest.json"), "{\"id\": \"scope_pack\"}");
        Files.writeString(pack.resolve("client_scripts").resolve("hud.js"), "hud()");
        Files.writeString(pack.resolve("assets/nekojs/textures").resolve("logo.png"), "png");
        Files.writeString(pack.resolve("data/nekojs/recipes").resolve("r.json"), "{}");
        Files.writeString(pack.resolve("ignored_dir").resolve("misc.txt"), "not packed");
        Files.writeString(pack.resolve(".neko_pack.state.json"), "{\"enabled\": true}");

        List<PackContentFile> files = PackHasher.readContentFiles(pack);
        assertEquals(
            List.of("assets/nekojs/textures/logo.png", "client_scripts/hud.js", "data/nekojs/recipes/r.json"),
            files.stream().map(PackContentFile::relativePath).toList()); // 排序 + 范围过滤（状态文件/未知目录不参与）

        // 哈希 = manifest + 排序内容文件：等价手工构造
        String expected = PackHasher.hash(
            Files.readAllBytes(pack.resolve("manifest.json")),
            List.of(
                new PackContentFile("assets/nekojs/textures/logo.png", "png".getBytes()),
                new PackContentFile("client_scripts/hud.js", "hud()".getBytes()),
                new PackContentFile("data/nekojs/recipes/r.json", "{}".getBytes())));
        assertEquals(expected, PackHasher.hashPackDir(pack));

        // 状态文件翻转不影响哈希（不属于分发内容）
        Files.writeString(pack.resolve(".neko_pack.state.json"), "{\"enabled\": false}");
        assertEquals(expected, PackHasher.hashPackDir(pack));
    }

    @Test
    void missingManifestIsRejected() throws Exception {
        Path noManifest = Files.createDirectories(packRoot.resolve("not_a_pack"));
        try {
            PackHasher.hashPackDir(noManifest);
            org.junit.jupiter.api.Assertions.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(true);
        }
    }

    /** 建包：manifest（两包同 id——manifest 字节参与哈希，目录名不参与）+ 两个
     * server_scripts 文件（one.js=hello、two.js=second，写入顺序可变）。 */
    private Path buildPack(String id, boolean oneFirst) throws Exception {
        Path pack = packRoot.resolve(id);
        Files.createDirectories(pack.resolve("server_scripts"));
        Files.writeString(pack.resolve("manifest.json"), "{\"id\": \"same_id\"}");
        if (oneFirst) {
            Files.writeString(pack.resolve("server_scripts").resolve("one.js"), "hello");
            Files.writeString(pack.resolve("server_scripts").resolve("two.js"), "second");
        } else {
            Files.writeString(pack.resolve("server_scripts").resolve("two.js"), "second");
            Files.writeString(pack.resolve("server_scripts").resolve("one.js"), "hello");
        }
        return pack;
    }
}
