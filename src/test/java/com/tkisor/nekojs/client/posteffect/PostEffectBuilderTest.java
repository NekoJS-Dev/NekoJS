//? if neoforge {
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.posteffect.PostEffectApplier;
import com.tkisor.nekojs.core.posteffect.PostEffectCandidatePlan;
import com.tkisor.nekojs.core.posteffect.PostEffectDeclaration;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PostEffectBuilderTest {
    private static final PostEffectApplier NO_EFFECTS = new PostEffectApplier() {
        public String adapterId() { return "builder-test"; }
        public void preflight(List<PostEffectDeclaration> declarations) {}
        public void apply(List<PostEffectDeclaration> declarations) { fail("Collection must stay inert"); }
    };

    private static Context context(PostEffectEventJS event) {
        Context context = Context.newBuilder("js").allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest").build();
        context.getBindings("js").putMember("event", event);
        return context;
    }

    private static PostEffectCandidatePlan plan() { return PostEffectCandidatePlan.beginBatch(NO_EFFECTS, 1, Set.of()); }

    @Test
    void realGraalOverloadsAcceptCallbacksAndLegacyObjectsAndSealCapturedBuilder() {
        var plan = plan();
        try (Context context = context(new PostEffectEventJS(plan))) {
            assertTrue(context.eval("js", "event.register('nekojs:callback', b => { globalThis.saved = b; if (b.chainJson !== '' || b.getChainJson() !== '') throw new Error('unset'); b.setChainJson('{}'); if (b.getChainJson() !== '{}') throw new Error('accessor'); })").asBoolean(),
                    () -> "Callback rejected: " + plan.collectionError()
                            + "; callback entered=" + context.eval("js", "typeof saved !== 'undefined'").asBoolean());
            assertTrue(context.eval("js", "event.register('nekojs:object', {chainJson:'{}'})").asBoolean(), plan::collectionError);
            assertEquals(2, plan.installCount());
            assertThrows(PolyglotException.class, () -> context.eval("js", "saved.chainJson = 'mutated'"));
            assertEquals(2, plan.installCount());
            assertNull(plan.collectionError());
            assertTrue(plan.finish().stream().allMatch(d -> d.chainJson().equals("{}")));
        }
    }

    @Test
    void caughtInvalidAssignmentsAndCallbackExceptionsPoisonTheWholeCandidate() {
        for (String callback : List.of("b => { try { b.typo = 3; } catch(e) {} b.chainJson='{}'; }",
                "b => { try { b.chainJson = 3; } catch(e) {} b.chainJson='{}'; }",
                "b => { b.chainJson='{}'; throw new Error('stop'); }",
                "b => { try { b.setChainJson('{}', 'extra'); } catch(e) {} b.chainJson='{}'; }",
                "b => { try { delete b.chainJson; } catch(e) {} b.chainJson='{}'; }")) {
            var plan = plan();
            try (Context context = context(new PostEffectEventJS(plan))) {
                String entered = callback.replace("b => {", "b => { globalThis.callbackEntered = true;");
                assertFalse(context.eval("js", "event.register('nekojs:bad', " + entered + ")").asBoolean());
                assertTrue(context.eval("js", "globalThis.callbackEntered === true").asBoolean(),
                        () -> "Invalid callback was never entered: " + plan.collectionError());
                assertEquals(0, plan.installCount());
                plan.finish();
                assertThrows(IllegalStateException.class, plan::preflight);
            }
        }
    }

    @Test
    void legacyObjectUnknownFieldsAndWrongTypesAreRejectedNotIgnored() {
        for (String options : List.of("{chainJson:'{}',typo:1}", "{chainJson:3}", "{chainJson:null}")) {
            var plan = plan();
            try (Context context = context(new PostEffectEventJS(plan))) {
                assertFalse(context.eval("js", "event.register('nekojs:bad', " + options + ")").asBoolean());
                assertNotNull(plan.collectionError());
                assertEquals(0, plan.installCount());
            }
        }
    }

    @Test
    void blurNumericLimitsApplyToBuildersAndLegacyObjectsBeforeCollection() {
        if (!PostEffectOptions.SCHEMA.hasField("blurRadius")) {
            var plan = plan();
            assertFalse(new PostEffectEventJS(plan).register("nekojs:unsupported", Map.of("blurRadius", 0)));
            assertEquals(0, plan.installCount());
            return;
        }
        for (String values : List.of("blurRadius:0,blurRounds:1", "blurRadius:64,blurRounds:8")) {
            var plan = plan();
            try (Context context = context(new PostEffectEventJS(plan))) {
                assertTrue(context.eval("js", "event.register('nekojs:object', {chainJson:'{}'," + values + "})").asBoolean());
                String assignments = values.replace(":", "=").replace(",", ";b.");
                assertTrue(context.eval("js", "event.register('nekojs:builder', b => {b.chainJson='{}';b." + assignments + ";})").asBoolean());
                assertEquals(2, plan.installCount());
                assertNull(plan.collectionError());
            }
        }
        for (String invalid : List.of("blurRadius:-0.1", "blurRadius:64.1", "blurRadius:NaN",
                "blurRadius:Infinity", "blurRounds:0", "blurRounds:9", "blurRounds:1.5",
                "blurRounds:NaN", "blurRounds:Infinity")) {
            for (boolean builder : List.of(false, true)) {
                var plan = plan();
                try (Context context = context(new PostEffectEventJS(plan))) {
                    String options = builder
                            ? "b => {try {b." + invalid.replace(":", "=") + ";} catch(e) {} b.chainJson='{}';}"
                            : "{chainJson:'{}'," + invalid + "}";
                    if (builder) options = options.replace("b => {", "b => { globalThis.callbackEntered = true;");
                    assertFalse(context.eval("js", "event.register('nekojs:invalid', " + options + ")").asBoolean(), options);
                    if (builder) {
                        assertTrue(context.eval("js", "globalThis.callbackEntered === true").asBoolean(),
                                () -> "Invalid numeric callback was never entered: " + plan.collectionError());
                    }
                    assertEquals(0, plan.installCount());
                    assertNotNull(plan.collectionError());
                    plan.finish();
                    assertThrows(IllegalStateException.class, plan::preflight);
                }
            }
        }
    }

    @Test
    void guestDispatcherPreservesPayloadMembersAndRejectsMalformedCallsBeforeCollection() {
        var valid = plan();
        try (Context context = context(new PostEffectEventJS(valid))) {
            assertEquals(0, context.eval("js", "event.declaredCount").asInt());
            assertEquals(0, context.eval("js", "event.getDeclaredCount()").asInt());
            assertTrue(context.eval("js", "event.register('nekojs:valid', b => { b.chainJson='{}'; })").asBoolean(), valid::collectionError);
            assertEquals(1, context.eval("js", "event.declaredCount").asInt());
            assertEquals(1, context.eval("js", "event.getDeclaredCount()").asInt());
            assertTrue(context.eval("js", "event.unregister('nekojs:valid')").asBoolean(), valid::collectionError);
            assertEquals(0, context.eval("js", "event.declaredCount").asInt());
            assertEquals(0, context.eval("js", "event.getDeclaredCount()").asInt());
            assertTrue(context.eval("js", "['register','unregister','getDeclaredCount','declaredCount'].every(key => Object.keys(event).includes(key))").asBoolean());
            assertNull(valid.collectionError());
        }
        for (String invocation : List.of("event.register()", "event.register('nekojs:bad')",
                "event.register('nekojs:bad', {chainJson:'{}'}, 1)",
                "event.register(1, {chainJson:'{}'})", "event.register('nekojs:bad', null)",
                "event.register('nekojs:bad', [])", "event.register('nekojs:bad', 3)",
                "event.register('nekojs:bad', 'text')", "event.unregister()", "event.unregister(1)",
                "event.unregister('nekojs:bad', 1)", "event.getDeclaredCount(1)")) {
            var invalid = plan();
            try (Context context = context(new PostEffectEventJS(invalid))) {
                assertFalse(context.eval("js", invocation).asBoolean(), invocation);
                assertEquals(0, invalid.installCount(), invocation);
                assertNotNull(invalid.collectionError(), invocation);
                invalid.finish();
                assertThrows(IllegalStateException.class, invalid::preflight, invocation);
            }
        }
        for (String write : List.of("event.declaredCount = 4", "delete event.declaredCount")) {
            var readOnly = plan();
            try (Context context = context(new PostEffectEventJS(readOnly))) {
                assertThrows(PolyglotException.class, () -> context.eval("js", write), write);
                assertNotNull(readOnly.collectionError(), write);
                assertEquals(0, readOnly.installCount(), write);
            }
        }
    }

    @Test
    void reflectedAndRenderedPayloadsHideProxyInfrastructureWithoutDroppingTheirScriptContracts() {
        var reflector = new TypeReflector();
        var builder = reflector.reflect(PostEffectBuilder.class);
        var event = reflector.reflect(PostEffectEventJS.class);
        var renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        renderer.setClassDeclarations(Map.of(builder.fqn, builder, event.fqn, event));
        for (var declaration : List.of(builder, event)) {
            String rendered = renderer.render(declaration);
            assertFalse(rendered.contains("$ProxyObject"), rendered);
            for (String hidden : List.of("getMember(", "getMemberKeys(", "hasMember(", "putMember(", "removeMember(")) {
                assertFalse(rendered.contains(hidden), rendered);
            }
            assertTrue(rendered.contains(declaration.sourceClass == PostEffectBuilder.class ? "chainJson" : "register("), rendered);
        }
    }

    @Test
    void javaBuilderIsDetachedAndCannotChangeCollectedDeclarations() {
        var plan = plan();
        PostEffectBuilder[] captured = new PostEffectBuilder[1];
        assertTrue(new PostEffectEventJS(plan).register("nekojs:java", (java.util.function.Consumer<PostEffectBuilder>) builder -> {
            captured[0] = builder;
            builder.setChainJson("{}");
        }));
        assertThrows(IllegalStateException.class, () -> captured[0].setChainJson("changed"));
        assertEquals("{}", plan.finish().getFirst().chainJson());
    }
}
//?}
