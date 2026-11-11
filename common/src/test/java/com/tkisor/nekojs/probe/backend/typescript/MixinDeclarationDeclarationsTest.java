package com.tkisor.nekojs.probe.backend.typescript;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MixinDeclarationDeclarationsTest {
    private static boolean initialized;
    interface Generic<T> { T value(); }
    abstract static class Target implements Generic<String> {
        static { initialized = true; }
        public String value() { return "example"; }
        public int count(int n, boolean flag) { return n; }
        public static void reset() {}
        public abstract void abstractMethod();
        public native void nativeMethod();
        public long withheld() { return 0L; }
        public void withheldArray(long[][] values) {}
    }

    @Test void emitsExactDeclaredSelectorsWithoutInitializingTargets() throws Exception {
        var warnings = new ArrayList<String>();
        String text = MixinDeclarationDeclarations.render(List.of(Target.class), warnings);
        assertFalse(initialized, "Probe must not initialize targets");
        assertTrue(warnings.isEmpty());
        assertTrue(text.contains("count(IZ)I"));
        assertTrue(text.contains("value()Ljava/lang/String;"));
        assertTrue(text.contains("reset()V"));
        for (String excluded : List.of("<init>", "<clinit>", "abstractMethod", "nativeMethod", "withheld", "value()Ljava/lang/Object;")) {
            assertFalse(text.contains(excluded), excluded);
        }
        assertEquals(text, MixinDeclarationDeclarations.render(List.of(Target.class, Target.class), warnings));
        assertTrue(MixinDeclarationDeclarations.render(List.of(), warnings).contains("declare module"));
        Path out = Path.of("build/probe-ts-mixin-declarations");
        Files.createDirectories(out);
        Files.writeString(out.resolve("mixin.d.ts"), text);
        Files.writeString(out.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"target":"ES2022","types":[]},
                 "files":["mixin.d.ts","exercise.ts"]}
                """);
        Files.writeString(out.resolve("exercise.ts"), """
                import { Mixin } from "nekojs/mixin";
                Mixin.target("%s", target => {
                    target.inject("count(IZ)I", "example:count", build => {
                        build.at = "RETURN";
                        build.cancellable = true;
                        build.scriptType = "SERVER";
                        build.thread = "SERVER_MAIN";
                        build.require = 1;
                        build.staticMethod = false;
                        // @ts-expect-error Unsupported injection point.
                        build.at = "TAIL";
                        // @ts-expect-error Exactly one target is required.
                        build.require = 0;
                        // @ts-expect-error Unknown builder property.
                        build.remap = true;
                    });
                    // @ts-expect-error Exact descriptor, not a method name.
                    target.inject("count", "example:bad", build => {});
                    // @ts-expect-error The known target has no such descriptor.
                    target.inject("count(I)I", "example:bad", build => {});
                    // @ts-expect-error Native and abstract methods are excluded.
                    target.inject("nativeMethod()V", "example:bad", build => {});
                    // @ts-expect-error Long values are outside the supported ABI.
                    target.inject("withheld()J", "example:bad", build => {});
                    // @ts-expect-error Namespaced hook ID required.
                    target.inject("reset()V", "invalid", build => {});
                    // @ts-expect-error Builders must be synchronous.
                    target.inject("reset()V", "example:async", async build => {});
                });
                Mixin.target("unscanned.Target", target => {
                    target.inject("method()V", "example:unknown", build => {});
                    // @ts-expect-error Even unscanned selectors need a descriptor shape.
                    target.inject("method", "example:bad", build => {});
                });
                // @ts-expect-error No runtime subscriptions in the declaration sandbox.
                MixinEvents.inject("example:count", event => {});
                // @ts-expect-error No ordinary globals in the declaration sandbox.
                ServerEvents.started(event => {});
                // @ts-expect-error Target callbacks must be synchronous.
                Mixin.target("unscanned.Target", async target => {});
                """.formatted(Target.class.getName()));
    }
}
