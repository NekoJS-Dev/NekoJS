package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.ScriptId;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptPropertyParsingTest {
    @TempDir Path directory;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void malformedPriorityRetainsAnEarlierValidValue() throws Exception {
        var registry = new ScriptPropertyRegistry.Impl();
        registry.register(ScriptProperty.PRIORITY);
        registry.freeze();
        Path source = directory.resolve("priority.js");
        Files.writeString(source, "// priority: 7\n// priority: invalid\nconsole.info('body');\n");
        var script = new ScriptContainer(ScriptId.of("nekojs", "server/priority.js"), ScriptType.SERVER, source, registry);

        script.preload();

        assertEquals(7, script.properties.getOrDefault(ScriptProperty.PRIORITY));
        assertFalse(script.disabled);
        assertNull(script.lastError);
    }

    @Test
    void aNullResultFromACustomReaderDoesNotEraseItsEarlierValue() throws Exception {
        var custom = new ScriptProperty<Integer>("custom", 0, value -> value.equals("invalid") ? null : Integer.valueOf(value));
        var registry = new ScriptPropertyRegistry.Impl();
        registry.register(custom);
        registry.freeze();
        Path source = directory.resolve("custom.js");
        Files.writeString(source, "// custom: 7\n// custom: invalid\n");
        var script = new ScriptContainer(ScriptId.of("nekojs", "server/custom.js"), ScriptType.SERVER, source, registry);

        script.preload();

        assertEquals(7, script.properties.getOrDefault(custom));
        assertFalse(script.disabled);
        assertNull(script.lastError);
    }
    @Test
    void invalidFirstValueUsesTheDefaultAndLaterValidValuesWin() throws Exception {
        var firstInvalid = script("first-invalid.js", "// priority: invalid\n", ScriptProperty.PRIORITY);
        firstInvalid.preload();
        assertEquals(0, firstInvalid.properties.getOrDefault(ScriptProperty.PRIORITY));
        assertFalse(firstInvalid.disabled);
        assertNull(firstInvalid.lastError);

        var laterValid = script("later-valid.js", "// priority: invalid\n// priority: 5\n// priority: 9\n", ScriptProperty.PRIORITY);
        laterValid.preload();
        assertEquals(9, laterValid.properties.getOrDefault(ScriptProperty.PRIORITY));
    }

    @Test
    void unknownKeysAndDirectivesAfterTheBodyDoNotChangePropertiesOrWarn() throws Exception {
        var script = script("header.js", "// priority: 7\n// unknown: invalid\nconsole.info('body');\n// priority: invalid\n",
                ScriptProperty.PRIORITY);
        try (var capture = new LogCapture()) {
            script.preload();
            assertEquals(7, script.properties.getOrDefault(ScriptProperty.PRIORITY));
            assertTrue(capture.forSource(script.path).isEmpty());
        }
    }

    @Test
    void disablePresenceAndCommaSeparatedPropertiesKeepTheirExistingMeaning() throws Exception {
        var script = script("lists.js", "// disable: false\n// after: init.js, , ./base.js\n// modloaded: first, , second\n",
                ScriptProperty.DISABLE, ScriptProperty.AFTER, ScriptProperty.MODLOADED);
        script.preload();
        assertTrue(script.properties.getOrDefault(ScriptProperty.DISABLE));
        assertEquals(List.of("init.js", "./base.js"), script.properties.getOrDefault(ScriptProperty.AFTER));
        assertEquals(List.of("first", "second"), script.properties.getOrDefault(ScriptProperty.MODLOADED));
    }

    @Test
    void eachMalformedLineProducesOneCodedWarningWithPhysicalLineAndOriginalCause() throws Exception {
        var script = script("warning.js", "\n// explanatory comment\n// priority: 7\n// priority: invalid\n// priority: invalid-again\n",
                ScriptProperty.PRIORITY);
        try (var capture = new LogCapture()) {
            script.preload();
            var events = capture.forSource(script.path);
            assertEquals(2, events.size(), "one diagnostic per invalid directive, not per parser layer");
            assertWarning(events.get(0), script.path, "priority", 4);
            assertWarning(events.get(1), script.path, "priority", 5);
            assertTrue(events.get(0).cause() instanceof NumberFormatException);
            assertTrue(events.get(1).cause() instanceof NumberFormatException);
            assertEquals(7, script.properties.getOrDefault(ScriptProperty.PRIORITY));
        }
    }

    @Test
    void aThrowingCustomReaderPreservesItsValueAndReportsTheSameCause() throws Exception {
        var failure = new IllegalStateException("custom-reader-failure");
        var custom = new ScriptProperty<Integer>("custom", 3, raw -> {
            if (raw.equals("invalid")) throw failure;
            return Integer.valueOf(raw);
        });
        var script = script("throwing.js", "// custom: 7\n// custom: invalid\n", custom);
        try (var capture = new LogCapture()) {
            script.preload();
            var events = capture.forSource(script.path);
            assertEquals(1, events.size());
            assertWarning(events.getFirst(), script.path, "custom", 2);
            assertSame(failure, events.getFirst().cause());
            assertEquals(7, script.properties.getOrDefault(custom));
            assertFalse(script.disabled);
            assertNull(script.lastError);
        }
    }

    @Test
    void aNullCustomResultIsReportedWithoutChangingGeneralNullClearing() throws Exception {
        var custom = new ScriptProperty<Integer>("nullable", 3, raw -> raw.equals("invalid") ? null : Integer.valueOf(raw));
        var script = script("nullable.js", "// nullable: 7\n// nullable: invalid\n", custom);
        try (var capture = new LogCapture()) {
            script.preload();
            var events = capture.forSource(script.path);
            assertEquals(1, events.size());
            assertWarning(events.getFirst(), script.path, "nullable", 2);
            assertTrue(events.getFirst().cause() instanceof IllegalArgumentException);
            assertEquals(7, script.properties.getOrDefault(custom));
            assertNull(custom.read("invalid"), "null remains a supported unparsed result at the reader seam");
            assertEquals(7, script.properties.put(custom, null), "explicit storage clearing remains supported");
            assertEquals(3, script.properties.getOrDefault(custom));
        }
    }

    @Test
    void directReadsPreserveTheOriginalReaderFailure() {
        var failure = new IllegalArgumentException("bad-direct-input");
        var custom = new ScriptProperty<Integer>("direct", 0, raw -> { throw failure; });
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> custom.read("invalid")));
    }

    private ScriptContainer script(String name, String content, ScriptProperty<?>... properties) throws Exception {
        var registry = new ScriptPropertyRegistry.Impl();
        for (var property : properties) registry.register(property);
        registry.freeze();
        Path source = directory.resolve(name);
        Files.writeString(source, content);
        return new ScriptContainer(ScriptId.of("nekojs", "server/" + name), ScriptType.SERVER, source, registry);
    }

    private static void assertWarning(Captured event, Path source, String key, int line) {
        assertEquals("WARN", event.level());
        assertTrue(event.message().contains("[NEKO-1025]"), event.message());
        assertTrue(event.message().contains("'" + key + "'"), event.message());
        assertTrue(event.message().contains(source + ":" + line), event.message());
        assertTrue(event.cause() != null, "a parse failure must retain its cause");
    }

    private record Captured(String level, String message, Throwable cause) {}

    /** Log4j is runtime-only in these tests, so capture its public appender boundary reflectively. */
    private static final class LogCapture implements AutoCloseable {
        private final List<Captured> events = new CopyOnWriteArrayList<>();
        private final Class<?> appenderType = Class.forName("org.apache.logging.log4j.core.Appender");
        private final Class<?> coreLoggerType = Class.forName("org.apache.logging.log4j.core.Logger");
        private final Object logger;
        private final Object appender;

        LogCapture() throws Exception {
            ScriptTypeEnv.class.getMethod("logger", ScriptType.class).invoke(null, ScriptType.SERVER);
            logger = Class.forName("org.apache.logging.log4j.LogManager").getMethod("getLogger", String.class)
                    .invoke(null, "nekojs." + ScriptType.SERVER.name);
            var logEvent = Class.forName("org.apache.logging.log4j.core.LogEvent");
            var messageType = Class.forName("org.apache.logging.log4j.message.Message");
            String name = "property-capture-" + UUID.randomUUID();
            appender = Proxy.newProxyInstance(appenderType.getClassLoader(), new Class<?>[]{appenderType}, (proxy, method, arguments) -> {
                return switch (method.getName()) {
                    case "append" -> {
                        Object event = arguments[0];
                        Object message = logEvent.getMethod("getMessage").invoke(event);
                        events.add(new Captured(String.valueOf(logEvent.getMethod("getLevel").invoke(event)),
                                (String) messageType.getMethod("getFormattedMessage").invoke(message),
                                (Throwable) logEvent.getMethod("getThrown").invoke(event)));
                        yield null;
                    }
                    case "getName", "toString" -> name;
                    case "isStarted", "ignoreExceptions" -> true;
                    case "isStopped" -> false;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "getState" -> java.util.Arrays.stream(method.getReturnType().getEnumConstants())
                            .filter(value -> value.toString().equals("STARTED")).findFirst().orElseThrow();
                    default -> null;
                };
            });
            coreLoggerType.getMethod("addAppender", appenderType).invoke(logger, appender);
        }

        List<Captured> forSource(Path source) {
            return events.stream().filter(event -> event.message().contains(source.toString())).toList();
        }

        @Override
        public void close() throws Exception {
            coreLoggerType.getMethod("removeAppender", appenderType).invoke(logger, appender);
        }
    }

}
