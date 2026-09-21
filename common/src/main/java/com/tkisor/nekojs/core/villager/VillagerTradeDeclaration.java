package com.tkisor.nekojs.core.villager;

import java.util.Locale;
import java.util.Objects;

/**
 * One "append a trade to an existing trade set" declaration (ticket 22), normalized into a
 * <b>pure JVM</b> value: every member is a plain id string, a number or a boolean, so this
 * type can live in {@code common} and can never touch a Minecraft type.
 *
 * <p>Registry surgery (building the real trade, writing the live trade set) happens only in
 * the platform/version adapter at the legal commit point of a candidate generation.
 *
 * <p>{@link #key()} is the canonical identity used for idempotent re-declaration across
 * reloads (the same declaration from the same or another script maps to the same key) and for
 * the "no longer declared" (unrestored) bookkeeping. The first version of this domain exposes
 * {@code add} only — remove/replace/modify are not part of the action set.
 */
public final class VillagerTradeDeclaration {

    private final String tradeSetId;
    private final String wantsId;
    private final int wantsCount;
    private final String additionalWantsId; // nullable
    private final int additionalWantsCount;
    private final String givesId;
    private final int givesCount;
    private final int maxUses;
    private final int xp;
    private final double priceMultiplier;
    private final String scriptId; // nullable, diagnostics only

    public VillagerTradeDeclaration(
            String tradeSetId,
            String wantsId, int wantsCount,
            String additionalWantsId, int additionalWantsCount,
            String givesId, int givesCount,
            int maxUses, int xp, double priceMultiplier,
            String scriptId) {
        this.tradeSetId = requireId(tradeSetId, "trade set");
        this.wantsId = requireId(wantsId, "cost");
        this.wantsCount = requirePositive(wantsCount, "cost count");
        if (additionalWantsId != null) {
            this.additionalWantsId = requireId(additionalWantsId, "secondary cost");
            this.additionalWantsCount = requirePositive(additionalWantsCount, "secondary cost count");
        } else {
            this.additionalWantsId = null;
            this.additionalWantsCount = 0;
        }
        this.givesId = requireId(givesId, "result");
        this.givesCount = requirePositive(givesCount, "result count");
        this.maxUses = requirePositive(maxUses, "maxUses");
        if (xp < 0) {
            throw new IllegalArgumentException("xp must be >= 0 (got " + xp + ")");
        }
        if (priceMultiplier < 0.0D || priceMultiplier > 1.0D) {
            throw new IllegalArgumentException("priceMultiplier must be within 0..1 (got " + priceMultiplier + ")");
        }
        this.xp = xp;
        this.priceMultiplier = priceMultiplier;
        this.scriptId = scriptId;
    }

    public String tradeSetId() {
        return tradeSetId;
    }

    public String wantsId() {
        return wantsId;
    }

    public int wantsCount() {
        return wantsCount;
    }

    /** Secondary cost item id, or {@code null} when the trade has a single cost. */
    public String additionalWantsId() {
        return additionalWantsId;
    }

    public int additionalWantsCount() {
        return additionalWantsCount;
    }

    public String givesId() {
        return givesId;
    }

    public int givesCount() {
        return givesCount;
    }

    public int maxUses() {
        return maxUses;
    }

    public int xp() {
        return xp;
    }

    public double priceMultiplier() {
        return priceMultiplier;
    }

    /** Owning script id for diagnostics, or {@code null} when unavailable. */
    public String scriptId() {
        return scriptId;
    }

    /**
     * Canonical identity of this listing inside its trade set: the declaration tuple without
     * the owning script. Two identical declarations (same script or not) share a key, so a
     * reload is idempotent instead of appending duplicates.
     */
    public String listingKey() {
        return "trade[" + wantsId + "x" + wantsCount
                + (additionalWantsId == null ? "" : "+" + additionalWantsId + "x" + additionalWantsCount)
                + "->" + givesId + "x" + givesCount
                + ",maxUses=" + maxUses + ",xp=" + xp + ",price=" + canonicalPrice() + "]";
    }

    /** Canonical identity of the trade-set-scoped declaration ({@link #listingKey()} inside a set). */
    public String key() {
        return tradeSetId + "|" + listingKey();
    }

    /** Stable, locale-independent rendering of the price multiplier ({@code 0.05} stays {@code 0.05}). */
    private String canonicalPrice() {
        String text = String.format(Locale.ROOT, "%.4f", priceMultiplier);
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && text.charAt(end - 1) == '.') {
            end--;
        }
        return text.substring(0, end);
    }

    private static String requireId(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " id must not be empty");
        }
        return value.trim();
    }

    private static int requirePositive(int value, String what) {
        if (value <= 0) {
            throw new IllegalArgumentException(what + " must be > 0 (got " + value + ")");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VillagerTradeDeclaration declaration && key().equals(declaration.key());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(key());
    }

    @Override
    public String toString() {
        return key() + " @ " + tradeSetId + (scriptId == null ? "" : " (from " + scriptId + ")");
    }
}
