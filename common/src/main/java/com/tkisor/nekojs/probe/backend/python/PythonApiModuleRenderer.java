package com.tkisor.nekojs.probe.backend.python;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.ModuleBindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.ModuleEventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry.EventPayload;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.EventPayloadDeclarations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Renders scoped API module declarations from {@link ModuleBindingCatalogEntry} and
 * {@link ModuleEventCatalogEntry} into PEP 484 {@code .pyi} subpackages under {@code nekojs/_modules/<side>/}.
 *
 * <p>When both {@code snapshot.moduleBindings()} and {@code snapshot.moduleEvents()} are empty, no files are
 * added so empty-catalog output remains identical to legacy snapshots.
 */
final class PythonApiModuleRenderer {

    private static final String MARKER_TEXT = "# Auto-generated namespace marker.\n";
    private PythonApiModuleRenderer() {
    }

    static void writeModuleStubs(Map<String, String> files,
                                 NekoScriptCatalogSnapshot snapshot,
                                 ApiTypeRefPyRenderer typeR,
                                 Set<String> availableFqns,
                                 Map<String, PythonProbeBackend.PyAdapterAlias> adapterAliases) {
        List<ModuleBindingCatalogEntry> moduleBindings = snapshot.moduleBindings();
        List<ModuleEventCatalogEntry> moduleEvents = snapshot.moduleEvents();
        if ((moduleBindings == null || moduleBindings.isEmpty())
                && (moduleEvents == null || moduleEvents.isEmpty())) {
            return;
        }

        Map<String, String> fqnBySimpleName = PythonProbeBackend.buildFqnBySimpleName(availableFqns);
        List<RegistryBuilderSurfaceEntry> builders = snapshot.registryBuilderSurfaces();

        Map<String, String> renderedModules = new TreeMap<>();
        Set<String> markerPaths = new TreeSet<>();
        markerPaths.add("nekojs/_modules/__init__.pyi");

        for (ScriptType side : ScriptType.all()) {
            Map<String, ModuleBucket> buckets = collectSideModules(side, moduleBindings, moduleEvents);
            if (buckets.isEmpty()) {
                continue;
            }
            String sidePrefix = "nekojs/_modules/" + side.name;
            markerPaths.add(sidePrefix + "/__init__.pyi");

            for (var entry : buckets.entrySet()) {
                String subpath = entry.getKey();
                ModuleBucket bucket = entry.getValue();
                addAncestorMarkers(sidePrefix, subpath, markerPaths);

                String moduleFile = sidePrefix + "/" + subpath + "/__init__.pyi";
                renderedModules.put(moduleFile, renderModule(
                        side, bucket, typeR, availableFqns, fqnBySimpleName, adapterAliases, builders));
            }
        }

        if (renderedModules.isEmpty()) {
            return;
        }

        Map<String, String> orderedOut = new TreeMap<>();
        for (String marker : markerPaths) {
            orderedOut.put(marker, MARKER_TEXT);
        }
        orderedOut.putAll(renderedModules);
        files.putAll(orderedOut);
    }

    private static Map<String, ModuleBucket> collectSideModules(ScriptType side,
                                                                List<ModuleBindingCatalogEntry> moduleBindings,
                                                                List<ModuleEventCatalogEntry> moduleEvents) {
        Map<String, ModuleBucket> buckets = new TreeMap<>();
        if (moduleBindings != null) {
            for (ModuleBindingCatalogEntry entry : moduleBindings) {
                if (entry == null || entry.moduleId() == null || entry.moduleId().isBlank()
                        || entry.binding() == null || entry.binding().scriptType() != side) {
                    continue;
                }
                String subpath = moduleSubpath(entry.moduleId());
                ModuleBucket bucket = buckets.computeIfAbsent(subpath, ignored -> new ModuleBucket());
                bucket.moduleIds.add(entry.moduleId().trim());
                bucket.bindings.add(entry.binding());
            }
        }
        if (moduleEvents != null) {
            for (ModuleEventCatalogEntry entry : moduleEvents) {
                if (entry == null || entry.moduleId() == null || entry.moduleId().isBlank()
                        || entry.event() == null || !matchesSide(entry, side)) {
                    continue;
                }
                String subpath = moduleSubpath(entry.moduleId());
                ModuleBucket bucket = buckets.computeIfAbsent(subpath, ignored -> new ModuleBucket());
                bucket.moduleIds.add(entry.moduleId().trim());
                bucket.events.add(entry.event());
            }
        }
        return buckets;
    }

    private static boolean matchesSide(ModuleEventCatalogEntry entry, ScriptType side) {
        if (entry.scriptType() != null) {
            return entry.scriptType() == side;
        }
        return entry.event().scriptType() != null && entry.event().scriptType().test(side);
    }

    private static void addAncestorMarkers(String sidePrefix, String subpath, Set<String> markerPaths) {
        String[] segments = subpath.split("/");
        StringBuilder acc = new StringBuilder(sidePrefix);
        for (int i = 0; i < segments.length - 1; i++) {
            acc.append('/').append(segments[i]);
            markerPaths.add(acc + "/__init__.pyi");
        }
    }

    /**
     * Maps a raw API {@code moduleId} (such as {@code "nekojs/api/stable"} or {@code "nekojs:event/stable/addon"})
     * to a safe Python subpackage path relative to {@code nekojs/_modules/<side>/}.
     */
    static String moduleSubpath(String moduleId) {
        String stripped = moduleId.trim();
        if (stripped.startsWith("nekojs:")) {
            stripped = stripped.substring("nekojs:".length());
        } else if (stripped.startsWith("nekojs/")) {
            stripped = stripped.substring("nekojs/".length());
        }
        String[] rawSegments = stripped.split("[:/]+");
        List<String> segments = new ArrayList<>(rawSegments.length);
        for (String seg : rawSegments) {
            if (!seg.isEmpty()) {
                segments.add(sanitizePackageSegment(seg));
            }
        }
        if (segments.isEmpty()) {
            segments.add("root");
        }
        return String.join("/", segments);
    }

    private static String sanitizePackageSegment(String segment) {
        StringBuilder sb = new StringBuilder(segment.length());
        for (int i = 0; i < segment.length();) {
            int cp = segment.codePointAt(i);
            if (cp == '-' || cp == '.' || cp == '$') {
                sb.append('_');
            } else if (sb.isEmpty()
                    ? (cp == '_' || Character.isUnicodeIdentifierStart(cp))
                    : (Character.isUnicodeIdentifierPart(cp) && !Character.isIdentifierIgnorable(cp))) {
                sb.appendCodePoint(cp);
            } else if (sb.isEmpty() && Character.isUnicodeIdentifierPart(cp)) {
                sb.append('_').appendCodePoint(cp);
            } else {
                sb.append('_');
            }
            i += Character.charCount(cp);
        }
        String sanitized = sb.isEmpty() ? "_" : sb.toString();
        return PythonClassRenderer.pyIdent(sanitized);
    }

    private static String renderModule(ScriptType side,
                                       ModuleBucket bucket,
                                       ApiTypeRefPyRenderer typeR,
                                       Set<String> availableFqns,
                                       Map<String, String> fqnBySimpleName,
                                       Map<String, PythonProbeBackend.PyAdapterAlias> adapterAliases,
                                       List<RegistryBuilderSurfaceEntry> builders) {
        List<BindingCatalogEntry> sortedBindings = deduplicateBindings(bucket.bindings);
        Map<String, List<EventCatalogEntry>> eventsByGroup = groupAndSortEvents(bucket.events);
        List<EventCatalogEntry> allEvents = eventsByGroup.values().stream()
                .flatMap(List::stream)
                .toList();

        Map<Class<?>, EventPayload> payloads = allEvents.isEmpty()
                ? Map.of()
                : EventPayloadDeclarations.resolve(allEvents, builders);
        List<String> builderNames = EventPayloadDeclarations.builderNames(payloads, builders);

        Set<String> reserved = new LinkedHashSet<>(
                List.of("Any", "Callable", "Literal", "overload", "Protocol", "_EventPriority"));
        for (BindingCatalogEntry b : sortedBindings) {
            reserved.add(PythonClassRenderer.pyIdent(b.name()));
        }
        payloads.values().forEach(payload -> reserved.addAll(payload.declarationNames()));
        boolean emptySelectorCatalog = PythonEventRenderer.hasEmptySelectorCatalog(payloads);
        if (emptySelectorCatalog) {
            reserved.add("NoReturn");
        }
        reserved.addAll(builderNames);

        Map<String, String> groupClassNames = new LinkedHashMap<>();
        for (String group : eventsByGroup.keySet()) {
            String exportGroup = PythonClassRenderer.pyIdent(group);
            reserved.add(exportGroup);
            String baseClass = "_" + exportGroup + "Group";
            String className = baseClass;
            for (int suffix = 1; !reserved.add(className); suffix++) {
                className = baseClass + suffix;
            }
            groupClassNames.put(group, className);
        }

        PythonImports importByName = new PythonImports(reserved, typeR);
        List<String[]> renderedBindings = new ArrayList<>(sortedBindings.size());
        Set<String> allExports = new LinkedHashSet<>();

        for (BindingCatalogEntry b : sortedBindings) {
            String exportName = PythonClassRenderer.pyIdent(b.name());
            String pyType = resolveBindingType(b, availableFqns, fqnBySimpleName, importByName, typeR);
            renderedBindings.add(new String[]{exportName, pyType});
            allExports.add(exportName);
        }

        Map<String, List<PendingEvent>> pendingByGroup = new LinkedHashMap<>();
        for (var groupEntry : eventsByGroup.entrySet()) {
            List<PendingEvent> pending = new ArrayList<>(groupEntry.getValue().size());
            for (EventCatalogEntry e : groupEntry.getValue()) {
                EventPayload payload = payloads.get(e.eventType());
                String fixedEventType = payload != null ? (payload.selectorScoped() ? "Any" : payload.name())
                        : (e.eventType() == null ? "Any" : null);
                ApiTypeRef eventRef = fixedEventType == null ? PythonEventRenderer.classToTypeRef(e.eventType()) : null;
                if (eventRef != null) {
                    importSymbolsIfAvailable(eventRef, availableFqns, importByName);
                }

                String fixedKeyType = null;
                ApiTypeRef keyRef = null;
                if (e.dispatchable() && e.dispatchKeyType() != null && (payload == null || !payload.selectorScoped())) {
                    ApiTypeRef ref = PythonEventRenderer.classToTypeRef(e.dispatchKeyType());
                    if (ref.kind() == ApiTypeRef.Kind.SYMBOL) {
                        String fqn = ApiTypeRefPyRenderer.extractFqn(ref.name());
                        PythonProbeBackend.PyAdapterAlias alias =
                                adapterAliases == null ? null : adapterAliases.get(fqn);
                        if (alias != null && availableFqns.contains(fqn)) {
                            fixedKeyType = importByName.symbol(fqn, alias.aliasName());
                        }
                    }
                    if (fixedKeyType == null) {
                        importSymbolsIfAvailable(ref, availableFqns, importByName);
                        keyRef = ref;
                    }
                }
                pending.add(new PendingEvent(e, fixedEventType, eventRef, fixedKeyType, keyRef));
            }
            pendingByGroup.put(groupEntry.getKey(), pending);
        }

        ApiTypeRefPyRenderer moduleTypeR = typeR.withSymbolNames(importByName.typeNames());
        Map<String, List<EventInfo>> infosByGroup = new LinkedHashMap<>();
        for (var groupEntry : pendingByGroup.entrySet()) {
            List<EventInfo> infos = new ArrayList<>(groupEntry.getValue().size());
            for (PendingEvent p : groupEntry.getValue()) {
                String eventType = p.fixedEventType() != null
                        ? p.fixedEventType()
                        : moduleTypeR.render(p.eventRef());
                String keyType = p.fixedKeyType() != null
                        ? p.fixedKeyType()
                        : (p.keyRef() != null ? moduleTypeR.render(p.keyRef()) : null);
                infos.add(new EventInfo(p.entry(), eventType, keyType));
            }
            infosByGroup.put(groupEntry.getKey(), infos);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Auto-generated by NekoJS probe — Python stubs for ")
                .append(side.name).append(" API module.\n");
        sb.append("# Module: ").append(String.join(", ", bucket.moduleIds)).append('\n');
        sb.append("# Do not edit; regenerate with `/nekojs probe python`.\n\n");

        if (allEvents.isEmpty()) {
            sb.append("from typing import Any\n");
        } else {
            sb.append("from typing import Any, Callable, Literal, overload")
                    .append(payloads.isEmpty() ? "" : ", Protocol")
                    .append(emptySelectorCatalog ? ", NoReturn" : "")
                    .append('\n');
        }
        if (!builderNames.isEmpty()) {
            sb.append("from nekojs._registry_builders import ")
                    .append(String.join(", ", builderNames)).append('\n');
        }
        importByName.appendTo(sb);

        if (!allEvents.isEmpty()) {
            sb.append('\n').append(PythonEventRenderer.PRIORITY_ALIAS_LINE);
        }

        PythonEventRenderer.appendPayloadProtocols(sb, payloads);

        for (var groupEntry : infosByGroup.entrySet()) {
            String group = groupEntry.getKey();
            String className = groupClassNames.get(group);
            sb.append("\nclass ").append(className).append(":\n");
            for (EventInfo info : groupEntry.getValue()) {
                EventPayload payload = payloads.get(info.entry().eventType());
                if (info.entry().selectorRequired() || (payload != null && payload.selectorScoped())) {
                    PythonEventRenderer.appendRequiredSelectors(sb, info.entry(), payload,
                            info.eventType(), info.keyType(), false);
                } else {
                    appendEventMethods(sb, info);
                }
            }
        }

        if (!renderedBindings.isEmpty() || !infosByGroup.isEmpty()) {
            sb.append('\n');
            for (String[] b : renderedBindings) {
                sb.append(b[0]).append(": ").append(b[1]).append('\n');
            }
            for (String group : infosByGroup.keySet()) {
                String exportGroup = PythonClassRenderer.pyIdent(group);
                if (allExports.add(exportGroup)) {
                    sb.append(exportGroup).append(": ").append(groupClassNames.get(group)).append('\n');
                }
            }
        }

        sb.append("\n__all__ = [");
        boolean first = true;
        for (String name : allExports) {
            if (!first) sb.append(", ");
            first = false;
            sb.append('"').append(name).append('"');
        }
        sb.append("]\n");
        return sb.toString();
    }

    private static void appendEventMethods(StringBuilder sb, EventInfo info) {
        String name = PythonClassRenderer.pyIdent(info.entry().name());
        if (info.entry().scriptDefined()) {
            sb.append("    class ").append(name).append(":\n");
            sb.append("        @overload\n");
            sb.append("        def __call__(self, handler: Callable[[Any], None]) -> None: ...\n");
            sb.append("        @overload\n");
            sb.append("        def __call__(self, priority: _EventPriority, handler: Callable[[Any], None]) -> None: ...\n");
            sb.append("        def post(self, payload: Any = ...) -> None: ...\n");
            sb.append("    ").append(name).append(": ").append(name).append('\n');
            return;
        }

        String callbackReturn = info.entry().cancellable() ? "bool | None" : "None";
        String handlerType = "Callable[[" + info.eventType() + "], " + callbackReturn + "]";

        sb.append("    @overload\n");
        sb.append("    def ").append(name)
                .append("(self, handler: ").append(handlerType).append(") -> None: ...\n");
        sb.append("    @overload\n");
        sb.append("    def ").append(name)
                .append("(self, priority: _EventPriority, handler: ").append(handlerType).append(") -> None: ...\n");

        if (info.keyType() != null) {
            sb.append("    @overload\n");
            sb.append("    def ").append(name)
                    .append("(self, extra: ").append(info.keyType())
                    .append(", handler: ").append(handlerType).append(") -> None: ...\n");
            sb.append("    @overload\n");
            sb.append("    def ").append(name)
                    .append("(self, priority: _EventPriority, extra: ").append(info.keyType())
                    .append(", handler: ").append(handlerType).append(") -> None: ...\n");
        }
    }

    private static String resolveBindingType(BindingCatalogEntry b,
                                             Set<String> availableFqns,
                                             Map<String, String> fqnBySimpleName,
                                             PythonImports importByName,
                                             ApiTypeRefPyRenderer baseTypeR) {
        if (b.typeOverride() != null && !b.typeOverride().isEmpty()) {
            String fqn = fqnBySimpleName.get(b.typeOverride());
            if (fqn != null && availableFqns.contains(fqn)) {
                String imported = importByName.type(fqn);
                return b.staticClass() ? "type[" + imported + "]" : imported;
            }
        }
        Class<?> cls = b.javaType();
        if (cls == null) {
            return "Any";
        }
        if (!b.staticClass()) {
            if (cls == String.class || cls == char.class || cls == Character.class) return "str";
            if (cls == boolean.class || cls == Boolean.class) return "bool";
            if (cls == float.class || cls == Float.class || cls == double.class || cls == Double.class) return "float";
            if (cls.isPrimitive() || (Number.class.isAssignableFrom(cls) && !availableFqns.contains(cls.getName()))) {
                return "int";
            }
            if (cls == Object.class) return "Any";
            if (cls.isArray()) {
                ApiTypeRef arrayRef = PythonEventRenderer.classToTypeRef(cls);
                importSymbolsIfAvailable(arrayRef, availableFqns, importByName);
                return baseTypeR.withSymbolNames(importByName.typeNames()).render(arrayRef);
            }
        }
        String fqn = cls.getName();
        if (availableFqns.contains(fqn)) {
            String imported = importByName.type(fqn);
            return b.staticClass() ? "type[" + imported + "]" : imported;
        }
        return "Any";
    }

    private static void importSymbolsIfAvailable(ApiTypeRef ref,
                                                 Set<String> availableFqns,
                                                 PythonImports importByName) {
        Set<String> symbols = new TreeSet<>();
        ApiTypeRefPyRenderer.collectSymbolFqns(ref, symbols);
        for (String fqn : symbols) {
            if (availableFqns.contains(fqn)) {
                importByName.type(fqn);
            }
        }
    }

    private static List<BindingCatalogEntry> deduplicateBindings(List<BindingCatalogEntry> raw) {
        if (raw.isEmpty()) return List.of();
        List<BindingCatalogEntry> sorted = raw.stream()
                .sorted(Comparator.comparing(BindingCatalogEntry::name))
                .toList();
        Map<String, BindingCatalogEntry> byIdent = new LinkedHashMap<>();
        for (BindingCatalogEntry b : sorted) {
            byIdent.putIfAbsent(PythonClassRenderer.pyIdent(b.name()), b);
        }
        return List.copyOf(byIdent.values());
    }

    private static Map<String, List<EventCatalogEntry>> groupAndSortEvents(List<EventCatalogEntry> raw) {
        if (raw.isEmpty()) return Map.of();
        Map<String, Map<String, EventCatalogEntry>> grouped = new TreeMap<>();
        List<EventCatalogEntry> sorted = raw.stream()
                .sorted(Comparator.comparing(EventCatalogEntry::group)
                        .thenComparing(EventCatalogEntry::name))
                .toList();
        for (EventCatalogEntry e : sorted) {
            grouped.computeIfAbsent(e.group(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(PythonClassRenderer.pyIdent(e.name()), e);
        }
        Map<String, List<EventCatalogEntry>> out = new LinkedHashMap<>();
        grouped.forEach((group, byName) -> out.put(group, List.copyOf(byName.values())));
        return out;
    }

    private static final class ModuleBucket {
        final Set<String> moduleIds = new LinkedHashSet<>();
        final List<BindingCatalogEntry> bindings = new ArrayList<>();
        final List<EventCatalogEntry> events = new ArrayList<>();
    }

    private record PendingEvent(EventCatalogEntry entry, String fixedEventType, ApiTypeRef eventRef,
                                String fixedKeyType, ApiTypeRef keyRef) {
    }

    private record EventInfo(EventCatalogEntry entry, String eventType, String keyType) {
    }
}
