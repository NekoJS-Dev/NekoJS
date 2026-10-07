package com.tkisor.nekojs.core.compiler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class NekoSourceMapBuilderTest {
    @Test
    void exactIdentityPreservesTrailingLineAndUtf16Columns() {
        String source = "abc\nxy\n";
        JsonObject map = JsonParser.parseString(
                NekoSourceMapBuilder.identity(Path.of("native.js"), source, source)).getAsJsonObject();
        assertEquals("AAAA,CAAC,CAAC,CAAC;AACH,CAAC,CAAC;AAAF",
                map.get("mappings").getAsString());
        String utf16 = "😀\r\nx\n";
        JsonObject utf16Map = JsonParser.parseString(
                NekoSourceMapBuilder.identity(Path.of("unicode.js"), utf16, utf16)).getAsJsonObject();
        assertEquals("AAAA,CAAC,CAAC,CAAC;AACH,CAAC;AAAD",
                utf16Map.get("mappings").getAsString());
    }

    @Test
    void exactNativeIdentityHandlesLargeMultilineSourceWithoutRepeatedPrefixScans() {
        String source = "value++;\n".repeat(50000);
        String map = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> NekoSourceMapBuilder.identity(Path.of("native-large.js"), source, source));
        JsonObject parsed = JsonParser.parseString(map).getAsJsonObject();
        assertEquals("native-large.js", parsed.get("file").getAsString());
        assertEquals(source, parsed.getAsJsonArray("sourcesContent").get(0).getAsString());
        assertEquals(50001, parsed.get("mappings").getAsString().split(";", -1).length);
        assertEquals("AAAA,CAAC,CAAC,CAAC,CAAC,CAAC,CAAC,CAAC,CAAC",
                parsed.get("mappings").getAsString().split(";", 2)[0]);
    }
}
