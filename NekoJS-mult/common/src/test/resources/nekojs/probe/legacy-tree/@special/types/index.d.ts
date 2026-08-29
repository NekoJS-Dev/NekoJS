declare module "@special/types" {
    export namespace RegistryTypes {
        type SampleBlock = "testcraft:alpha" | "testcraft:beta" | "packns:gamma";
        type SampleBlockTag = "testcraft:all_blocks";
        type Namespace = "othermod" | "packns" | "testcraft";
    }
}

export * as types from "@special/types";
