package com.tkisor.nekojs.bindings.static_access;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * 数学常量与向量工厂（KubeJS {@code KMath} 全局的对标）。
 *
 * <p>绑定为类（{@code registry.register("KMath", KMathJS.class)}），脚本直接
 * {@code KMath.PI}、{@code KMath.blockPos(1, 64, -2)}。
 */
public final class KMathJS {
    public static final double E = Math.E;
    public static final double PI = Math.PI;
    public static final double DEGREES_TO_RADIANS = Math.PI / 180.0;
    public static final double RADIANS_TO_DEGREES = 180.0 / Math.PI;

    private KMathJS() {}

    /** 角度转弧度（{@code KMath.deg(90) === Math.PI / 2}）。 */
    public static double deg(double degrees) {
        return Math.toRadians(degrees);
    }

    /** 弧度转角度。 */
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
