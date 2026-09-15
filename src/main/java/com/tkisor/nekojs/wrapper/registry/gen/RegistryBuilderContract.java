package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * typed Builder 的公开成员契约（ticket 15，AC7/AC9）：
 * 以<b>反射</b>从 Builder 类本身派生成员目录——同一份契约输入同时驱动
 * 脚本面 {@link BuilderSurface}（runtime member）、definition fingerprint、
 * TS/Python declaration 与 contract/golden，不手写第二份成员表。
 *
 * <p>分类规则（确定性，按成员名字典序输出）：
 * <ul>
 *   <li><b>可写属性</b>——{@code setXxx(T)} 与配套 {@code getXxx():T}（或 boolean 的
 *       {@code isXxx()}) 同时存在。脚本端 {@code b.xxx = v}（经 ProxyObject putMember）
 *       与显式 {@code b.setXxx(v)}（经成员表方法）调用<b>同一个</b> setter
 *       {@link Method}，进入同一校验/规范化/指纹路径（ADR-0005 修订后的单一写入语义）。</li>
 *   <li><b>只读属性</b>——有 getter 无 setter（含 final identity 字段 {@code id}，
 *       final/只读成员是 AC3 声明的例外）。</li>
 *   <li><b>方法</b>——其余 public 实例方法（含 setter 本身：显式 setter 形态必须可用）。</li>
 * </ul>
 *
 * <p>引擎生命周期接缝（{@code build}/{@code get}/{@code handleAdditionalObjects}：
 * 先攒后建的懒构建点）与 tag 内部接缝不进脚本面——对象创建只发生在节点 Adapter 的
 * 注册 pass（AC8），脚本在收集期提前 build 会破坏「先攒后建」。
 * static 成员不属于实例面。反射结果按类缓存（builder 类不可变）。
 */
public final class RegistryBuilderContract {

    /**
     * 引擎生命周期/内部接缝：不出现在脚本面与声明里。{@code entityType()} 会经 {@code get()}
     * 提前构建实体（破坏先攒后建），与 build/get 同属生命周期接缝。
     */
    private static final Set<String> ENGINE_SEAM = Set.of(
            "build", "get", "handleAdditionalObjects", "equals", "hashCode", "toString",
            "getLocation", "getTagRegistry", "getTagTargets", "entityType", "buildProperties");

    private static final Map<Class<?>, RegistryBuilderContract> CACHE = new ConcurrentHashMap<>();

    /** 成员种类：可写属性 / 只读属性 / 方法（含显式 setter）。 */
    public enum MemberKind { WRITABLE_PROPERTY, READ_ONLY_PROPERTY, METHOD }

    /** 单个契约成员。方法成员可含同名重载（如 {@code PotionBuilder.effect}）。 */
    public record Member(
            String name, MemberKind kind, Class<?> valueType, Method getter, Method setter, List<Method> overloads) {

        Member(String name, MemberKind kind, Class<?> valueType, Method getter, Method setter) {
            this(name, kind, valueType, getter, setter, List.of());
        }

        /** TS 声明类型（确定性映射；未知类型 any）。 */
        public String tsType() {
            return tsTypeOf(valueType);
        }

        /** Python 声明类型（与 TS 同一输入派生，成员语义一致）。 */
        public String pyType() {
            return pyTypeOf(valueType);
        }
    }

    private final Class<?> builderType;
    private final Map<String, Member> members;
    /** final identity 字段（如基类 {@code id}）：只读成员（AC3 的例外清单）。 */
    private final Map<String, Field> readOnlyFields;

    private RegistryBuilderContract(Class<?> builderType) {
        this.builderType = builderType;
        Map<String, Method> getters = new LinkedHashMap<>();
        Map<String, Method> setters = new LinkedHashMap<>();
        // 方法成员按名聚成<b>重载列表</b>（同名不同参各有存在权，如 PotionBuilder.effect
        // 的 3 参/5 参形态）——单 Method 收集会在 getMethods() 顺序下丢重载，
        // 令部分脚本形态不可达（审查 F1）
        Map<String, List<Method>> rest = new LinkedHashMap<>();
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Field field : builderType.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            fields.put(field.getName(), field);
        }
        for (Method method : builderType.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.isBridge() || method.isSynthetic()) {
                continue;
            }
            String name = method.getName();
            if (ENGINE_SEAM.contains(name) || method.getDeclaringClass() == Object.class) {
                continue;
            }
            if (isGetterName(name) && method.getParameterCount() == 0) {
                getters.put(propertyName(name), method);
            } else if (isSetterName(name) && method.getParameterCount() == 1) {
                setters.put(propertyName(name), method);
            } else {
                rest.computeIfAbsent(name, k -> new ArrayList<>()).add(method);
            }
        }
        Map<String, Member> result = new LinkedHashMap<>();
        Map<String, List<Method>> methodOverloads = new LinkedHashMap<>();
        // final identity 字段（基类 id 等）作为只读成员进入目录（AC3：final id 是例外）
        fields.forEach((name, field) -> result.putIfAbsent(name,
                new Member(name, MemberKind.READ_ONLY_PROPERTY, field.getType(), null, null)));
        getters.keySet().forEach(property -> {
            Method setter = setters.get(property);
            Method getter = getters.get(property);
            if (setter != null) {
                result.put(property, new Member(property, MemberKind.WRITABLE_PROPERTY, setter.getParameterTypes()[0], getter, setter));
            } else {
                result.put(property, new Member(property, MemberKind.READ_ONLY_PROPERTY, getter.getReturnType(), getter, null));
            }
        });
        // 显式 setter 形态（b.setXxx(v)）作为方法成员保留——与属性写入同一 Method
        setters.forEach((property, setter) -> methodOverloads.computeIfAbsent(setter.getName(), k -> new ArrayList<>()).add(setter));
        rest.forEach((name, overloads) -> methodOverloads.computeIfAbsent(name, k -> new ArrayList<>()).addAll(overloads));
        methodOverloads.forEach((name, overloads) -> {
            // getMethods() 顺序不保证：重载列表按参数个数降序 + 签名串稳定排序，
            // 契约/声明/golden 派生确定（脚本侧调用按实参个数解析，不受此序影响）
            List<Method> stable = overloads.stream()
                    .sorted(Comparator.comparingInt((Method m) -> -m.getParameterCount())
                            .thenComparing(m -> m.getParameterTypes().length == 0 ? "" : m.toGenericString()))
                    .toList();
            result.putIfAbsent(name, new Member(name, MemberKind.METHOD, null, null, null, stable));
        });
        Map<String, Member> sorted = new LinkedHashMap<>();
        result.values().stream()
                .sorted(Comparator.comparing(Member::name))
                .forEach(member -> sorted.put(member.name(), member));
        this.members = sorted;
        this.readOnlyFields = Map.copyOf(fields);
    }

    /** 取（并缓存）某 builder 类的契约。 */
    public static RegistryBuilderContract of(Class<? extends RegistryObjectBuilder<?>> builderType) {
        return CACHE.computeIfAbsent(builderType, RegistryBuilderContract::new);
    }

    /** Builder 类（契约反射输入）。 */
    public Class<?> builderType() {
        return builderType;
    }

    /** 全部成员（字典序）。 */
    public List<Member> members() {
        return List.copyOf(members.values());
    }

    /** 按名取成员；无则 null。 */
    public Member member(String name) {
        return members.get(name);
    }

    /** 读一个属性成员的当前值（getter 或 final identity 字段）。 */
    public Object readMember(Object builder, Member member) {
        try {
            if (member.getter() != null) {
                return member.getter().invoke(builder);
            }
            Field field = readOnlyFields.get(member.name());
            if (field != null) {
                return field.get(builder);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read builder member '" + member.name() + "'", e);
        }
        throw new IllegalArgumentException("'" + member.name() + "' is not a readable property of " + builderType.getSimpleName());
    }

    /** 成员名目录（诊断/补全）。 */
    public List<String> memberNames() {
        return List.copyOf(members.keySet());
    }

    /**
     * definition fingerprint 的规范化成员读数：按字典序读出全部属性当前值
     * （{@code name=value}）。两种写入方式经过同一 setter 后此处读数必然一致。
     *
     * <p><b>对象值属性规范化</b>（审查 F2）：值为 {@link RegistryObjectBuilder}（如
     * BlockBuilder 的 {@code item} 子 builder）时不用 {@code String.valueOf}（会内嵌
     * {@code @<identityHashCode>}，同声明跨实例/跨启动指纹漂移），改为递归展开子 builder
     * 的<b>子指纹</b>（类型简名 + id + 其全部可写属性读数）；嵌套环以 {@code cycle}
     * 标记截断（防御：当前连带 builder 无环，不承诺任意第三方 builder 无环）。
     * 抑制（null）与存在（子指纹）天然可区分。
     */
    public List<String> normalizedPropertyReadings(Object builder) {
        return normalizedReadingsOf(builder, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    private List<String> normalizedReadingsOf(Object builder, Set<Object> seen) {
        List<String> readings = new ArrayList<>();
        for (Member member : members.values()) {
            if (member.kind() == MemberKind.WRITABLE_PROPERTY) {
                Object value = readMember(builder, member);
                readings.add(member.name() + "=" + canonicalValue(value, seen));
            }
        }
        return readings;
    }

    /** 单个属性值的规范化字串：null / 嵌套 builder 子指纹（递归）/ 其余 String.valueOf。 */
    private String canonicalValue(Object value, Set<Object> seen) {
        if (value == null) {
            return "null";
        }
        if (value instanceof RegistryObjectBuilder<?> nested) {
            if (!seen.add(nested)) {
                return "cycle";
            }
            String inner = String.join(";", RegistryBuilderContract.of(builderClassOf(nested)).normalizedReadingsOf(nested, seen));
            seen.remove(nested);
            return "builder[" + nested.getClass().getSimpleName() + "(" + nested.id + ")" + inner + "]";
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends RegistryObjectBuilder<?>> builderClassOf(Object builder) {
        return (Class<? extends RegistryObjectBuilder<?>>) builder.getClass();
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
