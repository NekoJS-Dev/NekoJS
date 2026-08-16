package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 杂项工具：随机数与 Java 值类型判断（数组 / List / Map）。
 */
@Doc("Misc helpers: randomness and Java value type checks.")
public final class UtilsJS {
    /** [0, maxExclusive) 的随机 int。 */
    @Doc("Returns a random int in [0, maxExclusive).")
    @Param(name = "maxExclusive", value = "exclusive upper bound; must be positive")
    @Return("random int, never equal to maxExclusive")
    public int randomInt(int maxExclusive) {
        return ThreadLocalRandom.current().nextInt(maxExclusive);
    }

    /** [minInclusive, maxExclusive) 的随机 int。 */
    @Doc("Returns a random int in [minInclusive, maxExclusive).")
    @Param(name = "minInclusive", value = "inclusive lower bound")
    @Param(name = "maxExclusive", value = "exclusive upper bound")
    @Return("random int within the range")
    public int randomInt(int minInclusive, int maxExclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxExclusive);
    }

    /** [0, 1) 的随机 double。 */
    @Doc("Returns a random double in [0, 1).")
    @Return("random double, never 1.0")
    public double randomDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    /** [0, maxExclusive) 的随机 double。 */
    @Doc("Returns a random double in [0, maxExclusive).")
    @Param(name = "maxExclusive", value = "exclusive upper bound; must be positive")
    @Return("random double within the range")
    public double randomDouble(double maxExclusive) {
        return ThreadLocalRandom.current().nextDouble(maxExclusive);
    }

    /** [minInclusive, maxExclusive) 的随机 double。 */
    @Doc("Returns a random double in [minInclusive, maxExclusive).")
    @Param(name = "minInclusive", value = "inclusive lower bound")
    @Param(name = "maxExclusive", value = "exclusive upper bound")
    @Return("random double within the range")
    public double randomDouble(double minInclusive, double maxExclusive) {
        return ThreadLocalRandom.current().nextDouble(minInclusive, maxExclusive);
    }

    /** 以给定概率返回 true（0 恒 false，>= 1 恒 true）。 */
    @Doc("Returns true with the given probability.")
    @Param(name = "probability", value = "chance of true, from 0 (never) to 1 (always)")
    @Return("true with the given probability, false otherwise")
    public boolean chance(double probability) {
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    /** 是否为 Java 数组。 */
    @Doc("Checks whether the value is a Java array.")
    @Param(name = "value", value = "value to check; null is allowed")
    @Return("true for arrays; false for null and non-arrays")
    public boolean isArray(Object value) {
        return value != null && value.getClass().isArray();
    }

    /** 是否为 Java List。 */
    @Doc("Checks whether the value is a Java List.")
    @Param(name = "value", value = "value to check; null is allowed")
    @Return("true for List instances; false for null and other types")
    public boolean isList(Object value) {
        return value instanceof List<?>;
    }

    /** 是否为 Java Map。 */
    @Doc("Checks whether the value is a Java Map.")
    @Param(name = "value", value = "value to check; null is allowed")
    @Return("true for Map instances; false for null and other types")
    public boolean isMap(Object value) {
        return value instanceof Map<?, ?>;
    }
}
