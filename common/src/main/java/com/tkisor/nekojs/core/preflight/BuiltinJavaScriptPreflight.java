package com.tkisor.nekojs.core.preflight;

import com.tkisor.nekojs.api.compiler.FrontendSemantics;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.preflight.ScriptPreflight;
import com.tkisor.nekojs.core.compiler.ScriptSourcePreflight;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exposes the existing JavaScript validators through the preflight contract. */
public enum BuiltinJavaScriptPreflight implements ScriptPreflight {
    INSTANCE;

    public static final String ID = "nekojs:javascript";
    private static final Descriptor DESCRIPTOR = new Descriptor(ID, "1", Map.of(
            Stage.SOURCE, Set.of(FrontendSemantics.JAVASCRIPT_SOURCE),
            Stage.OUTPUT, Set.of(FrontendSemantics.JAVASCRIPT_OUTPUT)),
            Set.of(Scope.FULL, Scope.BINDINGS_ONLY), Set.of());

    @Override
    public Descriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Worker open() {
        return new Worker() {
            private final ScriptSourcePreflight.Cache facts = new ScriptSourcePreflight.Cache();

            @Override
            public List<Diagnostic> check(Request request) {
                var findings = new ArrayList<Diagnostic>();
                var view = new ScriptBindingSchema.View(request.bindings(), request.globals(), report -> {
                    var diagnostic = ((NekoEsmLinkException) report.throwable()).diagnostic();
                    String key = report.callbackKind();
                    int separator = key.indexOf(' ');
                    String code = separator < 0 ? key : key.substring(0, separator);
                    findings.add(new Diagnostic(code, Severity.ERROR, diagnostic.message(),
                            new Position(diagnostic.line(), diagnostic.column(), diagnostic.span().start()), key));
                });
                facts.check(request.path(), request.text(), view, request.stage().name(),
                        request.scope() == Scope.BINDINGS_ONLY);
                return List.copyOf(findings);
            }

            @Override
            public void close() {
                facts.close();
            }
        };
    }
}
