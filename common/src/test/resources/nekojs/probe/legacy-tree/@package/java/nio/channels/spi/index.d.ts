import { $Channel, $InterruptibleChannel } from "java:java/nio/channels";

declare module "java:java/nio/channels/spi" {
    export class $AbstractInterruptibleChannel implements $Channel, $InterruptibleChannel {
        get open(): boolean;
        isOpen(): boolean;
        close(): void;
    }

}
