import { $ProbeAddGlobalEventJS, $ProbeAssignTypeEventJS, $ProbeModifyTypeEventJS, $ProbeSnippetEventJS } from "java:com/tkisor/nekojs/probe/events";

declare module "@side-only/server/events" {
}

export {};

declare global {
    namespace ProbeEvents {
        function modifyType(handler: ((event: $ProbeModifyTypeEventJS) => void)): void;
        function assignType(handler: ((event: $ProbeAssignTypeEventJS) => void)): void;
        function addGlobal(handler: ((event: $ProbeAddGlobalEventJS) => void)): void;
        function snippets(handler: ((event: $ProbeSnippetEventJS) => void)): void;
    }

}
