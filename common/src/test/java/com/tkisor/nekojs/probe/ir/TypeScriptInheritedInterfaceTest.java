package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.NekoJSMemberRemapper;
import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.probe.backend.typescript.FunctionalInterfaceAliasGenerator;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TypeScriptInheritedInterfaceTest {
    public interface Values<T> {
        T value();
        default T echo(T input) { return input; }
        default <R> R identity(R input) { return input; }
        default T[] array(T[] input) { return input; }
        @HideFromJS
        default String hidden() { return "hidden"; }
        static String factory() { return "factory"; }
    }

    public interface ChildValues<T> extends Values<T> {}

    public interface Sized {
        default int size() { return 1; }
    }

    public static class Value<T> implements ChildValues<T>, Sized {
        private final T input;
        public Value(T input) { this.input = input; }
        public T value() { return input; }
        public int echo() { return 7; }
    }

    public static class TextValue extends Value<String> {
        public TextValue(String input) { super(input); }
    }

    public static class RemappedParent {
        @Remap("call")
        public String base(String input) { return input; }
    }

    public static class RemappedChild extends RemappedParent {
        @Remap("call")
        public int local(int input) { return input; }
    }

    public interface WideSetter {
        default void setValue(CharSequence input) {}
    }

    public static class OwnSetter implements WideSetter {
        private String value;
        public String getValue() { return value; }
        public void setValue(String input) { value = input; }
    }

    public interface ReadOnlyValue {
        default String getValue() { return "read"; }
    }

    public static class StaticSetter implements ReadOnlyValue {
        public static void setValue(String input) {}
    }

    public static class RootValue {
        public String marker = "root";
    }

    public static class Constrained<T extends RootValue> {
        public final T input;
        public Constrained(T input) { this.input = input; }
    }

    public interface GenericSource<A> {
        default <T> T transform(A input, T result) { return result; }
        default <T extends RootValue> Constrained<T> bounded(T input) { return new Constrained<>(input); }
    }

    public static class GenericValue<T> implements GenericSource<T> {}

    public interface FunctionalSource<A> {
        default <T> T apply(A input, Function<A, T> operation) { return operation.apply(input); }
    }

    public static class FunctionalValue<T> implements FunctionalSource<T> {}

    public static class Box<T> {
        public final T value;
        public Box(T value) { this.value = value; }
    }

    public interface ContainerSource<A> {
        default Box<A> pass(Box<A> input) { return input; }
        default Box<A> apply(Function<String, Box<A>> operation) { return operation.apply("key"); }
    }

    public static class MapContainer<K, V> implements ContainerSource<Map<K, V>> {}

    public interface LevelSource {
        default String getLevel() { return "accessor"; }
        default String level() { return "method"; }
        default void setLevel(String input) {}
    }
    public static class LevelMethods implements LevelSource {}
    public static class LevelParent {
        public String getLevel() { return "parent accessor"; }
    }
    public static class LevelField extends LevelParent {
        public String level = "field";
    }
    public static class DifferentLevelField extends LevelParent {
        public int level = 7;
    }
    public static class SetterNameCollision {
        public String getSetName() { return "getter"; }
        public void setName(String input) {}
    }
    public interface LevelGetter {
        default String getLevel() { return "getter"; }
    }
    public static class MethodParent {
        public String level() { return "inherited method"; }
    }
    public static class MethodChild extends MethodParent implements LevelGetter {}
    public interface BoundedCallback<T extends RootValue> {
        T apply(T input);
    }
    public static class BoundedReceiver {
        public RootValue apply(BoundedCallback<RootValue> callback) { return callback.apply(new RootValue()); }
    }
    public static class BoundedHost implements BoundedCallback<RootValue> {
        public RootValue apply(RootValue input) { return input; }
    }
    public interface ObjectContract {
        boolean equals(Object other);
        int hashCode();
    }
    public static class ObjectValue implements ObjectContract {}
    public static class ObjectChild extends ObjectValue {
        public boolean equals(String other) { return "child".equals(other); }
    }
    public interface ParentOperation extends ObjectContract {
        String operation();
    }
    public static class OperationParent implements ParentOperation {
        public String operation() { return "parent"; }
    }
    public static class OperationChild extends OperationParent {
        public boolean equals(String other) { return "child".equals(other); }
    }
    public interface BeanValue<T> {
        T getValue();
        void setValue(T value);
    }
    public static class BeanParent<T> implements BeanValue<T> {
        private T value;
        public T getValue() { return value; }
        public void setValue(T value) { this.value = value; }
    }
    public static class BeanChild extends BeanParent<String> {
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
    public interface ReadyContract {
        boolean getReady();
        boolean isReady();
    }
    public static class ReadyValue implements ReadyContract {
        public boolean getReady() { return true; }
        public boolean isReady() { return false; }
    }
    public static class SaveMethod {
        public boolean noSave() { return false; }
    }
    public static class SaveField extends SaveMethod {
        public boolean noSave = true;
    }
    public interface SelfComparable<T extends Comparable<T>> {
        T select(T input);
    }
    public static class ComparableReceiver {
        public int apply(SelfComparable<Integer> operation) { return operation.select(7); }
        public boolean flag(SelfComparable<Boolean> operation) { return operation.select(true); }
        public String text(SelfComparable<String> operation) { return operation.select("text"); }
    }
    public interface OtherComparable<T extends Comparable<String>> {}
    public static class NumericBounds {
        public <T extends Number & Comparable<T>> T select(T input) { return input; }
    }

    @Test
    void selfComparableBoundsRetainBoxedPrimitiveProjections() {
        try (Context context = context()) {
            context.getBindings("js").putMember("comparableReceiver", new ComparableReceiver());
            assertEquals(8, context.eval("js", "comparableReceiver.apply(function(input) { return input + 1; })")
                    .asInt());
            assertTrue(!context.eval("js", "comparableReceiver.flag(function(input) { return !input; })").asBoolean());
            assertEquals("text!", context.eval("js", "comparableReceiver.text(function(input) { return input + '!'; })")
                    .asString());
        }
        var registry = new TypeAliasRegistry();
        var generator = new FunctionalInterfaceAliasGenerator(registry);
        generator.prepare(Set.of(SelfComparable.class.getName(), Comparable.class.getName()), Set.of());
        String alias = generator.declaration(SelfComparable.class.getName());
        assertTrue(alias.contains("Host0 extends ($Comparable<Host0> | string | number | boolean) = any"), alias);
        String output = new TypeScriptClassRenderer(registry).render(new TypeReflector().reflect(SelfComparable.class));
        assertTrue(output.contains("T extends ($Comparable<T> | string | number | boolean)"), output);
        String otherBound = new TypeScriptClassRenderer(registry)
                .render(new TypeReflector().reflect(OtherComparable.class));
        assertTrue(otherBound.contains("T extends $Comparable<string>"), otherBound);
        assertTrue(!otherBound.contains("| number"), otherBound);
        String numeric = new TypeScriptClassRenderer(registry).render(new TypeReflector().reflect(NumericBounds.class));
        assertTrue(numeric.contains("T extends number & ($Comparable<T> | string | number | boolean)"), numeric);
    }

    @Test
    void interfaceObjectContractsRetainTheActualInheritedOperations() {
        var declaration = new TypeReflector().reflect(ObjectValue.class);
        assertTrue(declaration.methods.stream().anyMatch(method -> method.name.equals("equals")));
        assertTrue(declaration.methods.stream().anyMatch(method -> method.name.equals("hashCode")));
        assertTrue(declaration.methods.stream().noneMatch(method -> method.name.equals("getClass")));
        try (Context context = context()) {
            context.getBindings("js").putMember("objectValue", new ObjectValue());
            assertTrue(context.eval("js", "objectValue.equals(objectValue)").asBoolean());
            assertEquals("number", context.eval("js", "typeof objectValue.hashCode()").asString());
        }
    }

    @Test
    void superclassInterfaceContractsRetainObjectOverloads() {
        var declaration = new TypeReflector().reflect(ObjectChild.class);
        assertEquals(2, declaration.methods.stream().filter(method -> method.name.equals("equals")).count());
        assertTrue(declaration.methods.stream().anyMatch(method -> method.name.equals("hashCode")));
        assertTrue(declaration.methods.stream().noneMatch(method -> method.name.equals("getClass")));
        try (Context context = context()) {
            context.getBindings("js").putMember("objectChild", new ObjectChild());
            assertTrue(context.eval("js", "objectChild.equals(objectChild)").asBoolean());
            assertTrue(context.eval("js", "objectChild.equals('child')").asBoolean());
        }
    }

    @Test
    void ancestorContractsDoNotCopyOrdinaryConcreteParentMembers() {
        try (Context context = context()) {
            context.getBindings("js").putMember("operationChild", new OperationChild());
            assertEquals("parent", context.eval("js", "operationChild.operation()").asString());
            assertTrue(context.eval("js", "operationChild.equals(operationChild)").asBoolean());
        }
        var reflector = new TypeReflector();
        var parent = reflector.reflect(OperationParent.class);
        assertTrue(parent.methods.stream().anyMatch(method -> method.name.equals("operation")));
        var child = reflector.reflect(OperationChild.class);
        assertEquals(2, child.methods.stream().filter(method -> method.name.equals("equals")).count());
        assertTrue(child.methods.stream().anyMatch(method -> method.name.equals("hashCode")));
        assertTrue(child.methods.stream().noneMatch(method -> method.name.equals("operation")),
                "ordinary concrete methods remain inherited from the emitted parent declaration");
    }

    @Test
    void overriddenGetterRetainsTheRealInheritedSetter() {
        BeanChild bean = new BeanChild();
        try (Context context = context()) {
            context.getBindings("js").putMember("bean", bean);
            context.eval("js", "bean.value = 'written';");
            assertEquals("written", context.eval("js", "bean.value").asString());
            context.eval("js", "bean.setValue('explicit');");
            assertEquals("explicit", bean.getValue());
        }
        var declaration = new TypeReflector().reflect(BeanChild.class);
        var getter = declaration.methods.stream().filter(method -> method.isGetter).findFirst().orElseThrow();
        assertNotNull(getter.setterParamType, "overriding a getter does not remove the inherited write operation");
        assertEquals(String.class, getter.setterParamType.sourceType);
        String output = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(declaration);
        assertTrue(output.contains("set value(value: string);"), output);
        assertTrue(output.contains("setValue(value: string): void;"), output);
    }

    @Test
    void alternateBeanGetterNamesRetainBothRealCallableMethods() {
        var declaration = new TypeReflector().reflect(ReadyValue.class);
        assertTrue(declaration.methods.stream().anyMatch(method -> method.name.equals("getReady")));
        assertTrue(declaration.methods.stream().anyMatch(method -> method.name.equals("isReady")));
        assertEquals(1, declaration.methods.stream().filter(method -> method.isGetter).count());
        try (Context context = context()) {
            context.getBindings("js").putMember("readyValue", new ReadyValue());
            assertTrue(context.eval("js", "readyValue.getReady()").asBoolean());
            assertTrue(!context.eval("js", "readyValue.isReady()").asBoolean());
        }
    }

    @Test
    void ordinaryFieldAndInheritedMethodRuntimeControl() throws Exception {
        try (Context context = context()) {
            context.getBindings("js").putMember("saveField", new SaveField());
            String observations = context.eval("js", "JSON.stringify({read: saveField.noSave,"
                    + " readType: typeof saveField.noSave, call: saveField.noSave(),"
                    + " detached: (function() { try { var operation = saveField.noSave; return operation(); }"
                    + " catch(error) { return String(error); } })()})").asString();
            assertTrue(observations.contains("\"read\":true"), observations);
            assertTrue(observations.contains("\"call\":false"), observations);
            assertTrue(observations.contains("TypeError: operation is not a function"), observations);
            Path output = Path.of("build", "probe-field-method-control.json");
            Files.writeString(output, observations);
        }
    }

    @Test
    void hostSamInstancesDoNotExposeJavaScriptFunctionConstructorBehavior() {
        try (Context context = context()) {
            context.getBindings("js").putMember("hostSam", new BoundedHost());
            assertTrue(context.eval("js", "hostSam[Symbol.hasInstance] === undefined"
                    + " && !(hostSam instanceof Function)").asBoolean());
            context.getBindings("js").putMember("boundedReceiver", new BoundedReceiver());
            assertTrue(context.eval("js", "boundedReceiver.apply(hostSam).marker === 'root'"
                    + " && boundedReceiver.apply(function(input) { return input; }).marker === 'root'").asBoolean());
        }
    }

    @Test
    void inheritedRealNamesTakePrecedenceOverBeanAliases() {
        String output = new TypeScriptClassRenderer(new TypeAliasRegistry())
                .render(new TypeReflector().reflect(MethodChild.class));
        assertTrue(output.contains("level(): string;"), output);
        assertTrue(!output.contains("get level()"), output);
        try (Context context = context()) {
            context.getBindings("js").putMember("methodChild", new MethodChild());
            assertTrue(context.eval("js", "methodChild.level() === 'inherited method'"
                    + " && methodChild.getLevel() === 'getter'").asBoolean());
        }
    }

    @Test
    void functionalAliasHostVariablesRetainClassBounds() {
        var aliases = new TypeAliasRegistry();
        var generator = new FunctionalInterfaceAliasGenerator(aliases);
        generator.prepare(Set.of(BoundedCallback.class.getName(), RootValue.class.getName()), Set.of());
        String output = generator.declaration(BoundedCallback.class.getName());
        assertTrue(output.contains("Host0 extends $TypeScriptInheritedInterfaceTest$RootValue = any"), output);
    }

    @Test
    void beanReadsAndFieldWritesRetainTheirDifferentRuntimeTypes() {
        var declaration = new TypeReflector().reflect(DifferentLevelField.class);
        String output = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(declaration);
        assertTrue(output.contains("get level(): string;"), output);
        assertTrue(output.contains("set level(value: number);"), output);
        DifferentLevelField target = new DifferentLevelField();
        try (Context context = context()) {
            context.getBindings("js").putMember("different", target);
            assertEquals("parent accessor", context.eval("js", "different.level").asString());
            context.eval("js", "different.level = 9");
        }
        assertEquals(9, target.level);
    }

    @Test
    void fieldBackedBeanReadRetainsExplicitGetterOverride() {
        var renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        renderer.overrideGetter(DifferentLevelField.class, "level", "CustomLevel", null);
        String output = renderer.render(new TypeReflector().reflect(DifferentLevelField.class));
        assertTrue(output.contains("get level(): CustomLevel;"), output);
        assertTrue(output.contains("set level(value: number);"), output);
        assertEquals(1, output.split("get level\\(\\)", -1).length - 1, output);
    }

    @Test
    void explicitSetterNamesOwnTheirBeanAliasNamespace() {
        String output = new TypeScriptClassRenderer(new TypeAliasRegistry())
                .render(new TypeReflector().reflect(SetterNameCollision.class));
        assertTrue(output.contains("setName(input: string): void;"), output);
        assertTrue(!output.contains("readonly setName:") && !output.contains("get setName()"), output);
    }

    @Test
    void nestedGetterNamesRetainEverySelectedInheritedWriteOperation() {
        var target = new NestedGetterChild();
        try (Context context = context()) {
            context.getBindings("js").putMember("target", target);
            context.eval("js", "target.value = 'written'");
            assertEquals("written", context.eval("js", "target.value").asString());
            context.eval("js", "target.setValue('explicit')");
            assertEquals("explicit", context.eval("js", "target.getValue()").asString());
        }
        var declaration = new TypeReflector().reflect(NestedGetterChild.class);
        var getter = declaration.methods.stream()
                .filter(method -> method.isGetter && "value".equals(method.property)).findFirst().orElseThrow();
        assertNotNull(getter.setterParamType, "Selected nested getters retain their real inherited setters");
        assertEquals(String.class, getter.setterParamType.sourceType);
        String output = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(declaration);
        assertTrue(output.contains("set value(value: string);"), output);
        assertTrue(output.contains("setValue(value: string): void;"), output);
        assertTrue(!output.contains("get getValue()") && !output.contains("get getGetValue()"), output);
    }

    @Test
    void beanAliasesPreserveRealMethodsAndFieldsWhenNamesCollide() throws Exception {
        var reflector = new TypeReflector();
        var renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        String methods = renderer.render(reflector.reflect(LevelMethods.class));
        assertTrue(methods.contains("level(): string;"), methods);
        assertTrue(methods.contains("getLevel(): string;"), methods);
        assertTrue(methods.contains("setLevel(input: string): void;"), methods);
        assertTrue(!methods.contains("get level()"), methods);
        String parent = renderer.render(reflector.reflect(LevelParent.class));
        assertTrue(parent.contains("get level(): string;"), parent);
        String field = renderer.render(reflector.reflect(LevelField.class));
        assertTrue(field.contains("get level(): string;"), field);
        Path declaration = Path.of("build", "probe-ts-bean-inheritance", "index.d.ts");
        Files.createDirectories(declaration.getParent());
        Files.writeString(declaration, renderer.render(reflector.reflect(LevelSource.class)) + methods + parent + field
                + renderer.render(reflector.reflect(DifferentLevelField.class))
                + renderer.render(reflector.reflect(SetterNameCollision.class)));
        try (Context context = context()) {
            context.getBindings("js").putMember("methods", new LevelMethods());
            context.getBindings("js").putMember("field", new LevelField());
            // Graal's Bean getter wins the direct read even when a real subclass field exists.
            assertEquals("method|accessor|parent accessor|parent accessor", context.eval("js",
                    "[methods.level(), methods.getLevel(), field.level, field.getLevel()].join('|')").asString());
        }
    }

    @Test
    void inheritedInterfaceMembersRemainCallableAndProduceRealDeclarations() throws Exception {
        var reflector = new TypeReflector();
        var aliases = new TypeAliasRegistry();
        var functional = new FunctionalInterfaceAliasGenerator(aliases);
        functional.prepare(Set.of(BoundedCallback.class.getName(), RootValue.class.getName(),
                SelfComparable.class.getName(), Comparable.class.getName()), Set.of());
        var renderer = new TypeScriptClassRenderer(aliases);
        StringBuilder output = new StringBuilder();
        for (Class<?> type : List.of(Values.class, ChildValues.class, Sized.class, Value.class, TextValue.class,
                RemappedParent.class, RemappedChild.class, ReadOnlyValue.class, StaticSetter.class,
                RootValue.class, Constrained.class, GenericSource.class, GenericValue.class,
                BoundedCallback.class, BoundedReceiver.class, BoundedHost.class,
                ObjectContract.class, ObjectValue.class, ObjectChild.class, ReadyContract.class, ReadyValue.class,
                Comparable.class, SelfComparable.class, ComparableReceiver.class, OtherComparable.class,
                NumericBounds.class, ParentOperation.class, OperationParent.class, OperationChild.class,
                BeanValue.class, BeanParent.class, BeanChild.class, NestedGetterParent.class, NestedGetterChild.class)) {
            output.append(renderer.render(reflector.reflect(type)));
        }
        output.append(functional.declaration(BoundedCallback.class.getName()));
        output.append(functional.declaration(SelfComparable.class.getName()));
        Path declaration = Path.of("build", "probe-ts-inherited-interface", "index.d.ts");
        Files.createDirectories(declaration.getParent());
        Files.writeString(declaration, output);
        try (Context context = context()) {
            assertTrue(context.eval("js", "var Text = Java.type('" + TextValue.class.getName()
                    + "'); var value = new Text('x'); value.value() === 'x'"
                    + " && value.echo('y') === 'y' && value.echo() === 7"
                    + " && value.identity(3) === 3 && value.size() === 1").asBoolean());
        }
        String value = renderer.render(reflector.reflect(Value.class));
        assertTrue(value.contains("echo(input: T): T;"), value);
        String text = renderer.render(reflector.reflect(TextValue.class));
        assertTrue(text.contains("echo(input: string): string;"), text);
    }

    @Test
    void superclassOverloadsUseTheirExposedNames() throws Exception {
        var remapper = new NekoJSMemberRemapper();
        assertEquals("call", remapper.remapMethod(RemappedParent.class.getMethod("base", String.class), RemappedChild.class));
        assertEquals("call", remapper.remapMethod(RemappedChild.class.getMethod("local", int.class), RemappedChild.class));
        try (Context context = context()) {
            context.getBindings("js").putMember("remapped", new RemappedChild());
            assertTrue(context.eval("js", "remapped.base('x') === 'x' && remapped.local(7) === 7").asBoolean());
        }
        var reflector = new TypeReflector();
        var declaration = reflector.reflect(RemappedChild.class);
        var calls = declaration.methods.stream().filter(method -> "call".equals(method.effectiveName())).toList();
        assertEquals(2, calls.size(), "Both actual remapped overloads must remain available");
        assertTrue(calls.stream().anyMatch(method -> method.params.get(0).type.sourceType == String.class));
        assertTrue(calls.stream().anyMatch(method -> method.params.get(0).type.sourceType == int.class));
    }

    @Test
    void declaredSetterRetainsItsBeanWriteType() {
        var declaration = new TypeReflector().reflect(OwnSetter.class);
        var getter = declaration.methods.stream().filter(method -> method.isGetter && "value".equals(method.property))
                .findFirst().orElseThrow();
        assertEquals(String.class, getter.setterParamType.sourceType);
        assertEquals("string", getter.setterParamType.ref.name());
        String output = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(declaration);
        assertTrue(output.contains("set value(value: string);"), output);
        OwnSetter target = new OwnSetter();
        try (Context context = context()) {
            context.getBindings("js").putMember("target", target);
            context.eval("js", "target.setValue('written')");
        }
        assertEquals("written", target.getValue());
    }

    @Test
    void staticSetterDoesNotMakeInheritedInstanceGetterWritable() {
        var declaration = new TypeReflector().reflect(StaticSetter.class);
        var getter = declaration.methods.stream().filter(method -> method.isGetter && "value".equals(method.property))
                .findFirst().orElseThrow();
        assertNull(getter.setterParamType);
        String output = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(declaration);
        assertTrue(output.contains("get value(): string;"), output);
        assertTrue(output.contains("static setValue(input: string): void;"), output);
        assertTrue(!output.contains("set value("), output);
    }

    @Test
    void inheritedMethodVariablesRemainDistinctFromClassVariables() {
        String output = new TypeScriptClassRenderer(new TypeAliasRegistry())
                .render(new TypeReflector().reflect(GenericValue.class));
        assertTrue(output.contains("transform<T1>(input: T, result: T1): T1;"), output);
    }

    @Test
    void inheritedMethodBoundsRetainTheirActualConstraint() {
        String output = new TypeScriptClassRenderer(new TypeAliasRegistry())
                .render(new TypeReflector().reflect(GenericValue.class));
        assertTrue(output.contains("bounded<T1 extends $TypeScriptInheritedInterfaceTest$RootValue>"), output);
    }

    @Test
    void contextualMethodVariablesRemainUsableInFunctionalAliases() {
        var aliases = new TypeAliasRegistry();
        new FunctionalInterfaceAliasGenerator(aliases).prepare(Set.of(Function.class.getName()), Set.of());
        String output = new TypeScriptClassRenderer(aliases).render(new TypeReflector().reflect(FunctionalValue.class));
        assertTrue(output.contains("$Function_<T, T1, T, T1>"), output);
        try (Context context = context()) {
            context.getBindings("js").putMember("functional", new FunctionalValue<String>());
            assertTrue(context.eval("js", "functional.apply('input', function(input) { return input.length; }) === 5")
                    .asBoolean());
        }
    }

    @Test
    void ordinaryContainersRetainHostArgumentsInInputsAndCallbackResults() {
        var aliases = new TypeAliasRegistry();
        new FunctionalInterfaceAliasGenerator(aliases).prepare(Set.of(Function.class.getName(), Box.class.getName(),
                Map.class.getName()), Set.of());
        String output = new TypeScriptClassRenderer(aliases).render(new TypeReflector().reflect(MapContainer.class));
        assertTrue(output.contains("pass(input: $TypeScriptInheritedInterfaceTest$Box<$Map<K, V>>)"), output);
        assertTrue(output.contains("$Function_<string, $TypeScriptInheritedInterfaceTest$Box<$Map<K, V>>, string, "
                + "$TypeScriptInheritedInterfaceTest$Box<$Map<K, V>>>"), output);
        try (Context context = context()) {
            context.getBindings("js").putMember("nested", new MapContainer<String, Integer>());
            context.getBindings("js").putMember("box", new Box<>(Map.of("key", 7)));
            assertTrue(context.eval("js", "nested.apply(function(input) { return box; }).value.get('key') === 7")
                    .asBoolean());
        }
    }

    @Test
    void genericMapInputsRetainHostMapsWithoutIllegalIndexKeys() {
        var aliases = new TypeAliasRegistry();
        String output = aliases.getCollectionAlias(Map.class, new String[]{"K", "V"});
        assertTrue(output.contains("$Map<K, V>"), output);
        assertTrue(!output.contains("[key: K]"), output);
    }

    private static Context context() {
        return Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false)
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .build();
    }
}
