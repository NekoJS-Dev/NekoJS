package com.tkisor.nekojs.probe.backend.typescript;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeConstructorOverloadsTest {
    @Test
    void foldsOnlyContiguousEquivalentPrefixesRegardlessOfDeclarationOrder() {
        var constructors = List.of(List.of("input", "output", "time"), List.of("input"), List.of("input", "output"));
        var overloads = RecipeConstructorOverloads.plan(constructors, false);
        assertEquals(List.of(new RecipeConstructorOverloads.Overload(List.of("input", "output", "time"), 1)), overloads);
        assertThrows(UnsupportedOperationException.class, () -> overloads.add(overloads.getFirst()));
        assertThrows(UnsupportedOperationException.class, () -> overloads.getFirst().fields().clear());
    }

    @Test
    void retainsGapsAndChangedFieldOrderAsSeparateSignatures() {
        var gap = List.of(List.of("input"), List.of("input", "output", "time"));
        assertEquals(List.of(new RecipeConstructorOverloads.Overload(gap.get(0), 1),
                new RecipeConstructorOverloads.Overload(gap.get(1), 3)), RecipeConstructorOverloads.plan(gap, false));
        var changed = List.of(List.of("input"), List.of("output", "input"));
        assertEquals(List.of(new RecipeConstructorOverloads.Overload(changed.get(0), 1),
                new RecipeConstructorOverloads.Overload(changed.get(1), 2)), RecipeConstructorOverloads.plan(changed, false));
    }

    @Test
    void doesNotFoldAmbiguousAritiesOrAnyConstructorOverrides() {
        var ambiguous = List.of(List.of("input"), List.of("output"), List.of("input", "time"));
        assertEquals(List.of(new RecipeConstructorOverloads.Overload(ambiguous.get(0), 1),
                new RecipeConstructorOverloads.Overload(ambiguous.get(1), 1),
                new RecipeConstructorOverloads.Overload(ambiguous.get(2), 2)), RecipeConstructorOverloads.plan(ambiguous, false));
        var overridden = List.of(List.of("input"), List.of("input", "time"));
        assertEquals(List.of(new RecipeConstructorOverloads.Overload(overridden.get(0), 1),
                new RecipeConstructorOverloads.Overload(overridden.get(1), 2)), RecipeConstructorOverloads.plan(overridden, true));
    }

    @Test
    void foldsZeroArgumentPrefixesButNeverInventsMissingIntermediateArities() {
        var constructors = List.of(List.<String>of(), List.of("input"), List.of("input", "output", "time"));
        assertEquals(List.of(new RecipeConstructorOverloads.Overload(List.of("input"), 0),
                new RecipeConstructorOverloads.Overload(constructors.get(2), 3)), RecipeConstructorOverloads.plan(constructors, false));
        assertEquals(List.of(), RecipeConstructorOverloads.plan(List.of(), false));
    }
}
