package com.tkisor.nekojs.api.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiResourceIdTest {
    @Test
    void parsesNamespacedIds() {
        UiResourceId id = UiResourceId.parse("mymod:gui/panel.png").orElseThrow();
        assertEquals("mymod", id.namespace());
        assertEquals("gui/panel.png", id.path());
        assertEquals("mymod:gui/panel.png", id.toString());
    }

    @Test
    void missingNamespaceDefaultsToMinecraft() {
        UiResourceId id = UiResourceId.parse("gui/panel").orElseThrow();
        assertEquals("minecraft", id.namespace());
        assertEquals("gui/panel", id.path());
    }

    @Test
    void acceptsGrammarLegalCharacters() {
        assertTrue(UiResourceId.parse("a.b-c_d:e/f.g-h_i").isPresent());
        assertTrue(UiResourceId.parse("0123:textures/gui/9x9").isPresent());
    }

    @Test
    void rejectsUppercaseAndSpaces() {
        assertTrue(UiResourceId.parse("Foo:bar").isEmpty());
        assertTrue(UiResourceId.parse("foo:BAR").isEmpty());
        assertTrue(UiResourceId.parse("foo: bar").isEmpty());
        assertTrue(UiResourceId.parse("foo bar").isEmpty());
    }

    @Test
    void rejectsTraversalAndEmptySegments() {
        assertTrue(UiResourceId.parse("foo:../secret").isEmpty());
        assertTrue(UiResourceId.parse("foo:bar/../baz").isEmpty());
        assertTrue(UiResourceId.parse("foo:./bar").isEmpty());
        assertTrue(UiResourceId.parse("foo:bar//baz").isEmpty());
        assertTrue(UiResourceId.parse("foo:bar/").isEmpty());
    }

    @Test
    void rejectsMalformedSeparatorsAndEmpty() {
        assertTrue(UiResourceId.parse("a:b:c").isEmpty());
        assertTrue(UiResourceId.parse(":foo").isEmpty());
        assertTrue(UiResourceId.parse("foo:").isEmpty());
        assertTrue(UiResourceId.parse("").isEmpty());
        assertTrue(UiResourceId.parse(null).isEmpty());
    }

    @Test
    void windowsStylePathsAreNotIds() {
        assertTrue(UiResourceId.parse("C:\\assets\\gui").isEmpty());
        assertTrue(UiResourceId.parse("..\\..\\secrets").isEmpty());
    }
}
