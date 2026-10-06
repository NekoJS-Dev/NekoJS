//? if neoforge {
package net.neoforged.neoforge.capabilities;

/** Creates the package-private native event for registration integration tests. */
public final class NekoCapabilityTestEvent {
    private NekoCapabilityTestEvent() {}

    public static RegisterCapabilitiesEvent create() {
        return new RegisterCapabilitiesEvent();
    }
}
//?}
