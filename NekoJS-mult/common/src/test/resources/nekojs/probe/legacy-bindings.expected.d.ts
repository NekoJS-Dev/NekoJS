// Bindings for server scripts
import { $LegacyProbeCompatibilityTest$Helper } from "java:com/tkisor/nekojs/probe";

export {};

declare global {
    let Helper: typeof $LegacyProbeCompatibilityTest$Helper;
}
