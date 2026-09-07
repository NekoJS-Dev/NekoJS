package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.villager.VillagerTradePlan;
import graal.graalvm.polyglot.Value;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Neutral SERVER declaration payload for {@code VillagerTradesEvents.modify}.
 *
 * <p>This payload only validates and stages declarative offers into the
 * current candidate plan. It intentionally has no server, registry, native
 * trade, pending-count, or apply operation. The platform posts it after a
 * successful SERVER script candidate has been committed and seals the plan
 * through {@link VillagerTradePlan.Builder}.
 */
@Doc("Declarative villager trade contribution payload. Additions are validated and staged for the next registry reconciliation.")
public final class VillagerTradeEventJS {
    private static final Pattern ITEM_SPEC = Pattern.compile("(?i)^(\\d+)\\s*x\\s+(\\S+)$");

    private final VillagerTradePlan.Builder collector;

    /** Creates a standalone payload backed by a fresh candidate collector. */
    public VillagerTradeEventJS() {
        this(VillagerTradePlan.builder());
    }

    /** Creates a payload backed by the lifecycle's candidate collector. */
    public VillagerTradeEventJS(VillagerTradePlan.Builder collector) {
        this.collector = java.util.Objects.requireNonNull(collector, "collector");
    }

    /**
     * Validates and stages one declarative offer.
     *
     * @return {@code true} after the declaration was staged
     * @throws IllegalArgumentException when the id, item, or numeric field is invalid
     */
    @Doc("Stages one offer for an existing villager or wandering trader trade set. Invalid declarations fail before entering the plan.")
    @Param(name = "tradeSet", value = "canonical trade-set id such as 'minecraft:farmer/level_1'")
    @Param(name = "config", value = "{ cost: '<count>x <item id>', costB: '<count>x <item id>' (optional), result: '<count>x <item id>', maxUses: 12, xp: 2, priceMultiplier: 0.05 }")
    @Return("true when the validated declaration was staged")
    public boolean add(String tradeSet, Object config) {
        ItemSpec cost = readItem(config, "cost", true);
        ItemSpec costB = readItem(config, "costB", false);
        ItemSpec result = readItem(config, "result", true);
        int maxUses = readInt(config, "maxUses", 12);
        int xp = readInt(config, "xp", 2);
        float priceMultiplier = readFloat(config, "priceMultiplier", 0.05f);
        collector.add(
                tradeSet,
                cost.id,
                cost.count,
                costB == null ? null : costB.id,
                costB == null ? 1 : costB.count,
                result.id,
                result.count,
                maxUses,
                xp,
                priceMultiplier);
        return true;
    }

    /** Java-side convenience overload for adapters and focused tests. */
    public boolean add(
            String tradeSet,
            String wantsId,
            int wantsCount,
            String additionalWantsId,
            int additionalWantsCount,
            String givesId,
            int givesCount,
            int maxUses,
            int xp,
            float priceMultiplier) {
        collector.add(
                tradeSet,
                wantsId,
                wantsCount,
                additionalWantsId,
                additionalWantsCount,
                givesId,
                givesCount,
                maxUses,
                xp,
                priceMultiplier);
        return true;
    }

    /** Number of validated declarations staged in this payload. */
    public int size() {
        return collector.size();
    }

    /** Immutable view of the declarations staged so far. */
    public VillagerTradePlan plan() {
        return collector.snapshot();
    }

    private static ItemSpec readItem(Object config, String key, boolean required) {
        Object raw = member(config, key);
        if (isNull(raw)) {
            if (!required) return null;
            throw new IllegalArgumentException("Missing required '" + key + "' entry");
        }

        int count = 1;
        String idText = text(raw);
        if (idText == null) {
            Object item = member(raw, "item");
            idText = text(item);
            if (idText == null) {
                throw new IllegalArgumentException("'" + key + "' must be an item id string or { item, count }");
            }
            Object rawCount = member(raw, "count");
            if (!isNull(rawCount)) {
                count = integer(rawCount, key + ".count");
            }
        } else {
            Matcher matcher = ITEM_SPEC.matcher(idText);
            if (matcher.matches()) {
                try {
                    count = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("'" + key + "' count is outside the integer range", e);
                }
                idText = matcher.group(2).trim();
            }
        }
        if (count <= 0) {
            throw new IllegalArgumentException("'" + key + "' count must be > 0: " + count);
        }
        return new ItemSpec(
                VillagerTradePlan.requireCanonicalIdentifier(idText, key + " item"),
                count);
    }

    private static int readInt(Object config, String key, int fallback) {
        Object raw = member(config, key);
        return isNull(raw) ? fallback : integer(raw, key);
    }

    private static float readFloat(Object config, String key, float fallback) {
        Object raw = member(config, key);
        if (isNull(raw)) return fallback;
        double number = number(raw, key);
        if (!Double.isFinite(number) || number < -Float.MAX_VALUE || number > Float.MAX_VALUE) {
            throw new IllegalArgumentException(key + " must be a finite number: " + number);
        }
        return (float) number;
    }

    private static int integer(Object value, String field) {
        double number = number(value, field);
        if (!Double.isFinite(number) || number != Math.rint(number)
                || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " must be an integer: " + number);
        }
        return (int) number;
    }

    private static double number(Object value, String field) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof Value polyglot && polyglot.isNumber()) return polyglot.asDouble();
        throw new IllegalArgumentException(field + " must be a number");
    }

    private static String text(Object value) {
        if (value instanceof String string) return string.trim();
        if (value instanceof Value polyglot && polyglot.isString()) return polyglot.asString().trim();
        return null;
    }

    private static boolean isNull(Object value) {
        return value == null || value instanceof Value polyglot && polyglot.isNull();
    }

    private static Object member(Object value, String key) {
        if (isNull(value)) return null;
        if (value instanceof Map<?, ?> map) return map.get(key);
        if (value instanceof Value polyglot) {
            return polyglot.hasMembers() && polyglot.hasMember(key) ? polyglot.getMember(key) : null;
        }
        try {
            Value polyglot = Value.asValue(value);
            return polyglot.hasMembers() && polyglot.hasMember(key) ? polyglot.getMember(key) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private record ItemSpec(String id, int count) {
    }
}
