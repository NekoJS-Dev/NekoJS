import { $LegacyProbeCompatibilityTest$SampleEvent } from "java:com/tkisor/nekojs/probe";

declare module "@side-only/server/events" {
}

export {};

declare global {
    namespace ServerEvents {
        function sample(handler: ((event: $LegacyProbeCompatibilityTest$SampleEvent) => void)): void;
    }

}
