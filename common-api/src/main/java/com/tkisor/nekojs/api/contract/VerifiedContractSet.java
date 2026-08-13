package com.tkisor.nekojs.api.contract;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 一组已验证契约的不可变集合，按 {@link ApiContractIdentity} 索引。
 *
 * <p>通过 {@link #of(VerifiedApiContract...)} 构造，重复身份会抛
 * {@link IllegalArgumentException}。
 */
public final class VerifiedContractSet {

    private final Map<ApiContractIdentity, VerifiedApiContract> index;

    private VerifiedContractSet(Map<ApiContractIdentity, VerifiedApiContract> index) {
        this.index = Map.copyOf(index);
    }

    /** 以一组契约构造集合；重复身份抛 {@link IllegalArgumentException}。 */
    @SafeVarargs
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

    /** 返回全部契约（不可变列表）。 */
    public List<VerifiedApiContract> all() {
        return List.copyOf(index.values());
    }

    /** 返回指定 owner 的全部契约。 */
    public List<VerifiedApiContract> forOwner(String owner) {
        return index.values().stream()
                .filter(c -> c.identity().owner().equals(owner))
                .collect(Collectors.toUnmodifiableList());
    }

    /** 返回指定 owner 的 PORTABLE 契约；数量不为 1 时抛 {@link IllegalStateException}。 */
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
