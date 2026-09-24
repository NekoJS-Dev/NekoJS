package com.tkisor.nekojs.api.ui;

/**
 * Stable diagnostic codes for the UI visual and resource contract (areas 6xxx and 8xxx).
 * The code is the lookup key: wording may change, codes may not be reused.
 */
public final class UiErrorCodes {
    /** A color prop is not a controlled color form. */
    public static final String INVALID_COLOR = "NEKO-6001";
    /** A visual prop value is out of range or has the wrong type (opacity, fontSize, fit, crop, ...). */
    public static final String INVALID_VISUAL_VALUE = "NEKO-6002";
    /** A resource identifier violates the controlled id grammar. */
    public static final String INVALID_RESOURCE_ID = "NEKO-6003";
    /** A controlled resource id does not resolve in the resource roots. */
    public static final String MISSING_RESOURCE = "NEKO-6004";
    /** Reading or loading a resolved resource failed. */
    public static final String RESOURCE_LOAD_FAILED = "NEKO-6005";
    /** A resource or crop size is not legal (non-positive dimensions and similar). */
    public static final String INVALID_RESOURCE_SIZE = "NEKO-6006";
    /** Decoding a resource into a usable image failed. */
    public static final String RESOURCE_DECODE_FAILED = "NEKO-6007";

    // Inspector codes use area 8xxx (ticket 45); 6xxx stays with the visual contract, 7xxx with lifecycle.
    /** A runtime layout snapshot does not match the inspector read contract (version skew). */
    public static final String INSPECTOR_SNAPSHOT_MALFORMED = "NEKO-8001";

    private UiErrorCodes() {
    }
}
