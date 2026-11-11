package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.core.mixin.MixinPlan;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.TreeMap;
import java.util.TreeSet;

/** Declaration-only completion in the exact runtime namespace; never a runtime module. */
final class MixinDeclarationDeclarations {
    private MixinDeclarationDeclarations() {}

    static String render(Collection<Class<?>> classes, Collection<String> warnings) {
        var targets = new TreeMap<String, TreeSet<String>>();
        for (Class<?> target : classes) {
            if (target.isArray() || target.isPrimitive()) continue;
            var selectors = new TreeSet<String>();
            try {
                // Declared methods only: inherited selectors cannot be injected into this class.
                for (var method : target.getDeclaredMethods()) {
                    if (Modifier.isAbstract(method.getModifiers()) || Modifier.isNative(method.getModifiers())
                            || method.isSynthetic() || method.isBridge()) continue;
                    String descriptor = MethodType.methodType(method.getReturnType(), method.getParameterTypes())
                            .toMethodDescriptorString();
                    try {
                        MixinPlan.Descriptor.parse(descriptor);
                        selectors.add(method.getName() + descriptor);
                    } catch (IllegalArgumentException unsupportedAbi) {
                        // The compiler also rejects long values, including nested long arrays.
                    }
                }
                targets.put(target.getName(), selectors);
            } catch (LinkageError | RuntimeException unavailable) {
                warnings.add("MixinJS declaration completion unavailable for " + target.getName() + ": " + unavailable);
            }
        }
        var out = new StringBuilder("""
                // Generated for the isolated mixins editor project, not ordinary script execution.
                declare module "nekojs/mixin" {
                  interface Targets {
                """);
        targets.forEach((name, selectors) -> out.append("    ").append('"').append(name).append('"').append(": ")
                .append(selectors.isEmpty() ? "never" : String.join(" | ", selectors.stream()
                        .map(selector -> '"' + selector + '"').toList())).append(";\n"));
        return out.append("""
                  }
                  /** Unscanned classes remain possible; the compiler validates their exact target bytes. */
                  type ClassName = keyof Targets | (string & {});
                  type Selector<T extends string> = T extends keyof Targets ? Targets[T] : `${string}(${string})${string}`;
                  interface HookBuilder {
                    /** HEAD by default. Only HEAD and RETURN are supported. */
                    at: "HEAD" | "RETURN";
                    cancellable: boolean;
                    /** SERVER by default; thread must match the selected script type. */
                    scriptType: "SERVER" | "CLIENT";
                    thread: "SERVER_MAIN" | "CLIENT_MAIN";
                    require: 1;
                    /** Set true for a static target method; false by default. */
                    staticMethod: boolean;
                  }
                  interface Target<T extends string> {
                    inject(selector: Selector<T>, id: `${string}:${string}`, build: (builder: HookBuilder) => undefined): void;
                  }
                  export const Mixin: {
                    /** Names and descriptors belong to this Probe runtime, not a portable mapping namespace. */
                    target<T extends ClassName>(className: T, configure: (target: Target<T>) => undefined): void;
                  };
                }
                """).toString();
    }
}
