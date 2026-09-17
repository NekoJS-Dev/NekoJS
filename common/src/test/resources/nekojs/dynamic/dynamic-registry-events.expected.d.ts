declare module "@side-only/server/events" {
}

export {};

declare global {
    namespace DynamicRegistryEvents {
        function dynamicRegistry(handler: ((event: $DynamicRegistryEventJS) => void)): void;
    }

}
