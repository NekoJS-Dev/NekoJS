package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.facade.TextFacade;
import com.tkisor.nekojs.api.facade.IdFacade;
import com.tkisor.nekojs.api.facade.JsonFacade;
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

        // global:Text 存在
        assertNotNull(byId.get("global:Text"));

        // member:Text.of 存在，签名正确
        ApiSymbol of = byId.get("member:Text.of");
        assertNotNull(of);
        assertEquals(1, of.signatures().getFirst().parameters().size());
        assertEquals("text", of.signatures().getFirst().parameters().getFirst().name());
        assertEquals(ApiTypeRef.Kind.PRIMITIVE, of.signatures().getFirst().parameters().getFirst().type().kind());
        assertEquals("string", of.signatures().getFirst().parameters().getFirst().type().name());
        // 返回 type:TextValue
        assertEquals(ApiTypeRef.Kind.SYMBOL, of.signatures().getFirst().returnType().kind());
        assertEquals("type:TextValue", of.signatures().getFirst().returnType().name());
    }

    @Test
    void idFacadeProducesOverloads() {
        List<ApiSymbol> symbols = ContractReflector.extractSymbols("ID", IdFacade.class);
        Map<String, ApiSymbol> byId = symbols.stream()
                .collect(Collectors.toMap(s -> s.id().value(), s -> s));

        // ID.of 有两个重载：(String) 和 (String, String)
        ApiSymbol of = byId.get("member:ID.of");
        assertNotNull(of);
        assertEquals(2, of.signatures().size(), "ID.of should have 2 overloads");
    }

    @Test
    void jsonFacadeReturnsCorrectTypes() {
        List<ApiSymbol> symbols = ContractReflector.extractSymbols("JsonIO", JsonFacade.class);
        Map<String, ApiSymbol> byId = symbols.stream()
                .collect(Collectors.toMap(s -> s.id().value(), s -> s));

        // JsonIO.parse(String) → JsonValue(PRIMITIVE json)
        ApiSymbol parse = byId.get("member:JsonIO.parse");
        assertNotNull(parse);
        assertEquals("json", parse.signatures().getFirst().returnType().name());

        // JsonIO.toString(JsonValue) → String
        ApiSymbol toString = byId.get("member:JsonIO.toString");
        assertNotNull(toString);
        assertEquals("json", toString.signatures().getFirst().parameters().getFirst().type().name());
        assertEquals("string", toString.signatures().getFirst().returnType().name());
    }

    @Test
    void textVarargsProducesUnion() {
        List<ApiSymbol> symbols = ContractReflector.extractSymbols("Text", TextFacade.class);
        Map<String, ApiSymbol> byId = symbols.stream()
                .collect(Collectors.toMap(s -> s.id().value(), s -> s));

        // Text.translatable(String, List<Object>) — 第二参数应为 UNION
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
}
