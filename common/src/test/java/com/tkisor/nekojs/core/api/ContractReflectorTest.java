package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.data.NbtEntry;
import com.tkisor.nekojs.api.facade.TextFacade;
import com.tkisor.nekojs.api.facade.IdFacade;
import com.tkisor.nekojs.api.facade.JsonFacade;
import com.tkisor.nekojs.api.facade.PlatformFacade;
import com.tkisor.nekojs.api.facade.RegistryView;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ContractReflectorTest {

    @Test
    void textFacadeProducesExpectedSymbols() {
        List<ApiSymbol> symbols = ContractReflector.extractSymbols("Text", TextFacade.class);
        Map<String, ApiSymbol> byId = symbols.stream()
                .collect(Collectors.toMap(s -> s.id().value(), s -> s));

        // global:Text exists
        assertNotNull(byId.get("global:Text"));

        // member:Text.of exists with the right signature
        ApiSymbol of = byId.get("member:Text.of");
        assertNotNull(of);
        assertEquals(1, of.signatures().getFirst().parameters().size());
        assertEquals("text", of.signatures().getFirst().parameters().getFirst().name());
        assertEquals(ApiTypeRef.Kind.PRIMITIVE, of.signatures().getFirst().parameters().getFirst().type().kind());
        assertEquals("string", of.signatures().getFirst().parameters().getFirst().type().name());
        // returns UNION(type:TextValue, null) — TextValue as a return type is wrapped by the proxy
        ApiTypeRef ofReturn = of.signatures().getFirst().returnType();
        assertEquals(ApiTypeRef.Kind.UNION, ofReturn.kind());
        assertTrue(ofReturn.arguments().stream()
                .anyMatch(a -> a.kind() == ApiTypeRef.Kind.SYMBOL && "type:TextValue".equals(a.name())));
    }

    @Test
    void idFacadeProducesOverloads() {
        List<ApiSymbol> symbols = ContractReflector.extractSymbols("ID", IdFacade.class);
        Map<String, ApiSymbol> byId = symbols.stream()
                .collect(Collectors.toMap(s -> s.id().value(), s -> s));

        // ID.of has two overloads: (String) and (String, String)
        ApiSymbol of = byId.get("member:ID.of");
        assertNotNull(of);
        assertEquals(2, of.signatures().size(), "ID.of should have 2 overloads");
    }

    @Test
    void jsonFacadeReturnsCorrectTypes() {
        List<ApiSymbol> symbols = ContractReflector.extractSymbols("JsonIO", JsonFacade.class);
        Map<String, ApiSymbol> byId = symbols.stream()
                .collect(Collectors.toMap(s -> s.id().value(), s -> s));

        // JsonIO.parse(String) → returns JsonValue, which as a return type is
        // UNION(type:JsonValue, null) (the proxy wraps it as a JS object, so .toString() works;
        // nullable because a failure returns null)
        ApiSymbol parse = byId.get("member:JsonIO.parse");
        assertNotNull(parse);
        ApiTypeRef parseReturn = parse.signatures().getFirst().returnType();
        assertEquals(ApiTypeRef.Kind.UNION, parseReturn.kind());
        assertTrue(parseReturn.arguments().stream()
                .anyMatch(a -> a.kind() == ApiTypeRef.Kind.SYMBOL && "type:JsonValue".equals(a.name())));

        // JsonIO.toString(JsonValue) → String; the return type is the nullable UNION(string, null)
        ApiSymbol toString = byId.get("member:JsonIO.toString");
        assertNotNull(toString);
        assertEquals("json", toString.signatures().getFirst().parameters().getFirst().type().name());
        assertTrue(returnContainsPrimitive(toString, "string"));
    }

    /** Whether the return type (a nullable UNION or a plain PRIMITIVE) contains the named primitive branch. */
    private static boolean returnContainsPrimitive(ApiSymbol symbol, String primitiveName) {
        ApiTypeRef rt = symbol.signatures().getFirst().returnType();
        if (rt.kind() == ApiTypeRef.Kind.PRIMITIVE) return primitiveName.equals(rt.name());
        return rt.arguments().stream()
                .anyMatch(a -> a.kind() == ApiTypeRef.Kind.PRIMITIVE && primitiveName.equals(a.name()));
    }

    @Test
    void textVarargsProducesUnion() {
        List<ApiSymbol> symbols = ContractReflector.extractSymbols("Text", TextFacade.class);
        Map<String, ApiSymbol> byId = symbols.stream()
                .collect(Collectors.toMap(s -> s.id().value(), s -> s));

        // Text.translatable(String, List<Object>) — the second parameter should be a UNION
        ApiSymbol translatable = byId.get("member:Text.translatable");
        assertNotNull(translatable);
        assertEquals(2, translatable.signatures().getFirst().parameters().size());
        ApiTypeRef argType = translatable.signatures().getFirst().parameters().get(1).type();
        assertEquals(ApiTypeRef.Kind.UNION, argType.kind(),
                "List<Object> param should map to UNION, got " + argType.kind());
    }

    @Test
    void voidReturnTypeHandled() {
        List<ApiSymbol> symbols = ContractReflector.extractSymbols("JsonIO", JsonFacade.class);
        Map<String, ApiSymbol> byId = symbols.stream()
                .collect(Collectors.toMap(s -> s.id().value(), s -> s));

        // JsonIO.write(String, JsonValue) → void
        ApiSymbol write = byId.get("member:JsonIO.write");
        assertNotNull(write);
        assertEquals(ApiTypeRef.Kind.VOID, write.signatures().getFirst().returnType().kind());
    }

    @Test
    void reflectDataTypeHandlesRecordAccessors() {
        // NbtEntry is record(key, value)
        List<ApiSymbol> symbols = ContractReflector.reflectDataType("NbtEntry", NbtEntry.class);
        Map<String, ApiSymbol> byId = symbols.stream()
                .collect(Collectors.toMap(s -> s.id().value(), s -> s));

        ApiSymbol key = byId.get("member:NbtEntry.key");
        assertNotNull(key);
        assertEquals(0, key.signatures().getFirst().parameters().size(), "record accessor is zero-arg");
        // return type is the nullable UNION(string, null)
        assertTrue(returnContainsPrimitive(key, "string"));

        ApiSymbol value = byId.get("member:NbtEntry.value");
        assertNotNull(value);
        // NbtValue as a return type → UNION(type:NbtValue, null) (wrapped by the proxy)
        ApiTypeRef valueReturn = value.signatures().getFirst().returnType();
        assertTrue(valueReturn.arguments().stream()
                        .anyMatch(a -> a.kind() == ApiTypeRef.Kind.SYMBOL && "type:NbtValue".equals(a.name())),
                "NbtEntry.value return must contain type:NbtValue");
    }

    @Test
    void reflectDataTypeHandlesInterfaceMethods() {
        // RegistryView is an interface, so its declared instance methods are scanned
        List<ApiSymbol> symbols = ContractReflector.reflectDataType("RegistryView", RegistryView.class);
        Map<String, ApiSymbol> byId = symbols.stream()
                .collect(Collectors.toMap(s -> s.id().value(), s -> s));

        assertNotNull(byId.get("member:RegistryView.exists"), "zero-arg method");
        assertNotNull(byId.get("member:RegistryView.all"), "List-returning method");
        ApiSymbol has = byId.get("member:RegistryView.has");
        assertNotNull(has);
        assertEquals(1, has.signatures().getFirst().parameters().size(), "has(String) has 1 param");
    }

    @Test
    void contractNameOverrideAppliedToSymbolRef() {
        // ModInfoValue → contract name ModInfo; PlatformFacade.getInfo returns ModInfoValue,
        // so the type is UNION(type:ModInfo, null) (contract name ModInfo, not the class name
        // ModInfoValue)
        List<ApiSymbol> symbols = ContractReflector.extractSymbols("Platform", PlatformFacade.class);
        Map<String, ApiSymbol> byId = symbols.stream()
                .collect(Collectors.toMap(s -> s.id().value(), s -> s));

        ApiSymbol getInfo = byId.get("member:Platform.getInfo");
        assertNotNull(getInfo);
        ApiTypeRef ret = getInfo.signatures().getFirst().returnType();
        assertTrue(ret.arguments().stream()
                        .anyMatch(a -> a.kind() == ApiTypeRef.Kind.SYMBOL && "type:ModInfo".equals(a.name())),
                "ModInfoValue must map to contract name ModInfo in return UNION");
    }

    @Test
    void jsonValueProducesReceiverDualAfterIsReceiverTypeAddition() {
        List<ApiSymbol> symbols = ContractReflector.extractSymbols("JsonIO", JsonFacade.class);
        Map<String, ApiSymbol> byId = symbols.stream()
                .collect(Collectors.toMap(s -> s.id().value(), s -> s));

        // JsonFacade.toString(JsonValue) → the receiver produces both members, including member:JsonValue.toString
        assertNotNull(byId.get("member:JsonValue.toString"), "JsonValue receiver-dual");
        assertNotNull(byId.get("member:JsonValue.toPrettyString"), "JsonValue receiver-dual");
    }
}
