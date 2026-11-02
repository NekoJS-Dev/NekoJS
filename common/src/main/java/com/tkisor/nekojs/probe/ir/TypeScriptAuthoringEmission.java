package com.tkisor.nekojs.probe.ir;

import com.google.gson.*;
import com.tkisor.nekojs.authoring.AuthoringJson;

import java.util.*;

/** Collects already-rendered member decisions without reflecting or parsing TypeScript fragments. */
public final class TypeScriptAuthoringEmission {
    public record Declaration(String text, List<JsonObject> members, boolean complete) {}
    private final String owner;
    private final Map<String, JsonObject> members = new LinkedHashMap<>();
    private boolean complete = true;

    TypeScriptAuthoringEmission(String owner) { this.owner = owner; }

    void incomplete() { complete = false; }

    public static String classId(String fqn) { return "class:" + AuthoringJson.hash(fqn); }

    public JsonObject type(String display) {
        // Primitive spellings are global. Compound spellings are scoped to their emitted module.
        // This deliberately does not assert semantic equivalence between different aliases.
        String context = Set.of("string", "number", "boolean", "void", "never", "unknown", "any", "null", "undefined").contains(display)
                ? "" : owner.substring(0, Math.max(0, owner.lastIndexOf('.'))) + ":";
        return AuthoringJson.obj("id", "type:" + AuthoringJson.hash(context + display), "display", display);
    }

    void method(MethodDecl method, String name, boolean isStatic, List<String> parameterTypes, String result, String signature) {
        if (name == null) { incomplete(); return; }
        JsonObject record = base(method.isConstructor ? "constructor" : "method", name, isStatic, signature);
        JsonArray parameters = new JsonArray();
        int min = 0;
        for (int i = 0; i < method.params.size(); i++) {
            MethodDecl.MethodParam parameter = method.params.get(i);
            // The current renderer emits Java varargs as one optional array parameter, not TS rest syntax.
            if (!parameter.optional && !parameter.varargs) min = i + 1;
            parameters.add(AuthoringJson.obj("position", i, "name", parameter.name, "type", type(parameterTypes.get(i)),
                    "optional", parameter.optional || parameter.varargs, "varargs", parameter.varargs));
        }
        record.add("parameters", parameters);
        record.addProperty("parameterCount", method.params.size());
        record.add("callArity", AuthoringJson.obj("min", min, "max", AuthoringJson.obj("kind", "finite", "value", method.params.size())));
        if (!method.isConstructor) record.add("returnType", type(result));
        record.add("docs", AuthoringJson.GSON.toJsonTree(method.docs));
        add(record);
        if (!method.overloads.isEmpty()) incomplete();
    }

    void property(String name, boolean isStatic, String read, String write, String signature) {
        JsonObject record = base("property", name, isStatic, signature);
        record.addProperty("readable", read != null);
        record.addProperty("writable", write != null);
        if (read != null) record.add("type", type(read));
        record.addProperty("coverage", "complete");
        String key = "property:" + isStatic + ":" + name;
        JsonObject previous = members.get(key);
        if (previous != null) {
            if (read != null) { previous.add("type", type(read)); previous.addProperty("readable", true); }
            if (write != null) previous.addProperty("writable", true);
            return;
        }
        record.addProperty("symbolId", "member:" + AuthoringJson.hash(owner + ":" + key));
        members.put(key, record);
    }

    void field(FieldDecl field, boolean isStatic, String type, String signature) {
        JsonObject record = base("field", field.effectiveName(), isStatic, signature);
        record.add("type", type(type));
        record.addProperty("readable", true);
        // Final host fields are not writable even where the existing TS declaration lacks readonly.
        record.addProperty("writable", !field.isFinal);
        record.add("docs", AuthoringJson.GSON.toJsonTree(field.docs));
        add(record);
    }

    private JsonObject base(String kind, String name, boolean isStatic, String signature) {
        return AuthoringJson.obj("symbolId", "member:" + AuthoringJson.hash(owner + ":" + kind + ":" + isStatic + ":" + signature),
                "ownerSymbolId", classId(owner), "declaringType", AuthoringJson.obj("id", classId(owner)),
                "name", name, "kind", kind, "isStatic", isStatic, "signature", signature.strip(), "coverage", "complete");
    }
    private void add(JsonObject record) { members.put(record.get("symbolId").getAsString(), record); }
    Declaration finish(String text) { return new Declaration(text, List.copyOf(members.values()), complete); }
}
