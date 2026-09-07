package com.tkisor.nekojs.villager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable, loader-neutral declarations collected during one complete SERVER
 * script revision. The list preserves listener and declaration order, including
 * valid duplicate declarations.
 */
public record VillagerTradePlan(List<VillagerTradePlan.Offer> offers) {
    private static final Pattern CANONICAL_ID =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public VillagerTradePlan {
        offers = List.copyOf(offers == null ? List.of() : offers);
    }

    /** Returns an empty immutable plan. */
    public static VillagerTradePlan empty() {
        return new VillagerTradePlan(List.of());
    }

    /** Starts a mutable candidate builder. */
    public static Builder builder() {
        return new Builder();
    }

    public int size() {
        return offers.size();
    }

    public boolean isEmpty() {
        return offers.isEmpty();
    }

    /** Returns whether an explicit, canonical namespace:path id is valid. */
    public static boolean isCanonicalIdentifier(String value) {
        return value != null && CANONICAL_ID.matcher(value).matches();
    }

    /** Validates and returns a canonical namespace:path id. */
    public static String requireCanonicalIdentifier(String value, String field) {
        String normalized = value == null ? null : value.trim();
        if (!isCanonicalIdentifier(normalized)) {
            throw new IllegalArgumentException(field + " must be a canonical namespace:path id: " + value);
        }
        return normalized;
    }

    /** One validated declarative offer. */
    public record Offer(
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
        public Offer {
            tradeSet = requireCanonicalIdentifier(tradeSet, "tradeSet");
            wantsId = requireCanonicalIdentifier(wantsId, "cost item");
            givesId = requireCanonicalIdentifier(givesId, "result item");
            if (additionalWantsId != null) {
                additionalWantsId = requireCanonicalIdentifier(additionalWantsId, "secondary cost item");
            }
            wantsCount = requirePositive(wantsCount, "cost count");
            additionalWantsCount = additionalWantsId == null
                    ? 1
                    : requirePositive(additionalWantsCount, "secondary cost count");
            givesCount = requirePositive(givesCount, "result count");
            if (maxUses <= 0) {
                throw new IllegalArgumentException("maxUses must be > 0: " + maxUses);
            }
            if (xp < 0) {
                throw new IllegalArgumentException("xp must be >= 0: " + xp);
            }
            if (!Float.isFinite(priceMultiplier) || priceMultiplier < 0.0f || priceMultiplier > 1.0f) {
                throw new IllegalArgumentException("priceMultiplier must be finite and within 0..1: " + priceMultiplier);
            }
        }

        /** Alias matching the script-facing config name. */
        public String costId() {
            return wantsId;
        }

        /** Alias matching the script-facing config name. */
        public int costCount() {
            return wantsCount;
        }

        /** Alias matching the script-facing config name. */
        public String costBId() {
            return additionalWantsId;
        }

        /** Alias matching the script-facing config name. */
        public int costBCount() {
            return additionalWantsCount;
        }

        /** Alias matching the script-facing config name. */
        public String resultId() {
            return givesId;
        }

        /** Alias matching the script-facing config name. */
        public int resultCount() {
            return givesCount;
        }

        private static int requirePositive(int value, String field) {
            if (value <= 0) {
                throw new IllegalArgumentException(field + " must be > 0: " + value);
            }
            return value;
        }
    }

    /** Mutable candidate collector; sealing makes a defensive immutable copy. */
    public static final class Builder {
        private final List<Offer> offers = new ArrayList<>();
        private VillagerTradePlan sealedPlan;

        private Builder() {
        }

        public Builder add(Offer offer) {
            ensureOpen();
            offers.add(Objects.requireNonNull(offer, "offer"));
            return this;
        }

        public Builder add(
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
            return add(new Offer(
                    tradeSet,
                    wantsId,
                    wantsCount,
                    additionalWantsId,
                    additionalWantsCount,
                    givesId,
                    givesCount,
                    maxUses,
                    xp,
                    priceMultiplier));
        }

        /** Returns the number of declarations currently staged. */
        public int size() {
            return offers.size();
        }

        /** Returns an immutable view of declarations currently staged. */
        public List<Offer> offers() {
            return List.copyOf(offers);
        }

        /** Returns an immutable snapshot without closing this collector. */
        public VillagerTradePlan snapshot() {
            return new VillagerTradePlan(offers);
        }

        /** Seals this builder; repeated calls return the same immutable plan. */
        public VillagerTradePlan seal() {
            if (sealedPlan == null) {
                sealedPlan = new VillagerTradePlan(offers);
            }
            return sealedPlan;
        }

        private void ensureOpen() {
            if (sealedPlan != null) {
                throw new IllegalStateException("Villager trade plan is sealed");
            }
        }
    }
}
