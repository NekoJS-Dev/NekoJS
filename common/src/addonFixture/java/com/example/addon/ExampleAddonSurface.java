package com.example.addon;

/**
 * Script-visible surface published as the {@code ExampleAddon} global binding.
 *
 * <p>The instance is created once per plugin instance during collection and the
 * exact same frozen instance is what scripts of every generation read; nothing
 * here is rebuilt by a script reload. Test harnesses (and scripts) observe the
 * plugin lifecycle through the counters so "bootstrap ran exactly once" stays
 * observable without touching plugin internals.
 */
public final class ExampleAddonSurface {

    private static ExampleAddonSurface instance;

    private int initCalls;
    private int closeCalls;
    private int registrationCalls;
    private String publishedSummary = "<not finished yet>";

    /** Current singleton of this class-loader world (test observation seam). */
    public static ExampleAddonSurface get() {
        if (instance == null) {
            instance = new ExampleAddonSurface();
        }
        return instance;
    }

    private ExampleAddonSurface() {
    }

    /** Stable marker proving scripts and Handle readers see the same frozen product. */
    public String marker() {
        return "exampleaddon-frozen-product";
    }

    /** How often the plugin's init() callback ran (once per plugin bootstrap). */
    public int initCalls() {
        return initCalls;
    }

    /** How often Binding.close(type) reached this surface (session cleanup touch count). */
    public int closeCalls() {
        return closeCalls;
    }

    /** How often the provider registered its extension point (once per bootstrap). */
    public int registrationCalls() {
        return registrationCalls;
    }

    /** Summary published by the custom point's finisher (frozen afterwards). */
    public String publishedSummary() {
        return publishedSummary;
    }

    void noteInit() {
        initCalls++;
    }

    void noteClose() {
        closeCalls++;
    }

    void noteRegistration() {
        registrationCalls++;
    }

    void publishSummary(String summary) {
        this.publishedSummary = summary;
    }

    /** Close callback of the {@code ExampleAddon} binding (called by session teardown paths). */
    public void onClose() {
        noteClose();
    }
}
