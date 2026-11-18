// API modules for the selected script environment.
declare module "nekojs:event/versioned" {
interface DynamicRegistryEvent {
    item(id: string, build?: (build: DynamicItemBuilder) => void): boolean;
    mobEffect(id: string, build?: (build: DynamicMobEffectBuilder) => void): boolean;
    soundEvent(id: string, build?: (build: DynamicSoundEventBuilder) => void): boolean;
}
    export namespace DynamicRegistryEvents {
        function dynamicRegistry(handler: ((event: DynamicRegistryEvent) => void)): void;
    }
}
declare function require(id: "nekojs:event/versioned"): typeof import("nekojs:event/versioned");
declare function require(id: string): unknown;
