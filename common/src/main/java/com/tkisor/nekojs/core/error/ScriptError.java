package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.api.ScriptType;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.SourceSection;
import com.tkisor.nekojs.api.data.ScriptId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScriptError {
    private final ScriptId errorId;
    private final ScriptContainer script;
    private final ScriptType scriptType;
    private final Throwable rawException;

    private String errorMessage;
    private int lineNumber = -1;
    private int columnNumber = -1;
    private String originalSymbolName = null;
    private String sourceCodeSnippet = "";
    private String errorPath = null;

    private String fallbackPath = "Unknown location";

    private volatile int occurrenceCount = 1;
    private final DefaultErrorTracker tracker;

    public ScriptError(ScriptContainer script, Throwable rawException, DefaultErrorTracker tracker) {
        this.tracker = tracker;
        this.errorId = script.id;
        this.script = script;
        this.scriptType = script.type;
        this.rawException = rawException;
        parseException();
    }

    public ScriptError(ScriptType scriptType, ScriptId errorId, String fallbackPath, Throwable rawException, DefaultErrorTracker tracker) {
        this.tracker = tracker;
        this.errorId = errorId;
        this.script = null;
        this.scriptType = scriptType;
        this.fallbackPath = fallbackPath;
        this.rawException = rawException;
        parseException();
    }

    private void parseException() {
        applySignature(parseSignature(tracker, rawException, script));
    }

    /**
     * 轻量解析（不读取源码文件）：仅提取错误信息、映射后的行列号等字段。
     * 既作为去重签名（{@code DefaultErrorTracker} 在构造完整 {@code ScriptError} 之前先比对），
     * 也作为完整解析 {@link #parseException()} 的输入，保证两者字段完全一致。
     */
    static ErrorSignature parseSignature(DefaultErrorTracker tracker, Throwable rawException, ScriptContainer script) {
        Throwable primary = primaryCause(rawException);
        if (primary instanceof NekoEsmLinkException linkException) {
            NekoEsmDiagnostic diagnostic = linkException.diagnostic();
            if (diagnostic == null) {
                return new ErrorSignature(bestMessage(primary), null, -1, -1, null, false, false, null, "");
            }
            String message = diagnostic.message();
            String errorPath = diagnostic.file() != null ? pathToDisplay(tracker, diagnostic.file()) : null;
            return new ErrorSignature(message != null ? message : "Unknown error", errorPath,
                    diagnostic.line(), diagnostic.column(), null, true, true, null, "");
        }
        PolyglotException polyglotException = findPolyglotException(rawException);
        if (polyglotException != null) {
            String errorMessage = bestMessage(primary);
            SourceSection sourceLocation = bestUserSourceLocation(tracker, polyglotException, script);
            if (sourceLocation != null) {
                int rawLine = sourceLocation.getStartLine();
                int rawColumn = sourceLocation.getStartColumn();
                CharSequence chars = sourceLocation.getCharacters();
                String jsSnippet = chars != null ? chars.toString().trim() : "";

                String displayPath = extractRelativePath(tracker, sourceLocation);
                SourceMapRegistry.OriginalPosition pos = SourceMapRegistry.getMappedPosition(displayPath, rawLine, rawColumn);
                String errorPath = pos.path != null && !pos.path.isBlank() ? pos.path : displayPath;
                return new ErrorSignature(errorMessage, errorPath, pos.line, pos.column, pos.name,
                        true, false, pos.sourceContent, usefulFallbackSnippet(jsSnippet));
            }
            return new ErrorSignature(errorMessage, null, -1, -1, null, false, false, null, "");
        }
        return new ErrorSignature(bestMessage(primary), null, -1, -1, null, false, false, null, "");
    }

    /** 将轻量解析结果写入实例字段；仅在 {@code buildSnippet} 时读取源码文件构造代码片段。 */
    private void applySignature(ErrorSignature signature) {
        this.errorMessage = signature.errorMessage;
        this.errorPath = signature.errorPath;
        this.lineNumber = signature.lineNumber;
        this.columnNumber = signature.columnNumber;
        this.originalSymbolName = signature.originalSymbolName;
        if (!signature.buildSnippet) {
            this.sourceCodeSnippet = "";
            return;
        }
        // ESM 诊断沿用旧 parseEsmDiagnostic 的 getDisplayPath() 语义；Polyglot 异常沿用旧逻辑的 errorPath。
        String snippetPath = signature.snippetFromDisplayPath ? getDisplayPath() : signature.errorPath;
        this.sourceCodeSnippet = buildSourceSnippet(snippetPath, signature.sourceContent, signature.fallbackSnippet);
    }

    /** 轻量解析结果（包内可见）：错误信息、映射后的行列号等去重签名所需字段。 */
    static final class ErrorSignature {
        final String errorMessage;
        final String errorPath;
        final int lineNumber;
        final int columnNumber;
        final String originalSymbolName;
        /** true = 需要读取源码文件构建代码片段；false = 片段为空串。 */
        final boolean buildSnippet;
        /** true = 片段路径用 getDisplayPath()（ESM 诊断）；false = 用 errorPath（Polyglot 异常）。 */
        final boolean snippetFromDisplayPath;
        final String sourceContent;
        final String fallbackSnippet;

        ErrorSignature(String errorMessage, String errorPath, int lineNumber, int columnNumber, String originalSymbolName,
                       boolean buildSnippet, boolean snippetFromDisplayPath, String sourceContent, String fallbackSnippet) {
            this.errorMessage = errorMessage;
            this.errorPath = errorPath;
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
            this.originalSymbolName = originalSymbolName;
            this.buildSnippet = buildSnippet;
            this.snippetFromDisplayPath = snippetFromDisplayPath;
            this.sourceContent = sourceContent;
            this.fallbackSnippet = fallbackSnippet;
        }
    }

    private String buildSourceSnippet(String displayPath, String sourceContent, String fallbackSnippet) {
        List<String> sourceLines = null;
        try {
            Path sourcePath = tracker.paths().root().resolve(displayPath).normalize().toAbsolutePath();
            Path root = tracker.paths().root().normalize().toAbsolutePath();
            if (sourcePath.startsWith(root) && Files.exists(sourcePath) && this.lineNumber > 0) {
                sourceLines = Files.readAllLines(sourcePath);
            }
        } catch (Exception ignored) { // source file unreadable → use embedded source content fallback
        }
        if ((sourceLines == null || sourceLines.isEmpty()) && sourceContent != null && !sourceContent.isEmpty()) {
            sourceLines = sourceContent.lines().toList();
        }
        if (sourceLines == null || this.lineNumber <= 0) {
            return fallbackSnippet;
        }

        int lineIndex = this.lineNumber - 1;
        if (lineIndex < 0 || lineIndex >= sourceLines.size()) {
            return fallbackSnippet;
        }
        int start = Math.max(0, lineIndex - 2);
        int end = Math.min(sourceLines.size() - 1, lineIndex + 2);
        StringBuilder snippet = new StringBuilder();

        for (int i = start; i <= end; i++) {
            int displayLine = i + 1;
            snippet.append(displayLine == this.lineNumber ? " > " : "   ");
            snippet.append(displayLine).append(" | ").append(sourceLines.get(i)).append("\n");

            if (displayLine == this.lineNumber && this.columnNumber > 0) {
                snippet.append("     | ").append(" ".repeat(Math.max(0, this.columnNumber - 1))).append("^\n");
            }
        }

        return snippet.toString().stripTrailing();
    }

    public void incrementOccurrence() {
        this.occurrenceCount++;
    }

    public void setOccurrenceCount(int occurrenceCount) {
        this.occurrenceCount = occurrenceCount;
    }

    public ScriptId getErrorId() { return errorId; }

    public ScriptContainer getScript() { return script; }

    public ScriptType getScriptType() { return scriptType; }

    public Throwable getRawException() { return rawException; }

    public int getLineNumber() { return lineNumber; }

    public int getColumnNumber() { return columnNumber; }

    public String getOriginalSymbolName() { return originalSymbolName; }

    public String getSourceCodeSnippet() { return sourceCodeSnippet; }

    public int getOccurrenceCount() { return occurrenceCount; }

    public String getErrorMessage() { return errorMessage != null ? errorMessage : "Unknown error"; }

    public String getDisplayPath() {
        if (errorPath != null && !errorPath.isBlank()) {
            return errorPath;
        }
        if (script != null) {
            return pathToDisplay(tracker, script.path);
        }
        return fallbackPath;
    }

    private static String extractRelativePath(DefaultErrorTracker tracker, SourceSection sourceLocation) {
        if (sourceLocation == null || sourceLocation.getSource() == null) return "Unknown location";
        return tracker.extractRelativePath(sourceLocation.getSource());
    }

    private static String pathToDisplay(DefaultErrorTracker tracker, Path path) {
        if (path == null) {
            return "Unknown location";
        }
        try {
            return tracker.paths().root().relativize(path.normalize().toAbsolutePath()).toString().replace('\\', '/');
        } catch (Exception ignored) { // path relativize fails (different filesystem) → return absolute path
            return path.toString().replace('\\', '/');
        }
    }

    public String getLogDetailText(boolean concise) {
        return concise ? getConciseDetailText() : getFullDetailText();
    }

    public String getConciseDetailText() {
        StringBuilder sb = new StringBuilder();
        sb.append("环境: ").append(scriptType != null ? scriptType.name() : "未知").append("\n");
        sb.append("位置: ").append(getDisplayPath());
        if (lineNumber > 0) {
            sb.append(":").append(lineNumber);
            if (columnNumber > 0) {
                sb.append(":").append(columnNumber);
            }
        }
        sb.append("\n");
        sb.append("原因: ").append(conciseMessage(getErrorMessage())).append("\n");
        if (occurrenceCount > 1) {
            sb.append("频次: 连续发生了 ").append(occurrenceCount).append(" 次\n");
        }
        if (!sourceCodeSnippet.isEmpty()) {
            sb.append("\n").append(sourceCodeSnippet).append("\n");
        }
        PolyglotException polyglotException = findPolyglotException(rawException);
        if (polyglotException != null) {
            String trace = firstGuestFrame(polyglotException);
            if (!trace.isEmpty()) {
                sb.append("\n").append(trace);
            }
        }
        return sb.toString().stripTrailing();
    }

    private static String conciseMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown error";
        }
        String normalized = message.strip();
        int syntaxStart = normalized.indexOf("SyntaxError:");
        if (syntaxStart >= 0) {
            int lineBreak = normalized.indexOf('\n', syntaxStart);
            return lineBreak >= 0 ? normalized.substring(syntaxStart, lineBreak).strip() : normalized.substring(syntaxStart).strip();
        }
        int causedBy = normalized.lastIndexOf(": ");
        if (causedBy > 0 && causedBy + 2 < normalized.length()) {
            String tail = normalized.substring(causedBy + 2).strip();
            if (!tail.isBlank()) {
                return tail;
            }
        }
        return normalized;
    }

    private static String usefulFallbackSnippet(String snippet) {
        if (snippet == null) {
            return "";
        }
        String trimmed = snippet.trim();
        return trimmed.length() <= 1 ? "" : trimmed;
    }

    private static SourceSection bestUserSourceLocation(DefaultErrorTracker tracker, PolyglotException exception, ScriptContainer script) {
        SourceSection fallback = tracker.getBestSourceLocation(exception);
        if (fallback != null && !isInternalFrame(extractRelativePath(tracker, fallback))) {
            return fallback;
        }
        for (PolyglotException.StackFrame frame : exception.getPolyglotStackTrace()) {
            if (!frame.isGuestFrame()) {
                continue;
            }
            SourceSection loc = frame.getSourceLocation();
            if (loc == null || loc.getSource() == null) {
                continue;
            }
            if (!isInternalFrame(extractRelativePath(tracker, loc))) {
                return loc;
            }
        }
        return fallback != null && script == null ? fallback : null;
    }

    private static PolyglotException findPolyglotException(Throwable throwable) {
        Throwable current = throwable;
        Set<Throwable> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            if (current instanceof PolyglotException polyglotException) {
                return polyglotException;
            }
            current = current.getCause();
        }
        return null;
    }

    private static Throwable primaryCause(Throwable throwable) {
        Throwable current = throwable;
        Throwable best = throwable;
        Set<Throwable> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            if (current instanceof NekoEsmLinkException) {
                return current;
            }
            if (current instanceof PolyglotException) {
                best = current;
            } else if (current.getMessage() != null && !current.getMessage().isBlank()) {
                best = current;
            }
            current = current.getCause();
        }
        return best == null ? throwable : best;
    }

    private static String bestMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        String message = throwable.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return throwable.toString();
    }

    private static boolean isInternalFrame(String path) {
        if (path == null || path.isBlank()) {
            return true;
        }
        String normalized = path.replace('\\', '/');
        return normalized.contains("/nekojs/node/internal/")
                || normalized.contains("/nekojs/node/modules/")
                || normalized.startsWith("nekojs/node/internal/")
                || normalized.startsWith("nekojs/node/modules/")
                || normalized.startsWith("truffle:") && normalized.contains("/nekojs/node/");
    }

    private String firstGuestFrame(PolyglotException exception) {
        for (PolyglotException.StackFrame frame : exception.getPolyglotStackTrace()) {
            if (!frame.isGuestFrame()) {
                continue;
            }
            SourceSection loc = frame.getSourceLocation();
            if (loc == null || loc.getSource() == null) {
                continue;
            }
            String path = tracker.extractRelativePath(loc.getSource());
            if (isInternalFrame(path)) {
                continue;
            }
            SourceMapRegistry.OriginalPosition pos = SourceMapRegistry.getMappedPosition(path, loc.getStartLine(), loc.getStartColumn());
            String mappedPath = pos.path != null && !pos.path.isBlank() ? pos.path : path;
            String rootName = frame.getRootName();
            if (pos.name != null && !pos.name.isEmpty()) {
                rootName = pos.name;
            } else if (rootName == null || rootName.isEmpty() || rootName.equals(":program")) {
                rootName = "<module>";
            }
            return "堆栈: at " + rootName + " (" + mappedPath + ":" + pos.line + ")";
        }
        return "";
    }

    public String getFullDetailText() {
        StringBuilder sb = new StringBuilder();
        sb.append("环境: ").append(scriptType != null ? scriptType.name() : "未知").append("\n");
        sb.append("脚本: ").append(getDisplayPath()).append("\n");

        if (occurrenceCount > 1) {
            sb.append("频次: 连续发生了 ").append(occurrenceCount).append(" 次\n");
        }

        if (lineNumber != -1 && !sourceCodeSnippet.isEmpty()) {
            sb.append("\n>> 异常代码片段 (");
            if (originalSymbolName != null) {
                sb.append("于方法 `").append(originalSymbolName).append("` 行 ").append(lineNumber);
            } else {
                sb.append("行 ").append(lineNumber);
            }
            if (columnNumber > 0) {
                sb.append(", 列 ").append(columnNumber);
            }
            sb.append("):\n");
            sb.append(sourceCodeSnippet).append("\n");
        }

        sb.append("\n");
        PolyglotException polyglotException = findPolyglotException(rawException);
        if (polyglotException != null) {
            sb.append(tracker.getMappedStackTrace(polyglotException));
        } else {
            sb.append(getErrorMessage()).append("\n");
        }

        return sb.toString();
    }
}