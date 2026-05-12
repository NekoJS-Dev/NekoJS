package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptType;
import lombok.Getter;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.SourceSection;
import com.tkisor.nekojs.api.data.ScriptId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ScriptError {
    @Getter
    private final ScriptId errorId;
    @Getter
    private final ScriptContainer script;
    @Getter
    private final ScriptType scriptType;
    @Getter
    private final Throwable rawException;

    private String errorMessage;
    @Getter
    private int lineNumber = -1;
    @Getter
    private int columnNumber = -1;
    @Getter
    private String originalSymbolName = null;
    @Getter
    private String sourceCodeSnippet = "";

    private String fallbackPath = "Unknown location";

    @Getter
    private int occurrenceCount = 1;

    public ScriptError(ScriptContainer script, Throwable rawException) {
        this.errorId = script.id;
        this.script = script;
        this.scriptType = script.type;
        this.rawException = rawException;
        parseException();
    }

    public ScriptError(ScriptType scriptType, ScriptId errorId, String fallbackPath, Throwable rawException) {
        this.errorId = errorId;
        this.script = null;
        this.scriptType = scriptType;
        this.fallbackPath = fallbackPath;
        this.rawException = rawException;
        parseException();
    }

    private void parseException() {
        if (rawException instanceof PolyglotException polyglotException) {
            this.errorMessage = polyglotException.getMessage();

            SourceSection sourceLocation = polyglotException.getSourceLocation();
            if (sourceLocation == null) {
                for (PolyglotException.StackFrame frame : polyglotException.getPolyglotStackTrace()) {
                    if (frame.isGuestFrame() && frame.getSourceLocation() != null) {
                        sourceLocation = frame.getSourceLocation();
                        break;
                    }
                }
            }

            if (sourceLocation != null) {
                int rawLine = sourceLocation.getStartLine();
                int rawColumn = sourceLocation.getStartColumn();
                CharSequence chars = sourceLocation.getCharacters();
                String jsSnippet = chars != null ? chars.toString().trim() : "";

                String displayPath = script != null ? getDisplayPath() : extractRelativePath(sourceLocation);
                SourceMapRegistry.OriginalPosition pos = SourceMapRegistry.getMappedPosition(displayPath, rawLine, rawColumn);
                this.lineNumber = pos.line;
                this.columnNumber = pos.column;
                this.originalSymbolName = pos.name;
                this.sourceCodeSnippet = buildSourceSnippet(displayPath, jsSnippet);
            }
        } else {
            this.errorMessage = rawException.toString();
        }
    }

    private String buildSourceSnippet(String displayPath, String fallbackSnippet) {
        try {
            Path sourcePath = NekoJSPaths.ROOT.resolve(displayPath);
            if (Files.exists(sourcePath) && this.lineNumber > 0) {
                List<String> allLines = Files.readAllLines(sourcePath);
                int lineIndex = this.lineNumber - 1;
                if (lineIndex >= 0 && lineIndex < allLines.size()) {
                    int start = Math.max(0, lineIndex - 2);
                    int end = Math.min(allLines.size() - 1, lineIndex + 2);
                    StringBuilder snippet = new StringBuilder();

                    for (int i = start; i <= end; i++) {
                        int displayLine = i + 1;
                        snippet.append(displayLine == this.lineNumber ? " > " : "   ");
                        snippet.append(displayLine).append(" | ").append(allLines.get(i)).append("\n");

                        if (displayLine == this.lineNumber && this.columnNumber > 0) {
                            snippet.append("     | ").append(" ".repeat(Math.max(0, this.columnNumber - 1))).append("^\n");
                        }
                    }

                    return snippet.toString().stripTrailing();
                }
            }
        } catch (Exception ignored) {
        }
        return fallbackSnippet;
    }

    public void incrementOccurrence() {
        this.occurrenceCount++;
    }

    public void setOccurrenceCount(int occurrenceCount) {
        this.occurrenceCount = occurrenceCount;
    }

    public String getErrorMessage() { return errorMessage != null ? errorMessage : "Unknown error"; }

    public String getDisplayPath() {
        if (script != null) {
            return NekoJSPaths.ROOT.relativize(script.path).toString().replace('\\', '/');
        }
        return fallbackPath;
    }

    private static String extractRelativePath(SourceSection sourceLocation) {
        if (sourceLocation == null || sourceLocation.getSource() == null) return "Unknown location";
        var source = sourceLocation.getSource();
        if (source.getPath() != null) {
            try {
                return NekoJSPaths.ROOT.relativize(Path.of(source.getPath())).toString().replace('\\', '/');
            } catch (Exception ignored) {
                return source.getPath().replace('\\', '/');
            }
        }
        if (source.getURI() != null) {
            return source.getURI().toString().replace(NekoJSPaths.ROOT.toUri().toString(), "").replace('\\', '/');
        }
        return source.getName();
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
        if (rawException instanceof PolyglotException pe) {
            sb.append(NekoErrorTracker.getMappedStackTrace(pe));
        } else {
            sb.append(getErrorMessage()).append("\n");
        }

        return sb.toString();
    }
}