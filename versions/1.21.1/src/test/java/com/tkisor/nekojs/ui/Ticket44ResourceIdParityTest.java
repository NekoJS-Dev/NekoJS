package com.tkisor.nekojs.ui;

import com.tkisor.nekojs.api.ui.UiResourceId;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 44 node-side check: the common controlled id grammar stays compatible
 * with vanilla resource locations on this node — every id the UI contract accepts
 * is also a legal vanilla location, and the security-relevant rejections hold.
 */
class Ticket44ResourceIdParityTest {
    private static final List<String> ACCEPTED = List.of(
            "mymod:gui/panel",
            "minecraft:textures/gui/icons.png",
            "a.b-c_d:e/f.g-h_i",
            "0123:textures/gui/9x9",
            "gui/panel",
            "minecraft:font/default");

    private static final List<String> REJECTED = List.of(
            "Foo:bar",
            "foo:BAR",
            "foo:../secret",
            "foo:bar/../baz",
            "a:b:c",
            "foo:",
            ":foo",
            "",
            "foo bar");

    @Test
    void everyControlledIdIsAlsoAVanillaLocation() {
        for (String id : ACCEPTED) {
            assertTrue(UiResourceId.parse(id).isPresent(), "expected accepted: " + id);
            assertTrue(ResourceLocation.tryParse(id) != null, "vanilla should accept: " + id);
        }
    }

    @Test
    void controlledGrammarRejectsUnsafeOrMalformedIds() {
        for (String id : REJECTED) {
            assertTrue(UiResourceId.parse(id).isEmpty(), "expected rejected: " + id);
        }
    }

    @Test
    void parseResultsCarryCanonicalParts() {
        UiResourceId id = UiResourceId.parse("mymod:gui/panel.png").orElseThrow();
        assertEquals("mymod", id.namespace());
        assertEquals("gui/panel.png", id.path());
        assertEquals("mymod:gui/panel.png", id.toString());
    }
}
