package com.tkisor.nekujs.audit;

import com.google.gson.GsonBuilder;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnnotationRemapper;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.FieldRemapper;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.RecordComponentRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;

/** Inventories legacy linkage without defining or executing any addon class. */
public final class AddonCompatibilityAudit {
    private static final String KUBEJS = "dev/latvian/mods/kubejs/";
    private static final String RHINO = "dev/latvian/mods/rhino/";

    private AddonCompatibilityAudit() {}

    public record MemberReference(String kind, String owner, String name, String descriptor)
            implements Comparable<MemberReference> {
        @Override
        public int compareTo(MemberReference other) {
            int order = kind.compareTo(other.kind);
            if (order == 0) order = owner.compareTo(other.owner);
            if (order == 0) order = name.compareTo(other.name);
            if (order == 0) order = descriptor.compareTo(other.descriptor);
            return order;
        }
    }

    public record JarReport(String file, String sha256, int classes, String neoForgeMetadata,
                            List<String> pluginEntrypoints, List<String> recipeSchemas,
                            List<String> nestedJars, List<String> legacyTypes,
                            List<String> legacyTypesNotProvidedByFixtures,
                            List<MemberReference> legacyMembers) {}

    public record Report(int formatVersion, String runtimeCompatibility, boolean addonClassesExecuted,
                         List<String> limitations, List<JarReport> jars) {}

    /** Fails on absent/empty input or unreadable classes; partial scans are not successful reports. */
    public static Report inspect(Path directory) throws IOException {
        List<Path> paths;
        try (var files = Files.list(directory)) {
            paths = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted().toList();
        }
        if (paths.isEmpty()) {
            throw new IOException("[NEKO-4180] No fixture JARs found: " + directory);
        }
        Set<String> provided = new TreeSet<>();
        var scanned = new ArrayList<JarReport>();
        for (var path : paths) {
            scanned.add(inspectJar(path, provided));
        }
        var jars = new ArrayList<JarReport>();
        for (var jar : scanned) {
            jars.add(new JarReport(jar.file(), jar.sha256(), jar.classes(), jar.neoForgeMetadata(),
                    jar.pluginEntrypoints(), jar.recipeSchemas(), jar.nestedJars(), jar.legacyTypes(),
                    jar.legacyTypes().stream().filter(type -> !provided.contains(type)).toList(),
                    jar.legacyMembers()));
        }
        return new Report(1, "NOT_VERIFIED", false, List.of(
                "Static linkage inventory is not a compatibility pass or a complete linker check.",
                "Nested JARs are listed, not recursively inspected.",
                "Reflective class names, script execution, mixin application and transitive dependency resolution are not verified.",
                "Types absent from fixtures may be supplied by the game, a loader or other runtime dependencies.",
                "NekuJS does not currently provide the legacy KubeJS/Rhino ABI or the kubejs mod identity."
        ), List.copyOf(jars));
    }

    private static JarReport inspectJar(Path path, Set<String> provided) throws IOException {
        var types = new TreeSet<String>();
        var members = new TreeSet<MemberReference>();
        var recipes = new TreeSet<String>();
        var nested = new TreeSet<String>();
        var plugins = new TreeSet<String>();
        String metadata = "";
        int classes = 0;
        try (var jar = new JarFile(path.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.endsWith(".class")) {
                    try (var input = jar.getInputStream(entry)) {
                        var reader = new ClassReader(input);
                        provided.add(reader.getClassName());
                        reader.accept(new LegacyClassRemapper(new LegacyReferences(types, members)),
                                ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                        classes++;
                    } catch (RuntimeException failure) {
                        throw new IOException("[NEKO-4181] Class scan rejected: " + path + "!/" + name, failure);
                    }
                } else if (name.equals("kubejs.plugins.txt")) {
                    try (var input = jar.getInputStream(entry)) {
                        new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
                                .map(line -> line.split("#", 2)[0].strip()).filter(line -> !line.isEmpty())
                                .forEach(plugins::add);
                    }
                } else if (name.equals("META-INF/neoforge.mods.toml")) {
                    try (var input = jar.getInputStream(entry)) {
                        metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    }
                } else if (name.startsWith("data/") && name.contains("/kubejs/recipe_schema/")
                        && name.endsWith(".json")) {
                    recipes.add(name);
                } else if (name.endsWith(".jar")) {
                    nested.add(name);
                }
            }
        }
        return new JarReport(path.getFileName().toString(), sha256(path), classes, metadata,
                List.copyOf(plugins), List.copyOf(recipes), List.copyOf(nested), List.copyOf(types),
                List.of(), List.copyOf(members));
    }

    private static boolean legacy(String name) {
        return name.startsWith(KUBEJS) || name.startsWith(RHINO);
    }

    // Keep ASM's signature/instruction traversal; all annotation sites share the enum-member visitor.
    private static final class LegacyClassRemapper extends ClassRemapper {
        private LegacyClassRemapper(Remapper remapper) {
            super(Opcodes.ASM9, new ClassWriter(0), remapper);
        }

        @Override
        protected AnnotationVisitor createAnnotationRemapper(String descriptor, AnnotationVisitor visitor) {
            return new LegacyAnnotationRemapper(descriptor, visitor, remapper);
        }

        @Override
        protected FieldVisitor createFieldRemapper(FieldVisitor visitor) {
            return new FieldRemapper(Opcodes.ASM9, visitor, remapper) {
                @Override
                protected AnnotationVisitor createAnnotationRemapper(String descriptor, AnnotationVisitor annotation) {
                    return new LegacyAnnotationRemapper(descriptor, annotation, remapper);
                }
            };
        }

        @Override
        protected MethodVisitor createMethodRemapper(MethodVisitor visitor) {
            return new MethodRemapper(Opcodes.ASM9, visitor, remapper) {
                @Override
                protected AnnotationVisitor createAnnotationRemapper(String descriptor, AnnotationVisitor annotation) {
                    return new LegacyAnnotationRemapper(descriptor, annotation, remapper);
                }
            };
        }

        @Override
        protected RecordComponentVisitor createRecordComponentRemapper(RecordComponentVisitor visitor) {
            return new RecordComponentRemapper(Opcodes.ASM9, visitor, remapper) {
                @Override
                protected AnnotationVisitor createAnnotationRemapper(String descriptor, AnnotationVisitor annotation) {
                    return new LegacyAnnotationRemapper(descriptor, annotation, remapper);
                }
            };
        }
    }

    private static final class LegacyAnnotationRemapper extends AnnotationRemapper {
        private LegacyAnnotationRemapper(String descriptor, AnnotationVisitor visitor, Remapper remapper) {
            super(Opcodes.ASM9, descriptor, visitor, remapper);
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            // ASM maps the enum type but leaves its constant name untouched, so record the field explicitly.
            remapper.mapFieldName(Type.getType(descriptor).getInternalName(), value, descriptor);
            super.visitEnum(name, descriptor, value);
        }

        @Override
        protected AnnotationVisitor createAnnotationRemapper(String descriptor, AnnotationVisitor visitor) {
            return new LegacyAnnotationRemapper(descriptor, visitor, remapper);
        }
    }

    // ASM remapping visits signatures, annotations, instructions and bootstrap handles without loading types.
    private static final class LegacyReferences extends Remapper {
        private final Set<String> types;
        private final Set<MemberReference> members;

        private LegacyReferences(Set<String> types, Set<MemberReference> members) {
            this.types = types;
            this.members = members;
        }

        @Override
        public String map(String internalName) {
            if (legacy(internalName)) types.add(internalName);
            return internalName;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            if (legacy(owner)) {
                types.add(owner);
                members.add(new MemberReference("METHOD", owner, name, descriptor));
            }
            return name;
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            if (legacy(owner)) {
                types.add(owner);
                members.add(new MemberReference("FIELD", owner, name, descriptor));
            }
            return name;
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("[NEKO-4182] SHA-256 unavailable", impossible);
        }
    }

    /** Arguments: fixture directory and report path. */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("[NEKO-4183] Expected fixture directory and report path");
        }
        var report = inspect(Path.of(args[0]));
        var output = Path.of(args[1]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report)
                + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println("Inspected " + report.jars().size() + " JARs; runtime compatibility NOT_VERIFIED; report=" + output);
    }
}
