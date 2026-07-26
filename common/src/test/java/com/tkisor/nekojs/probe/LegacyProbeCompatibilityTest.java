package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.probe.types.TypeConverter;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(resource("legacy-bindings.expected.d.ts"), normalize(actual));
    }

    @Test
    void legacyEventDeclarationMatchesGolden() throws Exception {
        var aliases = new TypeAliasRegistry();
        var converter = new TypeConverter(aliases);
        var generator = new EventDeclarationGenerator(converter, new AdapterAliasGenerator(aliases));
        var event = EventCatalogEntry.of("ServerEvents", "sample", ScriptType.SERVER,
                SampleEvent.class, null, false, false);
        assertEquals(resource("legacy-events.expected.d.ts"), normalize(generator.generate(List.of(event), ScriptType.SERVER)));
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
        String[] lines = sb.toString().split("\n");
        Arrays.sort(lines);
        return String.join("\n", lines);
    }
}
