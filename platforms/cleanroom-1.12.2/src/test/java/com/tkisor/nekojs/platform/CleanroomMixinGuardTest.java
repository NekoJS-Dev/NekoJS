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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W2 护栏的 cleanroom 副本：nekojs.default.mixin.json + nekojs.mod.mixin.json 两份配置的
 * 完整性 / 目标解析 / Extension 覆盖（NeoForge 侧是三个分散测试，这里合并一份；解析器
 * {@link ClassFileAnnotations} 为同源拷贝）。字节级解析的原因见其 javadoc——cleanroom 的
 * sponge-mixin 注解是 CLASS retention，反射读不到，字节读得到。
 */
class CleanroomMixinGuardTest {

    private static final List<String> CONFIGS = List.of(
            "nekojs.default.mixin.json", "nekojs.mod.mixin.json");
    private static final String INJECT_PACKAGE = "com.tkisor.nekojs.api.inject";

    private final Map<String, ClassFileAnnotations.ClassData> targetCache = new HashMap<>();

    @Test
    void configsParseAndEveryListedClassLoads() throws Exception {
        for (String config : CONFIGS) {
            JsonObject root = readConfig(config);
            assertTrue(root.get("required").getAsBoolean(), config + " must set required=true");
            String pkg = root.get("package").getAsString();
            Set<String> seen = new HashSet<>();
            for (String section : List.of("mixins", "client", "server")) {
                JsonElement sectionEl = root.get(section);
                if (sectionEl == null) continue;
                for (JsonElement e : sectionEl.getAsJsonArray()) {
                    String entry = e.getAsString();
                    assertTrue(seen.add(entry), config + " duplicate entry: " + entry);
                    assertDoesNotThrow(() -> Class.forName(pkg + "." + entry, false,
                                    getClass().getClassLoader()),
                            config + " lists unloadable class: " + entry);
                }
            }
            assertFalse(seen.isEmpty(), config + " lists no mixins");
        }
    }

    @Test
    void everyInjectionTargetResolves() throws Exception {
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (String config : CONFIGS) {
            JsonObject root = readConfig(config);
            String pkg = root.get("package").getAsString();
            for (String section : List.of("mixins", "client", "server")) {
                JsonElement sectionEl = root.get(section);
                if (sectionEl == null) continue;
                for (JsonElement e : sectionEl.getAsJsonArray()) {
                    String internalName = (pkg + "." + e.getAsString()).replace('.', '/');
                    ClassFileAnnotations.ClassData data = readClass(internalName);
                    assertNotNull(data, config + " class bytes missing: " + internalName);
                    checked++;
                    verifyTargets(internalName, data, failures);
                }
            }
        }
        assertTrue(failures.isEmpty(), checked + " mixins checked.\nFailures:\n  "
                + String.join("\n  ", failures));
    }

    @Test
    void everyExtensionIsAppliedByAMixin() throws Exception {
        Set<String> extensions = new HashSet<>();
        ClassLoader loader = getClass().getClassLoader();
        for (var url : Collections.list(loader.getResources(INJECT_PACKAGE.replace('.', '/')))) {
            if (!"file".equalsIgnoreCase(url.getProtocol())) continue;
            try (var files = Files.list(Path.of(url.toURI()))) {
                files.filter(f -> f.getFileName().toString().endsWith("Extension.class"))
                        .forEach(f -> extensions.add(INJECT_PACKAGE + "."
                                + f.getFileName().toString().replace(".class", "")));
            }
        }
        assertFalse(extensions.isEmpty(), "no *Extension classes found");

        Set<String> covered = new HashSet<>();
        for (String config : CONFIGS) {
            JsonObject root = readConfig(config);
            String pkg = root.get("package").getAsString();
            for (String section : List.of("mixins", "client", "server")) {
                JsonElement sectionEl = root.get(section);
                if (sectionEl == null) continue;
                for (JsonElement e : sectionEl.getAsJsonArray()) {
                    Class<?> mixin = Class.forName(pkg + "." + e.getAsString(), false, loader);
                    for (Class<?> iface : mixin.getInterfaces()) {
                        if (iface.getName().startsWith(INJECT_PACKAGE + ".")) {
                            covered.add(iface.getName());
                        }
                    }
                }
            }
        }

        Set<String> missing = new HashSet<>(extensions);
        missing.removeAll(covered);
        // cleanroom 无 interface_injection：Extension 只能靠 mixin 应用
        assertTrue(missing.isEmpty(), "Extensions not applied by any mixin: " + missing);
    }

    // ==================== 目标验证（与 NeoForge 版同规则） ====================

    private void verifyTargets(String internalName, ClassFileAnnotations.ClassData data,
                               List<String> failures) {
        var mixinAnnos = ofType(data.annotations(), "Lorg/spongepowered/asm/mixin/Mixin;");
        if (mixinAnnos.isEmpty()) {
            failures.add(internalName + ": missing @Mixin annotation");
            return;
        }
        Set<String> targets = new HashSet<>();
        for (var anno : mixinAnnos) {
            for (String v : anno.memberStrings("value")) {
                targets.add(normalize(v));
            }
            for (String v : anno.memberStrings("targets")) {
                targets.add(normalize(v));
            }
        }
        targets.removeIf(String::isEmpty);

        List<ClassFileAnnotations.ClassData> targetData = new ArrayList<>();
        for (String target : targets) {
            ClassFileAnnotations.ClassData t = targetClass(target);
            if (t == null) failures.add(internalName + ": target not on classpath: " + target);
            else targetData.add(t);
        }

        for (ClassFileAnnotations.MemberData method : data.methods()) {
            for (var injection : allInjections(method)) {
                for (String target : injection.memberStrings("method")) {
                    MemberRef ref = MemberRef.parse(target);
                    if (ref == null) {
                        failures.add(internalName + ": unparseable target '" + target + "'");
                        continue;
                    }
                    List<ClassFileAnnotations.ClassData> owners = new ArrayList<>(targetData);
                    if (ref.owner() != null) {
                        ClassFileAnnotations.ClassData owner = targetClass(ref.owner());
                        owners.clear();
                        if (owner != null) owners.add(owner);
                    }
                    boolean found = false;
                    for (ClassFileAnnotations.ClassData owner : owners) {
                        for (ClassFileAnnotations.MemberData m : owner.methods()) {
                            if (!m.name().equals(ref.name())) continue;
                            if (ref.descriptor() == null || !ref.descriptor().contains(")")
                                    || m.descriptor().equals(ref.descriptor())) {
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        failures.add(internalName + ": injection target not found: '" + target + "'");
                    }
                }
            }
            for (var shadow : ofType(method.annotations(), "Lorg/spongepowered/asm/mixin/Shadow;")) {
                Set<String> names = new HashSet<>();
                names.add(method.name());
                names.addAll(shadow.memberStrings("aliases"));
                for (String name : names) {
                    if (!targetData.stream().flatMap(t -> t.methods().stream())
                            .anyMatch(m -> m.name().equals(name))) {
                        failures.add(internalName + ": @Shadow method not found: '" + name + "'");
                    }
                }
            }
        }
        for (ClassFileAnnotations.MemberData field : data.fields()) {
            for (var shadow : ofType(field.annotations(), "Lorg/spongepowered/asm/mixin/Shadow;")) {
                if (targetData.stream().flatMap(t -> t.fields().stream())
                        .noneMatch(f -> f.name().equals(field.name()))) {
                    failures.add(internalName + ": @Shadow field not found: '" + field.name() + "'");
                }
            }
        }
    }

    private static List<ClassFileAnnotations.AnnotationData> ofType(
            List<ClassFileAnnotations.AnnotationData> list, String type) {
        List<ClassFileAnnotations.AnnotationData> out = new ArrayList<>();
        for (ClassFileAnnotations.AnnotationData a : list) {
            if (a.type().equals(type)) out.add(a);
        }
        return out;
    }

    private static List<ClassFileAnnotations.AnnotationData> allInjections(ClassFileAnnotations.MemberData method) {
        List<ClassFileAnnotations.AnnotationData> out = new ArrayList<>();
        for (String type : List.of(
                "Lorg/spongepowered/asm/mixin/injection/Inject;",
                "Lorg/spongepowered/asm/mixin/injection/Redirect;",
                "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
                "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
                "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
                "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;")) {
            for (ClassFileAnnotations.AnnotationData a : method.annotations()) {
                if (a.type().equals(type)) out.add(a);
            }
        }
        return out;
    }

    record MemberRef(String owner, String name, String descriptor) {
        static MemberRef parse(String raw) {
            String s = raw.trim();
            if (s.isEmpty()) return null;
            int paren = s.indexOf('(');
            String head = paren >= 0 ? s.substring(0, paren) : s;
            String desc = paren >= 0 ? s.substring(paren) : null;
            String owner = null;
            int semi = head.lastIndexOf(';');
            if (semi >= 0) {
                owner = head.substring(0, semi);
                if (owner.startsWith("L")) owner = owner.substring(1);
                head = head.substring(semi + 1);
            }
            if (head.isEmpty() || head.contains("/") || head.contains(".")) return null;
            return new MemberRef(owner, head, desc);
        }
    }

    private static String normalize(String raw) {
        String s = raw.trim();
        if (s.startsWith("L") && s.endsWith(";")) s = s.substring(1, s.length() - 1);
        return s.replace('.', '/');
    }

    private ClassFileAnnotations.ClassData targetClass(String internalName) {
        String key = normalize(internalName);
        ClassFileAnnotations.ClassData cached = targetCache.get(key);
        if (cached == null) {
            cached = readClass(key);
            if (cached != null) targetCache.put(key, cached);
        }
        return cached;
    }

    private static ClassFileAnnotations.ClassData readClass(String internalName) {
        InputStream in = CleanroomMixinGuardTest.class.getClassLoader()
                .getResourceAsStream(internalName + ".class");
        if (in == null) return null;
        try (InputStream bytes = in) {
            return ClassFileAnnotations.parse(internalName, bytes);
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse " + internalName, e);
        }
    }

    private static JsonObject readConfig(String resource) throws Exception {
        InputStream in = CleanroomMixinGuardTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull(in, resource + " must be on the test classpath");
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
