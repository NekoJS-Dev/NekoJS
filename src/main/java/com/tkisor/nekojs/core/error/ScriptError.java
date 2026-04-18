package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptType;
import lombok.Getter;
import net.minecraft.resources.Identifier;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.SourceSection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ScriptError {
    @Getter
    private final Identifier errorId;
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

    private String fallbackPath = "未知位置";

    @Getter
    private int occurrenceCount = 1;

    public ScriptError(ScriptContainer script, Throwable rawException) {
        this.errorId = script.id;
        this.script = script;
        this.scriptType = script.type;
        this.rawException = rawException;
        parseException();
    }

    public ScriptError(ScriptType scriptType, Identifier errorId, String fallbackPath, Throwable rawException) {
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

                SourceMapRegistry.OriginalPosition pos = SourceMapRegistry.getMappedPosition(getDisplayPath(), rawLine, rawColumn);
                this.lineNumber = pos.line;
                this.columnNumber = pos.column;
                this.originalSymbolName = pos.name;

                String finalSnippet = jsSnippet;
                try {
                    Path sourcePath = NekoJSPaths.ROOT.resolve(getDisplayPath());
                    if (Files.exists(sourcePath) && this.lineNumber > 0) {
                        List<String> allLines = Files.readAllLines(sourcePath);
                        int lineIndex = this.lineNumber - 1; // 1-based 转 0-based

                        if (lineIndex >= 0 && lineIndex < allLines.size()) {
                            finalSnippet = allLines.get(lineIndex).trim();

                            int offset = 0;
                            while ((finalSnippet.startsWith("//") || finalSnippet.startsWith("/*") || finalSnippet.startsWith("*") || finalSnippet.isEmpty())
                                    && (lineIndex + 1) < allLines.size()) {
                                lineIndex++;
                                offset++;
                                finalSnippet = allLines.get(lineIndex).trim();
                            }

                            this.lineNumber += offset;
                        }
                    }
                } catch (Exception e) {
                }

                this.sourceCodeSnippet = finalSnippet;
            }
        } else {
            this.errorMessage = rawException.toString();
        }
    }

    public void incrementOccurrence() {
        this.occurrenceCount++;
    }

    public String getErrorMessage() { return errorMessage != null ? errorMessage : "未知错误"; }

    public String getDisplayPath() {
        if (script != null) {
            return NekoJSPaths.ROOT.relativize(script.path).toString().replace('\\', '/');
        }
        return fallbackPath;
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