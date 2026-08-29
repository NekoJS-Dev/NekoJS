package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot;
import com.tkisor.nekojs.api.surface.ApiParameter;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyArray;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiValueMarshallerTest {
    private static final ApiSymbolId NEKO_ID = ApiSymbolId.parse("type:NekoId");
    private final ApiValueMarshaller marshaller = new ApiValueMarshaller(new FixtureRuntimeView());

    @Test
    void symbolReturnIsWrappedAndCanBeUnwrappedAsArgument() {
        NekoId id = NekoId.of("minecraft:stone");
        Object wrapped = marshaller.marshalReturn(id, ApiTypeRef.symbol(NEKO_ID), false, "member:ID.of");
        ApiFacadeProxy proxy = assertInstanceOf(ApiFacadeProxy.class, wrapped);
        assertSame(id, proxy.implementation());

        ApiSignature signature = ApiSignature.function(
                List.of(new ApiParameter("id", ApiTypeRef.symbol(NEKO_ID), false, false)),
                ApiTypeRef.primitive("string"));
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("id", proxy);
            Value value = context.getBindings("js").getMember("id");
            assertEquals(List.of(id), marshaller.marshalArguments(signature, List.of(value), "member:ID.asString"));
        }
    }

    @Test
    void symbolArgumentRejectsDifferentStableType() {
        ApiFacadeProxy wrong = ApiFacadeProxy.value(
                new FixtureRuntimeView(), ApiSymbolId.parse("type:ModInfo"), new Object());
        ApiSignature signature = ApiSignature.function(
                List.of(new ApiParameter("id", ApiTypeRef.symbol(NEKO_ID), false, false)),
                ApiTypeRef.voidType());

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("value", wrong);
            Value value = context.getBindings("js").getMember("value");
            ApiRuntimeException error = assertThrows(ApiRuntimeException.class,
                    () -> marshaller.marshalArguments(signature, List.of(value), "member:test"));
            assertEquals("TYPE_MISMATCH", error.code());
        }
    }

    @Test
    void symbolTypeChecksStayCorrectAcrossRepeatedCalls() {
        ApiTypeRef symbolType = ApiTypeRef.symbol(NEKO_ID);
        ApiSignature signature = ApiSignature.function(
                List.of(new ApiParameter("id", symbolType, false, false)),
                ApiTypeRef.primitive("string"));
        ApiSymbol symbol = new ApiSymbol(ApiSymbolId.parse("member:ID.asString"), List.of(signature));
        NekoId id = NekoId.of("minecraft:stone");
        Object wrapped = marshaller.marshalReturn(id, symbolType, false, "member:ID.of");
        ApiFacadeProxy wrong = ApiFacadeProxy.value(
                new FixtureRuntimeView(), ApiSymbolId.parse("type:ModInfo"), new Object());

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("id", wrapped);
            context.getBindings("js").putMember("wrong", wrong);
            Value value = context.getBindings("js").getMember("id");
            Value wrongValue = context.getBindings("js").getMember("wrong");

            for (int i = 0; i < 5; i++) {
                assertSame(signature, marshaller.selectSignature(symbol, List.of(value)));
                assertEquals(List.of(id), marshaller.marshalArguments(
                        signature, List.of(value), "member:ID.asString"));
                ApiRuntimeException error = assertThrows(ApiRuntimeException.class,
                        () -> marshaller.marshalArguments(
                                signature, List.of(wrongValue), "member:ID.asString"));
                assertEquals("TYPE_MISMATCH", error.code());
            }
        }
    }

    @Test
    void structurallyEqualButDistinctSymbolTypeRefsBothMatch() {
        // ApiTypeRef is a record (structural equals): the marshaller caches parsed ids by
        // identity, so two equal-but-distinct instances must both resolve independently.
        ApiTypeRef first = ApiTypeRef.symbol(NEKO_ID);
        ApiTypeRef second = ApiTypeRef.symbol(NEKO_ID);
        assertEquals(first, second);
        assertTrue(first != second);

        ApiSignature firstSignature = ApiSignature.function(
                List.of(new ApiParameter("id", first, false, false)), ApiTypeRef.primitive("string"));
        ApiSignature secondSignature = ApiSignature.function(
                List.of(new ApiParameter("id", second, false, false)), ApiTypeRef.primitive("string"));

        NekoId id = NekoId.of("minecraft:stone");
        Object wrapped = marshaller.marshalReturn(id, first, false, "member:ID.of");

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("id", wrapped);
            Value value = context.getBindings("js").getMember("id");

            ApiSymbol firstSymbol = new ApiSymbol(
                    ApiSymbolId.parse("member:ID.asString"), List.of(firstSignature));
            ApiSymbol secondSymbol = new ApiSymbol(
                    ApiSymbolId.parse("member:ID.asString"), List.of(secondSignature));
            assertSame(firstSignature, marshaller.selectSignature(firstSymbol, List.of(value)));
            assertSame(secondSignature, marshaller.selectSignature(secondSymbol, List.of(value)));
            assertEquals(List.of(id), marshaller.marshalArguments(
                    firstSignature, List.of(value), "member:ID.asString"));
            assertEquals(List.of(id), marshaller.marshalArguments(
                    secondSignature, List.of(value), "member:ID.asString"));
        }
    }

    @Test
    void symbolReturnWrapsSameTypeRefRepeatedly() {
        ApiTypeRef symbolType = ApiTypeRef.symbol(NEKO_ID);
        NekoId id = NekoId.of("minecraft:stone");
        for (int i = 0; i < 5; i++) {
            ApiFacadeProxy proxy = assertInstanceOf(ApiFacadeProxy.class,
                    marshaller.marshalReturn(id, symbolType, false, "member:ID.of"));
            assertSame(id, proxy.implementation());
            assertEquals(NEKO_ID, proxy.typeId());
        }
    }

    @Test
    void arrayReturnUsesProxyArray() {
        Object value = marshaller.marshalReturn(
                List.of("alpha", "beta"), ApiTypeRef.array(ApiTypeRef.primitive("string")), false,
                "member:Platform.getList");
        ProxyArray array = assertInstanceOf(ProxyArray.class, value);
        assertEquals(2, array.getSize());
        assertEquals("alpha", array.get(0));
    }

    @Test
    void javaArrayReturnUsesProxyArray() {
        Object value = marshaller.marshalReturn(
                new int[]{2, 4}, ApiTypeRef.array(ApiTypeRef.primitive("number")), false,
                "member:test.values");
        ProxyArray array = assertInstanceOf(ProxyArray.class, value);
        assertEquals(2, array.getSize());
        assertEquals(4, array.get(1));
    }

    @Test
    void symbolOrNullUnionUsesOnlyNonNullBranch() {
        ApiTypeRef union = ApiTypeRef.union(List.of(
                ApiTypeRef.symbol(NEKO_ID), ApiTypeRef.primitive("null")));
        assertNull(marshaller.marshalReturn(null, union, false, "member:test.optional"));
        assertInstanceOf(ApiFacadeProxy.class, marshaller.marshalReturn(
                NekoId.of("minecraft:stone"), union, false, "member:test.optional"));
    }

    @Test
    void ambiguousUnionReturnFailsClosed() {
        ApiTypeRef union = ApiTypeRef.union(List.of(
                ApiTypeRef.symbol(NEKO_ID), ApiTypeRef.symbol(ApiSymbolId.parse("type:ModInfo"))));
        ApiRuntimeException error = assertThrows(ApiRuntimeException.class,
                () -> marshaller.marshalReturn(new Object(), union, false, "member:test.ambiguous"));
        assertEquals("API_CONTRACT_VIOLATION", error.code());
    }

    @Test
    void sameArityOverloadsAreSelectedByObservableType() {
        ApiSignature stringSignature = ApiSignature.function(
                List.of(new ApiParameter("value", ApiTypeRef.primitive("string"), false, false)),
                ApiTypeRef.primitive("string"));
        ApiSignature numberSignature = ApiSignature.function(
                List.of(new ApiParameter("value", ApiTypeRef.primitive("number"), false, false)),
                ApiTypeRef.primitive("string"));
        ApiSymbol symbol = new ApiSymbol(
                ApiSymbolId.parse("member:test.overloaded"), List.of(stringSignature, numberSignature));

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            assertSame(stringSignature, marshaller.selectSignature(symbol, List.of(context.eval("js", "'x'"))));
            assertSame(numberSignature, marshaller.selectSignature(symbol, List.of(context.eval("js", "42"))));
        }
    }

    @Test
    void wrongPrimitiveShapeHasNoMatchingSignature() {
        ApiSignature signature = ApiSignature.function(
                List.of(new ApiParameter("value", ApiTypeRef.primitive("string"), false, false)),
                ApiTypeRef.voidType());
        ApiSymbol symbol = new ApiSymbol(ApiSymbolId.parse("member:test.stringOnly"), List.of(signature));

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ApiRuntimeException error = assertThrows(ApiRuntimeException.class,
                    () -> marshaller.selectSignature(symbol, List.of(context.eval("js", "42"))));
            assertEquals("NO_MATCHING_SIGNATURE", error.code());
            assertEquals("member:test.stringOnly", error.symbolId().orElseThrow());
        }
    }

    @Test
    void arrayArgumentsAreConvertedToGraalFreeLists() {
        ApiSignature signature = ApiSignature.function(
                List.of(new ApiParameter(
                        "values", ApiTypeRef.array(ApiTypeRef.primitive("number")), false, false)),
                ApiTypeRef.voidType());

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            List<Object> values = marshaller.marshalArguments(
                    signature, List.of(context.eval("js", "[1, 2]")), "member:test.array");
            List<?> numbers = assertInstanceOf(List.class, values.getFirst());
            assertEquals(List.of("1", "2"), numbers.stream()
                    .map(value -> ((com.tkisor.nekojs.api.data.JsNumber) value).canonicalText())
                    .toList());
        }
    }

    @Test
    void numbersRetainEcmaScriptCanonicalText() {
        ApiSignature signature = ApiSignature.function(
                List.of(new ApiParameter("value", ApiTypeRef.primitive("number"), false, false)),
                ApiTypeRef.voidType());
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            Object large = marshaller.marshalArguments(
                    signature, List.of(context.eval("js", "1000000000000000128")), "member:test.number").getFirst();
            Object smallest = marshaller.marshalArguments(
                    signature, List.of(context.eval("js", "5e-324")), "member:test.number").getFirst();
            assertEquals("1000000000000000100",
                    ((com.tkisor.nekojs.api.data.JsNumber) large).canonicalText());
            assertEquals("5e-324", ((com.tkisor.nekojs.api.data.JsNumber) smallest).canonicalText());
        }
    }

    @Test
    void rejectsNonFiniteGuestNumbersWithStableTypeMismatch() {
        ApiSignature signature = ApiSignature.function(
                List.of(new ApiParameter("value", ApiTypeRef.primitive("number"), false, false)),
                ApiTypeRef.voidType());
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ApiRuntimeException error = assertThrows(ApiRuntimeException.class, () -> marshaller.marshalArguments(
                    signature, List.of(context.eval("js", "NaN")), "member:test.number"));
            assertEquals("TYPE_MISMATCH", error.code());
        }
    }

    @Test
    void jsonArgumentsCopyGuestValuesWithoutLeakingGraalObjects() {
        ApiSignature signature = ApiSignature.function(
                List.of(new ApiParameter("value", ApiTypeRef.primitive("json"), false, false)),
                ApiTypeRef.voidType());
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            Object value = marshaller.marshalArguments(
                    signature, List.of(context.eval("js", "({ a: [1, true, null] })")), "member:test.json")
                    .getFirst();
            com.tkisor.nekojs.api.data.JsonValue.ObjectValue object = assertInstanceOf(
                    com.tkisor.nekojs.api.data.JsonValue.ObjectValue.class, value);
            assertEquals("1", ((com.tkisor.nekojs.api.data.JsonValue.NumberValue) ((com.tkisor.nekojs.api.data.JsonValue.ArrayValue)
                    object.values().get("a")).values().getFirst()).lexeme());
        }
    }

    @Test
    void jsonArgumentsRejectGuestFunctionsAndNonFiniteNumbers() {
        ApiSignature signature = ApiSignature.function(
                List.of(new ApiParameter("value", ApiTypeRef.primitive("json"), false, false)),
                ApiTypeRef.voidType());
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            assertEquals("TYPE_MISMATCH", assertThrows(ApiRuntimeException.class, () -> marshaller.marshalArguments(
                    signature, List.of(context.eval("js", "({ fn: () => null })")), "member:test.json")).code());
            assertEquals("TYPE_MISMATCH", assertThrows(ApiRuntimeException.class, () -> marshaller.marshalArguments(
                    signature, List.of(context.eval("js", "({ n: NaN })")), "member:test.json")).code());
        }
    }

    @Test
    void jsonArgumentsRejectSelfAndMutualCyclesAsTypeMismatches() {
        ApiSignature signature = ApiSignature.function(
                List.of(new ApiParameter("value", ApiTypeRef.primitive("json"), false, false)),
                ApiTypeRef.voidType());
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            Value selfCycle = context.eval("js", "(() => { const value = {}; value.self = value; return value; })()");
            Value mutualCycle = context.eval("js", "(() => { const left = {}; const right = { left }; left.right = right; return left; })()");

            assertEquals("TYPE_MISMATCH", assertThrows(ApiRuntimeException.class, () -> marshaller.marshalArguments(
                    signature, List.of(selfCycle), "member:test.json")).code());
            assertEquals("TYPE_MISMATCH", assertThrows(ApiRuntimeException.class, () -> marshaller.marshalArguments(
                    signature, List.of(mutualCycle), "member:test.json")).code());
        }
    }

    @Test
    void graalContainerEqualityTracksGuestReferenceIdentityAcrossValueWrappers() {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            Value pair = context.eval("js", "(() => { const left = {}; const right = {}; left.self = left; return [left, right]; })()");
            Value left = pair.getArrayElement(0);
            Value right = pair.getArrayElement(1);

            assertEquals(left, left.getMember("self"));
            assertNotEquals(left, right);
        }
    }

    @Test
    void optionalAndVarargsSignaturesHaveDeterministicSpecificity() {
        ApiSignature exact = ApiSignature.function(
                List.of(new ApiParameter("value", ApiTypeRef.primitive("string"), false, false)),
                ApiTypeRef.voidType());
        ApiSignature optional = ApiSignature.function(
                List.of(
                        new ApiParameter("value", ApiTypeRef.primitive("string"), false, false),
                        new ApiParameter("count", ApiTypeRef.primitive("number"), true, false)),
                ApiTypeRef.voidType());
        ApiSignature varargs = ApiSignature.function(
                List.of(new ApiParameter("values", ApiTypeRef.primitive("string"), false, true)),
                ApiTypeRef.voidType());

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ApiSymbol overloaded = new ApiSymbol(
                    ApiSymbolId.parse("member:test.optional"), List.of(optional, exact));
            assertSame(exact, marshaller.selectSignature(
                    overloaded, List.of(context.eval("js", "'value'"))));

            ApiSymbol varargOnly = new ApiSymbol(
                    ApiSymbolId.parse("member:test.varargs"), List.of(varargs));
            assertSame(varargs, marshaller.selectSignature(varargOnly, List.of()));
        }
    }

    @Test
    void disambiguatesPrimitiveUnionReturns() {
        ApiTypeRef union = ApiTypeRef.union(List.of(
                ApiTypeRef.primitive("string"), ApiTypeRef.primitive("number")));
        assertEquals("value", marshaller.marshalReturn("value", union, false, "member:test.union"));
        assertEquals(42, marshaller.marshalReturn(42, union, false, "member:test.union"));
    }

    @Test
    void concreteReturnBranchWinsOverObject() {
        ApiTypeRef union = ApiTypeRef.union(List.of(
                ApiTypeRef.primitive("string"), ApiTypeRef.primitive("object")));
        assertEquals("value", marshaller.marshalReturn("value", union, false, "member:test.union"));
    }

    @Test
    void rejectsNullForNonNullableReturn() {
        ApiRuntimeException error = assertThrows(ApiRuntimeException.class,
                () -> marshaller.marshalReturn(
                        null, ApiTypeRef.primitive("string"), false, "member:test.nonNull"));
        assertEquals("API_CONTRACT_VIOLATION", error.code());
    }

    @Test
    void callbackElementsInArraysBecomeApiCallbacks() {
        ApiSignature callback = ApiSignature.function(List.of(), ApiTypeRef.voidType());
        ApiSignature signature = ApiSignature.function(
                List.of(new ApiParameter(
                        "callbacks", ApiTypeRef.array(ApiTypeRef.callback(callback)), false, false)),
                ApiTypeRef.voidType());

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            List<Object> arguments = marshaller.marshalArguments(
                    signature, List.of(context.eval("js", "[() => null]")), "member:test.callbacks");
            List<?> callbacks = assertInstanceOf(List.class, arguments.getFirst());
            assertInstanceOf(com.tkisor.nekojs.api.surface.ApiCallback.class, callbacks.getFirst());
        }
    }

    @Test
    void arbitraryHostReturnStillFails() {
        ApiRuntimeException error = assertThrows(ApiRuntimeException.class,
                () -> marshaller.marshalReturn(
                        new Object(), ApiTypeRef.primitive("object"), false, "member:test"));
        assertEquals("NATIVE_TYPE_LEAK", error.code());
    }

    private static final class FixtureRuntimeView implements ApiRuntimeView {
        @Override public Optional<ApiSymbol> findSymbol(ApiSymbolId id) { return Optional.empty(); }
        @Override public Map<String, ApiSymbol> symbolsByJsName(com.tkisor.nekojs.api.surface.ScriptTypeId type) {
            return Map.of();
        }
        @Override public Set<String> memberNames(ApiSymbolId typeId) { return Set.of(); }
        @Override public Object invoke(ApiSymbolId memberId, String signatureKey, Object receiver, List<Object> arguments) {
            throw new UnsupportedOperationException();
        }
        @Override public ApiEnvironmentSnapshot environmentSnapshot() { return null; }
    }
}
