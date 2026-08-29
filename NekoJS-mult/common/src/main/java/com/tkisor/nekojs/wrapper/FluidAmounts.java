package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.api.annotation.Doc;

/**
 * 常用流体量的毫桶（mB）常量，供配方脚本直接引用。
 */
@Doc("Common fluid amounts in millibuckets for recipe scripts.")
public final class FluidAmounts {
    /** 一桶（1000mB）。 */
    @Doc("One bucket, in millibuckets (1000mB).")
    public static final int BUCKET = 1000;
    /** 一毫桶。 */
    @Doc("One millibucket (1mB).")
    public static final int MILLIBUCKET = 1;
    /** {@link #BUCKET} 的别名。 */
    @Doc("Alias of BUCKET.")
    public static final int B = BUCKET;
    /** {@link #MILLIBUCKET} 的别名。 */
    @Doc("Alias of MILLIBUCKET.")
    public static final int MB = MILLIBUCKET;
    /** 一个锭的常见量（90mB）。 */
    @Doc("Fluid for one ingot (90mB).")
    public static final int INGOT = 90;
    /** 一个粒的常见量（10mB）。 */
    @Doc("Fluid for one nugget (10mB).")
    public static final int NUGGET = INGOT / 9;
    /** 一个金属方块的常见量（810mB）。 */
    @Doc("Fluid for one metal block (810mB).")
    public static final int METAL_BLOCK = INGOT * 9;
    /** 一瓶（250mB）。 */
    @Doc("One bottle (250mB).")
    public static final int BOTTLE = 250;

    private FluidAmounts() {}
}
