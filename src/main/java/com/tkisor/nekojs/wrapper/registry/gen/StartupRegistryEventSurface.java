package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry.EventPayload;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry.EventVariant;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry.Member;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry.MemberKind;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Derives STARTUP callback declarations from the explicit target catalog and registry factories. */
final class StartupRegistryEventSurface {
    private static final String BUILDER_NAMESPACE = "NekoStartupBuilders";

    private StartupRegistryEventSurface() {}

    static EventPayload derive(RegistryInfosPoint.RegistryInfos infos, RegistryTypesPoint.RegistryTypes types,
                               List<RegistryBuilderSurfaceEntry> builders) {
        var names = new HashSet<String>();
        builders.forEach(builder -> names.add(builder.builderName()));
        var variants = new ArrayList<EventVariant>();
        for (var registry : infos.startupRegistries().stream()
                .sorted(Comparator.comparing(key -> key.identifier().toString())).toList()) {
            var id = registry.identifier();
            String key = id.toString();
            boolean vanilla = id.getNamespace().equals("minecraft");
            List<String> selectors = vanilla ? List.of(id.getPath(), key) : List.of(key);
            String name = "StartupRegistryEvent_" + (vanilla ? id.getPath() : key).replaceAll("[^a-zA-Z0-9_]", "_");
            while (!names.add(name)) {
                name += "_";
            }
            variants.add(new EventVariant(selectors, name, members(registry, types, builders)));
        }
        return EventPayload.scoped(RegistryEventJS.class, BUILDER_NAMESPACE, variants);
    }

    private static List<Member> members(ResourceKey<? extends Registry<?>> registry, RegistryTypesPoint.RegistryTypes types,
                                        List<RegistryBuilderSurfaceEntry> builders) {
        var members = new ArrayList<Member>();
        String key = registry.identifier().toString();
        if (types.defaultType(registry) != null) {
            members.add(createMethod(null, find(builders, key, types.defaults().get(registry))));
        }
        types.typeNames(registry).stream().sorted().forEach(typeName ->
                members.add(createMethod(typeName, find(builders, key, typeName))));
        members.add(new Member("createCustom", MemberKind.METHOD,
                "createCustom(id: string, supplier: () => unknown): any",
                "def createCustom(self, id: str, supplier: Callable[[], Any]) -> Any: ..."));
        return members;
    }

    private static RegistryBuilderSurfaceEntry find(List<RegistryBuilderSurfaceEntry> builders,
                                                    String registry, String typeName) {
        return builders.stream().filter(builder -> builder.registryKey().equals(registry)
                && builder.typeName().equals(typeName)).findFirst().orElse(null);
    }

    private static Member createMethod(String typeName, RegistryBuilderSurfaceEntry builder) {
        String tsBuilder = builder == null ? "any" : BUILDER_NAMESPACE + "." + builder.builderName();
        String pyBuilder = builder == null ? "Any" : builder.builderName();
        String tsType = typeName == null ? "" : ", typeName: " + quoted(typeName);
        String pyType = typeName == null ? "" : ", typeName: Literal[" + quoted(typeName) + "]";
        return new Member("create", MemberKind.METHOD,
                "create(id: string" + tsType + ", build: (build: " + tsBuilder + ") => void): " + tsBuilder,
                "def create(self, id: str" + pyType + ", build: Callable[[" + pyBuilder + "], None]) -> " + pyBuilder + ": ...");
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
}
