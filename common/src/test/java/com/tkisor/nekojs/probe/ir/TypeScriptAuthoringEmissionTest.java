package com.tkisor.nekojs.probe.ir;

import com.google.gson.JsonObject;
import com.tkisor.nekojs.api.annotation.Overload;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptAuthoringEmissionTest {
    public static final class Fixture {
        public int x;
        public int x() { return x; }
        public String getLabel() { return "label"; }
        public void setLabel(String label) {}
        public String convert(String value) { return value; }
        public int convert(int value) { return value; }
        public void optionalArray(String... values) {}
        @Overload(value = {"x: string"}, returns = "string")
        public String authored(int value) { return Integer.toString(value); }
    }

    @Test void indexedRenderingKeepsExactTextSelectorsBeansAndOverloadSeparation() {
        var aliases = new TypeAliasRegistry();
        var declaration = new TypeReflector().reflect(Fixture.class);
        declaration.methods.stream().filter(method -> method.name.equals("setLabel")).forEach(method -> method.hidden = true);
        var renderer = new TypeScriptClassRenderer(aliases);
        renderer.setClassDeclarations(Map.of(declaration.fqn, declaration));
        String ordinary = renderer.render(declaration, aliases);
        var indexed = renderer.renderIndexed(declaration, aliases);
        assertEquals(ordinary, indexed.text());
        List<JsonObject> converted = indexed.members().stream().filter(record -> record.get("name").getAsString().equals("convert")).toList();
        assertEquals(2, converted.size());
        assertNotEquals(converted.get(0).get("symbolId"), converted.get(1).get("symbolId"));
        for (JsonObject member : indexed.members()) {
            assertTrue(ordinary.contains(member.get("signature").getAsString()), member.toString());
        }
        assertTrue(indexed.members().stream().anyMatch(record -> record.get("name").getAsString().equals("x()") && record.get("kind").getAsString().equals("method")));
        assertFalse(indexed.members().stream().anyMatch(record -> record.get("name").getAsString().equals("x") && record.get("kind").getAsString().equals("method")));
        assertTrue(indexed.members().stream().anyMatch(record -> record.get("name").getAsString().equals("label") && record.get("kind").getAsString().equals("property")));
        JsonObject varargs = indexed.members().stream().filter(record -> record.get("name").getAsString().equals("optionalArray")).findFirst().orElseThrow();
        assertEquals(0, varargs.getAsJsonObject("callArity").get("min").getAsInt());
        assertEquals(1, varargs.getAsJsonObject("callArity").getAsJsonObject("max").get("value").getAsInt());
        assertEquals("string[]", varargs.getAsJsonArray("parameters").get(0).getAsJsonObject().getAsJsonObject("type").get("display").getAsString());
        assertFalse(indexed.complete(), "Literal authored overload fragments must remain explicitly unstructured.");
    }
}
