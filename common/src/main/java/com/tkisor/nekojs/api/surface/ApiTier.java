package com.tkisor.nekojs.api.surface;

/**
 * Tier of an API symbol contribution. The tier sets where the symbol is visible and how stable
 * it is.
 *
 * <p>{@code JsApiSurfaceResolver} branches explicitly on two tiers:
 * <ul>
 *   <li>{@link #GLOBAL}: the contributed {@code jsName} is checked against legacy reserved global
 *       names, and a conflict throws {@code LEGACY_NAME_COLLISION};</li>
 *   <li>{@link #VERSION}: the only tier that may set {@code nativeReturn=true} (other tiers throw
 *       {@code NATIVE_TYPE_LEAK}). Contributions on this tier split the return type by version.</li>
 * </ul>
 * The remaining constants are part of the contribution model and have no branch of their own yet.
 */
public enum ApiTier {
    /** Global symbol tier (global objects such as {@code ID} and {@code Platform});
     * checked against legacy reserved names. */
    GLOBAL,
    /** Type member tier (such as {@code NekoId.namespace}). */
    MEMBER,
    /** Symbols that are members of a module. */
    MODULE_MEMBER,
    /** Symbols on the platform feature tier. */
    FEATURE,
    /** Symbols on the platform tier. */
    PLATFORM,
    /** Symbols on the addon (plugin extension) tier. */
    ADDON,
    /** Version tier: the return type is split by version; the only tier that may set
     * {@code nativeReturn=true}. */
    VERSION,
    /** Tier for symbols with unsafe native types. */
    UNSAFE_NATIVE,
    /** Legacy preview tier. */
    LEGACY_PREVIEW
}
