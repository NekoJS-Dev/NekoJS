




        available(): number;
        close(): void;
        close(): void;
        close(): void;
        constructor();
        constructor();
        flush(): void;
        mark(arg0: number): void;
        markSupported(): boolean;
        read(): number;
        read(arg0: number[]): number;
        read(arg0: number[], arg1: number, arg2: number): number;
        readAllBytes(): number[];
        readNBytes(arg0: number): number[];
        readNBytes(arg0: number[], arg1: number, arg2: number): number;
        reset(): void;
        skip(arg0: number): number;
        skipNBytes(arg0: number): void;
        static nullInputStream(): $InputStream;
        static nullOutputStream(): $OutputStream;
        transferTo(arg0: $OutputStream): number;
        write(arg0: number): void;
        write(arg0: number[]): void;
        write(arg0: number[], arg1: number, arg2: number): void;
    export class $InputStream implements $Closeable {
    export class $OutputStream implements $Closeable, $Flushable {
    export interface $Closeable extends $AutoCloseable {
    export interface $Serializable {
    }
    }
    }
    }
declare module "java:java/io" {
import { $AutoCloseable } from "java:java/lang";
}