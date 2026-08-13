import { $AutoCloseable } from "java:java/lang";

declare module "java:java/io" {
    export interface $Serializable {
    }

    export class $InputStream implements $Closeable {
        constructor();
        static nullInputStream(): $InputStream;
        available(): number;
        close(): void;
        markSupported(): boolean;
        mark(arg0: number): void;
        readAllBytes(): number[];
        readNBytes(arg0: number[], arg1: number, arg2: number): number;
        readNBytes(arg0: number): number[];
        read(arg0: number[], arg1: number, arg2: number): number;
        read(arg0: number[]): number;
        read(): number;
        reset(): void;
        skipNBytes(arg0: number): void;
        skip(arg0: number): number;
        transferTo(arg0: $OutputStream): number;
    }

    export interface $Closeable extends $AutoCloseable {
        close(): void;
    }

    export class $OutputStream implements $Closeable, $Flushable {
        constructor();
        static nullOutputStream(): $OutputStream;
        close(): void;
        flush(): void;
        write(arg0: number[], arg1: number, arg2: number): void;
        write(arg0: number[]): void;
        write(arg0: number): void;
    }

}
