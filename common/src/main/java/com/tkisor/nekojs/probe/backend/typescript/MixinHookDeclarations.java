package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.mixin.MixinPlan;
import java.util.*;

/** Emits overloads only for loader-admitted plans, never pending declaration files. */
public final class MixinHookDeclarations {
    private MixinHookDeclarations() {}
    public static String render(List<MixinPlan> plans, ScriptType type) {
        var hooks = plans.stream().flatMap(plan -> plan.hooks().stream())
                .filter(hook -> hook.scriptType().equals(type.name())).sorted(Comparator.comparing(MixinPlan.Hook::id)).toList();
        if (hooks.isEmpty()) return "";
        var out = new StringBuilder("declare namespace MixinEvents {\n");
        for (var hook : hooks) {
            var descriptor = MixinPlan.Descriptor.parse(hook.descriptor());
            String result = ts(descriptor.result());
            out.append("  function inject(id: \"").append(hook.id()).append("\", callback: (event: {\n");
            out.append("    readonly self: ").append(hook.staticMethod() ? "null" : ts("L" + hook.target().replace('.', '/') + ";")).append(";\n");
            out.append("    readonly args: readonly [").append(String.join(", ", descriptor.arguments().stream().map(MixinHookDeclarations::ts).toList())).append("];\n");
            if (!descriptor.result().equals("V")) {
                if (hook.cancellable() && hook.at().equals("HEAD")) {
                    out.append("    get returnValue(): ").append(result).append(" | null;\n");
                    out.append("    set returnValue(value: ").append(result).append(");\n");
                } else {
                    out.append("    ").append(hook.cancellable() ? "" : "readonly ").append("returnValue: ").append(result);
                    if (hook.at().equals("HEAD")) out.append(" | null");
                    out.append(";\n");
                }
            }
            if (hook.cancellable() && hook.at().equals("HEAD") && descriptor.result().equals("V")) out.append("    cancel(): void;\n");
            out.append("  }) => undefined): void;\n");
        }
        return out.append("}\n").toString();
    }
    public static Set<String> referencedClasses(List<MixinPlan> plans) {
        var names = new TreeSet<String>();
        for (var plan : plans) for (var hook : plan.hooks()) {
            names.add(hook.target());
            var descriptor = MixinPlan.Descriptor.parse(hook.descriptor());
            var types = new ArrayList<>(descriptor.arguments()); types.add(descriptor.result());
            for (String type : types) {
                while (type.startsWith("[")) type = type.substring(1);
                if (type.startsWith("L")) names.add(type.substring(1, type.length() - 1).replace('/', '.'));
            }
        }
        return names;
    }
    private static String ts(String descriptor) {
        if (descriptor.startsWith("[")) return "ReadonlyArray<" + ts(descriptor.substring(1)) + "> | null";
        return switch (descriptor.charAt(0)) {
            case 'V' -> "undefined";
            case 'Z' -> "boolean";
            case 'C' -> "string";
            case 'B', 'S', 'I', 'F', 'D' -> "number";
            case 'L' -> {
                String name = descriptor.substring(1, descriptor.length() - 1);
                if (name.equals("java/lang/String")) yield "string | null";
                int split = name.lastIndexOf('/');
                yield "import(\"java:" + (split < 0 ? "" : name.substring(0, split)) + "\").$" + name.substring(split + 1) + " | null";
            }
            default -> throw MixinPlan.invalid("Unsupported TypeScript ABI descriptor");
        };
    }
}
