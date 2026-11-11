package com.tkisor.nekujs.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypeReference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class AddonCompatibilityAuditTest {
    private static final String ENUM_OWNER = "dev/latvian/mods/kubejs/script/ScriptType";
    private static final String ENUM_DESCRIPTOR = "L" + ENUM_OWNER + ";";
    private static final String ANNOTATION_DESCRIPTOR = "Lfixture/Marker;";

    @TempDir Path directory;

    @Test
    void scansMethodBodiesSignaturesAndBootstrapHandlesWithoutLoadingClasses() throws IOException {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "fixture/Plugin", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "typed", "Ljava/util/List;",
                "Ljava/util/List<Ldev/latvian/mods/rhino/type/TypeInfo;>;", null).visitEnd();
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()V", null, null);
        method.visitCode();
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/latvian/mods/kubejs/event/EventGroup", "of",
                "(Ljava/lang/String;)Ldev/latvian/mods/kubejs/event/EventGroup;", false);
        method.visitFieldInsn(Opcodes.GETSTATIC, "dev/latvian/mods/kubejs/script/ScriptType", "SERVER",
                "Ldev/latvian/mods/kubejs/script/ScriptType;");
        method.visitLdcInsn(Type.getType("[Ldev/latvian/mods/rhino/Context;"));
        method.visitInvokeDynamicInsn("call", "()Ljava/lang/Runnable;",
                new Handle(Opcodes.H_INVOKESTATIC, "fixture/Bootstrap", "make", "()Ljava/lang/invoke/CallSite;", false),
                new Handle(Opcodes.H_INVOKESTATIC, "dev/latvian/mods/kubejs/util/Example", "callback", "()V", false));
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(4, 0);
        method.visitEnd();
        writer.visitEnd();
        writeJar("fixture.jar", Map.of("fixture/Plugin.class", writer.toByteArray()));
        var report = AddonCompatibilityAudit.inspect(directory);
        assertFalse(report.addonClassesExecuted());
        assertEquals("NOT_VERIFIED", report.runtimeCompatibility());
        var jar = report.jars().getFirst();
        assertTrue(jar.legacyTypes().contains("dev/latvian/mods/rhino/type/TypeInfo"));
        assertTrue(jar.legacyTypes().contains("dev/latvian/mods/rhino/Context"));
        assertTrue(jar.legacyMembers().stream().anyMatch(member -> member.name().equals("of")
                && member.descriptor().equals("(Ljava/lang/String;)Ldev/latvian/mods/kubejs/event/EventGroup;")));
        assertTrue(jar.legacyMembers().stream().anyMatch(member -> member.kind().equals("FIELD") && member.name().equals("SERVER")));
        assertTrue(jar.legacyMembers().stream().anyMatch(member -> member.name().equals("callback")));
    }

    @Test
    void inventoriesEnumFieldsAcrossDeclarationAndCodeAnnotationSites() throws IOException {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_RECORD,
                "fixture/AnnotatedRecord", null, "java/lang/Record", null);
        enumValue(writer.visitAnnotation(ANNOTATION_DESCRIPTOR, true), "CLASS");
        enumValue(writer.visitTypeAnnotation(TypeReference.newSuperTypeReference(-1).getValue(),
                null, ANNOTATION_DESCRIPTOR, false), "CLASS_TYPE");
        var field = writer.visitField(Opcodes.ACC_PRIVATE, "value", "Ljava/lang/String;", null, null);
        enumValue(field.visitAnnotation(ANNOTATION_DESCRIPTOR, false), "FIELD");
        enumValue(field.visitTypeAnnotation(TypeReference.newTypeReference(TypeReference.FIELD).getValue(),
                null, ANNOTATION_DESCRIPTOR, true), "FIELD_TYPE");
        field.visitEnd();
        var component = writer.visitRecordComponent("value", "Ljava/lang/String;", null);
        enumValue(component.visitAnnotation(ANNOTATION_DESCRIPTOR, true), "RECORD");
        enumValue(component.visitTypeAnnotation(TypeReference.newTypeReference(TypeReference.FIELD).getValue(),
                null, ANNOTATION_DESCRIPTOR, false), "RECORD_TYPE");
        component.visitEnd();

        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run",
                "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        enumValue(method.visitAnnotation(ANNOTATION_DESCRIPTOR, false), "METHOD");
        enumValue(method.visitTypeAnnotation(TypeReference.newTypeReference(TypeReference.METHOD_RETURN).getValue(),
                null, ANNOTATION_DESCRIPTOR, true), "METHOD_TYPE");
        enumValue(method.visitParameterAnnotation(0, ANNOTATION_DESCRIPTOR, true), "PARAMETER");
        enumValue(method.visitTypeAnnotation(TypeReference.newFormalParameterReference(0).getValue(),
                null, ANNOTATION_DESCRIPTOR, false), "PARAMETER_TYPE");
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();
        method.visitCode();
        method.visitTryCatchBlock(start, end, handler, "java/lang/RuntimeException");
        enumValue(method.visitTryCatchAnnotation(TypeReference.newTryCatchReference(0).getValue(),
                null, ANNOTATION_DESCRIPTOR, true), "TRY_CATCH");
        method.visitLabel(start);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
        enumValue(method.visitInsnAnnotation(TypeReference.newTypeArgumentReference(TypeReference.CAST, 0).getValue(),
                null, ANNOTATION_DESCRIPTOR, false), "INSTRUCTION");
        method.visitLabel(end);
        method.visitInsn(Opcodes.ARETURN);
        method.visitLabel(handler);
        method.visitInsn(Opcodes.ATHROW);
        enumValue(method.visitLocalVariableAnnotation(TypeReference.newTypeReference(TypeReference.LOCAL_VARIABLE).getValue(),
                null, new Label[]{start}, new Label[]{end}, new int[]{0}, ANNOTATION_DESCRIPTOR, true), "LOCAL_VARIABLE");
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();
        writeJar("annotations.jar", Map.of("fixture/AnnotatedRecord.class", writer.toByteArray()));

        var report = AddonCompatibilityAudit.inspect(directory);
        assertFalse(report.addonClassesExecuted());
        var jar = report.jars().getFirst();
        assertTrue(jar.legacyTypes().contains(ENUM_OWNER));
        assertEquals(enumMembers("CLASS", "CLASS_TYPE", "FIELD", "FIELD_TYPE", "RECORD", "RECORD_TYPE",
                "METHOD", "METHOD_TYPE", "PARAMETER", "PARAMETER_TYPE", "TRY_CATCH", "INSTRUCTION", "LOCAL_VARIABLE"),
                jar.legacyMembers());
    }

    @Test
    void inventoriesEnumFieldsInDefaultsNestedAnnotationsAndArrays() throws IOException {
        var holder = new ClassWriter(0);
        holder.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "fixture/Annotated", null, "java/lang/Object", null);
        var annotation = holder.visitAnnotation(ANNOTATION_DESCRIPTOR, true);
        enumValue(annotation.visitAnnotation("nested", ANNOTATION_DESCRIPTOR), "NESTED");
        var array = annotation.visitArray("choices");
        array.visitEnum(null, ENUM_DESCRIPTOR, "ARRAY");
        enumValue(array.visitAnnotation(null, ANNOTATION_DESCRIPTOR), "ARRAY_NESTED");
        array.visitEnd();
        var nested = annotation.visitAnnotation("container", ANNOTATION_DESCRIPTOR);
        var nestedArray = nested.visitArray("choices");
        nestedArray.visitEnum(null, ENUM_DESCRIPTOR, "NESTED_ARRAY");
        nestedArray.visitEnd();
        nested.visitEnd();
        annotation.visitEnum("ignored", "Ljava/lang/annotation/RetentionPolicy;", "RUNTIME");
        annotation.visitEnd();
        holder.visitEnd();

        var defaults = new ClassWriter(0);
        defaults.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION,
                "fixture/Defaults", null, "java/lang/Object", new String[]{"java/lang/annotation/Annotation"});
        var single = defaults.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "single",
                "()" + ENUM_DESCRIPTOR, null, null);
        var singleDefault = single.visitAnnotationDefault();
        singleDefault.visitEnum(null, ENUM_DESCRIPTOR, "DEFAULT");
        singleDefault.visitEnd();
        single.visitEnd();
        var multiple = defaults.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "multiple",
                "()[" + ENUM_DESCRIPTOR, null, null);
        var arrayDefault = multiple.visitAnnotationDefault();
        var defaultValues = arrayDefault.visitArray(null);
        defaultValues.visitEnum(null, ENUM_DESCRIPTOR, "DEFAULT_ARRAY");
        defaultValues.visitEnd();
        arrayDefault.visitEnd();
        multiple.visitEnd();
        var nestedMethod = defaults.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "nested",
                "()" + ANNOTATION_DESCRIPTOR, null, null);
        var nestedDefault = nestedMethod.visitAnnotationDefault();
        enumValue(nestedDefault.visitAnnotation(null, ANNOTATION_DESCRIPTOR), "DEFAULT_NESTED");
        nestedDefault.visitEnd();
        nestedMethod.visitEnd();
        var rhinoMethod = defaults.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "rhino",
                "()Ldev/latvian/mods/rhino/EnumKind;", null, null);
        var rhinoDefault = rhinoMethod.visitAnnotationDefault();
        rhinoDefault.visitEnum(null, "Ldev/latvian/mods/rhino/EnumKind;", "RHINO_DEFAULT");
        rhinoDefault.visitEnd();
        rhinoMethod.visitEnd();
        defaults.visitEnd();
        writeJar("nested-annotations.jar", Map.of("fixture/Annotated.class", holder.toByteArray(),
                "fixture/Defaults.class", defaults.toByteArray()));

        var report = AddonCompatibilityAudit.inspect(directory);
        assertFalse(report.addonClassesExecuted());
        var expected = new java.util.ArrayList<>(enumMembers("NESTED", "ARRAY", "ARRAY_NESTED", "NESTED_ARRAY",
                "DEFAULT", "DEFAULT_ARRAY", "DEFAULT_NESTED"));
        expected.add(new AddonCompatibilityAudit.MemberReference("FIELD", "dev/latvian/mods/rhino/EnumKind",
                "RHINO_DEFAULT", "Ldev/latvian/mods/rhino/EnumKind;"));
        assertEquals(expected.stream().sorted().toList(), report.jars().getFirst().legacyMembers());
        assertEquals(report, AddonCompatibilityAudit.inspect(directory));
    }

    private static void enumValue(AnnotationVisitor annotation, String constant) {
        annotation.visitEnum("value", ENUM_DESCRIPTOR, constant);
        annotation.visitEnd();
    }

    private static List<AddonCompatibilityAudit.MemberReference> enumMembers(String... constants) {
        return java.util.Arrays.stream(constants)
                .map(name -> new AddonCompatibilityAudit.MemberReference("FIELD", ENUM_OWNER, name, ENUM_DESCRIPTOR))
                .sorted().toList();
    }

    @Test
    void inventoriesResourcesAndRecognizesTypesSuppliedByOtherFixtures() throws IOException {
        String plugin = "dev/latvian/mods/kubejs/plugin/KubeJSPlugin";
        writeJar("a.jar", Map.of(
                "fixture/Consumer.class", emptyClass("fixture/Consumer", plugin),
                "kubejs.plugins.txt", "# Header\nfixture.Plugin mekanism # inline comment\n\n".getBytes(StandardCharsets.UTF_8),
                "META-INF/neoforge.mods.toml", "metadata".getBytes(StandardCharsets.UTF_8),
                "data/mekanism/kubejs/recipe_schema/crushing.json", "{}".getBytes(StandardCharsets.UTF_8),
                "META-INF/jarjar/nested.jar", new byte[0]));
        writeJar("b.jar", Map.of(plugin + ".class", emptyClass(plugin, "java/lang/Object")));
        var report = AddonCompatibilityAudit.inspect(directory);
        var jar = report.jars().getFirst();
        assertEquals("a.jar", jar.file());
        assertEquals("metadata", jar.neoForgeMetadata());
        assertEquals(java.util.List.of("fixture.Plugin mekanism"), jar.pluginEntrypoints());
        assertEquals(1, jar.recipeSchemas().size());
        assertEquals(1, jar.nestedJars().size());
        assertTrue(jar.legacyTypes().contains(plugin));
        assertFalse(jar.legacyTypesNotProvidedByFixtures().contains(plugin));
        assertEquals(64, jar.sha256().length());
        assertEquals(report, AddonCompatibilityAudit.inspect(directory));
    }

    @Test
    void rejectsEmptyDirectoriesAndCorruptClassEntries() throws IOException {
        assertThrows(IOException.class, () -> AddonCompatibilityAudit.inspect(directory));
        writeJar("bad.jar", Map.of("Broken.class", new byte[]{0}));
        var error = assertThrows(IOException.class, () -> AddonCompatibilityAudit.inspect(directory));
        assertTrue(error.getMessage().contains("[NEKO-4181]"));
        assertNotNull(error.getCause());
    }

    @Test
    void rejectsMissingDirectory() {
        assertThrows(IOException.class, () -> AddonCompatibilityAudit.inspect(directory.resolve("absent")));
    }

    private static byte[] emptyClass(String name, String parent) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, parent, null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private void writeJar(String name, Map<String, byte[]> entries) throws IOException {
        try (var output = new JarOutputStream(Files.newOutputStream(directory.resolve(name)))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }
}
