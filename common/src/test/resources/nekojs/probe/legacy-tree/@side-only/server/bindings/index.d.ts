// Bindings for server scripts
import { $LegacyProbeFixture$SampleContext, $LegacyProbeFixture$SampleHelper } from "java:com/tkisor/nekojs/probe";

export {};

declare global {
    let SampleHelper: typeof $LegacyProbeFixture$SampleHelper;
    /** Sample context binding */
    let sampleContext: $LegacyProbeFixture$SampleContext;
}
