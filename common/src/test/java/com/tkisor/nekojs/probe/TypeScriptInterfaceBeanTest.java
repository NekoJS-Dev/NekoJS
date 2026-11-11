package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.BeanAliasExclusionCatalogEntry;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptInterfaceBeanTest {
    public interface ReadOnly {
        int getCount();
    }

    public interface ReadWrite {
        int getCount();
        void setCount(String value);
    }

    public interface Conflicting {
        int getCount();
        int count();
    }

    public interface OtherGetter {
        int getCount();
        boolean isActive();
    }

    public static class ClassBean {
        public int getCount() { return 0; }
        public void setCount(String value) {}
    }

    @Test
    void interfaceRetainsReadOnlyBeanPropertyAndRealGetter() {
        var declaration = new TypeReflector().reflect(ReadOnly.class);
        String output = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(declaration);
        assertTrue(output.contains("get count(): number;"), output);
        assertTrue(output.contains("getCount(): number;"), output);
        assertFalse(output.contains("set count("), output);
    }

    @Test
    void interfaceUsesSeparateReadAndWriteTypesWithoutChangingTheClassPath() {
        var renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        for (Class<?> type : List.of(ReadWrite.class, ClassBean.class)) {
            String output = renderer.render(new TypeReflector().reflect(type));
            assertTrue(output.contains("get count(): number;"), output);
            assertTrue(output.contains("set count(value: string);"), output);
            assertTrue(output.contains("getCount(): number;"), output);
            assertTrue(output.contains("setCount("), output);
            assertTrue(output.contains(": string): void;"), output);
            assertFalse(output.contains("set count(value: number)"), output);
        }
    }

    @Test
    void realInstanceMemberKeepsItsNameAndSuppressesOnlyTheConflictingAlias() {
        String output = new TypeScriptClassRenderer(new TypeAliasRegistry())
                .render(new TypeReflector().reflect(Conflicting.class));
        assertTrue(output.contains("getCount(): number;"), output);
        assertTrue(output.contains("count(): number;"), output);
        assertFalse(output.contains("get count()"), output);
        assertEquals(1, output.split("getCount", -1).length - 1, output);
    }

    @Test
    void exactAliasExclusionPreservesRealGettersAndOtherInterfaceProperties() {
        var declaration = new TypeReflector().reflect(OtherGetter.class);
        var renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        String before = renderer.render(declaration);
        renderer.setBeanAliasExclusions(List.of(new BeanAliasExclusionCatalogEntry(
                OtherGetter.class, "getCount", "count")));
        String excluded = renderer.render(declaration);
        assertTrue(before.contains("get count(): number;"), before);
        assertFalse(excluded.contains("get count()"), excluded);
        assertTrue(excluded.contains("getCount(): number;"), excluded);
        assertTrue(excluded.contains("get active(): boolean;"), excluded);
        assertTrue(excluded.contains("isActive(): boolean;"), excluded);
        renderer.setBeanAliasExclusions(List.of());
        assertEquals(before, renderer.render(declaration));
    }

    @Test
    void explicitGetterOverrideUsesTheSamePropertyOnlyContractAsClasses() {
        var renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        renderer.overrideGetter(ReadWrite.class, "count", "boolean", "");
        renderer.overrideGetter(ClassBean.class, "count", "boolean", "");
        for (Class<?> type : List.of(ReadWrite.class, ClassBean.class)) {
            String output = renderer.render(new TypeReflector().reflect(type));
            assertTrue(output.contains("get count(): boolean;"), output);
            assertFalse(output.contains("getCount("), output);
            assertFalse(output.contains("set count("), output);
            assertTrue(output.contains("setCount("), output);
            assertTrue(output.contains(": string): void;"), output);
        }
    }
}
