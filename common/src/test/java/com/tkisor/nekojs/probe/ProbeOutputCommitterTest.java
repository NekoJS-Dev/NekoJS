package com.tkisor.nekojs.probe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ProbeOutputCommitter}（staging/backup 目录交换）的失败路径单测——
 * 此前该逻辑在 TS/Python 两 backend 内逐字重复且零测试。
 */
class ProbeOutputCommitterTest {

    @Test
    void commitSwapsStagingIntoOutputAndDeletesBackup(@TempDir Path tmp) throws Exception {
        Path output = dir(tmp, "out");
        Path staging = dir(tmp, "out.staging");
        Path backup = dir(tmp, "out.old");
        Files.writeString(output.resolve("old.txt"), "old");
        Files.writeString(staging.resolve("new.txt"), "new");

        ProbeOutputCommitter.commit(staging, output, backup);

        assertTrue(Files.exists(output.resolve("new.txt")), "staging 应成为新 output");
        assertFalse(Files.exists(output.resolve("old.txt")));
        assertFalse(Files.exists(staging), "staging 已被移走");
        assertFalse(Files.exists(backup), "backup 提交后删除");
    }

    @Test
    void commitWithoutExistingOutputStillCommits(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("out");
        Path staging = dir(tmp, "out.staging");
        Files.writeString(staging.resolve("new.txt"), "new");

        ProbeOutputCommitter.commit(staging, output, dir(tmp, "out.old"));

        assertTrue(Files.exists(output.resolve("new.txt")));
    }

    @Test
    void commitFailureRestoresBackupAsOutput(@TempDir Path tmp) throws Exception {
        Path output = dir(tmp, "out");
        Files.writeString(output.resolve("old.txt"), "old");
        Path backup = dir(tmp, "out.old");
        Path ghostStaging = tmp.resolve("out.staging"); // 不存在：move 必失败（确定性注入）

        assertThrows(IOException.class, () -> ProbeOutputCommitter.commit(ghostStaging, output, backup));

        assertTrue(Files.exists(output.resolve("old.txt")), "swap 中途失败必须把 backup 恢复为 output");
        assertFalse(Files.exists(backup), "恢复后 backup 不应残留");
    }

    @Test
    void recoverStagingDiscardsHalfWrittenAndRestoresBackupWhenOutputMissing(@TempDir Path tmp) throws Exception {
        Path output = tmp.resolve("out"); // 缺失：模拟崩溃发生在 output→backup 之后
        Path staging = dir(tmp, "out.staging");
        Files.writeString(staging.resolve("junk.txt"), "half-written");
        Path backup = dir(tmp, "out.old");
        Files.writeString(backup.resolve("old.txt"), "old");

        ProbeOutputCommitter.recoverStaging(output, staging, backup);

        assertFalse(Files.exists(staging), "半成品 staging 必须丢弃");
        assertTrue(Files.exists(output.resolve("old.txt")), "output 缺失时 backup 必须恢复");
        assertFalse(Files.exists(backup));
    }

    @Test
    void recoverStagingKeepsOutputWhenBothExist(@TempDir Path tmp) throws Exception {
        Path output = dir(tmp, "out");
        Files.writeString(output.resolve("cur.txt"), "cur");
        Path staging = dir(tmp, "out.staging");
        Files.writeString(staging.resolve("junk.txt"), "half-written");
        Path backup = dir(tmp, "out.old");
        Files.writeString(backup.resolve("old.txt"), "old");

        ProbeOutputCommitter.recoverStaging(output, staging, backup);

        assertTrue(Files.exists(output.resolve("cur.txt")), "output 存在时不得被 backup 覆盖");
        assertFalse(Files.exists(staging));
        assertTrue(Files.exists(backup), "output 存在时 backup 原样保留（下次崩溃恢复用不到但无害）");
    }

    @Test
    void deleteRecursiveIsNoOpForAbsentAndRemovesTree(@TempDir Path tmp) throws Exception {
        assertDoesNotThrow(() -> ProbeOutputCommitter.deleteRecursive(tmp.resolve("absent")));

        Path tree = dir(tmp, "tree");
        Files.createDirectories(tree.resolve("a/b"));
        Files.writeString(tree.resolve("a/b/leaf.txt"), "x");
        Files.writeString(tree.resolve("root.txt"), "x");

        ProbeOutputCommitter.deleteRecursive(tree);
        assertFalse(Files.exists(tree));
    }

    @Test
    void stagingAndBackupNamesAreSiblingDotSuffixes(@TempDir Path tmp) {
        Path out = tmp.resolve(".neko_probe").resolve("typescript");
        assertEquals("typescript.staging", ProbeOutputCommitter.stagingDir(out).getFileName().toString());
        assertEquals("typescript.old", ProbeOutputCommitter.backupDir(out).getFileName().toString());
    }

    private static Path dir(Path parent, String name) throws IOException {
        Path p = parent.resolve(name);
        Files.createDirectories(p);
        return p;
    }
}
