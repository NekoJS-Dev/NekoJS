package com.tkisor.nekojs.core.error;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.network.ErrorSummaryDTO;

/**
 * 将错误 DTO 的位置字段解析为“已验证的本机脚本文件”。
 *
 * <p>远端专用服务器永远不猜测本机路径；本机模式也必须位于 NekoJS script root 内，并通过
 * {@link NekoJSPaths#verifyInsideNekoRoot(Path)} 的 real-path 校验。列号只使用 DTO 的既有 line 字段；本类不会解析
 * fullDetails 来制造结构化列号。</p>
 */
public final class LocalErrorSource {
    private final NekoJSPaths paths;

    private LocalErrorSource(NekoJSPaths paths) {
        this.paths = Objects.requireNonNull(paths, "paths");
    }

    public static LocalErrorSource forPaths(NekoJSPaths paths) {
        return new LocalErrorSource(paths);
    }

    /**
     * 解析错误位置。
     *
     * @param integratedSingleplayerServer 必须由调用方显式传入：仅集成单人服务器的脚本根可视为本机路径
     */
    public Result resolve(ErrorSummaryDTO error, boolean integratedSingleplayerServer) {
        if (error == null) {
            return Result.unavailable(Status.NO_ERROR);
        }
        if (!integratedSingleplayerServer) {
            // 远端判断先于任何路径解析；专用服务器路径可能是远端绝对路径，绝不能碰本机同名文件。
            return Result.unavailable(Status.REMOTE_SERVER);
        }
        String rawPath = error.path();
        if (isInvalidText(rawPath)) {
            return Result.unavailable(Status.INVALID_PATH);
        }
        if (isVirtualPath(rawPath)) {
            return Result.unavailable(Status.VIRTUAL_PATH);
        }

        try {
            Path requested = Path.of(rawPath);
            Path candidate = requested.isAbsolute() ? requested : paths.root().resolve(requested);
            // 先钉住 gameDir 物理边界，再检查 nekojs 根；否则 nekojs 根本身被替换为
            // symlink/reparse point 时，verifyInsideNekoRoot 会把链接目标当作新的根。
            Path verified = paths.verifyInsideGameDir(candidate);
            verified = paths.verifyInsideNekoRoot(verified);
            if (!paths.isInsideScriptRoot(verified)) {
                return Result.unavailable(Status.OUTSIDE_SCRIPT_ROOT);
            }
            if (!Files.exists(verified)) {
                return Result.unavailable(Status.MISSING_FILE);
            }
            if (!Files.isRegularFile(verified)) {
                return Result.unavailable(Status.NOT_REGULAR_FILE);
            }
            if (!Files.isReadable(verified)) {
                return Result.unavailable(Status.UNREADABLE_FILE);
            }

            int line = error.line() > 0 ? error.line() : -1;
            return Result.local(verified, line);
        } catch (InvalidPathException e) {
            return Result.unavailable(Status.INVALID_PATH);
        } catch (IOException e) {
            // 两个 root verifier 的拒绝包含穿越、非法链接和 reparse escape；不能落入 fallback。
            return Result.unavailable(Status.OUTSIDE_SCRIPT_ROOT);
        } catch (RuntimeException e) {
            return Result.unavailable(Status.IO_ERROR);
        }
    }

    private static boolean isInvalidText(String path) {
        if (path == null || path.isBlank() || isUnknownPlaceholder(path.trim())) {
            return true;
        }
        if (path.startsWith("\\\\") || path.startsWith("//")) {
            return true; // UNC / network path
        }
        for (int i = 0; i < path.length(); i++) {
            if (Character.isISOControl(path.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnknownPlaceholder(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.equals("unknown")
                || normalized.equals("unknown location")
                || normalized.equals("unknown source")
                || normalized.startsWith("unknown/")
                || normalized.startsWith("unknown\\");
    }

    static boolean isVirtualPath(String path) {
        if (path.startsWith("<") && path.endsWith(">")) {
            return true; // GraalVM source name / NekoJS virtual module placeholder
        }
        int forwardSlash = path.indexOf('/');
        int backslash = path.indexOf('\\');
        int separator = Math.min(
                forwardSlash < 0 ? Integer.MAX_VALUE : forwardSlash,
                backslash < 0 ? Integer.MAX_VALUE : backslash
        );
        int colon = path.indexOf(':');
        if (colon < 0) {
            return false;
        }
        // 后续 path segment 内的冒号交给具体 Path 解析、script-root 和 real-path 校验。
        // 只有首个分隔符之前出现冒号才可能是 scheme / virtual module。
        if (separator != Integer.MAX_VALUE && colon > separator) {
            return false;
        }
        // 唯一允许的首段冒号是完整 Windows 盘符路径；C:relative 这种 drive-relative 形式仍拒绝。
        return !(colon == 1
                && path.length() > 2
                && (path.charAt(2) == '\\' || path.charAt(2) == '/')
                && isAsciiLetter(path.charAt(0)));
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    public enum Status {
        LOCAL_FILE, NO_ERROR, REMOTE_SERVER, INVALID_PATH, VIRTUAL_PATH,
        OUTSIDE_SCRIPT_ROOT, MISSING_FILE, NOT_REGULAR_FILE, UNREADABLE_FILE, IO_ERROR
    }

    /** 解析结果；只有 {@link Status#LOCAL_FILE} 会携带非 null 文件和打开目标。 */
    public record Result(Status status, Path file, int line, boolean lineKnown, Target target) {
        public boolean available() {
            return status == Status.LOCAL_FILE;
        }

        private static Result local(Path file, int line) {
            return new Result(Status.LOCAL_FILE, file, line, line > 0, Target.of(file, line));
        }

        private static Result unavailable(Status status) {
            return new Result(status, null, -1, false, null);
        }

        public Result {
            Objects.requireNonNull(status, "status");
            if (status == Status.LOCAL_FILE) {
                Objects.requireNonNull(file, "file");
                Objects.requireNonNull(target, "target");
            } else if (file != null || target != null) {
                throw new IllegalArgumentException("unavailable result must not carry a file target");
            }
        }
    }

    /**
     * 已验证的 VS Code 打开目标。
     *
     * <p>{@code vscodeFileUri} 始终只表示文件级打开。行号通过官方 CLI 的 {@code --goto} 原生参数传递；
     * POSIX 文件名本身含冒号会与行列分隔产生歧义，此时 gotoLine 为 false，只打开已验证文件。</p>
     */
    public record Target(Path file, URI vscodeFileUri, boolean gotoLine, int line) {
        public Target {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(vscodeFileUri, "vscodeFileUri");
            if (gotoLine && line <= 0) {
                throw new IllegalArgumentException("line must be positive when requesting goto");
            }
        }

        private static Target of(Path file, int line) {
            URI vscodeUri = URI.create("vscode://file" + encodedPath(file));
            boolean safeLine = line > 0 && lineSuffixIsUnambiguous(file);
            return new Target(file, vscodeUri, safeLine, line > 0 ? line : -1);
        }

        /** 传给进程的参数（不含 code 命令本身）。这里只生成 argv，绝不做 shell 字符串拼接。 */
        public List<String> arguments() {
            return gotoLine
                    ? List.of("--goto", file + ":" + line)
                    : List.of(file.toString());
        }

        /**
         * 手动按 UTF-8 编码每个 path segment。Path 的 name 元素不包含根：Windows 盘符和 UNC
         * server/share 都在 root 中，必须显式编码，否则协议 URI 会丢失打开目标所在的卷。
         */
        private static String encodedPath(Path file) {
            StringBuilder result = new StringBuilder();
            appendEncodedRoot(result, file.getRoot());
            for (int i = 0; i < file.getNameCount(); i++) {
                if (result.isEmpty() || result.charAt(result.length() - 1) != '/') {
                    result.append('/');
                }
                appendEncodedSegment(result, file.getName(i).toString(), false);
            }
            return result.toString();
        }

        private static void appendEncodedRoot(StringBuilder result, Path root) {
            if (root == null) {
                return;
            }
            String rootText = root.toString().replace('\\', '/');
            while (rootText.length() > 1 && rootText.endsWith("/")) {
                rootText = rootText.substring(0, rootText.length() - 1);
            }
            if (rootText.equals("/")) {
                result.append('/');
                return;
            }
            if (rootText.startsWith("//")) {
                result.append(rootText, 0, 2);
                boolean firstSegment = true;
                for (String segment : rootText.substring(2).split("/")) {
                    if (!firstSegment) {
                        result.append('/');
                    }
                    appendEncodedSegment(result, segment, false);
                    firstSegment = false;
                }
                return;
            }
            result.append('/');
            appendEncodedSegment(result, rootText, true);
        }

        private static void appendEncodedSegment(StringBuilder result, String segment, boolean firstSegment) {
            byte[] bytes = segment.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            for (int i = 0; i < bytes.length; i++) {
                byte value = bytes[i];
                int unsigned = value & 0xff;
                char c = (char) unsigned;
                boolean windowsDriveColon = firstSegment
                        && segment.length() == 2
                        && i == 1
                        && c == ':'
                        && isAsciiLetter(segment.charAt(0));
                if (windowsDriveColon || isUnreserved(unsigned)) {
                    result.append(c);
                } else {
                    result.append('%')
                            .append(Character.toUpperCase(Character.forDigit(unsigned >>> 4, 16)))
                            .append(Character.toUpperCase(Character.forDigit(unsigned & 0xf, 16)));
                }
            }
        }

        private static boolean isUnreserved(int value) {
            return (value >= 'A' && value <= 'Z')
                    || (value >= 'a' && value <= 'z')
                    || (value >= '0' && value <= '9')
                    || value == '-' || value == '.' || value == '_' || value == '~';
        }

        static boolean lineSuffixIsUnambiguous(Path file) {
            return lineSuffixIsUnambiguous(file.toString());
        }

        static boolean lineSuffixIsUnambiguous(String value) {
            int firstColon = value.indexOf(':');
            if (firstColon < 0) {
                return true;
            }
            // Windows 绝对路径允许且仅允许一个盘符冒号；盘符之后的任何冒号都会让
            // `file:line` 后缀歧义（包括 NTFS stream 语法），必须降级为文件级打开。
            boolean windowsDrive = firstColon == 1 && value.length() > 2 && value.charAt(1) == ':';
            return windowsDrive && value.indexOf(':', 2) < 0;
        }
    }
}


