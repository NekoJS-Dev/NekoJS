package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Renders registry literal shapes in {@link AdapterAliasGenerator}.
 *
 * <p>The point is that input positions must still complete: a bare {@code string} swallows the id
 * literals of the union, so prefix syntax such as {@code #tag} / {@code @mod} / {@code /regex}
 * has to use a template literal type ({@link AdapterInputShape.TemplateValue}), not {@code string}.
 */
class AdapterAliasGeneratorTest {

    /** Fixture for the alias target: the package path decides which module holds the alias declaration. */
    static class AliasTarget {}

    private static AdapterAliasGenerator.AdapterAlias render(List<AdapterInputShape> shapes) {
        AdapterAliasGenerator generator = new AdapterAliasGenerator(new TypeAliasRegistry());
        AdapterCatalogEntry entry = new AdapterCatalogEntry(
                AliasTarget.class, shapes, ConversionPrecedence.LOW, Optional.empty());
        generator.prepare(List.of(entry), Set.of(AliasTarget.class.getName()));
        AdapterAliasGenerator.AdapterAlias alias = generator.getAlias(AliasTarget.class.getName());
        assertNotNull(alias, "alias not generated");
        return alias;
    }

    @Test
    void registryTagRendersTagLiteralUnionAndPullsRegistryImport() {
        var alias = render(List.of(self(), registryTag("Item")));
        assertEquals("$AdapterAliasGeneratorTest$AliasTarget | RegistryTypes.ItemTag", alias.union());
        assertTrue(alias.usesRegistry(), "RegistryTypes import must be requested");
    }

    @Test
    void templateWrapsHoleInBacktickTemplateLiteral() {
        var alias = render(List.of(
                registry("Item"),
                template("#", registryTag("Item")),
                template("@", namespace()),
                literal("*"),
                template("/", string())));
        assertEquals("RegistryTypes.Item | `#${RegistryTypes.ItemTag}` | `@${RegistryTypes.Namespace}`"
                        + " | \"*\" | `/${string}`",
                alias.union());
        assertTrue(alias.usesRegistry());
    }

    @Test
    void unionSlotIsParenthesizedAndKeepsRegistryImport() {
        var alias = render(List.of(object(
                Slot.opt("output", union(registry("Item"), template("#", registryTag("Item")))),
                Slot.opt("id", string()))));
        assertEquals("{ output?: (RegistryTypes.Item | `#${RegistryTypes.ItemTag}`), id?: string }",
                alias.union());
        assertTrue(alias.usesRegistry());
    }

    @Test
    void templateSuffixIsAppendedAfterTheHole() {
        var alias = render(List.of(template("/", string(), "/")));
        assertEquals("`/${string}/`", alias.union());
        assertFalse(alias.usesRegistry(), "no registry referenced");
    }

    @Test
    void objectSlotsCanUseRegistryAndTagLiterals() {
        var alias = render(List.of(object(
                Slot.opt("item", registry("Item")),
                Slot.opt("tag", registryTag("Item")),
                Slot.opt("mod", string()))));
        assertEquals("{ item?: RegistryTypes.Item, tag?: RegistryTypes.ItemTag, mod?: string }",
                alias.union());
    }

    @Test
    void namespaceRendersSharedNamespaceUnionAndPullsRegistryImport() {
        // The namespace union lives in @special/types next to the registry literals, so it also pulls the
        // RegistryTypes import; it is not split per registry (a namespace works no matter which registry lists it)
        var alias = render(List.of(self(), namespace(), object(Slot.opt("mod", namespace()))));
        assertEquals("$AdapterAliasGeneratorTest$AliasTarget | RegistryTypes.Namespace"
                        + " | { mod?: RegistryTypes.Namespace }",
                alias.union());
        assertTrue(alias.usesRegistry());
    }

    @Test
    void bareStringStillRendersAsStringForAdaptersThatWantIt() {
        var alias = render(List.of(self(), string()));
        assertEquals("$AdapterAliasGeneratorTest$AliasTarget | string", alias.union());
        assertFalse(alias.usesRegistry());
    }

    @Test
    void nestedSelfWidensToInputAliasWhileTopLevelStaysTheClass() {
        // Nested positions (array elements and slots) also go through the adapter at runtime, so they widen
        // to $Foo_; widening the top level too would write $Foo_ = $Foo_ | ... and alias itself in a cycle
        var alias = render(List.of(self(), arrayOf(self()), object(
                Slot.opt("any", arrayOf(self())),
                Slot.opt("not", self()))));
        assertEquals("$AdapterAliasGeneratorTest$AliasTarget"
                        + " | $AdapterAliasGeneratorTest$AliasTarget_[]"
                        + " | { any?: $AdapterAliasGeneratorTest$AliasTarget_[],"
                        + " not?: $AdapterAliasGeneratorTest$AliasTarget_ }",
                alias.union());
    }

    @Test
    void nestedHostWidensToThatHostsInputAliasRegardlessOfAdapterOrder() {
        // Widening also applies when SizedIngredient is registered before Ingredient: prepare records every
        // alias name before rendering
        AdapterAliasGenerator generator = new AdapterAliasGenerator(new TypeAliasRegistry());
        AdapterCatalogEntry outer = new AdapterCatalogEntry(
                AliasTarget.class,
                List.of(object(Slot.opt("inner", host(NestedTarget.class)), Slot.req("count", number()))),
                ConversionPrecedence.LOW, Optional.empty());
        AdapterCatalogEntry inner = new AdapterCatalogEntry(
                NestedTarget.class, List.of(self(), string()), ConversionPrecedence.LOW, Optional.empty());
        generator.prepare(List.of(outer, inner),
                Set.of(AliasTarget.class.getName(), NestedTarget.class.getName()));

        assertEquals("{ inner?: $AdapterAliasGeneratorTest$NestedTarget_, count: number }",
                generator.getAlias(AliasTarget.class.getName()).union());
    }

    /** Fixture for a nested host: the type has an adapter alias of its own. */
    static class NestedTarget {}
}
