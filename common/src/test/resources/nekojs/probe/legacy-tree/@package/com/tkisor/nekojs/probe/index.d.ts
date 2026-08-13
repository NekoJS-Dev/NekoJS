import { $String } from "java:java/lang";

declare module "java:com/tkisor/nekojs/probe" {
    export class $LegacyProbeFixture$SampleCancellableEvent {
        constructor();
        get message(): string;
        getMessage(): string;
    }

    export class $LegacyProbeFixture$SampleContext {
        constructor();
        get world(): string;
        getWorld(): string;
    }

    export class $LegacyProbeFixture$SampleDispatchEvent {
        constructor();
        get result(): string;
        getResult(): string;
    }

    export class $LegacyProbeFixture$SampleDispatchKey {
        constructor();
        get value(): string;
        getValue(): string;
    }

    export class $LegacyProbeFixture$SampleHelper {
        constructor();
        get name(): string;
        getName(): string;
        static of(arg0: string, arg1: number): string;
        static of(arg0: string): string;
    }

    export class $LegacyProbeFixture$SampleWidget {
        constructor();
        get label(): string;
        getLabel(): string;
    }

    /** SampleWidget adapter: accepts a string label */
    export type $LegacyProbeFixture$SampleWidget_ = $LegacyProbeFixture$SampleWidget | string;
}
