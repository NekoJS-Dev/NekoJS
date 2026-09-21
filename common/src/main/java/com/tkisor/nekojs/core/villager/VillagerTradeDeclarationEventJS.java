package com.tkisor.nekojs.core.villager;

import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Payload of the {@code ServerEvents.tradeDeclaration} data sub-event (ticket 22): server
 * scripts append trades to existing trade sets here.
 *
 * <p>First version of the public surface is {@code add} only — no remove/replace/modify.
 * Collection normalizes the configuration into a {@link VillagerTradeDeclaration}; invalid
 * configuration throws, which fails the whole batch (no partial mutation, the old active
 * trades keep serving) instead of silently skipping a trade.
 *
 * <p>The payload is dispatched by the platform/version adapter at its two legal collection
 * points (initial generation on server start and the candidate DOMAIN_PLAN phase of a SERVER
 * reload); it never mutates a live registry itself.
 */
public final class VillagerTradeDeclarationEventJS {

    private static final Pattern ITEM_SPEC = Pattern.compile("(?i)^(\\d+)\\s*x\\s+(\\S+)$");

    private final VillagerTradeCandidatePlan plan;
    private int addedCount;

    public VillagerTradeDeclarationEventJS(VillagerTradeCandidatePlan plan) {
        this.plan = plan;
    }

    /**
     * Appends a trade to an existing trade set.
     *
     * <pre>
     * ServerEvents.tradeDeclaration(event =&gt; {
     *   event.add('minecraft:farmer/level_1', {
     *     cost: '1x minecraft:emerald',      // required; 'minecraft:emerald' means count 1
     *     costB: '2x minecraft:apple',       // optional secondary cost
     *     result: '5x minecraft:apple',      // required
     *     maxUses: 12, xp: 2, priceMultiplier: 0.05
     *   })
     * })
     * </pre>
     *
     * @param tradeSet trade set registry id (26.x) or {@code '<profession>/level_<n>'} /
     *                 {@code 'wandering_trader/<pool>'} pool id (1.21.1)
     * @param config   trade configuration object; see the example above
     */
    public void add(String tradeSet, Object config) {
        if (tradeSet == null || tradeSet.isBlank()) {
            throw new IllegalArgumentException("trade set id must not be empty");
        }
        Value cfg = config == null ? null : Value.asValue(config);
        ItemSpec cost = readItem(cfg, "cost", true);
        ItemSpec additionalCost = readItem(cfg, "costB", false);
        ItemSpec result = readItem(cfg, "result", true);
        int maxUses = readInt(cfg, "maxUses", 12);
        int xp = readInt(cfg, "xp", 2);
        double priceMultiplier = readNumber(cfg, "priceMultiplier", 0.05D);
        plan.add(new VillagerTradeDeclaration(
                tradeSet.trim(),
                cost.id, cost.count,
                additionalCost == null ? null : additionalCost.id,
                additionalCost == null ? 1 : additionalCost.count,
                result.id, result.count,
                maxUses, xp, priceMultiplier,
                currentScriptId()));
        addedCount++;
    }

    /** Number of declarations recorded so far during this event. */
    public int getAddedCount() {
        return addedCount;
    }

    private static String currentScriptId() {
        Context context = Context.getCurrent();
        return context == null ? null
                : com.tkisor.nekojs.script.ScriptContextRegistry.currentScriptIdOf(context);
    }

    private record ItemSpec(String id, int count) {}

    /** Reads '3x minecraft:apple', 'minecraft:apple' or {@code { item: 'minecraft:apple', count: 3 }}. */
    private static ItemSpec readItem(Value cfg, String key, boolean required) {
        Value raw = member(cfg, key);
        if (raw == null || raw.isNull()) {
            if (!required) {
                return null;
            }
            throw new IllegalArgumentException("missing required '" + key + "' entry");
        }
        int count = 1;
        String idText;
        if (raw.isString()) {
            String text = raw.asString().trim();
            Matcher matcher = ITEM_SPEC.matcher(text);
            if (matcher.matches()) {
                count = Integer.parseInt(matcher.group(1));
                idText = matcher.group(2).trim();
            } else {
                idText = text;
            }
        } else if (raw.hasMembers()) {
            Value item = member(raw, "item");
            Value itemCount = member(raw, "count");
            if (item == null || !item.isString()) {
                throw new IllegalArgumentException("'" + key + "' object form needs { item: '<id>', count: n }");
            }
            idText = item.asString().trim();
            if (itemCount != null && itemCount.isNumber()) {
                count = (int) itemCount.asDouble();
            }
        } else {
            throw new IllegalArgumentException("'" + key + "' must be an item id string or { item, count }");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("'" + key + "' count must be > 0 (got " + count + ")");
        }
        return new ItemSpec(idText, count);
    }

    private static int readInt(Value cfg, String key, int fallback) {
        Value value = member(cfg, key);
        return value != null && value.isNumber() ? (int) value.asDouble() : fallback;
    }

    private static double readNumber(Value cfg, String key, double fallback) {
        Value value = member(cfg, key);
        return value != null && value.isNumber() ? value.asDouble() : fallback;
    }

    private static Value member(Value cfg, String key) {
        return cfg != null && cfg.hasMembers() && cfg.hasMember(key) ? cfg.getMember(key) : null;
    }

    /** Locale-independent rendering used by diagnostics. */
    static String describe(Value value) {
        return value == null ? "null" : String.valueOf(value).toLowerCase(Locale.ROOT);
    }
}
