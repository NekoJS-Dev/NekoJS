package com.tkisor.nekojs.core.stage.gate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.StageReadView;
import com.tkisor.nekojs.api.stage.StageScope;
import com.tkisor.nekojs.core.stage.ClientStageMirror;
import com.tkisor.nekojs.core.stage.StagePlatformHooks.ReplacementChange;
import com.tkisor.nekojs.core.stage.gate.CompiledStageGateSnapshot.WireItemEntry;
import com.tkisor.nekojs.core.stage.gate.CompiledStageGateSnapshot.WireItemRule;
import com.tkisor.nekojs.core.stage.gate.CompiledStageGateSnapshot.WireRecipeEntry;
import com.tkisor.nekojs.core.stage.gate.CompiledStageGateSnapshot.WireRecipeRule;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Compact JSON wire codec for server-to-client stage state and viewer gate synchronization
 * transported over the reserved {@link #CHANNEL_KEY} slot of {@code ClientDataSyncPacket}.
 *
 * <p>Supports three S2C payload types under schema version {@link #SCHEMA_VERSION}:
 * <ul>
 *   <li>{@code "full"}: player permanent/temporary stages, server permanent/temporary stages,
 *       and an optional compiled viewer gate summary.</li>
 *   <li>{@code "delta"}: incremental {@code add}/{@code remove}/{@code clear}/{@code replace}
 *       updates for {@code player} or {@code server} scope, automatically re-evaluating cached
 *       viewer gate visibility on the target {@link ClientStageMirror}.</li>
 *   <li>{@code "gates"}: standalone viewer gate summary broadcast after server script/recipe
 *       reloads, re-evaluated immediately against the client's current stage mirror.</li>
 * </ul>
 */
public final class StageWireProtocol {

    /** Reserved {@code ClientDataSyncPacket} channel key intercepted by client stage sync hooks. */
    public static final String CHANNEL_KEY = "__nekojs_stages__";

    /** Wire protocol schema version. */
    public static final int SCHEMA_VERSION = 1;

    private static final String TYPE_FULL = "full";
    private static final String TYPE_DELTA = "delta";
    private static final String TYPE_GATES = "gates";
    private static final String TYPE_GATE_DELTA = "gate_delta";

    private static final Map<ClientStageMirror, ClientGateSummary> CLIENT_GATE_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private StageWireProtocol() {
    }

    /**
     * Encodes a full stage and viewer-gate synchronization payload for a joining or resyncing player.
     */
    public static String encodeFullSync(
            @Nullable Set<String> playerPermanent,
            @Nullable Map<String, Long> playerTemporary,
            @Nullable Set<String> serverPermanent,
            @Nullable Map<String, Long> serverTemporary,
            @Nullable CompiledStageGateSnapshot snapshot
    ) {
        return encodeFullSync(playerPermanent, playerTemporary, serverPermanent, serverTemporary, snapshot, false);
    }

    public static String encodeFullSync(
            @Nullable Set<String> playerPermanent,
            @Nullable Map<String, Long> playerTemporary,
            @Nullable Set<String> serverPermanent,
            @Nullable Map<String, Long> serverTemporary,
            @Nullable CompiledStageGateSnapshot snapshot,
            boolean stateOnly
    ) {
        JsonObject root = new JsonObject();
        root.addProperty("v", SCHEMA_VERSION);
        root.addProperty("type", TYPE_FULL);
        if (stateOnly) root.addProperty("stateOnly", true);
        root.add("playerPermanent", encodeStageSet(playerPermanent));
        root.add("playerTemporary", encodeTemporaryMap(playerTemporary, playerPermanent));
        root.add("serverPermanent", encodeStageSet(serverPermanent));
        root.add("serverTemporary", encodeTemporaryMap(serverTemporary, serverPermanent));
        if (snapshot != null) {
            root.add("gates", buildGateSummaryObject(snapshot));
        }
        return root.toString();
    }

    /**
     * Encodes a full player and server stage snapshot without replacing the client's gate summary.
     */
    public static String encodeFullSync(
            @Nullable Set<String> playerPermanent,
            @Nullable Map<String, Long> playerTemporary,
            @Nullable Set<String> serverPermanent,
            @Nullable Map<String, Long> serverTemporary
    ) {
        return encodeFullSync(playerPermanent, playerTemporary, serverPermanent, serverTemporary, null);
    }

    /**
     * Encodes an incremental stage delta ({@code add}, {@code remove}, {@code clear}, or {@code replace}).
     */
    public static String encodeDelta(
            String scope,
            String action,
            @Nullable String stage,
            long remainingTicks
    ) {
        return encodeDelta(scope, action, stage, remainingTicks, null, null);
    }

    /**
     * Encodes an incremental stage delta with optional full replacement state for {@code replace} actions.
     */
    public static String encodeDelta(
            String scope,
            String action,
            @Nullable String stage,
            long remainingTicks,
            @Nullable Set<String> fullPermanent,
            @Nullable Map<String, Long> fullTemporary
    ) {
        return buildDeltaObject(scope, action, stage, remainingTicks, fullPermanent, fullTemporary).toString();
    }

    public static String encodeReplacement(
            String scope,
            Set<String> permanent,
            Map<String, Long> temporary,
            List<ReplacementChange> changes
    ) {
        JsonObject root = buildDeltaObject(scope, "replace", "", 0L, permanent, temporary);
        JsonArray entries = new JsonArray();
        for (ReplacementChange change : changes) {
            JsonObject entry = new JsonObject();
            entry.addProperty("stage", change.stage());
            entry.addProperty("temporary", change.temporary());
            entry.addProperty("remainingTicks", change.remainingTicks());
            entries.add(entry);
        }
        root.add("replacementChanges", entries);
        return root.toString();
    }

    private static JsonObject buildDeltaObject(
            String scope,
            String action,
            @Nullable String stage,
            long remainingTicks,
            @Nullable Set<String> fullPermanent,
            @Nullable Map<String, Long> fullTemporary
    ) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(action, "action");
        String canonicalScope = "server".equalsIgnoreCase(scope.trim()) ? "server" : "player";
        String canonicalAction = action.trim().toLowerCase(Locale.ROOT);

        JsonObject root = new JsonObject();
        root.addProperty("v", SCHEMA_VERSION);
        root.addProperty("type", TYPE_DELTA);
        root.addProperty("scope", canonicalScope);
        boolean expired = "expire".equals(canonicalAction);
        root.addProperty("action", expired ? "remove" : canonicalAction);
        if (expired) root.addProperty("reason", "expired");
        root.addProperty("stage", stage == null ? "" : stage.trim());
        root.addProperty("remainingTicks", remainingTicks);
        if (fullPermanent != null) {
            root.add("permanent", encodeStageSet(fullPermanent));
        }
        if (fullTemporary != null) {
            root.add("temporary", encodeTemporaryMap(fullTemporary, fullPermanent));
        }
        return root;
    }

    /**
     * Encodes a standalone viewer gate summary from {@code snapshot} for client recipe-viewer and
     * tooltip synchronization after a script or recipe reload.
     */
    public static String encodeGateSummary(@Nullable CompiledStageGateSnapshot snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("v", SCHEMA_VERSION);
        root.addProperty("type", TYPE_GATES);
        root.add(
                "gates",
                buildGateSummaryObject(snapshot != null ? snapshot : CompiledStageGateSnapshot.EMPTY)
        );
        return root.toString();
    }

    /**
     * Parses a wire JSON payload and applies the resulting stage and viewer visibility state to
     * {@code mirror}.
     */
    public static void applyOnClient(String json, ClientStageMirror mirror) {
        Objects.requireNonNull(mirror, "mirror");
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("[NEKO-4072] Stage sync payload cannot be null or blank");
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (JsonParseException ex) {
            throw new IllegalArgumentException("[NEKO-4072] Invalid stage sync JSON: " + ex.getMessage(), ex);
        }
        applyOnClient(parsed, mirror);
    }

    /**
     * Applies an already-parsed JSON stage sync element to {@code mirror}.
     */
    public static void applyOnClient(JsonElement element, ClientStageMirror mirror) {
        Objects.requireNonNull(mirror, "mirror");
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("[NEKO-4072] Stage sync payload must be a JSON object");
        }
        JsonObject root = element.getAsJsonObject();
        int version = optionalInt(root, "v", SCHEMA_VERSION);
        if (version != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "[NEKO-4072] Unsupported stage wire schema version: " + version
            );
        }

        String type = requireString(root, "type");
        switch (type) {
            case TYPE_FULL -> {
                Set<String> playerPerm = decodeStageSet(root.get("playerPermanent"));
                Map<String, Long> playerTemp = decodeTemporaryMap(root.get("playerTemporary"), playerPerm);
                Set<String> serverPerm = decodeStageSet(root.get("serverPermanent"));
                Map<String, Long> serverTemp = decodeTemporaryMap(root.get("serverTemporary"), serverPerm);
                mirror.applyFullSnapshot(playerPerm, playerTemp, serverPerm, serverTemp,
                        optionalBoolean(root, "stateOnly", false));

                if (root.has("gates") && root.get("gates").isJsonObject()) {
                    ClientGateSummary summary = decodeGateSummaryObject(root.getAsJsonObject("gates"));
                    CLIENT_GATE_CACHE.put(mirror, summary);
                    applyEvaluatedSummary(summary, mirror);
                } else {
                    ClientGateSummary cached = CLIENT_GATE_CACHE.get(mirror);
                    if (cached != null) {
                        applyEvaluatedSummary(cached, mirror);
                    }
                }
            }
            case TYPE_DELTA -> {
                String scope = requireString(root, "scope");
                String action = requireString(root, "action");
                if ("remove".equals(action) && "expired".equals(optionalString(root, "reason"))) action = "expire";
                String stage = optionalString(root, "stage");
                long remainingTicks = optionalLong(root, "remainingTicks", 0L);
                Set<String> fullPerm = root.has("permanent") ? decodeStageSet(root.get("permanent")) : null;
                Map<String, Long> fullTemp = root.has("temporary")
                        ? decodeTemporaryMap(root.get("temporary"), fullPerm != null ? fullPerm : Set.of())
                        : null;
                if ("replace".equalsIgnoreCase(action.trim()) && root.has("replacementChanges")) {
                    mirror.applyReplacement(scope, fullPerm, fullTemp,
                            decodeReplacementChanges(root.get("replacementChanges")));
                } else {
                    mirror.applyDelta(scope, action, stage, remainingTicks, fullPerm, fullTemp);
                }

                ClientGateSummary cached = CLIENT_GATE_CACHE.get(mirror);
                if (cached != null) {
                    applyEvaluatedSummary(cached, mirror);
                }
            }
            case TYPE_GATE_DELTA -> {
                ClientGateSummary old = CLIENT_GATE_CACHE.get(mirror);
                if (old != null) {
                    ClientGateSummary changed = decodeGateSummaryObject(root);
                    ClientGateSummary summary = new ClientGateSummary(
                            mergeGateEntries(old.items(), changed.items(), decodeStageSet(root.get("itemsRemoved")), WireItemEntry::itemId),
                            mergeGateEntries(old.recipes(), changed.recipes(), decodeStageSet(root.get("recipesRemoved")), WireRecipeEntry::recipeId));
                    CLIENT_GATE_CACHE.put(mirror, summary);
                    applyEvaluatedSummary(summary, mirror);
                }
            }
            case TYPE_GATES -> {
                JsonElement gatesElement = root.get("gates");
                if (gatesElement == null || !gatesElement.isJsonObject()) {
                    throw new IllegalArgumentException("[NEKO-4072] Missing 'gates' object in stage gate summary");
                }
                ClientGateSummary summary = decodeGateSummaryObject(gatesElement.getAsJsonObject());
                CLIENT_GATE_CACHE.put(mirror, summary);
                applyEvaluatedSummary(summary, mirror);
            }
            default -> throw new IllegalArgumentException(
                    "[NEKO-4072] Unknown stage wire payload type: " + type
            );
        }
    }

    /** Clears any cached client gate summary associated with {@code mirror}. */
    public static void clearCachedSummary(ClientStageMirror mirror) {
        if (mirror != null) {
            CLIENT_GATE_CACHE.remove(mirror);
        }
    }

    // ---- Gate summary encoding & client evaluation ----

    public record GateUpdate(String message, String baseline) {
    }

    public static GateUpdate encodeGateUpdate(@Nullable String previous, CompiledStageGateSnapshot snapshot) {
        JsonObject next = buildGateSummaryObject(snapshot);
        if (previous == null) {
            JsonObject full = new JsonObject();
            full.addProperty("v", SCHEMA_VERSION);
            full.addProperty("type", TYPE_GATES);
            full.add("gates", next);
            return new GateUpdate(full.toString(), next.toString());
        }
        JsonObject old = JsonParser.parseString(previous).getAsJsonObject();
        JsonObject patch = new JsonObject();
        patch.addProperty("v", SCHEMA_VERSION);
        patch.addProperty("type", TYPE_GATE_DELTA);
        for (String category : List.of("items", "recipes")) {
            var before = indexWireEntries(old.getAsJsonArray(category));
            var after = indexWireEntries(next.getAsJsonArray(category));
            JsonArray entries = new JsonArray();
            JsonArray removed = new JsonArray();
            after.forEach((id, entry) -> { if (!entry.equals(before.get(id))) entries.add(entry); });
            before.keySet().forEach(id -> { if (!after.containsKey(id)) removed.add(id); });
            if (!entries.isEmpty()) patch.add(category, entries);
            if (!removed.isEmpty()) patch.add(category + "Removed", removed);
        }
        return new GateUpdate(patch.size() == 2 ? "" : patch.toString(), next.toString());
    }

    private static Map<String, JsonObject> indexWireEntries(JsonArray entries) {
        Map<String, JsonObject> index = new LinkedHashMap<>();
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            index.put(requireString(entry, "id"), entry);
        }
        return index;
    }

    private static <T> List<T> mergeGateEntries(List<T> current, List<T> changed,
                                                  Set<String> removed, java.util.function.Function<T, NekoId> id) {
        Map<NekoId, T> entries = new LinkedHashMap<>();
        for (T entry : current) {
            NekoId key = id.apply(entry);
            if (!removed.contains(key.toString())) entries.put(key, entry);
        }
        for (T entry : changed) entries.put(id.apply(entry), entry);
        return List.copyOf(entries.values());
    }

    private static JsonObject buildGateSummaryObject(CompiledStageGateSnapshot snapshot) {
        JsonObject summary = new JsonObject();

        JsonArray itemsArray = new JsonArray();
        for (WireItemEntry entry : snapshot.exportClientItemEntries()) {
            JsonObject itemObj = new JsonObject();
            itemObj.addProperty("id", entry.itemId().toString());
            if (!entry.winningRules().isEmpty()) {
                JsonArray rulesArr = new JsonArray();
                for (WireItemRule rule : entry.winningRules()) {
                    JsonObject ruleObj = new JsonObject();
                    ruleObj.addProperty("s", rule.stage());
                    ruleObj.addProperty("sc", rule.scope().id());
                    if (rule.showInViewer()) {
                        ruleObj.addProperty("v", true);
                    }
                    if (!rule.lockedName().isEmpty()) {
                        ruleObj.addProperty("n", rule.lockedName());
                    }
                    rulesArr.add(ruleObj);
                }
                itemObj.add("r", rulesArr);
            }
            if (!entry.requiredStages().isEmpty()) {
                JsonArray reqArr = new JsonArray();
                for (String reqStage : entry.requiredStages()) {
                    reqArr.add(reqStage);
                }
                itemObj.add("req", reqArr);
            }
            itemsArray.add(itemObj);
        }
        summary.add("items", itemsArray);

        JsonArray recipesArray = new JsonArray();
        for (WireRecipeEntry entry : snapshot.exportClientRecipeEntries()) {
            JsonObject recObj = new JsonObject();
            recObj.addProperty("id", entry.recipeId().toString());
            JsonArray rulesArr = new JsonArray();
            for (WireRecipeRule rule : entry.winningHiddenRules()) {
                JsonObject ruleObj = new JsonObject();
                ruleObj.addProperty("s", rule.stage());
                ruleObj.addProperty("sc", rule.scope().id());
                rulesArr.add(ruleObj);
            }
            recObj.add("r", rulesArr);
            recipesArray.add(recObj);
        }
        summary.add("recipes", recipesArray);

        return summary;
    }

    private static ClientGateSummary decodeGateSummaryObject(JsonObject summaryObj) {
        List<WireItemEntry> items = new ArrayList<>();
        if (summaryObj.has("items") && summaryObj.get("items").isJsonArray()) {
            for (JsonElement el : summaryObj.getAsJsonArray("items")) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject obj = el.getAsJsonObject();
                String rawId = optionalString(obj, "id");
                if (rawId == null || rawId.isBlank()) {
                    continue;
                }
                NekoId itemId = NekoId.of(rawId);
                List<WireItemRule> rules = new ArrayList<>();
                if (obj.has("r") && obj.get("r").isJsonArray()) {
                    for (JsonElement rEl : obj.getAsJsonArray("r")) {
                        if (!rEl.isJsonObject()) {
                            continue;
                        }
                        JsonObject rObj = rEl.getAsJsonObject();
                        String stage = optionalString(rObj, "s");
                        if (stage == null || stage.isBlank()) {
                            continue;
                        }
                        StageScope scope = StageScope.fromString(optionalString(rObj, "sc"));
                        boolean showInViewer = optionalBoolean(rObj, "v", false);
                        String lockedName = Objects.requireNonNullElse(optionalString(rObj, "n"), "");
                        rules.add(new WireItemRule(stage.trim(), scope, showInViewer, lockedName));
                    }
                }
                List<String> req = new ArrayList<>();
                if (obj.has("req") && obj.get("req").isJsonArray()) {
                    for (JsonElement reqEl : obj.getAsJsonArray("req")) {
                        if (reqEl.isJsonPrimitive() && reqEl.getAsJsonPrimitive().isString()) {
                            String s = reqEl.getAsString();
                            if (!s.isBlank()) {
                                req.add(s.trim());
                            }
                        }
                    }
                }
                items.add(new WireItemEntry(itemId, List.copyOf(rules), List.copyOf(req)));
            }
        }

        List<WireRecipeEntry> recipes = new ArrayList<>();
        if (summaryObj.has("recipes") && summaryObj.get("recipes").isJsonArray()) {
            for (JsonElement el : summaryObj.getAsJsonArray("recipes")) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject obj = el.getAsJsonObject();
                String rawId = optionalString(obj, "id");
                if (rawId == null || rawId.isBlank()) {
                    continue;
                }
                NekoId recipeId = NekoId.of(rawId);
                List<WireRecipeRule> rules = new ArrayList<>();
                if (obj.has("r") && obj.get("r").isJsonArray()) {
                    for (JsonElement rEl : obj.getAsJsonArray("r")) {
                        if (!rEl.isJsonObject()) {
                            continue;
                        }
                        JsonObject rObj = rEl.getAsJsonObject();
                        String stage = optionalString(rObj, "s");
                        if (stage == null || stage.isBlank()) {
                            continue;
                        }
                        StageScope scope = StageScope.fromString(optionalString(rObj, "sc"));
                        rules.add(new WireRecipeRule(stage.trim(), scope));
                    }
                }
                if (!rules.isEmpty()) {
                    recipes.add(new WireRecipeEntry(recipeId, List.copyOf(rules)));
                }
            }
        }

        return new ClientGateSummary(List.copyOf(items), List.copyOf(recipes));
    }

    private static void applyEvaluatedSummary(ClientGateSummary summary, ClientStageMirror mirror) {
        StageReadView playerView = mirror.playerView();
        StageReadView serverView = mirror.serverView();

        Set<NekoId> hiddenItems = new LinkedHashSet<>();
        Map<NekoId, String> lockedNames = new LinkedHashMap<>();
        Map<NekoId, List<String>> requiredStages = new LinkedHashMap<>();

        for (WireItemEntry entry : summary.items()) {
            if (!entry.requiredStages().isEmpty()) {
                requiredStages.put(entry.itemId(), entry.requiredStages());
            }
            boolean hidden = false;
            String lockedName = null;
            for (WireItemRule rule : entry.winningRules()) {
                boolean unlocked = rule.scope().evaluate(
                        playerView.has(rule.stage()),
                        serverView.has(rule.stage())
                );
                if (!unlocked) {
                    if (!rule.showInViewer()) {
                        hidden = true;
                    }
                    if (lockedName == null && !rule.lockedName().isEmpty()) {
                        lockedName = rule.lockedName();
                    }
                }
            }
            if (hidden) {
                hiddenItems.add(entry.itemId());
            }
            if (lockedName != null) {
                lockedNames.put(entry.itemId(), lockedName);
            }
        }

        Set<NekoId> hiddenRecipes = new LinkedHashSet<>();
        for (WireRecipeEntry entry : summary.recipes()) {
            if (entry.isHidden(playerView, serverView)) {
                hiddenRecipes.add(entry.recipeId());
            }
        }

        mirror.applyVisibilitySnapshot(hiddenItems, hiddenRecipes, lockedNames, requiredStages);
    }

    private record ClientGateSummary(List<WireItemEntry> items, List<WireRecipeEntry> recipes) {
    }

    // ---- Stage set/map JSON helpers ----

    private static JsonArray encodeStageSet(@Nullable Set<String> stages) {
        JsonArray arr = new JsonArray();
        if (stages == null || stages.isEmpty()) {
            return arr;
        }
        List<String> sorted = new ArrayList<>(stages.size());
        for (String stage : stages) {
            if (stage != null && !stage.isBlank()) {
                sorted.add(stage.trim());
            }
        }
        Collections.sort(sorted);
        for (String stage : sorted) {
            arr.add(stage);
        }
        return arr;
    }

    private static JsonObject encodeTemporaryMap(
            @Nullable Map<String, Long> temporary,
            @Nullable Set<String> permanent
    ) {
        JsonObject obj = new JsonObject();
        if (temporary == null || temporary.isEmpty()) {
            return obj;
        }
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Long> entry : temporary.entrySet()) {
            String stage = entry.getKey();
            Long ticks = entry.getValue();
            if (stage != null
                    && !stage.isBlank()
                    && ticks != null
                    && ticks > 0L
                    && (permanent == null || !permanent.contains(stage.trim()))) {
                keys.add(stage.trim());
            }
        }
        Collections.sort(keys);
        for (String key : keys) {
            Long ticks = temporary.get(key);
            if (ticks != null && ticks > 0L) {
                obj.addProperty(key, ticks);
            }
        }
        return obj;
    }

    private static Set<String> decodeStageSet(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Set.of();
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("[NEKO-4072] Expected JSON array for permanent stages");
        }
        Set<String> out = new LinkedHashSet<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (item != null && item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                String val = item.getAsString();
                if (!val.isBlank()) {
                    out.add(val.trim());
                }
            }
        }
        return out.isEmpty() ? Set.of() : Collections.unmodifiableSet(out);
    }

    private static Map<String, Long> decodeTemporaryMap(
            @Nullable JsonElement element,
            Set<String> permanent
    ) {
        if (element == null || element.isJsonNull()) {
            return Map.of();
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("[NEKO-4072] Expected JSON object for temporary stages");
        }
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            JsonElement val = entry.getValue();
            if (key != null
                    && !key.isBlank()
                    && val != null
                    && val.isJsonPrimitive()
                    && val.getAsJsonPrimitive().isNumber()) {
                String trimmed = key.trim();
                long ticks = val.getAsLong();
                if (ticks > 0L && !permanent.contains(trimmed)) {
                    out.put(trimmed, ticks);
                }
            }
        }
        return out.isEmpty() ? Map.of() : Collections.unmodifiableMap(out);
    }

    private static List<ReplacementChange> decodeReplacementChanges(JsonElement element) {
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("[NEKO-4072] Expected JSON array for replacement changes");
        }
        List<ReplacementChange> changes = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                throw new IllegalArgumentException("[NEKO-4072] Expected JSON object for replacement change");
            }
            JsonObject entry = item.getAsJsonObject();
            String stage = requireString(entry, "stage").trim();
            long ticks = optionalLong(entry, "remainingTicks", Long.MIN_VALUE);
            boolean temporary = optionalBoolean(entry, "temporary", ticks > 0L);
            if (ticks < -1L || (ticks != 0L && temporary != (ticks > 0L))) {
                throw new IllegalArgumentException("[NEKO-4072] Invalid replacement change duration");
            }
            changes.add(new ReplacementChange(stage, temporary, ticks));
        }
        return changes;
    }

    private static String requireString(JsonObject obj, String field) {
        String value = optionalString(obj, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("[NEKO-4072] Missing required string field '" + field + "'");
        }
        return value;
    }

    private static @Nullable String optionalString(JsonObject obj, String field) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            return null;
        }
        JsonElement el = obj.get(field);
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("[NEKO-4072] Expected string field '" + field + "'");
        }
        return el.getAsString();
    }

    private static int optionalInt(JsonObject obj, String field, int defaultValue) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            return defaultValue;
        }
        JsonElement el = obj.get(field);
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("[NEKO-4072] Expected integer field '" + field + "'");
        }
        return el.getAsInt();
    }

    private static long optionalLong(JsonObject obj, String field, long defaultValue) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            return defaultValue;
        }
        JsonElement el = obj.get(field);
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("[NEKO-4072] Expected numeric field '" + field + "'");
        }
        return el.getAsLong();
    }

    private static boolean optionalBoolean(JsonObject obj, String field, boolean defaultValue) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            return defaultValue;
        }
        JsonElement el = obj.get(field);
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("[NEKO-4072] Expected boolean field '" + field + "'");
        }
        return el.getAsBoolean();
    }
}
