package com.tkisor.nekojs.bindings.static_access;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class UtilsJS {
    public int randomInt(int maxExclusive) {
        return ThreadLocalRandom.current().nextInt(maxExclusive);
    }

    public int randomInt(int minInclusive, int maxExclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxExclusive);
    }

    public double randomDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    public double randomDouble(double maxExclusive) {
        return ThreadLocalRandom.current().nextDouble(maxExclusive);
    }

    public double randomDouble(double minInclusive, double maxExclusive) {
        return ThreadLocalRandom.current().nextDouble(minInclusive, maxExclusive);
    }

    public boolean chance(double probability) {
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    public boolean isArray(Object value) {
        return value != null && value.getClass().isArray();
    }

    public boolean isList(Object value) {
        return value instanceof List<?>;
    }

    public boolean isMap(Object value) {
        return value instanceof Map<?, ?>;
    }
}
