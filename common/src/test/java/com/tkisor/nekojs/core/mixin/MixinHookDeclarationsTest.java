package com.tkisor.nekojs.core.mixin;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.probe.backend.typescript.MixinHookDeclarations;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MixinHookDeclarationsTest {
    @Test void installedHooksProduceStrictLiteralOverloads() throws Exception {
        var profile = new MixinPlan.Profile("26.1.2", "neoforge", "26.1.2.71", "runtime", "client");
        var hook = new MixinPlan.Hook("demo:health", "demo.Target", "health", "(IZ)F", "RETURN", true, "SERVER", "SERVER_MAIN", 1, true);
        var observe = new MixinPlan.Hook("demo:observe", "demo.Target", "observe", "()I", "RETURN", false, "SERVER", "SERVER_MAIN", 1, true);
        var head = new MixinPlan.Hook("demo:head", "demo.Target", "head", "()I", "HEAD", true, "SERVER", "SERVER_MAIN", 1, true);
        var cancel = new MixinPlan.Hook("demo:cancel", "demo.Target", "update", "(Ljava/lang/String;)V", "HEAD", true, "SERVER", "SERVER_MAIN", 1, false);
        var reference = new MixinPlan.Hook("demo:reference", "demo.Target", "reference", "()[Ljava/lang/String;", "RETURN", false, "SERVER", "SERVER_MAIN", 1, false);
        var client = new MixinPlan.Hook("demo:client", "demo.Target", "client", "()Z", "RETURN", false, "CLIENT", "CLIENT_MAIN", 1, true);
        var plan = new MixinPlan(1, profile, "d".repeat(64), List.of(hook, observe, head, cancel, reference, client));
        String text = MixinHookDeclarations.render(List.of(plan), ScriptType.SERVER);
        assertTrue(text.contains("readonly args: readonly [number, boolean]"));
        assertFalse(text.contains("demo:client"));
        assertTrue(MixinHookDeclarations.render(List.of(plan), ScriptType.CLIENT).contains("demo:client"));
        assertTrue(MixinHookDeclarations.render(List.of(), ScriptType.SERVER).isEmpty());
        Path out = Path.of("build/probe-ts-mixin-hooks"); Files.createDirectories(out);
        Files.writeString(out.resolve("hooks.d.ts"), text + "\ndeclare module \"java:demo\" { interface $Target { nativeMarker(): number; } }\n");
        Files.writeString(out.resolve("tsconfig.json"), "{\"compilerOptions\":{\"strict\":true,\"noEmit\":true,\"target\":\"ES2022\",\"types\":[]},\"files\":[\"hooks.d.ts\",\"exercise.ts\"]}");
        Files.writeString(out.resolve("exercise.ts"), """
                MixinEvents.inject("demo:health", event => {
                    const n: number = event.args[0];
                    const flag: boolean = event.args[1];
                    event.returnValue = n + (flag ? 1 : 0);
                    // @ts-expect-error Immutable argument tuple.
                    event.args[0] = 1;
                    // @ts-expect-error Numeric ABI only.
                    event.returnValue = "wrong";
                });
                MixinEvents.inject("demo:head", event => {
                    const initial: number | null = event.returnValue;
                    event.returnValue = 2;
                    // @ts-expect-error Missing HEAD result does not permit a null primitive replacement.
                    event.returnValue = null;
                });
                MixinEvents.inject("demo:observe", event => {
                    // @ts-expect-error Observation cannot replace a return value.
                    event.returnValue = 1;
                });
                MixinEvents.inject("demo:cancel", event => {
                    if (event.self !== null) { const marker: number = event.self.nativeMarker(); }
                    const input: string | null = event.args[0];
                    event.cancel();
                    // @ts-expect-error Void callbacks have no return value control.
                    event.returnValue = 1;
                    // @ts-expect-error Receiver slot is immutable.
                    event.self = null;
                });
                MixinEvents.inject("demo:reference", event => {
                    const result: ReadonlyArray<string | null> | null = event.returnValue;
                    // @ts-expect-error Only cancellable void HEAD hooks support cancel.
                    event.cancel();
                });
                // @ts-expect-error Client hook IDs do not leak into SERVER completion.
                MixinEvents.inject("demo:client", event => {});
                // @ts-expect-error The declaration module is not an ordinary runtime import.
                import { Mixin } from "nekojs/mixin";
                // @ts-expect-error Unknown hook IDs are not a permissive fallback.
                MixinEvents.inject("demo:pending", event => {});
                // @ts-expect-error A Promise is not a synchronous undefined callback result.
                MixinEvents.inject("demo:health", async event => {});
                """);
    }
}
