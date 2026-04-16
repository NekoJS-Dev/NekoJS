package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.ScriptContainer;
import lombok.Getter;
import net.minecraft.resources.Identifier;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.SourceSection;

public class ScriptError {
    @Getter
    private final Identifier errorId;
    @Getter
    private final ScriptContainer script;
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
        this.rawException = rawException;
        parseException();
    }

    public ScriptError(Identifier errorId, String fallbackPath, Throwable rawException) {
        this.errorId = errorId;
        this.script = null;
        this.fallbackPath = fallbackPath;
        this.rawException = rawException;
        parseException();
    }

    private void parseException() {
        if (rawException instanceof PolyglotException polyglotException) {
            this.errorMessage = polyglotException.getMessage();
            SourceSection sourceLocation = polyglotException.getSourceLocation();
            if (sourceLocation != null) {
                this.lineNumber = sourceLocation.getStartLine();
                this.columnNumber = sourceLocation.getStartColumn();
                CharSequence chars = sourceLocation.getCharacters();
                this.sourceCodeSnippet = chars != null ? chars.toString().trim() : "";
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
        sb.append("脚本: ").append(getDisplayPath()).append("\n");
        sb.append("错误: ").append(getErrorMessage()).append("\n");
        if (occurrenceCount > 1) {
            sb.append("频次: 连续发生了 ").append(occurrenceCount).append(" 次\n");
        }
        if (lineNumber != -1) {
            sb.append("位置: 第 ").append(lineNumber).append(" 行, 第 ").append(columnNumber).append(" 列\n");
            sb.append("代码:\n> ").append(sourceCodeSnippet).append("\n");
        }
        return sb.toString();
    }
}