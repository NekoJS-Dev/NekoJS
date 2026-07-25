package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class JavaMemberIndexTest {

    @Test
    void returnsCallerFallThroughMarkerForUnmappedMember() throws Exception {
        Method plain = Fixture.class.getMethod("plain");
        String fallThrough = new String("fall-through");

        assertSame(fallThrough, JavaMemberIndex.remapName(plain, "hidden", fallThrough));
    }

    @Test
    void preservesNameWhenPrefixWouldProduceAnEmptyBinding() throws Exception {
        Method getter = PrefixOnly.class.getMethod("get");

        assertEquals("get", JavaMemberIndex.remapName(getter, "hidden", getter.getName()));
    }

    @Test
    void appliesMemberMappingsBeforePrefixMappings() throws Exception {
        Method renamed = Fixture.class.getMethod("getRenamed");
        Method prefixed = Fixture.class.getMethod("getValue");
        Field classPrefixed = Fixture.class.getField("isReady");

        assertEquals("renamed", JavaMemberIndex.remapName(renamed, "hidden", "fall-through"));
        assertEquals("Value", JavaMemberIndex.remapName(prefixed, "hidden", "fall-through"));
        assertEquals("Ready", JavaMemberIndex.remapName(classPrefixed, "hidden", "fall-through"));
    }

    @Test
    void returnsCallerHideMarkerForHiddenMember() throws Exception {
        Method hidden = Fixture.class.getMethod("hidden");
        String hideMarker = new String("hidden");

        assertSame(hideMarker, JavaMemberIndex.remapName(hidden, hideMarker, "fall-through"));
    }

    @RemapByPrefix({"get", "is"})
    static class Fixture {
        public String isReady;

        public void plain() {
        }

        @Remap("renamed")
        public void getRenamed() {
        }

        public void getValue() {
        }

        @HideFromJS
        public void hidden() {
        }
    }

    @RemapByPrefix("get")
    static class PrefixOnly {
        public void get() {
        }
    }
}
