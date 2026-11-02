package com.tkisor.nekojs.authoring;

import com.google.gson.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AuthoringQueryTest {
    static JsonObject scope() { return AuthoringJson.obj("environmentId", "env", "snapshotId", "s1", "language", "typescript", "scriptType", "SERVER", "memberView", "declared"); }
    static JsonObject request(String from, JsonObject where) {
        JsonObject request = AuthoringJson.obj("scope", scope(), "from", from);
        if (where != null) request.add("where", where);
        return request;
    }
    static JsonObject comparison(String field, String op, Object value) { return AuthoringJson.obj("field", field, "op", op, "value", value); }
    static JsonObject member(String id, String name, String result, String... params) {
        JsonArray parameters = new JsonArray();
        for (int i = 0; i < params.length; i++) parameters.add(AuthoringJson.obj("position", i, "name", "p" + i, "type", AuthoringJson.obj("id", params[i], "display", params[i])));
        return AuthoringJson.obj("symbolId", id, "ownerSymbolId", "owner", "kind", "method", "name", name, "returnType", AuthoringJson.obj("id", result),
                "parameters", parameters, "scriptTypes", List.of("SERVER"), "coverage", "complete", "callArity", AuthoringJson.obj("min", params.length, "max", AuthoringJson.obj("kind", "finite", "value", params.length)));
    }
    static JsonObject fixture(String id, List<JsonObject> members) {
        return AuthoringJson.obj("schemaVersion", "1", "environmentId", "env", "snapshotId", id, "complete", false,
                "resources", AuthoringJson.obj("symbols", List.of(AuthoringJson.obj("symbolId", "owner", "kind", "binding", "name", "Item", "coverage", "incomplete", "scriptTypes", List.of("SERVER"))),
                        "members", members, "events", List.of(), "inputAdapters", List.of(), "declarations", List.of()));
    }
    static AuthoringQuery engine(JsonObject... members) {
        AuthoringQuery engine = new AuthoringQuery("env"); engine.publish(new AuthoringSnapshot(fixture("s1", List.of(members)))); return engine;
    }
    static JsonArray items(AuthoringQuery engine, JsonObject request) { return engine.execute("query", request).getAsJsonArray("items"); }

    @Test void predicatesNeverCombineDifferentOverloadsOrParameters() {
        try (AuthoringQuery engine = engine(member("a", "convert", "ItemStack", "string"), member("b", "convert", "string", "number"), member("c", "convert", "string", "number", "string"))) {
            JsonObject sameParameter = AuthoringJson.obj("some", AuthoringJson.obj("path", "parameters", "where", AuthoringJson.obj("all", List.of(
                    comparison("position", "eq", 0), comparison("type.id", "eq", "string")))));
            JsonObject where = AuthoringJson.obj("all", List.of(comparison("returnType.id", "eq", "string"), sameParameter));
            assertTrue(items(engine, request("members", where)).isEmpty());
            assertEquals(1, items(engine, request("members", AuthoringJson.obj("all", List.of(comparison("returnType.id", "eq", "ItemStack"), sameParameter)))).size());
        }
    }

    @Test void negationAndEmptyArraysRetainTheirDifferentMeanings() {
        try (AuthoringQuery engine = engine(member("empty", "f", "void"), member("str", "f", "void", "string"), member("num", "f", "void", "number"))) {
            JsonObject isString = comparison("type.id", "eq", "string");
            JsonObject someNot = AuthoringJson.obj("some", AuthoringJson.obj("path", "parameters", "where", AuthoringJson.obj("not", isString)));
            JsonObject notSome = AuthoringJson.obj("not", AuthoringJson.obj("some", AuthoringJson.obj("path", "parameters", "where", isString)));
            assertEquals(1, items(engine, request("members", someNot)).size());
            assertEquals(2, items(engine, request("members", notSome)).size());
        }
    }

    @Test void unknownMetadataDoesNotBecomeTrueUnderNegation() {
        JsonObject known = member("known", "f", "void"); known.addProperty("writable", false);
        try (AuthoringQuery engine = engine(known, member("unknown", "f", "void"))) {
            JsonObject response = engine.execute("query", request("members", AuthoringJson.obj("not", comparison("writable", "eq", true))));
            assertEquals(1, response.getAsJsonArray("items").size());
            assertEquals(1, response.getAsJsonObject("coverage").get("unresolvedRecords").getAsInt());
            assertEquals("incomplete", response.getAsJsonObject("coverage").get("state").getAsString());
        }
    }

    @Test void cursorsBindAllConditionsSelectionScopeAndRetainedSnapshot() {
        try (AuthoringQuery engine = engine(member("c", "f", "void"), member("a", "f", "void"), member("b", "f", "void"))) {
            JsonObject first = request("members", null); first.add("page", AuthoringJson.obj("size", 1));
            JsonObject response = engine.execute("query", first);
            assertEquals("a", response.getAsJsonArray("items").get(0).getAsJsonObject().get("symbolId").getAsString());
            String cursor = response.getAsJsonObject("page").get("nextCursor").getAsString();
            engine.publish(new AuthoringSnapshot(fixture("s2", List.of(member("new", "f", "void")))));
            JsonObject second = first.deepCopy(); second.add("page", AuthoringJson.obj("size", 2, "cursor", cursor));
            JsonArray next = items(engine, second);
            assertEquals(List.of("b", "c"), next.asList().stream().map(item -> item.getAsJsonObject().get("symbolId").getAsString()).toList());
            second.add("select", AuthoringJson.GSON.toJsonTree(List.of("name")));
            assertThrows(AuthoringException.class, () -> engine.execute("query", second));
            JsonObject forged = first.deepCopy(); forged.add("page", AuthoringJson.obj("cursor", cursor + "x"));
            assertThrows(AuthoringException.class, () -> engine.execute("query", forged));
            engine.publish(new AuthoringSnapshot(fixture("s3", List.of())));
            engine.publish(new AuthoringSnapshot(fixture("s4", List.of())));
            assertEquals(410, assertThrows(AuthoringException.class, () -> engine.execute("query", first)).status());
        }
    }

    @Test void tokenExpiryIsEnforcedWithoutSleeping() {
        class MutableClock extends Clock {
            long now;
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochMilli(now); }
        }
        MutableClock clock = new MutableClock();
        try (AuthoringQuery engine = new AuthoringQuery("env", clock)) {
            engine.publish(new AuthoringSnapshot(fixture("s1", List.of(member("a", "f", "void"), member("b", "f", "void")))));
            JsonObject request = request("members", null); request.add("page", AuthoringJson.obj("size", 1));
            String cursor = engine.execute("query", request).getAsJsonObject("page").get("nextCursor").getAsString();
            clock.now = 900_000;
            request.add("page", AuthoringJson.obj("cursor", cursor));
            assertThrows(AuthoringException.class, () -> engine.execute("query", request));
        }
    }

    @Test void invalidFieldsOperatorsTypesViewsAndDuplicateKeysAreRejected() {
        try (AuthoringQuery engine = engine(member("a", "f", "void"))) {
            for (JsonObject predicate : List.of(comparison("typo", "eq", "x"), comparison("name", "gte", 1), comparison("parameterCount", "eq", "1"), comparison("parameters.position", "eq", 0))) {
                assertThrows(AuthoringException.class, () -> engine.execute("query", request("members", predicate)));
            }
            JsonObject request = request("members", null);
            request.getAsJsonObject("scope").addProperty("memberView", "effective");
            assertEquals("UNSUPPORTED_MEMBER_VIEW", assertThrows(AuthoringException.class, () -> engine.execute("query", request)).code());
            assertThrows(AuthoringException.class, () -> AuthoringJson.parse("{\"scope\":{},\"scope\":{}}"));
            assertThrows(AuthoringException.class, () -> AuthoringJson.parse("{name:'f'}"));
        }
    }

    @Test void selectionAndBatchDoNotChangeMatchingOrPermitScopeReplacement() {
        try (AuthoringQuery engine = engine(member("a", "f", "void"))) {
            JsonObject one = request("members", comparison("name", "eq", "f"));
            one.add("select", AuthoringJson.GSON.toJsonTree(List.of("name")));
            JsonObject selected = items(engine, one).get(0).getAsJsonObject();
            assertEquals(Set.of("name", "symbolId", "coverage"), selected.keySet());
            JsonObject good = AuthoringJson.obj("from", "members");
            JsonObject bad = AuthoringJson.obj("from", "members", "where", comparison("name", "lt", 5));
            JsonObject response = engine.execute("batch", AuthoringJson.obj("scope", scope(), "queries", List.of(good, bad)));
            assertEquals("ok", response.getAsJsonArray("results").get(0).getAsJsonObject().get("status").getAsString());
            assertEquals("error", response.getAsJsonArray("results").get(1).getAsJsonObject().get("status").getAsString());
        }
    }

    @Test void overloadWitnessesAndParameterWitnessesRemainIndependent() {
        JsonObject returnString = comparison("returnType.id", "eq", "string");
        JsonObject firstString = AuthoringJson.obj("some", AuthoringJson.obj("path", "parameters", "where",
                AuthoringJson.obj("all", List.of(comparison("position", "eq", 0), comparison("type.id", "eq", "string")))));
        JsonObject both = AuthoringJson.obj("all", List.of(returnString, firstString));
        JsonObject split = member("split", "convert", "string", "number", "string");
        JsonObject other = member("other", "convert", "ItemStack", "string");
        try (AuthoringQuery engine = engine(other, split)) {
            assertTrue(items(engine, request("members", both)).isEmpty());
            JsonObject separateWitnesses = AuthoringJson.obj("all", List.of(
                    AuthoringJson.obj("some", AuthoringJson.obj("path", "parameters", "where", comparison("position", "eq", 0))),
                    AuthoringJson.obj("some", AuthoringJson.obj("path", "parameters", "where", comparison("type.id", "eq", "string")))));
            assertEquals(2, items(engine, request("members", separateWitnesses)).size());
        }
        try (AuthoringQuery engine = engine(split, member("match", "convert", "string", "string"), other)) {
            JsonArray matches = items(engine, request("members", both));
            assertEquals(1, matches.size());
            assertEquals("match", matches.get(0).getAsJsonObject().get("symbolId").getAsString());
        }
    }

    @Test void unknownArrayElementsAndIncompleteArraysRemainUnknownUnderNegation() {
        JsonObject unknownElement = member("unknown-element", "f", "void", "string");
        unknownElement.getAsJsonArray("parameters").get(0).getAsJsonObject().remove("type");
        JsonObject incomplete = member("incomplete", "f", "void");
        incomplete.addProperty("coverage", "incomplete");
        JsonObject nullReturn = member("null", "f", "void");
        nullReturn.add("returnType", JsonNull.INSTANCE);
        nullReturn.add("writable", JsonNull.INSTANCE);
        JsonObject missingReturn = member("missing", "f", "void");
        missingReturn.remove("returnType");
        try (AuthoringQuery engine = engine(unknownElement, incomplete, nullReturn, missingReturn)) {
            JsonObject noneString = AuthoringJson.obj("not", AuthoringJson.obj("some", AuthoringJson.obj("path", "parameters", "where", comparison("type.id", "eq", "string"))));
            JsonObject response = engine.execute("query", request("members", noneString));
            assertEquals(2, response.getAsJsonArray("items").size());
            assertEquals(2, response.getAsJsonObject("coverage").get("unresolvedRecords").getAsInt());
            JsonObject noReturn = AuthoringJson.obj("not", AuthoringJson.obj("field", "returnType.id", "op", "hasValue"));
            assertTrue(items(engine, request("members", noReturn)).isEmpty());
            JsonObject noWritable = AuthoringJson.obj("not", AuthoringJson.obj("field", "writable", "op", "hasValue"));
            JsonArray knownNull = items(engine, request("members", noWritable));
            assertEquals(1, knownNull.size());
            assertEquals("null", knownNull.get(0).getAsJsonObject().get("symbolId").getAsString());
        }
    }

    @Test void arityUpperBoundsDistinguishFiniteUnboundedAndUnknown() {
        JsonObject finite = member("finite", "f", "void", "string");
        JsonObject unbounded = member("unbounded", "f", "void");
        unbounded.getAsJsonObject("callArity").add("max", AuthoringJson.obj("kind", "unbounded"));
        JsonObject unknown = member("unknown", "f", "void");
        unknown.getAsJsonObject("callArity").add("max", AuthoringJson.obj("kind", "unknown", "value", 99));
        JsonObject missing = member("missing", "f", "void");
        missing.getAsJsonObject("callArity").remove("max");
        JsonObject knownNull = member("null", "f", "void");
        knownNull.getAsJsonObject("callArity").add("max", JsonNull.INSTANCE);
        try (AuthoringQuery engine = engine(finite, unbounded, unknown, missing, knownNull)) {
            assertEquals("unbounded", items(engine, request("members", comparison("callArity.max", "gt", 100))).get(0).getAsJsonObject().get("symbolId").getAsString());
            JsonObject response = engine.execute("query", request("members", AuthoringJson.obj("not", comparison("callArity.max", "gt", 100))));
            assertEquals(1, response.getAsJsonArray("items").size());
            assertEquals(3, response.getAsJsonObject("coverage").get("unresolvedRecords").getAsInt());
            JsonObject hasValue = AuthoringJson.obj("field", "callArity.max", "op", "hasValue");
            assertEquals(2, items(engine, request("members", hasValue)).size());
            JsonArray absent = items(engine, request("members", AuthoringJson.obj("not", hasValue)));
            assertEquals(1, absent.size());
            assertEquals("null", absent.get(0).getAsJsonObject().get("symbolId").getAsString());
            for (Object invalid : List.of(-1, 1.5, "1", true, AuthoringJson.obj("kind", "unbounded"))) {
                assertEquals("/where/value", assertThrows(AuthoringException.class,
                        () -> engine.execute("query", request("members", comparison("callArity.max", "eq", invalid)))).path());
            }
            assertThrows(AuthoringException.class, () -> engine.execute("query", request("members", comparison("callArity.max", "in", List.of(1, -1)))));
            JsonObject boundSchema = engine.capabilities().getAsJsonObject("fields").getAsJsonObject("members").getAsJsonObject("fields").getAsJsonObject("callArity.max");
            assertEquals("arityUpperBound", boundSchema.get("type").getAsString());
            assertEquals("integer", boundSchema.getAsJsonObject("operandSchema").get("type").getAsString());
            assertTrue(boundSchema.has("valueSchema"));
        }
    }

    @Test void omittedSnapshotCursorUsesOriginalSnapshotAndCannotBeRebound() {
        try (AuthoringQuery engine = engine(member("a", "f", "void"), member("b", "f", "void"), member("c", "f", "void"))) {
            JsonObject first = request("members", null);
            first.getAsJsonObject("scope").remove("snapshotId");
            first.add("page", AuthoringJson.obj("size", 1));
            String cursor = engine.execute("query", first).getAsJsonObject("page").get("nextCursor").getAsString();
            engine.publish(new AuthoringSnapshot(fixture("s2", List.of(member("new", "f", "void")))));
            JsonObject next = first.deepCopy();
            next.add("page", AuthoringJson.obj("cursor", cursor, "size", 2));
            JsonObject response = engine.execute("query", next);
            assertEquals("s1", response.get("snapshotId").getAsString());
            assertEquals(List.of("b", "c"), response.getAsJsonArray("items").asList().stream().map(item -> item.getAsJsonObject().get("symbolId").getAsString()).toList());
            assertFalse(first.getAsJsonObject("scope").has("snapshotId"));
            for (String changed : List.of("where", "select", "orderBy", "scope")) {
                JsonObject altered = next.deepCopy();
                switch (changed) {
                    case "where" -> altered.add("where", comparison("name", "eq", "f"));
                    case "select" -> altered.add("select", AuthoringJson.GSON.toJsonTree(List.of("name")));
                    case "orderBy" -> altered.add("orderBy", AuthoringJson.GSON.toJsonTree(List.of(AuthoringJson.obj("field", "name", "direction", "desc"))));
                    case "scope" -> altered.getAsJsonObject("scope").addProperty("scriptType", "CLIENT");
                    default -> fail("Unexpected fixture mutation.");
                }
                assertEquals("/page/cursor", assertThrows(AuthoringException.class, () -> engine.execute("query", altered)).path());
            }
            assertThrows(AuthoringException.class, () -> engine.publish(new AuthoringSnapshot(fixture("s1", List.of(member("replacement", "f", "void"))))));
            assertEquals("s1", engine.execute("query", next).get("snapshotId").getAsString());
            engine.publish(new AuthoringSnapshot(fixture("s3", List.of())));
            engine.publish(new AuthoringSnapshot(fixture("s4", List.of())));
            assertEquals("SNAPSHOT_EXPIRED", assertThrows(AuthoringException.class, () -> engine.execute("query", next)).code());
        }
    }

    @Test void batchValidatesSharedScopeOnceAndIsolatesMalformedEntries() {
        try (AuthoringQuery engine = engine(member("a", "f", "void"))) {
            JsonArray queries = new JsonArray();
            queries.add(JsonNull.INSTANCE);
            queries.add(AuthoringJson.obj("scope", scope(), "from", "members"));
            queries.add(AuthoringJson.obj("from", "members"));
            JsonObject response = engine.execute("batch", AuthoringJson.obj("scope", scope(), "queries", queries));
            JsonArray results = response.getAsJsonArray("results");
            assertEquals("error", results.get(0).getAsJsonObject().get("status").getAsString());
            assertEquals("error", results.get(1).getAsJsonObject().get("status").getAsString());
            assertEquals("ok", results.get(2).getAsJsonObject().get("status").getAsString());
            JsonObject invalidScope = scope();
            invalidScope.addProperty("memberView", "effective");
            assertEquals("UNSUPPORTED_MEMBER_VIEW", assertThrows(AuthoringException.class,
                    () -> engine.execute("batch", AuthoringJson.obj("scope", invalidScope, "queries", queries))).code());
        }
    }

    @Test void strictValidationReportsIndexedPredicatesAndParsesGeneralJsonSeparately() {
        try (AuthoringQuery engine = engine(member("a", "f", "void"))) {
            JsonObject where = AuthoringJson.obj("all", List.of(comparison("name", "eq", "f"),
                    AuthoringJson.obj("some", AuthoringJson.obj("path", "unknown", "where", comparison("name", "eq", "x")))));
            assertEquals("/where/all/1/some/path", assertThrows(AuthoringException.class,
                    () -> engine.execute("query", request("members", where))).path());
            assertThrows(AuthoringException.class, () -> engine.execute("resolve", AuthoringJson.obj("scope", scope(), "arity", AuthoringJson.obj("min", 1, "max", 1))));
            assertThrows(AuthoringException.class, () -> engine.execute("query", request("members", AuthoringJson.obj("field", "name", "op", "hasValue", "value", "x"))));
            assertThrows(AuthoringException.class, () -> engine.execute("query", request("members", comparison("name", "eq", JsonNull.INSTANCE))));
        }
        assertTrue(AuthoringJson.parseElement("[]").isJsonArray());
        assertTrue(AuthoringJson.parseElement("null").isJsonNull());
        JsonObject knownNull = AuthoringJson.obj("records", List.of(AuthoringJson.obj("value", JsonNull.INSTANCE)));
        JsonObject preserved = AuthoringJson.parse(AuthoringJson.GSON.toJson(knownNull));
        assertTrue(preserved.getAsJsonArray("records").get(0).getAsJsonObject().get("value").isJsonNull());
        for (String invalid : List.of("[]", "null", "{\"a\":1,\"a\":2}", "{\"a\":01}", "{\"a\":NaN}", "{} {}")) {
            assertThrows(AuthoringException.class, () -> AuthoringJson.parse(invalid));
        }
    }

    @Test void simpleArityMeansAValidCallCountRatherThanDeclarationLengthEquality() {
        JsonObject optional = member("optional", "of", "ItemStack", "string", "number");
        optional.getAsJsonObject("callArity").addProperty("min", 1);
        try (AuthoringQuery engine = engine(optional)) {
            JsonObject scope = scope(); scope.addProperty("ownerSymbolId", "owner");
            JsonObject request = AuthoringJson.obj("scope", scope, "name", "of", "arity", AuthoringJson.obj("min", 1, "max", 1));
            assertEquals(1, engine.execute("members", request).getAsJsonArray("items").size());
            assertEquals(1, engine.execute("query", request("members", comparison("callArity.max", "eq", 2))).getAsJsonArray("items").size());
        }
    }
}
