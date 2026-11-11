package com.tkisor.nekojs.probe.types;

import java.util.List;

/** Formats callable declarations from already-rendered parameter fragments without parsing their types. */
public final class TypeScriptDeclarationFormatter {
    public static final int DEFAULT_MAX_WIDTH = 120;
    private TypeScriptDeclarationFormatter() {}

    /** Head excludes the opening parenthesis; suffix excludes the closing one. Appends a final newline. */
    public static void appendCallable(StringBuilder output, int indent, String head,
                                      List<String> parameters, String suffix) {
        String prefix = " ".repeat(indent);
        String signature = head + "(" + String.join(", ", parameters) + ")" + suffix;
        if (parameters.isEmpty() || (indent + signature.length() <= DEFAULT_MAX_WIDTH && signature.indexOf('\n') < 0)) {
            output.append(prefix).append(signature).append('\n');
            return;
        }
        output.append(prefix).append(head).append("(\n");
        String continuation = prefix + "    ";
        for (int index = 0; index < parameters.size(); index++) {
            output.append(continuation).append(parameters.get(index));
            if (index + 1 < parameters.size()) output.append(',');
            output.append('\n');
        }
        output.append(prefix).append(')').append(suffix).append('\n');
    }
}
