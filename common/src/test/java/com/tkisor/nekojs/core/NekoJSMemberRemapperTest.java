package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Remap;
import graal.mod.api.MemberRemapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class NekoJSMemberRemapperTest {

    private final NekoJSMemberRemapper remapper = new NekoJSMemberRemapper();

    @Test
    void fallsThroughForUnannotatedMembers() throws Exception {
        Field plainField = Fixture.class.getField("plainField");
        Method plainMethod = Fixture.class.getMethod("plainMethod");

        assertSame(MemberRemapper.FALL_THROUGH, remapper.remapField(plainField, Fixture.class));
        assertSame(MemberRemapper.FALL_THROUGH, remapper.remapMethod(plainMethod, Fixture.class));
    }

    @Test
    void emitsGraalHideMarkerForHiddenMembers() throws Exception {
        Method hidden = Fixture.class.getMethod("hiddenMethod");

        assertSame(MemberRemapper.HIDE_MEMBER, remapper.remapMethod(hidden, Fixture.class));
    }

    @Test
    void usesAnnotationMappingWhenExposedThroughASubtype() throws Exception {
        Method renamed = Fixture.class.getMethod("renamedMethod");

        assertEquals("renamed", remapper.remapMethod(renamed, Subtype.class));
    }

    static class Fixture {
        public String plainField;

        public void plainMethod() {
        }

        @HideFromJS
        public void hiddenMethod() {
        }

        @Remap("renamed")
        public void renamedMethod() {
        }
    }

    static class Subtype extends Fixture {
    }
}
