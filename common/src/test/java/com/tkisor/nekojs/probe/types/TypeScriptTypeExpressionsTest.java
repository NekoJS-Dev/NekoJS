package com.tkisor.nekojs.probe.types;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeScriptTypeExpressionsTest {
    @Test void primaryAndAlreadyGroupedTypesNeedNoAdditionalParentheses() {
        for (String type : new String[]{"string", "$Payload_", "scope.Host<T>", "Map<string, (x: T) => U>",
                "Map<string, List<T>>", "string[]", "(Host | Guest)", "(x & y)", "Box<'(>', T>"}) {
            assertEquals(type + "[]", TypeScriptTypeExpressions.arrayOf(type), type);
        }
    }

    @Test void compoundAndOpaqueTypesKeepTheirArrayElementPrecedence() {
        for (String type : new String[]{"A | B", "A & B", "(x: T) => U", "<T>(x: T) => T",
                "K extends string ? A : B", "keyof T", "readonly T[]", "typeof source", "T['name']",
                "(A) | B", "({a: A}) & B", "{ a: T }", "Box<"}) {
            assertEquals("(" + type + ")[]", TypeScriptTypeExpressions.arrayOf(type), type);
        }
    }
}
