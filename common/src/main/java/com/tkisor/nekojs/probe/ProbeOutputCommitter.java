package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.NekoJS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * probe 产物的落盘工具：把 render 出来的「相对路径 → 文本」就地同步进输出目录，
 * TS 与 Python 内置 backend 共用。
 *
 * <p><b>内部工具</b>：为内置 backend（分属不同包）共享而公开，非插件扩展点，第三方 backend
 * 自管输出时可用可不用；签名不承诺跨版本稳定。
 *
 * <h2>为什么不做目录级 rename</h2>
 * 早先的实现是「写 staging → 旧目录改名 .old → staging 改名成输出目录」。这在 Windows 上
 * 基本不可用：probe 输出目录的存在意义就是被编辑器消费，而 VS Code / TypeScript 语言服务
 * 一旦盯着这个目录，目录本身的 rename 就会 {@link AccessDeniedException}——重试也没用，
 * 因为句柄在编辑器关闭前不会释放。所以改成逐文件就地同步：
 * <ol>
 *   <li>内容相同的文件跳过（不 touch，减少编辑器无谓重载，也少一次抢锁）；</li>
 *   <li>内容变化的文件覆盖写（短暂被读占用时带退避重试）；</li>
 *   <li>本次不再产出的旧文件删除（删不掉只记 warning，不算失败）；</li>
 *   <li>顺带清掉空目录与历史遗留的 {@code .staging}/{@code .old}。</li>
 * </ol>
 *
 * <p>代价是失去「目录级一次性切换」：整个 render 结果已在内存里，所以不存在半成品内容，
 * 但若中途某个文件写失败，输出目录会停在「部分更新」状态。相比「有编辑器开着就永远
 * 提交不了」，这个折中更实用；失败信息里会点名具体文件。
 */
public final class ProbeOutputCommitter {

    private static final int WRITE_ATTEMPTS = 4;
    private static final long WRITE_RETRY_DELAY_MS = 75L;

    private ProbeOutputCommitter() {
    }

    /** 就地同步的结果统计。 */
    public record CommitReport(int written, int unchanged, int deleted, List<String> warnings) {
        public CommitReport {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    /**
     * 校验 render 产物键集：返回第一个非法的相对路径（null = 全部合法）。
     * 非法 = null/空白、绝对路径、含 {@code ..} 段、或含非法路径字符（NUL 等）。
     * backend 声明文件不应拥有任意文件系统写权限——越界路径在这里被拒绝。
     */
    public static String firstIllegalRelativePath(Iterable<String> keys) {
        for (String key : keys) {
            if (key == null || key.isBlank()) return String.valueOf(key);
            Path p;
            try {
                p = Path.of(key);
            } catch (RuntimeException e) {
                return key;
            }
            if (p.isAbsolute()) return key;
            for (Path seg : p) {
                if ("..".equals(seg.toString())) return key;
            }
        }
        return null;
    }

    /**
     * 把 render 结果就地同步进 {@code outputDir}：写入变化的文件、跳过内容相同的、删除本次
     * 不再产出的旧文件。
     *
     * @throws IOException 某个文件重试后仍写不进去（消息里点名该文件）
     */
    public static CommitReport commitInPlace(Path outputDir, Map<String, String> files) throws IOException {
        Path root = outputDir.toAbsolutePath().normalize();
        Files.createDirectories(root);

        Set<Path> existing = listFiles(root);
        Set<Path> keep = new HashSet<>();
        int written = 0;
        int unchanged = 0;

        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path file = root.resolve(entry.getKey()).normalize();
            if (!file.startsWith(root)) {
                throw new IOException("Probe output path escapes the output directory: " + entry.getKey());
            }
            keep.add(file);
            byte[] content = entry.getValue().getBytes(StandardCharsets.UTF_8);
            if (hasSameContent(file, content)) {
                unchanged++;
                continue;
            }
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            writeWithRetry(file, content);
            written++;
        }

        List<String> warnings = new ArrayList<>();
        int deleted = 0;
        // 删不掉的陈旧文件只降级成 warning：它不影响新产物正确性，而抛出会让整次 probe 白跑
        Set<String> undeletable = new TreeSet<>();
        for (Path stale : existing) {
            if (keep.contains(stale)) continue;
            if (deleteWithRetry(stale)) {
                deleted++;
            } else {
                undeletable.add(root.relativize(stale).toString());
            }
        }
        if (!undeletable.isEmpty()) {
            warnings.add("stale probe file(s) could not be removed (locked by another process?): " + undeletable);
            NekoJS.LOGGER.warn("Probe: {} stale file(s) under {} could not be removed: {}",
                    undeletable.size(), root, undeletable);
        }
        pruneEmptyDirs(root);
        warnings.addAll(cleanupLegacyDirs(outputDir));
        return new CommitReport(written, unchanged, deleted, warnings);
    }

    /**
     * 清理旧目录交换方案遗留的 {@code <outputDir>.staging} / {@code <outputDir>.old}。
     * 尽力而为：删不掉只回 warning（这两个目录不影响新产物）。
     */
    public static List<String> cleanupLegacyDirs(Path outputDir) {
        List<String> warnings = new ArrayList<>();
        String name = outputDir.getFileName() == null ? "" : outputDir.getFileName().toString();
        for (String suffix : List.of(".staging", ".old")) {
            Path legacy = outputDir.resolveSibling(name + suffix);
            if (!Files.exists(legacy)) continue;
            try {
                deleteRecursive(legacy);
                NekoJS.LOGGER.info("Probe: removed leftover directory {}", legacy);
            } catch (IOException e) {
                warnings.add("leftover directory could not be removed: " + legacy);
            }
        }
        return warnings;
    }

    /** 目录下所有普通文件的绝对规范路径；目录不存在时返回空集。 */
    private static Set<Path> listFiles(Path root) throws IOException {
        if (!Files.exists(root)) return Set.of();
        Set<Path> found = new HashSet<>();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(p -> found.add(p.toAbsolutePath().normalize()));
        }
        return found;
    }

    /**
     * 内容是否与磁盘上完全一致（不一致/不存在/读失败都当作需要写）。
     *
     * <p>先比字节数：产物动辄数千个文件，逐个 readAllBytes 太贵，长度不同就无需读内容。
     */
    private static boolean hasSameContent(Path file, byte[] content) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) != content.length) return false;
            return Arrays.equals(Files.readAllBytes(file), content);
        } catch (IOException e) {
            return false;
        }
    }

    private static void writeWithRetry(Path file, byte[] content) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= WRITE_ATTEMPTS; attempt++) {
            try {
                Files.write(file, content);
                return;
            } catch (AccessDeniedException e) {
                last = e;
                if (attempt == WRITE_ATTEMPTS) break;
                sleepBackoff(attempt);
            }
        }
        throw new IOException("Probe output file is locked by another process: " + file
                + " (close the editor/TypeScript language server holding it and re-run /nekojs probe)", last);
    }

    private static boolean deleteWithRetry(Path file) {
        for (int attempt = 1; attempt <= WRITE_ATTEMPTS; attempt++) {
            try {
                Files.deleteIfExists(file);
                return true;
            } catch (IOException e) {
                if (attempt == WRITE_ATTEMPTS) return false;
                sleepBackoff(attempt);
            }
        }
        return false;
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(WRITE_RETRY_DELAY_MS * attempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** 自底向上删空目录（root 自身保留）；失败静默——空目录残留无害。 */
    private static void pruneEmptyDirs(Path root) throws IOException {
        List<Path> dirs = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isDirectory).filter(p -> !p.equals(root)).forEach(dirs::add);
        }
        dirs.sort(Comparator.reverseOrder());
        for (Path dir : dirs) {
            try (var children = Files.list(dir)) {
                if (children.findAny().isPresent()) continue;
            } catch (IOException e) {
                continue;
            }
            try {
                Files.deleteIfExists(dir);
            } catch (IOException ignored) {
                // 空目录删不掉无所谓，下次再试
            }
        }
    }

    /**
     * 递归删除目录及其内容（深度优先逆序，先文件后目录）。walk 流用 try-with-resources 关闭
     * （文件句柄泄漏会锁住目录，Windows 上导致后续删除失败）；删除失败的路径收集后 warn
     * 一次（典型为 Windows 文件锁）并抛出——调用方按自身语义决定是否视为失败。
     */
    public static void deleteRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        List<Path> failed = new ArrayList<>();
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            failed.add(p);
                        }
                    });
        }
        if (!failed.isEmpty()) {
            IOException error = new IOException("Failed to delete locked probe path(s): " + failed);
            NekoJS.LOGGER.warn("Probe: failed to delete {} path(s) under {} (locked by another process?): {}",
                    failed.size(), dir, failed, error);
            throw error;
        }
    }
}
