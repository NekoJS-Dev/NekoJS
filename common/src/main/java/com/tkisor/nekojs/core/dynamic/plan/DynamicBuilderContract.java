package com.tkisor.nekojs.core.dynamic.plan;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态定义 builder 的公开成员契约（ticket 16，AC4/AC9）：以<b>反射</b>从 builder 类
 * 派生成员目录——同一份契约输入同时驱动脚本面 {@link DynamicBuilderSurface}
 * （runtime member：property 写入与显式 setter 调用转发到同一个 {@link Method}）、
 * definition fingerprint 的规范化读数、TS/Python declaration（经
 * {@link DynamicBuilderSurfaces} 派生的结构化条目），不手写第二份成员表。
 *
 * <p>分类规则与票 15 {@code RegistryBuilderContract} 同一约定（JavaBean 双形态，
 * 见 {@link DynamicDefinitionBuilder} 的命名决策）：
 * <ul>
 *   <li><b>可写属性</b>——{@code setXxx(T)} 与配套 {@code getXxx():T}（boolean 的
 *       {@code isXxx()}）同时存在。脚本端 {@code b.xxx = v}（ProxyObject putMember）
 *       与显式 {@code b.setXxx(v)}（方法成员）调用<b>同一个</b> setter
 *       {@link Method}，进入同一校验/规范化/指纹路径；</li>
 *   <li><b>只读属性</b>——有 getter 无 setter（动态 builder 当前没有，仍按规则分类）；</li>
 *   <li><b>方法</b>——其余 public 实例方法（含显式 setter 形态 {@code setXxx(v)} 本身）。</li>
 * </ul>
 * {@code toString} 等 Object 方法与 static 成员不进脚本面；反射结果按类缓存
 * （builder 类不可变）。
 */
public final class DynamicBuilderContract {

    private static final Map<Class<?>, DynamicBuilderContract> CACHE = new ConcurrentHashMap<>();

    /** 成员种类：可写属性（setter 双形态）/ 只读属性 / 方法（含显式 setter）。 */
    public enum MemberKind { WRITABLE_PROPERTY, READ_ONLY_PROPERTY, METHOD }

    /**
     * 单个契约成员。可写属性的 {@code setter()} 是唯一的 setter {@link Method}——property
     * 写入与显式调用共用（AC4 的结构保证）。
     */
    public record Member(String name, MemberKind kind, Class<?> valueType, Method getter, Method setter,
            List<Method> overloads) {

        Member(String name, MemberKind kind, Class<?> valueType, Method getter, Method setter) {
            this(name, kind, valueType, getter, setter, List.of());
        }

        /** TS 声明类型（确定性映射；未知类型 any）。 */
        public String tsType() {
            return tsTypeOf(valueType);
        }

        /** Python 声明类型（与 TS 同一输入派生）。 */
        public String pyType() {
            return pyTypeOf(valueType);
        }
    }

    private final Class<?> builderType;
    private final Map<String, Member> members;

    private DynamicBuilderContract(Class<?> builderType) {
        this.builderType = builderType;
        Map<String, Method> getters = new LinkedHashMap<>();
        Map<String, Method> setters = new LinkedHashMap<>();
        Map<String, List<Method>> rest = new LinkedHashMap<>();
        for (Method method : builderType.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.isBridge() || method.isSynthetic()) {
                continue;
            }
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class || name.equals("toString")) {
                continue;
            }
            if (isGetterName(name) && method.getParameterCount() == 0 && method.getReturnType() != void.class) {
                getters.put(propertyName(name), method);
            } else if (isSetterName(name) && method.getParameterCount() == 1) {
                setters.put(propertyName(name), method);
            } else {
                rest.computeIfAbsent(name, key -> new ArrayList<>()).add(method);
            }
        }
        Map<String, Member> result = new LinkedHashMap<>();
        Map<String, List<Method>> methodOverloads = new LinkedHashMap<>();
        getters.keySet().forEach(property -> {
            Method setter = setters.get(property);
            Method getter = getters.get(property);
            if (setter != null) {
                result.put(property, new Member(property, MemberKind.WRITABLE_PROPERTY,
                        setter.getParameterTypes()[0], getter, setter));
            } else {
                result.put(property, new Member(property, MemberKind.READ_ONLY_PROPERTY,
                        getter.getReturnType(), getter, null));
            }
        });
        // 显式 setter 形态（b.setXxx(v)）作为方法成员保留——与属性写入同一 Method
        setters.forEach((property, setter) -> methodOverloads
                .computeIfAbsent(setter.getName(), key -> new ArrayList<>()).add(setter));
        rest.forEach((name, overloads) -> methodOverloads
                .computeIfAbsent(name, key -> new ArrayList<>()).addAll(overloads));
        methodOverloads.forEach((name, overloads) -> {
            // getMethods() 顺序不保证：重载列表按参数个数降序 + 签名串稳定排序（派生确定）
            List<Method> stable = overloads.stream()
                    .sorted(Comparator.comparingInt((Method m) -> -m.getParameterCount())
                            .thenComparing(Method::toGenericString))
                    .toList();
            result.putIfAbsent(name, new Member(name, MemberKind.METHOD, null, null, null, stable));
        });
        Map<String, Member> sorted = new LinkedHashMap<>();
        result.values().stream().sorted(Comparator.comparing(Member::name))
                .forEach(member -> sorted.put(member.name(), member));
        this.members = sorted;
    }

    /** 取（并缓存）某动态 builder 类的契约。 */
    public static DynamicBuilderContract of(Class<? extends DynamicDefinitionBuilder> builderType) {
        return CACHE.computeIfAbsent(builderType, key -> new DynamicBuilderContract((Class<?>) key));
    }

    /** 全部成员（字典序）。 */
    public List<Member> members() {
        return List.copyOf(members.values());
    }

    /** 按名取成员；无则 null。 */
    public Member member(String name) {
        return members.get(name);
    }

    /** 成员名目录（诊断/补全）。 */
    public List<String> memberNames() {
        return List.copyOf(members.keySet());
    }

    /** 可写属性名目录（putMember 错误信息用）。 */
    public List<String> writableNames() {
        List<String> names = new ArrayList<>();
        members.values().forEach(member -> {
            if (member.kind() == MemberKind.WRITABLE_PROPERTY) {
                names.add(member.name());
            }
        });
        return names;
    }

    /** 读一个属性成员的当前值（getter）。 */
    public Object readMember(Object builder, Member member) {
        try {
            if (member.getter() != null) {
                return member.getter().invoke(builder);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read builder member '" + member.name() + "'", e);
        }
        throw new IllegalArgumentException(
                "'" + member.name() + "' is not a readable property of " + builderType.getSimpleName());
    }

    /**
     * definition fingerprint 的规范化成员读数（AC5）：按字典序读出全部可写属性当前值
     * （{@code name=value}）。两种写入方式经过同一 setter 后此处读数必然一致；全量可写
     * 属性参与（不依赖部分字段或对象身份）。
     */
    public List<String> normalizedPropertyReadings(Object builder) {
        List<String> readings = new ArrayList<>();
        for (Member member : members.values()) {
            if (member.kind() == MemberKind.WRITABLE_PROPERTY) {
                readings.add(member.name() + "=" + canonicalValue(readMember(builder, member)));
            }
        }
        return readings;
    }

    /** 单个属性值的规范化字串：null 显式区分 / Float 统一字串 / 其余 String.valueOf。 */
    private static String canonicalValue(Object value) {
        if (value == null) {
            return "null";
        }
        return String.valueOf(value);
    }

    /** 调用成员方法（surface 的方法形态与 putMember 共用；异常带成员名解包）。 */
    public Object invoke(Object builder, Method method, Object... args) {
        try {
            return method.invoke(builder, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot invoke '" + method.getName() + "' of "
                    + builderType.getSimpleName(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw cause instanceof RuntimeException runtime ? runtime
                    : new IllegalArgumentException("member '" + method.getName() + "' failed: " + cause, cause);
        }
    }

    private static boolean isGetterName(String name) {
        return name.length() > 3 && name.startsWith("get") && Character.isUpperCase(name.charAt(3))
                || name.length() > 2 && name.startsWith("is") && Character.isUpperCase(name.charAt(2));
    }

    private static boolean isSetterName(String name) {
        return name.length() > 3 && name.startsWith("set") && Character.isUpperCase(name.charAt(3));
    }

    /** {@code getMaxStackSize} → {@code maxStackSize}。 */
    private static String propertyName(String accessorName) {
        String prefix = accessorName.startsWith("is") ? accessorName.substring(0, 2) : accessorName.substring(0, 3);
        String rest = accessorName.substring(prefix.length());
        return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
    }

    /** Java 类型 → TS 类型（declaration 与 runtime 成员同一映射）。 */
    public static String tsTypeOf(Class<?> type) {
        if (type == int.class || type == long.class || type == double.class || type == float.class
                || type == Integer.class || type == Long.class || type == Double.class || type == Float.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type == String.class || type == CharSequence.class) {
            return "string";
        }
        return "any";
    }

    /** Java 类型 → Python 类型（与 TS 同一输入，成员语义一致）。 */
    public static String pyTypeOf(Class<?> type) {
        if (type == int.class || type == long.class || type == Integer.class || type == Long.class) {
            return "int";
        }
        if (type == double.class || type == float.class || type == Double.class || type == Float.class) {
            return "float";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "bool";
        }
        if (type == String.class || type == CharSequence.class) {
            return "str";
        }
        return "Any";
    }
}
