package com.tkisor.nekojs.probe.backend.python;

import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PythonFieldMethodCollisionTest {
    public static final class HostFixture {
        public boolean locked;
        public final double x = 3.0;

        public boolean locked() { return !locked; }
        public double x() { return x + 1.0; }
    }

    @Test
    void realFieldReadAndMethodInvocationHaveDistinctHostResults() throws Exception {
        HostFixture fixture = new HostFixture();
        assertFalse(fixture.locked);
        assertTrue(fixture.locked());
        assertEquals(3.0, fixture.x);
        assertEquals(4.0, fixture.x());
        try (Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false)
                .option("js.nashorn-compat", "true")
                .build()) {
            context.getBindings("js").putMember("target", fixture);
            String facts = context.eval("js", "JSON.stringify({lockedType:typeof target.locked, "
                    + "lockedRead:target.locked, lockedCall:target.locked(), xType:typeof target.x, "
                    + "xRead:target.x, xCall:target.x()})").asString();
            Path output = Path.of("build/probe-python-field-method-host-facts");
            Files.createDirectories(output);
            Files.writeString(output.resolve("host-facts.json"), facts + "\n");
            assertEquals("boolean", context.eval("js", "typeof target.locked").asString(), facts);
            assertFalse(context.eval("js", "target.locked").asBoolean(), facts);
            assertTrue(context.eval("js", "target.locked()").asBoolean(), facts);
            assertEquals(3.0, context.eval("js", "target.x").asDouble(), facts);
            assertEquals(4.0, context.eval("js", "target.x()").asDouble(), facts);
            assertTrue(context.eval("js", "var stored = target.x; var failed = false; "
                    + "try { stored(); } catch (error) { failed = error instanceof TypeError; } failed;").asBoolean());
            context.eval("js", "target.locked = true");
            assertTrue(fixture.locked);
            assertTrue(context.eval("js", "target.locked").asBoolean());
            assertFalse(context.eval("js", "target.locked()").asBoolean());
        }
    }
}
