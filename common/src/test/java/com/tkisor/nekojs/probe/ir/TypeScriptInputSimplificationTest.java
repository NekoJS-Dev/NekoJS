package com.tkisor.nekojs.probe.ir;

import static org.junit.jupiter.api.Assertions.*;

import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TypeScriptInputSimplificationTest {
    static final class Payload {}
    private static ApiTypeRef symbol(Class<?> type, ApiTypeRef... arguments) {
        return ApiTypeRef.symbol(new ApiSymbolId("java", type.getName()), List.of(arguments));
    }
    private static TypeAliasRegistry aliases() {
        var aliases = new TypeAliasRegistry();
        aliases.registerClassAlias(Payload.class.getName(), "$Payload_");
        return aliases;
    }
    private static String input(Class<?> type, ApiTypeRef... arguments) {
        return TypeScriptClassRenderer.renderTypeRef(symbol(type, arguments), aliases(), true);
    }

    @Test void equalHostAndGuestArgumentsReuseCanonicalInputAliasesGlobally() {
        ApiTypeRef text = ApiTypeRef.primitive("string");
        ApiTypeRef number = ApiTypeRef.primitive("number");
        assertEquals("$List_<string>", input(List.class, text));
        assertEquals("$Collection_<number>", input(Collection.class, number));
        assertEquals("$Iterable_<string>", input(Iterable.class, text));
        assertEquals("$Set<string>", input(Set.class, text), "Set must not acquire guest array conversion");
    }

    @Test void malformedCollectionArityKeepsTheExistingExpandedFallback() {
        var registry = aliases();
        String emptyList = registry.renderCollectionInput(List.class.getName(), new String[0], new String[0]);
        assertEquals(registry.getCollectionAlias(List.class.getName(), new String[0], new String[0]), emptyList);
        assertFalse(emptyList.contains("$List_<"), emptyList);
        String wrongMap = registry.renderCollectionInput(Map.class.getName(), new String[]{"string"}, new String[]{"string"});
        assertEquals(registry.getCollectionAlias(Map.class.getName(), new String[]{"string"}, new String[]{"string"}), wrongMap);
        assertFalse(wrongMap.contains("$Map_<"), wrongMap);
        assertEquals("$List_<string>", registry.renderCollectionInput(List.class.getName(), new String[]{"string"}, new String[]{"string"}));
        assertEquals("$Map_<string, number>", registry.renderCollectionInput(Map.class.getName(), new String[]{"string", "number"}, new String[]{"string", "number"}));
    }

    @Test void canonicalAliasBodiesRemainExpandedInsteadOfRecursingIntoThemselves() {
        String body = aliases().getCollectionAlias(List.class.getName(), new String[]{"E"});
        assertTrue(body.contains("$List<E>"), body);
        assertTrue(body.contains("E") && body.contains("[]"), body);
        assertFalse(body.contains("$List_<"), body);
    }

    @Test void adaptedElementsPreserveHostContainersAndNestedGuestInputDistinctions() {
        String adapted = input(List.class, symbol(Payload.class));
        assertTrue(adapted.contains("$List<$TypeScriptInputSimplificationTest$Payload>"), adapted);
        assertTrue(adapted.contains("$Payload_") && adapted.contains("[]"), adapted);
        assertFalse(adapted.contains("$List_<"), adapted);
        String nested = input(List.class, symbol(List.class, ApiTypeRef.primitive("string")));
        assertTrue(nested.contains("$List<$List<string>>"), nested);
        assertTrue(nested.contains("$List_<string>"), nested);
        assertFalse(nested.contains("$List_<$List_"), "An outer host list must not acquire converted inner host arguments: " + nested);
    }

    @Test void concretePropertyKeyMapsDropOnlyTheRedundantGuardAndExtract() {
        for (String key : List.of("string", "number")) {
            String projected = input(Map.class, ApiTypeRef.primitive(key), symbol(Payload.class));
            assertTrue(projected.contains("$Map<" + key + ", $TypeScriptInputSimplificationTest$Payload>"), projected);
            assertTrue(projected.contains("Partial<Record<" + key + ", $Payload_>>"), projected);
            assertFalse(projected.contains("extends [PropertyKey]"), projected);
            assertFalse(projected.contains("Extract<"), projected);
        }
    }

    @Test void genericMixedAndNonPropertyKeysRetainTheirSoundRecordGuard() {
        for (ApiTypeRef key : List.of(ApiTypeRef.typeVariable("K"),
                ApiTypeRef.union(List.of(ApiTypeRef.primitive("string"), symbol(Payload.class))), ApiTypeRef.primitive("boolean"))) {
            String projected = input(Map.class, key, symbol(Payload.class));
            assertTrue(projected.contains("extends [PropertyKey]"), projected);
            assertTrue(projected.contains("Partial<Record<Extract<"), projected);
            assertTrue(projected.contains("$Map<"), projected);
        }
    }

    @Test void canonicalInputAliasesRespectModuleLocalImportNames() {
        var scoped = aliases().withReferences((fqn, name) -> {
            if (fqn.equals(List.class.getName())) return name.endsWith("_") ? "LocalListInput" : "LocalListHost";
            return name;
        });
        assertEquals("LocalListInput<string>", TypeScriptClassRenderer.renderTypeRef(symbol(List.class, ApiTypeRef.primitive("string")), scoped, true));
        String adapted = TypeScriptClassRenderer.renderTypeRef(symbol(List.class, symbol(Payload.class)), scoped, true);
        assertTrue(adapted.contains("LocalListHost<"), adapted);
        assertFalse(adapted.contains("$List<"), adapted);
        assertEquals("$List_", TypeAliasRegistry.collectionInputAlias(List.class.getName()));
    }
}
