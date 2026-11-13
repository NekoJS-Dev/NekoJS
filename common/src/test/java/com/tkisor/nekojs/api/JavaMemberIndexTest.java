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
 * Tests for {@link JavaMemberIndex}: member name sets (including the asymmetry against
 * propertyMembersOf), spelling suggestions, annotation-driven remapName precedence, and the
 * fall-through marker pattern.
 */
class JavaMemberIndexTest {

    // ==================== Member name sets ====================

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
        // Method names, unchanged
        assertTrue(all.contains("getName"), all.toString());
        assertTrue(all.contains("isAlive"), all.toString());
        assertTrue(all.contains("greet"), all.toString());
        assertTrue(all.contains("getNull"), all.toString());
        // No-arg getter property names (getEntity -> entity)
        assertTrue(all.contains("name"), all.toString());
        assertTrue(all.contains("alive"), all.toString());
        assertTrue(all.contains("null"), all.toString());
        // Public fields
        assertTrue(all.contains("count"), all.toString());
        // getClass is explicitly excluded
        assertFalse(all.contains("getClass"), all.toString());
    }

    @Test
    void propertyMembersOfExcludesObjectClassMethods() {
        Set<String> prop = JavaMemberIndex.propertyMembersOf(FakeEvent.class);
        assertTrue(prop.contains("getName"), prop.toString());
        assertTrue(prop.contains("name"), prop.toString());
        assertTrue(prop.contains("count"), prop.toString());
        // Key difference: propertyMembersOf excludes methods declared on Object.class
        assertFalse(prop.contains("equals"), prop.toString());
        assertFalse(prop.contains("hashCode"), prop.toString());
        assertFalse(prop.contains("toString"), prop.toString());
    }

    @Test
    void allMembersOfDoesNotExcludeObjectClassMethods() {
        // Unlike propertyMembersOf, allMembersOf (used to validate global bindings) keeps Object.class methods
        Set<String> all = JavaMemberIndex.allMembersOf(FakeEvent.class);
        assertTrue(all.contains("equals"), all.toString());
        assertTrue(all.contains("hashCode"), all.toString());
        assertTrue(all.contains("toString"), all.toString());
        assertFalse(all.contains("getClass"), all.toString());
    }

    @Test
    void suggestMemberFindsCloseWithinDistance() {
        Set<String> members = Set.of("getName", "isAlive", "greet");
        assertEquals("getName", JavaMemberIndex.suggestMember(members, "getNam"));  // Distance 1
        assertEquals("greet", JavaMemberIndex.suggestMember(members, "grete"));      // Distance 1
        assertNull(JavaMemberIndex.suggestMember(members, "zzzzzzzzz"));             // Beyond the allowed distance
    }

    @Test
    void unknownMemberMessageIncludesClassAndSuggestion() {
        String msg = JavaMemberIndex.unknownMemberMessage(FakeEvent.class, "nam");
        assertTrue(msg.contains("FakeEvent"), msg);
        assertTrue(msg.contains("'nam'"), msg);
        assertTrue(msg.contains("Did you mean"), msg);
        assertTrue(msg.contains("name"), msg);
    }

    // ==================== remapName annotation precedence ====================

    /** Class-level {@code @RemapByPrefix({"get"})}; each member overrides a different level. */
    @RemapByPrefix({"get"})
    public static class Annotated {
        @HideFromJS
        public void hidden() {}

        @Remap("customName")
        public void remapped() {}

        @RemapByPrefix({"do"})   // Member level overrides class level
        public void doThing() {}

        public void getFoo() {}  // Uses the class-level get -> Foo prefix
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

    /** Mixin/interface-injection case: the annotation sits on the interface, but the injected method
     *  reflects from the host class. */
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
        // A host-class method (declaringClass=HostWithInjectedInterface) must pick up the remap from the
        // @RemapByPrefix on its interface. Otherwise the probe declaration (neko$data) and the runtime
        // JS name (data) drift apart.
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
        // The cache key must include hideMarker: the same hidden member returns a different marker per caller
        Method m = Annotated.class.getMethod("hidden");
        assertEquals("HIDE", JavaMemberIndex.remapName(m, "HIDE", m.getName()));
        assertEquals("GONE", JavaMemberIndex.remapName(m, "GONE", m.getName()));
        assertNull(JavaMemberIndex.remapName(m, null, m.getName()));
    }

    @Test
    void remapNameCacheDistinguishesFallThroughMarkers() throws NoSuchMethodException {
        // A member without a remap returns its own fallThroughMarker, so the cache key must tell them apart
        Method m = FakeEvent.class.getMethod("greet", String.class);
        String a = new String("FALL_A");
        String b = new String("FALL_B");
        assertSame(a, JavaMemberIndex.remapName(m, null, a));
        assertSame(b, JavaMemberIndex.remapName(m, null, b));
        assertSame(a, JavaMemberIndex.remapName(m, null, a));
    }

    /** A name that equals the prefix exactly must not produce an empty JS binding. */
    public static class PrefixEdge {
        @RemapByPrefix({"ge"})
        public void ge() {}
    }

    @Test
    void remapNameRejectsEmptyPrefixStrip() throws NoSuchMethodException {
        Method m = PrefixEdge.class.getMethod("ge");
        assertEquals("ge", JavaMemberIndex.remapName(m, null, m.getName()));
    }

    // ==================== ExposedMembers: index kept for typed overloads ====================

    /** Overloads, getter properties, fields and a generic return type. */
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
        // find(String) and find(int) both take 1 argument: arity=1 matches both candidates (conservative union)
        assertEquals(2, exposed.callReturnTypes("find", 1).size());
        assertEquals(0, exposed.callReturnTypes("find", 2).size(), "no overload with 2 args");
    }

    @Test
    void exposedMembersVarargsArity() {
        JavaMemberIndex.ExposedMembers exposed = JavaMemberIndex.exposedMembersOf(TypedEvent.class);
        // varargsJoin(sep, parts...): JS callers must pass at least sep (1 argument)
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
        // List<PlayerJS> resolves only to the raw class List: a container member is not an element member,
        // which keeps the policy conservative
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
