package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry.EventPayload;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry.Member;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry.MemberKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Derives STARTUP proxy signatures from the same registry metadata and factories used by RegistryEventJS. */
final class StartupRegistryEventSurface {
    private static final String BUILDER_NAMESPACE = "NekoStartupBuilders";

    private StartupRegistryEventSurface() {}

    static EventPayload derive(RegistryInfosPoint.RegistryInfos infos, RegistryTypesPoint.RegistryTypes types,
                               List<RegistryBuilderSurfaceEntry> builders) {
        var members = new ArrayList<Member>();
        var bySugar = new LinkedHashMap<String, RegistryInfo>();
        infos.infos().values().forEach(info -> bySugar.putIfAbsent(info.sugarName(), info));
        bySugar.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            String sugar = entry.getKey();
            RegistryInfo info = entry.getValue();
            String registry = info.key().identifier().toString();
            String defaultName = types.defaults().get(info.key());
            if (types.defaultType(info.key()) != null) {
                members.add(sugarMethod(sugar, null, find(builders, registry, defaultName)));
            }
            types.typeNames(info.key()).stream().sorted().forEach(typeName ->
                    members.add(sugarMethod(sugar, typeName, find(builders, registry, typeName))));
        });
        types.byRegistry().values().stream().flatMap(named -> named.keySet().stream()).distinct().sorted()
                .forEach(typeName -> {
                    var owners = types.registriesOf(typeName);
                    if (owners.size() == 1) {
                        var builder = find(builders, owners.getFirst().identifier().toString(), typeName);
                        if (builder != null) {
                            members.add(sugarMethod("custom", typeName, builder));
                        }
                    }
                });
        // Arbitrary addon factories and Supplier results have no promised concrete script builder type.
        members.add(new Member("custom", MemberKind.METHOD,
                "custom(id: string, typeName: string, build: (build: any) => void): any",
                "def custom(self, id: str, typeName: str, build: Callable[[Any], None]) -> Any: ..."));
        members.add(new Member("register", MemberKind.METHOD,
                "register(registry: string, id: string, supplier: () => unknown): unknown",
                "def register(self, registry: str, id: str, supplier: Callable[[], Any]) -> Any: ..."));
        return new EventPayload(RegistryEventJS.class, "StartupRegistryEvent", members, BUILDER_NAMESPACE);
    }

    private static RegistryBuilderSurfaceEntry find(List<RegistryBuilderSurfaceEntry> builders,
                                                    String registry, String typeName) {
        return builders.stream().filter(builder -> builder.registryKey().equals(registry)
                && builder.typeName().equals(typeName)).findFirst().orElse(null);
    }

    private static Member sugarMethod(String name, String typeName, RegistryBuilderSurfaceEntry builder) {
        String tsBuilder = builder == null ? "any" : BUILDER_NAMESPACE + "." + builder.builderName();
        String pyBuilder = builder == null ? "Any" : builder.builderName();
        String tsType = typeName == null ? "" : ", typeName: " + quoted(typeName);
        String pyType = typeName == null ? "" : ", typeName: Literal[" + quoted(typeName) + "]";
        return new Member(name, MemberKind.METHOD,
                name + "(id: string" + tsType + ", build: (build: " + tsBuilder + ") => void): " + tsBuilder,
                "def " + name + "(self, id: str" + pyType + ", build: Callable[[" + pyBuilder + "], None]) -> " + pyBuilder + ": ...");
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
}
