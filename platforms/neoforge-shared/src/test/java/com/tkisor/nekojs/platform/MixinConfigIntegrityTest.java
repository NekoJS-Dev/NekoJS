package com.tkisor.nekojs.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * nekojs.mixins.json 完整性 smoke test：JSON 可解析、required=true、每个 mixin 类可被加载，
 * 防止 mixin 配置与编译产物漂移。各平台读取各自 classpath 上的同名配置（1.21.1 / 26.x 内容不同）。
 */
class MixinConfigIntegrityTest {

    @Test
    void mixinConfigParsesAndEveryListedClassLoads() throws Exception {
        InputStream in = getClass().getResourceAsStream("/nekojs.mixins.json");
        assertNotNull(in, "nekojs.mixins.json resource must be present on the test classpath");

        JsonObject root;
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        JsonElement required = root.get("required");
        assertNotNull(required, "mixin config missing 'required'");
        assertTrue(required.getAsBoolean(), "mixin config must set required=true");

        JsonElement packageEl = root.get("package");
        assertNotNull(packageEl, "mixin config missing 'package'");
        String pkg = packageEl.getAsString();
        assertFalse(pkg.isEmpty(), "mixin config 'package' must not be empty");

        // 三段全查（旧版只查 mixins 段——client/server 段的类名拼错不会被抓住）
        Set<String> seen = new HashSet<>();
        ClassLoader loader = getClass().getClassLoader();
        for (String section : List.of("mixins", "client", "server")) {
            JsonElement sectionEl = root.get(section);
            if (sectionEl == null) continue;
            JsonArray sectionMixins = sectionEl.getAsJsonArray();
            for (JsonElement element : sectionMixins) {
                String simpleName = element.getAsString();
                assertTrue(seen.add(section + ":" + simpleName), "duplicate mixin entry: " + simpleName);

                String fqcn = pkg + "." + simpleName;
                // initialize=false：只验证类存在且可加载，避免在无游戏上下文下运行 mixin 静态初始化器。
                assertDoesNotThrow(() -> Class.forName(fqcn, false, loader),
                        "mixin class not found on classpath: " + fqcn);
            }
        }
        assertFalse(seen.isEmpty(), "mixin config lists no mixin classes at all");
    }
}
