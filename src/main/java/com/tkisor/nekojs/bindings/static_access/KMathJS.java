package com.tkisor.nekojs.bindings.static_access;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Math constants and vector factories, the counterpart of the KubeJS {@code KMath} global.
 *
 * <p>Bound as a class ({@code registry.register("KMath", KMathJS.class)}), so scripts use
 * {@code KMath.PI} and {@code KMath.blockPos(1, 64, -2)} directly.
 */
public final class KMathJS {
    public static final double E = Math.E;
    public static final double PI = Math.PI;
    public static final double DEGREES_TO_RADIANS = Math.PI / 180.0;
    public static final double RADIANS_TO_DEGREES = 180.0 / Math.PI;

    private KMathJS() {}

    /** Converts degrees to radians ({@code KMath.deg(90) === Math.PI / 2}). */
    public static double deg(double degrees) {
        return Math.toRadians(degrees);
    }

    /** Converts radians to degrees. */
    public static double rad(double radians) {
        return Math.toDegrees(radians);
    }

    public static BlockPos blockPos(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    public static BlockPos blockPos(double x, double y, double z) {
        return BlockPos.containing(x, y, z);
    }

    public static Vec3 vec3(double x, double y, double z) {
        return new Vec3(x, y, z);
    }

    public static Vec3 vec3(BlockPos pos) {
        return Vec3.atLowerCornerOf(pos);
    }
}
