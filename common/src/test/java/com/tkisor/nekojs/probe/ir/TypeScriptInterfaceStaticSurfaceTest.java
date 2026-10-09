package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.annotation.Overload;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptInterfaceStaticSurfaceTest {
    public interface ValueApi<T> {
        int LEVEL = 3;

        static <R> ValueApi<R> of(R value) { return () -> value; }

        @Overload(value = {"value: 3"}, returns = "3")
        static int length(int value) { return value; }

        T value();

        default String label() { return "value"; }
    }

    public interface ChildApi extends ValueApi<String> {}

    @Test
    void editedStaticMembersRetainQuotedNamesAndHiddenMembersLeaveNoValueSurface() {
        var declaration = new TypeReflector().reflect(ValueApi.class);
        declaration.renameTo = "EditedApi";
        declaration.methods.stream().filter(m -> m.isStatic).forEach(m -> m.hidden = true);
        declaration.fields.forEach(f -> f.hidden = true);
        var renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        assertFalse(renderer.render(declaration).contains("export const"));

        MethodDecl method = new MethodDecl("actual-factory");
        method.isStatic = true;
        method.returnType = TypeSlot.of(void.class, ApiTypeRef.voidType());
        method.overloads.add(new MethodDecl.Overload(List.of("arg: number"), "void", List.of()));
        declaration.methods.add(method);
        FieldDecl field = new FieldDecl("constant-name", TypeSlot.of(int.class, ApiTypeRef.primitive("int")));
        field.isStatic = true;
        field.isFinal = true;
        declaration.fields.add(field);
        String output = renderer.render(declaration);
        assertTrue(output.contains("export const $EditedApi: {"), output);
        assertTrue(output.contains("\"actual-factory\"(): void;"), output);
        assertTrue(output.contains("\"actual-factory\"(arg: number): void;"), output);
        assertTrue(output.contains("readonly \"constant-name\": number;"), output);
        assertFalse(output.contains("static "), output);
    }

    @Test
    void reflectedStaticMembersBelongToTheValueSurface() throws Exception {
        var renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        String output = renderer.render(new TypeReflector().reflect(ValueApi.class));
        output += renderer.render(new TypeReflector().reflect(ChildApi.class));
        Path actual = Path.of("build", "probe-ts-interface-static", "index.d.ts");
        Files.createDirectories(actual.getParent());
        Files.writeString(actual, output);
        int valueStart = output.indexOf("export const");
        assertTrue(valueStart > 0, output);
        String instance = output.substring(output.indexOf("export interface"), valueStart);
        assertTrue(instance.contains("value(): T;"), output);
        assertTrue(instance.contains("label(): string;"), output);
        assertFalse(instance.contains("LEVEL"), output);
        assertFalse(instance.contains("of<"), output);
        assertTrue(output.contains("export const $TypeScriptInterfaceStaticSurfaceTest$ValueApi: {"), output);
        assertTrue(output.contains("of<R>(value: R): $TypeScriptInterfaceStaticSurfaceTest$ValueApi<R>;"), output);
        assertTrue(output.contains("length(value: number): number;"), output);
        assertTrue(output.contains("length(value: 3): 3;"), output);
        assertFalse(output.contains("static "), output);
    }

    @Test
    void staticFactoriesAndConstantsRemainAccessibleUnderProductionHostAccess() {
        try (Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false)
                .option("js.nashorn-compat", "true")
                .build()) {
            assertTrue(context.eval("js", "var Api = Java.type('" + ValueApi.class.getName()
                    + "'); var value = Api.of('x'); Api.LEVEL === 3 && value.value() === 'x'"
                    + " && value.label() === 'value' && Api.length(4) === 4").asBoolean());
        }
    }
}
