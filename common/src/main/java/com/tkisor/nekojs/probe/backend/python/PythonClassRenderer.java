package com.tkisor.nekojs.probe.backend.python;

import com.tkisor.nekojs.probe.ir.FieldDecl;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeSlot;
import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders shared declarations as Python stub classes, interfaces and enums.
 * Bean aliases yield to real member names. Mixed static and instance overloads use a private
 * metaclass property for class access, leaving bound instance methods and their inheritance intact.
 * Types use {@link ApiTypeRefPyRenderer}; generic variables retain its existing best-effort mapping.
 */
public final class PythonClassRenderer {
    private final ApiTypeRefPyRenderer typeRenderer;
    private final Map<String, TypeDecl> declarations = new LinkedHashMap<>();
    private final Map<String, Set<String>> reservedNames = new HashMap<>();
    private final Map<String, String> helperNames = new HashMap<>();

    public PythonClassRenderer(ApiTypeRefPyRenderer typeRenderer) {
        this(typeRenderer, List.of());
    }

    public PythonClassRenderer(ApiTypeRefPyRenderer typeRenderer, List<TypeDecl> declarations) {
        this.typeRenderer = typeRenderer;
        for (TypeDecl declaration : declarations) {
            this.declarations.put(declaration.fqn, declaration);
            reservedNames.computeIfAbsent(packageName(declaration.fqn), key -> new LinkedHashSet<>())
                    .add(effectiveClassName(declaration));
        }
    }

    public String render(TypeDecl d) {
        if (d.hidden) return "";
        return switch (d.kind) {
            case ENUM -> renderEnum(d);
            case INTERFACE -> renderInterface(d);
            default -> renderClass(d);
        };
    }

    // ---------------- class ----------------

    private String renderClass(TypeDecl d) {
        String name = effectiveClassName(d);
        StringBuilder sb = new StringBuilder();
        Set<String> mixedNames = mixedBindingNames(d, declarations);
        if (!mixedNames.isEmpty()) appendClassBindings(sb, d, mixedNames);
        String bases = bases(d, true);
        if (!mixedNames.isEmpty()) {
            bases = bases.isEmpty() ? "(metaclass=" + metaclassName(d) + ")"
                    : bases.substring(0, bases.length() - 1) + ", metaclass=" + metaclassName(d) + ")";
        }
        sb.append("class ").append(name).append(bases).append(":\n");
        appendDoc(sb, d.docs);
        boolean hasMember = false;
        BeanPropertyConflicts propertyConflicts = beanPropertyConflicts(d, declarations);

        // 字段：静态（ClassVar）+ 实例
        for (FieldDecl f : d.fields) {
            if (f.hidden || !isPythonIdentifier(f.effectiveName())) continue;
            MethodDecl getter = d.beanGetterForField(f);
            if (getter != null) {
                appendBeanField(sb, d, f, getter);
                hasMember = true;
                continue;
            }
            sb.append("    ").append(pyIdent(f.effectiveName())).append(": ").append(fieldType(f));
            appendFieldDoc(sb, f.docs, "    ");
            sb.append("\n");
            hasMember = true;
        }
        // A selected child getter also owns reads of a collected ancestor's instance field.
        for (VisibleField visible : propertyConflicts.fields().values()) {
            FieldDecl field = visible.field();
            if (d.fields.contains(field)) continue;
            MethodDecl getter = d.beanGetterForField(field);
            if (getter != null) {
                MethodDecl inheritedGetter = inheritedBeanGetter(d, visible);
                TypeSlot inheritedRead = inheritedGetter == null ? field.type : inheritedGetter.returnType;
                TypeSlot inheritedWrite = inheritedGetter == null ? field.type
                        : d.beanFieldWriteType(field, inheritedGetter);
                // Compare the nearest inherited projection, including intermediate getter overrides.
                if (!field.isFinal && inheritedWrite != null
                        && renderSlot(inheritedRead).equals(renderSlot(getter.returnType))
                        && renderSlot(inheritedWrite).equals(renderSlot(d.beanFieldWriteType(field, getter)))) continue;
                appendBeanField(sb, d, field, getter);
                hasMember = true;
            }
        }
        // 构造器 → __init__（多个构造器 = 重载，每个 __init__ 都需 @overload）
        boolean ctorOverloaded = d.constructors.stream().filter(c -> !c.hidden).count() > 1;
        for (MethodDecl c : d.constructors) {
            if (c.hidden) continue;
            if (ctorOverloaded) sb.append("    @overload\n");
            sb.append("    def __init__(").append(params(c, true)).append(") -> None");
            appendMethodBody(sb, c.docs);
            hasMember = true;
        }
        // getter → @property（+ setter）
        for (MethodDecl m : d.methods) {
            if (m.hidden || !m.isGetter || !isPythonIdentifier(m.property)
                    || propertyConflicts.properties().contains(pyIdent(m.property))) continue;
            String ret = renderSlot(m.returnType);
            sb.append("    @property\n");
            sb.append("    def ").append(pyIdent(m.property)).append("(self) -> ").append(ret);
            appendMethodBody(sb, m.docs);
            if (m.setterParamType != null) {
                sb.append("    @").append(pyIdent(m.property)).append(".setter\n");
                sb.append("    def ").append(pyIdent(m.property)).append("(self, value: ")
                  .append(renderSlot(m.setterParamType)).append(") -> None");
                appendMethodBody(sb, m.docs);
            }
            hasMember = true;
        }
        // 方法：静态 + 实例（排除 getter/setter/构造器）。Java 重载（同名不同参数）渲染为
        // 同名 def 时必须全部标注 @overload，否则 Pylance 报「方法声明被同名声明遮盖」
        // （与 PythonEventRenderer 对 dispatch 事件的处理一致）。
        Map<String, Integer> methodNameCount = new HashMap<>();
        for (MethodDecl m : d.methods) {
            if (!isOrdinaryPythonMethod(m, propertyConflicts)) continue;
            if (m.isStatic && mixedNames.contains(pyIdent(m.effectiveName()))) continue;
            methodNameCount.merge(pyIdent(m.effectiveName()), 1, Integer::sum);
        }
        for (MethodDecl m : d.methods) {
            if (!isOrdinaryPythonMethod(m, propertyConflicts)) continue;
            if (m.isStatic && mixedNames.contains(pyIdent(m.effectiveName()))) continue;
            boolean overloaded = methodNameCount.getOrDefault(pyIdent(m.effectiveName()), 0) > 1;
            if (m.isStatic) sb.append("    @staticmethod\n");
            if (overloaded) sb.append("    @overload\n");
            sb.append("    def ").append(pyIdent(m.effectiveName()))
              .append("(").append(params(m, !m.isStatic)).append(") -> ")
              .append(renderSlot(m.returnType));
            appendMethodBody(sb, m.docs);
            hasMember = true;
        }
        if (!hasMember) sb.append("    ...\n");
        return sb.toString();
    }

    private void appendBeanField(StringBuilder sb, TypeDecl declaration, FieldDecl field, MethodDecl getter) {
        String name = pyIdent(field.effectiveName());
        sb.append("    @property\n    def ").append(name)
                .append("(self) -> ").append(renderSlot(getter.returnType));
        appendMethodBody(sb, field.docs);
        TypeSlot writeType = declaration.beanFieldWriteType(field, getter);
        if (writeType != null) {
            sb.append("    @").append(name).append(".setter\n    def ").append(name)
                    .append("(self, value: ").append(renderSlot(writeType)).append(") -> None");
            appendMethodBody(sb, field.docs);
        }
    }

    private MethodDecl inheritedBeanGetter(TypeDecl declaration, VisibleField visible) {
        Set<String> visited = new LinkedHashSet<>();
        TypeSlot parent = declaration.superType;
        while (parent != null && parent.ref != null && parent.ref.kind() == ApiTypeRef.Kind.SYMBOL) {
            String fqn = ApiTypeRefPyRenderer.extractFqn(parent.ref.name());
            if (!visited.add(fqn)) return null;
            TypeDecl ancestor = declarations.get(fqn);
            if (ancestor == null || ancestor.hidden) return null;
            MethodDecl getter = ancestor.beanGetterForField(visible.field());
            if (getter != null) return getter;
            if (ancestor == visible.owner()) return null;
            parent = ancestor.superType;
        }
        return null;
    }

    /** Class access uses the static overloads; instance access retains the real bound methods. */
    private void appendClassBindings(StringBuilder sb, TypeDecl declaration, Set<String> mixedNames) {
        for (String methodName : mixedNames) {
            List<MethodDecl> methods = declaration.methods.stream()
                    .filter(method -> !method.hidden && method.isStatic
                            && methodName.equals(pyIdent(method.effectiveName()))).toList();
            sb.append("class ").append(staticCallableName(declaration, methodName)).append("(Protocol):\n");
            for (MethodDecl method : methods) {
                if (methods.size() > 1) sb.append("    @overload\n");
                sb.append("    def __call__(").append(params(method, true)).append(") -> ")
                        .append(renderSlot(method.returnType));
                appendMethodBody(sb, method.docs);
            }
            sb.append("\n");
        }
        TypeDecl ancestor = metaclassAncestor(declaration);
        sb.append("class ").append(metaclassName(declaration)).append("(")
                .append(ancestor == null ? "type" : metaclassReference(ancestor, packageName(declaration.fqn)))
                .append("):\n");
        for (String methodName : mixedNames) {
            sb.append("    @property\n    def ").append(methodName).append("(self) -> ")
                    .append(staticCallableName(declaration, methodName)).append(": ...\n");
        }
        sb.append("\n");
    }

    public static boolean hasMixedBindings(TypeDecl declaration) {
        return !mixedBindingNames(declaration, Map.of()).isEmpty();
    }

    /** Includes collected ancestors when deciding which class bindings this renderer emits. */
    public boolean hasClassBindings(TypeDecl declaration) {
        return !mixedBindingNames(declaration, declarations).isEmpty();
    }

    private static Set<String> mixedBindingNames(TypeDecl declaration, Map<String, TypeDecl> declarations) {
        if (declaration.hidden || declaration.kind != TypeDecl.Kind.CLASS) return Set.of();
        Set<String> staticNames = new LinkedHashSet<>();
        Set<String> instanceNames = new LinkedHashSet<>();
        BeanPropertyConflicts conflicts = beanPropertyConflicts(declaration, declarations);
        for (MethodDecl method : declaration.methods) {
            if (!isOrdinaryPythonMethod(method, conflicts)) continue;
            (method.isStatic ? staticNames : instanceNames).add(pyIdent(method.effectiveName()));
        }
        for (FieldDecl field : declaration.fields) {
            if (!field.hidden && !field.isStatic && isPythonIdentifier(field.effectiveName())) {
                instanceNames.add(pyIdent(field.effectiveName()));
            }
        }
        staticNames.retainAll(instanceNames);
        return staticNames;
    }

    /** The closest class ancestor's metaclass preserves inherited class operations. */
    public TypeDecl metaclassAncestor(TypeDecl declaration) {
        Set<String> visited = new LinkedHashSet<>();
        TypeSlot parent = declaration.superType;
        while (parent != null && parent.ref != null
                && parent.ref.kind() == ApiTypeRef.Kind.SYMBOL) {
            String fqn = ApiTypeRefPyRenderer.extractFqn(parent.ref.name());
            if (!visited.add(fqn)) return null;
            TypeDecl ancestor = declarations.get(fqn);
            if (ancestor == null || ancestor.hidden) return null;
            if (hasClassBindings(ancestor)) return ancestor;
            parent = ancestor.superType;
        }
        return null;
    }

    public String metaclassName(TypeDecl declaration) {
        return helperName(declaration, "meta", "_NekoMeta_" + effectiveClassName(declaration));
    }

    /** Reserves imports and aliases before any local or imported helper name is allocated. */
    void reservePackageNames(String javaPackage, Set<String> names) {
        reservedNames.computeIfAbsent(javaPackage, ignored -> new LinkedHashSet<>()).addAll(names);
    }

    String metaclassReference(TypeDecl ancestor, String javaPackage) {
        String original = metaclassName(ancestor);
        if (packageName(ancestor.fqn).equals(javaPackage)) return original;
        return helperName(javaPackage, "import:" + ancestor.fqn, original);
    }

    private String staticCallableName(TypeDecl declaration, String methodName) {
        return helperName(declaration, "static:" + methodName,
                "_NekoStatic_" + effectiveClassName(declaration) + "_" + methodName);
    }

    private String helperName(TypeDecl declaration, String role, String base) {
        return helperName(packageName(declaration.fqn), declaration.fqn + "#" + role, base);
    }

    private String helperName(String javaPackage, String role, String base) {
        return helperNames.computeIfAbsent(javaPackage + "#" + role, key -> {
            Set<String> used = reservedNames.computeIfAbsent(javaPackage, ignored -> new LinkedHashSet<>());
            String name = base;
            for (int suffix = 1; !used.add(name); suffix++) name = base + suffix;
            return name;
        });
    }

    private static String packageName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? "" : fqn.substring(0, dot);
    }

    // ---------------- interface ----------------

    private String renderInterface(TypeDecl d) {
        String name = effectiveClassName(d);
        StringBuilder sb = new StringBuilder();
        sb.append("class ").append(name).append(bases(d, false)).append(":\n");
        appendDoc(sb, d.docs);
        boolean hasMember = false;
        // 接口方法同样可能有 Java 重载 → 同名 def 需 @overload
        Map<String, Integer> nameCount = new HashMap<>();
        for (MethodDecl m : d.methods) {
            if (m.hidden || !isPythonIdentifier(m.effectiveName())) continue;
            nameCount.merge(pyIdent(m.effectiveName()), 1, Integer::sum);
        }
        for (MethodDecl m : d.methods) {
            if (m.hidden || !isPythonIdentifier(m.effectiveName())) continue;
            if (nameCount.getOrDefault(pyIdent(m.effectiveName()), 0) > 1) sb.append("    @overload\n");
            sb.append("    def ").append(pyIdent(m.effectiveName()))
              .append("(").append(params(m, true)).append(") -> ")
              .append(renderSlot(m.returnType));
            appendMethodBody(sb, m.docs);
            hasMember = true;
        }
        for (FieldDecl f : d.fields) {
            if (f.hidden || !(f.isStatic && f.isFinal) || !isPythonIdentifier(f.effectiveName())) continue;
            sb.append("    ").append(pyIdent(f.effectiveName())).append(": ClassVar[").append(renderSlot(f.type)).append("]");
            appendFieldDoc(sb, f.docs, "    ");
            sb.append("\n");
            hasMember = true;
        }
        if (!hasMember) sb.append("    ...\n");
        return sb.toString();
    }

    // ---------------- enum ----------------

    private String renderEnum(TypeDecl d) {
        String name = effectiveClassName(d);
        StringBuilder sb = new StringBuilder();
        sb.append("class ").append(name).append(":\n");
        appendDoc(sb, d.docs);
        boolean hasMember = false;
        for (FieldDecl f : d.fields) {
            if (f.hidden || !f.isEnumConstant || !isPythonIdentifier(f.effectiveName())) continue;
            sb.append("    ").append(pyIdent(f.effectiveName())).append(": ").append(name);
            appendFieldDoc(sb, f.docs, "    ");
            sb.append("\n");
            hasMember = true;
        }
        sb.append("    def name(self) -> str: ...\n");
        sb.append("    def ordinal(self) -> int: ...\n");
        sb.append("    def toString(self) -> str: ...\n");
        return sb.toString();
    }

    // ---------------- helpers ----------------

    /** 渲染出的该类型是否需要 {@code typing.overload} 导入（存在同名 def：重载方法/重载构造器）。 */
    public static boolean hasOverloads(TypeDecl d) {
        return hasOverloads(d, Map.of());
    }

    /** Counts the methods rendered with this instance's collected ancestor declarations. */
    public boolean hasRenderedOverloads(TypeDecl declaration) {
        return hasOverloads(declaration, declarations);
    }

    private static boolean hasOverloads(TypeDecl d, Map<String, TypeDecl> declarations) {
        if (d == null) return false;
        if (d.constructors.stream().filter(c -> !c.hidden).count() > 1) return true;
        BeanPropertyConflicts propertyConflicts = beanPropertyConflicts(d, declarations);
        Map<String, Integer> nameCount = new HashMap<>();
        for (MethodDecl m : d.methods) {
            if (!isOrdinaryPythonMethod(m, propertyConflicts)) continue;
            nameCount.merge(pyIdent(m.effectiveName()), 1, Integer::sum);
        }
        return nameCount.values().stream().anyMatch(count -> count > 1);
    }

    /** 渲染用的类名：renameTo（modify_type 改名）优先，否则回退 fqn 的 Python 简单名。 */
    private static String effectiveClassName(TypeDecl d) {
        return d.effectiveTypeName() != null ? d.effectiveTypeName() : ApiTypeRefPyRenderer.simplePyName(d.fqn);
    }

    /** 基类列表：class 含 superType+interfaces；interface 仅 interfaces。 */
    private String bases(TypeDecl d, boolean includeSuper) {
        // LinkedHashSet：superType 在前、interfaces 按声明序在后，迭代序即插入序——Set 接口不保证
        // 迭代序（旧 HashSet 在跨 JVM/平台间可能乱序），违反本仓库的产物确定性标准。
        Set<String> bases = new LinkedHashSet<>();
        if (includeSuper && d.superType != null) {
            String s = renderSlot(d.superType);
            if (isBaseSafe(s)) bases.add(s);
        }
        for (TypeSlot i : d.interfaces) {
            String s = renderSlot(i);
            if (isBaseSafe(s)) bases.add(s);
        }
        return bases.isEmpty() ? "" : "(" + String.join(", ", bases) + ")";
    }

    /**
     * 渲染出的类型字符串能否安全地出现在 Python 基类位置——与 TS 侧
     * {@code TypeScriptClassRenderer.isHeritageSafe} 对称的守卫。{@code class X(基类):}
     * 只接受可子类化的类型引用，probe 编辑 API（{@code assign_type} / {@code modify_type.changeSuper}）
     * 可把 superType/implements 槽重写为任意 ref，以下形态必须逐条剔除：
     * <ul>
     *   <li>{@code Any}（未收集 SYMBOL / object）——原有过滤，悬空引用</li>
     *   <li>联合类型（含 {@code |}，渲染为 {@code A | B} → 非法基类语法）</li>
     *   <li>回调类型（{@code Callable[..., Any]} 开头 → 不可子类化）</li>
     *   <li>{@code None}（VOID 的渲染 → 非法基类）</li>
     * </ul>
     * 原始类型（int/float/str/bool）是合法的 Python 基类，保留。策略与 TS 侧一致：剔除而非失败，
     * 保证产出 stub 语法有效。
     */
    private static boolean isBaseSafe(String rendered) {
        if (rendered == null || rendered.isBlank()) return false;
        if (rendered.equals("Any") || rendered.equals("None")) return false;
        return !rendered.contains("|") && !rendered.startsWith("Callable");
    }

    /** 完整参数列表（含可选的 self 前缀）。 */
    private String params(MethodDecl m, boolean prependSelf) {
        StringBuilder sb = new StringBuilder();
        if (prependSelf) sb.append("self");
        String rest = paramsRest(m);
        if (!rest.isEmpty()) {
            if (prependSelf) sb.append(", ");
            sb.append(rest);
        }
        return sb.toString();
    }

    /** 除 self 外的参数（接口方法直接用这个）。 */
    private String paramsRest(MethodDecl m) {
        StringBuilder sb = new StringBuilder();
        List<MethodDecl.MethodParam> ps = m.params;
        for (int i = 0; i < ps.size(); i++) {
            MethodDecl.MethodParam p = ps.get(i);
            if (i > 0) sb.append(", ");
            if (p.varargs) {
                sb.append("*").append(pyIdent(p.name)).append(": ").append(renderSlot(p.type));
            } else if (p.optional) {
                sb.append(pyIdent(p.name)).append(": ").append(renderSlot(p.type)).append(" = ...");
            } else {
                sb.append(pyIdent(p.name)).append(": ").append(renderSlot(p.type));
            }
        }
        // 返回类型仅给非构造器方法；构造器在外层硬编码 None
        return sb.toString();
    }

    /** 字段类型：静态→ClassVar[...]，否则裸类型。 */
    private String fieldType(FieldDecl f) {
        String t = renderSlot(f.type);
        return f.isStatic ? "ClassVar[" + t + "]" : t;
    }

    private String renderSlot(TypeSlot slot) {
        return typeRenderer.render(slot == null ? null : slot.ref);
    }

    private static void appendDoc(StringBuilder sb, List<String> docs) {
        if (docs == null || docs.isEmpty()) return;
        sb.append("    \"\"\"").append(docText(docs)).append("\"\"\"\n");
    }

    /** 方法体：无 docs → 单行 {@code : ...}；有 docs → 换行 docstring + {@code ...}（stub 允许 docstring）。 */
    private static void appendMethodBody(StringBuilder sb, List<String> docs) {
        if (docs == null || docs.isEmpty()) {
            sb.append(": ...\n");
            return;
        }
        sb.append(":\n");
        sb.append("        \"\"\"").append(docText(docs)).append("\"\"\"\n");
        sb.append("        ...\n");
    }

    /** 字段级 docs：首行作为行尾 {@code   # ...} 注释；其余行以 {@code # } 前缀单独成行（跟在字段行之后）。 */
    private static void appendFieldDoc(StringBuilder sb, List<String> docs, String indent) {
        if (docs == null || docs.isEmpty()) return;
        List<String> lines = docs.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
        if (lines.isEmpty()) return;
        sb.append("  # ").append(lines.get(0));
        for (int i = 1; i < lines.size(); i++) sb.append("\n").append(indent).append("# ").append(lines.get(i));
    }

    /** docs 拼接（按行 strip 后换行连接）；docstring 定界符 {@code """} 转成 {@code '''} 避免截断。 */
    private static String docText(List<String> docs) {
        return docs.stream().map(String::strip)
                .collect(Collectors.joining("\n"))
                .replace("\"\"\"", "'''");
    }

    private record VisibleField(TypeDecl owner, FieldDecl field) {}

    private record BeanPropertyConflicts(Set<String> properties, Set<String> accessorCalls,
                                         Map<String, VisibleField> fields) {
        private static final BeanPropertyConflicts NONE = new BeanPropertyConflicts(Set.of(), Set.of(), Map.of());
    }

    private static BeanPropertyConflicts beanPropertyConflicts(TypeDecl declaration,
                                                              Map<String, TypeDecl> declarations) {
        Map<String, VisibleField> fields = new LinkedHashMap<>();
        Set<String> visited = new LinkedHashSet<>();
        for (TypeDecl ancestor = declaration; ancestor != null && !ancestor.hidden
                && visited.add(ancestor.fqn);) {
            for (FieldDecl field : ancestor.fields) {
                if (!field.hidden && isPythonIdentifier(field.effectiveName())
                        && (ancestor == declaration || !field.isStatic)) {
                    fields.putIfAbsent(pyIdent(field.effectiveName()), new VisibleField(ancestor, field));
                }
            }
            TypeSlot parent = ancestor.superType;
            ancestor = parent != null && parent.ref != null && parent.ref.kind() == ApiTypeRef.Kind.SYMBOL
                    ? declarations.get(ApiTypeRefPyRenderer.extractFqn(parent.ref.name())) : null;
        }
        Set<String> names = new LinkedHashSet<>(fields.keySet());
        for (MethodDecl method : declaration.methods) {
            if (isOrdinaryPythonMethod(method, BeanPropertyConflicts.NONE)) names.add(pyIdent(method.effectiveName()));
        }
        Set<String> callTargets = new LinkedHashSet<>();
        for (MethodDecl method : declaration.methods) {
            if (!method.hidden && !method.isConstructor && isPythonIdentifier(method.effectiveName())) {
                callTargets.add(pyIdent(method.effectiveName()));
            }
        }
        Set<String> accessorCalls = new LinkedHashSet<>();
        // Alias collisions retain actual accessor calls. A same-name field alone keeps its existing projection.
        for (MethodDecl method : declaration.methods) {
            if (!method.hidden && method.isGetter && isPythonIdentifier(method.property)
                    && callTargets.contains(pyIdent(method.property))
                    && !fields.containsKey(pyIdent(method.property))) accessorCalls.add(pyIdent(method.property));
        }
        names.addAll(accessorCalls);
        return new BeanPropertyConflicts(Set.copyOf(names), Set.copyOf(accessorCalls), fields);
    }

    private static boolean isOrdinaryPythonMethod(MethodDecl method, BeanPropertyConflicts propertyConflicts) {
        if (method.hidden || method.isConstructor || !isPythonIdentifier(method.effectiveName())) return false;
        if (propertyConflicts.accessorCalls().contains(pyIdent(method.effectiveName()))) return true;
        // A stub cannot declare a property and a method/field under the same name.
        // Preserve the real accessor call targets when the Bean alias would collide.
        if (method.isGetter) return !isPythonIdentifier(method.property)
                || propertyConflicts.properties().contains(pyIdent(method.property));
        if (method.isSetter && method.effectiveName().length() > 3) {
            String suffix = method.effectiveName().substring(3);
            String property = suffix.substring(0, 1).toLowerCase(Locale.ROOT) + suffix.substring(1);
            return !isPythonIdentifier(property) || propertyConflicts.properties().contains(pyIdent(property));
        }
        if (method.isSetter) return false;
        return true;
    }

    /** Java-only member names are omitted, not renamed to nonexistent Python call targets. */
    private static boolean isPythonIdentifier(String name) {
        if (name == null || name.isEmpty()) return false;
        int first = name.codePointAt(0);
        if (first != '_' && !Character.isUnicodeIdentifierStart(first)) return false;
        for (int offset = Character.charCount(first); offset < name.length();) {
            int character = name.codePointAt(offset);
            if (!Character.isUnicodeIdentifierPart(character) || Character.isIdentifierIgnorable(character)) return false;
            offset += Character.charCount(character);
        }
        return true;
    }

    /** 规避 Python 关键字/软关键字：命中则末尾加 {@code _}。 */
    static String pyIdent(String name) {
        if (name == null || name.isEmpty()) return "_";
        if (PY_KEYWORDS.contains(name)) return name + "_";
        return name;
    }

    private static final Set<String> PY_KEYWORDS = Set.of(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break",
            "class", "continue", "def", "del", "elif", "else", "except", "finally", "for",
            "from", "global", "if", "import", "in", "is", "lambda", "nonlocal", "not",
            "or", "pass", "raise", "return", "try", "while", "with", "yield", "self"
    );
}
