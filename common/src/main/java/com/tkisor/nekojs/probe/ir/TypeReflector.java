package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Comparator;
import java.util.HashSet;
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
            decl.fields.add(f);
        }
        // 方法 + getter/setter 推断（与 ClassDeclGenerator 对齐）
        reflectMethodsLikeClassDecl(cls, decl);
    }

    private void reflectInterfaceMembers(Class<?> cls, TypeDecl decl) {
        for (var method : cls.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                decl.methods.add(reflectMethod(method));
            }
        }
        for (var field : cls.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers())) {
                FieldDecl f = new FieldDecl(field.getName(), TypeSlot.of(field.getGenericType(), toRef(field.getGenericType())));
                f.isStatic = true;
                f.isFinal = true;
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
     */
    private void reflectMethodsLikeClassDecl(Class<?> cls, TypeDecl decl) {
        var declared = cls.getDeclaredMethods();
        // 单遍收集，保持 getDeclaredMethods 的原始声明序：旧 ClassDeclGenerator 的 import 收集
        // 按原始序建立 first-insertion 顺序，import 块字节兼容依赖于此；渲染按 isGetter/isSetter
        // 标志分段，与收集顺序无关。
        Set<String> processedProperties = new HashSet<>();
        for (var method : declared) {
            if (!Modifier.isPublic(method.getModifiers())) continue;
            boolean isStatic = Modifier.isStatic(method.getModifiers());

            // 非静态 getXxx/isXxx(0 参) → getter（按属性名去重，首个出现者胜出；重复者整体跳过，
            // 镜像旧实现：不双发射原方法名）
            if (!isStatic && isGetterName(method) && method.getParameterCount() == 0) {
                String propName = getPropertyName(method);
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
            if (!isStatic && isSetterName(method) && method.getParameterCount() == 1) {
                m.isSetter = true;
            }
            decl.methods.add(m);
        }
    }

    private MethodDecl reflectConstructor(java.lang.reflect.Constructor<?> ctor) {
        MethodDecl m = new MethodDecl(ctor.getName());
        m.isConstructor = true;
        reflectParamsInto(m, ctor);
        return m;
    }

    private MethodDecl reflectMethod(java.lang.reflect.Method method) {
        MethodDecl m = new MethodDecl(method.getName());
        m.isStatic = Modifier.isStatic(method.getModifiers());
        m.returnType = TypeSlot.of(method.getGenericReturnType(), toRef(method.getGenericReturnType()));
        for (TypeVariable<?> tv : method.getTypeParameters()) {
            m.typeParams.add(tv.getName());
        }
        reflectParamsInto(m, method);
        return m;
    }

    private void reflectParamsInto(MethodDecl m, java.lang.reflect.Executable exec) {
        boolean varArgs = exec.isVarArgs();
        var params = exec.getParameters();
        for (int i = 0; i < params.length; i++) {
            var p = params[i];
            Type sourceType;
            if (varArgs && i == params.length - 1 && p.getType().isArray()) {
                sourceType = p.getType().getComponentType();
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

    private TypeSlot findSetterParamSlot(Class<?> cls, String propName) {
        String setterName = "set" + propName.substring(0, 1).toUpperCase() + propName.substring(1);
        for (var method : cls.getDeclaredMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1
                    && Modifier.isPublic(method.getModifiers())) {
                Type t = method.getGenericParameterTypes()[0];
                return TypeSlot.of(t, toRef(t));
            }
        }
        return null;
    }

    private static boolean isGetterName(java.lang.reflect.Method method) {
        String name = method.getName();
        return (name.startsWith("get") && name.length() > 3) || (name.startsWith("is") && name.length() > 2);
    }

    private static boolean isSetterName(java.lang.reflect.Method method) {
        String name = method.getName();
        return name.startsWith("set") && name.length() > 3;
    }

    private static String getPropertyName(java.lang.reflect.Method method) {
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3) {
            return Character.toLowerCase(name.charAt(3)) + name.substring(4);
        }
        if (name.startsWith("is") && name.length() > 2) {
            return Character.toLowerCase(name.charAt(2)) + name.substring(3);
        }
        return null;
    }

    // ---- Java Type → ApiTypeRef（best-effort，供 Python/编辑；TS 默认渲染不用它）----

    private static ApiTypeRef toRef(Type type) {
        if (type == null || type == void.class || type == Void.class) return ApiTypeRef.voidType();
        if (type instanceof Class<?> cls) return classToRef(cls);
        if (type instanceof ParameterizedType pt) {
            Type raw = pt.getRawType();
            if (raw instanceof Class<?> rawCls) {
                Type[] args = pt.getActualTypeArguments();
                // 单参集合 → 数组；其余按 raw symbol（参数列表丢失，Phase 3 细化）
                if (args.length == 1 && isCollectionLike(rawCls)) {
                    return ApiTypeRef.array(toRef(args[0]));
                }
                return ApiTypeRef.symbol(new ApiSymbolId("java", rawCls.getName()));
            }
            return ApiTypeRef.voidType();
        }
        if (type instanceof GenericArrayType gat) return ApiTypeRef.array(toRef(gat.getGenericComponentType()));
        if (type instanceof TypeVariable<?> tv) return ApiTypeRef.typeVariable(tv.getName());
        if (type instanceof WildcardType wt) {
            Type[] upper = wt.getUpperBounds();
            if (upper.length > 0 && upper[0] != Object.class) return toRef(upper[0]);
            return ApiTypeRef.primitive("object");
        }
        return ApiTypeRef.voidType();
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

    private static boolean isCollectionLike(Class<?> cls) {
        return java.util.Collection.class.isAssignableFrom(cls)
                || java.lang.Iterable.class.isAssignableFrom(cls)
                || cls.getName().startsWith("java.util.stream.");
    }
}
