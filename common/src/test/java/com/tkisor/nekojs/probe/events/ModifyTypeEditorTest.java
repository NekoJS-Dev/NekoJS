package com.tkisor.nekojs.probe.events;

import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.ir.FieldDecl;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.ir.TypeSlot;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.probe.ProbeBackend;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for parameter-level edits through {@link ClassEditor} and {@link ProbeModifyTypeEventJS}: reflect, edit the IR,
 * render, and check that renaming, type changes, hiding, added parameters, optional parameters and import
 * collection all take effect.
 *
 * <p>The tests do not rely on the -parameters compiler flag: they address parameters by index and read the parameter
 * names from the IR when needed.
 */
class ModifyTypeEditorTest {

    /** Sample class for reflection. Its methods and fields are public so reflection sees them. */
    public static class Sample {
        public String nameField;
        public String greet(String name) { return "hi " + name; }
        public int compute(int x, int y) { return x + y; }
    }

    private final TypeReflector reflector = new TypeReflector();
    private final TypeScriptClassRenderer renderer =
            new TypeScriptClassRenderer(new TypeAliasRegistry());

    // ---------------- resolveType ----------------

    @Test
    void resolveType_fqnStringBecomesSymbol() {
        ApiTypeRef ref = ProbeModifyTypeEventJS.resolveType("net.minecraft.world.item.ItemStack");
        assertEquals(ApiTypeRef.Kind.SYMBOL, ref.kind());
        assertEquals("java:net.minecraft.world.item.ItemStack", ref.name());
    }

    @Test
    void resolveType_plainNameBecomesPrimitive() {
        ApiTypeRef ref = ProbeModifyTypeEventJS.resolveType("string");
        assertEquals(ApiTypeRef.Kind.PRIMITIVE, ref.kind());
        assertEquals("string", ref.name());
    }

    @Test
    void resolveType_passesThroughApiTypeRef() {
        ApiTypeRef original = ApiTypeRef.primitive("number");
        assertSame(original, ProbeModifyTypeEventJS.resolveType(original));
    }

    @Test
    void resolveType_rejectsBlankAndUnknown() {
        assertThrows(IllegalArgumentException.class, () -> ProbeModifyTypeEventJS.resolveType(""));
        assertThrows(IllegalArgumentException.class, () -> ProbeModifyTypeEventJS.resolveType(null));
        assertThrows(IllegalArgumentException.class, () -> ProbeModifyTypeEventJS.resolveType(42));
    }

    // ---------------- override slot ----------------

    @Test
    void override_setsOverriddenFlagAndKeepsRef() {
        var slot = ProbeModifyTypeEventJS.override(null, "java.util.List");
        assertTrue(slot.overridden);
        assertNull(slot.sourceType);
        assertEquals(ApiTypeRef.Kind.SYMBOL, slot.ref.kind());
    }

    // ---------------- event: forClass / hasClass ----------------

    @Test
    void event_forClassUnknownReturnsNull() {
        TypeDecl d = reflector.reflect(Sample.class);
        ProbeModifyTypeEventJS event = new ProbeModifyTypeEventJS(Map.of(d.fqn, d));
        assertTrue(event.hasClass(d.fqn));
        assertFalse(event.hasClass("does.not.Exist"));
        assertNotNull(event.forClass(d.fqn));
        assertNull(event.forClass("does.not.Exist"));
    }

    // ---------------- rename / hide method ----------------

    @Test
    void renameMethod_reflectsInRender() {
        TypeDecl d = reflector.reflect(Sample.class);
        String before = renderer.render(d);
        assertTrue(before.contains("greet("));

        new ClassEditor(d).renameMethod("greet", "hello");

        assertTrue(d.mutated);
        String after = renderer.render(d);
        assertFalse(after.contains("greet("), "old method name should be gone");
        assertTrue(after.contains("hello("), "renamed method should appear");
    }

    @Test
    void hideMethod_removesFromRender() {
        TypeDecl d = reflector.reflect(Sample.class);
        assertTrue(renderer.render(d).contains("compute("));

        new ClassEditor(d).hideMethod("compute");

        assertTrue(d.mutated);
        assertFalse(renderer.render(d).contains("compute("));
    }

    @Test
    void missingMember_isSilentNoOp() {
        TypeDecl d = reflector.reflect(Sample.class);
        // A member that does not exist is a no-op: nothing is thrown and mutated stays false
        new ClassEditor(d).renameMethod("nope", "x").hideField("absent");
        assertFalse(d.mutated);
    }

    // ---------------- change return type / param type ----------------

    @Test
    void changeReturnType_reflectsInRender() {
        TypeDecl d = reflector.reflect(Sample.class);
        new ClassEditor(d).changeReturnType("compute", "java.lang.String");
        String rendered = renderer.render(d);
        // compute's return type changes from number to $String
        assertTrue(rendered.contains("compute(") && rendered.contains("$String"));
    }

    @Test
    void changeParamType_byIndex_reflectsInRenderAndImports() {
        TypeDecl d = reflector.reflect(Sample.class);
        new ClassEditor(d).changeParamType("compute", 0, "java.lang.String");

        String rendered = renderer.render(d);
        // Edited parameters behave like reflected ones: an input position applies alias widening
        // (java.lang.String becomes string)
        assertTrue(rendered.contains("compute(x: string"), "edited param should widen like reflected params: " + rendered);

        // Import collection: compute's first parameter becomes java.lang.String from another package, so it is collected
        Set<String> imps = ProbeModifyTypeEventJS.collectEditedSymbolFqns(d, pkgOf(d.fqn));
        assertTrue(imps.contains("java.lang.String"));
    }

    @Test
    void changeParamType_excludesSamePackageSymbol() {
        TypeDecl d = reflector.reflect(Sample.class);
        // Use a made-up type in Sample's package. This only exercises the same-package exclusion, so no real class is needed
        d.methods.stream().filter(m -> m.name.equals("compute")).findFirst()
                .ifPresent(m -> m.params.get(0).type = ProbeModifyTypeEventJS.override(m.params.get(0).type, pkgOf(d.fqn) + ".Sibling"));
        Set<String> imps = ProbeModifyTypeEventJS.collectEditedSymbolFqns(d, pkgOf(d.fqn));
        assertFalse(imps.contains(pkgOf(d.fqn) + ".Sibling"), "same-package symbol must not produce a self-import");
    }

    // ---------------- mark optional (dynamic param name) ----------------

    @Test
    void markOptional_rendersQuestionMark() {
        TypeDecl d = reflector.reflect(Sample.class);
        MethodDecl compute = d.methods.stream().filter(m -> m.name.equals("compute")).findFirst().orElseThrow();
        String last = compute.params.get(compute.params.size() - 1).name;

        // Optional parameters must come last, so mark the last one
        new ClassEditor(d).markOptional("compute", last);

        assertTrue(renderer.render(d).contains(last + "?:"),
                "optional param should render with trailing '?'");
    }

    @Test
    void markOptional_rejectsOptionalBeforeRequired() {
        TypeDecl d = reflector.reflect(Sample.class);
        MethodDecl compute = d.methods.stream().filter(m -> m.name.equals("compute")).findFirst().orElseThrow();
        String first = compute.params.get(0).name;

        // compute(x, y): making the first parameter optional leaves the second required, which is not a valid TS signature
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new ClassEditor(d).markOptional("compute", first));
        assertTrue(ex.getMessage().contains("optional"),
                "message should explain the optional-param rule: " + ex.getMessage());
        assertTrue(ex.getMessage().contains(d.fqn), "message should name the class: " + ex.getMessage());
    }

    // ---------------- add param ----------------

    @Test
    void addParam_appendsToAllOverloads() {
        TypeDecl d = reflector.reflect(Sample.class);
        new ClassEditor(d).addParam("compute", "flag", "boolean");

        String rendered = renderer.render(d);
        assertTrue(rendered.contains("flag: boolean"), "added param should render");
    }

    @Test
    void addParam_rejectsRequiredAfterOptional() {
        TypeDecl d = reflector.reflect(Sample.class);
        MethodDecl compute = d.methods.stream().filter(m -> m.name.equals("compute")).findFirst().orElseThrow();
        String last = compute.params.get(compute.params.size() - 1).name;
        new ClassEditor(d).markOptional("compute", last);

        // Adding a required parameter after an optional one is not a valid TS signature
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new ClassEditor(d).addParam("compute", "flag", "boolean"));
        assertTrue(ex.getMessage().contains("addParam"),
                "message should name the rejected operation: " + ex.getMessage());
    }

    // ---------------- field edits ----------------

    @Test
    void changeFieldType_reflectsInRender() {
        TypeDecl d = reflector.reflect(Sample.class);
        new ClassEditor(d).changeFieldType("nameField", "number");
        String rendered = renderer.render(d);
        assertTrue(rendered.contains("nameField: number"));
    }

    @Test
    void hideField_removesFromRender() {
        TypeDecl d = reflector.reflect(Sample.class);
        assertTrue(renderer.render(d).contains("nameField"));
        new ClassEditor(d).hideField("nameField");
        assertFalse(renderer.render(d).contains("nameField"));
    }

    // ---------------- hide whole class ----------------

    @Test
    void hideClass_rendersEmpty() {
        TypeDecl d = reflector.reflect(Sample.class);
        new ClassEditor(d).hide();
        assertEquals("", renderer.render(d));
    }

    // ---------------- rename class ----------------

    @Test
    void renameClass_reflectsInTsRender() {
        TypeDecl d = reflector.reflect(Sample.class);
        // Sample is a nested class, so its original name is Outer$Simple
        String before = renderer.render(d);
        assertTrue(before.contains("export class $ModifyTypeEditorTest$Sample"), before);

        new ClassEditor(d).renameClass("Renamed");

        assertTrue(d.mutated);
        String after = renderer.render(d);
        assertTrue(after.contains("export class $Renamed"), after);
        assertFalse(after.contains("$ModifyTypeEditorTest$Sample"), "old class name should be gone: " + after);
    }

    @Test
    void renameClass_enumSelfRefsFollow() {
        TypeDecl d = new TypeDecl(TypeDecl.Kind.ENUM, null, "pkg.Color");
        FieldDecl red = new FieldDecl("RED", TypeSlot.of(null, ApiTypeRef.symbol(new ApiSymbolId("java", "pkg.Color"))));
        red.isStatic = true;
        red.isEnumConstant = true;
        d.fields.add(red);

        new ClassEditor(d).renameClass("Color2");

        String out = renderer.render(d);
        assertTrue(out.contains("export class $Color2"), out);
        assertTrue(out.contains("static RED: $Color2;"), "enum constant self-ref must follow rename: " + out);
        assertTrue(out.contains("static values(): $Color2[];"), out);
        assertTrue(out.contains("static valueOf(name: string): $Color2;"), out);
        assertFalse(out.contains("$Color;"), "old enum name should be gone: " + out);
    }

    // ---------------- change super ----------------

    @Test
    void changeSuper_toTsPrimitiveOmitsExtendsClause() {
        TypeDecl d = reflector.reflect(Sample.class);
        new ClassEditor(d).changeSuper("number");

        String out = renderer.render(d);
        // Rewriting superType to a TS primitive drops the extends clause, because class $X extends number is not valid
        assertTrue(out.contains("export class $ModifyTypeEditorTest$Sample {"),
                "extends clause must be omitted for primitive super type:\n" + out);
        assertFalse(out.contains("extends number"), out);
    }

    // ---------------- addMethod / addStaticMethod ----------------

    @Test
    void addMethod_emitsInstanceMethodWithParams() {
        TypeDecl d = reflector.reflect(Sample.class);
        // "a:string" is a named parameter and "int" is a bare type whose name becomes arg1 automatically
        new ClassEditor(d).addMethod("foo", "boolean", "a:string", "int");

        String out = renderer.render(d);
        assertTrue(out.contains("foo(a: string, arg1: number): boolean;"), out);
    }

    @Test
    void addStaticMethod_emitsStaticMethod() {
        TypeDecl d = reflector.reflect(Sample.class);
        new ClassEditor(d).addStaticMethod("create", "java.lang.String");

        String out = renderer.render(d);
        assertTrue(out.contains("static create(): $String;"), out);
    }

    @Test
    void addMethod_nullSpecsMeansNoParams() {
        TypeDecl d = reflector.reflect(Sample.class);
        new ClassEditor(d).addMethod("ping", "boolean", (Object[]) null);
        assertTrue(renderer.render(d).contains("ping(): boolean;"));
    }

    @Test
    void addMethod_rejectsBlankParamNameOrType() {
        TypeDecl d = reflector.reflect(Sample.class);
        assertThrows(IllegalArgumentException.class, () -> new ClassEditor(d).addMethod("foo", "boolean", ":int"));
        assertThrows(IllegalArgumentException.class, () -> new ClassEditor(d).addMethod("foo", "boolean", "a:"));
        assertThrows(IllegalArgumentException.class, () -> new ClassEditor(d).addMethod("foo", "boolean", "a:  "));
        assertThrows(IllegalArgumentException.class, () -> new ClassEditor(d).addMethod("foo", ""));
        assertThrows(IllegalArgumentException.class, () -> new ClassEditor(d).addMethod("foo", "boolean", (Object) 42));
    }

    // ---------------- Cleanup of hidden-class leftovers (TS backend integration) ----------------

    @Test
    void hiddenClass_removedFromTsImports(@TempDir Path temp) throws Exception {
        TestPlatformInit.ensureInitialized();

        // IR: Path is hidden with hide() (mutated + hidden). Reflected import collection for java.io.File
        // references java.nio.file.Path through toPath(), so that import must be filtered out or it dangles.
        TypeDecl pathDecl = new TypeDecl(TypeDecl.Kind.INTERFACE, null, "java.nio.file.Path");
        new ClassEditor(pathDecl).hide();

        NekoScriptCatalogSnapshot snapshot = emptySnapshot();
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("java"), List.of(), List.of(), List.of(), 5, "SMART"));
        Path out = temp.resolve("probe-ts");
        ProbeContext ctx = new ProbeContext.Of(snapshot,
                List.of(java.io.File.class, java.nio.file.Path.class), cfg,
                NekoJSPaths.fromGameDir(temp), "typescript", out, List.of(pathDecl));

        ProbeBackend.GenerateResult res = new TypeScriptProbeBackend().generate(ctx);
        assertTrue(res.success(), "generate failed: " + res.message());

        // java/io module: Path is dropped from File's reflected imports; the other cross-package imports (URI/URL) stay
        String ioModule = Files.readString(out.resolve("@package/java/io/index.d.ts"));
        assertFalse(ioModule.contains("import { $Path }"),
                "hidden class must not be imported: " + ioModule);
        assertTrue(java.util.regex.Pattern.compile("import \\{[^}]*\\$URI(?:,| )[^}]*\\$URL(?:,| )[^}]*} from \\\"java:java/net\\\";")
                .matcher(ioModule).find(), ioModule);

        // Path's own module: its declaration was overridden to empty, so the class name must not appear
        String fileModule = Files.readString(out.resolve("@package/java/nio/file/index.d.ts"));
        assertFalse(java.util.regex.Pattern.compile("(?<![\\w$])\\$Path(?![\\w$])").matcher(fileModule).find(),
                "The exact hidden class must not be declared: " + fileModule);

        // Whole output tree: no import statement may mention the hidden class name
        try (var files = Files.walk(out)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                for (String line : Files.readAllLines(f)) {
                    if (line.strip().startsWith("import ")) {
                        assertFalse(java.util.regex.Pattern.compile("(?<![\\w$])\\$Path(?![\\w$])").matcher(line).find(),
                                "hidden class leaked into import in " + f + ": " + line);
                    }
                }
            }
        }
    }

    // ---------------- helpers ----------------

    private static NekoScriptCatalogSnapshot emptySnapshot() {
        return new NekoScriptCatalogSnapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }

    private static String pkgOf(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(0, dot) : "";
    }
}
