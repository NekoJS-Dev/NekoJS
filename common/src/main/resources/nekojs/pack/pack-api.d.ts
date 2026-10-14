/** Available only inside formatVersion 1 pack entries and their imported modules. */
type NekoPackJson = null | boolean | number | string | NekoPackJson[] | { [key: string]: NekoPackJson };
declare const Pack: {
    readonly selector: string;
    readonly activation: number;
    /** Register a declared synchronous capability during entry initialization. */
    provide(name: string, callback: (request: NekoPackJson) => NekoPackJson): void;
    /** Obtain a declared provider capability. The handle expires with either activation. */
    require(provider: string, name: string): (request: NekoPackJson) => NekoPackJson;
};
