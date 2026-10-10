package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.JavaMemberIndex;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.reflect.FunctionalInterfaceResolver;

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
 * Reflects script-visible Java members into {@link TypeDecl}, retaining remapping and Bean accessor pairing.
 * Class declarations include callable interface methods and inherited overloads with contextual generic types.
 *
 * <p>Each {@link TypeSlot} retains Java signature metadata for contextual input aliases and a language-neutral
 * {@link ApiTypeRef} for rendering and type edits.
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
                Type genericSuperclass = cls.getGenericSuperclass();
                decl.superType = TypeSlot.of(genericSuperclass, toRef(genericSuperclass));
            }
        }

        // 接口
        for (Type iface : cls.getGenericInterfaces()) {
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
     * Reflects declared members and callable inherited interface methods with contextual generic types.
     * Declared accessors keep precedence; candidates are sorted before Bean pairing, and bridges are omitted.
     * Inherited overloads remain separate from a class's same-name methods instead of being hidden by them.
     */
    private void reflectMethodsLikeClassDecl(Class<?> cls, TypeDecl decl) {
        List<Method> declared = declarationMethods(cls);
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

            // Select one Bean alias per exposed property, retaining the other real accessor methods.
            if (!isStatic && isGetterName(jsName) && method.getParameterCount() == 0) {
                String propName = getPropertyName(jsName);
                if (propName != null && processedProperties.add(propName)) {
                    MethodDecl getter = reflectClassMethod(cls, method);
                    getter.isGetter = true;
                    getter.property = propName;
                    getter.setterParamType = findSetterParamSlot(cls, propName, declared);
                    decl.methods.add(getter);
                } else {
                    decl.methods.add(reflectClassMethod(cls, method));
                }
                continue;
            }

            MethodDecl m = reflectClassMethod(cls, method);
            // Mark Bean setters while retaining their real callable method signatures.
            if (!isStatic && isSetterName(jsName) && method.getParameterCount() == 1) {
                m.isSetter = true;
            }
            decl.methods.add(m);
        }
    }

    /** Returns declaration candidates shared by reflection and bounded dependency collection. */
    public static List<Method> declarationMethods(Class<?> cls) {
        List<Method> methods = new ArrayList<>(Arrays.asList(cls.getDeclaredMethods()));
        methods.sort(TypeReflector::compareCandidates);
        if (!cls.isEnum() && !cls.isInterface()) {
            // TypeScript implements clauses do not inherit Java's default interface methods or overloads.
            // Keep declared accessor precedence; getMethods selects actual overrides and excludes private members.
            Method[] visible = cls.getMethods();
            Set<String> names = new HashSet<>();
            // Object methods are required only when an implemented interface explicitly redeclares them.
            Set<String> interfaceNames = new HashSet<>();
            for (Class<?> ancestor = cls; ancestor != null; ancestor = ancestor.getSuperclass()) {
                for (Class<?> iface : ancestor.getInterfaces()) {
                    List<String> required = Arrays.stream(iface.getMethods())
                            .filter(method -> !Modifier.isStatic(method.getModifiers()))
                            .map(TypeReflector::jsName).filter(name -> name != null).toList();
                    interfaceNames.addAll(required);
                    if (ancestor == cls) names.addAll(required);
                }
            }
            // Ordinary ancestor implementations remain inherited from the parent declaration.
            // Object operations need explicit declarations when an inherited interface requires them.
            Arrays.stream(Object.class.getMethods()).map(TypeReflector::jsName)
                    .filter(name -> name != null && interfaceNames.contains(name)).forEach(names::add);
            Set<String> staticNames = new HashSet<>();
            Set<String> fieldNames = Arrays.stream(cls.getDeclaredFields())
                    .filter(field -> Modifier.isPublic(field.getModifiers()) && !Modifier.isStatic(field.getModifiers()))
                    .map(java.lang.reflect.Field::getName).collect(java.util.stream.Collectors.toSet());
            methods.stream().filter(method -> Modifier.isPublic(method.getModifiers())
                            && Modifier.isStatic(method.getModifiers()))
                    .map(TypeReflector::jsName).filter(name -> name != null).forEach(staticNames::add);
            methods.stream().filter(method -> Modifier.isPublic(method.getModifiers())
                            && !Modifier.isStatic(method.getModifiers()))
                    .map(TypeReflector::jsName).filter(name -> name != null).forEach(names::add);
            Arrays.stream(visible).filter(method -> method.getDeclaringClass().isInterface()
                            && !Modifier.isStatic(method.getModifiers()))
                    .map(TypeReflector::jsName).filter(name -> name != null).forEach(names::add);
            // An inherited Bean getter can own reads of a public subclass field with a different type.
            Arrays.stream(visible).filter(method -> !Modifier.isStatic(method.getModifiers())
                            && method.getParameterCount() == 0)
                    .map(TypeReflector::jsName).filter(name -> name != null && isGetterName(name)
                            && fieldNames.contains(getPropertyName(name))).forEach(names::add);
            // Real methods own names that would otherwise become Bean aliases. A property can select
            // another getter, so close this finite name set before pairing writes, independent of reflection order.
            int previousNames;
            do {
                previousNames = names.size();
                for (Method method : visible) {
                    String name = jsName(method);
                    if (!Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0
                            && name != null && names.contains(name)) {
                        String property = getPropertyName(name);
                        if (property != null) {
                            names.add(property);
                            names.add(beanSetterName(property));
                        }
                    }
                }
            } while (names.size() != previousNames);
            // Declaring one overload on a TypeScript subclass hides inherited same-name overloads.
            // Include the actual superclass overloads for each emitted instance method name as well.
            List<Method> inherited = Arrays.stream(visible)
                    .filter(method -> method.getDeclaringClass() != cls
                            && (method.getDeclaringClass() != Object.class || interfaceNames.contains(jsName(method)))
                            && (Modifier.isStatic(method.getModifiers())
                                ? !method.getDeclaringClass().isInterface() && staticNames.contains(jsName(method))
                                : names.contains(jsName(method))))
                    .sorted(TypeReflector::compareCandidates)
                    .toList();
            methods.addAll(inherited);
        }
        return methods;
    }

    private MethodDecl reflectClassMethod(Class<?> cls, Method method) {
        MethodDecl declaration = reflectMethod(method);
        if (method.getDeclaringClass() == cls) return declaration;
        FunctionalInterfaceResolver.Signature signature = inheritedSignature(cls, method);
        reflectTypeParameters(declaration, signature.typeParameters());
        declaration.returnType = TypeSlot.of(signature.returnType(), toRef(signature.returnType()));
        for (int index = 0; index < declaration.params.size(); index++) {
            Type type = signature.parameterTypes().get(index);
            if (declaration.params.get(index).varargs) {
                type = type instanceof GenericArrayType array ? array.getGenericComponentType()
                        : ((Class<?>) type).getComponentType();
            }
            declaration.params.get(index).type = TypeSlot.of(type, toRef(type));
        }
        return declaration;
    }

    private static FunctionalInterfaceResolver.Signature inheritedSignature(Class<?> cls, Method method) {
        FunctionalInterfaceResolver.Signature signature = FunctionalInterfaceResolver.resolve(cls, method);
        if (signature == null) {
            throw new IllegalStateException("[NEKO-4029] Inherited method signature is unreachable: "
                    + cls.getName() + " -> " + method.toGenericString());
        }
        return signature;
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
        m.overloads.addAll(AnnotatedDocs.overloads(ctor));
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
        reflectTypeParameters(m, List.of(method.getTypeParameters()));
        reflectParamsInto(m, method);
        m.docs.addAll(AnnotatedDocs.executableDocs(method));
        m.overloads.addAll(AnnotatedDocs.overloads(method));
        return m;
    }

    private static void reflectTypeParameters(MethodDecl method, List<TypeVariable<?>> variables) {
        method.typeParams.clear();
        method.typeParameterBounds.clear();
        for (TypeVariable<?> variable : variables) {
            List<TypeSlot> bounds = new ArrayList<>();
            for (Type bound : variable.getBounds()) {
                if (bound == Object.class) continue;
                TypeSlot slot = TypeSlot.of(bound, toRef(bound));
                bounds.add(slot);
            }
            method.typeParams.add(variable.getName());
            if (!bounds.isEmpty()) method.typeParameterBounds.put(variable.getName(), List.copyOf(bounds));
        }
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

    /** Prefers declared setters, then pairs inherited candidates deterministically in the host class. */
    private TypeSlot findSetterParamSlot(Class<?> cls, String propName, List<Method> methods) {
        String setterName = beanSetterName(propName);
        Method best = null;
        for (Method method : methods) {
            String jsName = jsName(method);
            if (jsName == null || !jsName.equals(setterName) || method.getParameterCount() != 1
                    || !Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (best == null || (method.getDeclaringClass() == cls && best.getDeclaringClass() != cls)
                    || ((method.getDeclaringClass() == cls) == (best.getDeclaringClass() == cls)
                    && compareCandidates(method, best) < 0)) {
                best = method;
            }
        }
        if (best == null) return null;
        Type t = best.getDeclaringClass() != cls
                ? inheritedSignature(cls, best).parameterTypes().get(0)
                : best.getGenericParameterTypes()[0];
        return TypeSlot.of(t, toRef(t));
    }

    private static String beanSetterName(String property) {
        return "set" + property.substring(0, 1).toUpperCase(Locale.ROOT) + property.substring(1);
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
