package com.tkisor.nekujs.recipe.schema;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.recipe.RecipeExecutionFactory;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldValueCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeSchemaGraphCompilerTest {
    @Test
    void inheritedDefaultsAndFunctionsCompileIntoOneCanonicalDefinition() {
        var base = document("create:base/processing", """
                {"keys":[
                  {"name":"results","type":"item_stack","role":"output"},
                  {"name":"processing_time","type":"ticks","optional":100},
                  {"name":"heat_requirement","type":"string","optional":"none","excluded":true}
                ],"functions":{"heated":{"type":"set","key":"heat_requirement","value":"heated"}}}
                """);
        var child = document("create:cutting", """
                {"parent":"create:base/processing","keys":[
                  {"name":"processing_time","type":"ticks","optional":100,"always_write":true}
                ],"merge":{"keys":true,"functions":true}}
                """);
        var result = new RecipeSchemaGraphCompiler().compile(List.of(child, base), components());
        var definition = result.definitions().get("create:cutting");
        assertNull(definition.unsupportedReason());
        assertEquals(List.of(List.of("results"), List.of("results", "processing_time")), definition.constructors());
        assertEquals(100, definition.fields().get("processing_time").defaultValue().getAsInt());
        assertTrue(definition.fields().get("processing_time").writeDefault());
        assertFalse(definition.fields().get("heat_requirement").writeDefault());
        assertEquals("heated", definition.functions().get("heated").get("heat_requirement").getAsString());
        assertNotNull(result.definitions().get(base.id()).unsupportedReason());
    }

    @Test
    void missingParentsCyclesAndDuplicatesRemainTerminalWithoutHarmingIndependentProfiles() {
        var result = new RecipeSchemaGraphCompiler().compile(List.of(
                document("test:missing", """
                {"parent":"test:absent"}"""),
                document("test:a", """
                {"parent":"test:b"}"""),
                document("test:b", """
                {"parent":"test:a"}"""),
                document("test:duplicate", "{}"), document("test:duplicate", "{}"),
                document("test:valid", """
                {"keys":[{"name":"input","type":"ingredient"}]}""")), components());
        assertEquals(5, result.definitions().size());
        assertEquals(4, result.rejections().size());
        assertNull(result.definitions().get("test:valid").unsupportedReason());
        assertTrue(result.definitions().get("test:missing").unsupportedReason().contains("test:absent"));
        assertTrue(result.definitions().get("test:a").unsupportedReason().contains("cycle"));
        assertTrue(result.definitions().get("test:duplicate").unsupportedReason().contains("NEKO-5241"));
        assertTrue(result.rejections().stream().allMatch(rejection -> rejection.source().equals("fixture")
                && rejection.resourcePath().endsWith(".json") && rejection.jsonPath().startsWith("$")));
    }

    @Test
    void childFunctionsOverrideAndInheritWithoutMergeFunctionsFlag() {
        var result = new RecipeSchemaGraphCompiler().compile(List.of(
                document("test:parent", """
                        {"keys":[{"name":"state","type":"string","optional":"none"}],
                         "functions":{"mode":{"type":"set","key":"state","value":"parent"},
                                      "kept":{"type":"set","key":"state","value":"kept"}}}
                        """),
                document("test:child", """
                        {"parent":"test:parent","functions":{"mode":{"type":"set","key":"state","value":"child"}}}
                        """)), components());
        var child = result.definitions().get("test:child");
        assertNull(child.unsupportedReason());
        assertEquals("child", child.functions().get("mode").get("state").getAsString());
        assertEquals("kept", child.functions().get("kept").get("state").getAsString());
    }

    @Test
    void aliasesConstructorOverridesAndExcludedFieldsUseValidatedEncodedConstants() {
        var components = new SemanticComponents() {
            @Override
            public RecipeFieldValueCodec resolve(JsonElement expression) { return components().resolve(expression); }
            @Override
            public JsonElement encodeConstant(JsonElement expression, JsonElement literal) {
                return JsonParser.parseString("{\"encoded\":" + literal + "}");
            }
        };
        var result = new RecipeSchemaGraphCompiler().compile(List.of(document("test:machine", """
                {"keys":[{"name":"input","type":"ingredient","alternative_names":["source"]},
                         {"name":"duration","type":"ticks","optional":100,"function_names":["time"]},
                         {"name":"mode","type":"string","optional":"none","excluded":true}],
                 "constructors":[{"keys":["input"],"overrides":{"duration":200}}],
                 "functions":{"hot":{"type":"set","key":"mode","value":"hot"}}}
                """)), components);
        var definition = result.definitions().get("test:machine");
        assertNull(definition.unsupportedReason());
        assertEquals("input", definition.fieldAliases().get("source"));
        assertEquals("duration", definition.fieldAliases().get("time"));
        assertEquals(100, definition.fields().get("duration").defaultValue().getAsJsonObject().get("encoded").getAsInt());
        assertEquals(200, definition.constructorOverrides().get(List.of("input")).get("duration").getAsJsonObject().get("encoded").getAsInt());
        assertEquals("hot", definition.functions().get("hot").get("mode").getAsJsonObject().get("encoded").getAsString());
        assertFalse(definition.fields().get("mode").writeDefault());
    }

    @Test
    void fluentAliasesNormalizeSelectedNamesWithoutRestoringExplicitlyReplacedDefaults() {
        var result = new RecipeSchemaGraphCompiler().compile(List.of(document("test:aliases", """
                {"keys":[{"name":"processing_time","type":"ticks","alternative_names":["recipe:duration/ticks"]},
                         {"name":"heat_requirement","type":"string","alternative_names":["temperature_level"],
                          "function_names":["heat_level","temperature:current/value"]},
                         {"name":"hidden_setter","type":"string","function_names":[]}]}
                """)), components());
        assertTrue(result.rejections().isEmpty());
        var aliases = result.definitions().get("test:aliases").fieldAliases();
        assertEquals("processing_time", aliases.get("processingTime"));
        assertEquals("processing_time", aliases.get("recipeDurationTicks"));
        assertEquals("heat_requirement", aliases.get("heatLevel"));
        assertEquals("heat_requirement", aliases.get("temperatureCurrentValue"));
        assertFalse(aliases.containsKey("heatRequirement"));
        assertFalse(aliases.containsKey("temperatureLevel"));
        assertFalse(aliases.containsKey("hiddenSetter"));
    }

    @Test
    void declarativeFunctionsReplaceRawAndNormalizedFieldAliasesEntirely() {
        var result = new RecipeSchemaGraphCompiler().compile(List.of(document("test:application", """
                {"keys":[{"name":"keep_held_item","type":"boolean","optional":false,
                          "alternative_names":["retain"]}],
                 "functions":{"keepHeldItem":{"type":"set","key":"keep_held_item","value":true},
                              "retain":{"type":"set","key":"keep_held_item","value":true}}}
                """)), components());
        assertTrue(result.rejections().isEmpty());
        var definition = result.definitions().get("test:application");
        assertTrue(definition.fields().containsKey("keep_held_item"));
        assertFalse(definition.fieldAliases().containsKey("keepHeldItem"));
        assertFalse(definition.fieldAliases().containsKey("retain"));
        assertTrue(definition.functions().get("keepHeldItem").get("keep_held_item").getAsBoolean());
        assertTrue(definition.functions().get("retain").get("keep_held_item").getAsBoolean());
    }

    @Test
    void normalizedFluentAliasesCannotTargetAnotherCanonicalField() {
        var result = new RecipeSchemaGraphCompiler().compile(List.of(document("test:collision", """
                {"keys":[{"name":"some_value","type":"string"},{"name":"someValue","type":"string"}]}
                """)), components());
        assertEquals(1, result.rejections().size());
        assertEquals("NEKO-5249", result.rejections().getFirst().code());
        assertTrue(result.definitions().get("test:collision").fields().isEmpty());
    }

    @Test
    void defaultOptionalIsAbsenceAndFalseAndZeroRemainRealDefaults() {
        var result = new RecipeSchemaGraphCompiler().compile(List.of(document("test:defaults", """
                {"keys":[{"name":"input","type":"ingredient"},
                         {"name":"empty","type":"optional_item_stack","default_optional":true},
                         {"name":"enabled","type":"boolean","optional":false,"always_write":true},
                         {"name":"energy","type":"non_negative_long","optional":0}]}
                """)), components());
        var definition = result.definitions().get("test:defaults");
        assertNull(definition.unsupportedReason());
        assertTrue(definition.fields().get("empty").optional());
        assertNull(definition.fields().get("empty").defaultValue());
        assertFalse(definition.fields().get("empty").writeDefault());
        assertFalse(definition.fields().get("enabled").defaultValue().getAsBoolean());
        assertTrue(definition.fields().get("enabled").writeDefault());
        assertEquals(0, definition.fields().get("energy").defaultValue().getAsInt());
        assertFalse(definition.fields().get("energy").writeDefault());
    }

    @Test
    void generatedAllOptionalConstructorsPreserveReferenceRotaryArityQuirk() {
        var result = new RecipeSchemaGraphCompiler().compile(List.of(document("test:rotary", """
                {"keys":[{"name":"a","type":"string","default_optional":true},
                         {"name":"b","type":"string","default_optional":true},
                         {"name":"c","type":"string","default_optional":true},
                         {"name":"d","type":"string","default_optional":true}]}
                """)), components());
        assertEquals(List.of(List.of("a"), List.of("a", "b"), List.of("a", "b", "c"), List.of("a", "b", "c", "d")),
                result.definitions().get("test:rotary").constructors());
    }

    @Test
    void mergingRequiredKeysBeforeOptionalsPreservesStableParentOrderAndRejectsRequiredReplacements() {
        var parent = document("test:parent", """
                {"keys":[{"name":"first","type":"string"},{"name":"time","type":"ticks","optional":100}]}
                """);
        var valid = document("test:valid", """
                {"parent":"test:parent","keys":[{"name":"second","type":"string"}],"merge":{"keys":true}}
                """);
        var invalid = document("test:invalid", """
                {"parent":"test:parent","keys":[{"name":"time","type":"ticks"}],"merge":{"keys":true}}
                """);
        var result = new RecipeSchemaGraphCompiler().compile(List.of(parent, valid, invalid), components());
        assertEquals(List.of("first", "second", "time"), List.copyOf(result.definitions().get(valid.id()).fields().keySet()));
        assertEquals(List.of(List.of("first", "second"), List.of("first", "second", "time")), result.definitions().get(valid.id()).constructors());
        assertTrue(result.definitions().get(invalid.id()).unsupportedReason().contains("Optional parent key cannot become required"));
    }

    @Test
    void explicitConstructorAndUniqueMergesPreserveReferenceOrderAndSerializerOverrides() {
        var result = new RecipeSchemaGraphCompiler().compile(List.of(
                document("test:parent", """
                        {"override_type":"test:serializer","keys":[{"name":"a","type":"string","excluded":true},
                         {"name":"b","type":"string","optional":"default"}],
                         "constructors":[{"keys":["a"]}],"unique":["a"]}
                        """),
                document("test:alias", """
                        {"parent":"test:parent"}
                        """),
                document("test:child", """
                        {"parent":"test:parent","constructors":[{"keys":["a","b"]}],"unique":["b"],
                         "merge":{"constructors":true,"unique":true}}
                        """)), components());
        var alias = result.definitions().get("test:alias");
        var child = result.definitions().get("test:child");
        assertEquals("test:serializer", alias.type());
        assertEquals("test:child", child.type());
        assertEquals(List.of(List.of("a"), List.of("a", "b")), child.constructors());
        assertEquals(List.of("a", "b"), child.unique());
        assertEquals("test_child", child.prefix(), "Native generated IDs intentionally retain NekoJS policy");
    }

    @Test
    void malformedReferencesAmbiguousConstructorsAndUnsupportedFeaturesAreNotPartiallyPublished() {
        List<String> badDocuments = List.of(
                """
                {"keys":[{"name":"x","type":"string","default_optional":true,"always_write":true}]}""",
                """
                {"keys":[{"name":"x","type":"string"}],"constructors":[{"keys":["absent"]}]}""",
                """
                {"keys":[{"name":"x","type":"string"}],"functions":{"f":{"type":"set","key":"absent","value":1}}}""",
                """
                {"keys":[{"name":"x","type":"string"}],"constructors":[{"keys":["x"]},{"keys":["x"]}]}""",
                """
                {"keys":[{"name":"x","type":"string"},{"name":"y","type":"string","function_names":["x"]}]}""",
                """
                {"recipe_factory":"custom:factory"}""",
                """
                {"post_processors":[{"type":"custom"}]}""",
                """
                {"functions":{"f":{"type":"custom"}}}""",
                """
                {"mappings":["another"]}"""
        );
        for (String json : badDocuments) {
            var result = new RecipeSchemaGraphCompiler().compile(List.of(document("test:bad", json)), components());
            assertEquals(1, result.rejections().size(), json);
            var definition = result.definitions().get("test:bad");
            assertNotNull(definition.unsupportedReason(), json);
            assertTrue(definition.fields().isEmpty(), json);
            assertTrue(definition.constructors().isEmpty(), json);
        }
    }

    @Test
    void componentAndConstantRejectionsCarryOriginalInheritedResourceLocations() {
        var badComponent = document("test:parent", """
                {"keys":[{"name":"x","type":"unsupported"}]}""");
        var badConstant = document("test:constant", """
                {"keys":[{"name":"x","type":"ticks","optional":-1}]}""");
        var semantic = new SemanticComponents() {
            @Override
            public RecipeFieldValueCodec resolve(JsonElement expression) {
                if (expression.getAsString().equals("unsupported")) throw new IllegalArgumentException("No semantic binding");
                return components().resolve(expression);
            }
            @Override
            public JsonElement encodeConstant(JsonElement expression, JsonElement literal) {
                throw new IllegalArgumentException("Out of bounds");
            }
        };
        var result = new RecipeSchemaGraphCompiler().compile(List.of(badComponent,
                document("test:child", """
                {"parent":"test:parent"}"""), badConstant), semantic);
        assertEquals(3, result.rejections().size());
        var childFailure = result.rejections().stream().filter(value -> value.schemaId().equals("test:child")).findFirst().orElseThrow();
        assertEquals(badComponent.resourcePath(), childFailure.resourcePath());
        assertEquals("$.keys[0].type", childFailure.jsonPath());
        var constantFailure = result.rejections().stream().filter(value -> value.schemaId().equals(badConstant.id())).findFirst().orElseThrow();
        assertEquals("$.keys[0].optional", constantFailure.jsonPath());
        assertEquals("NEKO-5247", constantFailure.code());
    }

    @Test
    void schemaOverridesAreEncodedAndAppliedToEveryGeneratedConstructor() {
        var result = new RecipeSchemaGraphCompiler().compile(List.of(document("test:overridden", """
                {"keys":[{"name":"input","type":"ingredient"},{"name":"mode","type":"string"},
                         {"name":"duration","type":"ticks","optional":100}],"override_keys":{"mode":"fixed"}}
                """)), components());
        var definition = result.definitions().get("test:overridden");
        assertEquals(List.of(List.of("input"), List.of("input", "duration")), definition.constructors());
        assertEquals("fixed", definition.constructorOverrides().get(List.of("input")).get("mode").getAsString());
        assertEquals("fixed", definition.constructorOverrides().get(List.of("input", "duration")).get("mode").getAsString());
    }

    @Test
    void schemaOverridesWinConflictingExplicitConstructorOverrides() {
        var result = new RecipeSchemaGraphCompiler().compile(List.of(document("test:overridden", """
                {"keys":[{"name":"input","type":"ingredient"},{"name":"mode","type":"string"}],
                 "override_keys":{"mode":"schema"},
                 "constructors":[{"keys":["input"],"overrides":{"mode":"ctor"}}]}
                """)), components());
        assertTrue(result.rejections().isEmpty());
        var definition = result.definitions().get("test:overridden");
        assertEquals("schema", definition.constructorOverrides().get(List.of("input")).get("mode").getAsString());
    }

    @Test
    void inputDocumentsAreNotMutatedAndUnexpectedResolverFailuresPropagate() {
        var input = document("test:machine", """
                {"keys":[{"name":"x","type":{"type":"int","min":0},"optional":2}]}""");
        String before = input.json().toString();
        var resolver = new SemanticComponents() {
            @Override
            public RecipeFieldValueCodec resolve(JsonElement expression) {
                expression.getAsJsonObject().addProperty("mutated", true);
                return components().resolve(expression);
            }
            @Override
            public JsonElement encodeConstant(JsonElement expression, JsonElement literal) {
                expression.getAsJsonObject().addProperty("mutated", true);
                return literal;
            }
        };
        assertNull(new RecipeSchemaGraphCompiler().compile(List.of(input), resolver).definitions().get(input.id()).unsupportedReason());
        assertEquals(before, input.json().toString());
        var failure = new IllegalStateException("Binding owner failed");
        var broken = new SemanticComponents() {
            @Override
            public RecipeFieldValueCodec resolve(JsonElement expression) { throw failure; }
            @Override
            public JsonElement encodeConstant(JsonElement expression, JsonElement literal) { throw new AssertionError(); }
        };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> new RecipeSchemaGraphCompiler().compile(List.of(input), broken)));
    }

    @Test
    void nativeFallbackParentsResolveOwnedProfilesWithoutPublishingSyntheticRoots() {
        var factory = (RecipeExecutionFactory) (builder, host, session) -> builder;
        var components = factoryComponents(factory);
        var result = new RecipeSchemaGraphCompiler().compile(List.of(
                document("sample:shaped_child", "{\"parent\":\"shaped\"}"),
                document("sample:extract", "{\"parent\":\"special\"}"),
                document("sample:insert", "{\"parent\":\"special\"}"),
                document("sample:clear", "{\"parent\":\"special\"}")), components);
        assertEquals(4, result.definitions().size());
        assertFalse(result.definitions().containsKey("minecraft:shaped"));
        assertFalse(result.definitions().containsKey("minecraft:special"));
        assertTrue(result.rejections().isEmpty(), result.rejections().toString());
        var shaped = result.definitions().get("sample:shaped_child");
        assertEquals("sample:shaped_child", shaped.type());
        assertEquals(List.of(List.of("result", "pattern", "key")), shaped.constructors());
        assertEquals(List.of("result", "pattern", "key", "category", "show_notification"), List.copyOf(shaped.fields().keySet()));
        assertSame(factory, shaped.executionFactory());
        assertEquals("misc", shaped.fields().get("category").defaultValue().getAsString());
        assertTrue(shaped.fields().get("show_notification").defaultValue().getAsBoolean());
        assertFalse(shaped.fields().containsKey("kubejs:mirror"));
        assertFalse(shaped.fields().containsKey("kubejs:shrink"));
        assertFalse(shaped.functions().containsKey("noMirror"));
        assertFalse(shaped.functions().containsKey("noShrink"));
        for (String name : List.of("extract", "insert", "clear")) {
            var special = result.definitions().get("sample:" + name);
            assertEquals(List.of(List.of()), special.constructors());
            assertEquals(List.of("category"), List.copyOf(special.fields().keySet()));
            assertEquals("sample:" + name, special.type());
        }
    }

    @Test
    void ownedParentDocumentsOverrideNativeFallbacksAndRemainEnumerable() {
        var result = new RecipeSchemaGraphCompiler().compile(List.of(
                document("minecraft:shaped", "{\"keys\":[{\"name\":\"custom\",\"type\":\"string\"}]}"),
                document("sample:child", "{\"parent\":\"minecraft:shaped\"}")), components());
        assertTrue(result.rejections().isEmpty());
        assertEquals(2, result.definitions().size());
        assertEquals(List.of("custom"), List.copyOf(result.definitions().get("sample:child").fields().keySet()));
        assertNull(result.definitions().get("sample:child").executionFactory());
    }

    @Test
    void hiddenMetadataDoesNotGateRuntimeWhileAbstractBaseDependenciesStayTerminal() {
        var result = new RecipeSchemaGraphCompiler().compile(List.of(
                document("sample:hidden", "{\"hidden\":true,\"keys\":[{\"name\":\"value\",\"type\":\"string\"}]}"),
                document("sample:alias", "{\"parent\":\"sample:hidden\"}"),
                document("sample:base/template", "{\"hidden\":true}")), components());
        assertNull(result.definitions().get("sample:hidden").unsupportedReason());
        assertNull(result.definitions().get("sample:alias").unsupportedReason());
        assertTrue(result.definitions().get("sample:base/template").unsupportedReason().contains("NEKO-5251"));
    }

    @Test
    void factoryAliasesAreCapabilityResolvedAndUnknownOrConflictingFactoriesFailClosed() {
        RecipeExecutionFactory factory = (builder, host, session) -> builder;
        for (String property : List.of("factory", "recipe_factory")) {
            var definition = new RecipeSchemaGraphCompiler().compile(List.of(document("sample:known",
                    "{\"" + property + "\":\"nekujs:native_shaped\"}")), factoryComponents(factory)).definitions().get("sample:known");
            assertNull(definition.unsupportedReason());
            assertSame(factory, definition.executionFactory());
            var unknown = new RecipeSchemaGraphCompiler().compile(List.of(document("sample:unknown",
                    "{\"" + property + "\":\"sample:unknown\"}")), components()).definitions().get("sample:unknown");
            assertTrue(unknown.unsupportedReason().contains("NEKO-5250"));
        }
        var conflict = new RecipeSchemaGraphCompiler().compile(List.of(document("sample:conflict",
                "{\"factory\":\"sample:first\",\"recipe_factory\":\"sample:second\"}")), factoryComponents(factory));
        assertTrue(conflict.definitions().get("sample:conflict").unsupportedReason().contains("NEKO-5250"));
    }

    @Test
    void factoryAndProcessorCapabilityInputsAreCopiedAndUnexpectedOwnerFailuresPropagate() {
        var calls = new AtomicInteger();
        var factory = (RecipeExecutionFactory) (builder, host, session) -> builder;
        var input = document("sample:factory", "{\"factory\":{\"type\":\"sample:factory\"},\"post_processors\":[{\"type\":\"sample:processor\"}]}");
        String before = input.json().toString();
        SemanticComponents resolver = new SemanticComponents() {
            @Override public RecipeFieldValueCodec resolve(JsonElement expression) { return components().resolve(expression); }
            @Override public JsonElement encodeConstant(JsonElement expression, JsonElement literal) { return literal.deepCopy(); }
            public RecipeExecutionFactory executionFactory(JsonElement expression, List<JsonElement> processors) {
                calls.incrementAndGet();
                expression.getAsJsonObject().addProperty("mutated", true);
                processors.getFirst().getAsJsonObject().addProperty("mutated", true);
                return factory;
            }
        };
        var result = new RecipeSchemaGraphCompiler().compile(List.of(input), resolver);
        assertNull(result.definitions().get(input.id()).unsupportedReason());
        assertEquals(1, calls.get());
        assertEquals(before, input.json().toString());
        assertSame(factory, result.definitions().get(input.id()).executionFactory());
    }

    private static SemanticComponents factoryComponents(RecipeExecutionFactory factory) {
        return new SemanticComponents() {
            @Override public RecipeFieldValueCodec resolve(JsonElement expression) { return components().resolve(expression); }
            @Override public JsonElement encodeConstant(JsonElement expression, JsonElement literal) { return literal.deepCopy(); }
            public RecipeExecutionFactory executionFactory(JsonElement expression, List<JsonElement> processors) {
                if (!expression.getAsString().equals("nekujs:native_shaped") || !processors.isEmpty()) {
                    throw new IllegalArgumentException("Unsupported factory fixture");
                }
                return factory;
            }
        };
    }

    static SchemaDocument document(String id, String json) {
        return new SchemaDocument("fixture", id, "data/" + id.replace(':', '/') + ".json",
                JsonParser.parseString(json).getAsJsonObject());
    }

    static SemanticComponents components() {
        return new SemanticComponents() {
            @Override
            public RecipeFieldValueCodec resolve(JsonElement expression) {
                return new RecipeFieldValueCodec(AdapterInputShape.string(), (value, host) -> {
                    throw new AssertionError("The pure compiler must not encode script values");
                });
            }

            @Override
            public JsonElement encodeConstant(JsonElement expression, JsonElement literal) {
                return literal.deepCopy();
            }
        };
    }
}
