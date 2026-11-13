package com.tkisor.nekojs.core.dynamic.plan;

import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.core.dynamic.facade.DynamicRegistryEventJS;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Derives the contract entries of the dynamic definition builders. The builder classes of the
 * three frozen types are the <b>only contract input</b>: {@link DynamicBuilderContract} reflects
 * their members into structured catalog entries ({@link RegistryBuilderSurfaceEntry}, the same
 * entry shape the startup builders use), which the TS/Python probe backends render. Runtime
 * members ({@link DynamicBuilderSurface}), the definition fingerprint and the TS/Python
 * declarations all come from that one source, so there is no second hand-written member table.
 *
 * <p>It shares the <b>entry shape and renderer</b> with {@code RegistryBuilderSurfaces}: the same
 * presentation convention over the same type facts, but the input here is the dynamic builders'
 * own contract. These entries do not enter the startup {@code registry_types} list and are not
 * coupled to startup drain semantics; the dynamic builder path stays separate.
 */
public final class DynamicBuilderSurfaces {

    private DynamicBuilderSurfaces() {}

    /** The structured contract entries of the three frozen types, sorted by builder name. */
    public static List<RegistryBuilderSurfaceEntry> derive() {
        List<RegistryBuilderSurfaceEntry> entries = new ArrayList<>();
        entries.add(entryOf(DynamicDefinitionType.ITEM, DynamicItemBuilder.class));
        entries.add(entryOf(DynamicDefinitionType.SOUND_EVENT, DynamicSoundEventBuilder.class));
        entries.add(entryOf(DynamicDefinitionType.MOB_EFFECT, DynamicMobEffectBuilder.class));
        entries.sort(Comparator.comparing(RegistryBuilderSurfaceEntry::builderName));
        return List.copyOf(entries);
    }

    private static RegistryBuilderSurfaceEntry entryOf(
            DynamicDefinitionType type, Class<? extends DynamicDefinitionBuilder> builderClass) {
        DynamicBuilderContract contract = DynamicBuilderContract.of(builderClass);
        List<RegistryBuilderSurfaceEntry.Member> members = new ArrayList<>();
        contract.members().forEach(member -> members.add(switch (member.kind()) {
            case WRITABLE_PROPERTY -> new RegistryBuilderSurfaceEntry.Member(
                    member.name(), RegistryBuilderSurfaceEntry.MemberKind.WRITABLE_PROPERTY,
                    member.tsType(), member.pyType());
            case READ_ONLY_PROPERTY -> new RegistryBuilderSurfaceEntry.Member(
                    member.name(), RegistryBuilderSurfaceEntry.MemberKind.READ_ONLY_PROPERTY,
                    member.tsType(), member.pyType());
            case METHOD -> new RegistryBuilderSurfaceEntry.Member(
                    member.name(), RegistryBuilderSurfaceEntry.MemberKind.METHOD,
                    tsSignature(builderClass, member.overloads()),
                    pySignature(builderClass, member.name(), member.overloads()));
        }));
        return new RegistryBuilderSurfaceEntry(
                builderClass.getSimpleName(),
                type.registryKey(),
                "dynamic",
                type.apiName(),
                members,
                "Dynamic registry candidate builder for '" + type.registryKey()
                        + "' (runtime only; activation gated by the transaction/sync gate);"
                        + " property writes and explicit setters hit the same setter.",
                new RegistryBuilderSurfaceEntry.EventPayload(DynamicRegistryEventJS.class,
                        "DynamicRegistryEvent", List.of(new RegistryBuilderSurfaceEntry.Member(
                        type.apiName(), RegistryBuilderSurfaceEntry.MemberKind.METHOD,
                        type.apiName() + "(id: string, build?: (build: " + builderClass.getSimpleName()
                                + ") => void): boolean",
                        "def " + type.apiName() + "(self, id: str, build: Callable[["
                                + builderClass.getSimpleName() + "], None] = ...) -> bool: ..."))));
    }

    /**
     * TypeScript shape of a method member. The declaration surface freezes one deterministic
     * signature per name: it picks the overload with the most parameters, then breaks ties by the
     * signature string. Overloads of the same name, such as {@code fireResistant()} and
     * {@code fireResistant(value)}, are resolved on the script side by argument count. A fluent
     * setter that returns itself is rendered with the builder name, which keeps the declaration
     * readable.
     */
    private static String tsSignature(Class<?> builderClass, List<Method> overloads) {
        Method method = deterministicOverload(overloads);
        StringBuilder sb = new StringBuilder(method.getName()).append('(');
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(paramName(params[i], i)).append(": ").append(DynamicBuilderContract.tsTypeOf(params[i].getType()));
        }
        sb.append("): ").append(returnShape(builderClass, method, "void", false));
        return sb.toString();
    }

    /** Python shape of a method member, derived from the same Method input as its TypeScript shape;
     * the member name stays verbatim as on the JS side. */
    private static String pySignature(Class<?> builderClass, String name, List<Method> overloads) {
        Method method = deterministicOverload(overloads);
        StringBuilder sb = new StringBuilder("def ").append(name).append("(self");
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            // The fallback argument name must use the real index, as it does on the TS side: always passing
            // 0 would produce repeated arg0 names.
            sb.append(", ").append(paramName(params[i], i)).append(": ")
                    .append(DynamicBuilderContract.pyTypeOf(params[i].getType()));
        }
        sb.append(") -> ").append(returnShape(builderClass, method, "None", true)).append(": ...");
        return sb.toString();
    }

    /** Return type shape: a fluent builder return becomes the builder name, void becomes
     * void/None, and anything else goes through the type mapping. */
    private static String returnShape(Class<?> builderClass, Method method, String voidShape, boolean python) {
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class || returnType == Void.class) {
            return voidShape;
        }
        if (DynamicDefinitionBuilder.class.isAssignableFrom(returnType)
                && (builderClass.isAssignableFrom(returnType) || returnType.isAssignableFrom(builderClass))) {
            return builderClass.getSimpleName();
        }
        return python ? DynamicBuilderContract.pyTypeOf(returnType) : DynamicBuilderContract.tsTypeOf(returnType);
    }

    private static Method deterministicOverload(List<Method> overloads) {
        return overloads.stream()
                .sorted(Comparator.comparingInt((Method m) -> -m.getParameterCount())
                        .thenComparing(m -> Arrays.toString(m.getParameterTypes())))
                .findFirst()
                .orElseThrow();
    }

    private static String paramName(Parameter param, int index) {
        return param.isNamePresent() && !param.getName().startsWith("arg")
                ? param.getName()
                : "arg" + index;
    }
}
