// API modules for the selected script environment.
declare module "nekojs:event/versioned" {
    export namespace ProbeEvents {
        function addGlobal(
            handler: ((event: import("java:com/tkisor/nekojs/probe/events").$ProbeAddGlobalEventJS) => void)
        ): void;
        function assignType(
            handler: ((event: import("java:com/tkisor/nekojs/probe/events").$ProbeAssignTypeEventJS) => void)
        ): void;
        function modifyType(
            handler: ((event: import("java:com/tkisor/nekojs/probe/events").$ProbeModifyTypeEventJS) => void)
        ): void;
        function snippets(
            handler: ((event: import("java:com/tkisor/nekojs/probe/events").$ProbeSnippetEventJS) => void)
        ): void;
    }
}
declare function require(id: "nekojs:event/versioned"): typeof import("nekojs:event/versioned");
declare function require(id: string): unknown;
