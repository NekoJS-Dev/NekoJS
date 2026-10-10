package com.tkisor.nekojs.probe.backend.python;

import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PythonBeanMemberCollisionTest {
    public interface Matches {
        default int matches(String input) { return input.length(); }
    }

    public static class MixedMatches implements Matches {
        public static boolean matches(String left, String right) { return left.equals(right); }
        public static boolean matches(String left, String right, boolean enabled) { return enabled && left.equals(right); }
    }

    public static class MixedChild extends MixedMatches {
        public static boolean combine(String left, String right) { return left.equals(right); }
        public int combine(int input) { return input; }
    }
    public static class SameNameChild extends MixedMatches {
        public static boolean matches(String left, String right, int repetitions) {
            return repetitions > 0 && left.equals(right);
        }
    }
    public static class FieldParent {
        public String getLevel() { return "read"; }
    }
    public static class FieldChild extends FieldParent {
        public int level = 7;
    }
    public static class StaticFieldName {
        public boolean tag = true;
        public boolean isTag() { return tag; }
        public static String tag(String input) { return input; }
    }
    public interface ReadWrite<T> {
        T getValue();
        void setValue(T value);
    }
    public static class GenericBean<T> implements ReadWrite<T> {
        private T value;
        public T getValue() { return value; }
        public void setValue(T value) { this.value = value; }
    }
    public static class GetterChild extends GenericBean<String> {
        public String getValue() { return super.getValue(); }
    }
    public static class NestedGetterParent {
        private String value;
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getGetValue() { return "nested"; }
    }
    public static class NestedGetterChild extends NestedGetterParent {
        public String getGetGetValue() { return "outer"; }
    }
    public static class GetterNamedField {
        public boolean isLive = true;
        public boolean isLive() { return false; }
    }
    public static class FieldAndNestedGetter {
        public boolean isLive = true;
        public boolean isLive() { return false; }
        public boolean getIsLive() { return true; }
    }
    public static class InheritedFieldAndNestedGetter extends GetterNamedField {
        public boolean getIsLive() { return true; }
    }
    public static class BooleanFieldParent {
        public boolean value = true;
    }
    public static class StringGetterChild extends BooleanFieldParent {
        public String getValue() { return "getter"; }
    }
    public static class StaticBooleanFieldParent {
        public static boolean value = true;
    }
    public static class InstanceStringGetterChild extends StaticBooleanFieldParent {
        public String getValue() { return "instance"; }
    }
    public static class BaseValue {}
    public static class PreciseValue extends BaseValue {
        public boolean precise() { return true; }
    }
    public static class PreciseFieldParent {
        public PreciseValue value = new PreciseValue();
    }
    public static class BroaderGetterMiddle extends PreciseFieldParent {
        public BaseValue getValue() { return new BaseValue(); }
    }
    public static class PreciseGetterChild extends BroaderGetterMiddle {
        public PreciseValue getValue() { return new PreciseValue(); }
    }

    @Test
    void covariantGetterOverridesNearestInheritedFieldProjection() throws Exception {
        var reflector = new TypeReflector();
        var declarations = List.of(reflector.reflect(BaseValue.class), reflector.reflect(PreciseValue.class),
                reflector.reflect(PreciseFieldParent.class), reflector.reflect(BroaderGetterMiddle.class),
                reflector.reflect(PreciseGetterChild.class));
        var available = declarations.stream().map(declaration -> declaration.fqn)
                .collect(java.util.stream.Collectors.toSet());
        var renderer = new PythonClassRenderer(new ApiTypeRefPyRenderer(available), declarations);
        var target = new PreciseGetterChild();
        var incoming = new PreciseValue();
        try (Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false)
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .build()) {
            context.getBindings("js").putMember("target", target);
            context.getBindings("js").putMember("incoming", incoming);
            assertTrue(context.eval("js", "target.value.precise() && target.getValue().precise()").asBoolean());
            context.eval("js", "target.value = incoming");
            assertTrue(target.value == incoming);
        }
        Path directory = Path.of("build", "probe-python-inherited-field-chain");
        Files.createDirectories(directory);
        StringBuilder generated = new StringBuilder();
        declarations.forEach(declaration -> generated.append(renderer.render(declaration)).append("\n"));
        Files.writeString(directory.resolve("fixture.pyi"), generated);
        String child = renderer.render(declarations.getLast());
        assertTrue(child.contains("def value(self) -> PythonBeanMemberCollisionTest_PreciseValue"), child);
        assertTrue(child.contains("def value(self, value: PythonBeanMemberCollisionTest_PreciseValue) -> None"), child);
    }

    @Test
    void ancestorStaticFieldDoesNotSuppressInstanceBeanRead() {
        var reflector = new TypeReflector();
        var parent = reflector.reflect(StaticBooleanFieldParent.class);
        var child = reflector.reflect(InstanceStringGetterChild.class);
        var renderer = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of(parent.fqn, child.fqn)),
                List.of(parent, child));
        try (Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false)
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .build()) {
            context.getBindings("js").putMember("target", new InstanceStringGetterChild());
            assertEquals("instance", context.eval("js", "target.value").asString());
            assertEquals("undefined:undefined", context.eval("js", "var member = Java.type('"
                    + InstanceStringGetterChild.class.getName() + "').value; typeof member + ':' + member").asString());
            assertTrue(context.eval("js", "Java.type('" + StaticBooleanFieldParent.class.getName()
                    + "').value === true").asBoolean());
        }
        String output = renderer.render(child);
        assertTrue(output.contains("@property\n    def value(self) -> str"), output);
        assertFalse(output.contains("@value.setter"), output);
        assertTrue(renderer.render(parent).contains("value: ClassVar[bool]"));
    }

    @Test
    void inheritedFieldRetainsDifferentBeanReadAndFieldWriteTypes() throws Exception {
        var reflector = new TypeReflector();
        var declarations = List.of(reflector.reflect(BooleanFieldParent.class),
                reflector.reflect(StringGetterChild.class));
        var available = declarations.stream().map(declaration -> declaration.fqn)
                .collect(java.util.stream.Collectors.toSet());
        var renderer = new PythonClassRenderer(new ApiTypeRefPyRenderer(available), declarations);
        var target = new StringGetterChild();
        try (Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false)
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .build()) {
            context.getBindings("js").putMember("target", target);
            assertTrue(context.eval("js", "target.value === 'getter' && target.getValue() === 'getter'").asBoolean());
            context.eval("js", "target.value = false");
            assertFalse(target.value);
            assertEquals("getter", context.eval("js", "target.value").asString());
        }
        String child = renderer.render(declarations.get(1));
        Path directory = Path.of("build", "probe-python-inherited-field");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("fixture.pyi"), renderer.render(declarations.get(0)) + "\n" + child);
        assertTrue(child.contains("@property\n    def value(self) -> str"), child);
        assertTrue(child.contains("@value.setter\n    def value(self, value: bool) -> None"), child);
        assertTrue(child.contains("def getValue(self) -> str"), child);
    }

    @Test
    void inheritedBeanFieldProjectionUsesEditedDeclarations() {
        var reflector = new TypeReflector();
        var parent = reflector.reflect(BooleanFieldParent.class);
        var child = reflector.reflect(StringGetterChild.class);
        var field = parent.fields.getFirst();
        var renderer = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of(parent.fqn, child.fqn)),
                List.of(parent, child));
        field.type = child.methods.stream().filter(method -> method.name.equals("getValue"))
                .findFirst().orElseThrow().returnType;
        assertFalse(renderer.render(child).contains("def value("));
        assertTrue(renderer.render(parent).contains("value: str"));
        field.isFinal = true;
        assertFalse(renderer.render(child).contains("@value.setter"));
        field.hidden = true;
        assertFalse(renderer.render(child).contains("@value.setter"));
        assertTrue(renderer.render(child).contains("def value(self) -> str"));
        field.hidden = false;
        field.isFinal = false;
        field.renameTo = "renamed";
        assertFalse(renderer.render(child).contains("@value.setter"));
        assertTrue(renderer.render(child).contains("def value(self) -> str"));
    }

    @Test
    void nestedAccessorAliasDoesNotReplaceDeclaredOrInheritedFields() throws Exception {
        var reflector = new TypeReflector();
        var parent = reflector.reflect(GetterNamedField.class);
        var combined = reflector.reflect(FieldAndNestedGetter.class);
        var inherited = reflector.reflect(InheritedFieldAndNestedGetter.class);
        var declarations = List.of(parent, combined, inherited);
        var available = declarations.stream().map(declaration -> declaration.fqn)
                .collect(java.util.stream.Collectors.toSet());
        var renderer = new PythonClassRenderer(new ApiTypeRefPyRenderer(available), declarations);
        try (Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false)
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .build()) {
            for (Class<?> type : List.of(FieldAndNestedGetter.class, InheritedFieldAndNestedGetter.class)) {
                context.getBindings("js").putMember("target", type.getConstructor().newInstance());
                assertTrue(context.eval("js", "target.isLive === true && target.live === false"
                        + " && target.isLive() === false && target.getIsLive() === true").asBoolean());
            }
        }
        String own = renderer.render(combined);
        String child = renderer.render(inherited);
        assertEquals(2L, own.lines().filter(line -> line.trim().startsWith("def isLive(")).count(), own);
        assertTrue(own.contains("@property\n    def isLive(self) -> bool"), own);
        assertTrue(own.contains("@isLive.setter\n    def isLive(self, value: bool) -> None"), own);
        assertFalse(child.contains("def isLive("), child);
        assertTrue(own.contains("def getIsLive(self) -> bool"), own);
        assertTrue(child.contains("def getIsLive(self) -> bool"), child);
    }

    @Test
    void getterNamedFieldDoesNotBecomeAnAccessorAliasCallConflict() {
        var declaration = new TypeReflector().reflect(GetterNamedField.class);
        var renderer = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of(declaration.fqn)),
                List.of(declaration));
        String output = renderer.render(declaration);
        assertTrue(output.contains("isLive: bool"), output);
        assertTrue(output.contains("@property\n    def live(self) -> bool"), output);
        assertFalse(output.contains("def isLive("), output);
        try (Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false)
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .build()) {
            context.getBindings("js").putMember("target", new GetterNamedField());
            assertTrue(context.eval("js", "target.isLive === true && target.live === false"
                    + " && target.isLive() === false").asBoolean());
        }
    }

    @Test
    void nestedGetterAliasesYieldToRealAccessorCallTargets() throws Exception {
        var reflector = new TypeReflector();
        var declarations = List.of(reflector.reflect(NestedGetterParent.class),
                reflector.reflect(NestedGetterChild.class), reflector.reflect(GetterNamedField.class),
                reflector.reflect(FieldAndNestedGetter.class), reflector.reflect(InheritedFieldAndNestedGetter.class));
        var available = declarations.stream().map(declaration -> declaration.fqn)
                .collect(java.util.stream.Collectors.toSet());
        var renderer = new PythonClassRenderer(new ApiTypeRefPyRenderer(available), declarations);
        StringBuilder generated = new StringBuilder();
        declarations.forEach(declaration -> generated.append(renderer.render(declaration)).append("\n"));
        Path directory = Path.of("build", "probe-python-nested-getter");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("fixture.pyi"), generated);
        String child = renderer.render(declarations.get(1));
        assertTrue(child.contains("def getGetValue(self) -> str"), child);
        assertTrue(child.contains("def getValue(self) -> str"), child);
        assertTrue(child.contains("def getGetGetValue(self) -> str"), child);
        assertFalse(child.contains("@property\n    def getValue("), child);
        assertFalse(child.contains("@property\n    def getGetValue("), child);
        assertTrue(child.contains("@value.setter\n    def value(self, value: str) -> None"), child);
        try (Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false)
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .build()) {
            context.getBindings("js").putMember("target", new NestedGetterChild());
            context.eval("js", "target.value = 'written'");
            assertTrue(context.eval("js", "target.value === 'written'"
                    + " && target.getGetValue() === 'nested' && target.getGetGetValue() === 'outer'").asBoolean());
        }
    }

    @Test
    void overriddenGetterKeepsInheritedSetterInPython() throws Exception {
        var reflector = new TypeReflector();
        var declarations = List.of(reflector.reflect(ReadWrite.class), reflector.reflect(GenericBean.class),
                reflector.reflect(GetterChild.class));
        var available = declarations.stream().map(declaration -> declaration.fqn)
                .collect(java.util.stream.Collectors.toSet());
        var renderer = new PythonClassRenderer(new ApiTypeRefPyRenderer(available), declarations);
        StringBuilder generated = new StringBuilder("from typing import Any\n\n");
        declarations.forEach(declaration -> generated.append(renderer.render(declaration)).append("\n"));
        Path directory = Path.of("build", "probe-python-inherited-bean");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("fixture.pyi"), generated);
        String child = renderer.render(declarations.get(2));
        assertTrue(child.contains("@value.setter\n    def value(self, value: str) -> None"), child);
        assertTrue(child.contains("@property\n    def value(self) -> str"), child);
    }

    @Test
    void staticMethodsAndInstanceFieldsRetainDifferentBindings() {
        var declaration = new TypeReflector().reflect(StaticFieldName.class);
        String output = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of(declaration.fqn))).render(declaration);
        assertTrue(output.contains("class _NekoMeta_"), output);
        assertTrue(output.contains("def tag(self) -> _NekoStatic_"), output);
    }

    @Test
    void subclassClassBindingsRetainActualSuperclassOverloads() {
        var declaration = new TypeReflector().reflect(SameNameChild.class);
        String output = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of(declaration.fqn)))
                .render(declaration);
        assertTrue(output.contains("def __call__(self, left: str, right: str) -> bool"), output);
    }

    @Test
    void mixedStaticAndInstanceCallsRetainTheirRealBinding() throws Exception {
        var reflector = new TypeReflector();
        var parent = reflector.reflect(Matches.class);
        var mixed = reflector.reflect(MixedMatches.class);
        var child = reflector.reflect(MixedChild.class);
        var same = reflector.reflect(SameNameChild.class);
        var fieldParent = reflector.reflect(FieldParent.class);
        var fieldChild = reflector.reflect(FieldChild.class);
        var fieldStatic = reflector.reflect(StaticFieldName.class);
        var renderer = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of(parent.fqn, mixed.fqn, child.fqn,
                same.fqn, fieldParent.fqn, fieldChild.fqn, fieldStatic.fqn)),
                List.of(parent, mixed, child, same, fieldParent, fieldChild, fieldStatic));
        Path output = Path.of("build", "probe-python-mixed-binding", "fixture.pyi");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "from typing import Protocol, overload\n\n"
                + renderer.render(parent) + "\n" + renderer.render(mixed) + "\n" + renderer.render(child)
                + "\n" + renderer.render(same) + "\n" + renderer.render(fieldParent)
                + "\n" + renderer.render(fieldChild) + "\n" + renderer.render(fieldStatic));
        try (Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false)
                .option("js.nashorn-compat", "true")
                .build()) {
            context.getBindings("js").putMember("mixed", new MixedMatches());
            assertTrue(context.eval("js", "Java.type('" + MixedMatches.class.getName()
                    + "').matches('x', 'x') && mixed.matches('word') === 4").asBoolean());
            assertTrue(context.eval("js", "var rejected = false; try { mixed.matches('x', 'x'); }"
                    + " catch (error) { rejected = true; } rejected").asBoolean());
            assertTrue(context.eval("js", "Java.type('" + SameNameChild.class.getName()
                    + "').matches('x', 'x')").asBoolean());
            context.getBindings("js").putMember("fieldStatic", new StaticFieldName());
            assertTrue(context.eval("js", "fieldStatic.tag === true && Java.type('" + StaticFieldName.class.getName()
                    + "').tag('class') === 'class'").asBoolean());
        }
    }
    public static final class CollisionFixture {
        public int count = 4;
        private String value = "bean";

        public String getRead() { return value; }
        public void setRead(String value) { this.value = value; }
        public void setRead(int value) { this.value = Integer.toString(value); }
        public String read() { return "method"; }
        public int getCount() { return count; }
        public void setCount(int value) { count = value; }
        public String getName() { return "fixture"; }
        public static String name(String value) { return "factory:" + value; }
        public boolean isReady() { return true; }
    }

    @Test
    void originalAccessorsAndRealMethodsRemainCallableUnderProductionHostAccess() {
        try (Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false)
                .option("js.nashorn-compat", "true")
                .build()) {
            context.getBindings("js").putMember("fixture", new CollisionFixture());
            assertTrue(context.eval("js", "fixture.read() === 'method' && fixture.getRead() === 'bean'").asBoolean());
            assertTrue(context.eval("js", "fixture.setRead('changed'); fixture.getRead() === 'changed'").asBoolean());
            assertTrue(context.eval("js", "fixture.setRead(7); fixture.getRead() === '7'").asBoolean());
            assertTrue(context.eval("js", "fixture.setCount(9); fixture.count === 9 && fixture.getCount() === 9").asBoolean());
            assertTrue(context.eval("js", "Java.type('" + CollisionFixture.class.getName()
                    + "').name('x') === 'factory:x' && fixture.getName() === 'fixture'").asBoolean());
        }
    }

    @Test
    void collisionsKeepOriginalAccessorsWithoutShadowingMethodsOrFields() {
        var declaration = new TypeReflector().reflect(CollisionFixture.class);
        var renderer = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of(declaration.fqn)));
        String output = renderer.render(declaration);
        assertTrue(output.contains("def getRead(self) -> str"), output);
        assertTrue(output.contains("def setRead(self, value: str) -> None"), output);
        assertTrue(output.contains("def read(self) -> str"), output);
        assertTrue(output.contains("def getCount(self) -> int"), output);
        assertTrue(output.contains("def setCount(self, value: int) -> None"), output);
        assertTrue(output.contains("@property\n    def count(self) -> int"), output);
        assertTrue(output.contains("def getName(self) -> str"), output);
        assertTrue(output.contains("def name(value: str) -> str"), output);
        assertFalse(output.contains("@property\n    def read("), output);
        assertFalse(output.contains("@property\n    def name("), output);
        assertTrue(output.contains("@property\n    def ready(self) -> bool"), output);
        assertTrue(output.contains("@overload\n    def setRead(self, value: int) -> None"), output);
        assertTrue(output.contains("@overload\n    def setRead(self, value: str) -> None"), output);
        assertTrue(PythonClassRenderer.hasOverloads(declaration));
    }

    @Test
    void hiddenOrRenamedMembersDoNotSuppressUnambiguousBeanProperties() {
        var declaration = new TypeReflector().reflect(CollisionFixture.class);
        declaration.methods.stream().filter(m -> m.name.equals("read")).forEach(m -> m.hidden = true);
        declaration.methods.stream().filter(m -> m.name.equals("name")).forEach(m -> m.renameTo = "factory");
        declaration.fields.forEach(f -> f.hidden = true);
        String output = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of(declaration.fqn))).render(declaration);
        assertTrue(output.contains("@property\n    def read(self) -> str"), output);
        assertTrue(output.contains("@property\n    def count(self) -> int"), output);
        assertTrue(output.contains("@property\n    def name(self) -> str"), output);
        assertTrue(output.contains("def factory(value: str) -> str"), output);
        assertFalse(output.contains("def getRead("), output);
        assertFalse(output.contains("def getCount("), output);
    }
}
