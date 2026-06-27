package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 栈追踪映射工具：将 GraalVM 原始栈帧路径映射为 source map 解析后的真实位置。
 */
final class StackTraceMapper {
    private static final Pattern STACK_LOCATION = Pattern.compile("(\\()([^()\\s]+):(\\d+)(?::(\\d+))?(\\))");

    private StackTraceMapper() {}

    static String mapStackText(String stack) {
        String[] lines = stack.split("\\R", -1);
        StringBuilder mapped = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                mapped.append('\n');
            }
            mapped.append(mapStackLine(lines[i]));
        }
        return mapped.toString();
    }

    private static String mapStackLine(String line) {
        Matcher matcher = STACK_LOCATION.matcher(line);
        StringBuilder mapped = new StringBuilder();
        while (matcher.find()) {
            String path = matcher.group(2);
            String displayPath = NekoEsmVirtualModuleRegistry.displayPath(path);
            if (displayPath == null) {
                displayPath = path;
            }
            int lineNumber = Integer.parseInt(matcher.group(3));
            int columnNumber = matcher.group(4) == null ? 1 : Integer.parseInt(matcher.group(4));
            SourceMapRegistry.OriginalPosition position = SourceMapRegistry.getMappedPosition(displayPath, lineNumber, columnNumber);
            String mappedPath = position.path != null && !position.path.isBlank() ? position.path : displayPath;
            String columnSuffix = matcher.group(4) == null ? "" : ":" + position.column;
            matcher.appendReplacement(mapped, Matcher.quoteReplacement(matcher.group(1) + mappedPath + ":" + position.line + columnSuffix + matcher.group(5)));
        }
        matcher.appendTail(mapped);
        return mapped.toString();
    }
}
