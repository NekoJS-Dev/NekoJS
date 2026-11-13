// fabric has no matching implementation yet; remove this whole-file guard once the port lands
//? if neoforge {
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard: static resolution of mixin targets. The mixin layer had no automated verification before.
 *
 * <p>A broken mixin injection only fails at runtime, and production and dev fail differently. This
 * test parses the .class bytes of every mixin class ({@link ClassFileAnnotations}) and checks:
 * <ol>
 *   <li>the {@code @Mixin} target class exists on the classpath;</li>
 *   <li>the {@code method} target of every injection annotation
 *       (@Inject/@Redirect/@ModifyArg/@ModifyArgs/@ModifyVariable/@ModifyConstant) exists on the
 *       target class; when a descriptor is present it must match exactly;</li>
 *   <li>the member referenced by {@code @At("INVOKE"/"FIELD")} exists — a mistyped At target is
 *       the most common cause of silent mixin failure;</li>
 *   <li>{@code @Shadow} (fields/methods, aliases included) and {@code @Overwrite} exist by name on
 *       the target class (descriptors are compared by name only because generics are erased,
 *       which avoids false reports).</li>
 * </ol>
 *
 * <p>Bytes, not reflection, are required: ModDev's test environment rewrites mixin classes in
 * memory with an agent (their annotations are cleared), and some mixin implementations only keep
 * CLASS retention annotations, which reflection cannot read either. Parsing bytes works for both.
 * Each platform reads the same-named config from its own classpath.
 */
class MixinTargetResolutionTest {

    private static final String CONFIG_RESOURCE = "nekojs.mixins.json";

    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
    private static final String MODIFY_ARGS = "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;";
    private static final String MODIFY_VARIABLE = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
    private static final String MODIFY_CONSTANT = "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;";

    private static final Set<String> INVOKE_ATS = Set.of("INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING");

    /** Cache of target class bytes: several mixins and injection points look up the same class repeatedly. */
    private final Map<String, ClassFileAnnotations.ClassData> targetCache = new HashMap<>();

    @Test
    void everyConfiguredMixinTargetResolves() throws Exception {
        verifyConfig(CONFIG_RESOURCE);
        if (getClass().getClassLoader().getResource("nekojs-dynamic.mixins.json") != null) {
            verifyConfig("nekojs-dynamic.mixins.json");
        }
    }

    private void verifyConfig(String resource) throws Exception {
        JsonObject root = readConfig(resource);
        String pkg = root.get("package").getAsString();

        List<String> failures = new ArrayList<>();
        int checkedMixins = 0;
        int verifiedPoints = 0;
        for (String section : List.of("mixins", "client", "server")) {
            for (String entry : sectionNames(root, section)) {
                String internalName = (pkg + "." + entry).replace('.', '/');
                ClassFileAnnotations.ClassData data = readClass(internalName);
                if (data == null) {
                    failures.add(resource + " [" + section + "] " + entry
                            + ": class bytes not found on classpath");
                    continue;
                }
                checkedMixins++;
                verifiedPoints += verifyMixin(internalName, data, failures);
            }
        }

        assertTrue(checkedMixins > 0, "no mixin classes were checked - config discovery is broken");
        assertTrue(failures.isEmpty(),
                checkedMixins + " mixins checked, " + verifiedPoints + " injection points verified.\nFailures:\n  "
                        + String.join("\n  ", failures));
    }

    // ==================== Verifying a single mixin ====================

    private int verifyMixin(String internalName, ClassFileAnnotations.ClassData data, List<String> failures) {
        List<ClassFileAnnotations.AnnotationData> mixinAnnos = ofType(data.annotations(), MIXIN);
        if (mixinAnnos.isEmpty()) {
            failures.add(internalName + ": missing @Mixin annotation");
            return 0;
        }
        ClassFileAnnotations.AnnotationData mixin = mixinAnnos.getFirst();

        // @Mixin(value = {A.class}, targets = {"b.C"}): the value member is a Class literal ('c' tag, parsed
        // with L...; wrapping) and targets holds dotted strings. Normalize both to internal names.
        Set<String> targets = new HashSet<>();
        for (String v : mixin.memberStrings("value")) targets.add(normalizeInternalName(v));
        for (String v : mixin.memberStrings("targets")) targets.add(normalizeInternalName(v));
        targets.removeIf(String::isEmpty);
        if (targets.isEmpty()) {
            failures.add(internalName + ": no @Mixin target");
            return 0;
        }

        List<ClassFileAnnotations.ClassData> targetData = new ArrayList<>();
        for (String target : targets) {
            ClassFileAnnotations.ClassData t = targetClass(target);
            if (t == null) {
                failures.add(internalName + ": @Mixin target class not on classpath: " + target);
            } else {
                targetData.add(t);
            }
        }

        int verified = 0;
        String context = internalName.substring(internalName.lastIndexOf('/') + 1)
                + " -> " + String.join(",", targets);

        for (ClassFileAnnotations.MemberData method : data.methods()) {
            for (ClassFileAnnotations.AnnotationData injection : injections(method)) {
                for (String target : injection.memberStrings("method")) {
                    MemberRef ref = MemberRef.parse(target);
                    if (ref == null) {
                        failures.add(context + ": unparseable method target '" + target
                                + "' on " + method.name());
                        continue;
                    }
                    verified++;
                    if (!memberExists(targetData, ref)) {
                        failures.add(context + ": injection method target not found: '" + target + "'");
                    }
                }
                ClassFileAnnotations.AnnotationData at = injection.memberAnnotation("at");
                if (at != null) {
                    String failure = verifyAt(context, method.name(), at);
                    if (failure != null) failures.add(failure);
                    else verified++;
                }
            }

            for (ClassFileAnnotations.AnnotationData shadow : ofType(method.annotations(), SHADOW)) {
                Set<String> names = new HashSet<>();
                names.add(method.name());
                names.addAll(shadow.memberStrings("aliases"));
                verified++;
                for (String name : names) {
                    if (!anyTargetHasMethod(targetData, name)) {
                        failures.add(context + ": @Shadow method not found by name: '" + name + "'");
                    }
                }
            }
            if (!ofType(method.annotations(), OVERWRITE).isEmpty()) {
                verified++;
                if (!anyTargetHasMethod(targetData, method.name())) {
                    failures.add(context + ": @Overwrite method not found: '" + method.name() + "'");
                }
            }

            for (ClassFileAnnotations.AnnotationData accessor : ofType(method.annotations(), ACCESSOR)) {
                verified += verifyExplicitName(context, "Accessor", accessor.memberString("value"),
                        method, targetData, failures);
            }
            for (ClassFileAnnotations.AnnotationData invoker : ofType(method.annotations(), INVOKER)) {
                verified += verifyExplicitName(context, "Invoker", invoker.memberString("value"),
                        method, targetData, failures);
            }
        }

        for (ClassFileAnnotations.MemberData field : data.fields()) {
            for (ClassFileAnnotations.AnnotationData shadow : ofType(field.annotations(), SHADOW)) {
                Set<String> names = new HashSet<>();
                names.add(field.name());
                names.addAll(shadow.memberStrings("aliases"));
                verified++;
                for (String name : names) {
                    if (!anyTargetHasField(targetData, name)) {
                        failures.add(context + ": @Shadow field not found: '" + name + "'");
                    }
                }
            }
        }
        return verified;
    }

    private static List<ClassFileAnnotations.AnnotationData> ofType(
            List<ClassFileAnnotations.AnnotationData> list, String type) {
        List<ClassFileAnnotations.AnnotationData> out = new ArrayList<>();
        for (ClassFileAnnotations.AnnotationData a : list) {
            if (a.type().equals(type)) out.add(a);
        }
        return out;
    }

    private static List<ClassFileAnnotations.AnnotationData> injections(ClassFileAnnotations.MemberData method) {
        List<ClassFileAnnotations.AnnotationData> out = new ArrayList<>();
        for (String type : List.of(INJECT, REDIRECT, MODIFY_ARG, MODIFY_ARGS, MODIFY_VARIABLE, MODIFY_CONSTANT)) {
            out.addAll(ofType(method.annotations(), type));
        }
        return out;
    }

    private static int verifyExplicitName(String context, String kind, String value,
                                          ClassFileAnnotations.MemberData method,
                                          List<ClassFileAnnotations.ClassData> targets,
                                          List<String> failures) {
        // Without an explicit value the name mapping rules are complex (get/set/is prefix stripping),
        // so skip the check rather than report a false miss
        if (value == null || value.isEmpty()) return 0;
        boolean found = anyTargetHasMethod(targets, value) || anyTargetHasField(targets, value);
        if (!found) {
            failures.add(context + ": @" + kind + " target not found: '" + value + "'");
        }
        return 1;
    }

    /**
     * At member reference: INVOKE means a method (descriptor included), FIELD means a field. Other
     * shift types have no member.
     */
    private String verifyAt(String context, String methodName, ClassFileAnnotations.AnnotationData at) {
        String target = at.memberString("target");
        String value = at.memberString("value");
        if (target == null || target.isBlank()) return null;
        if (value != null && INVOKE_ATS.contains(value)) {
            MemberRef ref = MemberRef.parse(target);
            if (ref == null) {
                return context + ": unparseable @At INVOKE target '" + target + "' on " + methodName;
            }
            List<ClassFileAnnotations.ClassData> owners = new ArrayList<>();
            if (ref.owner() != null) {
                ClassFileAnnotations.ClassData owner = targetClass(ref.owner());
                if (owner == null) {
                    return context + ": @At INVOKE owner not on classpath: '" + ref.owner() + "'";
                }
                owners.add(owner);
            }
            return memberExists(owners, ref)
                    ? null
                    : context + ": @At INVOKE target not found: '" + target + "' on " + methodName;
        }
        if ("FIELD".equals(value)) {
            // Lowner;name:type or a bare field name
            String name = target;
            int colon = target.lastIndexOf(':');
            if (colon >= 0) name = target.substring(0, colon);
            String owner = null;
            int semi = name.lastIndexOf(';');
            if (semi >= 0) {
                owner = name.substring(0, semi);
                if (owner.startsWith("L")) owner = owner.substring(1);
                name = name.substring(semi + 1);
            }
            List<ClassFileAnnotations.ClassData> owners = new ArrayList<>();
            if (owner != null) {
                ClassFileAnnotations.ClassData ownerData = targetClass(owner);
                if (ownerData == null) {
                    return context + ": @At FIELD owner not on classpath: '" + owner + "'";
                }
                owners.add(ownerData);
            }
            return anyTargetHasField(owners, name)
                    ? null
                    : context + ": @At FIELD target not found: '" + target + "' on " + methodName;
        }
        return null;
    }

    // ==================== Target lookup ====================

    private boolean memberExists(List<ClassFileAnnotations.ClassData> targets, MemberRef ref) {
        for (ClassFileAnnotations.ClassData target : targets) {
            for (ClassFileAnnotations.MemberData m : target.methods()) {
                if (!m.name().equals(ref.name())) continue;
                // No descriptor or an incomplete one: a name match is enough. A complete descriptor must match exactly.
                if (ref.descriptor() == null || !ref.descriptor().contains(")")) return true;
                if (m.descriptor().equals(ref.descriptor())) return true;
            }
        }
        return false;
    }

    private static boolean anyTargetHasMethod(List<ClassFileAnnotations.ClassData> targets, String name) {
        for (ClassFileAnnotations.ClassData target : targets) {
            for (ClassFileAnnotations.MemberData m : target.methods()) {
                if (m.name().equals(name)) return true;
            }
        }
        return false;
    }

    private static boolean anyTargetHasField(List<ClassFileAnnotations.ClassData> targets, String name) {
        for (ClassFileAnnotations.ClassData target : targets) {
            for (ClassFileAnnotations.MemberData f : target.fields()) {
                if (f.name().equals(name)) return true;
            }
        }
        return false;
    }

    private static String normalizeInternalName(String raw) {
        String s = raw.trim();
        if (s.startsWith("L") && s.endsWith(";")) s = s.substring(1, s.length() - 1);
        return s.replace('.', '/');
    }

    private ClassFileAnnotations.ClassData targetClass(String internalName) {
        String key = normalizeInternalName(internalName);
        ClassFileAnnotations.ClassData cached = targetCache.get(key);
        if (cached == null) {
            cached = readClass(key);
            if (cached != null) targetCache.put(key, cached);
        }
        return cached;
    }

    // ==================== Member reference parsing ====================

    /**
     * Mixin member reference: {@code Lowner;name(desc)ret} / {@code name(desc)ret} / {@code name}.
     * The field form ({@code owner;name:type}) is handled by the FIELD branch.
     */
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

    // ==================== Byte reading ====================

    private static ClassFileAnnotations.ClassData readClass(String internalName) {
        InputStream in = MixinTargetResolutionTest.class.getClassLoader()
                .getResourceAsStream(internalName + ".class");
        if (in == null) return null;
        try (InputStream bytes = in) {
            return ClassFileAnnotations.parse(internalName, bytes);
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse " + internalName, e);
        }
    }

    private static JsonObject readConfig(String resource) throws Exception {
        InputStream in = MixinTargetResolutionTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull(in, resource + " must be on the test classpath");
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static List<String> sectionNames(JsonObject root, String section) {
        JsonElement element = root.get(section);
        if (element == null) return List.of();
        JsonArray array = element.getAsJsonArray();
        List<String> names = new ArrayList<>();
        for (JsonElement e : array) names.add(e.getAsString());
        return names;
    }
}
//?}
