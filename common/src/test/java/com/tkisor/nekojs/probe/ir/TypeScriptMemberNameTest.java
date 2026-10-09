package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.annotation.Overload;
import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeScriptMemberNameTest {
    public static final class Fixture {
        @Remap("handler$zek000$fabric-events-interaction-v0$fakePlayerGameMode")
        @Overload(value = {"mode: string"}, returns = "string")
        public String handler(int mode) { return Integer.toString(mode); }

        @Remap("static-factory")
        public static int factory() { return 1; }

        @Remap("getOdd-name")
        public int getOdd() { return 2; }

        @Remap("quote\"slash\\line\n")
        public void escaped() {}

        public void ordinary() {}
    }

    @Test
    void reflectedMethodsKeepExactNamesAsQuotedMembers() throws Exception {
        var declaration = new TypeReflector().reflect(Fixture.class);
        String output = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(declaration);
        Path actual = Path.of("build", "probe-ts-member-names", "index.d.ts");
        Files.createDirectories(actual.getParent());
        Files.writeString(actual, output);
        assertTrue(output.contains("\"handler$zek000$fabric-events-interaction-v0$fakePlayerGameMode\"(mode: number): string;"), output);
        assertTrue(output.contains("\"handler$zek000$fabric-events-interaction-v0$fakePlayerGameMode\"(mode: string): string;"), output);
        assertTrue(output.contains("static \"static-factory\"(): number;"), output);
        assertTrue(output.contains("\"getOdd-name\"(): number;"), output);
        assertTrue(output.contains("\"quote\\\"slash\\\\line\\n\"(): void;"), output);
        assertTrue(output.contains("ordinary(): void;"), output);
        assertFalse(output.contains("get odd-name"), output);
    }

    @Test
    void editedFieldsAndInterfaceMethodsUseTheSameMemberNameRule() {
        TypeDecl declaration = new TypeDecl(TypeDecl.Kind.CLASS, null, "example.Edited");
        declaration.renameTo = "Edited";
        FieldDecl field = new FieldDecl("original", TypeSlot.of(int.class, ApiTypeRef.primitive("int")));
        field.renameTo = "field-name";
        declaration.fields.add(field);
        var renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        assertTrue(renderer.render(declaration).contains("\"field-name\": number;"));

        declaration.kind = TypeDecl.Kind.ENUM;
        field.isEnumConstant = true;
        assertTrue(renderer.render(declaration).contains("static \"field-name\": $Edited;"));

        declaration.kind = TypeDecl.Kind.INTERFACE;
        MethodDecl method = new MethodDecl("method-name");
        method.returnType = TypeSlot.of(void.class, ApiTypeRef.voidType());
        declaration.methods.add(method);
        assertTrue(renderer.render(declaration).contains("\"method-name\"(): void;"));
    }
}
