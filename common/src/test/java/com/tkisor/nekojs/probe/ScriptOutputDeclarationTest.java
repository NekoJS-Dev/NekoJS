package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.annotation.ScriptOutput;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.python.ApiTypeRefPyRenderer;
import com.tkisor.nekojs.probe.backend.python.PythonClassRenderer;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ScriptOutputDeclarationTest {
    public static class Shape<T> {
        @ScriptOutput("{ readonly width: number; readonly height: number }")
        public Map<String, Object> measure() { return Map.of("width", 1, "height", 2); }

        @ScriptOutput("{ readonly width: number }")
        public T getViewport() { return null; }

        public Map<String, Object> ordinary() { return Map.of(); }
    }

    public interface Bounds<T> {
        @ScriptOutput("{ readonly width: number }")
        default T getBounds() { return null; }
    }

    public static class InheritedShape extends Shape<Map<String, Object>> implements Bounds<Map<String, Object>> {}

    public static class InvalidShape {
        @ScriptOutput(" ")
        public Object value() { return null; }
    }

    @Test
    void outputMetadataSurvivesInheritanceAndBeanProjectionWithoutWideningOrdinaryMaps() {
        var declaration = new TypeReflector().reflect(InheritedShape.class);
        var inherited = declaration.methods.stream().filter(method -> method.name.equals("getBounds")).findFirst().orElseThrow();
        assertEquals("{ readonly width: number }", inherited.returnType.outputTypeScript);
        assertEquals(Map.class, ((java.lang.reflect.ParameterizedType) inherited.returnType.sourceType).getRawType());
        String text = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(new TypeReflector().reflect(Shape.class));
        assertTrue(text.contains("measure(): { readonly width: number; readonly height: number };"), text);
        assertTrue(text.contains("get viewport(): { readonly width: number };"), text);
        assertTrue(text.contains("ordinary(): $Map<string, unknown>;"), text);
    }

    @Test
    void explicitEditsOverrideMetadataAndPythonRetainsItsJavaContract() {
        var declaration = new TypeReflector().reflect(Shape.class);
        var method = declaration.methods.stream().filter(candidate -> candidate.name.equals("measure")).findFirst().orElseThrow();
        String python = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of(Map.class.getName()))).render(declaration);
        assertTrue(python.contains("def measure(self) -> Map:"), python);
        method.returnType.ref = ApiTypeRef.primitive("string");
        method.returnType.overridden = true;
        String text = new TypeScriptClassRenderer(new TypeAliasRegistry()).render(declaration);
        assertTrue(text.contains("measure(): string;"), text);
    }

    @Test
    void blankOutputMetadataRejectsGeneration() {
        var failure = assertThrows(IllegalArgumentException.class, () -> new TypeReflector().reflect(InvalidShape.class));
        assertTrue(failure.getMessage().contains("NEKO-4064"));
    }

    @Test
    void generatedConsumersCheckShapesAndKeepOrdinaryMapsStrict() throws Exception {
        com.tkisor.nekojs.testfixture.TestPlatformInit.ensureInitialized();
        Path output = Path.of("build/probe-ts-script-output");
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.probe"), List.of(), List.of(), List.of(), 1, "SMART"));
        List<Class<?>> types = List.of(Shape.class, Bounds.class, InheritedShape.class);
        var context = new ProbeContext.Of(snapshot, types, config, NekoJSPaths.fromGameDir(output), "typescript", output,
                types.stream().map(type -> new TypeReflector().reflect(type)).toList());
        for (var file : new TypeScriptProbeBackend().render(context).entrySet()) {
            Path target = output.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import { $ScriptOutputDeclarationTest$InheritedShape as Shape } from 'java:com/tkisor/nekojs/probe';
                const shape = new Shape();
                const width: number = shape.measure().width;
                const viewportWidth: number = shape.viewport.width;
                const inheritedWidth: number = shape.bounds.width;
                const raw: unknown = shape.ordinary().get('width');
                // @ts-expect-error Output fields remain numeric.
                const invalid: string = shape.measure().width;
                // @ts-expect-error Output fields remain read-only.
                shape.viewport.width = 3;
                // @ts-expect-error Unannotated Maps do not acquire dimension fields.
                shape.ordinary().width;
                """);
    }
}
