package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptType;
import lombok.Getter;
import net.minecraft.resources.Identifier;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;

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
                this.columnNumber = sourceLocation.getStartColumn();
                CharSequence chars = sourceLocation.getCharacters();
                this.sourceCodeSnippet = chars != null ? chars.toString().trim() : "";

                this.lineNumber = SourceMapRegistry.getMappedLine(getDisplayPath(), rawLine);
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
            sb.append("\n>> 异常代码片段 (行 ").append(lineNumber).append("):\n");
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