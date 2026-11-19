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
 * <p>The parser reads strings, classes, enums, integers, nested annotations, and arrays of those.
 * Other annotation values are skipped.
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

        int memberInt(String name, int fallback) {
            Object v = values.get(name);
            return v instanceof Integer number ? number : fallback;
        }

        List<String> memberStrings(String name) {
            Object v = values.get(name);
            if (v instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object o : list) if (o instanceof String s) out.add(s);
                return out;
            }
            return List.of();
        }

        List<AnnotationData> memberAnnotations(String name) {
            Object value = values.get(name);
            if (value instanceof AnnotationData annotation) {
                return List.of(annotation);
            }
            if (value instanceof List<?> list) {
                return list.stream().filter(AnnotationData.class::isInstance)
                        .map(AnnotationData.class::cast).toList();
            }
            return List.of();
        }
    }

    record MemberData(String name, String descriptor, List<AnnotationData> annotations) {}

    record ClassData(String internalName, List<String> supertypes, List<AnnotationData> annotations,
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
        Object[] pool = readConstantPool(d);

        d.readUnsignedShort(); // access flags
        String name = className(pool, d.readUnsignedShort());
        int superClass = d.readUnsignedShort();
        List<String> supertypes = new ArrayList<>();
        if (superClass != 0) {
            supertypes.add(className(pool, superClass));
        }
        int interfaces = d.readUnsignedShort();
        for (int i = 0; i < interfaces; i++) {
            supertypes.add(className(pool, d.readUnsignedShort()));
        }

        List<MemberData> fields = readMembers(d, pool);
        List<MemberData> methods = readMembers(d, pool);
        List<AnnotationData> classAnnotations = readClassAttributes(d, pool);

        return new ClassData(name, supertypes, classAnnotations, fields, methods);
    }

    private record ClassRef(int nameIndex) {}

    private static String className(Object[] pool, int index) {
        return (String) pool[((ClassRef) pool[index]).nameIndex()];
    }

    private static Object[] readConstantPool(DataInputStream d) throws IOException {
        int count = d.readUnsignedShort();
        Object[] pool = new Object[count];
        for (int i = 1; i < count; i++) {
            int tag = d.readUnsignedByte();
            switch (tag) {
                case 1 -> pool[i] = d.readUTF();
                case 3 -> pool[i] = d.readInt();
                case 4 -> d.skipBytes(4);                // Float
                case 5, 6 -> { d.skipBytes(8); i++; }   // Long/Double take two constant pool slots
                case 7 -> pool[i] = new ClassRef(d.readUnsignedShort());
                case 8, 16, 19, 20 -> d.skipBytes(2);    // String/MethodType/Module/Package
                case 9, 10, 11, 12, 17, 18 -> d.skipBytes(4); // ref, NameAndType and Dynamic entries
                case 15 -> d.skipBytes(3);              // MethodHandle
                default -> throw new IOException("unknown constant pool tag " + tag);
            }
        }
        return pool;
    }

    private static List<MemberData> readMembers(DataInputStream d, Object[] pool) throws IOException {
        int count = d.readUnsignedShort();
        List<MemberData> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            d.readUnsignedShort(); // access
            String name = (String) pool[d.readUnsignedShort()];
            String descriptor = (String) pool[d.readUnsignedShort()];
            List<AnnotationData> annotations = readMemberAttributes(d, pool);
            out.add(new MemberData(name, descriptor, annotations));
        }
        return out;
    }

    private static List<AnnotationData> readMemberAttributes(DataInputStream d, Object[] pool) throws IOException {
        return readAttributes(d, pool, false);
    }

    private static List<AnnotationData> readClassAttributes(DataInputStream d, Object[] pool) throws IOException {
        return readAttributes(d, pool, true);
    }

    private static List<AnnotationData> readAttributes(DataInputStream d, Object[] pool, boolean classLevel)
            throws IOException {
        List<AnnotationData> out = new ArrayList<>();
        int count = d.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            String name = (String) pool[d.readUnsignedShort()];
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

    private static List<AnnotationData> readAnnotationArray(DataInputStream d, Object[] pool) throws IOException {
        int count = d.readUnsignedShort();
        List<AnnotationData> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            AnnotationData a = readAnnotation(d, pool);
            if (a != null) out.add(a);
        }
        return out;
    }

    private static AnnotationData readAnnotation(DataInputStream d, Object[] pool) throws IOException {
        String type = (String) pool[d.readUnsignedShort()];
        int pairs = d.readUnsignedShort();
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs; i++) {
            String member = (String) pool[d.readUnsignedShort()];
            values.put(member, readElementValue(d, pool));
        }
        return new AnnotationData(type, values);
    }

    private static Object readElementValue(DataInputStream d, Object[] pool) throws IOException {
        int tag = d.readUnsignedByte();
        return switch (tag) {
            case 'I', 's' -> pool[d.readUnsignedShort()];
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
            // These unused primitive values still carry a constant pool index.
            case 'B', 'C', 'D', 'F', 'J', 'S', 'Z' -> {
                d.skipBytes(2);
                yield null;
            }
            default -> null;
        };
    }

    private ClassFileAnnotations() {}
}
