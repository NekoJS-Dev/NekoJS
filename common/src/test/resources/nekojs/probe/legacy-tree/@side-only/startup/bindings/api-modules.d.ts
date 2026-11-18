// API modules for the selected script environment.
declare module "nekojs/api/stable" {
}
declare function require(id: "nekojs/api/stable"): typeof import("nekojs/api/stable");
declare module "nekojs/api/version" {
}
declare function require(id: "nekojs/api/version"): typeof import("nekojs/api/version");
declare module "nekojs:event/versioned" {
    export namespace SampleEvents {
        function cancellable(
            handler: ((event: import("java:com/tkisor/nekojs/probe").$LegacyProbeFixture$SampleCancellableEvent) => void)
        ): void;
        function dispatch(
            handler: ((event: import("java:com/tkisor/nekojs/probe").$LegacyProbeFixture$SampleDispatchEvent) => void)
        ): void;
        function dispatch(
            extra: import("java:com/tkisor/nekojs/probe").$LegacyProbeFixture$SampleDispatchKey,
            handler: ((event: import("java:com/tkisor/nekojs/probe").$LegacyProbeFixture$SampleDispatchEvent) => void)
        ): void;
    }
}
declare function require(id: "nekojs:event/versioned"): typeof import("nekojs:event/versioned");
declare function require(id: string): unknown;
