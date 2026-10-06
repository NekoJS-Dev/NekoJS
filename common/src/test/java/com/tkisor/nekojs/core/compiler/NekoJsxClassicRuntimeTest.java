package com.tkisor.nekojs.core.compiler;

import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NekoJsxClassicRuntimeTest {
    @Test
    void classicFactoryProducesRetainedRuntimeVNodes() throws Exception {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build();
             InputStream input = getClass().getResourceAsStream("/nekojs/node/internal/define.js")) {
            if (input == null) throw new IllegalStateException("define.js resource is missing");
            context.eval("js", new String(input.readAllBytes(), StandardCharsets.UTF_8));
            var value = context.eval("js", "__nekoJsxFactory('label', { id: 'title' }, 'Hello')");

            assertTrue(value.getMember("$$nekoJsx").asBoolean());
            assertEquals("label", value.getMember("type").asString());
            assertEquals("label", value.getMember("tag").asString());
            assertEquals("title", value.getMember("props").getMember("id").asString());
            assertEquals("Hello", value.getMember("children").getArrayElement(0).asString());
        }
    }
}
