package com.tkisor.nekojs.core.preflight;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.compiler.FrontendSemantics;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Diagnostic;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Request;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Scope;
import com.tkisor.nekojs.api.preflight.ScriptPreflight.Stage;
import com.tkisor.nekojs.core.compiler.ScriptSourcePreflight;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinJavaScriptPreflightTest {
    private static final Path FILE = Path.of("server_scripts", "preflight.js");
    private static final String SOURCE = "Fixture.missing();\r\n"
            + "Fixture // binding\r\n"
            + "/* continuation */ .configure(config => config /* receiver */ .missing());";

    @BeforeAll
    static void initializePlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void returnsTheSameDiagnosticsAsTheExistingJsChecks() throws Exception {
        var schema = schema(false);
        try (var legacy = new ScriptSourcePreflight.Cache(); var worker = BuiltinJavaScriptPreflight.INSTANCE.open()) {
            for (Scope scope : Scope.values()) {
                List<Observed> before = new ArrayList<>();
                var receiver = new ScriptBindingSchema.View(schema, Set.of("console"), item -> {
                    var diagnostic = ((NekoEsmLinkException) item.throwable()).diagnostic();
                    before.add(new Observed(item.callbackKind(), diagnostic.line(), diagnostic.column(), diagnostic.message()));
                });
                if (scope == Scope.FULL) legacy.validate(FILE, SOURCE, receiver);
                else legacy.validateBindings(FILE, SOURCE, receiver);
                var after = worker.check(request(Stage.SOURCE, scope, SOURCE, schema));
                assertEquals(scope == Scope.FULL ? 2 : 1, before.size());
                assertEquals(before, after.stream().map(BuiltinJavaScriptPreflightTest::observe).toList());
            }
            assertTrue(worker.check(request(Stage.SOURCE, Scope.FULL,
                    SOURCE.replace(".missing()", ".valid()"), schema(true))).isEmpty());
            assertEquals(1, worker.check(request(Stage.OUTPUT, Scope.BINDINGS_ONLY, SOURCE, schema)).size());
        }
    }

    static Request request(Stage stage, Scope scope, String source,
                           Map<String, ScriptBindingSchema.BindingMembers> schema) {
        var input = stage == Stage.SOURCE ? FrontendSemantics.JAVASCRIPT_SOURCE : FrontendSemantics.JAVASCRIPT_OUTPUT;
        return new Request(FILE, ScriptType.SERVER, "nekojs:javascript", stage, scope, input,
                Set.of(), source, null, schema, Set.of("console"));
    }

    static Map<String, ScriptBindingSchema.BindingMembers> schema(boolean allowMissing) {
        return Map.of("Fixture", new ScriptBindingSchema.BindingMembers(
                allowMissing ? Set.of("valid", "missing", "configure") : Set.of("valid", "configure"),
                Set.of(Fixture.class)));
    }

    private static Observed observe(Diagnostic diagnostic) {
        return new Observed(diagnostic.dedupKey(), diagnostic.position().line(), diagnostic.position().column(),
                diagnostic.message());
    }

    public static class Fixture {
        public void valid() {}
        public void configure(Consumer<FixtureConfig> configure) {}
    }

    public static class FixtureConfig {
        public void valid() {}
    }

    private record Observed(String code, int line, int column, String message) {}
}
