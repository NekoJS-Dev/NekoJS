package com.tkisor.nekojs.authoring;

import com.google.gson.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.*;

/** Bounded read-only queries over retained immutable snapshots, shared by HTTP and MCP. */
public final class AuthoringQuery implements AutoCloseable {
    private enum Truth { TRUE, FALSE, UNKNOWN }
    @FunctionalInterface private interface Predicate { Truth test(JsonObject record); }
    private final LinkedHashMap<String, AuthoringSnapshot> snapshots = new LinkedHashMap<>();
    private final String environmentId;
    private final byte[] cursorKey = new byte[32];
    private final Clock clock;
    private volatile AuthoringSnapshot current;
    private boolean closed;

    public AuthoringQuery(String environmentId) { this(environmentId, Clock.systemUTC()); }
    AuthoringQuery(String environmentId, Clock clock) {
        this.environmentId = Objects.requireNonNull(environmentId);
        this.clock = clock;
        new SecureRandom().nextBytes(cursorKey);
    }

    public synchronized void publish(AuthoringSnapshot snapshot) {
        if (closed) throw new IllegalStateException("[NEKO-4055] Authoring query is closed.");
        if (!environmentId.equals(snapshot.environmentId())) throw AuthoringJson.invalid("/environmentId", "Snapshot belongs to another environment.");
        AuthoringSnapshot retained = snapshots.get(snapshot.id());
        if (retained != null && !retained.document().equals(snapshot.document())) {
            throw AuthoringJson.invalid("/snapshotId", "Snapshot ID is already bound to different content.");
        }
        snapshots.put(snapshot.id(), snapshot);
        current = snapshot;
        while (snapshots.size() > 3) snapshots.remove(snapshots.keySet().iterator().next());
    }

    public JsonObject context() {
        AuthoringSnapshot snapshot = current;
        return AuthoringJson.obj("schemaVersion", "1", "environmentId", environmentId,
                "snapshotId", snapshot == null ? null : snapshot.id(), "indexState", snapshot == null ? "not-ready" : "ready",
                "languages", List.of("typescript"), "memberViews", List.of("declared"),
                "readOnly", true);
    }

    public JsonObject capabilities() {
        return AuthoringJson.obj("schemaVersion", "1", "operations", List.of("context", "capabilities", "schema", "resolve", "members", "query", "batch"),
                "languages", List.of("typescript"), "memberViews", List.of("declared"),
                "fields", AuthoringFields.catalog(), "limits", AuthoringJson.obj("pageSize", 100,
                        "predicateNodes", 32, "predicateDepth", 4, "inValues", 100, "batchSize", 10),
                "unsupported", List.of("effective-inherited-generic-view", "assignableTo", "python-members", "runtime-evaluation", "reload", "script-writes"));
    }

    public JsonObject execute(String operation, JsonObject request) {
        return switch (operation) {
            case "context" -> { AuthoringJson.keys(request, ""); yield context(); }
            case "capabilities" -> { AuthoringJson.keys(request, ""); yield capabilities(); }
            case "schema" -> { AuthoringJson.keys(request, ""); yield AuthoringSchema.document(); }
            case "query" -> query(request.deepCopy());
            case "resolve", "members" -> simple(operation, request.deepCopy());
            case "batch" -> batch(request);
            default -> throw new AuthoringException(404, "UNKNOWN_OPERATION", "/", "Unknown authoring operation.");
        };
    }

    private JsonObject simple(String operation, JsonObject request) {
        boolean members = operation.equals("members");
        if (members) AuthoringJson.keys(request, "", "scope", "name", "kind", "match", "arity", "select", "page");
        else AuthoringJson.keys(request, "", "scope", "name", "kind", "match", "select", "page");
        JsonObject normalized = AuthoringJson.obj("scope", AuthoringJson.object(request, "scope", ""), "from", members ? "members" : "symbols");
        JsonArray conditions = new JsonArray();
        if (request.has("kind")) conditions.add(AuthoringJson.obj("field", "kind", "op", "eq", "value", AuthoringJson.string(request, "kind", "")));
        if (request.has("name")) {
            String match = request.has("match") ? AuthoringJson.string(request, "match", "") : "exact";
            if (!Set.of("exact", "prefix").contains(match)) throw AuthoringJson.invalid("/match", "Unsupported name match mode.");
            conditions.add(AuthoringJson.obj("field", "name", "op", match.equals("exact") ? "eq" : "prefix", "value", AuthoringJson.string(request, "name", "")));
        } else if (request.has("match")) throw AuthoringJson.invalid("/match", "Name is required with match.");
        if (request.has("arity")) {
            if (!members) throw AuthoringJson.invalid("/arity", "Arity applies only to members.");
            JsonObject arity = AuthoringJson.object(request, "arity", "");
            AuthoringJson.keys(arity, "/arity", "min", "max");
            int min = AuthoringJson.integer(arity.get("min"), "/arity/min", 0, 10000);
            int max = AuthoringJson.integer(arity.get("max"), "/arity/max", min, 10000);
            conditions.add(AuthoringJson.obj("field", "callArity.min", "op", "lte", "value", max));
            conditions.add(AuthoringJson.obj("field", "callArity.max", "op", "gte", "value", min));
        }
        if (!conditions.isEmpty()) normalized.add("where", AuthoringJson.obj("all", conditions));
        for (String key : List.of("select", "page")) if (request.has(key)) normalized.add(key, request.get(key));
        return query(normalized);
    }

    private JsonObject batch(JsonObject request) {
        AuthoringJson.keys(request, "", "scope", "queries");
        JsonObject scope = AuthoringJson.object(request, "scope", "").deepCopy();
        validateScope(scope);
        AuthoringSnapshot snapshot = snapshot(scope);
        scope.addProperty("snapshotId", snapshot.id());
        if (!request.has("queries") || !request.get("queries").isJsonArray()) throw AuthoringJson.invalid("/queries", "Expected query array.");
        JsonArray queries = request.getAsJsonArray("queries");
        if (queries.isEmpty() || queries.size() > 10) throw AuthoringJson.invalid("/queries", "Batch size must be between 1 and 10.");
        JsonArray results = new JsonArray();
        for (int i = 0; i < queries.size(); i++) {
            try {
                JsonElement item = queries.get(i);
                if (!item.isJsonObject()) throw AuthoringJson.invalid("/queries/" + i, "Expected query object.");
                JsonObject child = item.getAsJsonObject().deepCopy();
                if (child.has("scope")) throw AuthoringJson.invalid("/queries/" + i + "/scope", "Batch entries share the outer scope.");
                child.add("scope", scope.deepCopy());
                results.add(AuthoringJson.obj("status", "ok", "result", query(child)));
            } catch (AuthoringException e) { results.add(AuthoringJson.obj("status", "error", "error", error(e))); }
        }
        return AuthoringJson.obj("snapshotId", snapshot.id(), "results", results);
    }

    private synchronized AuthoringSnapshot snapshot(JsonObject scope) {
        if (closed) throw new AuthoringException(503, "SERVICE_CLOSED", "/", "Authoring service is closed.");
        if (!environmentId.equals(AuthoringJson.string(scope, "environmentId", "/scope"))) throw new AuthoringException(409, "SCOPE_CONFLICT", "/scope/environmentId", "Environment does not match this instance.");
        String id = scope.has("snapshotId") ? AuthoringJson.string(scope, "snapshotId", "/scope") : current == null ? null : current.id();
        if (id == null) throw new AuthoringException(503, "INDEX_NOT_READY", "/scope/snapshotId", "Run the TypeScript Probe to generate an authoring snapshot.");
        AuthoringSnapshot found = snapshots.get(id);
        if (found == null) throw new AuthoringException(410, "SNAPSHOT_EXPIRED", "/scope/snapshotId", "Snapshot is no longer retained; refresh context.");
        return found;
    }

    private static void validateScope(JsonObject scope) {
        AuthoringJson.keys(scope, "/scope", "environmentId", "snapshotId", "scriptType", "language", "ownerSymbolId", "memberView");
        AuthoringJson.string(scope, "environmentId", "/scope");
        if (scope.has("snapshotId")) AuthoringJson.string(scope, "snapshotId", "/scope");
        if (scope.has("ownerSymbolId")) AuthoringJson.string(scope, "ownerSymbolId", "/scope");
        String language = AuthoringJson.string(scope, "language", "/scope");
        if (!language.equals("typescript")) throw new AuthoringException(422, "UNSUPPORTED_LANGUAGE", "/scope/language", "Structured queries currently support TypeScript only.");
        String scriptType = AuthoringJson.string(scope, "scriptType", "/scope");
        if (!Set.of("STARTUP", "SERVER", "CLIENT", "TEST").contains(scriptType)) throw AuthoringJson.invalid("/scope/scriptType", "Unknown Script Type.");
        if (scope.has("memberView") && !AuthoringJson.string(scope, "memberView", "/scope").equals("declared")) {
            throw new AuthoringException(422, "UNSUPPORTED_MEMBER_VIEW", "/scope/memberView", "Only emitted declared members are indexed; follow declaration sources for inherited signatures.");
        }
    }

    private JsonObject query(JsonObject request) {
        AuthoringJson.keys(request, "", "scope", "from", "where", "select", "orderBy", "page", "explain");
        JsonObject scope = AuthoringJson.object(request, "scope", "");
        validateScope(scope);
        String scriptType = AuthoringJson.string(scope, "scriptType", "/scope");
        if (request.has("explain") && (!request.get("explain").isJsonPrimitive() || !request.getAsJsonPrimitive("explain").isBoolean())) {
            throw AuthoringJson.invalid("/explain", "Expected boolean.");
        }
        JsonObject page = request.has("page") ? AuthoringJson.object(request, "page", "") : new JsonObject();
        AuthoringJson.keys(page, "/page", "size", "cursor");
        int size = page.has("size") ? AuthoringJson.integer(page.get("size"), "/page/size", 1, 100) : 20;
        JsonObject cursor = page.has("cursor") ? decodeCursor(AuthoringJson.string(page, "cursor", "/page")) : null;
        // A continuation resolves omitted snapshot IDs from its authenticated token, never from current.
        if (cursor != null && !scope.has("snapshotId")) scope.add("snapshotId", cursor.get("snapshot").deepCopy());
        AuthoringSnapshot snapshot = snapshot(scope);
        scope.addProperty("snapshotId", snapshot.id());
        String resource = AuthoringJson.string(request, "from", "");
        List<JsonObject> records = snapshot.records(resource);
        String owner = scope.has("ownerSymbolId") ? AuthoringJson.string(scope, "ownerSymbolId", "/scope") : null;
        JsonObject ownerRecord = null;
        if (owner != null) {
            if (!resource.equals("members")) throw AuthoringJson.invalid("/scope/ownerSymbolId", "Owner scope applies only to members.");
            ownerRecord = snapshot.symbol(owner);
            if (ownerRecord == null || !visible(ownerRecord, scriptType)) throw new AuthoringException(404, "OWNER_NOT_FOUND", "/scope/ownerSymbolId", "Owner is not available in this scope.");
        }
        if (owner != null) records = snapshot.members(owner);
        Predicate predicate = request.has("where") ? compile(request.get("where"), resource, "/where", 1, new int[1]) : record -> Truth.TRUE;
        List<String> select = selection(request, resource);
        Comparator<JsonObject> order = ordering(request, resource);
        JsonObject identity = request.deepCopy();
        identity.remove("page");
        String fingerprint = AuthoringJson.hash(AuthoringJson.canonical(identity));
        if (cursor != null && (!snapshot.id().equals(cursor.get("snapshot").getAsString()) || !fingerprint.equals(cursor.get("query").getAsString()))) {
            throw AuthoringJson.invalid("/page/cursor", "Cursor belongs to a different query or snapshot.");
        }
        int offset = cursor == null ? 0 : AuthoringJson.integer(cursor.get("offset"), "/page/cursor", 0, Integer.MAX_VALUE);
        List<JsonObject> matches = new ArrayList<>();
        int unresolved = 0;
        long deadline = System.nanoTime() + 2_000_000_000L;
        int visited = 0;
        for (JsonObject record : records) {
            if ((++visited & 127) == 0 && (System.nanoTime() > deadline || Thread.currentThread().isInterrupted())) {
                throw new AuthoringException(503, "QUERY_BUDGET_EXCEEDED", "/where", "Query budget exceeded; narrow the owner scope or conditions.");
            }
            if (matches.size() > 100_000) throw new AuthoringException(422, "QUERY_TOO_BROAD", "/where", "Query matches too many records; narrow the scope.");
            if (!visible(record, scriptType) || owner != null && !owner.equals(record.has("ownerSymbolId") ? record.get("ownerSymbolId").getAsString() : null)) continue;
            Truth truth = predicate.test(record);
            if (truth == Truth.TRUE) matches.add(record);
            else if (truth == Truth.UNKNOWN) unresolved++;
        }
        matches.sort(order);
        if (offset > matches.size()) throw AuthoringJson.invalid("/page/cursor", "Cursor position is invalid.");
        JsonArray items = new JsonArray();
        int end = Math.min(matches.size(), offset + size);
        for (JsonObject match : matches.subList(offset, end)) items.add(project(match, select));
        boolean complete = snapshot.complete() && unresolved == 0 && (ownerRecord == null || "complete".equals(ownerRecord.get("coverage").getAsString()));
        JsonObject response = AuthoringJson.obj("schemaVersion", "1", "requestId", UUID.randomUUID().toString(),
                "environmentId", environmentId, "snapshotId", snapshot.id(), "status", "ok",
                "coverage", AuthoringJson.obj("state", complete ? "complete" : "incomplete", "scope", "emitted-declared-typescript-surface", "unresolvedRecords", unresolved),
                "items", items, "page", AuthoringJson.obj("nextCursor", end < matches.size() ? encodeCursor(snapshot.id(), fingerprint, end) : null,
                        "endOfResults", end == matches.size(), "stopReason", null), "warnings", complete ? List.of() : List.of("Coverage is incomplete; zero matches do not prove that the API is absent."));
        if (request.has("explain")) {
            if (request.get("explain").getAsBoolean()) response.add("explain", AuthoringJson.obj("scope", scope, "from", resource, "matchMode", "exact-conditions", "select", select));
        }
        return response;
    }

    private static boolean visible(JsonObject record, String type) {
        JsonElement types = record.get("scriptTypes");
        return types != null && types.isJsonArray() && types.getAsJsonArray().contains(new JsonPrimitive(type));
    }

    private Predicate compile(JsonElement value, String resource, String path, int depth, int[] count) {
        if (depth > 4 || ++count[0] > 32) throw AuthoringJson.invalid(path, "Predicate complexity exceeds the limit.");
        if (!value.isJsonObject()) throw AuthoringJson.invalid(path, "Expected a predicate object.");
        JsonObject node = value.getAsJsonObject();
        for (String group : List.of("all", "any")) if (node.has(group)) {
            AuthoringJson.keys(node, path, group);
            if (!node.get(group).isJsonArray() || node.getAsJsonArray(group).isEmpty()) throw AuthoringJson.invalid(path, "Boolean groups require at least one predicate.");
            List<Predicate> children = new ArrayList<>();
            JsonArray predicates = node.getAsJsonArray(group);
            for (int i = 0; i < predicates.size(); i++) children.add(compile(predicates.get(i), resource, path + "/" + group + "/" + i, depth + 1, count));
            return record -> {
                boolean unknown = false;
                for (Predicate child : children) {
                    Truth truth = child.test(record);
                    if (group.equals("all") && truth == Truth.FALSE) return Truth.FALSE;
                    if (group.equals("any") && truth == Truth.TRUE) return Truth.TRUE;
                    unknown |= truth == Truth.UNKNOWN;
                }
                return unknown ? Truth.UNKNOWN : group.equals("all") ? Truth.TRUE : Truth.FALSE;
            };
        }
        if (node.has("not")) {
            AuthoringJson.keys(node, path, "not");
            Predicate child = compile(node.get("not"), resource, path + "/not", depth + 1, count);
            return record -> switch (child.test(record)) { case TRUE -> Truth.FALSE; case FALSE -> Truth.TRUE; case UNKNOWN -> Truth.UNKNOWN; };
        }
        if (node.has("some")) {
            AuthoringJson.keys(node, path, "some");
            JsonObject some = AuthoringJson.object(node, "some", path);
            AuthoringJson.keys(some, path + "/some", "path", "where");
            String array = AuthoringJson.string(some, "path", path + "/some");
            String elementResource = AuthoringFields.array(resource, array, path + "/some/path");
            if (!some.has("where")) throw AuthoringJson.invalid(path, "Correlated predicate is missing.");
            Predicate child = compile(some.get("where"), elementResource, path + "/some/where", depth + 1, count);
            return record -> {
                JsonElement elements = AuthoringFields.value(record, array);
                if (elements == null || !elements.isJsonArray()) return Truth.UNKNOWN;
                // Incomplete records may omit array elements; no known witness is not a proven false.
                boolean unknown = !new JsonPrimitive("complete").equals(record.get("coverage"));
                for (JsonElement element : elements.getAsJsonArray()) {
                    if (!element.isJsonObject()) { unknown = true; continue; }
                    Truth truth = child.test(element.getAsJsonObject());
                    if (truth == Truth.TRUE) return Truth.TRUE;
                    unknown |= truth == Truth.UNKNOWN;
                }
                return unknown ? Truth.UNKNOWN : Truth.FALSE;
            };
        }
        AuthoringJson.keys(node, path, "field", "op", "value");
        String field = AuthoringJson.string(node, "field", path);
        String op = AuthoringJson.string(node, "op", path);
        AuthoringFields.Field definition = AuthoringFields.field(resource, field, path + "/field");
        if (!definition.operators().contains(op)) throw new AuthoringException(422, "UNSUPPORTED_QUERY_OPERATOR", path + "/op", "Operator is not supported for this field.");
        JsonElement operand = node.get("value");
        if (op.equals("hasValue")) {
            if (operand != null) throw AuthoringJson.invalid(path + "/value", "hasValue does not accept a value.");
        } else if (op.equals("in") || op.equals("notIn")) {
            if (operand == null || !operand.isJsonArray() || operand.getAsJsonArray().isEmpty() || operand.getAsJsonArray().size() > 100) throw AuthoringJson.invalid(path + "/value", "Expected 1 to 100 scalar values.");
            for (JsonElement item : operand.getAsJsonArray()) if (!definition.accepts(item)) throw AuthoringJson.invalid(path + "/value", "Operand type does not match the field.");
        } else if (operand == null || !definition.accepts(operand)) throw AuthoringJson.invalid(path + "/value", "Operand type does not match the field.");
        return record -> compare(AuthoringFields.value(record, field), op, operand, definition.kind());
    }

    private static Truth compare(JsonElement actual, String op, JsonElement expected, AuthoringFields.Kind kind) {
        if (actual == null) return Truth.UNKNOWN;
        if (actual.isJsonNull()) return op.equals("hasValue") ? Truth.FALSE : Truth.UNKNOWN;
        if (op.equals("hasValue") && kind != AuthoringFields.Kind.BOUND) return Truth.TRUE;
        if (op.equals("in") || op.equals("notIn")) {
            boolean unknown = false;
            for (JsonElement item : expected.getAsJsonArray()) {
                Truth truth = compare(actual, "eq", item, kind);
                if (truth == Truth.TRUE) return op.equals("in") ? Truth.TRUE : Truth.FALSE;
                unknown |= truth == Truth.UNKNOWN;
            }
            return unknown ? Truth.UNKNOWN : op.equals("in") ? Truth.FALSE : Truth.TRUE;
        }
        int comparison;
        if (kind == AuthoringFields.Kind.BOUND) {
            if (!actual.isJsonObject()) return Truth.UNKNOWN;
            JsonObject bound = actual.getAsJsonObject();
            if (!bound.has("kind")) return Truth.UNKNOWN;
            if (new JsonPrimitive("unbounded").equals(bound.get("kind"))) comparison = 1;
            else if (new JsonPrimitive("finite").equals(bound.get("kind")) && new AuthoringFields.Field(AuthoringFields.Kind.BOUND).accepts(bound.get("value"))) comparison = op.equals("hasValue") ? 0 : bound.get("value").getAsBigDecimal().compareTo(expected.getAsBigDecimal());
            else return Truth.UNKNOWN;
        } else {
            if (!actual.isJsonPrimitive()) return Truth.UNKNOWN;
            JsonPrimitive p = actual.getAsJsonPrimitive();
            if (kind == AuthoringFields.Kind.NUMBER && p.isNumber()) comparison = p.getAsBigDecimal().compareTo(expected.getAsBigDecimal());
            else if (kind == AuthoringFields.Kind.STRING && p.isString()) comparison = p.getAsString().compareTo(expected.getAsString());
            else if (kind == AuthoringFields.Kind.BOOLEAN && p.isBoolean()) comparison = Boolean.compare(p.getAsBoolean(), expected.getAsBoolean());
            else return Truth.UNKNOWN;
        }
        boolean matched = switch (op) {
            case "hasValue" -> true;
            case "eq" -> comparison == 0; case "ne" -> comparison != 0;
            case "lt" -> comparison < 0; case "lte" -> comparison <= 0;
            case "gt" -> comparison > 0; case "gte" -> comparison >= 0;
            case "prefix" -> actual.getAsString().startsWith(expected.getAsString());
            default -> false;
        };
        return matched ? Truth.TRUE : Truth.FALSE;
    }

    private static List<String> selection(JsonObject request, String resource) {
        if (!request.has("select")) return List.of("symbolId", "name", "kind", "signature", "coverage", "source");
        if (!request.get("select").isJsonArray() || request.getAsJsonArray("select").isEmpty() || request.getAsJsonArray("select").size() > 32) throw AuthoringJson.invalid("/select", "Expected 1 to 32 selected fields.");
        List<String> result = new ArrayList<>();
        for (JsonElement item : request.getAsJsonArray("select")) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString() || !AuthoringFields.selectable(resource).contains(item.getAsString())) throw AuthoringJson.invalid("/select", "Unknown selection field.");
            result.add(item.getAsString());
        }
        return result;
    }

    private static Comparator<JsonObject> ordering(JsonObject request, String resource) {
        Comparator<JsonObject> comparator = (left, right) -> 0;
        if (request.has("orderBy")) {
            if (!request.get("orderBy").isJsonArray() || request.getAsJsonArray("orderBy").size() > 3) throw AuthoringJson.invalid("/orderBy", "Expected at most three sort fields.");
            for (JsonElement element : request.getAsJsonArray("orderBy")) {
                if (!element.isJsonObject()) throw AuthoringJson.invalid("/orderBy", "Expected sort object.");
                JsonObject sort = element.getAsJsonObject();
                AuthoringJson.keys(sort, "/orderBy", "field", "direction");
                String field = AuthoringJson.string(sort, "field", "/orderBy");
                if (AuthoringFields.field(resource, field, "/orderBy/field").kind() != AuthoringFields.Kind.STRING) throw AuthoringJson.invalid("/orderBy/field", "Only string sort fields are currently supported.");
                String direction = AuthoringJson.string(sort, "direction", "/orderBy");
                if (!Set.of("asc", "desc").contains(direction)) throw AuthoringJson.invalid("/orderBy/direction", "Expected asc or desc.");
                Comparator<JsonObject> next = Comparator.comparing(record -> {
                    JsonElement value = AuthoringFields.value(record, field);
                    return value == null || value.isJsonNull() ? null : value.getAsString();
                }, Comparator.nullsLast(Comparator.<String>naturalOrder()));
                comparator = comparator.thenComparing(direction.equals("asc") ? next : next.reversed());
            }
        }
        return comparator.thenComparing(record -> record.get("symbolId").getAsString());
    }

    private static JsonObject project(JsonObject record, List<String> fields) {
        JsonObject result = new JsonObject();
        for (String field : fields) {
            JsonElement value = AuthoringFields.value(record, field);
            if (value == null) continue;
            JsonObject parent = result;
            String[] path = field.split("\\.");
            for (int i = 0; i < path.length - 1; i++) {
                if (!parent.has(path[i])) parent.add(path[i], new JsonObject());
                parent = parent.getAsJsonObject(path[i]);
            }
            parent.add(path[path.length - 1], value.deepCopy());
        }
        result.add("symbolId", record.get("symbolId").deepCopy());
        result.add("coverage", record.get("coverage").deepCopy());
        return result;
    }

    private String encodeCursor(String snapshot, String query, int offset) {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(AuthoringJson.obj("schemaVersion", "1", "snapshot", snapshot, "query", query,
                "offset", offset, "expires", clock.millis() + 900_000).toString().getBytes(StandardCharsets.UTF_8));
        return payload + "." + signature(payload);
    }
    private JsonObject decodeCursor(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(signature(parts[0]).getBytes(StandardCharsets.US_ASCII), parts[1].getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException();
            JsonObject payload = AuthoringJson.parse(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
            if (!"1".equals(payload.get("schemaVersion").getAsString()) || payload.get("expires").getAsLong() <= clock.millis()) throw new IllegalArgumentException();
            return payload;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw AuthoringJson.invalid("/page/cursor", "Cursor is invalid, expired, or belongs to a different query.");
        }
    }
    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cursorKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException e) { throw new AssertionError(e); }
    }
    public static JsonObject error(AuthoringException exception) {
        return AuthoringJson.obj("code", exception.code(), "message", exception.getMessage(), "path", exception.path());
    }
    @Override public synchronized void close() { closed = true; snapshots.clear(); current = null; Arrays.fill(cursorKey, (byte) 0); }
}
