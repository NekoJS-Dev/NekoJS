package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.api.annotation.Doc;

/**
 * Common fluid amounts in millibuckets (mB), for recipe scripts to reference directly.
 */
@Doc("Common fluid amounts in millibuckets for recipe scripts.")
public final class FluidAmounts {
    /** One bucket (1000mB). */
    @Doc("One bucket, in millibuckets (1000mB).")
    public static final int BUCKET = 1000;
    /** One millibucket. */
    @Doc("One millibucket (1mB).")
    public static final int MILLIBUCKET = 1;
    /** Alias of {@link #BUCKET}. */
    @Doc("Alias of BUCKET.")
    public static final int B = BUCKET;
    /** Alias of {@link #MILLIBUCKET}. */
    @Doc("Alias of MILLIBUCKET.")
    public static final int MB = MILLIBUCKET;
    /** The usual amount for one ingot (90mB). */
    @Doc("Fluid for one ingot (90mB).")
    public static final int INGOT = 90;
    /** The usual amount for one nugget (10mB). */
    @Doc("Fluid for one nugget (10mB).")
    public static final int NUGGET = INGOT / 9;
    /** The usual amount for one metal block (810mB). */
    @Doc("Fluid for one metal block (810mB).")
    public static final int METAL_BLOCK = INGOT * 9;
    /** One bottle (250mB). */
    @Doc("One bottle (250mB).")
    public static final int BOTTLE = 250;

    private FluidAmounts() {}
}
