package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Generic projection does not depend on a recipe namespace or an addon class. */
class TypeScriptDeclaredInputModelTest {
    public static class Box<T> { public T value; }

    @Test
    void converterOwnedDeclarationsAndErasedJavaParametersUseExplicitSemantics() {
        var aliases = aliases();
        var box = symbol(Box.class.getName(), symbol(Date.class.getName()));
        assertEquals("$Box_<$Date>", TypeScriptClassRenderer.renderTypeRef(box, aliases, true));
        assertEquals("$Box_<$DateInput>", TypeScriptClassRenderer.renderDeclaredInputType(box, aliases));
        var list = symbol(List.class.getName(), box);
        String nativeParameter = TypeScriptClassRenderer.renderTypeRef(list, aliases, true);
        assertTrue(nativeParameter.contains("$Box_<$Date>"), nativeParameter);
        assertFalse(nativeParameter.contains("$DateInput"), nativeParameter);
        assertEquals("$List_<$Box_<$DateInput>>", TypeScriptClassRenderer.renderDeclaredInputType(list, aliases));
        assertNull(aliases.getRegisteredAlias(List.class.getName()), "Builtin collections are not invented adapters");
    }

    @Test
    void samAndRegistryPayloadsRemainNominalInBothInputContexts() {
        var aliases = aliases();
        aliases.registerAdapterAlias(Function.class.getName(), "$Function_", 2);
        var function = symbol(Function.class.getName(), symbol(Date.class.getName()), symbol(Date.class.getName()));
        assertEquals("$Function_<$Date, $Date>", TypeScriptClassRenderer.renderDeclaredInputType(function, aliases));
        for (String holder : List.of("net.minecraft.core.Holder", "net.minecraft.core.HolderSet",
                "net.minecraft.resources.ResourceKey", "net.minecraft.tags.TagKey")) {
            aliases.registerAdapterAlias(holder, "$Payload_", 1);
            var type = symbol(holder, symbol(Date.class.getName()));
            assertEquals("$Payload_<$Date>", TypeScriptClassRenderer.renderDeclaredInputType(type, aliases));
        }
    }

    private static TypeAliasRegistry aliases() {
        var result = new TypeAliasRegistry();
        result.registerClassAlias(Date.class.getName(), "$DateInput");
        result.registerAdapterAlias(Box.class.getName(), "$Box_", 1);
        return result;
    }

    private static ApiTypeRef symbol(String name, ApiTypeRef... arguments) {
        return ApiTypeRef.symbol(new ApiSymbolId("java", name), List.of(arguments));
    }
}
