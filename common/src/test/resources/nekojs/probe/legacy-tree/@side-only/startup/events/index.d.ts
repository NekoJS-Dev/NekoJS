import { $LegacyProbeFixture$SampleCancellableEvent, $LegacyProbeFixture$SampleDispatchEvent, $LegacyProbeFixture$SampleDispatchKey } from "java:com/tkisor/nekojs/probe";

declare module "@side-only/startup/events" {
}

export {};

declare global {
    namespace SampleEvents {
        function cancellable(handler: ((event: $LegacyProbeFixture$SampleCancellableEvent) => void)): void;
        function dispatch(handler: ((event: $LegacyProbeFixture$SampleDispatchEvent) => void)): void;
        function dispatch(extra: $LegacyProbeFixture$SampleDispatchKey, handler: ((event: $LegacyProbeFixture$SampleDispatchEvent) => void)): void;
    }

}
