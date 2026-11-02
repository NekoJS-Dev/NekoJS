package com.tkisor.nekojs.probe.backend.typescript;

import com.google.gson.*;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.authoring.*;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.ir.TypeScriptAuthoringEmission;

import java.util.*;

/** Packages final emitted declarations and emission-site member records into a detached snapshot. */
final class TypeScriptAuthoringIndex {
    private TypeScriptAuthoringIndex() {}

    /** Consumes detached emission records; callers must not read or reuse the transferred records. */
    static JsonObject consumeOwnedEmissions(ProbeContext context, Map<String, String> files,
                            Map<String, TypeScriptAuthoringEmission.Declaration> emitted) {
        JsonArray symbols = new JsonArray();
        JsonArray members = new JsonArray();
        JsonArray declarations = new JsonArray();
        JsonArray events = new JsonArray();
        JsonArray adapters = new JsonArray();
        List<String> allTypes = context.snapshot().scriptTypes().stream().map(Enum::name).sorted().toList();
        Map<String, JsonObject> sources = new TreeMap<>();
        Map<String, JsonObject> classSources = new TreeMap<>();
        new TreeMap<>(files).forEach((path, text) -> {
            if (!path.endsWith(".d.ts")) return;
            JsonObject source = AuthoringJson.obj("path", path, "sha256", AuthoringJson.hash(text));
            sources.put(path, source);
            List<String> types = allTypes;
            if (path.startsWith("@side-only/")) {
                String side = path.substring("@side-only/".length()).split("/")[0].toUpperCase(Locale.ROOT);
                types = allTypes.contains(side) ? List.of(side) : List.of();
            }
            declarations.add(AuthoringJson.obj("symbolId", "declaration:" + AuthoringJson.hash(path), "name", path,
                    "kind", "declaration", "path", path, "sha256", AuthoringJson.hash(text), "text", text,
                    "scriptTypes", types, "source", source, "coverage", "complete"));
        });
        new TreeMap<>(emitted).forEach((fqn, declaration) -> {
            if (fqn.lastIndexOf('.') < 0) return;
            String path = "@package/" + fqn.substring(0, fqn.lastIndexOf('.')).replace('.', '/') + "/index.d.ts";
            JsonObject source = sources.get(path);
            if (source == null) return;
            classSources.put(fqn, source);
            symbols.add(AuthoringJson.obj("symbolId", TypeScriptAuthoringEmission.classId(fqn), "name", fqn.substring(fqn.lastIndexOf('.') + 1),
                    "qualifiedName", fqn, "package", fqn.substring(0, fqn.lastIndexOf('.')), "kind", "class",
                    "scriptTypes", allTypes, "source", source, "declarationId", "declaration:" + AuthoringJson.hash(path),
                    "coverage", declaration.complete() ? "complete" : "incomplete"));
            for (JsonObject entry : declaration.members()) {
                JsonObject member = entry;
                member.add("scriptTypes", AuthoringJson.GSON.toJsonTree(allTypes));
                member.add("source", source.deepCopy());
                members.add(member);
            }
        });
        for (var binding : context.snapshot().bindings()) {
            if (!binding.emit()) continue;
            String path = "@side-only/" + binding.scriptType().name + "/bindings/index.d.ts";
            JsonObject source = sources.get(path);
            if (source == null) continue;
            String id = "binding:" + AuthoringJson.hash(binding.scriptType().name() + ":" + binding.name());
            boolean available = binding.javaType() != null && classSources.containsKey(binding.javaType().getName());
            // A TypeDoc alias forwards to the instance type even when the runtime binding is static.
            boolean staticView = binding.staticClass() && (binding.typeOverride() == null || binding.typeOverride().isEmpty());
            List<String> types = List.of(binding.scriptType().name());
            symbols.add(AuthoringJson.obj("symbolId", id, "name", binding.name(), "kind", "binding", "scriptTypes", types,
                    "source", source, "staticClass", binding.staticClass(), "coverage", "incomplete",
                    "docs", binding.description() == null ? List.of() : List.of(binding.description()),
                    "incompleteReasons", List.of("Binding inheritance and delegated collision resolution are not flattened.")));
            if (available) copyBindingMembers(members, emitted.get(binding.javaType().getName()), id, staticView, types,
                    classSources.get(binding.javaType().getName()));
            // Repeated delegate sources describe one intersection member, not additional overloads.
            for (Class<?> extra : new LinkedHashSet<>(binding.extraDocTypes())) {
                if (staticView && extra == binding.javaType()) continue;
                var declaration = emitted.get(extra.getName());
                if (declaration != null && classSources.containsKey(extra.getName())) {
                    copyBindingMembers(members, declaration, id, true, types, classSources.get(extra.getName()));
                }
            }
        }
        for (var event : context.snapshot().events()) {
            List<String> types = context.snapshot().scriptTypes().stream().filter(event.scriptType()::test).map(Enum::name).sorted().toList();
            events.add(AuthoringJson.obj("symbolId", "event:" + AuthoringJson.hash(event.group() + ":" + event.name() + ":" + String.join(",", types)),
                    "name", event.name(), "kind", "event", "group", event.group(), "cancellable", event.cancellable(),
                    "scriptTypes", types, "coverage", "incomplete", "incompleteReasons", List.of("Read the emitted event declaration for its callback signature.")));
        }
        for (var adapter : context.snapshot().adapters()) {
            // Only expose adapters whose target has an emitted public class declaration.
            if (!emitted.containsKey(adapter.targetType().getName())) continue;
            adapters.add(AuthoringJson.obj("symbolId", "adapter:" + AuthoringJson.hash(adapter.targetType().getName()),
                    "name", adapter.targetType().getSimpleName(), "kind", "inputAdapter", "targetType", adapter.targetType().getName(),
                    "precedence", adapter.precedence().name(), "syntax", adapter.syntaxDoc().orElse(null),
                    "scriptTypes", allTypes, "coverage", "incomplete", "incompleteReasons", List.of("Input aliases are available in the emitted target package declaration.")));
        }
        JsonObject environment = AuthoringEnvironment.current();
        // These arrays are owned by this snapshot; converting them through Gson would copy the entire member tree.
        JsonObject resources = new JsonObject();
        resources.add("symbols", symbols);
        resources.add("members", members);
        resources.add("events", events);
        resources.add("inputAdapters", adapters);
        resources.add("declarations", declarations);
        JsonObject document = AuthoringJson.obj("schemaVersion", "1", "environmentId", AuthoringEnvironment.id(environment), "environment", environment,
                "language", "typescript", "complete", false, "coverageNotes", List.of("Declared emission records only; authored declarations, literal overloads, capture views, and inherited generic views remain available as declaration text."));
        document.add("resources", resources);
        document.addProperty("snapshotId", "snapshot:" + AuthoringJson.hashJson(document));
        return document;
    }

    private static void copyBindingMembers(JsonArray members, TypeScriptAuthoringEmission.Declaration declaration,
                                            String owner, boolean staticMembers, List<String> types, JsonObject source) {
        for (JsonObject record : declaration.members()) {
            if (record.get("isStatic").getAsBoolean() != staticMembers || record.get("kind").getAsString().equals("constructor")) continue;
            JsonObject copy = record.deepCopy();
            copy.addProperty("symbolId", "member:" + AuthoringJson.hash(owner + ":" + record.get("symbolId").getAsString()));
            copy.addProperty("ownerSymbolId", owner);
            copy.addProperty("isStatic", false);
            copy.add("scriptTypes", AuthoringJson.GSON.toJsonTree(types));
            copy.add("source", source.deepCopy());
            members.add(copy);
        }
    }
}
