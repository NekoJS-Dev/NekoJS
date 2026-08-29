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
 * 最小 classfile 注解读取器：直接解析 .class 字节的常量池与注解属性。
 *
 * <p>为什么不用反射：ModDev 的 test 环境会用 javaagent 在内存里改写注解类定义（本项目实测
 * mixin 类在 test JVM 里注解被清空，磁盘字节完好）；也不依赖 ASM（测试 classpath 上不可靠）。
 * {@code getResourceAsStream} 返回的是原始文件字节，不受 agent 影响，且 RuntimeVisible 与
 * RuntimeInvisible（CLASS retention，cleanroom 的 sponge-mixin 是这种）注解都读得到。
 *
 * <p>只覆盖 mixin 验证需要的注解形状：String / Class / 枚举 / 注解嵌套 / 上述类型的数组。
 * 其它 element_value 类型解析为 null 并跳过。
 */
final class ClassFileAnnotations {

    /** 一个注解：内部名类型（如 {@code Lorg/spongepowered/asm/mixin/Mixin;}）+ 成员名→值。 */
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

    /** 读取并解析一个 class 字节流（internalName 仅用于错误信息）。 */
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
                case 5, 6 -> { d.skipBytes(8); i++; }   // Long/Double 占双槽
                case 7, 8, 16, 19, 20 -> d.skipBytes(2); // Class/String/MethodType/Module/Package
                case 9, 10, 11, 12, 17, 18 -> d.skipBytes(4); // 各种 ref/NameAndType/Dynamic
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
                d.readUnsignedShort(); // 枚举类型名
                yield pool[d.readUnsignedShort()]; // 枚举常量名
            }
            case 'c' -> pool[d.readUnsignedShort()]; // 内部名
            case '@' -> readAnnotation(d, pool);
            case '[' -> {
                int n = d.readUnsignedShort();
                List<Object> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(readElementValue(d, pool));
                yield list;
            }
            // B/C/D/F/I/J/S/Z：const_value_index 仍占 u2，必须消费掉否则流错位（remap=false 的 Z 就在这）
            case 'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z' -> {
                d.skipBytes(2);
                yield null;
            }
            default -> null;
        };
    }

    private ClassFileAnnotations() {}
}
