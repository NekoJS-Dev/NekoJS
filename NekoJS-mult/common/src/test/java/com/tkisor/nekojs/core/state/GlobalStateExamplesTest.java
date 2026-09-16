package com.tkisor.nekojs.core.state;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 工单 10 AC6：最小可运行示例逐个穿透真实 Graal 管线。
 *
 * <p>示例文件是交付物（{@code docs/architecture-refactor/baseline/2026-09-16-global-state/examples/}），
 * 只使用已通过 gate 的能力（global/shared 顶层成员读写）。本 fixture 把它们原样拷进
 * 脚本目录执行，断言其可观察结果——示例不是文档摆设，是可执行验收输入。
 */
class GlobalStateExamplesTest {

    private static final Path EXAMPLES = Path.of("..", "docs", "architecture-refactor",
            "baseline", "2026-09-16-global-state", "examples");

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
        assumeTrue(Files.isRegularFile(EXAMPLES.resolve("same-type-global.js")),
                "examples directory must exist next to the repo docs");
    }

    @BeforeEach
    void cleanScriptDirs() throws Exception {
        for (ScriptType type : ScriptType.values()) {
            Path dir = ScriptTypeEnv.scriptsDir(type);
            if (dir == null) continue;
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) {
                for (Path path : stream.toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @AfterEach
    void cleanScriptDirsAfter() throws Exception {
        for (ScriptType type : ScriptType.values()) {
            Path dir = ScriptTypeEnv.scriptsDir(type);
            if (dir == null) continue;
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) {
                for (Path path : stream.toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }


    private static String example(String name) throws Exception {
        Path file = EXAMPLES.resolve(name);
        assumeTrue(Files.isRegularFile(file), "missing example: " + name);
        return Files.readString(file);
    }

    @Test
    void sameTypeGlobalExampleRunsThroughRealPipeline() throws Exception {
        try (Ticket10GlobalStateTest.Harness harness = new Ticket10GlobalStateTest.Harness()) {
            harness.writeScript(ScriptType.SERVER, "example.js", example("same-type-global.js"));
            harness.load(ScriptType.SERVER);
            harness.reload(ScriptType.SERVER);
            assertEquals(2, harness.stores.storeFor(ScriptType.SERVER).get("loads"),
                    "example must accumulate across reloads (retention + same-type sharing)");
            assertNotNull(harness.stores.storeFor(ScriptType.SERVER).get("mod"));
        }
    }

    @Test
    void explicitSharedExampleCrossesScriptTypes() throws Exception {
        try (Ticket10GlobalStateTest.Harness harness = new Ticket10GlobalStateTest.Harness()) {
            harness.writeScript(ScriptType.SERVER, "example.js", example("explicit-shared-server.js"));
            harness.writeScript(ScriptType.CLIENT, "example.js", example("explicit-shared-client.js"));
            harness.load(ScriptType.SERVER);
            harness.load(ScriptType.CLIENT);

            assertEquals("from-server", harness.stores.sharedStore().get("handoff"));
            assertEquals(1, harness.stores.sharedStore().get("count"));
            assertEquals("from-server", harness.stores.storeFor(ScriptType.CLIENT).get("lastHandoff"),
                    "client example reads the server example's shared write (same-root in-process sharing)");
        }
    }

    @Test
    void legacyCrossTypeMigrationExampleShowsOldFormGone() throws Exception {
        try (Ticket10GlobalStateTest.Harness harness = new Ticket10GlobalStateTest.Harness()) {
            harness.writeScript(ScriptType.SERVER, "example.js", example("legacy-cross-type-migration-server.js"));
            harness.writeScript(ScriptType.CLIENT, "example.js", example("legacy-cross-type-migration-client.js"));
            harness.load(ScriptType.SERVER);
            harness.load(ScriptType.CLIENT);

            assertNull(harness.stores.storeFor(ScriptType.CLIENT).get("serverOnly"),
                    "old implicit cross-type global fallback must be gone");
            assertNull(harness.stores.storeFor(ScriptType.CLIENT).get("handoff"),
                    "global.handoff on client must be undefined (not leaked from server)");
            assertEquals("explicit", harness.stores.sharedStore().get("handoff"),
                    "migration target is the explicit shared entry");
        }
    }

    @Test
    void failureRetentionExampleKeepsOldValuesWhenCandidateDies() throws Exception {
        try (Ticket10GlobalStateTest.Harness harness = new Ticket10GlobalStateTest.Harness()) {
            // 第一轮：示例在线上跑过（retained = 1）
            harness.writeScript(ScriptType.SERVER, "example.js", example("failure-retention.js"));
            harness.load(ScriptType.SERVER);
            assertEquals(1, harness.stores.storeFor(ScriptType.SERVER).get("retained"));

            // 注入示例注释里描述的坏脚本（写 top-level key + 烧尽语句上限）→ reload 失败
            harness.writeScript(ScriptType.SERVER, "boom.js", """
                    global.bad = 1
                    while (true) { }
                    """);
            assertThrows(NekoReloadException.class, () -> harness.reload(ScriptType.SERVER));
            assertNull(harness.stores.storeFor(ScriptType.SERVER).get("bad"),
                    "the failing script's top-level write must not publish");
            assertEquals(1, harness.stores.storeFor(ScriptType.SERVER).get("retained"),
                    "old active values stay intact through the failed reload");

            // 修好（删掉 boom.js）再 reload：示例重新执行，旧值上继续累加
            Files.delete(ScriptTypeEnv.scriptsDir(ScriptType.SERVER).resolve("boom.js"));
            harness.reload(ScriptType.SERVER);
            assertEquals(2, harness.stores.storeFor(ScriptType.SERVER).get("retained"),
                    "after fixing the bad script, the example resumes on the retained value");
            assertTrue(harness.stores.storeFor(ScriptType.SERVER).get("bad") == null);
        }
    }
}
