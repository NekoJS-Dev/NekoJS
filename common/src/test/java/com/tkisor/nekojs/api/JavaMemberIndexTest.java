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

    /** mixin/interface-injection 场景：注解在接口上，注入后方法反射自宿主类。 */
    @RemapByPrefix("neko$")
    public interface InjectedSpec {
        Object neko$data();
    }

    public static class HostWithInjectedInterface implements InjectedSpec {
        @Override
        public String neko$data() { return "x"; }
    }

    @Test
    void remapNameInheritsClassLevelPrefixFromInterfaces() throws NoSuchMethodException {
        // 宿主类方法（declaringClass=HostWithInjectedInterface）必须通过其接口上的
        // @RemapByPrefix 命中 remap——否则 probe 声明（neko$data）与运行时 JS 名（data）脱节
        Method m = HostWithInjectedInterface.class.getMethod("neko$data");
        assertEquals("data", JavaMemberIndex.remapName(m, null, m.getName()));
    }

    @RemapByPrefix("neko$")
    public interface InjectedStackSpec {
        String neko$getId();
    }

    public static class HostWithInjectedStack implements InjectedStackSpec {
        @Override
        public String neko$getId() { return "minecraft:stone"; }
    }

    public static class HostWithUnmappedInternal {
        public String neko$internal() { return "hidden"; }
    }

    @Test
    void injectedRemappedMethodsAppearInAllVisibilityIndexesButInternalRemainHidden() {
        assertTrue(JavaMemberIndex.allMembersOf(HostWithInjectedStack.class).contains("getId"));
        assertTrue(JavaMemberIndex.propertyMembersOf(HostWithInjectedStack.class).contains("getId"));
        assertTrue(JavaMemberIndex.exposedMembersOf(HostWithInjectedStack.class).hasMember("getId"));
        assertTrue(JavaMemberIndex.exposedMembersOf(HostWithInjectedStack.class).hasMember("id"));
        assertFalse(JavaMemberIndex.allMembersOf(HostWithUnmappedInternal.class).contains("neko$internal"));
        assertFalse(JavaMemberIndex.exposedMembersOf(HostWithUnmappedInternal.class).hasMember("internal"));
    }

    @Test
    void remapNameReturnsCallerMarkerWhenNoMappingApplies() throws NoSuchMethodException {
        Method m = FakeEvent.class.getMethod("greet", String.class);
        String fallThrough = new String("FALL_THROUGH");
        assertSame(fallThrough, JavaMemberIndex.remapName(m, null, fallThrough));
    }

    @Test
    void remapNameCacheDistinguishesHideMarkers() throws NoSuchMethodException {
        // 缓存 key 必须包含 hideMarker：同一隐藏成员按调用方语义返回不同 marker
        Method m = Annotated.class.getMethod("hidden");
        assertEquals("HIDE", JavaMemberIndex.remapName(m, "HIDE", m.getName()));
        assertEquals("GONE", JavaMemberIndex.remapName(m, "GONE", m.getName()));
        assertNull(JavaMemberIndex.remapName(m, null, m.getName()));
    }

    @Test
    void remapNameCacheDistinguishesFallThroughMarkers() throws NoSuchMethodException {
        // 无 remap 的成员：结果就是各自的 fallThroughMarker，缓存 key 必须区分
        Method m = FakeEvent.class.getMethod("greet", String.class);
        String a = new String("FALL_A");
        String b = new String("FALL_B");
        assertSame(a, JavaMemberIndex.remapName(m, null, a));
        assertSame(b, JavaMemberIndex.remapName(m, null, b));
        assertSame(a, JavaMemberIndex.remapName(m, null, a));
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

    // ==================== ExposedMembers：类型化重载保留索引 ====================

    /** 重载 + getter 属性 + 字段 + generic 返回。 */
    public static class TypedEvent {
        public Object getServer() { return new Object(); }
        public PlayerJS getPlayer() { return null; }
        public PlayerJS find(String name) { return null; }
        public PlayerJS find(int id) { return null; }
        public String varargsJoin(String sep, String... parts) { return ""; }
        public java.util.List<PlayerJS> getPlayers() { return java.util.List.of(); }
        public PlayerJS directField = null;
    }

    public static class PlayerJS {
        public Object getServer() { return new Object(); }
    }

    @Test
    void exposedMembersKeepsAllOverloads() {
        JavaMemberIndex.ExposedMembers exposed = JavaMemberIndex.exposedMembersOf(TypedEvent.class);
        assertEquals(2, exposed.methods().get("find").size(), "overloads must be preserved");
    }

    @Test
    void exposedMembersCallReturnTypesFiltersByArity() {
        JavaMemberIndex.ExposedMembers exposed = JavaMemberIndex.exposedMembersOf(TypedEvent.class);
        // find(String) 与 find(int) 都是 1 参数：arity=1 两个候选都匹配（保守 union）
        assertEquals(2, exposed.callReturnTypes("find", 1).size());
        assertEquals(0, exposed.callReturnTypes("find", 2).size(), "no overload with 2 args");
    }

    @Test
    void exposedMembersVarargsArity() {
        JavaMemberIndex.ExposedMembers exposed = JavaMemberIndex.exposedMembersOf(TypedEvent.class);
        // varargsJoin(sep, parts...)：JS 侧至少要传 sep（1 个参数）
        assertEquals(1, exposed.callReturnTypes("varargsJoin", 1).size(), "sep only");
        assertEquals(1, exposed.callReturnTypes("varargsJoin", 3).size(), "sep + 2 parts");
        assertEquals(0, exposed.callReturnTypes("varargsJoin", 0).size(), "sep is required");
        assertTrue(exposed.hasMember("varargsJoin"));
    }

    @Test
    void exposedMembersPropertyTypesFromGetterAndField() {
        JavaMemberIndex.ExposedMembers exposed = JavaMemberIndex.exposedMembersOf(TypedEvent.class);
        assertTrue(exposed.propertyTypes("player").stream()
                .anyMatch(t -> JavaMemberIndex.typeClasses(t).contains(PlayerJS.class)));
        assertTrue(exposed.propertyTypes("directField").stream()
                .anyMatch(t -> JavaMemberIndex.typeClasses(t).contains(PlayerJS.class)));
        assertTrue(exposed.hasMember("server"), "getter property name");
        assertTrue(exposed.hasMember("getServer"), "method name");
    }

    @Test
    void exposedMembersGenericReturnResolvesRawClass() {
        JavaMemberIndex.ExposedMembers exposed = JavaMemberIndex.exposedMembersOf(TypedEvent.class);
        // List<PlayerJS> 只解析 raw 类 List（容器成员不等于元素成员，符合保守策略）
        assertTrue(exposed.callReturnTypes("getPlayers", 0).stream()
                .flatMap(t -> JavaMemberIndex.typeClasses(t).stream())
                .anyMatch(c -> c == java.util.List.class));
    }

    @Test
    void exposedMembersRespectsRemapAndHide() {
        JavaMemberIndex.ExposedMembers exposed = JavaMemberIndex.exposedMembersOf(Annotated.class);
        assertTrue(exposed.hasMember("customName"), "remapped name exposed");
        assertFalse(exposed.hasMember("remapped"), "original name hidden after remap");
        assertFalse(exposed.hasMember("hidden"), "@HideFromJS excluded");
        assertFalse(exposed.hasMember("getFoo"), "class-level remap renames getFoo");
        assertTrue(exposed.hasMember("Foo"), "class-level remap target exposed");
    }

    @Test
    void typeClassesOfUnknownReturnsEmpty() {
        assertTrue(JavaMemberIndex.typeClasses(void.class).isEmpty());
        assertTrue(JavaMemberIndex.typeClasses(int.class).isEmpty());
    }
}
