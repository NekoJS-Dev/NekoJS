package com.tkisor.nekojs.core;

import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HostIdentityInputTest {
    @Test
    void productionHostAccessAcceptsIdentityValuesAndRejectsUnadaptedStrings() {
        try (Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false)
                .allowCreateThread(false)
                .option("js.foreign-object-prototype", "true")
                .option("js.nashorn-compat", "true")
                .build()) {
            context.getBindings("js").putMember("identity", new IdentityInputs());
            assertEquals(0, context.eval("js", "identity.file().compareTo(identity.file())").asInt());
            assertEquals(0, context.eval("js", "identity.path().compareTo(identity.path())").asInt());
            assertEquals(0, context.eval("js", "identity.uuid().compareTo(identity.uuid())").asInt());
            for (String member : List.of("file", "path", "uuid")) {
                assertThrows(PolyglotException.class,
                        () -> context.eval("js", "identity." + member + "().compareTo('unadapted')"));
            }
        }
    }

    public static final class IdentityInputs {
        public File file() { return new File("identity-fixture"); }
        public Path path() { return Path.of("identity-fixture"); }
        public UUID uuid() { return UUID.fromString("00000000-0000-0000-0000-000000000001"); }
    }
}
