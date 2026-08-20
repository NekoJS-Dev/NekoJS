package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.NekoJS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * probe 产物的原子提交工具：staging/backup 目录交换的唯一实现，TS 与 Python 内置 backend
 * 共用（此前两处逐字重复）。backend 仍自管「生成到 staging」的细节，本类只负责目录恢复、
 * 交换与递归删除。
 *
 * <p><b>内部工具</b>：为内置 backend（分属不同包）共享而公开，非插件扩展点，第三方 backend
 * 自管输出时可用可不用；签名不承诺跨版本稳定。
 *
 * <p>生命周期约定（与既有两 backend 的行为逐字对齐）：
 * <ol>
 *   <li>{@link #recoverStaging}：进入生成前清理上次进程崩溃的残留——丢弃半成品 staging；
 *       若 outputDir 缺失但有 backup，把 backup 恢复为 outputDir。</li>
 *   <li>backend 全部写完后 {@link #commit}：旧 outputDir → backup，staging → outputDir，
 *       删 backup；swap 中途失败时尝试把 backup 恢复为 outputDir 再抛出。</li>
 *   <li>生成中途失败由 backend 自行 {@link #deleteRecursive} 丢弃 staging，旧 outputDir 完整保留。</li>
 * </ol>
 *
 * <p>这是目录级 rename/swap，不是跨平台严格原子事务（如 Windows 的 move 非原子）；
 * 生成期间旧目录保持可见，提交阶段快速切换。
 */
public final class ProbeOutputCommitter {

    private ProbeOutputCommitter() {
    }

    /** staging 目录名：{@code <outputDir>.staging}（同级）。 */
    public static Path stagingDir(Path outputDir) {
        return outputDir.resolveSibling(outputDir.getFileName().toString() + ".staging");
    }

    /** backup 目录名：{@code <outputDir>.old}（同级）。 */
    public static Path backupDir(Path outputDir) {
        return outputDir.resolveSibling(outputDir.getFileName().toString() + ".old");
    }

    /**
     * 恢复上次进程崩溃可能残留的中间态：丢弃半成品 staging；若 outputDir 缺失但有 backup，
     * 恢复 backup。调用方随后自行 {@code Files.createDirectories(staging)}。
     */
    public static void recoverStaging(Path outputDir, Path staging, Path backup) throws IOException {
        deleteRecursive(staging);
        if (!Files.exists(outputDir) && Files.exists(backup)) {
            Files.move(backup, outputDir);
        }
    }

    /**
     * staging 整体替换 outputDir：旧 outputDir → backup，staging → outputDir，删 backup。
     * swap 中途失败时尝试把 backup 恢复为 outputDir。
     */
    public static void commit(Path staging, Path outputDir, Path backup) throws IOException {
        deleteRecursive(backup);
        if (Files.exists(outputDir)) {
            Files.move(outputDir, backup);
        }
        try {
            Files.move(staging, outputDir);
        } catch (IOException e) {
            if (!Files.exists(outputDir) && Files.exists(backup)) {
                try {
                    Files.move(backup, outputDir);
                } catch (IOException ignored) {
                }
            }
            throw e;
        }
        deleteRecursive(backup);
    }

    /**
     * 递归删除目录及其内容（深度优先逆序，先文件后目录）。walk 流用 try-with-resources 关闭
     * （文件句柄泄漏会锁住目录，Windows 上导致后续 move 失败）；删除失败的路径收集后 warn
     * 一次（典型为 Windows 文件锁），不抛出——调用方按自身语义决定是否视为失败。
     */
    public static void deleteRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        List<Path> failed = new ArrayList<>();
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        if (!p.toFile().delete()) failed.add(p);
                    });
        }
        if (!failed.isEmpty()) {
            NekoJS.LOGGER.warn("Probe: failed to delete {} path(s) under {} (locked by another process?): {}",
                    failed.size(), dir, failed);
        }
    }
}
