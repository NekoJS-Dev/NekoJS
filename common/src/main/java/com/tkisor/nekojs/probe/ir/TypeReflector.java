package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.JavaMemberIndex;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * {@code Class<?>} → {@link TypeDecl} 反射器。镜像旧 {@code ClassDeclGenerator} 的成员枚举与 getter/setter 推断，
 * 保证 {@link TypeScriptClassRenderer} 渲染未编辑 IR 时与旧实现逐字一致。
 *
 * <p>每个类型槽产出 {@link TypeSlot}：{@code sourceType} 供 TS 默认渲染（TypeConverter，零回归），
 * {@code ref}（ApiTypeRef，best-effort）供 Python（Phase 3）与 modify_type 编辑使用。
 */
public final class TypeReflector {

    public TypeDecl reflect(Class<?> cls) {
        TypeDecl.Kind kind = cls.isEnum() ? TypeDecl.Kind.ENUM
                : (cls.isInterface() ? TypeDecl.Kind.INTERFACE : TypeDecl.Kind.CLASS);
        TypeDecl decl = new TypeDecl(kind, cls, cls.getName());
        // 类级 @Doc → JSDoc（注解缺省时 docs 为空，渲染零输出）
        decl.docs.addAll(AnnotatedDocs.typeDocs(cls));

        // 类级泛型
        for (TypeVariable<?> tv : cls.getTypeParameters()) {
            TypeSlot bound = null;
            Type[] bounds = tv.getBounds();
            if (bounds.length > 0 && bounds[0] != Object.class) {
                bound = TypeSlot.of(bounds[0], toRef(bounds[0]));
            }
            decl.typeParams.add(new TypeDecl.TypeParam(tv.getName(), bound));
        }

        // 父类（仅 class；interface/enum 的 extends 在旧实现里不渲染 superclass）
        if (kind == TypeDecl.Kind.CLASS) {
            Class<?> sc = cls.getSuperclass();
            if (sc != null && sc != Object.class) {
                decl.superType = TypeSlot.of(sc, toRef(sc));
            }
        }

        // 接口
        for (Class<?> iface : cls.getInterfaces()) {
            decl.interfaces.add(TypeSlot.of(iface, toRef(iface)));
        }

        switch (kind) {
            case CLASS -> reflectClassMembers(cls, decl);
            case INTERFACE -> reflectInterfaceMembers(cls, decl);
            case ENUM -> reflectEnumMembers(cls, decl);
        }

        // 确定性排序：JVM 规范不保证 getDeclaredMethods/getDeclaredFields 的返回顺序，
        // 跨进程运行会产生成员顺序抖动 → 按名字（+参数/返回类型）稳定排序，保证 probe 产物可复现。
        // 渲染分段（getter/静态/实例）由 renderer 按标志过滤，与列表顺序无关。
        decl.constructors.sort(Comparator.comparing(TypeReflector::constructorKey));
        decl.fields.sort(Comparator.comparing(f -> f.name));
        decl.methods.sort(Comparator.comparing(TypeReflector::methodKey));
        return decl;
    }

    private static String constructorKey(MethodDecl c) {
        return paramsKey(c);
    }

    private static String methodKey(MethodDecl m) {
        return m.name + "|" + paramsKey(m) + "→" + typeKey(m.returnType);
    }

    private static String paramsKey(MethodDecl m) {
        StringBuilder sb = new StringBuilder();
        for (MethodDecl.MethodParam p : m.params) {
            sb.append('|').append(typeKey(p.type));
            // varargs/optional 是排序键的一部分：varargs 参数在 IR 中被扁平化为组件类型，
            // 若不加标志，of(int) 与 of(int...) 的排序键相同 → 稳定排序保留反射原始序 → 跨 JVM 抖动
            if (p.varargs) sb.append("[]");
            if (p.optional) sb.append("?");
        }
        return sb.toString();
    }

    private static String typeKey(TypeSlot slot) {
        if (slot == null || slot.sourceType == null) return "";
        return slot.sourceType.getTypeName();
    }

    private void reflectClassMembers(Class<?> cls, TypeDecl decl) {
        // 构造器
        for (var ctor : cls.getDeclaredConstructors()) {
            if (Modifier.isPublic(ctor.getModifiers())) {
                decl.constructors.add(reflectConstructor(ctor));
            }
        }
        // 字段
        for (var field : cls.getDeclaredFields()) {
            if (!Modifier.isPublic(field.getModifiers())) continue;
            boolean isStatic = Modifier.isStatic(field.getModifiers());
            FieldDecl f = new FieldDecl(field.getName(), TypeSlot.of(field.getGenericType(), toRef(field.getGenericType())));
            f.isStatic = isStatic;
            f.isFinal = Modifier.isFinal(field.getModifiers());
            f.docs.addAll(AnnotatedDocs.fieldDocs(field));
            decl.fields.add(f);
        }
        // 方法 + getter/setter 推断（与 ClassDeclGenerator 对齐）
        reflectMethodsLikeClassDecl(cls, decl);
    }

    private void reflectInterfaceMembers(Class<?> cls, TypeDecl decl) {
        for (var method : cls.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) continue;
            // bridge/synthetic 是 JVM 协变覆盖的实现细节（如 LevelExtension.neko$data()
            // 覆盖 LevelSpec 的 Object 哨兵时 javac 生成 Object bridge），对 JS/Python 侧
            // 无意义且会产生「同参不同返回」的非法重载，过滤掉
            if (method.isSynthetic() || method.isBridge()) continue;
            decl.methods.add(reflectMethod(method));
        }
        for (var field : cls.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers())) {
                FieldDecl f = new FieldDecl(field.getName(), TypeSlot.of(field.getGenericType(), toRef(field.getGenericType())));
                f.isStatic = true;
                f.isFinal = true;
                f.docs.addAll(AnnotatedDocs.fieldDocs(field));
                decl.fields.add(f);
            }
        }
    }

    private void reflectEnumMembers(Class<?> cls, TypeDecl decl) {
        for (var field : cls.getDeclaredFields()) {
            if (field.isEnumConstant()) {
                FieldDecl f = new FieldDecl(field.getName(), TypeSlot.of(cls, toRef(cls)));
                f.isStatic = true;
                f.isEnumConstant = true;
                f.docs.addAll(AnnotatedDocs.fieldDocs(field));
                decl.fields.add(f);
            } else if (Modifier.isPublic(field.getModifiers())) {
                // 非常量公开字段：renderer 的 renderEnum 只发射 isEnumConstant，这里仅供
                // import 收集镜像旧 collectImports（旧实现对枚举的公开字段类型也收集 import）
                FieldDecl f = new FieldDecl(field.getName(), TypeSlot.of(field.getGenericType(), toRef(field.getGenericType())));
                f.isStatic = Modifier.isStatic(field.getModifiers());
                f.isFinal = Modifier.isFinal(field.getModifiers());
                decl.fields.add(f);
            }
        }
        // 枚举的公开方法：renderEnum 不发射（固定骨架），仅用于 import 收集镜像旧行为
        reflectMethodsLikeClassDecl(cls, decl);
    }

    /**
     * 镜像 ClassDeclGenerator.generateClass 的方法枚举：
     * 非静态 getXxx/isXxx(0 参) → getter；其配对 setXxx(1 参) → setter（isSetter 标志）。
     * 其余方法按原样收集，由 renderer 按标志分段。
     *
     * <p>同一属性可能存在多个 getter 候选（协变覆盖 vs 其 bridge 方法、getFoo()/isFoo() 并存），
     * JVM 规范不保证 getDeclaredMethods 的返回顺序——first-seen 去重会跨 JVM 漂移，且 bridge 胜出时
     * 渲染出错误的（超类型）返回类型，事后排序无法修复选择。故先对候选做确定性排序：
     * 非 synthetic/bridge 优先（协变覆盖总是胜出 bridge），同优先级按签名键字典序（getFoo 先于 isFoo）。
     * 排序只影响候选选择；输出列表在 {@link #reflect} 末尾仍按 methodKey 全量排序，
     * bridge 方法本身保留在输出中（legacy ProbeJS parity，如 {@code append(arg0: string): $Appendable}）。
     */
    private void reflectMethodsLikeClassDecl(Class<?> cls, TypeDecl decl) {
        Method[] declared = cls.getDeclaredMethods();
        Arrays.sort(declared, TypeReflector::compareCandidates);
        Set<String> processedProperties = new HashSet<>();
        for (var method : declared) {
            if (!Modifier.isPublic(method.getModifiers())) continue;
            // bridge/synthetic 是 JVM 协变覆盖的实现细节（如宿主类上 mixin 注入接口的
            // 协变返回覆盖会生成 Object bridge），对 JS/Python 侧无意义且产生
            // 「同参不同返回」的冗余重载——与接口收集（reflectInterfaceMembers）一致地过滤
            if (method.isSynthetic() || method.isBridge()) continue;
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            // JS 侧方法名：@Remap/@RemapByPrefix 重映射；@HideFromJS → null（跳过）
            String jsName = jsName(method);
            if (jsName == null) continue;

            // 非静态 getXxx/isXxx(0 参) → getter（按属性名去重，首个出现者胜出；重复者整体跳过，
            // 镜像旧实现：不双发射原方法名）。getter 判定基于 JS 名：neko$getId remap 为 getId
            // 后与运行时 Graal getter 属性语义一致（脚本访问 .id）
            if (!isStatic && isGetterName(jsName) && method.getParameterCount() == 0) {
                String propName = getPropertyName(jsName);
                if (propName != null && processedProperties.add(propName)) {
                    MethodDecl getter = reflectMethod(method);
                    getter.isGetter = true;
                    getter.property = propName;
                    getter.setterParamType = findSetterParamSlot(cls, propName);
                    decl.methods.add(getter);
                }
                continue;
            }

            MethodDecl m = reflectMethod(method);
            // 非静态 setXxx(1 参) → isSetter（renderer 实例方法段据此排除）
            if (!isStatic && isSetterName(jsName) && method.getParameterCount() == 1) {
                m.isSetter = true;
            }
            decl.methods.add(m);
        }
    }

    /**
     * getter/setter 候选的确定性排序：非 synthetic/bridge 的声明优先（协变覆盖胜出其 bridge），
     * 同优先级按「名 + 参数类型 + 泛型返回类型」字典序。排序结果与 JVM 返回顺序无关。
     */
    private static int compareCandidates(Method a, Method b) {
        boolean syntheticA = a.isSynthetic() || a.isBridge();
        boolean syntheticB = b.isSynthetic() || b.isBridge();
        if (syntheticA != syntheticB) return syntheticA ? 1 : -1;
        return candidateKey(a).compareTo(candidateKey(b));
    }

    private static String candidateKey(Method m) {
        StringBuilder sb = new StringBuilder(m.getName());
        for (Type p : m.getGenericParameterTypes()) sb.append('|').append(p.getTypeName());
        sb.append("→").append(m.getGenericReturnType().getTypeName());
        return sb.toString();
    }

    private MethodDecl reflectConstructor(java.lang.reflect.Constructor<?> ctor) {
        MethodDecl m = new MethodDecl(ctor.getName());
        m.isConstructor = true;
        reflectParamsInto(m, ctor);
        m.docs.addAll(AnnotatedDocs.executableDocs(ctor));
        return m;
    }

    private MethodDecl reflectMethod(java.lang.reflect.Method method) {
        MethodDecl m = new MethodDecl(method.getName());
        // JS 侧方法名（@Remap/@RemapByPrefix）：与运行时 Graal remapper 语义一致，声明/提示
        // 用 remap 名；Java 原名保留在 name（排序/编辑语义），renameTo 为空时渲染回退原名
        String jsName = jsName(method);
        if (jsName == null) {
            m.hidden = true; // @HideFromJS
            return m;
        }
        if (!jsName.equals(method.getName())) {
            m.renameTo = jsName;
        }
        m.isStatic = Modifier.isStatic(method.getModifiers());
        m.returnType = TypeSlot.of(method.getGenericReturnType(), toRef(method.getGenericReturnType()));
        for (TypeVariable<?> tv : method.getTypeParameters()) {
            m.typeParams.add(tv.getName());
        }
        reflectParamsInto(m, method);
        m.docs.addAll(AnnotatedDocs.executableDocs(method));
        return m;
    }

    /**
     * JS 侧成员名：委托 {@link JavaMemberIndex#remapName}（hideMarker 传 null = 命中
     * {@code @HideFromJS} 返回 null，调用方跳过）。未命中 remap 返回原名。
     */
    private static String jsName(java.lang.reflect.Method method) {
        return JavaMemberIndex.remapName(method, null, method.getName());
    }

    private void reflectParamsInto(MethodDecl m, java.lang.reflect.Executable exec) {
        boolean varArgs = exec.isVarArgs();
        var params = exec.getParameters();
        for (int i = 0; i < params.length; i++) {
            var p = params[i];
            Type sourceType;
            if (varArgs && i == params.length - 1 && p.getType().isArray()) {
                // varargs 必须走泛型路径：p.getType() 只给 raw Class 数组（component 的泛型实参
                // 丢失，如 MemoryModuleType<?>... → raw MemoryModuleType）；getParameterizedType()
                // 返回 GenericArrayType（component = MemoryModuleType<?>）保留下界
                Type generic = p.getParameterizedType();
                sourceType = generic instanceof GenericArrayType gat ? gat.getGenericComponentType()
                        : p.getType().getComponentType();
            } else {
                sourceType = p.getParameterizedType();
            }
            MethodDecl.MethodParam mp = new MethodDecl.MethodParam(
                    p.isNamePresent() ? p.getName() : "arg" + i,
                    TypeSlot.of(sourceType, toRef(sourceType)),
                    varArgs && i == params.length - 1);
            m.params.add(mp);
        }
    }

    /**
     * 配对 setter 的入参槽：同名 setXxx(1 参) 的公开重载里确定性取一个——非 synthetic/bridge 优先，
     * 同优先级按泛型参数类型字典序（首个匹配胜出的旧实现依赖 getDeclaredMethods 顺序，跨 JVM 会漂移）。
     */
    private TypeSlot findSetterParamSlot(Class<?> cls, String propName) {
        String setterName = "set" + propName.substring(0, 1).toUpperCase(Locale.ROOT) + propName.substring(1);
        Method best = null;
        for (Method method : cls.getDeclaredMethods()) {
            String jsName = jsName(method);
            if (jsName == null || !jsName.equals(setterName) || method.getParameterCount() != 1
                    || !Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            if (best == null || compareCandidates(method, best) < 0) {
                best = method;
            }
        }
        if (best == null) return null;
        Type t = best.getGenericParameterTypes()[0];
        return TypeSlot.of(t, toRef(t));
    }

    /** getter 名判定基于 JS 名（remap 后）：neko$getId → getId 即 getter 形态。 */
    private static boolean isGetterName(String name) {
        return (name.startsWith("get") && name.length() > 3) || (name.startsWith("is") && name.length() > 2);
    }

    private static boolean isSetterName(String name) {
        return name.startsWith("set") && name.length() > 3;
    }

    /** 属性名：getFoo→foo、isFoo→foo。大小写归一显式用 {@link Locale#ROOT}，避免默认区域（如 tr）漂移。 */
    private static String getPropertyName(String name) {
        if (name.startsWith("get") && name.length() > 3) {
            return name.substring(3, 4).toLowerCase(Locale.ROOT) + name.substring(4);
        }
        if (name.startsWith("is") && name.length() > 2) {
            return name.substring(2, 3).toLowerCase(Locale.ROOT) + name.substring(3);
        }
        return null;
    }

    // ---- Java Type → ApiTypeRef（唯一类型映射：无损承载，语言糖由各渲染器决定）----

    /**
     * Java 反射类型 → {@link ApiTypeRef}（probe 的唯一类型映射，TS/Python 渲染均以此为准）。
     *
     * <p>保真约定（与旧 {@code TypeConverter} 的 TS 语义逐项对齐，保证双轨合并后产物零回归）：
     * <ul>
     *   <li>参数化类型 → SYMBOL(raw) **携带完整实参**（{@code Map<K,V>} 保留两个实参；
     *       语法糖——TS 的 {@code $Map<$K, $V>}、Python 的 {@code list[X]}——由各语言渲染器决定）</li>
     *   <li>有界通配符 → 上界；无界通配符 → {@code any}（对齐 TypeConverter 的 "any"，非 object）</li>
     *   <li>raw 非 Class 的参数化类型 / 未知形态 → {@code any}</li>
     * </ul>
     */
    public static ApiTypeRef toRef(Type type) {
        if (type == null || type == void.class || type == Void.class) return ApiTypeRef.voidType();
        if (type instanceof Class<?> cls) return classToRef(cls);
        if (type instanceof ParameterizedType pt) {
            Type raw = pt.getRawType();
            if (raw instanceof Class<?> rawCls) {
                Type[] args = pt.getActualTypeArguments();
                List<ApiTypeRef> argRefs = new ArrayList<>(args.length);
                for (Type arg : args) {
                    argRefs.add(toRef(arg));
                }
                return ApiTypeRef.symbol(new ApiSymbolId("java", rawCls.getName()), argRefs);
            }
            return ApiTypeRef.primitive("any");
        }
        if (type instanceof GenericArrayType gat) return ApiTypeRef.array(toRef(gat.getGenericComponentType()));
        if (type instanceof TypeVariable<?> tv) return ApiTypeRef.typeVariable(tv.getName());
        if (type instanceof WildcardType wt) {
            Type[] upper = wt.getUpperBounds();
            if (upper.length > 0 && upper[0] != Object.class) return toRef(upper[0]);
            return ApiTypeRef.primitive("any");
        }
        return ApiTypeRef.primitive("any");
    }

    private static ApiTypeRef classToRef(Class<?> cls) {
        if (cls == void.class || cls == Void.class) return ApiTypeRef.voidType();
        if (cls == String.class || cls == char.class) return ApiTypeRef.primitive("string");
        if (cls == boolean.class || cls == Boolean.class) return ApiTypeRef.primitive("boolean");
        if (cls == float.class || cls == Float.class || cls == double.class || cls == Double.class) {
            return ApiTypeRef.primitive("float");
        }
        if (cls.isPrimitive() || Number.class.isAssignableFrom(cls)) return ApiTypeRef.primitive("int");
        if (cls == Object.class) return ApiTypeRef.primitive("object");
        if (cls.isArray()) return ApiTypeRef.array(classToRef(cls.getComponentType()));
        return ApiTypeRef.symbol(new ApiSymbolId("java", cls.getName()));
    }
}
