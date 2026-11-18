package com.tkisor.nekojs.api.compiler;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Declares a frontend's source and generated representations and additional semantic capabilities. */
public record FrontendSemantics(Input source, Input output, Set<String> capabilities) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public static final String SOURCE_TEXT = "nekojs:source-text";
    public static final String GENERATED_TEXT = "nekojs:generated-text";
    public static final String JAVASCRIPT = "nekojs:javascript";
    public static final String TYPESCRIPT_AUTHORING = "nekojs:typescript-authoring/v1";
    public static final String PYTHON_AUTHORING = "nekojs:python-authoring/v1";

    public static final Input JAVASCRIPT_SOURCE = new Input(SOURCE_TEXT, JAVASCRIPT);
    public static final Input JAVASCRIPT_OUTPUT = new Input(GENERATED_TEXT, JAVASCRIPT);

    public FrontendSemantics {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(output, "output");
        capabilities = Set.copyOf(capabilities);
        for (String capability : capabilities) requireId(capability);
    }

    /** The representation and dialect a consumer must understand to handle this input. */
    public record Input(String representation, String dialect) {
        public Input {
            requireId(representation);
            requireId(dialect);
        }
    }

    private static void requireId(String value) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new IllegalArgumentException("[NEKO-1026] Invalid frontend semantic identifier: " + value);
        }
    }
}
