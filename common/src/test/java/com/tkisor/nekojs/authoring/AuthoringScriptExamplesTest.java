package com.tkisor.nekojs.authoring;

import com.google.gson.JsonObject;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Checks shipped scenario sources and their isolated platform-call contracts, not live game effects. */
class AuthoringScriptExamplesTest {
    @Test
    void everyShippedSourceHasInstallationEvidenceAndAnIdenticalCookbookBlock() throws Exception {
        AuthoringBundle bundle = AuthoringBundle.load();
        JsonObject metadata = AuthoringJson.parse(bundle.read("examples/metadata.json"));
        Set<String> ids = new HashSet<>();
        Set<String> documentedSources = new HashSet<>();
        String guide = bundle.read("handbook/scripting-examples.md");
        Set<String> blocks = new HashSet<>();
        var matcher = Pattern.compile("```(?:javascript|js|cjs|tsx)\n(.*?)```", Pattern.DOTALL).matcher(guide);
        while (matcher.find()) blocks.add(matcher.group(1).strip());
        for (var value : metadata.getAsJsonArray("examples")) {
            JsonObject example = value.getAsJsonObject();
            assertTrue(ids.add(example.get("id").getAsString()), "duplicate scenario ID");
            for (String key : Set.of("scriptType", "language", "verification", "assertion", "trigger", "expected", "cleanup")) {
                assertFalse(example.get(key).getAsString().isBlank(), example + " missing " + key);
            }
            assertFalse(example.getAsJsonArray("requires").isEmpty());
            for (var installed : example.getAsJsonArray("install")) {
                JsonObject file = installed.getAsJsonObject();
                String source = file.get("file").getAsString();
                String destination = file.get("destination").getAsString();
                assertFalse(source.contains(".."));
                assertFalse(destination.contains(".."));
                assertTrue(destination.startsWith("nekojs/"));
                assertTrue(documentedSources.add(source), "duplicate source " + source);
                String payload = bundle.read("examples/" + source);
                assertTrue(blocks.contains(payload.strip()), "cookbook drift: " + source);
                assertTrue(guide.contains(destination), "missing destination: " + destination);
            }
        }
        Set<String> shippedSources = new HashSet<>();
        for (String path : bundle.paths()) {
            if (path.startsWith("examples/") && !path.equals("examples/metadata.json")) shippedSources.add(path.substring("examples/".length()));
        }
        assertEquals(shippedSources, documentedSources, "every shipped script must have a copy/run/result contract");
        assertEquals(Set.of("greeting", "event-subscription", "player-login", "recipes", "item-tags", "block-tags",
                "registry", "item-components", "state-and-timer", "commonjs", "assertions", "client-counter", "client-controls", "client-forms-and-dialogs", "client-workspace"), ids);
    }

    @Test
    void recipeExampleRegistersOneDeferredCallbackAndThreePreciselyNamedRecipes() throws Exception {
        try (Context context = context("""
                globalThis.callbacks = [];
                globalThis.calls = [];
                globalThis.ServerEvents = {recipes: callback => callbacks.push(callback)};
                globalThis.Item = {of: (id, count = 1) => ({item: id, count})};
                globalThis.Ingredient = {of: id => ({ingredient: id})};
                globalThis.recipeEvent = {
                    remove: filter => calls.push({kind: "remove", filter}),
                    shaped: (result, pattern, keys) => ({id: id => calls.push({kind: "shaped", id, result, pattern, keys})}),
                    shapeless: (result, ingredients) => ({id: id => calls.push({kind: "shapeless", id, result, ingredients})}),
                    smelting: (result, ingredient, xp, cookTime) => ({id: id => calls.push({kind: "smelting", id, result, ingredient, xp, cookTime})})
                };
                """)) {
            run(context, "server/recipes.js");
            assertEquals(1, context.eval("js", "callbacks.length").asInt());
            assertEquals(0, context.eval("js", "calls.length").asInt(), "world-facing work is deferred to the recipe event");
            context.eval("js", "callbacks[0](recipeEvent)");
            var calls = AuthoringJson.parseElement(context.eval("js", "JSON.stringify(calls)").asString()).getAsJsonArray();
            assertEquals(4, calls.size());
            assertEquals("minecraft:stick", calls.get(0).getAsJsonObject().getAsJsonObject("filter").get("id").getAsString());
            JsonObject shaped = calls.get(1).getAsJsonObject();
            assertEquals("nekojs_example:sticks", shaped.get("id").getAsString());
            assertEquals(4, shaped.getAsJsonObject("result").get("count").getAsInt());
            assertEquals("minecraft:oak_planks", shaped.getAsJsonObject("keys").getAsJsonObject("P").get("ingredient").getAsString());
            assertEquals("[\"P\",\"P\"]", shaped.get("pattern").toString());
            JsonObject shapeless = calls.get(2).getAsJsonObject();
            assertEquals("nekojs_example:flint", shapeless.get("id").getAsString());
            assertEquals(2, shapeless.getAsJsonArray("ingredients").size());
            assertEquals(shapeless.getAsJsonArray("ingredients").get(0), shapeless.getAsJsonArray("ingredients").get(1));
            JsonObject smelting = calls.get(3).getAsJsonObject();
            assertEquals("nekojs_example:glass", smelting.get("id").getAsString());
            assertEquals("minecraft:glass", smelting.getAsJsonObject("result").get("item").getAsString());
            assertEquals("minecraft:sand", smelting.getAsJsonObject("ingredient").get("ingredient").getAsString());
            assertEquals(0.1, smelting.get("xp").getAsDouble());
            assertEquals(200, smelting.get("cookTime").getAsInt());
        }
    }

    @Test
    void loginExampleUsesTheDocumentedFabricPlayerVariantOnlyWhenTheEventFires() throws Exception {
        try (Context context = context("""
                globalThis.callbacks = [];
                globalThis.PlayerEvents = {loggedIn: callback => callbacks.push(callback)};
                globalThis.sent = [];
                globalThis.playerReads = 0;
                globalThis.login = {get player() {playerReads++; return {sendMessage: text => sent.push(text)};}};
                """)) {
            context.eval("js", documentedFabricLoginVariant(AuthoringBundle.load().read("examples/server/player-login.js")));
            assertEquals(1, context.eval("js", "callbacks.length").asInt());
            assertEquals(0, context.eval("js", "playerReads").asInt());
            context.eval("js", "callbacks[0](login)");
            assertEquals(1, context.eval("js", "playerReads").asInt());
            assertEquals("Welcome to NekoJS!", context.eval("js", "sent[0]").asString());
            assertEquals(1, context.eval("js", "sent.length").asInt());
        }
    }

    @Test
    void loginExampleUsesTheEntityPropertyForTheNativeNeoForgePayload() throws Exception {
        try (Context context = context("""
                globalThis.callbacks = [];
                globalThis.PlayerEvents = {loggedIn: callback => callbacks.push(callback)};
                globalThis.sent = [];
                globalThis.entityReads = 0;
                globalThis.login = {get entity() {entityReads++; return {sendMessage: text => sent.push(text)};}};
                """)) {
            run(context, "server/player-login.js");
            assertEquals(1, context.eval("js", "callbacks.length").asInt());
            assertEquals(0, context.eval("js", "entityReads").asInt());
            assertEquals(0, context.eval("js", "sent.length").asInt());
            context.eval("js", "callbacks[0](login)");
            assertEquals(1, context.eval("js", "entityReads").asInt());
            assertEquals("Welcome to NekoJS!", context.eval("js", "sent[0]").asString());
            assertEquals(1, context.eval("js", "sent.length").asInt());
        }
    }

    @Test
    void tagExamplesTargetSeparateRegistriesAndQueueTheDocumentedPlainIds() throws Exception {
        for (String registry : new String[]{"item", "block"}) {
            try (Context context = context("""
                    globalThis.subscriptions = [];
                    globalThis.edits = [];
                    globalThis.ServerEvents = {tags: (registry, callback) => subscriptions.push({registry, callback})};
                    globalThis.tags = {
                        add: (tag, entry) => edits.push({kind: "add", tag, entry}),
                        remove: (tag, entry) => edits.push({kind: "remove", tag, entry})
                    };
                    """)) {
                run(context, "server/" + registry + "-tags.js");
                assertEquals(1, context.eval("js", "subscriptions.length").asInt());
                assertEquals("minecraft:" + registry, context.eval("js", "subscriptions[0].registry").asString());
                assertEquals(0, context.eval("js", "edits.length").asInt());
                context.eval("js", "subscriptions[0].callback(tags)");
                var edits = AuthoringJson.parseElement(context.eval("js", "JSON.stringify(edits)").asString()).getAsJsonArray();
                assertEquals(registry.equals("item") ? 3 : 1, edits.size());
                for (var edit : edits) assertEquals("nekojs_example:stones", edit.getAsJsonObject().get("tag").getAsString());
                assertEquals("minecraft:stone", edits.get(0).getAsJsonObject().get("entry").getAsString());
                if (registry.equals("item")) {
                    assertEquals("add", edits.get(1).getAsJsonObject().get("kind").getAsString());
                    assertEquals("remove", edits.get(2).getAsJsonObject().get("kind").getAsString());
                    assertEquals("minecraft:granite", edits.get(1).getAsJsonObject().get("entry").getAsString());
                    assertEquals(edits.get(1).getAsJsonObject().get("entry"), edits.get(2).getAsJsonObject().get("entry"));
                }
            }
        }
    }

    @Test
    void registryExampleConfiguresBuildersLastAndDoesNotDuplicateTheImplicitBlockItem() throws Exception {
        try (Context context = context("""
                globalThis.callbacks = [];
                globalThis.created = [];
                globalThis.RegistryEvents = {register: callback => callbacks.push(callback)};
                globalThis.registry = {
                    item: (id, configure) => {
                        const properties = Object.seal({maxStackSize: 64, rarity: "common"});
                        configure(properties); created.push({kind: "item", id, properties});
                    },
                    block: (id, configure) => {
                        const properties = Object.seal({hardness: 0, resistance: 0, lightLevel: 0});
                        configure(properties); created.push({kind: "block", id, properties});
                    }
                };
                """)) {
            run(context, "startup/registry.js");
            assertEquals(1, context.eval("js", "callbacks.length").asInt());
            assertEquals(0, context.eval("js", "created.length").asInt());
            context.eval("js", "callbacks[0](registry)");
            var created = AuthoringJson.parseElement(context.eval("js", "JSON.stringify(created)").asString()).getAsJsonArray();
            assertEquals(2, created.size());
            JsonObject item = created.get(0).getAsJsonObject();
            assertEquals("item", item.get("kind").getAsString());
            assertEquals("nekojs_example:ruby", item.get("id").getAsString());
            assertEquals(16, item.getAsJsonObject("properties").get("maxStackSize").getAsInt());
            assertEquals("rare", item.getAsJsonObject("properties").get("rarity").getAsString());
            JsonObject block = created.get(1).getAsJsonObject();
            assertEquals("block", block.get("kind").getAsString());
            assertEquals("nekojs_example:ruby_block", block.get("id").getAsString());
            assertEquals(2, block.getAsJsonObject("properties").get("hardness").getAsInt());
            assertEquals(6, block.getAsJsonObject("properties").get("resistance").getAsInt());
            assertEquals(3, block.getAsJsonObject("properties").get("lightLevel").getAsInt());
        }
    }

    @Test
    void stateExampleUsesPrimitiveTopLevelKeysAndQueuesABoundedOneShotTimer() throws Exception {
        try (Context context = context("""
                globalThis.global = {};
                globalThis.shared = {};
                globalThis.timers = [];
                globalThis.setTimeout = (callback, delay) => timers.push({callback, delay});
                """)) {
            run(context, "startup/shared-config.js");
            run(context, "server/state-and-timer.js");
            assertEquals("Authoring SERVER load #1: Welcome to NekoJS", context.eval("js", "messages[0]").asString());
            run(context, "server/state-and-timer.js");
            assertEquals("Authoring SERVER load #2: Welcome to NekoJS", context.eval("js", "messages[1]").asString());
            assertEquals("number", context.eval("js", "typeof global.authoringLoads").asString());
            assertEquals("string", context.eval("js", "typeof shared.authoringMessage").asString());
            assertEquals(1000, context.eval("js", "timers[1].delay").asInt());
            assertEquals(2, context.eval("js", "messages.length").asInt());
            context.eval("js", "timers[1].callback()");
            assertEquals("Authoring timeout fired", context.eval("js", "messages[2]").asString());
        }
    }

    @Test
    void componentExampleUsesTheExactComponentStringAndExplicitCountsWithoutGivingItems() throws Exception {
        try (Context context = context("""
                globalThis.created = [];
                globalThis.Item = {of: (id, count) => {const stack = {id, count}; created.push(stack); return stack;}};
                """)) {
            run(context, "server/item-components.js");
            assertEquals(2, context.eval("js", "created.length").asInt());
            assertEquals("minecraft:iron_sword[damage=2]", context.eval("js", "created[0].id").asString());
            assertEquals(1, context.eval("js", "created[0].count").asInt());
            assertEquals("minecraft:stone", context.eval("js", "created[1].id").asString());
            assertEquals(4, context.eval("js", "created[1].count").asInt());
            assertEquals(4, context.eval("js", "messages.length").asInt());
            assertEquals("Detached sword with damage 2:", context.eval("js", "messages[0]").asString());
            assertEquals("Detached stone stack with count 4:", context.eval("js", "messages[2]").asString());
        }
    }

    private static Context context(String fixtures) {
        Context context = Context.newBuilder("js").build();
        context.eval("js", "globalThis.messages = []; globalThis.console = {info: text => messages.push(String(text))}; " + fixtures);
        return context;
    }

    static String documentedFabricLoginVariant(String canonicalSource) {
        assertTrue(canonicalSource.contains("// NeoForge default. On Fabric, replace the entity property below with player."),
                "The installed-copy adaptation must be documented by the actual shipped resource");
        int occurrence = canonicalSource.indexOf("event.entity");
        assertTrue(occurrence >= 0, "The NeoForge source must contain its native payload property");
        assertEquals(occurrence, canonicalSource.lastIndexOf("event.entity"),
                "The documented Fabric adaptation must replace exactly one property occurrence");
        return canonicalSource.replace("event.entity", "event.player");
    }

    private static void run(Context context, String file) throws Exception {
        context.eval("js", AuthoringBundle.load().read("examples/" + file));
    }
}
