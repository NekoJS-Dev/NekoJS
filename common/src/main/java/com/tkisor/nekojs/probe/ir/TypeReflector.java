package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
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
        return decl;
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
            }
        }
    }

    /**
     * 镜像 ClassDeclGenerator.generateClass 的方法枚举：
     * 非静态 getXxx/isXxx(0 参) → getter；其配对 setXxx(1 参) → setter（isSetter 标志）。
     * 其余方法按原样收集，由 renderer 按标志分段。
     */
    private void reflectMethodsLikeClassDecl(Class<?> cls, TypeDecl decl) {
        var declared = cls.getDeclaredMethods();
        // 先建索引以便 getter 配对 setter
        Set<String> processedProperties = new HashSet<>();
        for (var method : declared) {
            if (!Modifier.isPublic(method.getModifiers())) continue;
            boolean isStatic = Modifier.isStatic(method.getModifiers());

            // getter：仅非静态
            if (!isStatic && isGetterName(method) && method.getParameterCount() == 0) {
                String propName = getPropertyName(method);
                if (propName == null || processedProperties.contains(propName)) continue;
                processedProperties.add(propName);

                MethodDecl getter = reflectMethod(method);
                getter.isGetter = true;
                getter.property = propName;
                getter.setterParamType = findSetterParamSlot(cls, propName);
                decl.methods.add(getter);
            }
        }
        // 其余方法（含静态、setter、实例普通方法），按反射顺序收集并打标志
        for (var method : declared) {
            if (!Modifier.isPublic(method.getModifiers())) continue;
            boolean isStatic = Modifier.isStatic(method.getModifiers());

            boolean alreadyAddedAsGetter = !isStatic && isGetterName(method) && method.getParameterCount() == 0
                    && processedProperties.contains(getPropertyName(method));
            if (alreadyAddedAsGetter) continue;

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
