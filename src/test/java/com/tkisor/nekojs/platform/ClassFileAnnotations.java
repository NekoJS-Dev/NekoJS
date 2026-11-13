package com.tkisor.nekojs.platform;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal classfile annotation reader: parses the constant pool and the annotation attributes of
 * .class bytes directly.
 *
 * <p>Why not reflection: ModDev's test environment rewrites annotation class definitions in memory
 * with a javaagent (mixin classes lose their annotations in the test JVM while the bytes on disk
 * stay intact). ASM is not an option either, it is unreliable on the test classpath.
 * {@code getResourceAsStream} returns the raw file bytes, which the agent does not touch, and it
 * sees both RuntimeVisible and RuntimeInvisible (CLASS retention) annotations.
 *
 * <p>Only the annotation shapes the mixin checks need are supported: String / Class / enum /
 * nested annotation / arrays of those. Every other element_value type parses as null and is
 * skipped.
 */
final class ClassFileAnnotations {

    /**
     * One annotation: the type as an internal name (for example
     * {@code Lorg/spongepowered/asm/mixin/Mixin;}) plus member name to value.
     */
    record AnnotationData(String type, Map<String, Object> values) {
        String memberString(String name) {
            Object v = values.get(name);
            return v instanceof String s ? s : null;
        }

        @SuppressWarnings("unchecked")
        List<String> memberStrings(String name) {
            Object v = values.get(name);
            if (v instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object o : list) if (o instanceof String s) out.add(s);
                return out;
            }
            return List.of();
        }

        AnnotationData memberAnnotation(String name) {
            Object v = values.get(name);
            return v instanceof AnnotationData a ? a : null;
        }
    }

    record MemberData(String name, String descriptor, List<AnnotationData> annotations) {}

    record ClassData(String internalName, List<AnnotationData> annotations,
                     List<MemberData> fields, List<MemberData> methods) {

        public List<AnnotationData> annotationsOfType(String internalName) {
            List<AnnotationData> out = new ArrayList<>();
            for (AnnotationData a : annotations) if (a.type().equals(internalName)) out.add(a);
            return out;
        }
    }

    /** Reads and parses one class byte stream; {@code internalName} is used in error messages only. */
    static ClassData parse(String internalName, InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(in);
        if (d.readInt() != 0xCAFEBABE) {
            throw new IOException(internalName + ": not a class file");
        }
        d.readUnsignedShort(); // minor
        d.readUnsignedShort(); // major
        String[] pool = readConstantPool(d);

        d.readUnsignedShort(); // access flags
        int thisClass = d.readUnsignedShort();
        d.readUnsignedShort(); // super
        int interfaces = d.readUnsignedShort();
        for (int i = 0; i < interfaces; i++) d.readUnsignedShort();

        List<MemberData> fields = readMembers(d, pool);
        List<MemberData> methods = readMembers(d, pool);
        List<AnnotationData> classAnnotations = readClassAttributes(d, pool);

        String name = pool[thisClass];
        if (name != null && name.startsWith("L") && name.endsWith(";")) {
            name = name.substring(1, name.length() - 1);
        }
        return new ClassData(name, classAnnotations, fields, methods);
    }

    private static String[] readConstantPool(DataInputStream d) throws IOException {
        int count = d.readUnsignedShort();
        String[] pool = new String[count];
        for (int i = 1; i < count; i++) {
            int tag = d.readUnsignedByte();
            switch (tag) {
                case 1 -> pool[i] = d.readUTF();
                case 3, 4 -> d.skipBytes(4);            // Integer/Float
                case 5, 6 -> { d.skipBytes(8); i++; }   // Long/Double take two constant pool slots
                case 7, 8, 16, 19, 20 -> d.skipBytes(2); // Class/String/MethodType/Module/Package
                case 9, 10, 11, 12, 17, 18 -> d.skipBytes(4); // ref, NameAndType and Dynamic entries
                case 15 -> d.skipBytes(3);              // MethodHandle
                default -> throw new IOException("unknown constant pool tag " + tag);
            }
        }
        return pool;
    }

    private static List<MemberData> readMembers(DataInputStream d, String[] pool) throws IOException {
        int count = d.readUnsignedShort();
        List<MemberData> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            d.readUnsignedShort(); // access
            String name = pool[d.readUnsignedShort()];
            String descriptor = pool[d.readUnsignedShort()];
            List<AnnotationData> annotations = readMemberAttributes(d, pool);
            out.add(new MemberData(name, descriptor, annotations));
        }
        return out;
    }

    private static List<AnnotationData> readMemberAttributes(DataInputStream d, String[] pool) throws IOException {
        return readAttributes(d, pool, false);
    }

    private static List<AnnotationData> readClassAttributes(DataInputStream d, String[] pool) throws IOException {
        return readAttributes(d, pool, true);
    }

    private static List<AnnotationData> readAttributes(DataInputStream d, String[] pool, boolean classLevel)
            throws IOException {
        List<AnnotationData> out = new ArrayList<>();
        int count = d.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            String name = pool[d.readUnsignedShort()];
            int len = d.readInt();
            if (!classLevel && ("RuntimeVisibleAnnotations".equals(name) || "RuntimeInvisibleAnnotations".equals(name))) {
                out.addAll(readAnnotationArray(new DataInputStream(
                        new ByteArrayInputStream(readFully(d, len))), pool));
            } else if (classLevel && ("RuntimeVisibleAnnotations".equals(name) || "RuntimeInvisibleAnnotations".equals(name))) {
                out.addAll(readAnnotationArray(new DataInputStream(
                        new ByteArrayInputStream(readFully(d, len))), pool));
            } else {
                d.skipBytes(len);
            }
        }
        return out;
    }

    private static byte[] readFully(DataInputStream d, int len) throws IOException {
        byte[] bytes = new byte[len];
        d.readFully(bytes);
        return bytes;
    }

    private static List<AnnotationData> readAnnotationArray(DataInputStream d, String[] pool) throws IOException {
        int count = d.readUnsignedShort();
        List<AnnotationData> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            AnnotationData a = readAnnotation(d, pool);
            if (a != null) out.add(a);
        }
        return out;
    }

    private static AnnotationData readAnnotation(DataInputStream d, String[] pool) throws IOException {
        String type = pool[d.readUnsignedShort()];
        int pairs = d.readUnsignedShort();
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs; i++) {
            String member = pool[d.readUnsignedShort()];
            values.put(member, readElementValue(d, pool));
        }
        return new AnnotationData(type, values);
    }

    private static Object readElementValue(DataInputStream d, String[] pool) throws IOException {
        int tag = d.readUnsignedByte();
        return switch (tag) {
            case 's' -> pool[d.readUnsignedShort()];
            case 'e' -> {
                d.readUnsignedShort(); // enum type name
                yield pool[d.readUnsignedShort()]; // enum constant name
            }
            case 'c' -> pool[d.readUnsignedShort()]; // internal name
            case '@' -> readAnnotation(d, pool);
            case '[' -> {
                int n = d.readUnsignedShort();
                List<Object> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(readElementValue(d, pool));
                yield list;
            }
            // B/C/D/F/I/J/S/Z: const_value_index is still a u2 and must be consumed or the stream desyncs
            // (the Z values with remap=false land here)
            case 'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z' -> {
                d.skipBytes(2);
                yield null;
            }
            default -> null;
        };
    }

    private ClassFileAnnotations() {}
}
