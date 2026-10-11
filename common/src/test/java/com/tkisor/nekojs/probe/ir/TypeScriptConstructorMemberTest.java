package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.annotation.Overload;
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

class TypeScriptConstructorMemberTest {
    public static class StaticMember {
        @Overload(value = {"value: 3"}, returns = "string")
        public static String constructor(int value) { return "static-" + value; }
        public String other() { return "other"; }
    }

    public static class InstanceMember {
        public String constructor() { return "instance"; }
    }

    public static class BeanMember {
        private boolean value;
        public boolean isConstructor() { return value; }
        public void setConstructor(boolean value) { this.value = value; }
    }

    public static class FieldMember {
        public int constructor = 5;
    }

    public interface InterfaceMember {
        static String constructor(int value) { return "interface-" + value; }
    }

    public interface InterfaceField { int constructor = 7; }

    public enum EnumMember { constructor, OTHER }

    @Test
    void reflectedMembersKeepTheirNamesAndRealConstructorSyntax() throws Exception {
        var renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        StringBuilder all = new StringBuilder();
        for (Class<?> type : List.of(StaticMember.class, InstanceMember.class, BeanMember.class,
                FieldMember.class, InterfaceMember.class, InterfaceField.class, EnumMember.class)) {
            all.append(renderer.render(new TypeReflector().reflect(type)));
        }
        Path output = Path.of("build/probe-ts-constructor-members/index.d.ts");
        Files.createDirectories(output.getParent());
        Files.writeString(output, all);
        String text = all.toString();
        assertTrue(text.contains("static [\"constructor\"](value: number): string;"), text);
        assertTrue(text.contains("static [\"constructor\"](value: 3): string;"), text);
        assertTrue(text.contains("[\"constructor\"](): string;"), text);
        assertTrue(text.contains("get [\"constructor\"](): boolean;"), text);
        assertTrue(text.contains("set [\"constructor\"](value: boolean);"), text);
        assertTrue(text.contains("[\"constructor\"]: number;"), text);
        assertTrue(text.contains("readonly [\"constructor\"]: number;"), text);
        assertTrue(text.contains("static [\"constructor\"]: $TypeScriptConstructorMemberTest$EnumMember;"), text);
        assertTrue(text.contains("constructor();"), text);
        assertFalse(text.contains("static constructor("), text);
    }

    @Test
    void editedNamesHiddenMembersAndGetterOverridesUseTheSameNamingRule() {
        var declaration = new TypeReflector().reflect(StaticMember.class);
        declaration.methods.stream().filter(method -> method.isStatic).forEach(method -> method.hidden = true);
        declaration.methods.stream().filter(method -> method.name.equals("other"))
                .forEach(method -> method.renameTo = "constructor");
        String output = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(declaration);
        assertTrue(output.contains("[\"constructor\"](): string;"), output);
        assertFalse(output.contains("static "), output);
        assertTrue(output.contains("constructor();"), output);

        var renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        renderer.overrideGetter(BeanMember.class, "constructor", "number", null);
        output = renderer.render(new TypeReflector().reflect(BeanMember.class));
        assertTrue(output.contains("get [\"constructor\"](): number;"), output);
        assertFalse(output.contains("set [\"constructor\"]"), output);
    }

    @Test
    void actualHostMembersRemainAccessibleWithProductionPermissions() {
        try (Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false)
                .option("js.nashorn-compat", "true")
                .build()) {
            assertTrue(context.eval("js", "var Static = Java.type('" + StaticMember.class.getName()
                    + "'); var Instance = Java.type('" + InstanceMember.class.getName()
                    + "'); var Bean = Java.type('" + BeanMember.class.getName()
                    + "'); var Field = Java.type('" + FieldMember.class.getName()
                    + "'); var Interface = Java.type('" + InterfaceMember.class.getName()
                    + "'); var Constant = Java.type('" + InterfaceField.class.getName()
                    + "'); var Enum = Java.type('" + EnumMember.class.getName()
                    + "'); var bean = new Bean(); bean.constructor = true;"
                    + " Static.constructor(4) === 'static-4' && new Instance().constructor() === 'instance'"
                    + " && bean.constructor === true && bean.isConstructor() === true"
                    + " && new Field().constructor === 5 && Interface.constructor(2) === 'interface-2'"
                    + " && Constant.constructor === 7"
                    + " && Enum.constructor.name() === 'constructor'").asBoolean());
        }
    }
}
