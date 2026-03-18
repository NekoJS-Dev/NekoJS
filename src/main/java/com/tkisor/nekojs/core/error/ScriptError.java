package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.ScriptContainer;
import lombok.Getter;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;

public class ScriptError {
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

    public ScriptError(ScriptContainer script, Throwable rawException) {
        this.script = script;
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
                this.sourceCodeSnippet = chars != null ? chars.toString() : "";
            }
        } else {
            this.errorMessage = rawException.toString();
        }
    }

    public String getErrorMessage() { return errorMessage != null ? errorMessage : "未知错误"; }

    /**
     * 为 GUI 渲染提供格式化后的详细文本
     */
    public String getFullDetailText() {
        StringBuilder sb = new StringBuilder();

        String cleanPath = NekoJSPaths.ROOT.relativize(script.path).toString().replace('\\', '/');

        sb.append("脚本: ").append(cleanPath).append("\n");
        sb.append("错误: ").append(getErrorMessage()).append("\n");
        if (lineNumber != -1) {
            sb.append("位置: 第 ").append(lineNumber).append(" 行, 第 ").append(columnNumber).append(" 列\n");
            sb.append("代码段:\n").append(sourceCodeSnippet).append("\n");
        }
        return sb.toString();
    }
}