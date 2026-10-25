package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeScriptSamProxyInputTest {
    public interface RemappedOperation {
        @Remap("renamed") String original(String value);
    }

    public sealed interface ClosedOperation permits ClosedImplementation {
        String apply(String value);
        default String description() { return "closed"; }
    }

    public static final class ClosedImplementation implements ClosedOperation {
        public String apply(String value) { return value; }
    }

    public static class Calls {
        public static String unary(UnaryOperator<String> operator) { return operator.apply("value"); }
        public static String unaryDefault(UnaryOperator<String> operator) {
            return operator.andThen(value -> value + "?").apply("value");
        }
        public static String binary(BinaryOperator<String> operator) { return operator.apply("left", "right"); }
        public static UnaryOperator<String> hostUnary() { return UnaryOperator.identity(); }
        public static String remapped(RemappedOperation operation) { return operation.original("value"); }
        public static String closed(ClosedOperation operation) { return operation.apply("value"); }
        public static ClosedOperation hostClosed() { return new ClosedImplementation(); }
    }

    @BeforeAll
    static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test
    void graalAcceptsSamObjectsWithoutImplementationsOfDefaultMethods() {
        try (Context context = context()) {
            String calls = "var Calls = Java.type('" + Calls.class.getName() + "'); ";
            assertEquals("value!", context.eval("js", calls
                    + "Calls.unary({apply: value => value + '!'})").asString());
            assertEquals("value!?", context.eval("js",
                    "Calls.unaryDefault({apply: value => value + '!'})").asString());
            assertEquals("left", context.eval("js", "Calls.binary({apply: (left, right) => left})").asString());
            assertEquals("value", context.eval("js", "Calls.unary(Calls.hostUnary())").asString());
        }
    }

    @Test
    void graalProxyDispatchUsesTheJavaNameDespiteHostMemberRemapping() {
        try (Context context = context()) {
            String calls = "var Calls = Java.type('" + Calls.class.getName() + "'); ";
            assertEquals("value!", context.eval("js", calls
                    + "Calls.remapped({original: value => value + '!'})").asString());
            assertThrows(PolyglotException.class, () -> context.eval("js",
                    "Calls.remapped({renamed: value => value + '!'})"));
        }
    }

    @Test
    void sealedInterfacesAcceptRealHostsButRejectProxyImplementations() {
        try (Context context = context()) {
            String calls = "var Calls = Java.type('" + Calls.class.getName() + "'); ";
            assertEquals("value", context.eval("js", calls + "Calls.closed(Calls.hostClosed())").asString());
            assertThrows(PolyglotException.class, () -> context.eval("js",
                    "Calls.closed({apply: value => value})"));
            assertThrows(PolyglotException.class, () -> context.eval("js", "Calls.closed(value => value)"));
        }
    }

    @Test
    void generatedConsumerAcceptsProxyMembersAndRejectsWrongCallbacks(@TempDir Path temp) throws Exception {
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, Map.of(), List.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(getClass().getPackageName()), List.of(), List.of(), List.of(), 1, "SMART"));
        var context = new ProbeContext.Of(snapshot, List.of(Calls.class), config, NekoJSPaths.fromGameDir(temp),
                "typescript", temp.resolve("typescript"));
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        Path output = Path.of("build/probe-ts-sam-proxy-input");
        for (var entry : files.entrySet()) {
            Path destination = output.resolve(entry.getKey());
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, entry.getValue());
        }
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import { $TypeScriptSamProxyInputTest$Calls as Calls }
                    from 'java:com/tkisor/nekojs/probe';
                import { $UnaryOperator } from 'java:java/util/function';
                const unary: string = Calls.unary({apply: (value: string) => value + '!'});
                const defaults: string = Calls.unaryDefault({apply: (value: string) => value});
                const binary: string = Calls.binary({apply: (left: string, right: string) => left});
                const remapped: string = Calls.remapped({original: (value: string) => value});
                const callback: string = Calls.unary((value: string) => value);
                const host: string = Calls.unary(Calls.hostUnary());
                const closedHost: string = Calls.closed(Calls.hostClosed());
                // @ts-expect-error A sealed interface cannot gain a proxy implementation.
                Calls.closed({apply: (value: string) => value});
                // @ts-expect-error Sealed interfaces cannot be implemented by executable callbacks either.
                Calls.closed((value: string) => value);
                // @ts-expect-error A proxy member must accept the actual operand kind.
                Calls.unary({apply: (value: number) => value});
                // @ts-expect-error A proxy member must produce the actual result kind.
                Calls.unary({apply: (value: string) => 123});
                // @ts-expect-error Function.apply must not bypass callback result checking.
                Calls.unary((value: string) => 123);
                // @ts-expect-error A class value is not a SAM instance or proxy implementation.
                Calls.unary($UnaryOperator);
                // @ts-expect-error A remapped operation's proxy still checks its Java operand kind.
                Calls.remapped({original: (value: number) => value});
                """);
        String functional = files.get("@package/java/util/function/index.d.ts");
        assertTrue(functional.contains("readonly \"apply\":"), "SAM proxy inputs retain a typed Java method");
        String closedAlias = files.get("@package/com/tkisor/nekojs/probe/index.d.ts").lines()
                .filter(line -> line.contains("export type $TypeScriptSamProxyInputTest$ClosedOperation_<"))
                .findFirst().orElseThrow();
        assertFalse(closedAlias.contains("readonly \"apply\":"), "Sealed SAM shapes do not gain proxy inputs");
        assertFalse(closedAlias.contains("(arg0: CallbackArg0) => CallbackResult"),
                "Sealed interfaces retain host inputs without unsupported callbacks");
    }

    private static Context context() {
        return Context.newBuilder("js").allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> name.equals(Calls.class.getName()))
                .allowCreateProcess(false).option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "2022").build();
    }
}
