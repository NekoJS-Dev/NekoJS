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
 * Resolves an error location into a verified local script file. Both entry points share one
 * validation path: the error DTO (wire projection) and the frozen diagnostic record, handed in
 * as a {@link DiagnosticOpenAction}.
 *
 * <p>A remote dedicated server never guesses a local path. Local mode must still point inside the
 * NekoJS script root and pass the real-path check in
 * {@link NekoJSPaths#verifyInsideNekoRoot(Path)}. The column always comes from the existing
 * {@code line} field; this class never parses {@code fullDetails} to invent a structured column.
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
     * Resolves the location of an error.
     *
     * @param integratedSingleplayerServer must be passed explicitly by the caller: only the script
     *                                     root of an integrated singleplayer server counts as a
     *                                     local path
     */
    public Result resolve(ErrorSummaryDTO error, boolean integratedSingleplayerServer) {
        if (error == null) {
            return Result.unavailable(Status.NO_ERROR);
        }
        return resolveLocation(error.path(), error.line(), integratedSingleplayerServer);
    }

    /**
     * Resolves the location from the frozen diagnostic record's open action. It runs the same
     * validation as the DTO path; the action's source and line come from the record itself, so there
     * is no second source of truth.
     *
     * @param integratedSingleplayerServer must be passed explicitly by the caller: only the script
     *                                     root of an integrated singleplayer server counts as a
     *                                     local path
     */
    public Result resolve(DiagnosticOpenAction action, boolean integratedSingleplayerServer) {
        if (action == null) {
            return Result.unavailable(Status.NO_ERROR);
        }
        return resolveLocation(action.sourcePath(), action.line(), integratedSingleplayerServer);
    }

    /** Location validation shared by the DTO path and the open action: maps path and line to a verified local script file. */
    private Result resolveLocation(String rawPath, int requestedLine, boolean integratedSingleplayerServer) {
        if (!integratedSingleplayerServer) {
            // The remote check runs before any path parsing. A dedicated server path may be a remote absolute
            // path, and it must never touch a local file with the same name.
            return Result.unavailable(Status.REMOTE_SERVER);
        }
        if (isInvalidText(rawPath)) {
            return Result.unavailable(Status.INVALID_PATH);
        }
        if (isVirtualPath(rawPath)) {
            return Result.unavailable(Status.VIRTUAL_PATH);
        }

        try {
            Path requested = Path.of(rawPath);
            Path candidate = requested.isAbsolute() ? requested : paths.root().resolve(requested);
            // Pin the physical gameDir path first, then check the nekojs root. Otherwise, when the nekojs
            // root itself is replaced by a symlink or reparse point, verifyInsideNekoRoot treats the link
            // target as the new root.
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

            int line = requestedLine > 0 ? requestedLine : -1;
            return Result.local(verified, line);
        } catch (InvalidPathException e) {
            return Result.unavailable(Status.INVALID_PATH);
        } catch (IOException e) {
            // Both root verifiers reject traversal, illegal links and reparse escapes. Those are real failures,
            // so they must not fall into the fallback.
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
        // Colons inside later path segments are left to Path parsing, the script-root check and the
        // real-path check. Only a colon before the first separator can be a scheme or a virtual module.
        if (separator != Integer.MAX_VALUE && colon > separator) {
            return false;
        }
        // The only colon allowed in the first segment belongs to a full Windows drive path; drive-relative
        // forms such as C:relative are still rejected.
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

    /** Resolution result. Only {@link Status#LOCAL_FILE} carries a non-null file and open target. */
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
     * A verified VS Code open target.
     *
     * <p>{@code vscodeFileUri} always means a file-level open. The line number travels through the
     * official CLI's native {@code --goto} argument. A POSIX file name that itself contains a colon
     * would collide with the line and column separator; gotoLine is then false and only the
     * verified file opens.</p>
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

        /**
         * Arguments passed to the process, without the code command itself. This only builds argv and
         * never concatenates a shell string.
         */
        public List<String> arguments() {
            return gotoLine
                    ? List.of("--goto", file + ":" + line)
                    : List.of(file.toString());
        }

        /**
         * Encodes each path segment as UTF-8 by hand. The name elements of a Path do not include the
         * root: the Windows drive letter and the UNC server/share both live in the root, so they must
         * be encoded explicitly, or the protocol URI loses the volume the open target is on.
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
            // A Windows absolute path allows one drive colon and nothing else. Any colon after the drive makes
            // the `file:line` suffix ambiguous (NTFS stream syntax included), so it must fall back to a
            // file-level open.
            boolean windowsDrive = firstColon == 1 && value.length() > 2 && value.charAt(1) == ':';
            return windowsDrive && value.indexOf(':', 2) < 0;
        }
    }
}


