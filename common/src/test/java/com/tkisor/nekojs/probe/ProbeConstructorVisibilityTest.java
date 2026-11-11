package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.ScriptInternalConstructor;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.modification.ModificationApplier;
import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import com.tkisor.nekojs.core.modification.ModificationDeclaration;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProbeConstructorVisibilityTest {
    public static final class LifecycleEvent {
        private String label;

        @ScriptInternalConstructor
        public LifecycleEvent(ModificationCandidatePlan plan) {
            label = "framework-owned";
        }

        public LifecycleEvent(String label) {
            this.label = label;
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String status() { return "active:" + label; }
    }

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void internalConstructionIsExcludedWithoutLosingOrdinaryConstructorsOrBeanSurface() {
        assertEquals(2, LifecycleEvent.class.getConstructors().length);
        var declaration = new TypeReflector().reflect(LifecycleEvent.class);

        assertEquals(1, declaration.constructors.size());
        var parameter = declaration.constructors.getFirst().params.getFirst();
        assertEquals(String.class, parameter.type.sourceType);
        var getter = declaration.methods.stream().filter(method -> method.name.equals("getLabel"))
                .findFirst().orElseThrow();
        assertTrue(getter.isGetter);
        assertEquals("label", getter.property);
        assertNotNull(getter.setterParamType);
        assertEquals(String.class, getter.setterParamType.sourceType);
        assertTrue(declaration.methods.stream().anyMatch(method -> method.name.equals("status")));
    }

    @Test
    void internalConstructorParametersDoNotSeedTheDeclarationClosure() {
        var snapshot = new NekoScriptCatalogSnapshot(List.of(),
                List.of(BindingCatalogEntry.of("LifecycleEvent", ScriptType.SERVER, LifecycleEvent.class, false)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs", "java.lang"), List.of(), List.of(), List.of(), 1, "SMART"));

        var collected = ProbeCoordinator.collectClasses(snapshot, config);

        assertTrue(collected.contains(LifecycleEvent.class), collected::toString);
        assertTrue(collected.contains(String.class), collected::toString);
        assertFalse(collected.contains(ModificationCandidatePlan.class), collected::toString);
    }

    @Test
    void declarationAnnotationPreservesJavaAndNativeGraalConstruction() {
        var plan = new ModificationCandidatePlan(new ModificationApplier() {
            @Override public String adapterId() { return "constructor-visibility-fixture"; }
            @Override public void preflight(List<ModificationDeclaration> declarations) {}
            @Override public void apply(List<ModificationDeclaration> declarations) {}
        });
        assertEquals("active:framework-owned", new LifecycleEvent(plan).status());

        try (var context = Context.newBuilder("js").allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(name -> name.equals(LifecycleEvent.class.getName()))
                .option("engine.WarnInterpreterOnly", "false").build()) {
            context.getBindings("js").putMember("plan", plan);
            var event = context.eval("js", "new (Java.type('" + LifecycleEvent.class.getName() + "'))(plan)");
            assertEquals("active:framework-owned", event.invokeMember("status").asString());
            assertEquals("framework-owned", event.invokeMember("getLabel").asString());
            event.invokeMember("setLabel", "updated");
            assertEquals("active:updated", event.invokeMember("status").asString());
        }
    }
}
