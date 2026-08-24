package com.tkisor.nekojs.core.compiler;

import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.io.IOAccess;

final class CompilerExecutionAssertions {
    private static final String AUTOMATIC_RUNTIME_IMPORT =
        "(?m)\\Aimport \\{ [^\\r\\n]+ } from 'nekojs/jsx-runtime';\\R";

    private CompilerExecutionAssertions() {}

    static void parse(String code) {
        try (Context context = restrictedContext()) {
            context.parse(source(code));
        }
    }

    /** ES module 模式 parse-only（.mjs 触发）：含 import/export 语句的擦除产物必须这样验证。*/
    static void parseModule(String code) {
        try (Context context = restrictedContext()) {
            context.parse(Source.newBuilder("js", code, "corpus.mjs").build());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("failed to build module source", e);
        }
    }

    static Evaluation eval(String code) {
        Context context = restrictedContext();
        try {
            return new Evaluation(context, context.eval(source(code)));
        } catch (RuntimeException | Error failure) {
            context.close();
            throw failure;
        }
    }

    static Evaluation evalAutomatic(String code, String runtimeBindings) {
        String executable = code.replaceFirst(AUTOMATIC_RUNTIME_IMPORT, "");
        if (executable.equals(code)) {
            throw new AssertionError("Expected the automatic JSX runtime import at the start of output: " + code);
        }
        return eval(runtimeBindings + "\n" + executable);
    }

    private static Context restrictedContext() {
        return Context.newBuilder("js")
            .allowHostAccess(HostAccess.NONE)
            .allowHostClassLookup(className -> false)
            .allowIO(IOAccess.NONE)
            .allowCreateThread(false)
            .allowCreateProcess(false)
            .build();
    }

    private static Source source(String code) {
        return Source.create("js", code);
    }

    static final class Evaluation implements AutoCloseable {
        private final Context context;
        private final Value value;

        private Evaluation(Context context, Value value) {
            this.context = context;
            this.value = value;
        }

        Value value() {
            return value;
        }

        @Override
        public void close() {
            context.close();
        }
    }
}
