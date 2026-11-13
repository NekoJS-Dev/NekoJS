package com.tkisor.nekojs.api.contract;

import java.util.*;
import java.util.stream.Collectors;

/**
 * An immutable set of verified contracts, indexed by {@link ApiContractIdentity}.
 *
 * <p>Build one with {@link #of(VerifiedApiContract...)}; a duplicate identity throws
 * {@link IllegalArgumentException}.
 */
public final class VerifiedContractSet {

    private final Map<ApiContractIdentity, VerifiedApiContract> index;

    private VerifiedContractSet(Map<ApiContractIdentity, VerifiedApiContract> index) {
        this.index = Map.copyOf(index);
    }

    /** Builds a set from the given contracts; a duplicate identity throws {@link IllegalArgumentException}. */
    public static VerifiedContractSet of(VerifiedApiContract... contracts) {
        Map<ApiContractIdentity, VerifiedApiContract> map = new LinkedHashMap<>();
        for (VerifiedApiContract c : contracts) {
            if (map.containsKey(c.identity())) {
                throw new IllegalArgumentException("duplicate contract identity: " + c.identity());
            }
            map.put(c.identity(), c);
        }
        return new VerifiedContractSet(map);
    }

    /** Returns all contracts as an immutable list. */
    public List<VerifiedApiContract> all() {
        return List.copyOf(index.values());
    }

    /** Returns every contract registered for the given owner. */
    public List<VerifiedApiContract> forOwner(String owner) {
        return index.values().stream()
                .filter(c -> c.identity().owner().equals(owner))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns the PORTABLE contract for the given owner.
     * Throws {@link IllegalStateException} unless exactly one exists.
     */
    public VerifiedApiContract requirePortable(String owner) {
        List<VerifiedApiContract> portable = index.values().stream()
                .filter(c -> c.identity().owner().equals(owner)
                        && c.identity().kind() == ApiContractKind.PORTABLE)
                .toList();
        if (portable.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly 1 PORTABLE contract for owner '" + owner + "', found " + portable.size());
        }
        return portable.getFirst();
    }
}
