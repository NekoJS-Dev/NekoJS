package com.tkisor.nekojs.api.spec.processor;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@link SpecCoverageProcessor} through the system java compiler
 * ({@link ToolProvider#getSystemJavaCompiler()}) on small in-memory sources.
 *
 * <p>The inner compilation declares its own source copy of the
 * {@code @PlatformAvailability} annotation (same FQCN) and keeps common-api
 * <b>off</b> the inner {@code -classpath}, so scope-enforcement tests only see
 * the specs under test — otherwise the 11 real specs from common-api would be
 * discovered on the classpath and enforced as uncovered. The processor itself
 * is loaded from a {@code -processorpath} that contains the processor classes
 * plus common-api (which provides the real annotation class the processor
 * links against).
 */
class SpecCoverageProcessorTest {

    /** Same FQCN as the real annotation; prepended to every inner compilation. */
    private static final String PLATFORM_AVAILABILITY = """
            package com.tkisor.nekojs.api.spec;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.RUNTIME)
            @Target({ElementType.TYPE, ElementType.METHOD})
            public @interface PlatformAvailability {
                Scope value() default Scope.ALL;

                enum Scope { ALL, NF_ONLY, NF26_ONLY, CR_ONLY }
            }
            """;

    private static final String ALL_SPEC = """
            package demo;

            import com.tkisor.nekojs.api.spec.PlatformAvailability;

            @PlatformAvailability(PlatformAvailability.Scope.ALL)
            public interface AllSpec {
                default boolean neko$ping() { throw new UnsupportedOperationException(); }

                default boolean neko$pong(int count) { throw new UnsupportedOperationException(); }
            }
            """;

    private static final String NF_ONLY_SPEC = """
            package demo;

            import com.tkisor.nekojs.api.spec.PlatformAvailability;

            @PlatformAvailability(PlatformAvailability.Scope.NF_ONLY)
            public interface NfOnlySpec {
                default String neko$name() { throw new UnsupportedOperationException(); }
            }
            """;

    private static final String NF26_ONLY_SPEC = """
            package demo;

            import com.tkisor.nekojs.api.spec.PlatformAvailability;

            @PlatformAvailability(PlatformAvailability.Scope.NF26_ONLY)
            public interface Nf26OnlySpec {
                default String neko$name() { throw new UnsupportedOperationException(); }
            }
            """;

    /** Covers neko$ping but not neko$pong(int). */
    private static final String PARTIAL_IMPL = """
            package demo;

            public interface PartialImpl extends AllSpec {
                @Override
                default boolean neko$ping() { return true; }
            }
            """;

    private static final String FULL_IMPL = """
            package demo;

            public interface FullImpl extends AllSpec {
                @Override
                default boolean neko$ping() { return true; }

                @Override
                default boolean neko$pong(int count) { return count > 0; }
            }
            """;

    private static final String NF_ONLY_IMPL = """
            package demo;

            public interface NfOnlyImpl extends NfOnlySpec {
                @Override
                default String neko$name() { return "demo"; }
            }
            """;

    private static final String PLAIN = """
            package demo;

            public interface Plain {
                default boolean ping() { return true; }
            }
            """;

    // ==================== 方法覆盖校验（原有行为） ====================

    @Test
    void uncoveredNekoMethodFailsCompilation() throws Exception {
        CompileResult result = compile(List.of(),
                source("demo.AllSpec", ALL_SPEC),
                source("demo.PartialImpl", PARTIAL_IMPL));
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("未覆盖") && e.contains("neko$pong")),
                () -> "expected coverage error for neko$pong, got: " + result.errors());
    }

    @Test
    void fullCoverageCompiles() throws Exception {
        CompileResult result = compile(List.of(),
                source("demo.AllSpec", ALL_SPEC),
                source("demo.FullImpl", FULL_IMPL));
        assertTrue(result.success(), () -> String.join("; ", result.errors()));
        assertEquals(List.of(), result.errors());
    }

    // ==================== 平台范围强制（-Anekojs.platform） ====================

    @Test
    void requiredSpecCoveredWithOptionCompiles() throws Exception {
        CompileResult result = compile(List.of("-Anekojs.platform=nf26"),
                source("demo.NfOnlySpec", NF_ONLY_SPEC),
                source("demo.NfOnlyImpl", NF_ONLY_IMPL));
        assertTrue(result.success(), () -> String.join("; ", result.errors()));
    }

    @Test
    void nfOnlySpecNotRequiredOnCleanroom() throws Exception {
        CompileResult result = compile(List.of("-Anekojs.platform=cr"),
                source("demo.NfOnlySpec", NF_ONLY_SPEC));
        assertTrue(result.success(), () -> String.join("; ", result.errors()));
        assertEquals(List.of(), result.errors());
    }

    @Test
    void nfOnlySpecRequiredOnNeoForge() throws Exception {
        CompileResult result = compile(List.of("-Anekojs.platform=nf26"),
                source("demo.NfOnlySpec", NF_ONLY_SPEC));
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e ->
                        e.contains("NfOnlySpec") && e.contains("NF_ONLY") && e.contains("[nf26]")),
                () -> "expected scope enforcement error, got: " + result.errors());
    }

    @Test
    void nf26OnlySpecRequiredOnNf26() throws Exception {
        CompileResult result = compile(List.of("-Anekojs.platform=nf26"),
                source("demo.Nf26OnlySpec", NF26_ONLY_SPEC));
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e ->
                        e.contains("Nf26OnlySpec") && e.contains("NF26_ONLY") && e.contains("[nf26]")),
                () -> "expected scope enforcement error, got: " + result.errors());
    }

    @Test
    void nf26OnlySpecNotRequiredOnNf121() throws Exception {
        CompileResult result = compile(List.of("-Anekojs.platform=nf121"),
                source("demo.Nf26OnlySpec", NF26_ONLY_SPEC));
        assertTrue(result.success(), () -> String.join("; ", result.errors()));
    }

    @Test
    void legacyNfPlatformValueIsRejectedWithMigrationHint() throws Exception {
        CompileResult result = compile(List.of("-Anekojs.platform=nf"),
                source("demo.Plain", PLAIN));
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("已废弃")),
                () -> "expected legacy-value migration error, got: " + result.errors());
    }

    @Test
    void allScopeRequiredOnCleanroomToo() throws Exception {
        CompileResult result = compile(List.of("-Anekojs.platform=cr"),
                source("demo.AllSpec", ALL_SPEC));
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e ->
                        e.contains("AllSpec") && e.contains("ALL") && e.contains("[cr]")),
                () -> "expected scope enforcement error, got: " + result.errors());
    }

    @Test
    void withoutOptionScopeIsNotEnforced() throws Exception {
        CompileResult result = compile(List.of(),
                source("demo.NfOnlySpec", NF_ONLY_SPEC));
        assertTrue(result.success(), () -> String.join("; ", result.errors()));
    }

    @Test
    void invalidPlatformOptionValueFails() throws Exception {
        CompileResult result = compile(List.of("-Anekojs.platform=fabric"),
                source("demo.Plain", PLAIN));
        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("非法")),
                () -> "expected invalid-option error, got: " + result.errors());
    }

    // ==================== 无 spec 时的优雅降级 ====================

    @Test
    void noSpecsWithOptionWarnsAndSkips() throws Exception {
        CompileResult result = compile(List.of("-Anekojs.platform=nf26"),
                source("demo.Plain", PLAIN));
        assertTrue(result.success(), () -> String.join("; ", result.errors()));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("跳过范围校验")),
                () -> "expected skip warning, got: " + result.warnings());
    }

    @Test
    void noSpecsWithoutOptionIsSilentNoOp() throws Exception {
        CompileResult result = compile(List.of(), source("demo.Plain", PLAIN));
        assertTrue(result.success(), () -> String.join("; ", result.errors()));
        assertEquals(List.of(), result.errors());
        assertEquals(List.of(), result.warnings());
    }

    // ==================== 编译执行器 ====================

    private record CompileResult(boolean success, List<String> errors, List<String> warnings) {}

    /**
     * Compiles the given sources with the processor enabled ({@code -proc:only}).
     * Sources only depend on java.lang plus the source-defined annotation, so the
     * inner classpath is deliberately minimal (test classes only) to keep ambient
     * classpath entries (e.g. common-api's real specs) out of scope discovery.
     */
    private static CompileResult compile(List<String> extraOptions, JavaFileObject... units) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "system java compiler unavailable — run tests on a JDK, not a JRE");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<JavaFileObject> allUnits = new ArrayList<>();
        allUnits.add(source("com.tkisor.nekojs.api.spec.PlatformAvailability", PLATFORM_AVAILABILITY));
        allUnits.addAll(List.of(units));

        List<String> options = new ArrayList<>(List.of(
                "-proc:only",
                "-classpath", classpathEntryOf(SpecCoverageProcessorTest.class),
                "-processorpath", processorPath(),
                "-processor", SpecCoverageProcessor.class.getName()));
        options.addAll(extraOptions);

        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Boolean ok = compiler.getTask(null, fileManager, diagnostics, options, null, allUnits).call();
            assertNotNull(ok);
            return new CompileResult(ok, messages(diagnostics, Diagnostic.Kind.ERROR),
                    messages(diagnostics, Diagnostic.Kind.WARNING));
        }
    }

    private static JavaFileObject source(String fqn, String code) {
        return new SimpleJavaFileObject(
                URI.create("string:///" + fqn.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }
        };
    }

    /** Processor classes + common-api (the real annotation class the processor links against). */
    private static String processorPath() throws URISyntaxException {
        return classpathEntryOf(SpecCoverageProcessor.class)
                + File.pathSeparator
                + classpathEntryOf(com.tkisor.nekojs.api.spec.PlatformAvailability.class);
    }

    /** Resolves a runtime code-source location without relying on java.class.path (pathing jars). */
    private static String classpathEntryOf(Class<?> type) throws URISyntaxException {
        return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
    }

    private static List<String> messages(DiagnosticCollector<JavaFileObject> diagnostics, Diagnostic.Kind kind) {
        List<String> out = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() == kind) out.add(d.getMessage(Locale.ROOT));
        }
        return out;
    }
}
