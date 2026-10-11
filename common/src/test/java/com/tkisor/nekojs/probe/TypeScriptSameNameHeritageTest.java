package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptSameNameHeritageTest {
    private static final Class<?> BASE = com.tkisor.nekojs.probe.testfixture.samebase.PersistentData.class;
    private static final Class<?> CHILD = com.tkisor.nekojs.probe.testfixture.samechild.PersistentData.class;

    @Test
    void actualReflectionRetainsDistinctBaseIdentityAndCompleteModules() throws Exception {
        var aliases = new TypeAliasRegistry();
        var generator = new IndexFileGenerator(new TypeScriptClassRenderer(aliases),
                new AdapterAliasGenerator(aliases));
        var reflector = new TypeReflector();
        var child = reflector.reflect(CHILD);
        assertNotEquals(BASE.getName(), CHILD.getName());
        assertEquals(BASE, CHILD.getSuperclass());
        assertEquals(CHILD.getGenericSuperclass(), child.superType.sourceType);
        assertTrue(generator.collectImportsFromIr(child, CHILD.getPackageName()).contains(BASE.getName()));
        generator.predeclareClass(BASE.getName(), reflector.reflect(BASE), Set.of());
        generator.predeclareClass(CHILD.getName(), child, Set.of());
        Path output = Path.of("build/probe-ts-same-name-heritage");
        Files.createDirectories(output);
        for (Class<?> type : List.of(BASE, CHILD)) {
            String declaration = generator.generate(type.getPackageName(), List.of(type.getSimpleName()),
                    List.of(), Set.of(BASE.getName(), CHILD.getName()));
            Files.writeString(output.resolve(type == BASE ? "base.d.ts" : "child.d.ts"), declaration);
            assertTrue(declaration.contains("export class $PersistentData"), declaration);
        }
    }

    @Test
    void actualHostInheritanceAndBaseParameterKeepTheirCapabilities() {
        try (Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false)
                .option("js.nashorn-compat", "true")
                .build()) {
            assertTrue(context.eval("js", "var Base = Java.type('" + BASE.getName()
                    + "'); var Child = Java.type('" + CHILD.getName()
                    + "'); var base = new Base('base'); var child = new Child('child');"
                    + " child.baseValue() === 'child' && child.acceptBase(base) === 'base'"
                    + " && child.acceptBase(child) === 'child' && child instanceof Base").asBoolean());
        }
    }
}
