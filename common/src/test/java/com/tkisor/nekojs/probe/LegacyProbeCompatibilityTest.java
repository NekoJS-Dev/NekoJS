package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LegacyProbeCompatibilityTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    public static final class Helper {
        public static String of(String id) { return id; }
        public String getName() { return "helper"; }
    }

    public static final class SampleEvent {
        public String getMessage() { return "ok"; }
    }

    @Test
    void legacyBindingDeclarationMatchesGolden() throws Exception {
        var entry = BindingCatalogEntry.of("Helper", ScriptType.SERVER, Helper.class, true);
        String actual = new BindingDeclarationGenerator().generate(List.of(entry), ScriptType.SERVER);
        maybeRegenerate("legacy-bindings.expected.d.ts", actual);
        assertEquals(resource("legacy-bindings.expected.d.ts"), normalize(actual));
    }

    @Test
    void legacyEventDeclarationMatchesGolden() throws Exception {
        var aliases = new TypeAliasRegistry();
        // n/a: converter removed
        var generator = new EventDeclarationGenerator(aliases, new AdapterAliasGenerator(aliases));
        var event = EventCatalogEntry.of("ServerEvents", "sample", ScriptType.SERVER,
                SampleEvent.class, null, false, false);
        String actual = generator.generate(List.of(event), ScriptType.SERVER);
        maybeRegenerate("legacy-events.expected.d.ts", actual);
        assertEquals(resource("legacy-events.expected.d.ts"), normalize(actual));
    }

    /** 重生成模式（-Dnekojs.golden.regenerate=true）：实际产物覆盖写回 golden 后跳过断言。 */
    private static void maybeRegenerate(String name, String actual) throws Exception {
        if (!ProbeGoldenSupport.regenerateEnabled()) return;
        Path dir = ProbeGoldenSupport.resourceDir(LegacyProbeCompatibilityTest.class, "/nekojs/probe/");
        assertNotNull(dir, "golden resources must resolve to a file: URL");
        Files.writeString(dir.resolve(name), actual, StandardCharsets.UTF_8);
        Assumptions.assumeTrue(false, "goldens regenerated; review and commit");
    }

    private static String resource(String name) throws Exception {
        try (var in = LegacyProbeCompatibilityTest.class.getResourceAsStream("/nekojs/probe/" + name)) {
            if (in == null) throw new IllegalStateException("Missing golden " + name);
            return normalize(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static final Pattern IMPORT_PATTERN = Pattern.compile("import \\{([^}]+)\\} from \"([^\"]+)\";");

    private static String normalize(String value) {
        String normalized = value.replace("\r\n", "\n");
        // Sort imports within each import statement for deterministic comparison
        Matcher matcher = IMPORT_PATTERN.matcher(normalized);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String imports = matcher.group(1);
            String module = matcher.group(2);
            String[] parts = imports.split(",");
            String sorted = Arrays.stream(parts)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .sorted()
                    .collect(Collectors.joining(", "));
            matcher.appendReplacement(sb, Matcher.quoteReplacement("import { " + sorted + " } from \"" + module + "\";"));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
