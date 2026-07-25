package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JavaMemberIndex} 测试：成员名集合（含与 propertyMembersOf 的历史不对称）、拼写建议、
 * 注解驱动的 remapName 优先级与 fall-through marker 模式。
 */
class JavaMemberIndexTest {

    // ==================== 成员名集合 ====================

    public static class FakeEvent {
        public String getName() { return "neo"; }
        public boolean isAlive() { return true; }
        public String greet(String who) { return "hi " + who; }
        public String getNull() { return null; }
        public int count = 7;
    }

    @Test
    void allMembersOfContainsMethodsPropertiesAndFields() {
        Set<String> all = JavaMemberIndex.allMembersOf(FakeEvent.class);
        // 方法名（原样）
        assertTrue(all.contains("getName"), all.toString());
        assertTrue(all.contains("isAlive"), all.toString());
        assertTrue(all.contains("greet"), all.toString());
        assertTrue(all.contains("getNull"), all.toString());
        // 无参 getter 属性名（getEntity→entity）
        assertTrue(all.contains("name"), all.toString());
        assertTrue(all.contains("alive"), all.toString());
        assertTrue(all.contains("null"), all.toString());
        // public 字段
        assertTrue(all.contains("count"), all.toString());
        // getClass 被显式排除
        assertFalse(all.contains("getClass"), all.toString());
    }

    @Test
    void propertyMembersOfExcludesObjectClassMethods() {
        Set<String> prop = JavaMemberIndex.propertyMembersOf(FakeEvent.class);
        assertTrue(prop.contains("getName"), prop.toString());
        assertTrue(prop.contains("name"), prop.toString());
        assertTrue(prop.contains("count"), prop.toString());
        // 关键差异：propertyMembersOf 排除 Object.class 声明的方法
        assertFalse(prop.contains("equals"), prop.toString());
        assertFalse(prop.contains("hashCode"), prop.toString());
        assertFalse(prop.contains("toString"), prop.toString());
    }

    @Test
    void allMembersOfDoesNotExcludeObjectClassMethods() {
        // 历史不对称：allMembersOf（全局绑定校验用）不排除 Object.class 方法
        Set<String> all = JavaMemberIndex.allMembersOf(FakeEvent.class);
        assertTrue(all.contains("equals"), all.toString());
        assertTrue(all.contains("hashCode"), all.toString());
        assertTrue(all.contains("toString"), all.toString());
        assertFalse(all.contains("getClass"), all.toString());
    }

    @Test
    void suggestMemberFindsCloseWithinDistance() {
        Set<String> members = Set.of("getName", "isAlive", "greet");
        assertEquals("getName", JavaMemberIndex.suggestMember(members, "getNam"));  // 距离 1
        assertEquals("greet", JavaMemberIndex.suggestMember(members, "grete"));      // 距离 1
        assertNull(JavaMemberIndex.suggestMember(members, "zzzzzzzzz"));             // 超距
    }

    @Test
    void unknownMemberMessageIncludesClassAndSuggestion() {
        String msg = JavaMemberIndex.unknownMemberMessage(FakeEvent.class, "nam");
        assertTrue(msg.contains("FakeEvent"), msg);
        assertTrue(msg.contains("'nam'"), msg);
        assertTrue(msg.contains("Did you mean"), msg);
        assertTrue(msg.contains("name"), msg);
    }

    // ==================== remapName 注解优先级 ====================

    /** 类级 {@code @RemapByPrefix({"get"})}；各成员覆盖不同优先级。 */
    @RemapByPrefix({"get"})
    public static class Annotated {
        @HideFromJS
        public void hidden() {}

        @Remap("customName")
        public void remapped() {}

        @RemapByPrefix({"do"})   // 成员级覆盖类级
        public void doThing() {}

        public void getFoo() {}  // 走类级 get→Foo
    }

    @Test
    void remapNameHideFromJsReturnsHideMarker() throws NoSuchMethodException {
        Method m = Annotated.class.getMethod("hidden");
        assertEquals("HIDE", JavaMemberIndex.remapName(m, "HIDE", m.getName()));
        assertNull(JavaMemberIndex.remapName(m, null, m.getName()));
    }

    @Test
    void remapNameRemapAnnotationWins() throws NoSuchMethodException {
        Method m = Annotated.class.getMethod("remapped");
        assertEquals("customName", JavaMemberIndex.remapName(m, null, m.getName()));
    }

    @Test
    void remapNameMemberLevelRemapByPrefixOverridesClass() throws NoSuchMethodException {
        Method m = Annotated.class.getMethod("doThing");
        assertEquals("Thing", JavaMemberIndex.remapName(m, null, m.getName()));
    }

    @Test
    void remapNameClassLevelRemapByPrefix() throws NoSuchMethodException {
        Method m = Annotated.class.getMethod("getFoo");
        assertEquals("Foo", JavaMemberIndex.remapName(m, null, m.getName()));
    }

    @Test
    void remapNameReturnsCallerMarkerWhenNoMappingApplies() throws NoSuchMethodException {
        Method m = FakeEvent.class.getMethod("greet", String.class);
        String fallThrough = new String("FALL_THROUGH");
        assertSame(fallThrough, JavaMemberIndex.remapName(m, null, fallThrough));
    }

    /** name 恰等于前缀时不产生空 JS binding。 */
    public static class PrefixEdge {
        @RemapByPrefix({"ge"})
        public void ge() {}
    }

    @Test
    void remapNameRejectsEmptyPrefixStrip() throws NoSuchMethodException {
        Method m = PrefixEdge.class.getMethod("ge");
        assertEquals("ge", JavaMemberIndex.remapName(m, null, m.getName()));
    }
}
