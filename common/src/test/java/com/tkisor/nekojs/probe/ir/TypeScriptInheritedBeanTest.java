package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptInheritedBeanTest {
    public static class FieldParent { public boolean flag; }
    public static class ReadChild extends FieldParent { public boolean isFlag() { return !flag; } }
    public static class ReadLeaf extends ReadChild { public boolean isFlag() { return !flag; } }
    public static class WriteChild extends FieldParent {
        public boolean isFlag() { return !flag; }
        public void setFlag(boolean value) { flag = !value; }
    }
    public static class WriteLeaf extends WriteChild { public boolean isFlag() { return !flag; } }
    public static class AccessorParent {
        public boolean flag;
        public boolean isFlag() { return !flag; }
        public void setFlag(boolean value) { flag = !value; }
    }
    public static class AccessorChild extends AccessorParent { public boolean isFlag() { return !flag; } }
    public static class MixedChild extends FieldParent { public String getFlag() { return "flag"; } }
    public static class FieldBeanChild extends FieldParent {
        public boolean flag;
        public boolean isFlag() { return !flag; }
    }
    public static class FieldBeanLeaf extends FieldBeanChild { public boolean isFlag() { return !flag; } }
    public static class MixedWriterMiddle extends FieldParent {
        public boolean isFlag() { return !flag; }
        public void setFlag(String value) {}
    }
    public static class SameWriterLeaf extends MixedWriterMiddle {
        public boolean isFlag() { return !flag; }
        public void setFlag(boolean value) { flag = value; }
    }

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void inheritedFieldsAcceptBeanSyntaxWithoutChangingReadOrWriteSupport() throws Exception {
        List<Class<?>> types = List.of(FieldParent.class, ReadChild.class, ReadLeaf.class,
                WriteChild.class, WriteLeaf.class, AccessorParent.class, AccessorChild.class,
                FieldBeanChild.class, FieldBeanLeaf.class);
        var declarations = types.stream().map(type -> new TypeReflector().reflect(type)).toList();
        Path output = Path.of("build/probe-ts-inherited-bean/plain");
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Map.of(), List.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(getClass().getPackageName()), List.of(), List.of(), List.of(), 1, "SMART"));
        var context = new ProbeContext.Of(snapshot, types, config, NekoJSPaths.fromGameDir(output),
                "typescript", output, declarations);
        var files = new TypeScriptProbeBackend().render(context);
        for (var entry : files.entrySet()) {
            Path target = output.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue());
        }
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptInheritedBeanTest$FieldParent as Parent,
                  $TypeScriptInheritedBeanTest$ReadChild as ReadChild,
                  $TypeScriptInheritedBeanTest$ReadLeaf as ReadLeaf,
                  $TypeScriptInheritedBeanTest$WriteChild as WriteChild,
                  $TypeScriptInheritedBeanTest$WriteLeaf as WriteLeaf,
                  $TypeScriptInheritedBeanTest$AccessorChild as AccessorChild,
                  $TypeScriptInheritedBeanTest$FieldBeanChild as FieldBeanChild,
                  $TypeScriptInheritedBeanTest$FieldBeanLeaf as FieldBeanLeaf } from 'java:com/tkisor/nekojs/probe/ir';
                declare const read: ReadChild;
                declare const leaf: ReadLeaf;
                declare const write: WriteChild;
                declare const writeLeaf: WriteLeaf;
                declare const accessor: AccessorChild;
                declare const fieldBean: FieldBeanChild;
                declare const fieldLeaf: FieldBeanLeaf;
                const parent: Parent = read;
                const readValue: boolean = read.flag;
                const methodValue: boolean = read.isFlag();
                const leafValue: boolean = leaf.flag;
                write.flag = true;
                write.setFlag(false);
                writeLeaf.flag = false;
                writeLeaf.setFlag(true);
                accessor.flag = true;
                fieldBean.flag = true;
                const fieldRead: boolean = fieldBean.isFlag();
                const fieldLeafRead: boolean = fieldLeaf.flag;
                // @ts-expect-error An inherited field does not broaden a getter-only alias's declared write support.
                fieldLeaf.flag = true;
                // @ts-expect-error A getter-only Bean remains read-only on its concrete type.
                read.flag = true;
                // @ts-expect-error The projected read-only shape survives another subclass.
                leaf.flag = false;
                // @ts-expect-error A writer keeps its original boolean input.
                write.flag = 'flag';
                // @ts-expect-error Real setter methods retain their input type.
                writeLeaf.setFlag('flag');
                // @ts-expect-error A boolean property does not acquire a callable intersection.
                read.flag();
                """);
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"skipLibCheck":false,"noEmit":true,"target":"ES2022",
                "module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".",
                "paths":{"java:*": ["./@package/*"],"@special/*":["./@special/*"]}},
                "include":["exercise.ts","@package/**/*.d.ts","@special/**/*.d.ts"]}
                """);
        var renderer = renderer(declarations);
        assertTrue(renderer.render(declarations.get(1)).contains("readonly flag: boolean;"));
        assertTrue(renderer.render(declarations.get(3)).contains("        flag: boolean;"));
        assertTrue(renderer.render(declarations.get(6)).contains("get flag(): boolean;"));
        assertTrue(renderer.render(declarations.get(7)).contains("        flag: boolean;"));
    }

    @Test
    void hostReadsAndWritesStillUseTheRealBeanAccessors() {
        var target = new WriteLeaf();
        try (Context context = Context.newBuilder("js").allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowExperimentalOptions(true).option("js.nashorn-compat", "true")
                .allowHostClassLookup(name -> false).allowCreateThread(false).allowCreateProcess(false).build()) {
            context.getBindings("js").putMember("target", target);
            assertTrue(context.eval("js", "target.flag && target.isFlag()").asBoolean());
            context.eval("js", "target.flag = false");
            assertTrue(target.flag);
            assertFalse(context.eval("js", "target.flag").asBoolean());
            context.eval("js", "target.setFlag(true)");
            assertFalse(target.flag);
            assertTrue(context.eval("js", "target.flag").asBoolean());
        }
    }

    @Test
    void editedOrMissingAncestorsNeverReviveAFieldProjection() {
        var parent = new TypeReflector().reflect(FieldParent.class);
        var child = new TypeReflector().reflect(ReadChild.class);
        var renderer = renderer(List.of(parent, child));
        assertTrue(renderer.render(child).contains("readonly flag: boolean;"));
        parent.fields.getFirst().hidden = true;
        assertTrue(renderer.render(child).contains("get flag(): boolean;"));
        parent.fields.getFirst().hidden = false;
        parent.fields.getFirst().renameTo = "renamed";
        assertTrue(renderer.render(child).contains("get flag(): boolean;"));
        parent.fields.getFirst().renameTo = null;
        parent.hidden = true;
        assertTrue(renderer.render(child).contains("get flag(): boolean;"));
        assertTrue(renderer(List.of(child)).render(child).contains("get flag(): boolean;"));
        parent.hidden = false;
        renderer.overrideGetter(ReadChild.class, "flag", "string", null);
        assertTrue(renderer.render(child).contains("get flag(): string;"));
        var mixed = new TypeReflector().reflect(MixedChild.class);
        assertTrue(renderer(List.of(parent, mixed)).render(mixed).contains("get flag(): string;"));
        var middle = new TypeReflector().reflect(MixedWriterMiddle.class);
        var leaf = new TypeReflector().reflect(SameWriterLeaf.class);
        assertTrue(renderer(List.of(parent, middle, leaf)).render(leaf).contains("get flag(): boolean;"));
        var writer = new TypeReflector().reflect(WriteChild.class);
        var overridden = renderer(List.of(parent, writer));
        overridden.overrideGetter(WriteChild.class, "flag", "boolean", null);
        assertTrue(overridden.render(writer).contains("readonly flag: boolean;"));
        assertFalse(overridden.render(writer).contains("get flag()"));
        assertTrue(overridden.render(writer).contains("setFlag(value: boolean): void;"));
        var fieldBean = new TypeReflector().reflect(FieldBeanChild.class);
        var fieldOverride = renderer(List.of(parent, fieldBean));
        fieldOverride.overrideGetter(FieldBeanChild.class, "flag", "boolean", null);
        assertTrue(fieldOverride.render(fieldBean).contains("        flag: boolean;"));
        assertFalse(fieldOverride.render(fieldBean).contains("readonly flag"));
    }

    private static TypeScriptClassRenderer renderer(List<TypeDecl> declarations) {
        var renderer = new TypeScriptClassRenderer(new TypeAliasRegistry());
        renderer.setClassDeclarations(declarations.stream().collect(Collectors.toMap(type -> type.fqn, Function.identity())));
        return renderer;
    }
}
